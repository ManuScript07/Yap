package com.example.yap


import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.tasks.await


class ChatRepository(private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()) {

    private val messagesCollection = firestore.collection("messages")
    // при выходе из аккаунта очистить
    private val messagesCache = java.util.concurrent.ConcurrentHashMap<String, Flow<List<MessageEntity>>>()

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 1. Отправка сообщения (возвращает сгенерированный ID документа)
    suspend fun sendMessage(message: MessageEntity): Result<String> {
        return try {
            val documentRef = messagesCollection.document() // Создаем пустой документ для генерации ID
            val messageWithId = message.copy(id = documentRef.id)

            documentRef.set(messageWithId).await()
            Result.success(documentRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 2. Обновление текста (для расшифровки голоса)
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
        Log.d("FIREBASE_TEST", "!!! РЕАЛЬНЫЙ ЗАПРОС К FIREBASE СОЗДАН !!!")
        val query = messagesCollection
            .whereEqualTo("receiverId", currentUserId)
            .whereEqualTo("visibleForReceiver", true)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(50)

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
            messagesCache.remove(currentUserId)
        }
    }

    // 2. Публичный "Горячий" поток (Экономит запросы)
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
        try {
            messagesCollection.document(messageId).update("visibleForReceiver", false).await()
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error hiding message", e)
        }
    }
}