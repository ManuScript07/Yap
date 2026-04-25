package com.example.yap.util

fun generateRandomUserCode(): String {
    val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
    return (1..8)
        .map { chars.random() }
        .joinToString("")
}