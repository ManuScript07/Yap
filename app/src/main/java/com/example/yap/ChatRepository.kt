package com.example.yap


import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await


class ChatRepository(private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()) {

    private val messagesCollection = firestore.collection("messages")

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
    fun observeUserMessages(currentUserId: String): Flow<List<MessageEntity>> = callbackFlow {
        // Примечание: Для сложного OR-запроса в Firestore может потребоваться индекс.
        // Пока сделаем подписку на входящие (уведомления)
        val subscription = messagesCollection
            .whereEqualTo("receiverId", currentUserId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val messages = snapshot.documents.mapNotNull { doc ->
                        // 1. Превращаем данные в объект
                        val message = doc.toObject(MessageEntity::class.java)

                        // 2. ВРУЧНУЮ устанавливаем ID из документа, чтобы он не был ""
                        message?.copy(id = doc.id)
                    }
                    trySend(messages).isSuccess
                }
            }

        awaitClose { subscription.remove() } // Отписываемся, когда ViewModel умирает
    }
}