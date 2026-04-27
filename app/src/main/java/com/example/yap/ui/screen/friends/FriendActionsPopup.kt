package com.example.yap.ui.screen.friends

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.example.yap.R
import com.example.yap.ui.theme.LocalAdditionColors
import com.example.yap.ui.theme.LocalBaseScale

@Composable
fun FriendActionsPopup(
    isVisible: Boolean,
    isMuted: Boolean,
    onDismiss: () -> Unit,
    onMuteClick: () -> Unit,
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
                    height = 112.dp * baseScale
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
                        shape = RoundedCornerShape(18.dp * baseScale),
                        color = LocalAdditionColors.current.searchSurfaceColor,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // --- ПЕРВАЯ СТРОКА: ЗВУК ---
                            PopupActionRow(
                                iconRes =
                                    if (isMuted) R.drawable.outline_notifications_24
                                    else R.drawable.outline_notifications_off_24,
                                text = if (isMuted)
                                    stringResource(R.string.turn_on_notif)
                                else stringResource(R.string.turn_off_notif),
                                baseScale = baseScale,
                                onClick = {
                                    onMuteClick()
                                    onDismiss()
                                }
                            )

                            // Разделитель между кнопками
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 12.dp * baseScale),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                            )

                            // --- ВТОРАЯ СТРОКА: УДАЛЕНИЕ ---
                            PopupActionRow(
                                iconRes = R.drawable.outline_delete_24,
                                text = stringResource(R.string.delete_friend),
                                textColor = MaterialTheme.colorScheme.error,
                                baseScale = baseScale,
                                onClick = {
                                    onDeleteClick()
                                    onDismiss()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ColumnScope.PopupActionRow(
    iconRes: Int,
    text: String,
    baseScale: Float,
    onClick: () -> Unit,
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp * baseScale),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = textColor,
            modifier = Modifier.size(24.dp * baseScale)
        )

        Spacer(modifier = Modifier.width(12.dp * baseScale))

        Text(
            text = text,
            color = textColor,
            fontSize = (18 * baseScale).sp,
            fontWeight = FontWeight.Medium
        )
    }
}