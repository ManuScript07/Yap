package com.example.yap.ui.components

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
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
    IDLE, PRESSED, READY, FIRING, RECORDING, LOCKED
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
            YapButtonState.READY, YapButtonState.RECORDING, YapButtonState.FIRING, YapButtonState.LOCKED -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f)// Цвет "заряженной" кнопки
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

    // ДОБАВЛЯЕМ ФЛАГ: Он поможет узнать, затирала ли кнопка пользовательский ввод
    var didOverrideMessage by remember { mutableStateOf(false) }

    // 1. ЭФФЕКТ ДЛЯ ТАЙМЕРА (Зависит только от факта записи)
    LaunchedEffect(buttonState == YapButtonState.RECORDING || buttonState == YapButtonState.LOCKED) {
        if (buttonState == YapButtonState.RECORDING || buttonState == YapButtonState.LOCKED) {
            while (true) {
                delay(1000)
                recordTimer.value++
            }
        } else {
            recordTimer.value = 0
        }
    }

// 2. ЭФФЕКТ ДЛЯ ЛОГИКИ СОСТОЯНИЙ И ПОДСКАЗОК
    LaunchedEffect(buttonState, offsetY) {
        when (buttonState) {
            YapButtonState.PRESSED -> {
                delay(150)
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                buttonState = YapButtonState.READY
            }

            YapButtonState.READY -> {
                delay(300)
                if (buttonState == YapButtonState.READY) didOverrideMessage = true
                delay(300)
                if (buttonState == YapButtonState.READY) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    buttonState = YapButtonState.RECORDING
                }
            }

            YapButtonState.RECORDING, YapButtonState.LOCKED -> {
                // Динамические подсказки работают плавно, перезапуск эффекта им не мешает
                when {
                    buttonState == YapButtonState.LOCKED ->
                        viewModel.showAlert(message = "Запись закреплена", canClose = false)
                    offsetY < -130f ->
                        viewModel.showAlert(message = "Вверх - закрепить", canClose = false)
                    offsetY > 130f ->
                        viewModel.showAlert(message = "Вниз — отмена", canClose = false)
                    else ->
                        viewModel.showAlert(message = "Идёт запись...", canClose = false)
                }
            }

            YapButtonState.FIRING -> {
                delay(200)
                onClick()
                buttonState = YapButtonState.IDLE
                offsetY = 0f
                didOverrideMessage = false
            }

            YapButtonState.IDLE -> {
                offsetY = 0f
                if (didOverrideMessage) {
                    viewModel.dismissMessage()
                    didOverrideMessage = false
                }
            }
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
    ) {

        val infiniteTransition = rememberInfiniteTransition(label = "wave")
        val pulseAnim by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 15f,
            animationSpec = infiniteRepeatable(
                animation = tween(500, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse"
        )

        val waveFactor = if (buttonState == YapButtonState.RECORDING || buttonState == YapButtonState.LOCKED) pulseAnim else 0f

        Box(
            modifier = Modifier.size(width = backPillMaxWidth + 40.dp, height = backPillMaxHeight + 40.dp),
            contentAlignment = Alignment.Center
        ) {

            Box(
                modifier = Modifier
                    .size(
                        width = currentBackWidth + waveFactor.dp, // Меняем размер для имитации волны
                        height = currentBackHeight + (waveFactor / 2).dp
                    )
                    .rotate(backgroundRotation)
                    .background(
                        color = backColor,
                        shape = pillShape
                    )
                    // Добавляем размытый контур для пущей "жидкости"
                    .blur(if (buttonState == YapButtonState.RECORDING) 2.dp else 0.dp)
            )

            // Иконки Мусорки и Замка (внутри заднего пилла)
            if (buttonState == YapButtonState.RECORDING || buttonState == YapButtonState.LOCKED) {

                // 1. ИКОНКА МУСОРКИ (сверху) — активна только при движении вниз
                val trashAlpha = (offsetY / 130f).coerceIn(0f, 1f)
                if (trashAlpha > 0.1f) {
                    Icon(
                        painter = painterResource(R.drawable.baseline_delete_32),
                        contentDescription = null,
                        tint = Color(0xE1FFDD).copy(alpha = trashAlpha),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 20.dp)
                            .size(24.dp)
                            .scale(0.8f + (trashAlpha * 0.4f))
                    )
                }

                // 2. ИКОНКА ЗАМКА (снизу)
                // Если уже закрепили — показываем закрытый замок на 100% яркости
                // Если еще тянем — показываем открытый замок с переменной яркостью
                val isLocked = buttonState == YapButtonState.LOCKED
                val lockAlpha = if (isLocked) 1f else (-offsetY / 130f).coerceIn(0f, 1f)

                if (lockAlpha > 0.1f) {
                    Icon(
                        painter = painterResource(
                            id = if (isLocked) {
                                R.drawable.baseline_lock_32// Твоя иконка закрытого замка
                            } else {
                                R.drawable.lock_open_32  // Твоя иконка открытого замка
                            }
                        ),
                        contentDescription = null,
                        tint = Color(0xE1FFDD).copy(alpha = lockAlpha),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 20.dp)
                            .size(24.dp)
                            // Слегка увеличиваем иконку, когда она становится активной (закрывается)
                            .scale(if (isLocked) 1.2f else 0.8f + (lockAlpha * 0.2f))
                    )
                }
            }
        }

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
                            // Если мы уже в LOCKED, не сбрасываем состояние в PRESSED
                            if (buttonState != YapButtonState.LOCKED) {
                                buttonState = YapButtonState.PRESSED
                            }

                            var lastChange = down // Сохраняем последнее изменение для проверки координат в конце

                            do {
                                val event = awaitPointerEvent()
                                val change = event.changes.first()
                                lastChange = change // Обновляем состояние при каждом движении

                                if (buttonState == YapButtonState.RECORDING || buttonState == YapButtonState.LOCKED) {
                                    val dragY = change.position.y - down.position.y

                                    if (buttonState == YapButtonState.LOCKED) {
                                        // В режиме LOCKED кнопка "стартует" из позиции -180
                                        offsetY = (-120f + dragY).coerceIn(-200f, 200f)
                                    } else {
                                        offsetY = dragY.coerceIn(-200f, 200f)
                                    }
                                }
                                change.consume()
                            } while (event.changes.any { it.pressed })

                            // Проверяем, был ли палец внутри кнопки в момент отпускания
                            val isInside = lastChange.position.x in 0f..size.width.toFloat() &&
                                    lastChange.position.y in 0f..size.height.toFloat()

                            // ЛОГИКА ОТПУСКАНИЯ ПАЛЬЦА
                            when {
                                buttonState == YapButtonState.RECORDING -> {
                                    if (offsetY <= -130f) {
                                        buttonState = YapButtonState.LOCKED
                                        offsetY = -120f // Фиксируем вверху
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    } else if (offsetY >= 130f) {
                                        buttonState = YapButtonState.IDLE
                                    } else {
                                        buttonState = YapButtonState.FIRING
                                    }
                                }

                                buttonState == YapButtonState.LOCKED -> {
                                    // Если в режиме замка потянули вниз — отмена, иначе — отправка
                                    if (offsetY >= 50f) { // Порог отмены из замка чуть ниже центра
                                        buttonState = YapButtonState.IDLE
                                    } else {
                                        buttonState = YapButtonState.FIRING
                                    }
                                }

                                buttonState == YapButtonState.READY && isInside -> {
                                    buttonState = YapButtonState.FIRING
                                }

                                else ->
                                    if (buttonState != YapButtonState.LOCKED) buttonState = YapButtonState.IDLE
                            }
                        }
                    },
                shape = pillShape,
                color = MaterialTheme.colorScheme.primary,
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (buttonState == YapButtonState.RECORDING || buttonState == YapButtonState.LOCKED) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                painter = painterResource(R.drawable.baseline_mic_24),
                                contentDescription = "Mic",
                                tint = if (buttonState == YapButtonState.LOCKED) Color.White else Color.Red
                            )
                            Text(
                                text = String.format("0:%02d", recordTimer.value),
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White
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
