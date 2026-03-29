package com.example.yap.ui.theme

import android.R
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

//private val DarkColorScheme = darkColorScheme(
//    primary = Purple80,
//    secondary = PurpleGrey80,
//    tertiary = Pink80
//)

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = Color.Black,

    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,

    background = Background,
    onBackground = OnSurface20,

    surface = Color.White,
    onSurface = OnSurface20,

    secondary = SecondarySurface,
    onSecondary = Color.White,

    tertiary = PrimarySurface,
    onTertiary = Color.White,

    outline = OnSurface60
)

val LightAdditionColors = AdditionalColors(
    bottomSurface = Color(0xFFF3F4F9),
    unselectedColor = OnSurface40,
    speedBottomDialog = Color(0xFFEAF1FF),
    buttonReactionColor = Color(0xFFE1FFDD),
    centerGradientColor = Color(0xFFFFAA62),
    pinkForGradientColor = Color(0xFFFB62FE)
)

val LocalAdditionColors = staticCompositionLocalOf {
    LightAdditionColors
}


@Composable
fun NavigationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

//        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    CompositionLocalProvider(
        LocalAdditionColors provides LightAdditionColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = AppShapes,
            content = content
        )
    }
}