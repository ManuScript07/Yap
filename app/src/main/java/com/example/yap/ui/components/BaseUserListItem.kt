package com.example.yap.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.yap.R
import com.example.yap.data.model.UserItem
import com.example.yap.ui.theme.LocalBaseScale

@Composable
fun BaseUserListItem(
    user: UserItem,
    modifier: Modifier = Modifier,
    onUserClick: (String) -> Unit,
    onYapClick: (String) -> Unit,
    onLongYapClick: ((String) -> Unit)? = null,
    actionPopup: @Composable (isVisible: Boolean, onDismiss: () -> Unit) -> Unit
) {
    val baseScale = LocalBaseScale.current
    var showMenu by rememberSaveable { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onUserClick(user.id) })
            }
            .padding(vertical = 6.dp * baseScale),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Аватар
        AsyncImage(
            model = user.avatarUrl ?: R.drawable.avatar_1,
            contentDescription = null,
            modifier = Modifier
                .size(56.dp * baseScale)
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
            placeholder = painterResource(R.drawable.avatar_1),
            error = painterResource(R.drawable.avatar_1)
        )

        Spacer(modifier = Modifier.width(16.dp * baseScale))

        // Имя
        Text(
            text = user.name,
            modifier = Modifier.weight(1f),
            fontSize = 18.sp * baseScale,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // Кнопка Yap
        YapActionButton(
            user = user,
            onYapClick = onYapClick,
            onLongYapClick = onLongYapClick
        )

        Spacer(modifier = Modifier.width(8.dp * baseScale))

        // Контейнер для иконки меню и самого Popup
        Box(contentAlignment = Alignment.Center) {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.size(32.dp * baseScale)
            ) {
                Icon(
                    painter = painterResource(R.drawable.more_vert),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp * baseScale)
                )
            }

            // Вызываем переданный Popup здесь
            actionPopup(showMenu,{ showMenu = false })
        }
    }
}