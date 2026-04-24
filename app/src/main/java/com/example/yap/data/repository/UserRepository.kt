package com.example.yap.data.repository

import UserPreferences
import android.util.Log
import com.example.yap.R
import com.example.yap.data.manager.RemoteConfigManager
import com.example.yap.data.model.UserItem
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.messaging.FirebaseMessaging
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import io.ktor.websocket.WebSocketDeflateExtension.Companion.install
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.UUID
import kotlin.getValue

class UserRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val userPrefs: UserPreferences,
    private val configManager: RemoteConfigManager,
) {

    private val supabase by lazy {
        createSupabaseClient(
            supabaseUrl = configManager.supabaseUrl,
            supabaseKey = configManager.supabaseAnonKey
        ) {
            install(Storage)
        }
    }
    val usersCollection = firestore.collection("users")
    private val profileCache = MutableStateFlow<Map<String, UserItem>>(emptyMap())

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val fetchMutex = Mutex()

    // Кэш для потока профиля

    val currentUserFlow: Flow<FirebaseUser?> = callbackFlow {
        val auth = FirebaseAuth.getInstance()
        val listener = FirebaseAuth.AuthStateListener { fbAuth ->
            // Используем trySend().isSuccess для надежности
            trySend(fbAuth.currentUser)
        }
        auth.addAuthStateListener(listener)

        trySend(auth.currentUser)

        awaitClose {
            auth.removeAuthStateListener(listener)
        }
    }.distinctUntilChanged()

    val currentUserId: String?
        get() = FirebaseAuth.getInstance().currentUser?.uid


    suspend fun updateFcmTokenIfNeeded() {
        val userId = currentUserId ?: return

        try {
            // 1. Получаем свежий токен от сервиса Google
            val token = FirebaseMessaging.getInstance().token.await()

            // 2. Достаем последний сохраненный токен из DataStore (читаем первое значение из Flow)
            val lastSavedToken = userPrefs.lastFcmToken.first()

            // 3. Сравниваем
            if (token != lastSavedToken) {
                // Обновляем в Firestore
                usersCollection.document(userId)
                    .update("fcmToken", token)
                    .await()

                // Сохраняем в DataStore, чтобы не частить с запросами
                userPrefs.updateLastFcmToken(token)

                Log.d("FCM_TEST", "Token updated in Cloud and Local: $token")
            }
        } catch (e: Exception) {
            Log.e("FCM_TEST", "Error updating FCM token", e)
        }
    }


    suspend fun getUsersByIds(ids: List<String>): List<UserItem> {
        if (ids.isEmpty()) return emptyList()
        val tag = "UserRepository"
        val uniqueIds = ids.distinct()

        // 1. Быстрая проверка L1 (RAM)
        val initialMissing = uniqueIds.filter { !profileCache.value.containsKey(it) }
        if (initialMissing.isEmpty()) {
            return uniqueIds.mapNotNull { profileCache.value[it] }
        }

        Log.d(tag, "🔍 Ищем: ${initialMissing.size} чел. (жду мьютекс)")

        fetchMutex.withLock {
            val currentCache = profileCache.value
            val missingFromMemory = uniqueIds.filter { !currentCache.containsKey(it) }

            if (missingFromMemory.isEmpty()) {
                return uniqueIds.mapNotNull { profileCache.value[it] }
            }

            val newlyLoaded = mutableListOf<UserItem>()
            var networkSucceeded = false

            // 2. СНАЧАЛА ИДЕМ В СЕТЬ (L3) С ЖЕСТКИМ ТАЙМАУТОМ
            // Это решает проблему "Васи": мы всегда проверяем актуальность на сервере.
            // Таймаут в 2.5 секунды решает проблему "долгого оффлайна".
            try {
                Log.d(tag, "🌐 L3 (Network): Проверяем ${missingFromMemory.size} чел. (таймаут 2.5с)")

                withTimeout(2500) { // Если за 2.5 сек ответа нет - выкинет TimeoutCancellationException
                    missingFromMemory.chunked(30).forEach { chunk ->
                        val serverSnapshot = usersCollection
                            .whereIn(FieldPath.documentId(), chunk)
                            .get(Source.SERVER)
                            .await()
                        serverSnapshot.documents.forEach { doc -> newlyLoaded.add(mapToUser(doc)) }
                    }
                }
                networkSucceeded = true
                Log.d(tag, "✅ L3 (Network): Успешно")

            } catch (e: Exception) {
                Log.w(tag, "⚠️ Сеть недоступна или таймаут. Ошибка: ${e.message}")
                networkSucceeded = false
            }

            // 3. СПАСАТЕЛЬНЫЙ КРУГ: ДИСКОВЫЙ КЭШ (L2)
            // Если сеть не ответила (networkSucceeded == false), вытягиваем данные из кэша
            if (!networkSucceeded) {
                try {
                    Log.d(tag, "💾 L2 (Disk): Пытаемся достать ${missingFromMemory.size} чел. из кэша индивидуально")

                    // Используем обычный цикл вместо whereIn для L2.
                    // Прямое обращение к документу через .document(id).get(Source.CACHE)
                    // работает стабильнее, если локальные индексы еще не прогрузились.
                    missingFromMemory.forEach { id ->
                        try {
                            val docSnapshot = usersCollection.document(id).get(Source.CACHE).await()
                            if (docSnapshot.exists()) {
                                newlyLoaded.add(mapToUser(docSnapshot))
                            }
                        } catch (e: Exception) {
                            // Если конкретного юзера нет в кэше — просто идем дальше
                            Log.v(tag, "🔍 ID $id не найден в локальном кэше")
                        }
                    }
                    Log.d(tag, "💾 L2 (Disk): Итого найдено ${newlyLoaded.size} из ${missingFromMemory.size}")
                } catch (e: Exception) {
                    Log.e(tag, "❌ Критическая ошибка L2 кэша: ${e.message}")
                }
            }

            // 4. ЛОГИКА "ВАСИ" (Заглушки для удаленных)
            // Сработает ТОЛЬКО если сервер успешно ответил, но кого-то не вернул
            val placeholders = mutableListOf<UserItem>()
            if (networkSucceeded) {
                val foundIds = newlyLoaded.map { it.id }.toSet()
                val deletedUserIds = missingFromMemory.filter { !foundIds.contains(it) }

                if (deletedUserIds.isNotEmpty()) {
                    Log.w(tag, "🗑️ Обнаружены удаленные (заглушки): ${deletedUserIds.size} чел.")
                    deletedUserIds.forEach { id ->
                        placeholders.add(
                            UserItem(
                                id = id,
                                name = "Deleted User",
                                username = "deleted",
                                avatarUrl = null
                            )
                        )
                    }
                }
            }

            // 5. Сохраняем всё в RAM
            if (newlyLoaded.isNotEmpty() || placeholders.isNotEmpty()) {
                val newEntries = (newlyLoaded + placeholders).associateBy { it.id }
                profileCache.update { it + newEntries }
            }
        }

        // 6. Возвращаем результат
        return uniqueIds.mapNotNull { profileCache.value[it] }
    }


    // Выносим маппинг, чтобы не дублировать логику
    private fun mapToUser(doc: DocumentSnapshot): UserItem {
        return UserItem(
            id = doc.id,
            name = doc.getString("name") ?: "Unknown",
            username = doc.getString("username") ?: "",
            avatarUrl = doc.getString("avatarUrl"), // Теперь берем ссылку из базы!
            bio = doc.getString("bio") ?: "",
            dobTimestamp = doc.getLong("dobTimestamp"),
            showOnlyDay = doc.getBoolean("showOnlyDay") ?: false,
            email = doc.getString("email") ?: "",
            isYapActive = doc.getBoolean("isYapActive") ?: false,
            isMuted = doc.getBoolean("isMuted") ?: false,
            quickList = doc.get("quickList") as? List<String> ?: emptyList(),
            mutedUsers = doc.get("mutedUsers") as? List<String> ?: emptyList()
        )
    }

    // 2. Добавить/удалить пользователя из "Быстрого списка" (Quick List)
    suspend fun toggleQuickList(targetUserId: String, add: Boolean) {
        val uid = currentUserId ?: return
        val updateOperation = if (add) {
            FieldValue.arrayUnion(targetUserId)
        } else {
            FieldValue.arrayRemove(targetUserId)
        }
        usersCollection.document(uid).update("quickList", updateOperation).await()
    }

    // 3. Замутить / размутить пользователя
    suspend fun toggleMute(targetUserId: String, mute: Boolean) {
        val uid = currentUserId ?: return
        Log.d("UserRepository", "!!! ТРАНЗАКЦИЯ МУТА: target=$targetUserId, action=${if (mute) "ADD" else "REMOVE"} !!!")
        val updateOperation = if (mute) {
            FieldValue.arrayUnion(targetUserId)
        } else {
            FieldValue.arrayRemove(targetUserId)
        }
        try {
            usersCollection.document(uid).update("mutedUsers", updateOperation).await()
            Log.d("UserRepository", "--- МУТ УСПЕШНО ОБНОВЛЕН в Cloud Firestore ---")
        } catch (e: Exception) {
            Log.e("UserRepository", "ОШИБКА МУТА для $targetUserId: ${e.message}", e)
        }
    }


    // 1. Выносим поток в ленивое свойство (Property), чтобы он не пересоздавался при каждом вызове функции
    @OptIn(ExperimentalCoroutinesApi::class)
    private val profileFlow: Flow<UserItem?> by lazy {
        currentUserFlow.flatMapLatest { user ->
            val uid = user?.uid
            if (uid == null) {
                Log.d("UserRepository", "User is null, profile flow emitted null")
                flowOf(null)
            } else {
                // ФИКС 1: Явно указываем тип данных <DocumentSnapshot?>,
                // чтобы компилятор не вывел Nothing?
                callbackFlow<UserItem?> {
                    Log.d("FIREBASE_TEST", "!!! СЛУШАТЕЛЬ ПРОФИЛЯ СОЗДАН для $uid !!!")

                    val registration = usersCollection.document(uid)
                        .addSnapshotListener { snapshot, error ->
                            if (error != null) {
                                Log.e("UserRepository", "SnapshotListener error: ${error.message}")
                                return@addSnapshotListener
                            }
                            if (snapshot != null && snapshot.exists()) {
                                trySend(mapToUser(snapshot))
                            }
                            else{
                                trySend(null)
                                Log.e("UserRepository", "Failed to send profile snapshot to flow")
                            }
                        }

                    awaitClose {
                        Log.e("FIREBASE_TEST", "--- СЛУШАТЕЛЬ ПРОФИЛЯ ЗАКРЫТ ---")
                        registration.remove()
                    }
                }
            }
        }
        .distinctUntilChanged()
        .shareIn(
            scope = repositoryScope,
            started = SharingStarted.WhileSubscribed(5000),
            replay = 1
        )
    }

    // 2. Публичный метод теперь просто возвращает готовую ссылку на поток
    fun observeMyProfile(): Flow<UserItem?> = profileFlow

    suspend fun uploadAvatar(photoBytes: ByteArray, userId: String): Result<String> {
        return withContext(Dispatchers.IO) {
            var lastException: Exception? = null
            repeat(3) { attempt ->
                try {
                    val fileName = "avatar_${UUID.randomUUID()}.jpg"
                    val fullPath = "$userId/$fileName"

                    // Предполагается, что бакет называется "avatars" (замени на свой)
                    val bucketName = "avatars"

                    supabase.storage.from(bucketName).upload(
                        path = fullPath,
                        data = photoBytes
                    ) { upsert = true }

                    val downloadUrl = "${configManager.supabaseUrl}/storage/v1/object/public/$bucketName/$fullPath"
                    return@withContext Result.success(downloadUrl)

                } catch (e: Exception) {
                    lastException = e
                    kotlinx.coroutines.delay((attempt + 1) * 1000L)
                }
            }
            Result.failure(lastException ?: Exception("Upload failed"))
        }
    }


    suspend fun signInAnonymously(): Boolean {
        return try {
            val result = FirebaseAuth.getInstance().signInAnonymously().await()
            val uid = result.user?.uid

            if (uid != null) {
                // Проверяем, есть ли такой профиль в базе
                val doc = usersCollection.document(uid).get().await()
                if (!doc.exists()) {
                    // Если нет — создаем начальные данные
                    val initialData = mapOf(
                        "name" to "User_${uid.take(4)}",
                        "quickList" to emptyList<String>(),
                        "mutedUsers" to emptyList<String>()
                    )
                    usersCollection.document(uid).set(initialData).await()
                }
                true
            } else false
        } catch (e: Exception) {
            Log.e("UserRepository", "Anonymous auth failed", e)
            false
        }
    }



    suspend fun completeUserRegistration(
        userId: String,
        email: String?,
        userData: Map<String, Any?>
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val docRef = usersCollection.document(userId)

                // Дополняем данные системными полями
                val finalData = userData.toMutableMap().apply {
                    put("email", email ?: "")
                    put("quickList", emptyList<String>())
                    put("mutedUsers", emptyList<String>())
                    put("createdAt", FieldValue.serverTimestamp()) // Используем время сервера
                }

                docRef.set(finalData).await()
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }


    suspend fun signInWithGoogle(idToken: String): AuthResult {
        return withContext(Dispatchers.IO) {
            try {
                withTimeout(15000L) {
                    val credential = GoogleAuthProvider.getCredential(idToken, null)
                    val result = FirebaseAuth.getInstance().signInWithCredential(credential).await()
                    val user = result.user ?: return@withTimeout AuthResult.Error("User is null")

                    val doc = usersCollection.document(user.uid).get().await()

                    if (!doc.exists()) {
                        // МЫ НИЧЕГО НЕ ПИШЕМ В БД ТУТ!
                        // Просто передаем имя из Гугла для предзаполнения анкеты
                        AuthResult.SuccessNewUser(defaultName = user.displayName ?: "")
                    } else {
                        AuthResult.SuccessExistingUser
                    }
                }
            } catch (e: Exception) {
                AuthResult.Error(e.localizedMessage ?: "Auth error")
            }
        }
    }
}