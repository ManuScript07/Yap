package com.example.yap.util.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
fun <T> rememberLambda(onClick: (T) -> Unit): (T) -> Unit {
    var lastClickTime by remember { mutableLongStateOf(0L) }
    return remember(onClick) {
        { arg ->
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastClickTime > 600L) {
                lastClickTime = currentTime
                onClick(arg)
            }
        }
    }
}