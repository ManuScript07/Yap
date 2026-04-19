package com.example.yap.ui.screen.notification

import androidx.compose.runtime.Immutable
import com.example.yap.data.model.UserItem

@Immutable
data class NotificationItemModel(
    val id: String,
    val user: UserItem,
    val messageText: String? = null,
    val hasLocation: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val timeAgo: String,
    val timestamp: String,
    val isUserInQuickList: Boolean,
    val isMuted: Boolean,

)

data class NotificationsUiState(
    val notifications: List<NotificationItemModel> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false
)