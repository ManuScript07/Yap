package com.example.yap.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatBirthday(timestamp: Long?, showOnlyDay: Boolean): String {
    if (timestamp == null || timestamp == 0L) return "Не указано"

    val date = Date(timestamp)
    // Если true - только день и месяц, если false - добавляем год
    val pattern = if (showOnlyDay) "d MMMM" else "d MMMM yyyy"
    val formatter = SimpleDateFormat(pattern, Locale("ru"))

    return formatter.format(date)
}