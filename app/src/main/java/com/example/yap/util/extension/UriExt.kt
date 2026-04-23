package com.example.yap.util.extension

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream

fun Uri.compressToByteArray(context: Context, quality: Int = 70, maxDimension: Int = 800): ByteArray? {
    return try {
        val inputStream = context.contentResolver.openInputStream(this)
        val originalBitmap = BitmapFactory.decodeStream(inputStream)
        inputStream?.close()

        if (originalBitmap == null) return null

        // Вычисляем новые размеры с сохранением пропорций
        val ratio = originalBitmap.width.toFloat() / originalBitmap.height.toFloat()
        val width = if (ratio > 1) maxDimension else (maxDimension * ratio).toInt()
        val height = if (ratio > 1) (maxDimension / ratio).toInt() else maxDimension

        val resizedBitmap = Bitmap.createScaledBitmap(originalBitmap, width, height, true)

        val outputStream = ByteArrayOutputStream()
        // Сжимаем в JPEG (или WEBP, если minSdk >= 30)
        resizedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)

        outputStream.toByteArray()
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}