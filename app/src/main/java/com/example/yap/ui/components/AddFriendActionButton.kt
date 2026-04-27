package com.example.yap.ui.components

import android.annotation.SuppressLint
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import com.example.yap.ui.screen.addUser.AddFriendStatus
import com.example.yap.ui.theme.LocalAdditionColors

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AddFriendActionButton(
    status: AddFriendStatus,
    baseScale: Float = 1f,
    onAddClick: () -> Unit,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) {
    val additionColors = LocalAdditionColors.current
    val isCanAdd = status == AddFriendStatus.CAN_ADD

    val backgroundColor = if (isCanAdd) additionColors.darkYapButtonBackgroundColor
    else additionColors.disabledYabBackgroundColor
    val iconTint = if (isCanAdd) Color.Black else additionColors.secondTextColor

    val interactionSource = remember { MutableInteractionSource() }
    val rippleIndication = ripple(bounded = true)

    Box(
        modifier = modifier
            .height(32.dp * baseScale)
            .width(64.dp * baseScale)
            .clip(RoundedCornerShape(16.dp * baseScale))
            .background(backgroundColor)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = if (isCanAdd) rippleIndication else null,
                onClick = {
                    if (isCanAdd) onAddClick()
                },
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = R.drawable.outline_person_add_32),
            contentDescription = "Add Friend Icon",
            tint = iconTint,
            modifier = Modifier.size(32.dp * baseScale)
        )
    }
}