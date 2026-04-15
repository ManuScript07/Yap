package com.example.yap.ui.screen.notification

import androidx.lifecycle.ViewModel
import com.example.yap.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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
                    NotificationModel("1", R.drawable.avatar_1, "ReadHotChilliLiza", "Отправила Yap", timeAgo = "56 минут назад"),
                    NotificationModel("2", R.drawable.avatar_2, "ReadHotChilliLiza", "Отправила Yap", timeAgo = "56 минут назад"),
                    NotificationModel("3", R.drawable.avatar_3, "ReadHotChilliLiza", "Местоположение", hasLocation = true, timeAgo = "56 минут назад", isYapActive = true),
                    NotificationModel("4", R.drawable.avatar_4, "ReadHotChilliLiza", "Отправила ❤️", timeAgo = "1 час назад"),
                    NotificationModel("5", R.drawable.avatar_1, "ReadHotChilliLiza", "Всё хорошо?", timeAgo = "вчера"),
                    NotificationModel("6", R.drawable.avatar_2, "ReadHotChilliLiza", "Отправила Yap", timeAgo = "56 минут назад"),
                    NotificationModel("7", R.drawable.avatar_2, "ReadHotChilliLiza", "Отправила Yap", timeAgo = "56 минут назад"),
                    NotificationModel("8", R.drawable.avatar_1, "ReadHotChilliLiza", "Отправила Yap", timeAgo = "56 минут назад"),
                    NotificationModel("9", R.drawable.avatar_2, "ReadHotChilliLiza", "Отправила Yap", timeAgo = "56 минут назад"),
                    NotificationModel("10", R.drawable.avatar_3, "ReadHotChilliLiza", "Местоположение", hasLocation = true, timeAgo = "56 минут назад", isYapActive = true),
                    NotificationModel("11", R.drawable.avatar_4, "ReadHotChilliLiza", "Отправила ❤️", timeAgo = "1 час назад"),
                    NotificationModel("12", R.drawable.avatar_1, "ReadHotChilliLiza", "Всё хорошо?", timeAgo = "вчера"),
                    NotificationModel("13", R.drawable.avatar_2, "ReadHotChilliLiza", "Отправила Yap", timeAgo = "56 минут назад"),
                    NotificationModel("14", R.drawable.avatar_2, "ReadHotChilliLiza", "Отправила Yap", timeAgo = "56 минут назад"),
                    NotificationModel("15", R.drawable.avatar_1, "ReadHotChilliLiza", "Отправила Yap", timeAgo = "56 минут назад"),
                    NotificationModel("16", R.drawable.avatar_2, "ReadHotChilliLiza", "Отправила Yap", timeAgo = "56 минут назад"),
                    NotificationModel("17", R.drawable.avatar_3, "ReadHotChilliLiza", "Местоположение", hasLocation = true, timeAgo = "56 минут назад", isYapActive = true),
                    NotificationModel("18", R.drawable.avatar_4, "ReadHotChilliLiza", "Отправила ❤️", timeAgo = "1 час назад"),
                    NotificationModel("19", R.drawable.avatar_1, "ReadHotChilliLiza", "Всё хорошо?", timeAgo = "вчера"),
                    NotificationModel("20", R.drawable.avatar_2, "ReadHotChilliLiza", "Отправила Yap", timeAgo = "56 минут назад"),
                    NotificationModel("21", R.drawable.avatar_2, "ReadHotChilliLiza", "Отправила Yap", timeAgo = "56 минут назад")
                )
            )
        }
    }

    fun deleteNotification(id: String) {
        _state.update { currentState ->
            currentState.copy(
                notifications = currentState.notifications.filter { it.id != id }
            )
        }
    }

    fun muteNotification(id: String) {
        // Логика отключения уведомлений
    }
}