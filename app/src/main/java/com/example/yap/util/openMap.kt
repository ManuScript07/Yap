package com.example.yap.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

fun openMap(context: Context, lat: Double, lon: Double, label: String = "Location") {
    // Формируем URI. 'q' позволяет поставить маркер в точку
    val uri = Uri.parse("geo:$lat,$lon?q=$lat,$lon($label)")
    val mapIntent = Intent(Intent.ACTION_VIEW, uri)

    // Пытаемся запустить
    try {
        context.startActivity(mapIntent)
    } catch (e: Exception) {
        // Если карт вообще нет (редко, но бывает)
        Toast.makeText(context, "Maps app not found", Toast.LENGTH_SHORT).show()
    }
}