package com.example.yap.ui.screen.myProfile

import com.example.yap.data.model.UserItem

data class MyProfileUiState(
    val isLoading: Boolean = true,
    val user: UserItem? = null,
    val error: String? = null,
    val isAvatarViewerOpen: Boolean = false
)
