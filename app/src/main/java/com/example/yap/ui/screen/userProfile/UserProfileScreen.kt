package com.example.yap.ui.screen.userProfile

import android.annotation.SuppressLint
import android.app.Application
import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.yap.R
import com.example.yap.ui.components.FullScreenAvatarViewer
import com.example.yap.ui.screen.home.HomeViewModel
import com.example.yap.ui.screen.home.YapType
import com.example.yap.ui.theme.LocalAdditionColors
import com.example.yap.ui.theme.LocalBaseScale
import com.example.yap.util.compose.SystemBarsIconsColor
import com.example.yap.util.extension.SystemStatusPill
import com.example.yap.util.fetchLocationAndSendDirectYap
import com.example.yap.util.formatBirthday
import androidx.compose.ui.platform.LocalResources
import com.example.yap.ui.screen.addUser.AddFriendStatus
import com.example.yap.ui.screen.friends.RemoveFriendDialog
import com.example.yap.util.compose.rememberLambda

@SuppressLint("LocalContextGetResourceValueCall")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    userId: String,
    onBack: () -> Unit,
    homeViewModel: HomeViewModel,
    viewModel: UserProfileViewModel = viewModel(
        factory = UserProfileViewModel.provideFactory(
            application = LocalContext.current.applicationContext as Application,
            userId = userId
        )
    ),
    onNavigateToFriendsList: (String) -> Unit,
) {

    SystemBarsIconsColor(isLight = true)
    val state by viewModel.state.collectAsState()
    val homeState by homeViewModel.state.collectAsState()
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val baseScale = LocalBaseScale.current


    val onBackClick = remember { { onBack() } }

    val isLocationEnabled by remember { derivedStateOf { homeState.isLocationEnabled } }

    var isMenuVisible by remember { mutableStateOf(false) }
    var showRemoveDialog by remember { mutableStateOf(false) }

    val onShareProfile = {
        val userId = state.user?.id ?: ""
        val userCode = state.user?.userCode ?: ""
        val deepLinkUrl = "https://yap.app/profile/$userId"

        val shareMessage = context.getString(R.string.share_profile_message, userCode, deepLinkUrl)

        val sendIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, shareMessage)
            type = "text/plain"
        }

        context.startActivity(Intent.createChooser(sendIntent, null))
    }

    val onYapSend = remember(state.user, homeViewModel, context, isLocationEnabled) {
        {
            state.user?.let { user ->
                fetchLocationAndSendDirectYap(
                    context = context,
                    isLocationEnabled = isLocationEnabled,
                    onLocationReady = { lat, lon ->
                        homeViewModel.handleDirectSend(
                            userId = user.id,
                            latitude = lat,
                            longitude = lon,
                            type = YapType.YAP
                        )
                    }
                )
            }
            Unit
        }
    }

    val onToggleQuickList = remember(viewModel) {
        { viewModel.toggleQuickList() }
    }

    val onAddFriendClick = remember(viewModel, homeViewModel) {
        {
            viewModel.sendFriendRequest { resId, isSuccess ->
                homeViewModel.showStatus(
                    resId = resId,
                    isSuccess = isSuccess
                )
            }
        }
    }

    val guardedNavigateToFriendsList = rememberLambda<String> { targetUserId ->
        onNavigateToFriendsList(targetUserId)
    }


    val startGradient = MaterialTheme.colorScheme.primary
    val centerGradient = LocalAdditionColors.current.centerGradientColor
    val endGradient = LocalAdditionColors.current.pinkForGradientColor


    val gradientBrush = remember {
        Brush.linearGradient(
            colors = listOf(
                startGradient,
                centerGradient,
                endGradient
            )
        )
    }

    val backgroundColor = LocalAdditionColors.current.speedBottomDialog
    val cardColor = LocalAdditionColors.current.descriptionSurfaceColor


    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().background(backgroundColor)) {

            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = LocalAdditionColors.current.toggleButtonColor)
            } else if (state.error != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp * baseScale),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = state.error ?: stringResource(R.string.error_generic),
                        fontSize = 20.sp * baseScale,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                val user = state.user!!

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        // 1. Шапка
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(240.dp * baseScale)
                                .background(gradientBrush)
                        )

                        // 2. Тело профиля
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 190.dp * baseScale),
                            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                            color = backgroundColor
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp * baseScale),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Spacer(modifier = Modifier.height(80.dp * baseScale))

                                Text(
                                    text = user.name,
                                    fontSize = 32.sp * baseScale,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )


                                Spacer(modifier = Modifier.height(16.dp * baseScale))

                                // Кнопки действий
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp * baseScale),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ProfileYapButton(
                                        onClick = onYapSend,
                                        baseScale = baseScale,
                                        modifier = Modifier.size(width = 116.dp * baseScale, height = 58.dp * baseScale)
                                    )

                                    Button(
                                        onClick = { onToggleQuickList() },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = LocalAdditionColors.current.toggleButtonColor
                                        ),
                                        shape = RoundedCornerShape(20.dp * baseScale),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(58.dp * baseScale),
                                        contentPadding = PaddingValues(horizontal = 8.dp * baseScale)
                                    ) {
                                        Text(
                                            text = if (state.isUserInQuickList)
                                                stringResource(R.string.remove_from_list)
                                            else
                                                stringResource(R.string.add_to_list),
                                            color = Color.White,
                                            fontSize = 20.sp * baseScale,
                                            fontWeight = FontWeight.SemiBold,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            softWrap = false
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp * baseScale))

                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(20.dp),
                                    color = cardColor
                                ) {
                                    Column(modifier = Modifier.padding(20.dp * baseScale)) {
                                        if (user.bio.isNotEmpty()) {
                                            ProfileInfoItem(user.bio, stringResource(R.string.about_me), baseScale)
                                        }

                                        if (user.username.isNotEmpty()) {
                                            if (user.bio.isNotEmpty()) Spacer(modifier = Modifier.height(20.dp * baseScale))

                                            ProfileInfoItem("@${user.username}", stringResource(R.string.user_name), baseScale)
                                        }

                                        val birthday = formatBirthday(user.dobTimestamp, user.showOnlyDay)
                                        if (birthday.isNotEmpty()) {
                                            if (user.bio.isNotEmpty() || user.username.isNotEmpty()) {
                                                Spacer(modifier = Modifier.height(20.dp * baseScale))
                                            }

                                            ProfileInfoItem(birthday, stringResource(R.string.your_birthday), baseScale)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp * baseScale))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp * baseScale)
                                ) {

                                    val friendsCount = user.friends.size

                                    val friendsText = if (friendsCount == 0) {
                                        stringResource(R.string.no_friends)
                                    } else {
                                        LocalResources.current.getQuantityString(
                                            R.plurals.friends_count,
                                            friendsCount,
                                            friendsCount
                                        )
                                    }
                                    Button(
                                        onClick = {
                                            if (user.friends.isNotEmpty()) {
                                                guardedNavigateToFriendsList(userId)
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = LocalAdditionColors.current.checkBackgroundColor
                                        ),
                                        shape = RoundedCornerShape(20.dp * baseScale),
                                        // Убрали width(), добавили минимальную ширину, чтобы кнопка не была слишком узкой при 0-1 друге
                                        modifier = Modifier
                                            .widthIn(min = 120.dp * baseScale)
                                            .height(52.dp * baseScale),
                                        // Добавляем внутренние отступы, чтобы текст не касался краев
                                        contentPadding = PaddingValues(horizontal = 20.dp * baseScale)
                                    ) {
                                        Text(
                                            text = friendsText,
                                            color = Color.White,
                                            letterSpacing = 1.sp * baseScale,
                                            fontSize = 20.sp * baseScale,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1 // Чтобы текст не прыгал на вторую строку
                                        )
                                    }

                                    val addFriendStatus = state.addFriendStatus
                                    val isCanAdd = addFriendStatus == AddFriendStatus.CAN_ADD && !state.isFriend

                                    val (icon, text, contentColor) = when (addFriendStatus) {
                                        AddFriendStatus.PENDING -> Triple(
                                            R.drawable.waiting,
                                            R.string.sending,
                                            LocalAdditionColors.current.secondTextColor // Серый цвет во время отправки
                                        )
                                        AddFriendStatus.ALREADY_FRIEND -> Triple(
                                            R.drawable.person_check_24,
                                            R.string.already_friends,
                                            LocalAdditionColors.current.secondTextColor // Серый цвет, если уже друзья
                                        )
                                        else -> Triple(
                                            R.drawable.outline_person_add_32,
                                            R.string.add,
                                            Color.Black // Черный цвет, когда можно добавить
                                        )
                                    }

                                    Button(
                                        onClick = onAddFriendClick,
                                        enabled = isCanAdd,
                                        colors = ButtonDefaults
                                            .buttonColors(
                                                containerColor = LocalAdditionColors.current.darkYapButtonBackgroundColor,
                                                disabledContainerColor = LocalAdditionColors.current.disabledYabBackgroundColor

                                            ),
                                        shape = RoundedCornerShape(50),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(52.dp * baseScale)
                                    ) {


                                        Icon(
                                            painter = painterResource(id = icon),
                                            contentDescription = null,
                                            tint = contentColor,
                                            modifier = Modifier.size(36.dp * baseScale)
                                        )
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Text(
                                            text = stringResource(text),
                                            color = contentColor,
                                            fontSize = 20.sp * baseScale,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            softWrap = false,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                    }
                                }
                            }
                        }

                        // 3. Аватарка
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 112.dp * baseScale),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = user.avatarUrl ?: R.drawable.avatar_1,
                                contentDescription = "Avatar",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(160.dp * baseScale)
                                    .border(
                                        width = 4.dp * baseScale,
                                        color = LocalAdditionColors.current.speedBottomDialog,
                                        shape = CircleShape
                                    )
                                    .padding(0.5.dp)

                                    // 3. Теперь обрезаем картинку
                                    .clip(CircleShape)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = ripple(bounded = false, radius = 80.dp * baseScale),
                                        onClick = { viewModel.toggleAvatarViewer(true) } // Вызов метода VM
                                    ),
                                placeholder = painterResource(id = R.drawable.avatar_1),
                                error = painterResource(id = R.drawable.avatar_1),
                                fallback = painterResource(R.drawable.avatar_1)
                            )
                        }
                    }
                }
            }

            UserProfileTopBar(
                onBack = onBackClick,
                onMenuClick = { isMenuVisible = true },
                baseScale = baseScale
            )

            ProfileActionsPopup(
                isVisible = isMenuVisible,
                isFriend = state.isFriend,
                isMuted = state.isMuted,
                onDismiss = { isMenuVisible = false },
                onMuteClick = { viewModel.toggleMute() },
                onShareClick = onShareProfile,
                onDeleteClick = { showRemoveDialog = true } // Открываем диалог подтверждения
            )

            if (showRemoveDialog) {
                RemoveFriendDialog(
                    friendId = state.user?.id, // Передаем ID, просто чтобы компонент не ругался
                    onConfirm = {
                        viewModel.removeFriend { isSuccess ->
                            if (!isSuccess) {
                                // Здесь можно вызвать homeViewModel.showStatus для ошибки
                                homeViewModel.showStatus(
                                    resId = R.string.error_generic,
                                    isSuccess = false
                                )
                            }
                        }
                        showRemoveDialog = false
                    },
                    onDismiss = { showRemoveDialog = false }
                )
            }

        }
        // В самом конце экрана
        if (state.isAvatarViewerOpen && state.user != null) {
            FullScreenAvatarViewer(
                avatarUrl = state.user?.avatarUrl,
                userName = state.user?.name ?: "",
                onClose = { viewModel.toggleAvatarViewer(false) }, // Закрытие через VM
                baseScale = baseScale
            )
        }
        SystemStatusPill(
            statusResource = homeState.systemStatusResource,
            statusMessage = homeState.systemStatusMessage,
            statusId = homeState.statusId,
            isSuccess = homeState.isStatusSuccess
        )
    }
}
@Composable
fun ProfileInfoItem(title: String, subtitle: String, baseScale: Float) {
    Column {
        Text(
            text = title,
            fontSize = 18.sp * baseScale,
            fontWeight = FontWeight.Medium,
            lineHeight = 16.sp * baseScale,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = subtitle,
            fontSize = 14.sp * baseScale,
            color = LocalAdditionColors.current.unselectedColor,
            lineHeight = 16.sp * baseScale
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProfileYapButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    baseScale: Float = 1f
) {
    val backgroundColor = LocalAdditionColors.current.darkYapButtonBackgroundColor
    val iconTint = Color.Black

    val interactionSource = remember { MutableInteractionSource() }
    val rippleIndication = ripple(bounded = true)

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(backgroundColor)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = rippleIndication,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = R.drawable.yap_button_text),
            contentDescription = "YAP Logo",
            tint = iconTint,
            modifier = Modifier
                // УВЕЛИЧИЛИ коэффициент до 0.7f (70% от высоты кнопки)
                .width(84.dp * baseScale)
                .height(32.dp * baseScale)
                // Уменьшили паддинг, чтобы иконка росла шире
                .padding(horizontal = 4.dp * baseScale)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileTopBar(
    onBack: () -> Unit,
    onMenuClick: () -> Unit,
    baseScale: Float
) {
    TopAppBar(
        title = { }, // Заголовок пустой, так как имя пользователя находится в карточке
        navigationIcon = {
            // Используем логику из твоего BaseTopAppBar (без стандартного риппла)
            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clickable(
                        onClick = onBack,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null // Убираем стандартный круг нажатия
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.baseline_arrow_back_24),
                    contentDescription = "Назад",
                    modifier = Modifier.size(32.dp),
                    tint = Color.Black
                )
            }
        },
        actions = {
            // Кнопка меню в том же стиле
            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .padding(end = 8.dp * baseScale)
                    .clickable(
                        onClick = onMenuClick,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.more_vert),
                    contentDescription = "Меню",
                    modifier = Modifier.size(32.dp * baseScale),
                    tint = Color.Black
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent, // Важно: прозрачный фон
            scrolledContainerColor = Color.Transparent,
            navigationIconContentColor = Color.Black,
            actionIconContentColor = Color.Black
        )
    )
}
