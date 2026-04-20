package com.example.yap

import android.util.Log
import com.example.yap.data.model.UserItem
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.tasks.await

class UserRepository(private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()) {
    val usersCollection = firestore.collection("users")
    private val profileCache = MutableStateFlow<Map<String, UserItem>>(emptyMap())

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Кэш для потока профиля
    private var myProfileSharedFlow: Flow<DocumentSnapshot?>? = null

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


    suspend fun getUsersByIds(ids: List<String>): List<UserItem> {
        if (ids.isEmpty()) return emptyList()

        val uniqueIds = ids.distinct()
        val currentCache = profileCache.value

        // 1. Проверяем оперативную память (L1)
        val idsNotInMemory = uniqueIds.filter { !currentCache.containsKey(it) }
        if (idsNotInMemory.isEmpty()) {
            return uniqueIds.mapNotNull { currentCache[it] }
        }

        return try {
            val loadedUsers = mutableListOf<UserItem>()

            // 2. Пытаемся достать недостающих из Дискового Кэша Firestore (L2)
            // Это быстро и бесплатно.
            idsNotInMemory.chunked(30).forEach { chunk ->
                val cacheSnapshot = usersCollection
                    .whereIn(FieldPath.documentId(), chunk)
                    .get(Source.CACHE)
                    .await()

                cacheSnapshot.documents.forEach { doc ->
                    loadedUsers.add(mapToUser(doc))
                }
            }

            // 3. Проверяем: всех ли нашли?
            val foundIds = loadedUsers.map { it.id }.toSet()
            val missingFromCache = idsNotInMemory.filter { !foundIds.contains(it) }

            // 4. Если кого-то нет в кэше (как после переустановки), идем на Сервер (L3)
            if (missingFromCache.isNotEmpty()) {
                Log.d("UserRepository", "В кэше нет ${missingFromCache.size} чел, запрос к сети...")
                missingFromCache.chunked(30).forEach { chunk ->
                    val serverSnapshot = usersCollection
                        .whereIn(FieldPath.documentId(), chunk)
                        .get(Source.SERVER) // Вот здесь мы платим чтение, но только 1 раз
                        .await()

                    serverSnapshot.documents.forEach { doc ->
                        loadedUsers.add(mapToUser(doc))
                    }
                }
            }

            // 5. Синхронно обновляем кэш в памяти
            if (loadedUsers.isNotEmpty()) {
                profileCache.update { it + loadedUsers.associateBy { u -> u.id } }
            }

            // 6. Собираем финальный список из актуального кэша
            val finalCache = profileCache.value
            uniqueIds.mapNotNull { finalCache[it] }

        } catch (e: Exception) {
            Log.e("UserRepository", "Ошибка при загрузке профилей: ${e.message}")
            // Если всё упало (нет сети), возвращаем хотя бы то, что было в памяти
            uniqueIds.mapNotNull { currentCache[it] }
        }
    }

    // Выносим маппинг, чтобы не дублировать логику
    private fun mapToUser(doc: DocumentSnapshot): UserItem {
        return UserItem(
            id = doc.id,
            name = doc.getString("name") ?: "Unknown",
            isYapActive = false,
            avatarRes = R.drawable.avatar_1
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
    private val profileFlow: Flow<DocumentSnapshot?> by lazy {
        currentUserFlow.flatMapLatest { user ->
            val uid = user?.uid
            if (uid == null) {
                Log.d("UserRepository", "User is null, profile flow emitted null")
                flowOf(null)
            } else {
                // ФИКС 1: Явно указываем тип данных <DocumentSnapshot?>,
                // чтобы компилятор не вывел Nothing?
                callbackFlow<DocumentSnapshot?> {
                    Log.d("FIREBASE_TEST", "!!! СЛУШАТЕЛЬ ПРОФИЛЯ СОЗДАН для $uid !!!")

                    val registration = usersCollection.document(uid)
                        .addSnapshotListener { snapshot, error ->
                            if (error != null) {
                                Log.e("UserRepository", "SnapshotListener error: ${error.message}")
                                return@addSnapshotListener
                            }
                            val result = trySend(snapshot)
                            if (result.isFailure) {
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
            .distinctUntilChanged { old, new ->
                if (old == null || new == null) return@distinctUntilChanged old == new

                val oldQuick = old["quickList"] as? List<*>
                val newQuick = new["quickList"] as? List<*>
                val oldMuted = old["mutedUsers"] as? List<*>
                val newMuted = new["mutedUsers"] as? List<*>

                oldQuick == newQuick && oldMuted == newMuted
            }
            .shareIn(
                scope = repositoryScope,
                started = SharingStarted.WhileSubscribed(5000),
                replay = 1
            )
    }

    // 2. Публичный метод теперь просто возвращает готовую ссылку на поток
    fun observeMyProfile(): Flow<DocumentSnapshot?> = profileFlow

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

    suspend fun signInWithGoogle(idToken: String): Boolean {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = FirebaseAuth.getInstance().signInWithCredential(credential).await()
            val user = result.user

            if (user != null) {
                val docRef = usersCollection.document(user.uid)
                val doc = docRef.get().await()

                if (!doc.exists()) {
                    val initialData = mapOf(
                        "name" to (user.displayName ?: "New User"),
                        "email" to user.email,
                        "quickList" to emptyList<String>(),
                        "mutedUsers" to emptyList<String>()
                    )
                    docRef.set(initialData).await()
                }
                true
            } else false
        } catch (e: Exception) {
            Log.e("UserRepository", "Google Auth failed", e)
            false
        }
    }
}