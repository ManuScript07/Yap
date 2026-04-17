package com.example.yap.ui.screen.notification


import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yap.R
import com.example.yap.ui.theme.LocalAdditionColors
import com.example.yap.ui.theme.LocalBaseScale
import com.example.yap.util.compose.StatusBarIconsColor
import com.example.yap.util.compose.rememberLambda
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.platform.LocalContext
import com.example.yap.ui.screen.home.HomeViewModel
import com.example.yap.ui.screen.home.YapType
import com.example.yap.ui.screen.home.fetchLocationAndSendYap
import com.google.android.gms.location.LocationServices


// Убедись, что нет импорта PullToRefreshBox, если он подчеркнут
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onNavigateToProfile: (Int) -> Unit,
    viewModel: NotificationsViewModel = viewModel(),
    @SuppressLint("ContextCastToActivity") homeViewModel: HomeViewModel = viewModel(LocalContext.current as ComponentActivity)
) {
    StatusBarIconsColor(isLight = true)

    val state by viewModel.state.collectAsState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val baseScale = LocalBaseScale.current
    val context = LocalContext.current

    val guardedNavigateToProfile = rememberLambda<Int> { userId ->
        onNavigateToProfile(userId)
    }



    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.notification),
                        fontSize = (28 * baseScale).sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    Box(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clickable(
                                onClick = onBack,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.baseline_arrow_back_24),
                            contentDescription = "Назад",
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = LocalAdditionColors.current.headerColor
                ),
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        // Используем Box для наложения индикатора поверх списка
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = 12.dp * baseScale,
                    bottom = 20.dp * baseScale
                )
            ) {
                items(
                    items = state.notifications,
                    key = { it.id }
                ) { notification ->
                    NotificationRow(
                        item = notification,
                        onDelete = { viewModel.deleteNotification(notification.id) },
                        onMute = { viewModel.muteNotification(notification.id) },
                        onYapClick = { userId ->
                            viewModel.toggleUserQuickList(notification.user)
                        },
                        onYapSend = { userId ->
                            fetchLocationAndSendDirectYap(
                                context = context,
                                viewModel = homeViewModel,
                                userId = userId,
                                messageType = YapType.YAP
                            )
                        },
                        onLocationClick = {},
                        onNavigateToProfile = guardedNavigateToProfile,

                    )
                }
            }

        }
    }
}


@SuppressLint("MissingPermission")
fun fetchLocationAndSendDirectYap(
    context: Context,
    viewModel: HomeViewModel,
    userId: Int,
    messageType: YapType
) {
    Log.d("API1", "Нажатие")
    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
        // Вызываем новый метод handleDirectSend, который мы создали в пункте 1
        viewModel.handleDirectSend(
            userId = userId,
            latitude = location?.latitude,
            longitude = location?.longitude,
            type = messageType
        )
    }
}