package com.example.yap.data.model

data class UserItem(
    val id: String = "",
    val name: String = "",
    val username: String = "",
    val avatarUrl: String? = null,
    val userCode: String = "",
    val bio: String = "",
    val dobTimestamp: Long? = null,
    val showOnlyDay: Boolean = false,
    val email: String = "",
    val isYapActive: Boolean = false,
    val isMuted: Boolean = false,
    val createdAt: Long? = null,
    val quickList: List<String> = emptyList(),
    val mutedUsers: List<String> = emptyList(),
    val friends: List<String> = emptyList(),
)