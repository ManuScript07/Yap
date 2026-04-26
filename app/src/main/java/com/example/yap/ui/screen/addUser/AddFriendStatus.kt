package com.example.yap.ui.screen.addUser

import com.example.yap.data.model.FriendRequestEntity
import com.example.yap.data.model.UserItem

enum class AddFriendStatus {
    CAN_ADD,
    PENDING,
    ALREADY_FRIEND
}

data class FoundUser(
    val user: UserItem,
    val status: AddFriendStatus
)

data class AddUserUiState(
    val searchQuery: String = "",
    val isValidCode: Boolean = false,
    val formattedCodeForUI: String = "",
    val remoteSearchResult: FoundUser? = null,
    val isSearchPerformed: Boolean = false,
    val incomingRequests: List<FriendRequestEntity> = emptyList()
)