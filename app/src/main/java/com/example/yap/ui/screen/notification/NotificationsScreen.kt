package com.example.yap.ui.screen.notification


import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yap.R
import com.example.yap.ui.components.BaseTopAppBar
import com.example.yap.ui.screen.home.HomeViewModel
import com.example.yap.ui.screen.home.YapType
import com.example.yap.ui.theme.LocalAdditionColors
import com.example.yap.ui.theme.LocalBaseScale
import com.example.yap.util.compose.SystemBarsIconsColor
import com.example.yap.util.compose.rememberLambda
import com.example.yap.util.extension.SystemStatusPill
import com.example.yap.util.extension.shimmerEffect
import com.example.yap.util.fetchLocationAndSendDirectYap
import com.example.yap.util.formatTime
import com.example.yap.util.openMap
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
    SystemBarsIconsColor(isLight = true)

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
                if (state.notifications.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillParentMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.no_notifications),
                                fontSize = 20.sp * baseScale,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .padding(horizontal = 48.dp * baseScale)
                                    .graphicsLayer(translationY = - (innerPadding.calculateTopPadding().value))
                            )
                        }
                    }
                } else {
                    items(
                        items = state.notifications,
                        key = { item -> item.id  },
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
                                        notification.isUserInQuickList
                                    )
                            },

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
            // Передаем актуальные значения из стейта
            currentProgressMs = state.currentProgressMs,
            totalDurationMs = state.totalDurationMs,

            onDismiss = { viewModel.selectNotification(null) },
            onTogglePlay = { url -> viewModel.togglePlayback(url) },
            onRequestTranscription = { audioUrl ->
                viewModel.requestTranscription(
                    notificationId = state.selectedNotification!!.id,
                    audioUrl = audioUrl
                )
            },
            onSeek = { newPosition -> viewModel.seekTo(newPosition) },
            onPrepare = { url -> viewModel.prepareAudio(url) }
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceDetailsSheet(
    state: NotificationsUiState,
    onDismiss: () -> Unit,
    onTogglePlay: (String) -> Unit,
    onRequestTranscription: (String) -> Unit,
    onSeek: (Float) -> Unit,
    currentProgressMs: Int = 0,
    totalDurationMs: Int = 12000,
    onPrepare: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val notification = state.selectedNotification ?: return

    val baseScale = LocalBaseScale.current

    var isDragging by remember { mutableStateOf(false) }

    var localSliderValue by remember { mutableFloatStateOf(0f) }

    val targetProgress = if (totalDurationMs > 0) currentProgressMs.toFloat() / totalDurationMs else 0f

    val animatedProgress by animateFloatAsState(
        targetValue = if (isDragging) localSliderValue else targetProgress,
        animationSpec = if (isDragging) snap() else tween(200, easing = LinearEasing),
        label = "SliderSmooth"
    )

    LaunchedEffect(targetProgress) {
        if (!isDragging) {
            localSliderValue = targetProgress
        }
    }

    LaunchedEffect(notification.audioUrl) {
        notification.audioUrl?.let { url ->
            onPrepare(url)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = LocalAdditionColors.current.purpleBackColor
    ) {
        Column(
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 30.dp * baseScale)
        .navigationBarsPadding(),
    horizontalAlignment = Alignment.Start
) {
    when {
        notification.messageText != null -> {
            Text(
                text = stringResource(R.string.transcription),
                fontSize = 24.sp * baseScale,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(20.dp * baseScale))

            Text(
                text = notification.messageText.trim(),
                fontSize = 18.sp * baseScale,
                color = LocalAdditionColors.current.secondTextColor,
                lineHeight = 20.sp * baseScale,
                textAlign = TextAlign.Start
            )
        }
        notification.isTranscribing -> {
            Text(
                text = stringResource(R.string.transcription),
                fontSize = 24.sp * baseScale,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(24.dp * baseScale))
            TranscriptionShimmer(baseScale)
        }
        else -> {
            val interactionSource = remember { MutableInteractionSource() }
            Text(
                text = stringResource(R.string.to_decipher),
                fontSize = 24.sp * baseScale,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp * baseScale))
                    .background(LocalAdditionColors.current.purpleLightColor.copy(alpha = 0.8f))
                    .padding(horizontal = 8.dp * baseScale, vertical = 8.dp * baseScale)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null
                    ) {
                        notification.audioUrl?.let { onRequestTranscription(it) }
                    }
            )
        }
    }


    Spacer(Modifier.height(24.dp * baseScale))

    // ПЛЕЕР
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp * baseScale) // Высота по самому высокому элементу (Play)
    ) {
        // 1. Анимация размеров (Play — высокая, Pause — квадратная)
        val buttonWidth by animateDpAsState(if (state.isPlaying) 54.dp else 48.dp, label = "w")
        val buttonCornerRadius by animateDpAsState(if (state.isPlaying) 8.dp else 24.dp, label = "r")

        val buttonColor by animateColorAsState(
            targetValue = if (state.isPlaying)
                LocalAdditionColors.current.purpleLightColor
            else
                LocalAdditionColors.current.purpleSurfaceColor
        )

        val contentColor by animateColorAsState(
            targetValue = if (state.isPlaying)
                LocalAdditionColors.current.purpleSurfaceColor
            else
                Color.White
        )

        // Кнопка Play/Pause
        Box(
            modifier = Modifier
                .width(buttonWidth * baseScale)
                .height(54.dp * baseScale)
                .clip(RoundedCornerShape(buttonCornerRadius * baseScale))
                .background(buttonColor)
                .clickable { notification.audioUrl?.let { onTogglePlay(it) } },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(
                    if (state.isPlaying) R.drawable.baseline_pause_32 else R.drawable.baseline_play_arrow_32
                ),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(32.dp * baseScale)
            )
        }

        Spacer(Modifier.width(10.dp * baseScale))

        Slider(
            value = if (isDragging) localSliderValue else animatedProgress,
            onValueChange = {
                isDragging = true
                localSliderValue = it
            },
            onValueChangeFinished = {
                isDragging = false
                onSeek(localSliderValue * totalDurationMs)
            },
            modifier = Modifier
                .weight(1f)
                .height(54.dp * baseScale),
            // Кастомный ползунок (вертикальная палочка)
            thumb = {
                Box(
                    Modifier
                        .width(4.dp * baseScale)
                        .height(56.dp * baseScale)
                        .clip(RoundedCornerShape(2.dp))
                        .background(LocalAdditionColors.current.purpleSurfaceColor)
                )
            },
            // Кастомный трек (высокая плашка)
            track = {
                val currentFraction = if (isDragging) localSliderValue else animatedProgress

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp * baseScale)
                        .clip(RoundedCornerShape(14.dp * baseScale))
                        .background(LocalAdditionColors.current.purpleLightColor.copy(alpha = 0.5f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(currentFraction.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(LocalAdditionColors.current.purpleSurfaceColor)
                    )
                }
            }
        )

        Spacer(Modifier.width(10.dp * baseScale))

        val displayTimeMs = if (isDragging) {
            (localSliderValue * totalDurationMs).toInt()
        } else if (state.isPlaying || currentProgressMs > 0) {
            currentProgressMs
        } else {
            totalDurationMs
        }

        Text(
            text = formatTime(displayTimeMs),
            fontSize = 15.sp * baseScale,
            fontWeight = FontWeight.Bold,
            color = LocalAdditionColors.current.purpleSurfaceColor,
            modifier = Modifier
                .height(54.dp * baseScale)
                .background(
                    LocalAdditionColors.current.purpleLightColor,
                    RoundedCornerShape(8.dp * baseScale)
                )
                .padding(horizontal = 12.dp * baseScale)
                .wrapContentHeight(Alignment.CenterVertically)
        )
    }
        Spacer(Modifier.height(32.dp * baseScale))
        }
    }
}

@Composable
fun TranscriptionShimmer(baseScale: Float) {
    Column {
        repeat(4) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp * baseScale)
                    .clip(RoundedCornerShape(8.dp))
                    .shimmerEffect()
            )
            Spacer(Modifier.height(5.dp * baseScale))
        }
        Box(
            modifier = Modifier
                .fillMaxWidth(0.4f)
                .height(24.dp * baseScale)
                .clip(RoundedCornerShape(8.dp))
                .shimmerEffect()
        )
    }
}




