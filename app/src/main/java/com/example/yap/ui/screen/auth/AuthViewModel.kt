package com.example.yap.ui.screen.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.yap.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(private val repository: UserRepository = UserRepository()) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState = _authState.asStateFlow()

    fun handleGoogleSignIn(idToken: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val success = repository.signInWithGoogle(idToken)
            if (success) {
                _authState.value = AuthState.Success
            } else {
                _authState.value = AuthState.Error("Ошибка авторизации в Firebase")
            }
        }
    }

    sealed class AuthState {
        object Idle : AuthState()
        object Loading : AuthState()
        object Success : AuthState()
        data class Error(val message: String) : AuthState()
    }
}