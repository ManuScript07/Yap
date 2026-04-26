package com.example.yap.data.repository


import android.util.Log
import com.example.yap.data.manager.RemoteConfigManager
import com.example.yap.data.model.MessageEntity
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import com.google.firebase.firestore.Query
import com.google.gson.Gson
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.UUID
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.ConcurrentHashMap


class ChatRepository(
    private val configManager: RemoteConfigManager,
    private val firestore: FirebaseFirestore,
    private val supabase: SupabaseClient,
    private val client: OkHttpClient,
    private val gson: Gson
) {


    private val messagesCollection = firestore.collection("messages")
    private val usersCollection = firestore.collection("users")


    // при выходе из аккаунта очистить
    private val messagesCache = ConcurrentHashMap<String, Flow<List<MessageEntity>>>()

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 1. Отправка сообщения (возвращает сгенерированный ID документа)
    suspend fun sendMessage(message: MessageEntity): Result<String> {
        return try {
            val documentRef = messagesCollection.document() // Создаем пустой документ для генерации ID
            val messageWithId = message.copy(id = documentRef.id)

            documentRef.set(messageWithId).await()

            sendPushNotification(
                senderId = messageWithId.senderId,
                receiverId = messageWithId.receiverId,
                text = messageWithId.text
            )

            Result.success(documentRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun sendPushNotification(senderId: String, receiverId: String, text: String?) {
        withContext(Dispatchers.IO) {
            try {
                // А) Получаем токен ПОЛУЧАТЕЛЯ
                val receiverDoc = usersCollection.document(receiverId).get().await()

                // Без защиты, такие проверки надо делать на сервере
                val mutedUsers = receiverDoc.get("mutedUsers") as? List<String> ?: emptyList()
                if (mutedUsers.contains(senderId)) {
                    Log.d("PUSH_SENDER", "Уведомление отменено: получатель $receiverId замьютил отправителя $senderId")
                    return@withContext // Просто выходим, не дергая сервер Render
                }

                val fcmToken = receiverDoc.getString("fcmToken")

                if (fcmToken.isNullOrEmpty()) {
                    Log.e("PUSH_SENDER", "У пользователя $receiverId нет токена. Пуш не отправлен.")
                    return@withContext
                }

                // Б) Получаем имя ОТПРАВИТЕЛЯ (чтобы красиво показать в уведомлении)
                val senderDoc = usersCollection.document(senderId).get().await()
                val senderName = senderDoc.getString("name") ?: "Новое сообщение"

                val jsonMap = mapOf(
                    "fcmToken" to fcmToken,
                    "senderName" to senderName,
                    "text" to text
                )
                val jsonString = gson.toJson(jsonMap)

                // Г) Отправляем POST запрос на твой Render сервер
                val requestBody = jsonString.toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder()
                    .url("https://yap-server.onrender.com/send-notification")
                    .post(requestBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        Log.d("PUSH_SENDER", "Успех! Сервер Render принял запрос.")
                    } else {
                        Log.e("PUSH_SENDER", "Ошибка Render: ${response.code} ${response.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e("PUSH_SENDER", "Сбой при отправке пуша", e)
            }
        }
    }

    suspend fun uploadVoiceFile(file: File, senderId: String): Result<String> {
        // Делаем до 3 попыток с нарастающей задержкой
        var lastException: Exception? = null

        repeat(3) { attempt ->
            try {
                if (!file.exists()) return Result.failure(Exception("Файл не найден"))

                val fileName = "${UUID.randomUUID()}.m4a"
                val fullPath = "$senderId/$fileName"

                val bytes = file.readBytes()

                // Сама загрузка
                supabase.storage.from(configManager.supabaseBucket).upload(
                    path = fullPath,
                    data = bytes
                ) { upsert = false }

                // Если дошли сюда — успех!
                val downloadUrl = "${configManager.supabaseUrl}/storage/v1/object/public/${configManager.supabaseBucket}/$fullPath"
                return Result.success(downloadUrl)

            } catch (e: Exception) {
                lastException = e
                Log.w("ChatRepository", "Попытка ${attempt + 1} не удалась: ${e.message}")
                Log.e("ChatRepository", "Ошибка Supabase: ${e.stackTraceToString()}")
                // Ждем перед следующей попыткой (1с, 2с...)
                kotlinx.coroutines.delay((attempt + 1) * 1000L)
            }
        }

        return Result.failure(lastException ?: Exception("Неизвестная ошибка загрузки"))
    }

    suspend fun updateMessageText(messageId: String, transcribedText: String) {
        try {
            messagesCollection.document(messageId).update("text", transcribedText).await()
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error updating message", e)
        }
    }

    // 3. Подписка на сообщения в реальном времени (для страницы уведомлений)
    // Слушаем сообщения, где текущий юзер является получателем ИЛИ отправителем
    // 1. Приватный низкоуровневый источник (Холодный поток)
    private fun createMessagesFlow(currentUserId: String): Flow<List<MessageEntity>> = callbackFlow {
        Log.d("FIREBASE_TEST", "!!! РЕАЛЬНЫЙ ЗАПРОС К FIREBASE СОЗДАН для $currentUserId !!!")
        val query = messagesCollection
            .whereEqualTo("receiverId", currentUserId)
            .whereEqualTo("visibleForReceiver", true)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(30)

        val subscription = query.addSnapshotListener { snapshot, error ->

            if (error != null) {
                Log.e("ChatRepository", "Firestore Error: ${error.message}. Check if index is created.")
                return@addSnapshotListener
            }

            snapshot?.let { querySnapshot ->
                Log.d("FIREBASE_TEST", "Пришли свежие данные из облака")
                val messages = querySnapshot.documents.mapNotNull { doc ->
                    val message = doc.toObject(MessageEntity::class.java)
                    // Вручную присваиваем ID документа
                    message?.copy(id = doc.id)
                }
                trySend(messages)
            }
        }

        awaitClose {
            Log.e("FIREBASE_TEST", "--- СОЕДИНЕНИЕ ЗАКРЫТО ---")
            subscription.remove()
//            messagesCache.remove(currentUserId)
        }
    }


    fun observeUserMessages(currentUserId: String): Flow<List<MessageEntity>> {
        return messagesCache.getOrPut(currentUserId) {
            createMessagesFlow(currentUserId)
                .shareIn(
                    scope = repositoryScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    replay = 1
                )
        }
    }

    suspend fun hideMessageForReceiver(messageId: String) {
        Log.d("ChatRepository", "!!! ЗАПРОС НА СКРЫТИЕ СООБЩЕНИЯ: id=$messageId !!!")
        try {
            messagesCollection.document(messageId)
//                .update("visibleForReceiver", false)
                .delete()// delete()
                .await()
            Log.d("ChatRepository", "--- СООБЩЕНИЕ $messageId ТЕПЕРЬ СКРЫТО (visibleForReceiver = false) ---")
        } catch (e: Exception) {
            Log.e("ChatRepository", "ОШИБКА при скрытии сообщения $messageId: ${e.message}", e)
        }
    }

    fun clearCacheOnLogout() {
        firestore.terminate()
        firestore.clearPersistence()
    }


}