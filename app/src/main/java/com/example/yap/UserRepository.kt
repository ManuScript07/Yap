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

        // Сразу отправляем текущее состояние
        trySend(auth.currentUser)

        awaitClose {
            auth.removeAuthStateListener(listener)
        }
    }.distinctUntilChanged() // Чтобы не триггерить UI на одинаковые события
    // Получить текущего ID
    val currentUserId: String?
        get() = FirebaseAuth.getInstance().currentUser?.uid

    // 1. Получить данные пользователя по ID (замена cachedUsers)
//    suspend fun getUserProfile(userId: String): UserItem? {
//        return try {
//            val snapshot = usersCollection.document(userId).get().await()
//            if (!snapshot.exists()) {
//                Log.e("NAV_DEBUG", "Document for $userId does not exist!")
//                return null
//            }
//            // Маппим документ в UserItem
//            val name = snapshot.getString("name") ?: "Unknown"
//            UserItem(id = userId, name = name, isYapActive = false, avatarRes = R.drawable.avatar_1)
//            // Позже заменишь avatarRes на загрузку картинки по URL
//        } catch (e: Exception) {
//            Log.e("NAV_DEBUG", "Error loading profile for $userId: ${e.message}")
//            null
//        }
//    }

    suspend fun getUsersByIds(ids: List<String>): List<UserItem> {
        if (ids.isEmpty()) return emptyList()

        val uniqueIds = ids.distinct()
        val currentCache = profileCache.value

        // 1. Находим только те ID, которых реально нет в памяти (RAM)
        val idsToLoad = uniqueIds.filter { !currentCache.containsKey(it) }

        // 2. Если все в кэше, сразу возвращаем результат
        if (idsToLoad.isEmpty()) {
            return uniqueIds.mapNotNull { currentCache[it] }
        }

        return try {
            // 3. РАЗБИВАЕМ список на куски по 30 (ЧАНКИ)
            // chunked(30) превращает [1..40] в [[1..30], [31..40]]
            val newlyLoadedUsers = idsToLoad.chunked(30).flatMap { chunk ->
                val snapshot = usersCollection
                    .whereIn(FieldPath.documentId(), chunk) // Теперь тут всегда <= 30
                    .get(Source.CACHE)
                    .await()

                snapshot.documents.mapNotNull { doc ->
                    val name = doc.getString("name") ?: "Unknown"
                    UserItem(id = doc.id, name = name, isYapActive = false, avatarRes = R.drawable.avatar_1)
                }
            }

            // 4. Обновляем кэш памяти новыми бойцами
            if (newlyLoadedUsers.isNotEmpty()) {
                profileCache.update { it + newlyLoadedUsers.associateBy { u -> u.id } }
            }

            // 5. Возвращаем полный список (старые + только что загруженные)
            val finalCache = profileCache.value
            uniqueIds.mapNotNull { finalCache[it] }

        } catch (e: Exception) {
            Log.e("UserRepository", "Batch load failed: ${e.message}")
            uniqueIds.mapNotNull { currentCache[it] }
        }
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
        val updateOperation = if (mute) {
            FieldValue.arrayUnion(targetUserId)
        } else {
            FieldValue.arrayRemove(targetUserId)
        }
        usersCollection.document(uid).update("mutedUsers", updateOperation).await()
    }

    // 4. Слушать свой профиль (чтобы реактивно обновлять списки мутов и контактов)
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeMyProfile(): Flow<DocumentSnapshot?> {
        return myProfileSharedFlow ?: currentUserFlow.flatMapLatest { user ->
            val uid = user?.uid
            if (uid == null) {
                flowOf(null)
            } else {
                // Явно указываем тип данных в callbackFlow
                callbackFlow<DocumentSnapshot?> {
                    Log.d("FIREBASE_TEST", "!!! СЛУШАТЕЛЬ ПРОФИЛЯ СОЗДАН !!!")
                    val listener = usersCollection.document(uid).addSnapshotListener { snapshot, _ ->
                        // Теперь ошибки "Nothing?" не будет
                        trySend(snapshot)
                    }
                    awaitClose {
                        Log.e("FIREBASE_TEST", "--- СЛУШАТЕЛЬ ПРОФИЛЯ ЗАКРЫТ ---")
                        listener.remove()
                    }
                }
            }
        }.distinctUntilChanged { old, new ->
            if (old == null || new == null) return@distinctUntilChanged old == new

            // Используем getStringList или просто заменяем [] на .get("field")
            // чтобы избежать ложной ошибки API 26 (конфликт с Regex Matcher)
            val oldQuick = old.get("quickList") as? List<*>
            val newQuick = new.get("quickList") as? List<*>
            val oldMuted = old.get("mutedUsers") as? List<*>
            val newMuted = new.get("mutedUsers") as? List<*>

            oldQuick == newQuick && oldMuted == newMuted
        }.shareIn(
            scope = repositoryScope,
            started = SharingStarted.WhileSubscribed(5000),
            replay = 1
        ).also {
            myProfileSharedFlow = it
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