package com.example.yap.ui.components

import android.annotation.SuppressLint
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun YapActionButton(
    user: UserItem,
    onYapClick: (String) -> Unit,
    onLongYapClick: ((String) -> Unit)? = null,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier,
    baseScale: Float = 1f
) {
    val additionColors = LocalAdditionColors.current

    val iconTint = remember(user.isYapActive, additionColors) {
        if (user.isYapActive) Color.Black
        else additionColors.secondTextColor
    }

    val backgroundColor = remember(user.isYapActive, additionColors) {
        if (user.isYapActive) additionColors.darkYapButtonBackgroundColor
        else additionColors.disabledYabBackgroundColor
    }

    val interactionSource = remember { MutableInteractionSource() }
    val rippleIndication = ripple(bounded = true)

    Box(
        modifier = modifier
            .height(35.dp * baseScale)
            .width(70.dp * baseScale)
            .clip(RoundedCornerShape(18.dp * baseScale))
            .background(backgroundColor)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = rippleIndication,
                onClick = { onYapClick(user.id) },
                onLongClick = remember(user.id, onLongYapClick) {
                    onLongYapClick?.let { { it(user.id) } }
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = R.drawable.yap_button_text),
            contentDescription = "YAP Logo",
            tint = iconTint,
            modifier = Modifier
                .size(width = 47.dp * baseScale, height = 18.dp * baseScale)
        )
    }
}