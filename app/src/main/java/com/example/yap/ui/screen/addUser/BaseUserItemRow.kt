package com.example.yap.ui.screen.addUser

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.yap.R
import com.example.yap.data.model.UserItem

@Composable
fun BaseUserItemRow(
    user: UserItem,
    baseScale: Float,
    onUserClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    buttons: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onUserClick(user.id) })
            }
            .padding(vertical = 6.dp * baseScale),
        verticalAlignment = Alignment.CenterVertically
    ) {
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

        Text(
            text = user.name,
            modifier = Modifier.weight(1f),
            fontSize = 18.sp * baseScale,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        buttons()
    }
}