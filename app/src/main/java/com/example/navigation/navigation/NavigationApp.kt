package com.example.navigation.navigation

import android.annotation.SuppressLint
import android.app.Activity
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.navigation.ui.screen.FavDetailsScreen
import com.example.navigation.ui.screen.FavoritesScreen
import com.example.navigation.ui.screen.HomeDeepDetailsScreen
import com.example.navigation.ui.screen.HomeDetailsScreen
import com.example.navigation.ui.screen.HomeScreen
import com.example.navigation.ui.screen.HomeSettingsScreen
import com.example.navigation.ui.screen.ProfileScreen


@SuppressLint("RestrictedApi")
@Composable
fun NavigationApp() {
    val bottomItems = listOf(Screen.Favorites, Screen.Home, Screen.Profile) // Порядок

    // NavController для каждой вкладки
    val navControllers: Map<Screen, NavHostController> = bottomItems.associateWith { rememberNavController() }

    // Saver для сохранения текущей вкладки при пересоздании Activity
    val screenSaver = Saver<Screen, String>(
        save = { it.route },
        restore = { route ->
            when (route) {
                Screen.Home.route -> Screen.Home
                Screen.HomeDetails.route -> Screen.HomeDetails
                Screen.HomeDeepDetails.route -> Screen.HomeDeepDetails
                Screen.HomeSettings.route -> Screen.HomeSettings
                Screen.Favorites.route -> Screen.Favorites
                Screen.FavDetails.route -> Screen.FavDetails
                Screen.Profile.route -> Screen.Profile
                else -> Screen.Home
            }
        }
    )

    var currentTab: Screen by rememberSaveable(stateSaver = screenSaver) {
        mutableStateOf(Screen.Home) // Основной таб
    }

    val context = LocalContext.current

    // Обработка системной кнопки Back
    BackHandler {
        val currentNavController = navControllers[currentTab]!!

//        val backStackRoutes = currentNavController.currentBackStack.value
//            .mapNotNull { it.destination.route }
//            .joinToString(" -> ")
//
//        Log.d("NAV_DEBUG", "Текущий таб: ${currentTab.route}")
//        Log.d("NAV_DEBUG", "Стек этого таба: $backStackRoutes")

        // 1. Пытаемся вернуться назад ВНУТРИ текущего таба
        // (например, из HomeDetails в Home)
        if (currentNavController.previousBackStackEntry != null) {
            currentNavController.popBackStack()
        }
        // 2. Если внутри таба мы в корне, но сам таб — не Home
        else if (currentTab != Screen.Home) {
            currentTab = Screen.Home // Основной таб
            // Здесь МЫ НЕ ВЫЗЫВАЕМ popBackStack() для Home,
            // чтобы сохранить там открытый дочерний экран (HomeDetails), если он был.
        }
        // 3. Если мы уже в корне Home — закрываем приложение
        else {
            (context as? Activity)?.moveTaskToBack(true)
        }
    }
    NavigationSuiteScaffold(
        layoutType = NavigationSuiteType.NavigationBar,
        containerColor = Color.Transparent,
        navigationSuiteItems = {
            bottomItems.forEach { screen ->
                val isSelected = currentTab == screen

                item(
                    icon = {
                        Icon(
                            when (screen) {
                                Screen.Home -> Icons.Default.Home
                                Screen.Favorites -> Icons.Default.Favorite
                                Screen.Profile -> Icons.Default.AccountBox
                                else -> Icons.Default.Home
                            },
                            contentDescription = screen.route
                        )
                    },
                    label = { Text(screen.route.substringBefore("/").replaceFirstChar { it.uppercase() }) },
                    selected = isSelected,
                    onClick = {
                        if (currentTab == screen) {
                            // Сброс стека текущей вкладки до корня
                            navControllers[screen]?.popBackStack(route = screen.route, inclusive = false)
                        } else {
                            // Переключение на другую вкладку
                            currentTab = screen
                        }
                    }
                )
            }
        },
    ) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets(0, 0, 0, 0)),
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { innerPadding ->
            Box(modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(innerPadding)) {
                bottomItems.forEach { screen ->
                    TabNavHost(
                        navController = navControllers[screen]!!,
                        startRoute = screen.route,
                        visible = currentTab == screen
                    ) {
                        when (screen) {
                            Screen.Home -> {
                                composable(Screen.Home.route) {
                                    HomeScreen(
                                        onGoToDetails = { navControllers[Screen.Home]?.navigate(
                                            Screen.HomeDetails.route) {
                                            launchSingleTop= true
                                        }},
                                        onGoToSettings = {navControllers[Screen.Home]?.navigate(
                                            Screen.HomeSettings.route){
                                            launchSingleTop = true
                                        }}
                                    )

                                }
                                composable(Screen.HomeDetails.route) {
                                    HomeDetailsScreen(
                                        onGoToDeepDetails = {navControllers[Screen.Home]?.navigate(
                                            Screen.HomeDeepDetails.route
                                        ){
                                            launchSingleTop = true
                                        }}
                                    )
                                }

                                composable(Screen.HomeDeepDetails.route){
                                    HomeDeepDetailsScreen()
                                }
                                composable(Screen.HomeSettings.route){
                                    HomeSettingsScreen()
                                }
                            }
                            Screen.Favorites -> {
                                composable(Screen.Favorites.route) {
                                    FavoritesScreen(
                                        onGoToFavDetails = {navControllers[Screen.Favorites]?.navigate(
                                            Screen.FavDetails.route
                                        ){
                                            launchSingleTop = true
                                        }}
                                    )
                                }
                                composable(Screen.FavDetails.route){
                                    FavDetailsScreen()
                                }
                            }
                            Screen.Profile -> {
                                composable(Screen.Profile.route) { ProfileScreen() }
                            }
                            else -> {error("Unexpected screen: $screen")}
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TabNavHost(
    navController: NavHostController,
    startRoute: String,
    visible: Boolean,
    content: NavGraphBuilder.() -> Unit
) {
    // Если таб не активен, мы полностью убираем NavHost из дерева композиции.
    // Это гарантирует, что системный BackHandler внутри NavHost не будет мешать.
    if (visible) {
//        DisposableEffect(startRoute) {
//            Log.d("NAV_DEBUG", "Таб $startRoute стал видимым")
//            onDispose {
//                Log.d("NAV_DEBUG", "Таб $startRoute скрыт")
//            }
//        }
        NavHost(
            navController = navController,
            startDestination = startRoute,
            modifier = Modifier.fillMaxSize(),
            builder = content
        )
    }
}

