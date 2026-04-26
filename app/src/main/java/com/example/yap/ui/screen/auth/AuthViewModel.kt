package com.example.yap.ui.screen.auth

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.yap.data.repository.AuthResult
import com.example.yap.ui.main.YapApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as YapApp
    private val repository = app.userRepository

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState = _authState.asStateFlow()

    fun handleGoogleSignIn(context: Context) {
        viewModelScope.launch {
            Log.i("AuthDebug", "Клик: Старт handleGoogleSignIn. Текущее состояние: ${_authState.value}")
            _authState.value = AuthState.Loading

            try {
                val idToken = startGoogleSignIn(context)
                if (idToken != null) {
                    Log.i("AuthDebug", "Token получен, идем в репозиторий...")
                    // Теперь репозиторий возвращает AuthResult (как мы обсуждали ранее)
                    val result = repository.signInWithGoogle(idToken)
                    Log.i("AuthDebug", "Результат репозитория: $result")

                    when (result) {
                        is AuthResult.SuccessExistingUser -> {
                            _authState.value = AuthState.SuccessExisting
                        }
                        is AuthResult.SuccessNewUser -> {
                            _authState.value = AuthState.SuccessNew(result.defaultName)
                        }
                        is AuthResult.Error -> {
                            _authState.value = AuthState.Error(result.message)
                        }
                    }
                } else {
                    Log.w("AuthDebug", "Token == null. Возвращаемся в Idle.")
                    _authState.value = AuthState.Idle
                }
            } catch (e: Exception) {
                Log.e("AuthDebug", "Ошибка в ViewModel: ${e.message}")
                _authState.value = AuthState.Error("Ошибка: ${e.localizedMessage}")
            }
        }
    }

    sealed class AuthState {
        object Idle : AuthState()
        object Loading : AuthState()
        object SuccessExisting : AuthState()
        data class SuccessNew(val defaultName: String) : AuthState()
        data class Error(val message: String) : AuthState()
    }


}