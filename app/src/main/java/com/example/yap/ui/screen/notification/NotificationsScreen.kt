package com.example.yap.ui.screen.notification


import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.yap.ui.screen.home.HomeUiState
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
    homeViewModel: HomeViewModel
//    @SuppressLint("ContextCastToActivity") homeViewModel: HomeViewModel = viewModel(LocalContext.current as ComponentActivity)
) {
    StatusBarIconsColor(isLight = true)

    val state by viewModel.state.collectAsState()
    val homeState by homeViewModel.state.collectAsState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val baseScale = LocalBaseScale.current
    val context = LocalContext.current

    val guardedNavigateToProfile = rememberLambda<Int> { userId ->
        onNavigateToProfile(userId)
    }


    Box(modifier = Modifier.fillMaxSize()) {
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
                                    messageType = YapType.YAP,
                                    isLocationEnabled = homeState.isLocationEnabled,
                                )
                            },
                            onLocationClick = {},
                            onNavigateToProfile = guardedNavigateToProfile,

                            )
                    }
                }

            }
        }



        val statusMessage = homeState.systemStatusResource?.let { stringResource(it) }
            ?: homeState.systemStatusMessage

        AnimatedVisibility(
            visible = statusMessage != null, // Теперь привязано к системному статусу
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp)
        ) {
            val isSuccess = homeState.systemStatusResource == R.string.yap_sent_success
            val bgColor = if (isSuccess) Color(0xFF4CAF50) else Color(0xFF323232)

            Surface(
                shape = CircleShape,
                color = bgColor,
                shadowElevation = 6.dp,
                modifier = Modifier.padding(horizontal = 24.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(
                            if (isSuccess) R.drawable.outline_notifications_24
                            else R.drawable.outline_notifications_off_24
                        ),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = statusMessage ?: "",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
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
    isLocationEnabled: Boolean,
    messageType: YapType
) {
    if (!isLocationEnabled) {
        Log.d("API1", "Тумблер ВЫКЛЮЧЕН. Координаты: null")
        viewModel.handleDirectSend(userId, null, null, messageType)
        return // ВАЖНО: дальше код не идет
    }

    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
        if (location != null) {
            Log.d("API1", "ViewModel Hash: ${viewModel.hashCode()}, Тумблер: ${viewModel.state.value.isLocationEnabled}")
            Log.d("API1", "Тумблер ВКЛЮЧЕН. Координаты: ${location.latitude}")
            viewModel.handleDirectSend(userId, location.latitude, location.longitude, messageType)
        } else {
            viewModel.handleDirectSend(userId, null, null, messageType)
        }
    }.addOnFailureListener {
        viewModel.handleDirectSend(userId, null, null, messageType)
    }
}