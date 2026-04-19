package com.example.yap

import com.google.firebase.firestore.DocumentId

data class MessageEntity(
    val id: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val type: String = "YAP",
    val text: String? = null,
    val audioUrl: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @get:com.google.firebase.firestore.ServerTimestamp
    val timestamp: com.google.firebase.Timestamp? = null,
    val visibleForReceiver: Boolean = true
)
