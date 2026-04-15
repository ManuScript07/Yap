package com.example.yap.ui.screen.notification

import androidx.compose.runtime.Immutable

@Immutable
data class NotificationModel(
    val id: String,
    val avatarRes: Int, // R.drawable...
    val nickname: String,
    val messageText: String, // "Отправила Yap", "Всё хорошо?", "Отправила ❤️"
    val hasLocation: Boolean = false,
    val timeAgo: String,
    val isYapActive: Boolean = false // Желтая или серая кнопка
)

data class NotificationsUiState(
    val notifications: List<NotificationModel> = emptyList(),
    val isLoading: Boolean = false
)