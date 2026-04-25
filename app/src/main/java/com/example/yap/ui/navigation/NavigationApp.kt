package com.example.yap.ui.navigation

import android.annotation.SuppressLint
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.yap.ui.screen.ChatsScreen
import com.example.yap.ui.screen.friends.FriendsScreen
import com.example.yap.ui.screen.MapScreen
import com.example.yap.ui.screen.ProfileScreen
import com.example.yap.ui.screen.home.HomeScreen
import com.example.yap.ui.screen.home.HomeViewModel
import com.example.yap.ui.screen.notification.NotificationsScreen
import com.example.yap.ui.screen.splash.SplashViewModel
import com.example.yap.ui.screen.user_profile.UserProfileScreen
import com.example.yap.ui.theme.LocalAdditionColors
import kotlinx.coroutines.yield


@SuppressLint("RestrictedApi")
@Composable
fun NavigationApp(splashViewModel: SplashViewModel = viewModel()) {


    val bottomItems = listOf(Screen.Chats, Screen.Map, Screen.Home, Screen.Friends, Screen.Profile) // Порядок

    // NavController для каждой вкладки
    val navControllers: Map<Screen, NavHostController> = bottomItems.associateWith { rememberNavController() }

    val navLockTime = remember { mutableLongStateOf(0L) }
    val sharedViewModel: HomeViewModel = viewModel()
    val currentPendingRoute by splashViewModel.pendingRoute.collectAsState()


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
                else -> Screen.Home
            }
        }
    )

    var currentTab: Screen by rememberSaveable(stateSaver = screenSaver) {
        mutableStateOf(Screen.Home) // Основной таб
    }

    val context = LocalContext.current

    val additionalColors = LocalAdditionColors.current

    // Обработка системной кнопки Back
    BackHandler {
        val currentNavController = navControllers[currentTab]!!



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



    LaunchedEffect(currentPendingRoute) {
        if (currentPendingRoute == AppDestinations.NOTIFICATIONS) {
            currentTab = Screen.Home

            // Даем Compose время переключить вкладку
            yield()

            val navController = navControllers[Screen.Home]
            if (navController?.currentDestination?.route != AppDestinations.NOTIFICATIONS) {
                navController?.navigate(AppDestinations.NOTIFICATIONS) {
                    launchSingleTop = true
                }
            }

            // ОБЯЗАТЕЛЬНО: Очищаем роут после перехода!
            splashViewModel.onRouteConsumed()
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),

        bottomBar = {
            Column {
                NavigationBar(
                    containerColor = additionalColors.bottomSurface,
                    tonalElevation = 0.dp,
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    modifier = Modifier.height(64.dp)
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
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(additionalColors.bottomSurface) // Тот же цвет, что у меню
                        .navigationBarsPadding() // Эта штука просто создаст пустой блок высотой с кнопки
                )
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
                                HomeScreen(
                                    onNavigateToProfile = { userId ->
                                        safeNavigate(
                                            controller = navControllers[screen],
                                            route = AppDestinations.createProfileRoute(userId),
                                            lockState = navLockTime
                                        )
                                    },
                                    onNavigateToNotifications = {
                                        safeNavigate(
                                            controller = navControllers[screen],
                                            route = AppDestinations.NOTIFICATIONS,
                                            lockState = navLockTime
                                        )
                                    },
                                    viewModel = sharedViewModel
                                )
                            }

                            composable(AppDestinations.NOTIFICATIONS) {
                                NotificationsScreen(
                                    onBack = { safePopBackStack(navControllers[screen]) },
                                    onNavigateToProfile = { userId ->
                                        safeNavigate(
                                            controller = navControllers[screen],
                                            route = AppDestinations.createProfileRoute(userId),
                                            lockState = navLockTime
                                        )
                                    },
                                    homeViewModel = sharedViewModel
                                )
                            }
                            userProfileComposable(navControllers[screen])
                        }

                        Screen.Chats -> {
                            composable(Screen.Chats.route) {
                                ChatsScreen(

                                )
                            }
                        }

                        Screen.Map -> {
                            composable(Screen.Map.route) {
                                MapScreen()
                            }
                        }

                        Screen.Friends -> {
                            composable(Screen.Friends.route) {
                                FriendsScreen(
                                    onNavigateToProfile = { userId ->
                                        safeNavigate(
                                            controller = navControllers[screen],
                                            route = AppDestinations.createProfileRoute(userId),
                                            lockState = navLockTime
                                        )
                                    },
                                    homeViewModel = sharedViewModel,
                                )
                            }
                            userProfileComposable(navControllers[screen])
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

    if (visible) {
        val instantDuration = 400

        NavHost(
            navController = navController,
            startDestination = startRoute,
            modifier = Modifier.fillMaxSize(),
            enterTransition = {
                fadeIn(animationSpec = tween(instantDuration))
            },
            exitTransition = {
                fadeOut(animationSpec = tween(instantDuration))
            },
            popEnterTransition = {
                fadeIn(animationSpec = tween(instantDuration))
            },
            popExitTransition = {
                fadeOut(animationSpec = tween(instantDuration))
            },
            builder = content
        )
    }
}


fun NavGraphBuilder.userProfileComposable(navController: NavHostController?) {
    composable(
        route = AppDestinations.USER_PROFILE_ROUTE,
        arguments = listOf(navArgument("userId") { type = NavType.StringType })
    ) { backStackEntry ->
        val userId = backStackEntry.arguments?.getString("userId") ?: return@composable
        UserProfileScreen(
            userId = userId,
            onBackClick = { safePopBackStack(navController) }
        )
    }
}

fun safeNavigate(
    controller: NavHostController?,
    route: String,
    lockState: MutableLongState
) {
    val currentTime = System.currentTimeMillis()
    if (currentTime - lockState.longValue > 600L) {
        lockState.longValue = currentTime
        controller?.navigate(route) {
            launchSingleTop = true
        }
    }
}

fun safePopBackStack(controller: NavHostController?) {
    if (controller?.previousBackStackEntry != null) {
        controller.popBackStack()
    }
}



