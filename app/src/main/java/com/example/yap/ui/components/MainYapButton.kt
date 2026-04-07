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
import com.example.yap.ui.screen.home.HomeViewModel

// 1. ОПРЕДЕЛЯЕМ СОСТОЯНИЯ КНОПКИ
private enum class YapButtonState {
    IDLE, PRESSED, READY, FIRING, RECORDING
}

@SuppressLint("ConfigurationScreenWidthHeight", "DefaultLocale")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainYapButton(
    price: Int,
    isEnoughStars: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel
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


    var offsetY by remember { mutableStateOf(0f) }
    val recordTimer = remember { mutableStateOf(0) }
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
            YapButtonState.READY, YapButtonState.RECORDING, YapButtonState.FIRING -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f)// Цвет "заряженной" кнопки
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
                viewModel.showAlert(resId = R.string.hold_to_record, canClose = false)
            }

            YapButtonState.READY -> {
                delay(600) // Пауза перед началом записи
                if (buttonState == YapButtonState.READY) { // Проверка, что палец всё еще там
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress) // Повторный тик — старт записи
                    buttonState = YapButtonState.RECORDING
                }

                // Здесь можно вызвать viewModel.startRecording()
            }

            YapButtonState.RECORDING -> {
                viewModel.showAlert(message = "Запись голосового сообщения...", canClose = false)

                // Запуск таймера
                // viewModel.startVoiceRecording()
                while (buttonState == YapButtonState.RECORDING) {
                    delay(1000)
                    recordTimer.value++
                }
            }

            YapButtonState.FIRING -> {
                delay(200) // Ждем завершения анимации уменьшения
                onClick()  // Вызываем твою функцию
                buttonState = YapButtonState.IDLE // Сбрасываем кнопку обратно
                recordTimer.value = 0
                offsetY = 0f
            }
            else -> {
                recordTimer.value = 0
                offsetY = 0f
            }
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
                    .offset(y = offsetY.dp/5)
                    .size(width = frontPillWidth, height = frontPillHeight)
                    .shadow(elevation = 6.dp * baseScale, shape = pillShape)
                    .clip(pillShape)
                    // 4. ОБРАБОТКА ЖЕСТОВ
                    .pointerInput(isEnoughStars) {
                        if (!isEnoughStars) return@pointerInput

                        awaitEachGesture {
                            val down = awaitFirstDown()
                            buttonState = YapButtonState.PRESSED

                            var isInside = true // Флаг: находится ли палец в границах кнопки

                            do {
                                val event = awaitPointerEvent()
                                val change = event.changes.first()
                                val position = change.position

                                // Проверяем, не вышел ли палец за границы передней плашки
                                isInside = position.x in 0f..size.width.toFloat() &&
                                        position.y in 0f..size.height.toFloat()

                                if (buttonState == YapButtonState.RECORDING) {
                                    offsetY = position.y - down.position.y

                                    // Логика отмены (свайп вниз)
                                    if (offsetY > 200f) {
                                        buttonState = YapButtonState.IDLE
                                    }
                                    // Логика закрепления (свайп вверх)
                                    else if (offsetY < -200f) {
                                        // buttonState = YapButtonState.LOCKED
                                    }
                                }

                                change.consume()
                            } while (event.changes.any { it.pressed })

                            // ПАЛЕЦ ПОДНЯЛИ: решаем, что делать
                            when {
                                // Если мы были в режиме записи и отпустили (не отменив свайпом)
                                buttonState == YapButtonState.RECORDING -> {
                                    buttonState = YapButtonState.FIRING
                                }

                                // Если мы были в READY и отпустили ВНУТРИ кнопки
                                buttonState == YapButtonState.READY && isInside -> {
                                    buttonState = YapButtonState.FIRING
                                }

                                // Во всех остальных случаях (вышли за границы, слишком быстро или отмена)
                                else -> {
                                    buttonState = YapButtonState.IDLE
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                        }
                    },
                shape = pillShape,
                color = MaterialTheme.colorScheme.primary,
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (buttonState == YapButtonState.RECORDING) {
                        // Слой с секундомером
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(painterResource(R.drawable.baseline_mic_24), "Mic", tint = Color.Red)
                            Text(
                                text = String.format("0:%02d", recordTimer.value),
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                    }
                    else {
                        Icon(
                            painter = painterResource(id = R.drawable.yap_button_big_text), // Замени на свой ID
                            contentDescription = "YAP Logo",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(
                                width = 120.dp * baseScale,
                                height = 60.dp * baseScale
                            )
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
}