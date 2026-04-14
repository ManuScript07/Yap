package com.example.yap.ui.screen.splash

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yap.R
import com.example.yap.ui.navigation.NavigationApp
import com.example.yap.util.compose.StatusBarIconsColor

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
                targetOffsetY = { 0 }
            ) + fadeOut(tween())
        ) {
            SplashContent()
        }
    }
}

@Composable
fun SplashContent() {
    StatusBarIconsColor(isLight = true)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = R.drawable.yap_button_big_text),
            contentDescription = "YAP Logo",
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(
                width = 140.dp,
                height = 60.dp
            )
        )
    }
}
