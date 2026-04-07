package com.example.yap

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.example.yap.ui.screen.home.HomeViewModel
import com.example.yap.ui.theme.NavigationTheme
import com.example.yap.ui.screen.splash.AppEntryWithSplash
import com.example.yap.ui.theme.LocalBaseScale

class MainActivity : ComponentActivity() {

    private val homeViewModel: HomeViewModel by viewModels()

    @SuppressLint("ConfigurationScreenWidthHeight")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                Color.TRANSPARENT,
                Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.auto(
                Color.TRANSPARENT,
                Color.TRANSPARENT
            )
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }


        setContent {
            val configuration = LocalConfiguration.current
            val baseScale = (configuration.screenWidthDp.dp / 390.dp).coerceIn(0.8f, 1.2f)
            CompositionLocalProvider(LocalBaseScale provides baseScale) {
                NavigationTheme {
                    AppEntryWithSplash()
                }
            }
        }
    }


//    override fun onStop() {
//        super.onStop()
//        homeViewModel.saveProgress()
//    }
}
