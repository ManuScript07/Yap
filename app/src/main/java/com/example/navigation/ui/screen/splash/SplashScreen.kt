package com.example.navigation.ui.screen.splash

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.navigation.navigation.NavigationApp
import kotlinx.coroutines.delay

@Composable
fun AppEntryWithSplash(
    viewModel: SplashViewModel = viewModel()
) {
    val splashVisible by viewModel.isSplashVisible.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {

        NavigationApp()

        AnimatedVisibility(
            visible = splashVisible,
            exit = slideOutVertically (
                targetOffsetY = { 0 } // it
            ) + fadeOut(tween())
        ) {
            SplashContent()
        }
    }
}

@Composable
fun SplashContent() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "MyApp", fontSize = 32.sp)
    }
}
// Спросить про появление логотипа