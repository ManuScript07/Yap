package com.example.yap

import android.util.Log
import com.example.yap.data.model.UserItem
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await

class UserRepository(private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()) {
    val usersCollection = firestore.collection("users")

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
    suspend fun getUserProfile(userId: String): UserItem? {
        return try {
            val snapshot = usersCollection.document(userId).get().await()
            if (!snapshot.exists()) {
                Log.e("NAV_DEBUG", "Document for $userId does not exist!")
                return null
            }
            // Маппим документ в UserItem
            val name = snapshot.getString("name") ?: "Unknown"
            UserItem(id = userId, name = name, isYapActive = false, avatarRes = R.drawable.avatar_1)
            // Позже заменишь avatarRes на загрузку картинки по URL
        } catch (e: Exception) {
            Log.e("NAV_DEBUG", "Error loading profile for $userId: ${e.message}")
            null
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
    fun observeMyProfile(): Flow<DocumentSnapshot?> = currentUserFlow.flatMapLatest { user ->
        val uid = user?.uid
        if (uid == null) {
            flowOf(null)
        } else {
            callbackFlow {
                val listener = usersCollection.document(uid).addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e("UserRepository", "Snapshot error", error)
                    } else {
                        trySend(snapshot)
                    }
                }
                awaitClose { listener.remove() }
            }
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