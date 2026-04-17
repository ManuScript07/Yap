package com.example.yap.ui.screen.notification

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.key
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
import com.example.yap.R
import com.example.yap.ui.components.YapActionButton
import com.example.yap.ui.theme.LocalAdditionColors
import com.example.yap.ui.theme.LocalBaseScale
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationRow(
    item: NotificationItemModel,
    onDelete: () -> Unit,
    onMute: () -> Unit,
    onNavigateToProfile: (Int) -> Unit,
    onLocationClick: () -> Unit,
    onYapClick: (Int) -> Unit,
    onYapSend: (Int) -> Unit,
) {
    val baseScale = LocalBaseScale.current
    val currentOnMute by rememberUpdatedState(onMute)
    val currentOnDelete by rememberUpdatedState(onDelete)

    key(item.id) {
        val dismissState = rememberSwipeToDismissBoxState(
            confirmValueChange = { direction ->
                when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> {
                        currentOnMute()
                        false // Пружина
                    }
                    SwipeToDismissBoxValue.EndToStart -> true // Разрешаем зафиксировать удаление
                    else -> false
                }
            },
            positionalThreshold = { it * 0.5f } // Порог 50% ширины для уверенности
        )

        // ИСПРАВЛЕНИЕ 1: Скрываем контент только когда жест ЗАВЕРШЕН (currentValue)
        val isSettledAsDeleted = dismissState.currentValue == SwipeToDismissBoxValue.EndToStart

        AnimatedVisibility(
            visible = !isSettledAsDeleted,
            enter = expandVertically(),
            exit = shrinkVertically(animationSpec = tween(400)) + fadeOut(tween(200))
        ) {
            // ИСПРАВЛЕНИЕ 2: Удаляем из данных только после фиксации состояния
            LaunchedEffect(isSettledAsDeleted) {
                if (isSettledAsDeleted) {
                    delay(400) // Ждем завершения shrinkVertically
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
                    onLocationClick = onLocationClick,
                    onYapClick = onYapClick,
                    onYapSend = onYapSend,
                    onNavigateToProfile = onNavigateToProfile
                )
            }
        }
    }
}

@Composable
private fun NotificationCardContent(
    item: NotificationItemModel,
    baseScale: Float,
    onLocationClick: () -> Unit,
    onYapClick: (Int) -> Unit,
    onYapSend: (Int) -> Unit,
    onNavigateToProfile: (Int) -> Unit
) {
    val messageLineHeight = if (item.hasLocation) (20 * baseScale).sp else (24 * baseScale).sp

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp * baseScale, vertical = 14.dp * baseScale)
                .fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Image(
                painter = painterResource(id = item.user.avatarRes),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp * baseScale)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        onNavigateToProfile(item.user.id)
                    }
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

                item.messageText?.let {
                    Text(
                        text = it,
                        fontSize = (16 * baseScale).sp,
                        color = LocalAdditionColors.current.notifText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = messageLineHeight,
                        style = LocalTextStyle.current.copy(
                            platformStyle = PlatformTextStyle(includeFontPadding = false)
                        )
                    )
                }

                if (item.hasLocation) {
                    Column(
                        modifier = Modifier
                            .clickable { onLocationClick() }
                    ) {
                        Text(
                            text = stringResource(R.string.location),
                            fontSize = (16 * baseScale).sp,
                            color = LocalAdditionColors.current.notifText,
                            textDecoration = TextDecoration.Underline,
                            lineHeight = (20 * baseScale).sp,
                            style = LocalTextStyle.current.copy(
                                platformStyle = PlatformTextStyle(includeFontPadding = false)
                            )
                        )
                    }
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
//                    baseScale = baseScale
                )
            }
        }
    }
}

@Composable
private fun SwipeBackground(
    dismissState: SwipeToDismissBoxState,
    isMuted: Boolean,
    baseScale: Float
) {
    val direction = dismissState.dismissDirection
    val target = dismissState.targetValue
    val progress = dismissState.progress

    // Подключаем вибрацию
    val haptic = LocalHapticFeedback.current

    // Вибрируем (щелчок), когда targetValue меняется (пересечение порога)
    LaunchedEffect(target) {
        if (target != SwipeToDismissBoxValue.Settled) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            // Используем LongPress или TextHandleMove для отчетливого "тика"
        }
    }

    val backgroundColor = when (direction) {
        SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.secondary
        SwipeToDismissBoxValue.EndToStart -> LocalAdditionColors.current.deleteColor
        else -> Color.Transparent
    }

    val iconRes = when (direction) {
        SwipeToDismissBoxValue.StartToEnd -> {
            val willBeMuted = if (target == SwipeToDismissBoxValue.StartToEnd) !isMuted else isMuted
            if (willBeMuted) R.drawable.outline_notifications_off_24 else R.drawable.outline_notifications_24
        }
        SwipeToDismissBoxValue.EndToStart -> R.drawable.outline_delete_24
        else -> null
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
            // ОПТИМИЗАЦИЯ ВИДИМОСТИ:
            // 1. Alpha стартует не с 0, а с 0.2, и достигает 1.0 быстрее (на 70% свайпа)
            val animatedAlpha = (progress * 1.4f + 0.4f).coerceIn(0f, 1f)

            // 2. Scale теперь стартует с 0.8 (вместо 0.5), чтобы иконку было сразу видно
            val animatedScale = 0.9f + (progress.coerceIn(0f, 1f) * 0.2f)

            Icon(
                painter = painterResource(id = resId),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .size(28.dp * baseScale)
                    .graphicsLayer {
                        alpha = animatedAlpha
                        scaleX = animatedScale
                        scaleY = animatedScale
                    }
            )
        }
    }
}