package com.example.yap.ui.components

import android.annotation.SuppressLint
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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.yap.R
import com.example.yap.ui.screen.home.HomeViewModel
import com.example.yap.ui.screen.home.YapType
import com.example.yap.ui.theme.LocalBaseScale
import kotlinx.coroutines.delay


@SuppressLint("ConfigurationScreenWidthHeight", "DefaultLocale")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainYapButton(
    price: Int,
    isEnoughStars: Boolean,
    onClick: (YapType) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel
) {

    val uiState by viewModel.state.collectAsStateWithLifecycle()

    // Локальные ссылки для удобства (чтобы не писать uiState.везде)
    val buttonState = uiState.yapButtonState
    val offsetY = uiState.yapOffsetY
    val recordTimeMs = uiState.yapRecordTimeMs

//    val configuration = LocalConfiguration.current
    val baseScale = LocalBaseScale.current // Предполагаю, что он у тебя объявлен через CompositionLocal

    val frontPillWidth = 196.dp * baseScale
    val frontPillHeight = 160.dp * baseScale
    val backPillMaxWidth = 260.dp * baseScale
    val backPillMaxHeight = 212.dp * baseScale

    // 2. ИНИЦИАЛИЗИРУЕМ ИНСТРУМЕНТЫ
//    var buttonState by rememberSaveable { mutableStateOf(YapButtonState.IDLE) }
    val haptic = LocalHapticFeedback.current


//    var offsetY by rememberSaveable { mutableStateOf(0f) }
//    val recordTimer = rememberSaveable { mutableStateOf(0) }
    var didOverrideMessage by rememberSaveable { mutableStateOf(false) }
//    var recordTimeMs by rememberSaveable { mutableLongStateOf(0L) }
//    val maxDurationMs = 20_000L

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
            YapButtonState.READY, YapButtonState.RECORDING, YapButtonState.FIRING, YapButtonState.LOCKED, YapButtonState.REVIEW -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f)// Цвет "заряженной" кнопки
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

    // Создаем плавную обертку над offsetY
    val animatedOffsetY by animateFloatAsState(
        targetValue = offsetY,
        // Настраиваем мягкую пружину, чтобы кнопка приятно "примагнитилась"
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "OffsetAnimation"
    )



    val pillShape = RoundedCornerShape(percent = 80)

    // 1. ЭФФЕКТ ДЛЯ ТАЙМЕРА (Зависит только от факта записи)
    // 1. ЭФФЕКТ ДЛЯ ТАЙМЕРА (Считывает состояние из ViewModel)
    LaunchedEffect(buttonState) {
        // Проверяем, нужно ли запускать/продолжать таймер
        if (buttonState == YapButtonState.RECORDING || buttonState == YapButtonState.LOCKED) {

            // Вычисляем точку старта относительно уже накопленного времени (из стейта)
            val startTime = System.currentTimeMillis() - uiState.yapRecordTimeMs

            while (buttonState == YapButtonState.RECORDING || buttonState == YapButtonState.LOCKED) {
                val currentMs = System.currentTimeMillis() - startTime

                // Обновляем время в общем стейте
                viewModel.updateYapRecordTime(currentMs)

                // Проверка лимита в 20 секунд
                if (currentMs >= uiState.maxDurationMs) {
                    viewModel.updateYapRecordTime(uiState.maxDurationMs)
                    viewModel.updateYapButtonState(YapButtonState.REVIEW)
                    viewModel.updateYapOffsetY(0f) // Плавно возвращаем в центр
                    break
                }
                delay(50) // 20 кадров в секунду для плавности 1/10 сек
            }
        } else if (buttonState == YapButtonState.IDLE) {
            // Если кнопка сброшена — обнуляем время во ViewModel
            viewModel.updateYapRecordTime(0L)
        }
    }

    LaunchedEffect(buttonState) {
        if (buttonState == YapButtonState.REVIEW) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            // Можно добавить вибрацию и для других переходов, если нужно
        }
    }

// 2. ЭФФЕКТ ДЛЯ ЛОГИКИ СОСТОЯНИЙ И ПОДСКАЗОК
    // 2. ЭФФЕКТ ТОЛЬКО ДЛЯ СМЕНЫ СОСТОЯНИЙ (Ключ только buttonState)
    // 1. ЛОГИКА ПЕРЕХОДОВ (Срабатывает ОДИН РАЗ при смене стейта)
    LaunchedEffect(buttonState) {
        when (buttonState) {
            YapButtonState.PRESSED -> {
                val state = viewModel.state.value

                // Проверяем, есть ли блокирующее условие
                if (state.currentStars < state.yapPrice || state.yapPrice == 0) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                    // Показываем алерт ТОЛЬКО если проблема реально в звездах
                    if (state.currentStars < state.yapPrice) {
                        viewModel.showAlert("Недостаточно звезд!", durationMs = 1000)
                    }

                    delay(200) // Время для визуального "отскока" плашки
                    viewModel.resetYapButton() // Возвращаем назад в IDLE
                    return@LaunchedEffect
                }

                // Если всё хорошо — идем дальше
                delay(150)
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.updateYapButtonState(YapButtonState.READY)
            }

            YapButtonState.READY -> {
                delay(300)
                if (buttonState == YapButtonState.READY) didOverrideMessage = true
                delay(300)
                if (buttonState == YapButtonState.READY) {
                    val voicePrice = viewModel.getPriceForType(YapType.VOICE)
                    val currentStars = viewModel.state.value.currentStars
                    // Дополнительная проверка перед записью голоса (если цена стала 10)
                    if (currentStars >= voicePrice) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.updateYapButtonState(YapButtonState.RECORDING)
                    } else {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.resetYapButton()
                    }
                }
            }

            YapButtonState.RECORDING -> {
                // Как только вошли в режим записи — меняем тип на VOICE
                // Чтобы цена сразу стала 10, и sendYap знал, что отправлять
                viewModel.startVoiceRecording()
            }

            YapButtonState.REVIEW -> {
                // Мягко возвращаем кнопку в центр при входе в Review
                viewModel.updateYapOffsetY(0f)
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.prepareVoiceForReview(audioPath = "path/to/file.m4a")
                viewModel.showAlert(message = "Нажмите YAP, чтобы отправить", canClose = false)
            }

            YapButtonState.FIRING -> {
                val snapshotType = viewModel.state.value.yapType
                delay(200)
                onClick(snapshotType)
//                viewModel.resetYapButton()
                didOverrideMessage = false
            }

            YapButtonState.IDLE -> {
                viewModel.updateYapOffsetY(0f)
                if (didOverrideMessage) {
//                    viewModel.dismissMessage()
                    viewModel.clearSystemAlertOnly()
                    didOverrideMessage = false
                }
            }
            else -> {}
        }
    }

    // 2. ДИНАМИЧЕСКИЕ ПОДСКАЗКИ (Реагируют на движение пальца)
    LaunchedEffect(buttonState, offsetY) {
        if (buttonState == YapButtonState.RECORDING || buttonState == YapButtonState.LOCKED) {
            val message = when {
                buttonState == YapButtonState.LOCKED -> "Запись закреплена"
                offsetY < -130f -> "Вверх — закрепить"
                offsetY > 130f -> "Вниз — отмена"
                else -> "Идёт запись..."
            }
            viewModel.showAlert(message = message, canClose = false)
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
            if (buttonState == YapButtonState.RECORDING ||
                buttonState == YapButtonState.LOCKED ||
                buttonState == YapButtonState.REVIEW) {

                // 1. ИКОНКА МУСОРКИ (сверху) — активна только при движении вниз
                val trashAlpha = (offsetY / 130f).coerceIn(0f, 1f)
                if (trashAlpha > 0.1f) {
                    Icon(
                        painter = painterResource(R.drawable.baseline_delete_32),
                        contentDescription = null,
                        tint = Color(0xE1FFDD).copy(alpha = trashAlpha),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 30.dp)
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
                            .padding(bottom = 30.dp)
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
            .offset(y = (animatedOffsetY / 5).dp)
            .size(width = frontPillWidth, height = frontPillHeight)
            .shadow(elevation = 6.dp * baseScale, shape = pillShape)
            .clip(pillShape)
            // 4. ОБРАБОТКА ЖЕСТОВ
            .pointerInput(Unit) {

                awaitEachGesture {
                    val down = awaitFirstDown()
                    var isMoved = false

                    // ЧИТАЕМ СВЕЖЕЕ СОСТОЯНИЕ ИЗ VIEWMODEL
                    if (viewModel.state.value.yapButtonState != YapButtonState.LOCKED &&
                        viewModel.state.value.yapButtonState != YapButtonState.REVIEW) {
                        viewModel.updateYapButtonState(YapButtonState.PRESSED)
                    }

                    var lastChange = down

                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.first()
                        lastChange = change

                        val dragY = change.position.y - down.position.y

                        if (kotlin.math.abs(dragY) > 10f) {
                            isMoved = true
                        }

                        // ВАЖНО: Получаем актуальное состояние прямо в момент движения пальца!
                        val currentState = viewModel.state.value.yapButtonState

                        if (currentState == YapButtonState.RECORDING ||
                            currentState == YapButtonState.LOCKED ||
                            currentState == YapButtonState.REVIEW
                        ) {
                            val newLocalY = when (currentState) {
                                YapButtonState.LOCKED -> {
                                    (-120f + dragY).coerceIn(-200f, 200f)
                                }
                                YapButtonState.REVIEW -> {
                                    // В режиме Review позволяем тянуть только вниз (удаление)
                                    dragY.coerceIn(0f, 200f)
                                }
                                else -> {
                                    dragY.coerceIn(-200f, 200f)
                                }
                            }
                            // Отправляем во ViewModel
                            viewModel.updateYapOffsetY(newLocalY)
                        }

                        if (isMoved) {
                            change.consume()
                        }
                    } while (event.changes.any { it.pressed })

                    val isInside = lastChange.position.x in 0f..size.width.toFloat() &&
                            lastChange.position.y in 0f..size.height.toFloat()

                    val isValidTap = !isMoved && isInside && !lastChange.isConsumed

                    // ЛОГИКА ОТПУСКАНИЯ (Читаем актуальные данные перед принятием решения)
                    val finalState = viewModel.state.value.yapButtonState
                    val finalOffsetY = viewModel.state.value.yapOffsetY

                    when (finalState) {
                        YapButtonState.RECORDING -> {
                            if (finalOffsetY <= -130f) {
                                viewModel.updateYapButtonState(YapButtonState.LOCKED)
                                viewModel.updateYapOffsetY(-120f)
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            } else if (finalOffsetY >= 130f) {
                                viewModel.resetYapButton() // Вызываем метод полного сброса
                            } else {
                                viewModel.updateVoicePath("path/to/current/record.m4a")
                                viewModel.updateYapButtonState(YapButtonState.FIRING)
                            }
                        }

                        YapButtonState.LOCKED -> {
                            if (finalOffsetY >= 50f) {
                                viewModel.resetYapButton()
                            } else if (isValidTap) {
                                viewModel.updateVoicePath("path/to/current/record.m4a")
                                viewModel.updateYapButtonState(YapButtonState.FIRING)

                            } else {
                                viewModel.updateYapOffsetY(-120f)
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }

                        YapButtonState.REVIEW -> {
                            if (finalOffsetY >= 100f) {
                                viewModel.resetYapButton()
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            } else if (isValidTap) {
                                viewModel.updateYapButtonState(YapButtonState.FIRING)
                            } else {
                                viewModel.updateYapOffsetY(0f)
                            }
                        }

                        YapButtonState.READY -> {
                            if (isInside) {
                                viewModel.updateYapButtonState(YapButtonState.FIRING)
                            } else {
                                viewModel.resetYapButton()
                            }
                        }

                        else -> {
                            if (finalState != YapButtonState.LOCKED && finalState != YapButtonState.REVIEW) {
                                viewModel.resetYapButton()
                            }
                        }
                    }
                }
            },
        shape = pillShape,
        color = MaterialTheme.colorScheme.primary,
    ) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // --- 1. ЦЕНТРАЛЬНЫЙ ЭЛЕМЕНТ (Таймер или Лого) ---
            if (buttonState == YapButtonState.RECORDING || buttonState == YapButtonState.LOCKED || buttonState == YapButtonState.REVIEW) {
                // ТАЙМЕР ПО ЦЕНТРУ
                val seconds = recordTimeMs / 1000
                val tenths = (recordTimeMs % 1000) / 100
                val timerText = String.format("%02d,%d/20", seconds, tenths)

                val pillBackgroundColor by animateColorAsState(
                    targetValue = if (buttonState == YapButtonState.REVIEW) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        Color.Black.copy(alpha = 0.15f)
                    },
                    label = "timerBgColor"
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(pillBackgroundColor)
                        .clickable(enabled = buttonState == YapButtonState.LOCKED || buttonState == YapButtonState.REVIEW) {
                            if (buttonState == YapButtonState.LOCKED) {
                                viewModel.updateYapButtonState(YapButtonState.REVIEW)
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    if (buttonState == YapButtonState.LOCKED) {
                        Icon(
                            painter = painterResource(R.drawable.baseline_pause_32),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(32.dp).padding(end = 8.dp)
                        )
                    } else if (buttonState == YapButtonState.REVIEW) {
                        Icon(
                            painter = painterResource(R.drawable.baseline_play_arrow_32),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(32.dp).padding(end = 8.dp)
                        )
                    }

                    Text(
                        text = timerText,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
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
            }

            Surface(
                modifier = Modifier
                    .offset(y = 50.dp * baseScale)
                    .size(width = 60.dp * baseScale, height = 32.dp * baseScale),
                shape = RoundedCornerShape(18.dp * baseScale),
                color = MaterialTheme.colorScheme.tertiary,
            ) {


                Row(
                    modifier = Modifier
                        .fillMaxSize(),
//                                    .padding(horizontal = 16.dp, vertical = 8.dp),
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

