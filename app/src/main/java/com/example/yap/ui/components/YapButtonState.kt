package com.example.yap.ui.components

import android.os.Parcelable
import kotlinx.parcelize.Parcelize



@Parcelize
enum class YapButtonState : Parcelable {
    IDLE, PRESSED, READY, FIRING, RECORDING, LOCKED, REVIEW
}