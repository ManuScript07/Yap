package com.example.yap.ui.screen.userProfile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.example.yap.R
import com.example.yap.ui.screen.friends.PopupActionRow
import com.example.yap.ui.theme.LocalAdditionColors
import com.example.yap.ui.theme.LocalBaseScale

@Composable
fun ProfileActionsPopup(
    isVisible: Boolean,
    isFriend: Boolean,
    isMuted: Boolean,
    onDismiss: () -> Unit,
    onMuteClick: () -> Unit,
    onShareClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val baseScale = LocalBaseScale.current
    val density = LocalDensity.current

    // Используем MutableTransitionState для полного контроля над фазами анимации
    val transitionState = remember {
        MutableTransitionState(false).apply {
            targetState = isVisible
        }
    }

    // Синхронизируем состояние
    LaunchedEffect(isVisible) {
        transitionState.targetState = isVisible
    }

    var isProcessing by remember { mutableStateOf(false) }

    // Сбрасываем флаг блокировки, когда попап открывается заново
    LaunchedEffect(isVisible) {
        if (isVisible) isProcessing = false
    }


    val offsetX = remember(density, baseScale) { with(density) { ((-16).dp * baseScale).toPx().toInt() } }
    val offsetY = remember(density, baseScale) { with(density) { (48.dp * baseScale).toPx().toInt() } }

    // Popup живет, пока targetState == true ИЛИ пока анимация еще проигрывается
    if (transitionState.targetState || !transitionState.isIdle) {
        Popup(
            alignment = Alignment.TopEnd,
            onDismissRequest = onDismiss,
            offset = IntOffset(x = offsetX, y = offsetY),
            properties = PopupProperties(focusable = true)
        ) {
            val popupHeight = if (isFriend) 168.dp else 112.dp

            Box(
                modifier = Modifier.size(
                    width = 248.dp * baseScale,
                    height = popupHeight * baseScale
                ),
                contentAlignment = Alignment.TopCenter
            ) {
                AnimatedVisibility(
                    visibleState = transitionState, // Используем стейт вместо Boolean
                    enter = fadeIn(animationSpec = tween(200)) +
                            scaleIn(initialScale = 0.95f, transformOrigin = TransformOrigin(1f, 0f), animationSpec = tween(200)),
                    exit = fadeOut(animationSpec = tween(150)) +
                            scaleOut(targetScale = 0.95f, transformOrigin = TransformOrigin(1f, 0f), animationSpec = tween(150))
                ) {
                    Surface(
                        shape = RoundedCornerShape(18.dp * baseScale),
                        color = LocalAdditionColors.current.searchSurfaceColor,
                        shadowElevation = 4.dp,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Column(modifier = Modifier
                            .fillMaxSize()
                            .alpha(if (isProcessing) 0.5f else 1f)) {
                            // --- ПОДЕЛИТЬСЯ ---
                            PopupActionRow(
                                iconRes = R.drawable.outline_share_32,
                                text = stringResource(R.string.share),
                                baseScale = baseScale,
                                onClick = {
                                    if (!isProcessing) {
                                        isProcessing = true
                                        onShareClick()
                                        onDismiss()
                                    }
                                }
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 12.dp * baseScale),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                            )

                            // --- ЗВУК ---
                            PopupActionRow(
                                iconRes = if (isMuted) R.drawable.outline_notifications_24 else R.drawable.outline_notifications_off_24,
                                text = if (isMuted) stringResource(R.string.turn_on_notif) else stringResource(R.string.turn_off_notif),
                                baseScale = baseScale,
                                onClick = {
                                    if (!isProcessing) {
                                        isProcessing = true
                                        onMuteClick()
                                        onDismiss()
                                    }
                                }
                            )

                            // --- УДАЛЕНИЕ ---
                            if (isFriend) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 12.dp * baseScale),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                                )

                                PopupActionRow(
                                    iconRes = R.drawable.outline_delete_24,
                                    text = stringResource(R.string.delete_friend),
                                    textColor = MaterialTheme.colorScheme.error,
                                    baseScale = baseScale,
                                    onClick = {
                                        if (!isProcessing) {
                                            isProcessing = true
                                            onDeleteClick()
                                            onDismiss()
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

