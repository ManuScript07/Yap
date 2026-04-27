package com.example.yap.ui.screen.userProfile

import com.example.yap.data.model.UserItem

data class UserProfileUiState(
    val isLoading: Boolean = true,
    val user: UserItem? = null,
    val isUserInQuickList: Boolean = false,
    val isFriend: Boolean = false,
    val error: String? = null,
    val isAvatarViewerOpen: Boolean = false,
)
