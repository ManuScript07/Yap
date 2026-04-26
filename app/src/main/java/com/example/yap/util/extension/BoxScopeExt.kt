package com.example.yap.util.extension

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.yap.R

@Composable
fun BoxScope.SystemStatusPill(
    statusResource: Int?,
    statusMessage: String?,
    statusId: Long,
    isSuccess: Boolean
) {
    // 1. Создаем "хранилище" для последнего валидного сообщения
    // Оно НЕ обнуляется, когда statusResource становится null
    var lastValidMessage by remember { mutableStateOf("") }
    var lastValidIconIsSuccess by remember { mutableStateOf(true) }

    val currentMessage = statusResource?.let { stringResource(it) } ?: statusMessage

    // Обновляем хранилище только если пришло что-то реальное
    LaunchedEffect(statusId) {
        if (currentMessage != null) {
            lastValidMessage = currentMessage
            lastValidIconIsSuccess = isSuccess
        }
    }

    AnimatedVisibility(
        visible = statusResource != null || statusMessage != null,
        // Смещаем анимацию появления еще ниже, а улетание делаем симметричным
        enter = slideInVertically(initialOffsetY = { -it * 3 }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it * 3 }) + fadeOut(),
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = 80.dp) // Чуть ниже от края
            .zIndex(100f)
    ) {
        // Черный полупрозрачный фон
        val pillColor = Color.Black.copy(alpha = 0.8f)

        Surface(
            shape = CircleShape,
            color = pillColor,
            shadowElevation = 4.dp,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(
                        if (lastValidIconIsSuccess) R.drawable.baseline_check_circle_24
                        else R.drawable.baseline_cancel_24
                    ),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )

                Spacer(Modifier.width(10.dp))

                Text(
                    text = lastValidMessage,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}