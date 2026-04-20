package com.example.yap.ui.main

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.yap.ui.screen.splash.AppEntryWithSplash
import com.example.yap.ui.screen.splash.SplashViewModel
import com.example.yap.ui.theme.LocalBaseScale
import com.example.yap.ui.theme.YapTheme

class MainActivity : ComponentActivity() {

    private val viewModel: SplashViewModel by viewModels()

    @SuppressLint("ConfigurationScreenWidthHeight")
    override fun onCreate(savedInstanceState: Bundle?) {

        val splashScreen = installSplashScreen()

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        splashScreen.setKeepOnScreenCondition {
            false
        }


        setContent {
            val configuration = LocalConfiguration.current
            val baseScale = (configuration.screenWidthDp.dp / 390.dp).coerceIn(0.8f, 1.2f)
            CompositionLocalProvider(LocalBaseScale provides baseScale) {
                YapTheme {
                    AppEntryWithSplash(viewModel)
                }
            }
        }
    }

}