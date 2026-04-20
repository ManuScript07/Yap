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
    val audioUrl: String? = null,
    val isTranscribing: Boolean = false
)

data class NotificationsUiState(
    val notifications: List<NotificationItemModel> = emptyList(),
    val isRefreshing: Boolean = false,
    val selectedNotification: NotificationItemModel? = null,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val currentProgressMs: Int = 0,
    val totalDurationMs: Int = 0
)