package com.example.navigation.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object HomeDetails : Screen("home/details")
    object HomeSettings: Screen("home/settings")
    object HomeDeepDetails: Screen("home/details/deepDetails")
    object Favorites : Screen("favorites")
    object FavDetails: Screen("favorites/details")
    object Profile : Screen("profile")
}