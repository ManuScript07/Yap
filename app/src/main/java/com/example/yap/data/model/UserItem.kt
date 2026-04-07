package com.example.yap.data.model

data class UserItem(
    val id: Int,
    val name: String,
    val isYapActive: Boolean,
    val avatarRes: Int // Добавляем ресурс аватара
)