package com.example.yap.ui.screen.notification

import androidx.compose.runtime.Immutable
import com.example.yap.data.model.UserItem

@Immutable
data class NotificationItemModel(
    val id: Int,
    val user: UserItem,
    val messageText: String? = null,
    val hasLocation: Boolean = false,
    val timeAgo: String,
    val timestamp: String,
)

data class NotificationsUiState(
    val notifications: List<NotificationItemModel> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false
)