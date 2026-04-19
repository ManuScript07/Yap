package com.example.yap.ui.screen.notification


import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.location.Location
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yap.R
import com.example.yap.data.model.UserItem
import com.example.yap.ui.screen.home.HomeViewModel
import com.example.yap.ui.screen.home.YapType
import com.example.yap.ui.theme.LocalAdditionColors
import com.example.yap.ui.theme.LocalBaseScale
import com.example.yap.util.compose.StatusBarIconsColor
import com.example.yap.util.compose.rememberLambda
import com.example.yap.util.openMap
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample


@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
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

    val onDelete = remember(viewModel) { { id: String -> viewModel.deleteNotification(id) } }
    val onMute = remember(viewModel) { { id: String -> viewModel.muteNotification(id) } }
    val onLocationClick = remember {
        { lat: Double, lon: Double, userName: String ->
            openMap(context, lat, lon, "Локация от $userName")
        }
    }
    val isLocationEnabled by remember { derivedStateOf { homeState.isLocationEnabled } }
    val listState = rememberLazyListState()

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo }
            .map { info -> info.mapNotNull { it.key as? String } }
            .sample(200)
            .distinctUntilChanged()
            .collectLatest { visibleIds ->
                if (visibleIds.isNotEmpty()) {
                    delay(200)
                    viewModel.markAsRead(visibleIds)
                }
            }
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
                state = listState,
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
                    key = { it.id },
                    contentType = { "notification" }
                ) { notification ->

                    NotificationRow(
                        modifier = Modifier.animateItem(
                            fadeInSpec = tween(500),
                            placementSpec = spring(stiffness = Spring.StiffnessLow),
                            fadeOutSpec = tween(300)
                        ),
                        item = notification,
                        onDelete = { onDelete(notification.id) },
                        onMute = { onMute(notification.id) },
                        onYapClick = {
                            viewModel
                                .toggleUserQuickList(
                                    notification.user,
                                    notification.isUserInQuickList) },

                        onYapSend = {
                            fetchLocationAndSendDirectYap(
                                context = context,
                                onLocationReady = { lat, lon ->
                                    homeViewModel.handleDirectSend(
                                        userId = notification.user.id,
                                        latitude = lat,
                                        longitude = lon,
                                        type = YapType.YAP
                                    )
                                },
                                isLocationEnabled = isLocationEnabled,
                            )
                        },
                        onLocationClick = {
                            notification.latitude?.let { lat ->
                                notification.longitude?.let { lon ->
                                    onLocationClick(lat, lon, notification.user.name)
                                }
                            }
                        },
                        onNavigateToProfile = guardedNavigateToProfile,
                        onListenClick = {
                            viewModel.selectNotification(notification)
                        }
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
    if (state.selectedNotification != null) {
        VoiceDetailsSheet(
            state = state,
            onDismiss = { viewModel.selectNotification(null) },
            onTogglePlay = { url -> viewModel.togglePlayback(url) }
        )
    }
}


@SuppressLint("MissingPermission")
fun fetchLocationAndSendDirectYap(
    context: Context,
    isLocationEnabled: Boolean,
    onLocationReady: (Double?, Double?) -> Unit
) {
    if (!isLocationEnabled) {
        Log.d("API1", "Тумблер ВЫКЛЮЧЕН. Координаты: null")
        onLocationReady(null, null)
        return
    }

    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
        if (location != null) {
            Log.d("API1", "Тумблер ВКЛЮЧЕН. Координаты: ${location.latitude}")
            onLocationReady(location.latitude, location.longitude)
        } else {
            onLocationReady(null, null)
        }
    }.addOnFailureListener {
        onLocationReady(null, null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BaseTopAppBar(
    title: String,
    onBack: (() -> Unit)? = null,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceDetailsSheet(
    state: NotificationsUiState,
    onDismiss: () -> Unit,
    onTogglePlay: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    val notification = state.selectedNotification ?: return

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = Color(0xFFB9B9E5) // Цвет из твоего скрина
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Расшифровка",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(16.dp))

            // Текст расшифровки (если его нет — показываем "Расшифровывается...")
            Text(
                text = notification.messageText ?: "Идет расшифровка сообщения...",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.DarkGray
            )

            Spacer(Modifier.height(32.dp))

            // Плеер
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = { notification.audioUrl?.let { onTogglePlay(it) } }) {
                    Icon(
                        painter = painterResource(
                            if (state.isPlaying) R.drawable.baseline_pause_32 else R.drawable.baseline_play_arrow_32
                        ),
                        contentDescription = null,
                        modifier = Modifier.size(48.dp)
                    )
                }

                // Здесь можно добавить индикатор прогресса (LinearProgressIndicator)
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .weight(1f)
                        .height(40.dp)
                        .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                )

                Spacer(Modifier.width(8.dp))
                Text("0:12") // Время можно брать из MediaPlayer
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

