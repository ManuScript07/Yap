package com.example.yap.ui.screen.userFriends

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.yap.ui.components.AddFriendActionButton
import com.example.yap.ui.components.YapActionButton
import com.example.yap.ui.screen.addUser.AddFriendStatus
import com.example.yap.ui.screen.addUser.BaseUserItemRow

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UserFriendListItem(
    foundUser: FoundUser,
    baseScale: Float,
    onUserClick: (String) -> Unit,
    onAddClick: () -> Unit,
    onYapClick: (String) -> Unit,
    onToggleQuickList: (FoundUser) -> Unit,
    onYapLongClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {

    BaseUserItemRow(
        user = foundUser.user,
        baseScale = baseScale,
        onUserClick = onUserClick,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp * baseScale)
        ) {
            if (foundUser.status == AddFriendStatus.ALREADY_FRIEND) {
                // Если друг — кнопка Япа
                YapActionButton(
                    user = foundUser.user,
                    onYapClick = onYapClick,
                    onLongYapClick = onYapLongClick
                )
            } else {
                AddFriendActionButton(
                    status = foundUser.status,
                    onAddClick = onAddClick
                )
            }
        }
    }
}