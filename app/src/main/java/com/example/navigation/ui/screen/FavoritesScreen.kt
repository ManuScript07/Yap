package com.example.navigation.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Preview
@Composable
fun FavoritesScreen(
    onGoToFavDetails: () -> Unit
) {
    Box(Modifier.fillMaxSize().background(Color.Red), contentAlignment = Alignment.Center) {
        Text("Favorites Screen")

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = onGoToFavDetails) {
            Text("Go to Details")
        }
    }
}