package com.example.yap.ui.navigation

import android.annotation.SuppressLint
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.yap.ui.screen.ChatsScreen
import com.example.yap.ui.screen.FriendsScreen
import com.example.yap.ui.screen.home.HomeScreen
import com.example.yap.ui.screen.MapScreen
import com.example.yap.ui.screen.ProfileScreen
import com.example.yap.ui.theme.LocalAdditionColors


@SuppressLint("RestrictedApi")
@Composable
fun NavigationApp() {
    val bottomItems = listOf(Screen.Chats, Screen.Map, Screen.Home, Screen.Friends, Screen.Profile) // Порядок

    // NavController для каждой вкладки
    val navControllers: Map<Screen, NavHostController> = bottomItems.associateWith { rememberNavController() }

    // Saver для сохранения текущей вкладки при пересоздании Activity
    val screenSaver = Saver<Screen, String>(
        save = { it.route },
       restore = { route ->
            when (route) {
                Screen.Home.route -> Screen.Home
                Screen.Chats.route -> Screen.Chats
                Screen.Map.route -> Screen.Map
                Screen.Friends.route -> Screen.Friends
                Screen.Profile.route -> Screen.Profile
//                Screen.HomeDetails.route -> Screen.HomeDetails
//                Screen.HomeDeepDetails.route -> Screen.HomeDeepDetails
//                Screen.HomeSettings.route -> Screen.HomeSettings
//                Screen.Favorites.route -> Screen.Favorites
//                Screen.FavDetails.route -> Screen.FavDetails
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
    val additionalColors = LocalAdditionColors.current


    Scaffold(
        modifier = Modifier
            .fillMaxSize(),
//            .windowInsetsPadding(WindowInsets(0, 0, 0, 0)),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),

        bottomBar = {
                NavigationBar(
                    containerColor = additionalColors.bottomSurface,
                    tonalElevation = 0.dp,
                    windowInsets = WindowInsets(0, 0, 0, 0)
                ) {
                    bottomItems.forEach { screen ->
                        val isSelected = currentTab == screen

                        NavigationBarItem(

                            selected = isSelected,
                            onClick = {
                                if (currentTab == screen) {
                                    navControllers[screen]?.popBackStack(
                                        route = screen.route,
                                        inclusive = false
                                    )
                                } else {
                                    currentTab = screen
                                }
                            },
                            icon = {
                                val iconRes = if (!screen.hasSelectedState) {
                                    screen.unselectedIcon
                                } else {
                                    if (isSelected) screen.selectedIcon else screen.unselectedIcon
                                }
                                Icon(
                                    painter = painterResource(iconRes),
                                    contentDescription = screen.route
                                )
                            },
                            label = {
                                Text(
                                    screen.route
                                        .substringBefore("/")
                                        .replaceFirstChar { it.uppercase() },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,

                                unselectedIconColor = additionalColors.unselectedColor,
                                unselectedTextColor = additionalColors.unselectedColor,

                                indicatorColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
            }
        }
    ) { innerPadding ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            bottomItems.forEach { screen ->
                TabNavHost(
                    navController = navControllers[screen]!!,
                    startRoute = screen.route,
                    visible = currentTab == screen
                ) {
                    when (screen) {
                        Screen.Home -> {
                            composable(Screen.Home.route) {
                                HomeScreen()
                            }
                        }

                        Screen.Chats -> {
                            composable(Screen.Chats.route) {
                                ChatsScreen()
                            }
                        }

                        Screen.Map -> {
                            composable(Screen.Map.route) {
                                MapScreen()
                            }
                        }

                        Screen.Friends -> {
                            composable(Screen.Friends.route) {
                                FriendsScreen()
                            }
                        }

                        Screen.Profile -> {
                            composable(Screen.Profile.route) {
                                ProfileScreen()
                            }
                        }

                        else -> error("Unexpected screen: $screen")
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



