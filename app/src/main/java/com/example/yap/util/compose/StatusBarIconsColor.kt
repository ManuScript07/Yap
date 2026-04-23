package com.example.yap.util.compose

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun SystemBarsIconsColor(isLight: Boolean) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val insetsController = WindowCompat.getInsetsController(window, view)

            // Статус-бар (верх)
            insetsController.isAppearanceLightStatusBars = isLight

            // Навигационная панель (низ)
            // true — темные кнопки (для светлого фона)
            // false — светлые кнопки (для темного фона)
            insetsController.isAppearanceLightNavigationBars = isLight
        }
    }
}