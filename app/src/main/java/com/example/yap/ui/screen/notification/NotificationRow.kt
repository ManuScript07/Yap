package com.example.yap.ui.screen.notification

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yap.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationRow(
    item: NotificationModel,
    onDelete: () -> Unit,
    onMute: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            // Не удаляем сразу при свайпе, просто открываем меню
            false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false, // Свайп только справа налево
        backgroundContent = {
            // ФОН ПРИ СВАЙПЕ (Фиолетовый с кнопками)
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Color(0xFFB0B0E5), // Примерный фиолетовый из скрина
                        RoundedCornerShape(8.dp)
                    )
                    .padding(end = 16.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onMute,
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color.White.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(painterResource(R.drawable.outline_notifications_off_24), contentDescription = "Mute", tint = Color(0xFF4A4A8A))
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFF4A4A8A), CircleShape) // Темно-синий
                ) {
                    Icon(painterResource(R.drawable.outline_delete_24), contentDescription = "Delete", tint = Color.White)
                }
            }
        }
    ) {
        // ОСНОВНОЙ КОНТЕНТ (Белый фон)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(vertical=14.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Аватар
            Image(
                painter = painterResource(id = item.avatarRes),
                contentDescription = "Avatar",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
            )

            Spacer(modifier = Modifier.width(16.dp))

            // Тексты
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.nickname,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground
                )

//                Spacer(modifier = Modifier.height(2.dp))

                if (item.hasLocation) {
                    Text(
                        text = "Местоположение",
                        fontSize = 16.sp,
                        color = Color.Gray,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable { /* TODO: Открыть карту */ }
                    )
                } else {
                    Text(
                        text = item.messageText,
                        fontSize = 16.sp,
                        color = Color.Gray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Время и кнопка YAP
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.height(56.dp)
            ) {
                Text(
                    text = item.timeAgo,
                    fontSize = 12.sp,
                    color = Color.Gray
                )

                // Кнопка YAP
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (item.isYapActive) Color(0xFFD4E12F) else Color(0xFFE0E0E0), // Желтый или Серый
                    onClick = { /* TODO: Yap Action */ }
                ) {
                    Text(
                        text = "YAP",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        color = if (item.isYapActive) Color.Black else Color.Gray,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}