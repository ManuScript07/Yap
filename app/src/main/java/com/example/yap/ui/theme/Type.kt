package com.example.yap.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.yap.R

// Set of Material typography styles to start with
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
    /* Other default text styles to override
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
    */
)

@OptIn(ExperimentalTextApi::class)
val RobotoFlexFamily = FontFamily(
    Font(
        resId = R.font.roboto_flex, // твой файл шрифта
        variationSettings = FontVariation.Settings(
            FontVariation.width(766f),      // V 766
            FontVariation.Setting("GRAD", 150f), // Grad 150
            FontVariation.Setting("XOPQ", 106f), // Xopq 106
            FontVariation.Setting("YTLC", 518f), // Ytlc 518
            FontVariation.Setting("SINT", -10f)  // SInt -10
        )
    )
)