package com.example.navigation.ui.screen


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.navigation.ui.util.rememberLambda

@Composable
fun HomeScreen(
    onGoToDetails: () -> Unit,
    onGoToSettings: () -> Unit
) {
    val safeGoToDetails = rememberLambda { onGoToDetails() }
    val safeGoToSettings = rememberLambda { onGoToSettings() }

    // Для тех у кого нет onClick Box(modifier = Modifier.clickableOnce { onGoToDetails() }) { ... }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Cyan)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Home Screen")

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = safeGoToDetails) {
            Text("Go to Details")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = safeGoToSettings) {
            Text("Go to Settings")
        }
    }
}

