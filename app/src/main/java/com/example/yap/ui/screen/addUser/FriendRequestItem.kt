package com.example.yap.ui.screen.addUser

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.yap.data.model.UserItem
import com.example.yap.ui.theme.LocalAdditionColors

@Composable
fun FriendRequestItem(
    user: UserItem,
    baseScale: Float,
    onUserClick: (String) -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier
) {
    val additionColors = LocalAdditionColors.current

    BaseUserItemRow(
        user = user,
        baseScale = baseScale,
        onUserClick = onUserClick,
        modifier = modifier
    ) {
        RoundActionButton(
            imageVector = Icons.Default.Close,
            onClick = onDecline,
            backgroundColor = additionColors.disabledYabBackgroundColor,
            contentColor = additionColors.crossColor,
            baseScale = baseScale
        )

        Spacer(modifier = Modifier.width(12.dp * baseScale))

        RoundActionButton(
            imageVector = Icons.Default.Check,
            onClick = onAccept,
            backgroundColor = additionColors.checkBackgroundColor,
            contentColor = Color.White,
            baseScale = baseScale,
            iconSize = 28.dp
        )
    }
}

@Composable
fun RoundActionButton(
    imageVector: ImageVector,
    onClick: () -> Unit,
    backgroundColor: Color,
    contentColor: Color,
    baseScale: Float,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp
) {
    Box(
        modifier = modifier
            .size(44.dp * baseScale)
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(iconSize * baseScale)
        )
    }
}