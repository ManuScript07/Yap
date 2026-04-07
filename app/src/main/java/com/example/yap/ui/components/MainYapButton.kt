package com.example.yap.ui.components

import android.annotation.SuppressLint
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yap.ui.theme.LocalBaseScale
import kotlinx.coroutines.delay
import com.example.yap.R

// 1. ОПРЕДЕЛЯЕМ СОСТОЯНИЯ КНОПКИ
private enum class YapButtonState {
    IDLE, PRESSED, READY, FIRING
}

@SuppressLint("ConfigurationScreenWidthHeight")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainYapButton(
    price: Int,
    isEnoughStars: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val baseScale = LocalBaseScale.current // Предполагаю, что он у тебя объявлен через CompositionLocal

    val frontPillWidth = 196.dp * baseScale
    val frontPillHeight = 160.dp * baseScale
    val backPillMaxWidth = 260.dp * baseScale
    val backPillMaxHeight = 212.dp * baseScale

    // 2. ИНИЦИАЛИЗИРУЕМ ИНСТРУМЕНТЫ
    var buttonState by remember { mutableStateOf(YapButtonState.IDLE) }
    val haptic = LocalHapticFeedback.current

    // --- АНИМАЦИИ НА ОСНОВЕ СОСТОЯНИЯ ---

    val backgroundRotation by animateFloatAsState(
        targetValue = if (buttonState == YapButtonState.IDLE) -45f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "BgRotation"
    )

    // При готовности задняя плашка станет яркой (сольется с передней или выделится)
    val backColor by animateColorAsState(
        targetValue = when (buttonState) {
            YapButtonState.IDLE, YapButtonState.PRESSED -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            YapButtonState.READY, YapButtonState.FIRING -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f)// Цвет "заряженной" кнопки
        },
        animationSpec = tween(durationMillis = 150),
        label = "BgColor"
    )

    val currentBackWidth by animateDpAsState(
        targetValue = if (buttonState == YapButtonState.FIRING) frontPillWidth else backPillMaxWidth,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "BgWidth"
    )

    val currentBackHeight by animateDpAsState(
        targetValue = if (buttonState == YapButtonState.FIRING) frontPillHeight else backPillMaxHeight,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "BgHeight"
    )

    val pillShape = RoundedCornerShape(percent = 80)

    // 3. УПРАВЛЕНИЕ ЗАДЕРЖКАМИ И СОБЫТИЯМИ
    LaunchedEffect(buttonState) {
        when (buttonState) {
            YapButtonState.PRESSED -> {
                delay(150) // Короткая задержка: если держим палец, кнопка "заряжается"
                haptic.performHapticFeedback(HapticFeedbackType.LongPress) // Жесткая вибрация
                buttonState = YapButtonState.READY
            }
            YapButtonState.FIRING -> {
                delay(200) // Ждем завершения анимации уменьшения
                onClick()  // Вызываем твою функцию
                buttonState = YapButtonState.IDLE // Сбрасываем кнопку обратно
            }
            else -> {}
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier.size(width = backPillMaxWidth + 40.dp, height = backPillMaxHeight + 40.dp),
            contentAlignment = Alignment.Center
        ) {
            // --- ЗАДНЯЯ ПЛАШКА ---
            Surface(
                modifier = Modifier
                    .size(width = currentBackWidth, height = currentBackHeight) // Анимированные размеры
                    .rotate(backgroundRotation),
                shape = pillShape,
                color = backColor, // Анимированный цвет
            ) {}

            // --- ПЕРЕДНЯЯ ПЛАШКА ---
            Surface(
                modifier = Modifier
                    .size(width = frontPillWidth, height = frontPillHeight)
                    .shadow(elevation = 6.dp * baseScale, shape = pillShape)
                    .clip(pillShape)
                    // 4. ОБРАБОТКА ЖЕСТОВ
                    .pointerInput(isEnoughStars) {
                        if (!isEnoughStars) return@pointerInput

                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            buttonState = YapButtonState.PRESSED

                            val up = waitForUpOrCancellation()

                            if (up != null) {
                                // Палец отпустили в пределах кнопки
                                if (buttonState == YapButtonState.READY) {
                                    // Только если кнопка успела перейти в состояние READY (заряжена),
                                    // мы запускаем процесс отправки FIRING
                                    buttonState = YapButtonState.FIRING
                                } else {
                                    // Если отпустили слишком рано (состояние всё еще PRESSED),
                                    // просто даем легкий фидбек и отменяем действие
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    buttonState = YapButtonState.IDLE
                                }
                            } else {
                                // Жест отменен (увели палец в сторону)
                                buttonState = YapButtonState.IDLE
                            }
                        }
                    }   ,
                shape = pillShape,
                color = MaterialTheme.colorScheme.primary,
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(id = R.drawable.yap_button_big_text), // Замени на свой ID
                        contentDescription = "YAP Logo",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(width = 120.dp * baseScale, height = 60.dp * baseScale)
                    )

                    Surface(
                        modifier = Modifier
                            .offset(y = 50.dp * baseScale)
                            .size(width = 60.dp * baseScale, height = 32.dp * baseScale),
                        shape = RoundedCornerShape(18.dp * baseScale),
                        color = MaterialTheme.colorScheme.tertiary,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "$price",
                                color = MaterialTheme.colorScheme.background,
                                fontWeight = FontWeight.Bold,
                                fontSize = (18 * baseScale).sp,
                                lineHeight = (16 * baseScale).sp
                            )
                            Spacer(modifier = Modifier.width(5.dp * baseScale))
                            Icon(
                                painter = painterResource(R.drawable.star), // Замени на свой ID
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.background,
                                modifier = Modifier.size(20.dp * baseScale)
                            )
                        }
                    }
                }
            }
        }
    }
}