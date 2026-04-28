package com.example.yap.ui.screen.support

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Composable
fun SocialIconButton(
    iconRes: Int,
    onClick: () -> Unit,
    baseScale: Float,
    isCircle: Boolean
) {
    Box(
        modifier = Modifier
            .size(64.dp * baseScale)
            .clip(if (isCircle) CircleShape else RoundedCornerShape(16.dp * baseScale))
            .background(Color.Black)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            modifier = Modifier.size(36.dp * baseScale),
            tint = Color.White
        )
    }
}