package com.example.yap.ui.navigation

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Intent
import android.util.Log
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.navigation.navDeepLink
import com.example.yap.ui.screen.friends.FriendsScreen
import com.example.yap.ui.screen.addUser.SearchFriendsScreen
import com.example.yap.ui.screen.editProfile.EditProfileScreen
import com.example.yap.ui.screen.home.HomeScreen
import com.example.yap.ui.screen.home.HomeViewModel
import com.example.yap.ui.screen.myProfile.MyProfileScreen
import com.example.yap.ui.screen.myProfile.MyProfileViewModel
import com.example.yap.ui.screen.notification.NotificationsScreen
import com.example.yap.ui.screen.splash.SplashViewModel
import com.example.yap.ui.screen.support.SupportScreen
import com.example.yap.ui.screen.userFriends.UserFriendsScreen
import com.example.yap.ui.screen.userFriends.UserFriendsViewModel
import com.example.yap.ui.screen.userProfile.UserProfileScreen
import com.example.yap.ui.theme.LocalAdditionColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield


@SuppressLint("RestrictedApi")
@Composable
fun NavigationApp(splashViewModel: SplashViewModel = viewModel()) {


    val bottomItems = listOf(Screen.Friends, Screen.Home, Screen.Profile) // Порядок

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
        val route = currentPendingRoute ?: return@LaunchedEffect

        when (route) {
            AppDestinations.NOTIFICATIONS -> {
                currentTab = Screen.Home
                yield()
                val navController = navControllers[Screen.Home]
                // Безопасная навигация
                navigateSafely(navController, route) {
                    splashViewModel.onRouteConsumed()
                }
            }

            AppDestinations.SEARCH_FRIENDS -> {
                currentTab = Screen.Friends

                // Даем время на смену вкладки
                delay(200)

                val navController = navControllers[Screen.Friends]

                // Безопасная навигация
                navigateSafely(navController, route) {
                    splashViewModel.onRouteConsumed()
                }
            }
        }
    }

    DisposableEffect(Unit) {
        val activity = context as? Activity
        val intentHandler = { intent: Intent ->
            val data = intent.data // например, yap://profile/123
            if (data != null && data.scheme == "yap") {
                val userId = data.lastPathSegment
                if (userId != null) {
                    // Решаем, на какой вкладке открыть профиль.
                    // Профессиональнее всего открывать на Home
                    currentTab = Screen.Home
                    navControllers[Screen.Home]?.navigate(AppDestinations.createProfileRoute(userId))
                }
            }
        }

        // Обрабатываем интент, если приложение было закрыто и открылось по ссылке
        activity?.intent?.let { intentHandler(it) }

        onDispose { }
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
                                    onNavigateToSearch = {
                                        safeNavigate(
                                            controller = navControllers[screen],
                                            route = AppDestinations.SEARCH_FRIENDS_FROM_HOME,
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


                            userProfileComposable(
                                navController = navControllers[screen],
                                homeViewModel = sharedViewModel,
                                navLockTime = navLockTime
                            )

                            searchFriendsComposable(
                                navController = navControllers[screen],
                                navLockTime = navLockTime,
                                route = AppDestinations.SEARCH_FRIENDS_FROM_HOME
                            )


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
                                    onNavigateToAddFriend = {
                                        safeNavigate(
                                            controller = navControllers[screen],
                                            route = AppDestinations.SEARCH_FRIENDS,
                                            lockState = navLockTime
                                        )
                                    },
                                )
                            }

                            searchFriendsComposable(
                                navController = navControllers[screen],
                                navLockTime = navLockTime,
                                route = AppDestinations.SEARCH_FRIENDS
                            )


                            userProfileComposable(
                                navController = navControllers[screen],
                                homeViewModel = sharedViewModel,
                                navLockTime = navLockTime)
                        }

                        Screen.Profile -> {
                            composable(Screen.Profile.route) {
                                MyProfileScreen(
                                    onNavigateToEditProfile = {
                                        safeNavigate(
                                            controller = navControllers[screen],
                                            route = AppDestinations.EDIT_PROFILE,
                                            lockState = navLockTime
                                        )
                                    },
                                    onSupportClick = {
                                        safeNavigate(navControllers[screen], AppDestinations.SUPPORT, navLockTime)
                                    }
                                )
                            }

                            composable(AppDestinations.EDIT_PROFILE) {
                                val profileViewModel: MyProfileViewModel = viewModel(
                                    factory = MyProfileViewModel.provideFactory(LocalContext.current.applicationContext as Application)
                                )
                                val profileState by profileViewModel.state.collectAsState()

                                profileState.user?.let { currentUser ->
                                    EditProfileScreen(
                                        currentUser = currentUser,
                                        onBack = { safePopBackStack(navControllers[screen]) }
                                    )
                                }
                            }

                            composable(AppDestinations.SUPPORT) {
                                SupportScreen(
                                    onBack = { safePopBackStack(navControllers[screen]) },
                                )
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


fun NavGraphBuilder.userProfileComposable(
    navController: NavHostController?,
    homeViewModel: HomeViewModel,
    navLockTime: MutableLongState // Добавляем lockState для safeNavigate
) {
    // ЭКРАН ПРОФИЛЯ
    composable(
        route = AppDestinations.USER_PROFILE_ROUTE,
        arguments = listOf(navArgument("userId") { type = NavType.StringType }),
        deepLinks = listOf(
            navDeepLink { uriPattern = "https://yap.app/profile/{userId}" },
            navDeepLink { uriPattern = "yap://profile/{userId}" } // Для надежности
        )
    ) { backStackEntry ->
        val userId = backStackEntry.arguments?.getString("userId") ?: return@composable
        UserProfileScreen(
            userId = userId,
            onBack = { safePopBackStack(navController) },
            homeViewModel = homeViewModel,
            onNavigateToFriendsList = { targetUserId ->
                safeNavigate(
                    controller = navController,
                    route = AppDestinations.createUserFriendsListRoute(targetUserId),
                    lockState = navLockTime
                )
            }
        )
    }

    // ЭКРАН СПИСКА ДРУЗЕЙ
    composable(
        route = AppDestinations.USER_FRIENDS_LIST,
        arguments = listOf(navArgument("userId") { type = NavType.StringType })
    ) { backStackEntry ->
        val userId = backStackEntry.arguments?.getString("userId") ?: return@composable
        val context = LocalContext.current

        // Создаем ViewModel с фабрикой
        val friendsVm: UserFriendsViewModel = viewModel(
            factory = UserFriendsViewModel.provideFactory(context.applicationContext as Application, userId)
        )

        UserFriendsScreen(
            viewModel = friendsVm,
            onBack = { safePopBackStack(navController) },
            onNavigateToProfile = { targetId ->
                safeNavigate(
                    controller = navController,
                    route = AppDestinations.createProfileRoute(targetId),
                    lockState = navLockTime
                )
            },
            homeViewModel = homeViewModel
        )
    }
}


fun NavGraphBuilder.searchFriendsComposable(
    navController: NavHostController?,
    navLockTime: MutableLongState,
    route: String
) {
    composable(route) {
        SearchFriendsScreen(
            onBack = { safePopBackStack(navController) },
            onNavigateToProfile = { userId ->
                safeNavigate(
                    controller = navController,
                    route = AppDestinations.createProfileRoute(userId),
                    lockState = navLockTime
                )
            }
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


private fun navigateSafely(
    navController: NavHostController?,
    route: String,
    onSuccess: () -> Unit
) {
    try {
        // КЛЮЧЕВОЙ МОМЕНТ:
        // Вместо .graph используем backQueue или currentBackStackEntry.
        // Если граф не установлен, currentBackStackEntry будет null, и мы просто не пойдем дальше.
        val hasGraph = navController?.currentBackStackEntry != null

        if (hasGraph) {
            if (navController?.currentDestination?.route != route) {
                navController?.navigate(route) {
                    launchSingleTop = true
                    restoreState = true
                }
            }
            onSuccess()
        } else {
            Log.w("NAV_DEBUG", "Контроллер для $route еще не готов (нет BackStackEntry)")
        }
    } catch (e: Exception) {
        Log.e("NAV_DEBUG", "Ошибка навигации на $route: ${e.message}")
    }
}



