package com.example.yap.data.repository

sealed class AuthResult {
    object SuccessExistingUser : AuthResult()
    data class SuccessNewUser(val defaultName: String) : AuthResult()
    data class Error(val message: String) : AuthResult()
}