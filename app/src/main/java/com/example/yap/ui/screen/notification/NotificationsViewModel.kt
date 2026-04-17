package com.example.yap.ui.screen.notification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.yap.R
import com.example.yap.data.model.UserItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NotificationsViewModel : ViewModel() {

    private val _state = MutableStateFlow(NotificationsUiState())
    val state: StateFlow<NotificationsUiState> = _state.asStateFlow()

    init {
        loadNotifications()
    }

    private fun loadNotifications() {
        // Заглушки как на твоем скриншоте
        _state.update {
            it.copy(
                notifications = listOf(
                    NotificationItemModel(
                        id = 1,
                        user = UserItem(1, "ReadHotChilliLiza", false, R.drawable.avatar_1),
                        messageText = "Отправила Yap",
                        timestamp = "12:00",
                        timeAgo = "56 минут назад"
                    ),
                    NotificationItemModel(
                        id = 2,
                        user = UserItem(2, "ReadHotChilliLiza", true, R.drawable.avatar_1),
                        messageText = "Всё хорошо?",
                        hasLocation = true, // И текст, и локация
                        timestamp = "11:30",
                        timeAgo = "вчера"
                    ),
                    NotificationItemModel(
                        id = 3,
                        user = UserItem(1, "ReadHotChilliLiza", false, R.drawable.avatar_1),
                        messageText = "Отправила Yap",
                        timestamp = "12:00",
                        timeAgo = "56 минут назад"
                    ),
                    NotificationItemModel(
                        id = 4,
                        user = UserItem(2, "ReadHotChilliLiza", true, R.drawable.avatar_1),
                        messageText = "Всё хорошо?",
                        hasLocation = true, // И текст, и локация
                        timestamp = "11:30",
                        timeAgo = "вчера"
                    ),
                    NotificationItemModel(
                        id = 5,
                        user = UserItem(1, "ReadHotChilliLiza", false, R.drawable.avatar_1),
                        messageText = "Отправила Yap",
                        timestamp = "12:00",
                        timeAgo = "56 минут назад"
                    ),
                    NotificationItemModel(
                        id = 6,
                        user = UserItem(2, "ReadHotChilliLiza", true, R.drawable.avatar_1),
                        messageText = "Всё хорошо?",
                        hasLocation = true, // И текст, и локация
                        timestamp = "11:30",
                        timeAgo = "вчера"
                    ),
                    NotificationItemModel(
                        id = 7,
                        user = UserItem(1, "ReadHotChilliLiza", false, R.drawable.avatar_1),
                        messageText = "Отправила Yap",
                        timestamp = "12:00",
                        timeAgo = "56 минут назад"
                    ),
                    NotificationItemModel(
                        id = 8,
                        user = UserItem(2, "ReadHotChilliLiza", true, R.drawable.avatar_1),
                        messageText = "Всё хорошо?",
                        hasLocation = true, // И текст, и локация
                        timestamp = "11:30",
                        timeAgo = "вчера"
                    )
                )
            )
        }
    }

    fun deleteNotification(id: Int) {
        _state.update { currentState ->
            currentState.copy(
                notifications = currentState.notifications.filter { it.id != id }
            )
        }
    }

    fun muteNotification(notificationId: Int) {
        _state.update { currentState ->
            // 1. Находим уведомление, по которому кликнули, чтобы узнать ID пользователя
            val targetNotification = currentState.notifications.find { it.id == notificationId }
            val targetUserId = targetNotification?.user?.id

            if (targetUserId != null) {
                // Новое состояние (инвертируем текущее)
                val newMuteState = !targetNotification.user.isMuted

                // 2. Обновляем ВСЕ уведомления этого пользователя в списке
                currentState.copy(
                    notifications = currentState.notifications.map { notif ->
                        if (notif.user.id == targetUserId) {
                            notif.copy(user = notif.user.copy(isMuted = newMuteState))
                        } else {
                            notif
                        }
                    }
                )
            } else {
                currentState
            }
        }
    }

    // В NotificationsViewModel добавить:
    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isRefreshing = true) }
            loadNotifications() // Твоя функция загрузки
            _state.update { it.copy(isRefreshing = false) }
        }
    }
}