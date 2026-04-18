package com.example.yap.ui.screen.home


import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.BottomSheetScaffoldState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yap.R
import com.example.yap.data.model.UserItem
import com.example.yap.ui.components.MainYapButton
import com.example.yap.ui.components.YapActionButton
import com.example.yap.ui.theme.LocalAdditionColors
import com.example.yap.ui.theme.LocalBaseScale
import com.example.yap.util.LocationHelper
import com.example.yap.util.LocationHelper.checkLocationSettings
import com.example.yap.util.compose.StatusBarIconsColor
import com.example.yap.util.compose.rememberLambda
import com.google.android.gms.location.LocationServices
import kotlin.hashCode


@SuppressLint("ConfigurationScreenWidthHeight")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
//    viewModel: HomeViewModel = viewModel(),
    viewModel: HomeViewModel,
    onNavigateToProfile: (Int) -> Unit,
    onNavigateToNotifications: () -> Unit,
) {
    StatusBarIconsColor(isLight = true)
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current


    val gpsResolverLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.setLocationToggle(isEnabled = true, isManualAction = true)
        } else {
            viewModel.setLocationToggle(false)
        }
    }

    val microphonePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.resetYapButton()
        if (!isGranted) {
            viewModel.showAlert(resId = R.string.mic_permission, durationMs = 2000)
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val isGranted = permissions.values.any { it }
        if (isGranted) {
            checkLocationSettings(
                context = context,
                onEnabled = { viewModel.setLocationToggle(true) },
                onShowResolver = { exception ->
                    gpsResolverLauncher.launch(IntentSenderRequest.Builder(exception.resolution).build())
                },
                onFailure = { viewModel.setLocationToggle(false) }
            )
        } else {
            viewModel.setLocationToggle(false)
        }
    }

    DisposableEffect(context) {
        val filter = IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                context?.let {
                    val isAvailable = LocationHelper.isLocationAvailable(it)
                    if (state.isLocationEnabled && !isAvailable) {
                        viewModel.setLocationToggle(false)
//                        viewModel.showAlert(resId = R.string.location_disabled_error, durationMs = 3000)
                    }
                }
            }
        }
        context.registerReceiver(receiver, filter)
        onDispose { context.unregisterReceiver(receiver) }
    }

    // --- 2. ЖИЗНЕННЫЙ ЦИКЛ (На случай возврата из настроек) ---
    DisposableEffect(lifecycleOwner, state.isLocationEnabled) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (state.isLocationEnabled && !LocationHelper.isLocationAvailable(context)) {
                    viewModel.setLocationToggle(false)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // --- 3. UI СОСТОЯНИЕ ШТОРКИ ---
    val sheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.PartiallyExpanded,
        skipHiddenState = false,
        confirmValueChange = { newState -> newState != SheetValue.Hidden }
    )
    val scaffoldState = rememberBottomSheetScaffoldState(sheetState)

    val guardedNavigateToProfile = rememberLambda<Int> { userId ->
        onNavigateToProfile(userId)
    }

    val guardedNavigateToNotifications = rememberLambda<Unit> {
        onNavigateToNotifications()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.bg),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0033FF)),
            contentScale = ContentScale.Crop
        )

        HomeUsersBottomSheet(
            scaffoldState = scaffoldState,
            state = state,
            screenHeight = LocalConfiguration.current.screenHeightDp.dp,
            onUserClick = guardedNavigateToProfile,
            onYapClick = { userId -> viewModel.toggleUserYap(userId) },
            onAddUserClick = { viewModel.addUser() },
            onRemoveUserClick = { id -> viewModel.removeUser(id) },
            content = { innerPadding ->
                HomeContent(
                    innerPadding = innerPadding,
                    viewModel = viewModel,
                    onLocationToggle = { isChecked ->
                        if (isChecked) {
                            handleLocationActivation(
                                context = context,
                                permissionLauncher = locationPermissionLauncher,
                                gpsLauncher = gpsResolverLauncher,
                                viewModel = viewModel
                            )
                        } else {
                            viewModel.setLocationToggle(false)
                        }
                    },
                    onRequestMicrophonePermission = {
                        microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onNotificationsClick = {guardedNavigateToNotifications(Unit)}
                )
            }
        )
    }
}


private fun handleLocationActivation(
    context: Context,
    permissionLauncher: ManagedActivityResultLauncher<Array<String>, Map<String, Boolean>>,
    gpsLauncher: ManagedActivityResultLauncher<IntentSenderRequest, ActivityResult>,
    viewModel: HomeViewModel
) {
    val hasFineLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val hasCoarseLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    if (hasFineLocation || hasCoarseLocation) {
        checkLocationSettings(
            context = context,
            onEnabled = { viewModel.setLocationToggle(true) },
            onShowResolver = { exception ->
                gpsLauncher.launch(IntentSenderRequest.Builder(exception.resolution).build())
            },
            onFailure = {
                viewModel.setLocationToggle(false)
            }
        )
    } else {
        permissionLauncher.launch(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeUsersBottomSheet(
    scaffoldState: BottomSheetScaffoldState,
    state: HomeUiState,
    screenHeight: Dp,
    onYapClick: (Int) -> Unit,
    onUserClick: (Int) -> Unit,
    onAddUserClick: () -> Unit,
    onRemoveUserClick: (Int) -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    val adaptivePeekHeight = screenHeight * 0.28f
    val baseScale = LocalBaseScale.current
    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        containerColor = Color.Transparent,
        sheetPeekHeight = adaptivePeekHeight,
        sheetContainerColor = LocalAdditionColors.current.speedBottomDialog,
        sheetShape = RoundedCornerShape(
            topStart = 28.dp * baseScale,
            topEnd = 28.dp * baseScale
        ),
        sheetSwipeEnabled = true,
        sheetDragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp * baseScale)
                    .width(40.dp * baseScale)
                    .height(4.dp * baseScale)
                    .background(
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = CircleShape
                    )
            )
        },
        sheetContent = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(screenHeight * 0.75f)
            ) {
                UsersBottomSheet(
                    users = state.users,
                    onUserClick = onUserClick,
                    onYapClick = onYapClick,
                    onAddUser = onAddUserClick,
                    onRemoveUser = onRemoveUserClick,
                    maxUsers = state.maxUsers
                )
            }
        },
        content = content
    )
}


@Composable
fun UsersBottomSheet(
    users: List<UserItem>,
    onYapClick: (Int) -> Unit,
    onUserClick: (Int) -> Unit,
    onAddUser: () -> Unit,
    onRemoveUser: (Int) -> Unit,
    maxUsers: Int
) {
    val baseScale = LocalBaseScale.current

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 300.dp * baseScale),
        contentPadding = PaddingValues(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (users.isEmpty()) {
            item(key = "empty_state") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp * baseScale),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(R.string.users_empty_state),
                        fontSize = (20 * baseScale).sp,
                        fontWeight = FontWeight.Bold,
                        color = LocalAdditionColors.current.secondTextColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 20.dp * baseScale)
                    )

                    AddUserButton(onClick = onAddUser)
                }
            }
        } else {
            items(
                items = users,
                key = { it.id }
            ) { user ->
                Box(modifier = Modifier.animateItem(
                    fadeInSpec = tween(150),
                    fadeOutSpec = tween(150),
                    placementSpec = spring(stiffness = Spring.StiffnessLow)
                )) {
                    UserListItem(
                        user = user,
                        onUserClick = onUserClick,
                        onYapClick = onYapClick,
                        onRemoveClick = onRemoveUser
                    )
                }
            }

            if (users.size < maxUsers){
                item(key = "add_button") {
                    Box(modifier = Modifier.animateItem()) {
                        AddUserButton(onClick = onAddUser)
                    }
                }
            }
        }
    }
}

@Composable
fun UserListItem(
    user: UserItem,
    onYapClick: (Int) -> Unit,
    onUserClick: (Int) -> Unit,
    onRemoveClick: (Int) -> Unit
) {
    var showMenu by rememberSaveable { mutableStateOf(false) }
    val baseScale = LocalBaseScale.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true), // Эффект от края до края
                onClick = { onUserClick(user.id) }
            )
            .padding(horizontal = 16.dp, vertical = 6.dp * baseScale),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(user.avatarRes),
            contentDescription = null,
            modifier = Modifier
                .size(48.dp * baseScale)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.width(14.dp*baseScale))

        Text(
            text = user.name,
            modifier = Modifier.weight(1f),
            fontSize = 18.sp * baseScale,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        YapActionButton(
            user = user,
            onYapClick = onYapClick
        )
        Spacer(modifier = Modifier.width(8.dp * baseScale))

        Box(contentAlignment = Alignment.Center) {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.size(32.dp * baseScale)
            ) {
                Icon(
                    painter = painterResource(R.drawable.more_vert),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp * baseScale)
                )
            }

            DeleteUserPopup(
                isVisible = showMenu,
                onDismiss = { showMenu = false },
                onDeleteClick = { onRemoveClick(user.id) },
            )
        }
    }
}


@Composable
fun DeleteUserPopup(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val baseScale = LocalBaseScale.current
    val density = LocalDensity.current
    val offsetX = remember(density, baseScale) {
        with(density) { ((-110).dp * baseScale).toPx().toInt() }
    }

    var isAnimatedVisible by remember { mutableStateOf(false) }
    LaunchedEffect(isVisible) { isAnimatedVisible = isVisible }

    if (isVisible) {
        Popup(
            alignment = Alignment.Center,
            onDismissRequest = onDismiss,
            offset = IntOffset(x = offsetX, y = 0),
            properties = PopupProperties(focusable = true)
        ) {
            Box(
                modifier = Modifier.size(
                    width = 248.dp * baseScale,
                    height = 56.dp * baseScale
                ),
                contentAlignment = Alignment.Center
            ) {
                AnimatedVisibility(
                    visible = isAnimatedVisible,
                    enter = fadeIn(animationSpec = tween(200)) +
                            scaleIn(initialScale = 0.95f, animationSpec = tween(200)),
                    exit = fadeOut(animationSpec = tween(150)) +
                            scaleOut(targetScale = 0.95f, animationSpec = tween(150))
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable {
                                onDeleteClick()
                                onDismiss()
                            },
                        shape = RoundedCornerShape(18.dp * baseScale),
                        color = LocalAdditionColors.current.popupColor
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.users_delete_popup),
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = (20 * baseScale).sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddUserButton(onClick: () -> Unit) {
    val baseScale = LocalBaseScale.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp * baseScale),
        contentAlignment = Alignment.Center
    ) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = LocalAdditionColors.current.darkYapButtonBackgroundColor,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            shape = RoundedCornerShape(100.dp),
            contentPadding = PaddingValues(
                horizontal = 24.dp * baseScale,
                vertical = 12.dp * baseScale
            ),
            elevation = ButtonDefaults.buttonElevation(0.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp * baseScale),
                )

                Spacer(modifier = Modifier.width(8.dp * baseScale))

                Text(
                    text = stringResource(R.string.users_add_button),
                    fontSize = (18 * baseScale).sp,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun HomeContent(
    innerPadding: PaddingValues,
    viewModel: HomeViewModel,
    onLocationToggle: (Boolean) -> Unit,
    onRequestMicrophonePermission: () -> Unit,
    onNotificationsClick: () -> Unit
    ) {
    val state by viewModel.state.collectAsState()
    val baseScale = LocalBaseScale.current
    val context = LocalContext.current
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            if (state.isEmojiPickerOpen || state.isChatPickerOpen) {
                                viewModel.toggleEmojiPicker(false)
                                viewModel.toggleChatPicker(false)
                            }
                        }
                    )
                }
                .padding(bottom = innerPadding.calculateBottomPadding() + (10.dp * baseScale))
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // --- 1. ВЕРХ ---
            TopActionBar(
                state = state,
                onLocationToggle = onLocationToggle,
                modifier = Modifier.padding(top = 12.dp * baseScale),
                onNotificationsClick = onNotificationsClick
            )

            InfoMessage(
                message = state.currentAlertMessage,
                messageResId = state.currentAlertResource,
                showCloseIcon = state.canCloseMessage,
                onClose = { viewModel.dismissMessage() },
                baseScale = baseScale,
                isEmojiOnly = state.isEmojiOnly
            )

            // --- 2. ЦЕНТР ---
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                   MainYapButton(
                    price = state.yapPrice,
                    onClick = { selectedType ->
                        fetchLocationAndSendYap(
                            context = context,
                            viewModel = viewModel,
                            isLocationEnabled = state.isLocationEnabled,
                            messageType = selectedType
                    )},
                    viewModel = viewModel,
                    context = context,
                    onRequestMicrophonePermission = onRequestMicrophonePermission
                )
            }

            // --- 3. НИЗ ---
            // Теперь здесь ТОЛЬКО кнопки и прогресс-бар. Никакого чата.
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ActionButtonsRow(
                    state = state,
                    onEmojiClick = { viewModel.selectEmoji(it) },
                    onTogglePicker = { viewModel.toggleEmojiPicker(it) },
                    onToggleChat = { viewModel.toggleChatPicker(it) },
                    baseScale = baseScale
                )

                Spacer(modifier = Modifier.height(12.dp * baseScale))
                ProgressIndicatorOnly(progress = state.progress)
            }
        }

        // ==========================================
        // СЛОЙ 2: ПАНЕЛЬ ЧАТА (ВСПЛЫВАЕТ ПОВЕРХ ВСЕГО)
        // ==========================================
        AnimatedVisibility(
            visible = state.isChatPickerOpen,
            enter = fadeIn(animationSpec = tween(220, delayMillis = 90)) +
                    scaleIn(
                        initialScale = 0.85f,
                        // Указываем, что анимация идет из правого нижнего угла (от кнопки)
                        transformOrigin = TransformOrigin(1f, 1f),
                        animationSpec = tween(220, delayMillis = 90)
                    ),
            exit = fadeOut(animationSpec = tween(150)) +
                    scaleOut(
                        targetScale = 0.92f,
                        transformOrigin = TransformOrigin(1f, 1f),
                        animationSpec = tween(150)
                    ),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    bottom = innerPadding.calculateBottomPadding() + (90.dp * baseScale),
                    end = 20.dp
                )
        ) {
            QuickMessagesPanel(
                onMessageSelected = { viewModel.selectQuickMessage(it) },
                baseScale = baseScale
            )
        }
    }
}

@SuppressLint("MissingPermission")
fun fetchLocationAndSendYap(
    context: Context,
    viewModel: HomeViewModel,
    isLocationEnabled: Boolean,
    messageType: YapType
) {
    if (!isLocationEnabled) {
        viewModel.handleSendRequest(latitude = null, longitude = null, messageType)
        return
    }

    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
        if (location != null) {
            Log.d("API1", "ViewModel Hash: ${viewModel.hashCode()}, Тумблер: ${viewModel.state.value.isLocationEnabled}")

            Log.d("YAP_LOCATION", "🌍 Успешно: Lat=${location.latitude}, Lon=${location.longitude}")
            viewModel.handleSendRequest(location.latitude, location.longitude, messageType)
        } else {
            Log.e("YAP_LOCATION", "⚠️ Локация равна null (GPS еще не поймал спутники)")
            viewModel.handleSendRequest(null, null, messageType)
            viewModel.showAlert(resId = R.string.failed_location, durationMs = 3000, canClose = false)
        }
    }
}

@Composable
fun InfoMessage(
    message: String?,
    messageResId: Int?,
    isEmojiOnly: Boolean,
    showCloseIcon: Boolean,
    onClose: () -> Unit,
    baseScale: Float
) {
    val finalMessage = message ?: messageResId?.let { stringResource(it) } ?: ""

    val dynamicFontSize = if (isEmojiOnly) (32 * baseScale).sp else (18 * baseScale).sp
    val dynamicLetterSpacing = if (isEmojiOnly) (4 * baseScale).sp else TextUnit.Unspecified

    AnimatedVisibility(
        visible = finalMessage.isNotEmpty(),
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp * baseScale),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = finalMessage,
                color = MaterialTheme.colorScheme.background,
                fontSize = dynamicFontSize,
                letterSpacing = dynamicLetterSpacing,
                textAlign = TextAlign.Center,
                fontWeight = if (isEmojiOnly) FontWeight.Normal else FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp * baseScale)
            )

            if (showCloseIcon) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 8.dp * baseScale)
                        .size(32.dp * baseScale)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.background.copy(alpha = 0.7f),
                        modifier = Modifier.size(24.dp * baseScale)
                    )
                }
            }
        }
    }
}

@Composable
fun ActionButtonsRow(
    state: HomeUiState,
    onEmojiClick: (String) -> Unit,
    onTogglePicker: (Boolean) -> Unit,
    onToggleChat: (Boolean) -> Unit,
    baseScale: Float
) {
    AnimatedContent(
        targetState = state.isEmojiPickerOpen,
        transitionSpec = {
            (fadeIn(animationSpec = tween(220, delayMillis = 90)) +
                    scaleIn(initialScale = 0.85f, animationSpec = tween(220, delayMillis = 90)))
                .togetherWith(
                    fadeOut(animationSpec = tween(150)) +
                            scaleOut(targetScale = 0.92f, animationSpec = tween(150))
                )
        },
        label = "EmojiPickerTransition"
    ) { isOpen ->
        if (isOpen) {
            EmojiPickerPanel(
                onEmojiSelected = onEmojiClick,
                onClose = { onTogglePicker(false) },
                baseScale = baseScale
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProgressText(currentValue = state.currentStars, maxValue = state.maxStars)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    ActionButton(
                        iconRes = R.drawable.baseline_favorite_24,
                        onClick = { onTogglePicker(true) },
                        baseScale = baseScale,
                        shape = RoundedCornerShape(48.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp * baseScale))
                    ActionButton(
                        iconRes = R.drawable.baseline_chat_bubble_24,
                        onClick = { onToggleChat(!state.isChatPickerOpen) },
                        baseScale = baseScale
                    )
                }
            }
        }
    }
}

@Composable
fun ActionButton(
    iconRes: Int,
    baseScale: Float,
    onClick: () -> Unit,
    bgColor: Color = LocalAdditionColors.current.buttonReactionColor,
    shape: Shape = RoundedCornerShape(16.dp)
) {
    Surface(
        modifier = Modifier
            .size(width = 72.dp * baseScale, height = 52.dp * baseScale),
        shape = shape,
        color = bgColor,
        onClick = onClick
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(32.dp * baseScale),
                tint = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}

@Composable
fun EmojiPickerPanel(
    onEmojiSelected: (String) -> Unit,
    onClose: () -> Unit,
    baseScale: Float
) {
    val emojis = stringArrayResource(R.array.quick_emojis)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp * baseScale)
            .background(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = CircleShape
            )
            .padding(horizontal = 12.dp * baseScale),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        emojis.forEach { emoji ->
            Text(
                text = emoji,
                fontSize = (32 * baseScale).sp,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onEmojiSelected(emoji) }
                    .padding(4.dp * baseScale)
            )
        }

        IconButton(
            onClick = onClose,
            modifier = Modifier.size(32.dp * baseScale)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = null,
                modifier = Modifier.size(24.dp * baseScale),
                tint = LocalAdditionColors.current.unselectedColor
            )
        }
    }
}


@Composable
fun QuickMessagesPanel(
    onMessageSelected: (String) -> Unit,
    baseScale: Float
) {
    val messages = stringArrayResource(R.array.quick_messages).toList()

    Card(
        modifier = Modifier
            .width(220.dp * baseScale)
            .padding(bottom = 8.dp * baseScale),
        shape = RoundedCornerShape(28.dp * baseScale),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 8.dp * baseScale)
        ) {
            messages.forEach { msg ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(),
                            onClick = { onMessageSelected(msg) }
                        )
                        .padding(vertical = 14.dp * baseScale, horizontal = 20.dp * baseScale)
                ) {
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = (16 * baseScale).sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun ProgressText(currentValue: Int, maxValue: Int = 100) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(R.drawable.star),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = "$currentValue/$maxValue",
            color = Color.White,
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun ProgressIndicatorOnly(progress: Float) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 500, easing = LinearOutSlowInEasing),
        label = "ProgressCanvas"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "PulseTransition")
    val pulseOffset by infiniteTransition.animateFloat(
        initialValue = -0.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "PulseOffset"
    )

    val trackColor = Color.White.copy(alpha = 0.4f)
    val progressColor = MaterialTheme.colorScheme.primary
    val pulseColor = Color.White.copy(alpha = 0.6f)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(20.dp)
            .padding(horizontal = 2.dp)
    ) {
        val strokeWidth = size.height
        val radius = strokeWidth / 2
        val usableWidth = size.width - (radius * 2)

        drawLine(
            color = trackColor,
            start = Offset(radius, radius),
            end = Offset(size.width - radius, radius),
            cap = StrokeCap.Round,
            strokeWidth = strokeWidth
        )

        val startX = radius + (if (animatedProgress == 0f) 0.01f else 0f)
        val endX = radius + (usableWidth * animatedProgress)

        val progressBrush = if (progress >= 1f) {

            val pulsePosition = radius + (usableWidth * pulseOffset)
            val blurWidth = usableWidth * 0.3f

            Brush.linearGradient(
                0.0f to progressColor,
                0.5f to pulseColor,
                1.0f to progressColor,
                start = Offset(pulsePosition - blurWidth, radius),
                end = Offset(pulsePosition + blurWidth, radius),
                tileMode = TileMode.Clamp
            )
        } else {
            SolidColor(progressColor)
        }

        drawLine(
            brush = progressBrush,
            start = Offset(startX, radius),
            end = Offset(endX, radius),
            cap = StrokeCap.Round,
            strokeWidth = strokeWidth
        )
    }
}



@Composable
fun TopActionBar(
    state: HomeUiState,
    onLocationToggle: (Boolean) -> Unit,
    onNotificationsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. КНОПКА УВЕДОМЛЕНИЙ
        Box(modifier = Modifier.size(56.dp)) {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                onClick = { onNotificationsClick() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(id = R.drawable.baseline_notifications_24),
                        contentDescription = "Notifications",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(40.dp)
                    )

                }
            }
            if (state.notificationsCount > 0) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(20.dp)
                        .offset(x = (2).dp, y = (-2).dp),
                    shape = CircleShape,
                    color = Color.Red,
                ) {
                    Box(contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()) {
                        Text(
                            modifier = Modifier.offset(y = (-0.5).dp),
                            text = if (state.notificationsCount > 9) "9+" else state.notificationsCount.toString(),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            style = LocalTextStyle.current.copy(
                                lineHeight = 10.sp,
                                lineHeightStyle = LineHeightStyle(
                                    alignment = LineHeightStyle.Alignment.Center,
                                    trim = LineHeightStyle.Trim.Both
                                )
                            ),
                        )
                    }
                }
            }

        }


        Spacer(modifier = Modifier.width(12.dp))

        // 2. ПЕРЕКЛЮЧАТЕЛЬ ЛОКАЦИИ
        Surface(
            modifier = Modifier.height(52.dp),
            shape = RoundedCornerShape(16.dp),
            color = LocalAdditionColors.current.buttonReactionColor
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(start = 6.dp)
                    .offset(x = (-6).dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.base_location_38),
                    contentDescription = null,
                    modifier = Modifier.size(38.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Switch(
                    checked = state.isLocationEnabled,
                    onCheckedChange = onLocationToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = MaterialTheme.colorScheme.tertiary,
                        uncheckedTrackColor = Color.White.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .width(52.dp)
                        .height(32.dp)
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Surface(
            modifier = Modifier
                .width(112.dp)
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
            onClick = { /* TODO */ }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                LocalAdditionColors.current.centerGradientColor,
                                LocalAdditionColors.current.pinkForGradientColor
                            ),
                            start = Offset(0f, 0f),
                            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.nice),
                    contentDescription = "NICE",
                    tint = Color.Unspecified,
                    modifier = Modifier.size(width = 102.dp, height = 52.dp)
                )
            }
        }
    }
}



