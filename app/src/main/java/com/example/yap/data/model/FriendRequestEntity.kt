package com.example.yap.data.model

data class FriendRequestEntity(
    val id: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val senderName: String = "",
    val senderAvatarUrl: String? = null,
    val timestamp: Long = 0L,
    val status: String = "pending"
)
