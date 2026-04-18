package com.example.yap.util


import java.util.Date
import java.util.Locale
import com.google.firebase.Timestamp
import android.text.format.DateUtils
import java.text.SimpleDateFormat


fun formatTime(timestamp: Timestamp?): String {
    // Если времени нет, возвращаем пустую строку, чтобы не вводить в заблуждение
    val date = timestamp?.toDate() ?: return ""
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(date)
}

fun getTimeAgo(timestamp: Timestamp?): String {
    val date = timestamp?.toDate() ?: return "отправляется..."

    return DateUtils.getRelativeTimeSpanString(
        date.time,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE
    ).toString()
}