package com.example.yap

data class MessageEntity(
    val id: String = "",
    val senderId: Int = 0,
    val receiverId: Int = 0,
    val type: String = "YAP",
    val text: String? = null,
    val audioUrl: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val timestamp: com.google.firebase.Timestamp? = null
)
