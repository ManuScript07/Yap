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


//@Preview
@Composable
fun HomeDetailsScreen(
    onGoToDeepDetails: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Blue),
        contentAlignment = Alignment.Center
    ) {
        Text("Home Details Screen")

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = onGoToDeepDetails) {
            Text("Go to Deep Details")
        }
    }
}