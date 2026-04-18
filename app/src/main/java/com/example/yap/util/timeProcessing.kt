package com.example.yap.util


import java.util.Date
import java.util.Locale
import com.google.firebase.Timestamp
import android.text.format.DateUtils
import java.text.SimpleDateFormat


fun formatTime(timestamp: Timestamp?): String {
    // Извлекаем Date из Timestamp, если он null — берем текущее время
    val date = timestamp?.toDate() ?: Date()
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(date)
}

fun getTimeAgo(timestamp: Timestamp?): String {
    val timeMillis = timestamp?.toDate()?.time ?: System.currentTimeMillis()
    val now = System.currentTimeMillis()

    return DateUtils.getRelativeTimeSpanString(
        timeMillis,
        now,
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE
    ).toString()
}