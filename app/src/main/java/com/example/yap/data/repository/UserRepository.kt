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
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import io.ktor.websocket.WebSocketDeflateExtension.Companion.install
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
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
import kotlin.coroutines.cancellation.CancellationException
import kotlin.getValue

class UserRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val userPrefs: UserPreferences,
    private val configManager: RemoteConfigManager,
    private val supabase: SupabaseClient
) {


    val usersCollection = firestore.collection("users")

    fun getCurrentUser(): FirebaseUser? = auth.currentUser
    val currentUserId: String?
        get() = auth.currentUser?.uid
    private val profileCache = MutableStateFlow<Map<String, UserItem>>(emptyMap())

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val fetchMutex = Mutex()

    // Кэш для потока профиля

    val currentUserFlow: Flow<FirebaseUser?> = callbackFlow {
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
            userCode = doc.getString("userCode") ?: "",
            avatarUrl = doc.getString("avatarUrl"), // Теперь берем ссылку из базы!
            bio = doc.getString("bio") ?: "",
            dobTimestamp = doc.getLong("dobTimestamp"),
            showOnlyDay = doc.getBoolean("showOnlyDay") ?: false,
            email = doc.getString("email") ?: "",
            isYapActive = doc.getBoolean("isYapActive") ?: false,
            isMuted = doc.getBoolean("isMuted") ?: false,
            quickList = doc.get("quickList") as? List<String> ?: emptyList(),
            mutedUsers = doc.get("mutedUsers") as? List<String> ?: emptyList(),
            friends = doc.get("friends") as? List<String> ?: emptyList(),
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

    suspend fun removeFriend(friendId: String) {
        val uid = currentUserId ?: throw IllegalStateException("Пользователь не авторизован")

        try {
            val batch = firestore.batch()

            val currentUserRef = usersCollection.document(uid)
            val friendRef = usersCollection.document(friendId)

            // 1. Удаляем друга у себя
            batch.update(currentUserRef, "friends", FieldValue.arrayRemove(friendId))

            // 2. Удаляем себя у друга
            batch.update(friendRef, "friends", FieldValue.arrayRemove(uid))

            // Выполняем обе операции одновременно
            batch.commit().await()
            Log.d("UserRepository", "Друг $friendId успешно удален у обоих пользователей")

        } catch (e: Exception) {
            Log.e("UserRepository", "Ошибка при удалении друга (Батч)", e)
            throw e // Пробрасываем ошибку во ViewModel для отката UI
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
            try {
                // Общий таймаут на всю операцию (включая ретраи)
                withTimeout(45000L) {
                    val fileName = "avatar_${UUID.randomUUID()}.jpg"
                    val fullPath = "$userId/$fileName"
                    val bucketName = "avatars"

                    Log.d("RegLog", "Starting upload to Supabase: $fullPath (${photoBytes.size} bytes)")

                    var lastException: Exception? = null

                    // Ретраи внутри таймаута
                    for (attempt in 1..2) {
                        try {
                            Log.d("RegLog", "Upload attempt #$attempt...")

                            supabase.storage.from(bucketName).upload(
                                path = fullPath,
                                data = photoBytes
                            ) {
                                upsert = true
                            }

                            // Если дошли сюда — успех
                            val downloadUrl = "${configManager.supabaseUrl}/storage/v1/object/public/$bucketName/$fullPath"
                            Log.d("RegLog", "Upload successful: $downloadUrl")
                            return@withTimeout Result.success(downloadUrl)

                        } catch (e: Exception) {
                            // Если корутина была отменена (например, юзер закрыл экран),
                            // нужно пробросить CancellationException дальше
                            if (e is CancellationException) throw e

                            lastException = e
                            Log.w("RegLog", "Attempt #$attempt failed: ${e.message}")

                            if (attempt < 2) {
                                delay(1000L * attempt) // Прогрессирующая задержка (1с, потом ошибка)
                            }
                        }
                    }

                    // Если циклы кончились и не вышли через return
                    Result.failure(lastException ?: Exception("Unknown upload error"))
                }
            } catch (e: TimeoutCancellationException) {
                Log.e("RegLog", "Upload timed out after 45s")
                Result.failure(Exception("Сервер долго не отвечает. Проверьте соединение или VPN."))
            } catch (e: CancellationException) {
                // Важно: не перехватывать отмену корутины как ошибку результата
                throw e
            } catch (e: Exception) {
                Log.e("RegLog", "Fatal upload error: ${e.javaClass.simpleName} - ${e.message}")
                Result.failure(e)
            }
        }
    }



    suspend fun completeUserRegistration(
        userId: String,
        email: String?,
        userData: Map<String, Any?>
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                // Берем userCode из данных, которые пришли из ViewModel
                val userCode = userData["userCode"] as? String
                    ?: return@withContext Result.failure(Exception("UserCode is missing"))

                val userDocRef = usersCollection.document(userId)

                // Используем firestore, который у тебя уже есть в конструкторе
                val codeRegistryRef = firestore.collection("user_codes").document(userCode)

                firestore.runTransaction { transaction ->
                    // 1. Проверяем реестр кодов
                    val codeSnapshot = transaction.get(codeRegistryRef)
                    if (codeSnapshot.exists()) {
                        throw Exception("Этот код уже занят. Попробуйте другой.")
                    }

                    // 2. Подготовка данных пользователя
                    val finalUserData = userData.toMutableMap().apply {
                        put("email", email ?: "")
                        put("quickList", emptyList<String>())
                        put("mutedUsers", emptyList<String>())
                        put("friends", emptyList<String>())
                        put("createdAt", FieldValue.serverTimestamp())
                    }

                    // 3. Атомарная запись в две коллекции
                    transaction.set(userDocRef, finalUserData)
                    transaction.set(codeRegistryRef, mapOf("ownerId" to userId))
                }.await()

                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun generateUniqueUserCode(): Result<String> {
        return withContext(Dispatchers.IO) {
            val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
            val codeRegistry = firestore.collection("user_codes")
            var attempts = 0
            val maxAttempts = 5

            try {
                while (attempts < maxAttempts) {
                    // 1. Генерируем 8-значный код
                    val candidateCode = (1..8)
                        .map { chars.random() }
                        .joinToString("")

                    // 2. Проверяем наличие документа в реестре кодов по его ID
                    // Это самая быстрая и дешевая операция (get document by ID)
                    val docSnapshot = codeRegistry.document(candidateCode)
                        .get(Source.SERVER) // Форсируем сервер, чтобы исключить коллизии
                        .await()

                    if (!docSnapshot.exists()) {
                        // Код свободен
                        Log.d("UserRepository", "Generated unique code: $candidateCode (attempts: ${attempts + 1})")
                        return@withContext Result.success(candidateCode)
                    }

                    attempts++
                    Log.w("UserRepository", "Collision detected for code: $candidateCode. Retrying...")
                }

                Result.failure(Exception("Превышено количество попыток генерации уникального кода"))
            } catch (e: Exception) {
                Log.e("UserRepository", "Error generating user code: ${e.message}")
                Result.failure(e)
            }
        }
    }


    suspend fun signInWithGoogle(idToken: String): AuthResult {
        return withContext(Dispatchers.IO) {
            try {
                withTimeout(15000L) {
                    val credential = GoogleAuthProvider.getCredential(idToken, null)
                    val result = auth.signInWithCredential(credential).await()
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