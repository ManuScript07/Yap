package com.example.yap.ui.screen.user_profile

object AppDestinations {
    const val USER_PROFILE_ROUTE = "user_profile/{userId}"

    fun createProfileRoute(userId: Int): String {
        return "user_profile/$userId"
    }
}