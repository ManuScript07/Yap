package com.example.yap.ui.components

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

// 1. ОПРЕДЕЛЯ ЕМ СОСТОЯНИЯ КНОПКИ


@Parcelize
enum class YapButtonState : Parcelable {
    IDLE, PRESSED, READY, FIRING, RECORDING, LOCKED, REVIEW
}