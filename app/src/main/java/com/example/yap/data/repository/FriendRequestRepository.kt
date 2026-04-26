package com.example.yap.data.repository

import android.util.Log
import com.example.yap.data.model.FriendRequestEntity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.tasks.await

class FriendRequestRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    private val requestsCollection = firestore.collection("friend_requests")

    // RAM-кэш для отправленных заявок. Хранит receiverId, которым мы уже кинули инвайт.
    // Это спасет от спам-кликов и лишних чтений базы.
    private val sessionSentRequests = MutableStateFlow<Set<String>>(emptySet())

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
    suspend fun sendRequest(receiverId: String): Result<String> {
        val currentUserId = auth.currentUser?.uid ?: return Result.failure(Exception("Пользователь не авторизован"))

        // Защита от дублей на стороне клиента
        if (sessionSentRequests.value.contains(receiverId)) {
            return Result.failure(Exception("Заявка уже отправлена"))
        }

        return try {
            val documentRef = requestsCollection.document()
            val request = FriendRequestEntity(
                id = documentRef.id,
                senderId = currentUserId,
                receiverId = receiverId,
                timestamp = System.currentTimeMillis(),
                status = "pending"
            )

            // Отправляем в Firebase
            documentRef.set(request).await()

            // Сразу добавляем в кэш
            sessionSentRequests.update { it + receiverId }

            Result.success(documentRef.id)
        } catch (e: Exception) {
            Log.e("FriendRequestRepo", "Ошибка отправки заявки: ${e.message}")
            Result.failure(e)
        }
    }
}