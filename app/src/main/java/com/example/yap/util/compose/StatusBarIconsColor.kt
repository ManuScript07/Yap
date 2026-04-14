package com.example.yap.util.compose

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun StatusBarIconsColor(isLight: Boolean) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val insetsController = WindowCompat.getInsetsController(window, view)
            // isLight = true означает, что мы хотим ТЕМНЫЕ иконки (для светлого фона)
            // isLight = false означает, что мы хотим СВЕТЛЫЕ иконки (для темного фона)
            insetsController.isAppearanceLightStatusBars = isLight
        }
    }
}