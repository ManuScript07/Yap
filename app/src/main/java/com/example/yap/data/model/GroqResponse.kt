package com.example.yap.data.model

import com.google.gson.annotations.SerializedName

data class GroqResponse(
    @SerializedName("text") val text: String
)