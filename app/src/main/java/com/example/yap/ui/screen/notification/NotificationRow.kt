package com.example.yap.ui.screen.notification

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.yap.R
import com.example.yap.ui.components.YapActionButton
import com.example.yap.ui.theme.LocalAdditionColors
import com.example.yap.ui.theme.LocalBaseScale
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationRow(
    item: NotificationItemModel,
    modifier: Modifier = Modifier,
    onDelete: () -> Unit,
    onMute: () -> Unit,
    onNavigateToProfile: (String) -> Unit,
    onLocationClick: () -> Unit,
    onYapClick: () -> Unit,
    onYapSend: () -> Unit,
    onListenClick: () -> Unit
) {
    val baseScale = LocalBaseScale.current
    val currentOnMute by rememberUpdatedState(onMute)
    val currentOnDelete by rememberUpdatedState(onDelete)

    // Правильная стабилизация лямбд, которые принимают String (ID)
    // Мы просто запоминаем саму функцию, которую нам передали сверху.
    val memoizedYapClick = remember(item) { { _: String -> onYapClick() } }
    val memoizedYapSend = remember(item) { { _: String -> onYapSend() } }
    val memoizedLocationClick = remember(item) { { onLocationClick() } }
    val memoizedNavigate = remember(item) { onNavigateToProfile }
    val memoizedListenClick = remember(item) { { onListenClick() } }


    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { direction ->
            direction != SwipeToDismissBoxValue.StartToEnd
        },
        positionalThreshold = { it * 0.5f }
    )

    LaunchedEffect(dismissState.targetValue) {
        if (dismissState.targetValue == SwipeToDismissBoxValue.StartToEnd) {
            currentOnMute()

            dismissState.reset()
        }
    }

    val isSettledAsDeleted = dismissState.currentValue == SwipeToDismissBoxValue.EndToStart

    AnimatedVisibility(
        visible = !isSettledAsDeleted,
        exit = shrinkVertically(animationSpec = tween(400)) + fadeOut(tween(200)),
        modifier = modifier
    ) {
        LaunchedEffect(isSettledAsDeleted) {
            if (isSettledAsDeleted) {
                delay(400)
                currentOnDelete()
            }
        }

        SwipeToDismissBox(
            state = dismissState,
            modifier = Modifier.fillMaxWidth(),
            backgroundContent = {
                SwipeBackground(
                    dismissState = dismissState,
                    isMuted = item.user.isMuted,
                    baseScale = baseScale
                )
            }
        ) {
            NotificationCardContent(
                item = item,
                baseScale = baseScale,
                onLocationClick = memoizedLocationClick,
                onYapClick = memoizedYapClick,
                onYapSend = memoizedYapSend,
                onNavigateToProfile = memoizedNavigate,
                onListenClick = memoizedListenClick
            )
        }
    }
}

@Composable
private fun NotificationCardContent(
    item: NotificationItemModel,
    baseScale: Float,
    onLocationClick: () -> Unit,
    onYapClick: (String) -> Unit,
    onYapSend: (String) -> Unit,
    onNavigateToProfile: (String) -> Unit,
    onListenClick: () -> Unit
) {

    val isVoice = item.audioUrl != null

    val showPlaceholder = isVoice && item.messageText.isNullOrBlank()

    val textToShow = if (showPlaceholder) {
        stringResource(R.string.voice_message_placeholder)
    } else {
        item.messageText ?: ""
    }

    // 1. Создаем правильный запрос с жестким кэшированием


    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isVoice) Modifier.clickable { onListenClick() }
                else Modifier
            ),
        color = MaterialTheme.colorScheme.background

    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp * baseScale, vertical = 14.dp * baseScale)
                .fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            AsyncImage(
                model = item.user.avatarUrl ?: R.drawable.avatar_1,
                contentDescription = "User Avatar",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp * baseScale)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        onNavigateToProfile(item.user.id)
                    },
                // Плейсхолдер и ошибка — используем локальный ресурс
                placeholder = painterResource(id = R.drawable.avatar_1),
                error = painterResource(id = R.drawable.avatar_1),
                fallback = painterResource(R.drawable.avatar_1)
            )

            Spacer(modifier = Modifier.width(16.dp * baseScale))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = item.user.name,
                    fontSize = (18 * baseScale).sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground,
                    lineHeight = (24 * baseScale).sp,
                    style = LocalTextStyle.current.copy(
                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                    )
                )

                if (textToShow.isNotEmpty()) {
                    Text(
                        text = textToShow,
                        fontSize = (16 * baseScale).sp,
                        // Если это заглушка, подсвечиваем её цветом бренда
                        color = LocalAdditionColors.current.notifText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = (20 * baseScale).sp
                    )
                }


                if (item.hasLocation) {
                    NotificationActionText(
                        text = stringResource(R.string.location),
                        baseScale = baseScale,
                        onClick = onLocationClick
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp * baseScale))

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.height(60.dp * baseScale)
            ) {
                Text(
                    text = item.timeAgo,
                    fontSize = (14 * baseScale).sp,
                    color = LocalAdditionColors.current.notifText,
                    style = LocalTextStyle.current.copy(
                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                    )
                )

                YapActionButton(
                    user = item.user,
                    onYapClick = onYapClick,
                    onLongYapClick = onYapSend,
                )
            }
        }
    }
}

@Composable
fun NotificationActionText(
    text: String,
    baseScale: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        modifier = modifier.clickable { onClick() },
        fontSize = (16 * baseScale).sp,
        color = LocalAdditionColors.current.notifText,
        textDecoration = TextDecoration.Underline,
        lineHeight = (20 * baseScale).sp,
        style = LocalTextStyle.current.copy(
            platformStyle = PlatformTextStyle(includeFontPadding = false)
        )
    )
}

@Composable
private fun SwipeBackground(
    dismissState: SwipeToDismissBoxState,
    isMuted: Boolean,
    baseScale: Float
) {
    val direction = dismissState.dismissDirection
    val target = dismissState.targetValue


    val haptic = LocalHapticFeedback.current

    val secondaryColor = MaterialTheme.colorScheme.secondary
    val deleteColor = LocalAdditionColors.current.deleteColor

    LaunchedEffect(target) {
        if (target != SwipeToDismissBoxValue.Settled) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    // 2. Оборачиваем вычисления в remember, чтобы они не дергались лишний раз
    val backgroundColor = remember(direction) {
        when (direction) {
            SwipeToDismissBoxValue.StartToEnd -> secondaryColor
            SwipeToDismissBoxValue.EndToStart -> deleteColor
            else -> Color.Transparent
        }
    }

    val iconRes = remember(direction, target, isMuted) {
        when (direction) {
            SwipeToDismissBoxValue.StartToEnd -> {
                val willBeMuted = if (target == SwipeToDismissBoxValue.StartToEnd) !isMuted else isMuted
                if (willBeMuted) R.drawable.outline_notifications_off_24 else R.drawable.outline_notifications_24
            }
            SwipeToDismissBoxValue.EndToStart -> R.drawable.outline_delete_24
            else -> null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(horizontal = 24.dp * baseScale),
        contentAlignment = if (direction == SwipeToDismissBoxValue.StartToEnd)
            Alignment.CenterStart else Alignment.CenterEnd
    ) {
        iconRes?.let { resId ->
            Icon(
                painter = painterResource(id = resId),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .size(28.dp * baseScale)
                    .graphicsLayer {
                        // 3. Читаем progress ТОЛЬКО ЗДЕСЬ.
                        // Теперь изменения прогресса меняют свойства слоя, не вызывая рекомпозицию.
                        val progress = dismissState.progress

                        alpha = (progress * 1.4f + 0.4f).coerceIn(0f, 1f)
                        val scale = 0.9f + (progress.coerceIn(0f, 1f) * 0.2f)
                        scaleX = scale
                        scaleY = scale
                    }
            )
        }
    }
}