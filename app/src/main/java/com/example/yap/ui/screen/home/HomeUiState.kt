package com.example.yap.ui.screen.home

import com.example.yap.data.model.UserItem
import com.example.yap.ui.components.YapButtonState

data class HomeUiState(
    val users: List<UserItem> = emptyList(),

    val currentStars: Int = 100,
    val maxStars: Int = 100,
    val progress: Float = 0.100f,
    val yapPrice: Int = 0,
    val yapType: YapType = YapType.YAP,

    val isNiceActive: Boolean = true,
    val isLocationEnabled: Boolean = true,
    val notificationsCount: Int = 8,

    val currentAlertMessage: String? = null,
    val currentAlertResource: Int? = null,

    val systemStatusResource: Int? = null,
    val systemStatusMessage: String? = null,
    val showSuccessAlert: Boolean = false,
    val statusId: Long = 0L,
    val isStatusSuccess: Boolean = true,

    val userGeneratedContent: String? = null,
    val voiceAudioUri: String? = null,
    val alertId: Long = 0L,
    val recordStartDate: Long? = null,

    val canCloseMessage: Boolean = false,
    val messageType: MessageType = MessageType.INFO,
    val isEmojiPickerOpen: Boolean = false,
    val isChatPickerOpen: Boolean = false,
    val isSheetExpanded: Boolean = false,
    val isEmojiOnly: Boolean = false,
    val maxUsers: Int = 20,

    val yapButtonState: YapButtonState = YapButtonState.IDLE,
    val yapRecordTimeMs: Long = 0L,
    val yapOffsetY: Float = 0f,
    val didOverrideMessage: Boolean = false,
    val maxDurationMs: Long = 20_000L,
    val totalDurationMs: Long = 0L,
    val currentProgressMs: Int = 0,

    val isSystemAlertOverridden: Boolean = false,
    val isPlayingVoice: Boolean = false,

    val transcribedText: String? = null,
    val isTranscribing: Boolean = false,



)

enum class MessageType {
    INFO,
}

enum class YapType {
    YAP, EMOJI, TEXT, VOICE
}


