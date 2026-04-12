package com.example.yap.service

import android.util.Log
import com.example.yap.data.api.GroqTranscriptionApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File

class GroqTranscriptionService {
    private val apiKey = "Bearer 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"
    val okHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val response = chain.proceed(chain.request())

            // Извлекаем заголовки лимитов Groq
            val remainingRequests = response.header("x-ratelimit-remaining-requests")
            val remainingTokens = response.header("x-ratelimit-remaining-tokens")
            val resetTime = response.header("x-ratelimit-reset-requests")

            Log.d("GroqLimits", """
            --- Лимиты Groq ---
            Осталось запросов: $remainingRequests
            Осталось токенов: $remainingTokens
            Сброс лимитов через: $resetTime
            -------------------
        """.trimIndent())

            response
        }
        .build()
    private val api = Retrofit.Builder()
        .baseUrl("https://api.groq.com/openai/")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(GroqTranscriptionApi::class.java)

    suspend fun transcribe(file: File): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Whisper в Groq лучше всего работает с .m4a или .mp3
            val requestFile = file.asRequestBody("audio/*".toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("file", file.name, requestFile)

            val response = api.transcribe(apiKey, body)
            Result.success(response.text)
        } catch (e: Exception) {
            Log.e("GroqSTT", "Ошибка API: ${e.message}")
            Result.failure(e)
        }
    }
}