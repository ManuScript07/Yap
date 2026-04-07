package com.example.yap.ui.navigation

import androidx.annotation.DrawableRes
import com.example.yap.R


sealed class Screen(val route: String,
                    val unselectedIcon: Int,
                    val selectedIcon: Int,
                    val hasSelectedState: Boolean = true
) {
    object Home : Screen("главная", R.drawable.yap2, R.drawable.yap2, false)
//    object HomeDetails : Screen("home/details")
//    object HomeSettings: Screen("home/settings")
//    object HomeDeepDetails: Screen("home/details/deepDetails")
    object Chats : Screen("чатикс", R.drawable.outline_sms_24, R.drawable.baseline_chat_24)
//    object FavDetails: Screen("favorites/details")
    object Map : Screen("карта", R.drawable.outline_map_24, R.drawable.baseline_map_24)
    object Friends : Screen("друны", R.drawable.outline_smile_24,R.drawable.friends_24)
    object Profile : Screen("акк", R.drawable.outline_person_24,R.drawable.baseline_person_24)
}