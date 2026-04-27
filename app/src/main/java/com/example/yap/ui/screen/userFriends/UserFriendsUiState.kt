package com.example.yap.ui.screen.userFriends

import com.example.yap.data.model.UserItem
import com.example.yap.ui.screen.addUser.AddFriendStatus

data class UserFriendsUiState(
    val isLoading: Boolean = true,
    val targetUserName: String = "",
    val friends: List<FoundUser> = emptyList()
)

// Модель для отображения пользователя со статусом отношений
data class FoundUser(
    val user: UserItem,
    val status: AddFriendStatus,
    val mutualFriendsCount: Int = 0,
    val isInQuickList: Boolean
)

