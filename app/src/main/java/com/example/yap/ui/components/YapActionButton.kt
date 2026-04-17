package com.example.yap.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.yap.R
import com.example.yap.data.model.UserItem
import com.example.yap.ui.theme.LocalAdditionColors

@Composable
fun YapActionButton(
    user: UserItem,
    onYapClick: (Int) -> Unit
) {
    val iconTint = if (user.isYapActive) Color.Black else LocalAdditionColors.current.secondTextColor
    Box(
        modifier = Modifier
            .height(32.dp)
            .width(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (user.isYapActive) LocalAdditionColors.current.darkYapButtonBackgroundColor
                else LocalAdditionColors.current.disabledYabBackgroundColor
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true),
                onClick = { onYapClick(user.id) }
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = R.drawable.yap_button_text),
            contentDescription = "YAP Logo",
            tint = iconTint,
//            modifier = Modifier.size(width = 44.dp, height = 20.dp) // Подбери размер под SVG
        )
    }
}