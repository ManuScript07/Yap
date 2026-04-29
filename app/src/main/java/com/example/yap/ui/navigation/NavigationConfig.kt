package com.example.yap.ui.navigation

import com.example.yap.R


sealed class Screen(val route: String,
                    val unselectedIcon: Int,
                    val selectedIcon: Int,
                    val hasSelectedState: Boolean = true
) {

    object Home : Screen("главная", R.drawable.yap2, R.drawable.yap2, false)

    object Friends : Screen("друны", R.drawable.outline_smile_24,R.drawable.friends_24)
    object Profile : Screen("акк", R.drawable.outline_person_24,R.drawable.baseline_person_24)
}

object AppDestinations {

    const val NOTIFICATIONS = "home/notifications"
    const val USER_PROFILE_ROUTE = "user_profile/{userId}"
    const val SEARCH_FRIENDS = "friends/search"
    const val SEARCH_FRIENDS_FROM_HOME = "home/search"
    const val USER_FRIENDS_LIST = "user_friends_list/{userId}"
    const val EDIT_PROFILE = "profile/edit"
    const val SUPPORT = "profile/support"

    fun createProfileRoute(userId: String): String {
        return "user_profile/$userId"
    }

    fun createUserFriendsListRoute(userId: String) = "user_friends_list/$userId"
}
