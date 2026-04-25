package com.example.yap.ui.screen.friends

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yap.R
import com.example.yap.ui.theme.LocalBaseScale

@Composable
fun RemoveFriendDialog(
    friendId: String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    baseScale: Float = LocalBaseScale.current
) {
    if (friendId == null) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.delete_friend_title),
                fontSize = 20.sp * baseScale,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Text(
                text = stringResource(R.string.delete_friend_desc),
                fontSize = 16.sp * baseScale,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(friendId)
                    onDismiss()
                }
            ) {
                Text(
                    text = stringResource(R.string.delete),
                    fontSize = 16.sp * baseScale,
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.cancel),
                    fontSize = 16.sp * baseScale,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp * baseScale) // Сделал чуть более скругленным для стиля
    )
}