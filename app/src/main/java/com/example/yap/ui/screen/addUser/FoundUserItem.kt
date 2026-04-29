package com.example.yap.ui.screen.addUser

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.yap.ui.components.AddFriendActionButton

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