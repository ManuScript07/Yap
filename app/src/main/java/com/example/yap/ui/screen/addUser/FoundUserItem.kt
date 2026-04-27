package com.example.yap.ui.screen.addUser

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
import com.example.yap.ui.components.AddFriendActionButton
import com.example.yap.ui.theme.LocalAdditionColors

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FoundUserItem(
    foundUser: FoundUser,
    baseScale: Float,
    onUserClick: (String) -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BaseUserItemRow(
        user = foundUser.user,
        baseScale = baseScale,
        onUserClick = onUserClick,
        modifier = modifier
    ) {
        AddFriendActionButton(
            status = foundUser.status,
            baseScale = baseScale,
            onAddClick = onAddClick
        )
    }
}