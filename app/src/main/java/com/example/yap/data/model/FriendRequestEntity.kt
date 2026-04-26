package com.example.yap.data.model

data class FriendRequestEntity(
    val id: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val timestamp: Long = 0L,
    val status: String = "pending"
)
