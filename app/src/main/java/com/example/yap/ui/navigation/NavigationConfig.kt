package com.example.yap.ui.navigation

import com.example.yap.R


sealed class Screen(val route: String,
                    val unselectedIcon: Int,
                    val selectedIcon: Int,
                    val hasSelectedState: Boolean = true
) {

    object Home : Screen("главная", R.drawable.yap2, R.drawable.yap2, false)

    object Chats : Screen("чатикс", R.drawable.outline_sms_24, R.drawable.baseline_chat_24)
    object Map : Screen("карта", R.drawable.outline_map_24, R.drawable.baseline_map_24)
    object Friends : Screen("друны", R.drawable.outline_smile_24,R.drawable.friends_24)
    object Profile : Screen("акк", R.drawable.outline_person_24,R.drawable.baseline_person_24)
}

object AppDestinations {

    const val NOTIFICATIONS = "home/notifications"
    const val USER_PROFILE_ROUTE = "user_profile/{userId}"
    const val SEARCH_FRIENDS = "friends/search"

    fun createProfileRoute(userId: String): String {
        return "user_profile/$userId"
    }
}