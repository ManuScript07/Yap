package com.example.yap.data.model

import androidx.annotation.Keep

@Keep
data class FriendRequestEntity(
    val id: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val senderName: String = "",
    val senderAvatarUrl: String? = null,
    val timestamp: Long = 0L,
    val status: String = "pending"
)
