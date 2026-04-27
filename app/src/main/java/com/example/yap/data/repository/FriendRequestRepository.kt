package com.example.yap.data.repository

import android.util.Log
import com.example.yap.data.model.FriendRequestEntity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class FriendRequestRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val client: OkHttpClient,
    private val gson: Gson
) {
    private val requestsCollection = firestore.collection("friend_requests")
    private val usersCollection = firestore.collection("users")
    val sessionSentRequests = MutableStateFlow<Set<String>>(emptySet())


    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)


    private val incomingRequestsCache = ConcurrentHashMap<String, Flow<List<FriendRequestEntity>>>()


    /**
     * Проверяет, отправляли ли мы уже заявку этому пользователю.
     * Экономит запросы: сначала смотрит в RAM, и только если там нет — делает точечный (limit 1) запрос.
     */
    suspend fun checkIsRequestPending(receiverId: String): Boolean {
        val currentUserId = auth.currentUser?.uid ?: return false

        // 1. Мгновенная проверка в RAM (отправили только что)
        if (sessionSentRequests.value.contains(receiverId)) {
            Log.d("FriendRequestRepo", "🚀 RAM Hit: Заявка пользователю $receiverId уже в ожидании")
            return true
        }

        // 2. Проверка в Firebase (если заявка была отправлена в прошлой сессии)
        return try {
            val snapshot = requestsCollection
                .whereEqualTo("senderId", currentUserId)
                .whereEqualTo("receiverId", receiverId)
                .whereEqualTo("status", "pending")
                .limit(1) // Экономия: нам нужен максимум 1 документ!
                .get()
                .await()

            val isPending = !snapshot.isEmpty
            if (isPending) {
                // Кэшируем, чтобы больше не лезть в сеть для этого юзера
                sessionSentRequests.update { it + receiverId }
            }
            isPending
        } catch (e: Exception) {
            Log.e("FriendRequestRepo", "Ошибка проверки статуса: ${e.message}")
            false
        }
    }

    /**
     * Отправка новой заявки
     */
    suspend fun sendRequest(
        receiverId: String,
        senderName: String,
        senderAvatar: String?
    ): Result<String> {
        val currentUserId = auth.currentUser?.uid ?: return Result.failure(Exception("Пользователь не авторизован"))

        // Защита от дублей на стороне клиента
        if (sessionSentRequests.value.contains(receiverId)) {
            return Result.failure(Exception("Заявка уже отправлена"))
        }

        return try {

            if (checkIsRequestPending(receiverId)) return Result.failure(Exception("already_sent"))

            val cross = requestsCollection
                .whereEqualTo("senderId", receiverId)
                .whereEqualTo("receiverId", currentUserId)
                .whereEqualTo("status", "pending").limit(1).get().await()

            if (!cross.isEmpty) return Result.failure(Exception("cross_request"))

            val documentRef = requestsCollection.document()
            val request = FriendRequestEntity(
                id = documentRef.id,
                senderId = currentUserId,
                receiverId = receiverId,
                senderName = senderName,
                senderAvatarUrl = senderAvatar,
                timestamp = System.currentTimeMillis(),
                status = "pending"
            )

            // Отправляем в Firebase
            documentRef.set(request).await()

            // Сразу добавляем в кэш
            sessionSentRequests.update { it + receiverId }

            sendFriendRequestPush(receiverId, senderName)

            Result.success(documentRef.id)
        } catch (e: Exception) {
            Log.e("FriendRequestRepo", "Ошибка отправки заявки: ${e.message}")
            Result.failure(e)
        }
    }

    private suspend fun sendFriendRequestPush(receiverId: String, senderName: String) {
        withContext(Dispatchers.IO) {
            try {
                val receiverDoc = usersCollection.document(receiverId).get().await()

                // ВАЖНО: Мы ИГНОРИРУЕМ проверку mutedUsers. Заявки доходят всегда.

                val fcmToken = receiverDoc.getString("fcmToken")
                if (fcmToken.isNullOrEmpty()) {
                    Log.e("PUSH_REQUEST", "У пользователя $receiverId нет токена. Пуш заявки не отправлен.")
                    return@withContext
                }

                // Формируем payload с указанием ТИПА уведомления
                val jsonMap = mapOf(
                    "fcmToken" to fcmToken,
                    "senderName" to senderName,
                    "type" to "FRIEND_REQUEST" // Сервер поймет, что это заявка
                )
                // Предполагается, что gson и client (OkHttpClient) доступны в этом классе,
                // так же как они были доступны в ChatRepo
                val jsonString = gson.toJson(jsonMap)

                val requestBody = jsonString.toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder()
                    .url("https://yap-server.onrender.com/send-notification")
                    .post(requestBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        Log.d("PUSH_REQUEST", "Пуш-уведомление о заявке успешно улетело на сервер.")
                    } else {
                        Log.e("PUSH_REQUEST", "Ошибка Render: ${response.code} ${response.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e("PUSH_REQUEST", "Сбой при отправке пуша заявки", e)
            }
        }
    }

    private fun createIncomingRequestsFlow(currentUserId: String): Flow<List<FriendRequestEntity>> = callbackFlow {
        Log.i("FIREBASE_NET", "🛰️ СОЗДАНИЕ реального SnapshotListener для заявок (UID: $currentUserId)")
        val query = requestsCollection
            .whereEqualTo("receiverId", currentUserId)
            .whereEqualTo("status", "pending")
            .orderBy("timestamp", Query.Direction.DESCENDING)

        val subscription = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("FIREBASE_NET", "❌ Ошибка Firestore в заявках", error)
                Log.e("FriendRequestRepo", "Ошибка подписки. Проверь композитный индекс в Firebase!", error)
                return@addSnapshotListener
            }

            snapshot?.let { querySnapshot ->
                val isFromCache = querySnapshot.metadata.hasPendingWrites() || querySnapshot.metadata.isFromCache
                val requests = querySnapshot.documents.mapNotNull { doc ->
                    doc.toObject(FriendRequestEntity::class.java)?.copy(id = doc.id)
                }
                Log.d("FIREBASE_NET", "📥 Пришли данные заявок. Кол-во: ${requests.size} (Из кэша: $isFromCache)")
                trySend(requests)
            }
        }

        awaitClose {
            Log.w("FIREBASE_NET", "🔌 ЗАКРЫТИЕ SnapshotListener для заявок (UID: $currentUserId)")
            subscription.remove()
        }
    }

    fun observeIncomingRequests(currentUserId: String): Flow<List<FriendRequestEntity>> {
        return incomingRequestsCache.getOrPut(currentUserId) {
            Log.d("REQS_DEBUG", "Создание нового SharedFlow в кэше репозитория для $currentUserId")
            createIncomingRequestsFlow(currentUserId)
                .shareIn(
                    scope = repositoryScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    replay = 1
                )
        }
    }

    suspend fun getAllSentPendingRequests(): Set<String> {
        val currentUserId = auth.currentUser?.uid ?: return emptySet()
        return try {
            val snapshot = requestsCollection
                .whereEqualTo("senderId", currentUserId)
                .whereEqualTo("status", "pending")
                .get()
                .await()

            val dbIds = snapshot.documents.mapNotNull { it.getString("receiverId") }.toSet()

            // Синхронизируем RAM кэш с полученными данными из БД
            if (dbIds.isNotEmpty()) {
                sessionSentRequests.update { it + dbIds }
            }
            dbIds
        } catch (e: Exception) {
            Log.e("FriendRequestRepo", "Ошибка получения всех исходящих: ${e.message}")
            emptySet()
        }
    }

    // 2. Отклонение заявки (Самое дешевое - просто удалить документ)
    suspend fun declineRequest(requestId: String): Result<Unit> {
        return try {
            requestsCollection.document(requestId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun acceptRequest(requestId: String, senderId: String): Result<Unit> {
        val currentUserId = auth.currentUser?.uid ?: return Result.failure(Exception("Not auth"))

        return try {
            firestore.runBatch { batch ->
                // А) Удаляем заявку, чтобы она пропала из UI
                val requestRef = requestsCollection.document(requestId)
                batch.delete(requestRef)

                // Б) Добавляем отправителя в друзья текущему пользователю
                val currentUserRef = usersCollection.document(currentUserId)
                batch.update(currentUserRef, "friends", FieldValue.arrayUnion(senderId))

                // В) Добавляем текущего пользователя в друзья отправителю
                val senderRef = usersCollection.document(senderId)
                batch.update(senderRef, "friends", FieldValue.arrayUnion(currentUserId))
            }.await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FriendRequestRepo", "Ошибка принятия: ${e.message}")
            Result.failure(e)
        }
    }

    fun clearCache() {
        incomingRequestsCache.clear()
    }

}

