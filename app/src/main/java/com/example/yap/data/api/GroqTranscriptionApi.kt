package com.example.yap.data.api

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface GroqTranscriptionApi {
    @Multipart
    @POST("v1/audio/transcriptions")
    suspend fun transcribe(
        @Header("Authorization") token: String, // Bearer YOUR_KEY
        @Part file: MultipartBody.Part,
        @Part("model") model: RequestBody = "whisper-large-v3"
            .toRequestBody("text/plain"
                .toMediaType())
    ): GroqResponse
}

data class GroqResponse(val text: String)