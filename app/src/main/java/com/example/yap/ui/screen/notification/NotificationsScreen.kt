package com.example.yap.ui.screen.notification


import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yap.R
import com.example.yap.ui.screen.home.HomeViewModel
import com.example.yap.ui.screen.home.YapType
import com.example.yap.ui.theme.LocalAdditionColors
import com.example.yap.ui.theme.LocalBaseScale
import com.example.yap.util.compose.StatusBarIconsColor
import com.example.yap.util.compose.rememberLambda
import com.google.android.gms.location.LocationServices


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onNavigateToProfile: (String) -> Unit,
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

    val guardedNavigateToProfile = rememberLambda<String> { userId ->
        onNavigateToProfile(userId)
    }


    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                BaseTopAppBar(
                    title = stringResource(R.string.notification),
                    onBack = onBack,
                    scrollBehavior = scrollBehavior
                )
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding()),
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
                            viewModel.toggleUserQuickList(notification.user, notification.isUserInQuickList)
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
        SystemStatusPill(
            statusResource = homeState.systemStatusResource,
            statusMessage = homeState.systemStatusMessage,
            statusId = homeState.statusId
        )
    }
}


@SuppressLint("MissingPermission")
fun fetchLocationAndSendDirectYap(
    context: Context,
    viewModel: HomeViewModel,
    userId: String,
    isLocationEnabled: Boolean,
    messageType: YapType
) {
    Log.d("API1", "Получатель $userId")
    if (!isLocationEnabled) {
        Log.d("API1", "Тумблер ВЫКЛЮЧЕН. Координаты: null")
        viewModel.handleDirectSend(userId, null, null, messageType)
        return
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BaseTopAppBar(
    title: String,
    onBack: (() -> Unit)? = null, // Если null, кнопки назад не будет
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    val baseScale = LocalBaseScale.current

    TopAppBar(
        title = {
            Text(
                text = title,
                fontSize = (28 * baseScale).sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground
            )
        },
        navigationIcon = {
            if (onBack != null) {
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
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = LocalAdditionColors.current.headerColor
        ),
        scrollBehavior = scrollBehavior
    )
}


@Composable
fun BoxScope.SystemStatusPill(
    statusResource: Int?,
    statusMessage: String?,
    statusId: Long
) {
    // 1. Создаем "хранилище" для последнего валидного сообщения
    // Оно НЕ обнуляется, когда statusResource становится null
    var lastValidMessage by remember { mutableStateOf("") }
    var lastValidIconIsSuccess by remember { mutableStateOf(true) }

    val currentMessage = statusResource?.let { stringResource(it) } ?: statusMessage

    // Обновляем хранилище только если пришло что-то реальное
    LaunchedEffect(statusId) {
        if (currentMessage != null) {
            lastValidMessage = currentMessage
            lastValidIconIsSuccess = (statusResource == R.string.yap_sent_success)
        }
    }

    AnimatedVisibility(
        visible = statusResource != null || statusMessage != null,
        // Смещаем анимацию появления еще ниже, а улетание делаем симметричным
        enter = slideInVertically(initialOffsetY = { -it * 3 }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it * 3 }) + fadeOut(),
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = 80.dp) // Чуть ниже от края
            .zIndex(100f)
    ) {
        // Черный полупрозрачный фон
        val pillColor = Color.Black.copy(alpha = 0.8f)

        Surface(
            shape = CircleShape,
            color = pillColor,
            shadowElevation = 4.dp,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(
                        if (lastValidIconIsSuccess) R.drawable.baseline_check_circle_24
                        else R.drawable.baseline_cancel_24
                    ),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )

                Spacer(Modifier.width(10.dp))

                Text(
                    // Самое важное: берем lastValidMessage, оно не зануляется!
                    text = lastValidMessage,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}