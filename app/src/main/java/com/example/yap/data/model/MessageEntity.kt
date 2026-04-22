package com.example.yap.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.ServerTimestamp

data class MessageEntity(
    val id: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val type: String = "YAP",
    val text: String? = null,
    val audioUrl: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @get:ServerTimestamp
    val timestamp: Timestamp? = null,
    val visibleForReceiver: Boolean = true
)