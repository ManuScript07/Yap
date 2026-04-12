package com.example.yap.service

import com.example.yap.data.api.TranscriptionApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File

class ServerTranscriptionService {
    private val api = Retrofit.Builder()
        .baseUrl("http://192.168.0.101:8000/") // IP твоего сервера
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(TranscriptionApi::class.java)

    suspend fun transcribe(file: File): Result<String> = withContext(Dispatchers.IO) {
        try {
            val requestFile = file.asRequestBody("audio/*".toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("file", file.name, requestFile)

            val response = api.transcribe(body)
            Result.success(response.text)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}