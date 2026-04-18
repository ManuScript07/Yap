package com.example.yap.data.model

data class UserItem(
    val id: String,
    val name: String,
    val isYapActive: Boolean,
    val avatarRes: Int,
    val isMuted: Boolean = false

)