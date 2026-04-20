package com.example.yap.util


import android.annotation.SuppressLint
import android.text.format.DateUtils
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Locale


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

@SuppressLint("DefaultLocale")
fun formatTime(ms: Int): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}