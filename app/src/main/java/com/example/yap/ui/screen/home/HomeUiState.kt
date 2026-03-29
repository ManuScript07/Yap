package com.example.yap.ui.screen.home

import com.example.yap.data.UserItem

data class HomeUiState(
    val users: List<UserItem> = emptyList(),
    val progress: Float = 0.65f, // 65/100
    val starsCount: Int = 5,
    val isNiceActive: Boolean = true,
    val yapStars: Int = 5,// Звездочка внутри кнопки YAP
    val isLocationEnabled: Boolean = true,
    val currentAlertMessage: String? = null,
    val canCloseMessage: Boolean = false,
    val messageType: MessageType = MessageType.INFO,
    val isEmojiPickerOpen: Boolean = false,
    val isChatPickerOpen: Boolean = false,
    val isSheetExpanded: Boolean = false,
)

enum class MessageType {
    INFO, WARNING, ERROR
}


