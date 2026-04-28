package com.example.yap.ui.screen.myProfile

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.yap.ui.main.YapApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MyProfileViewModel(
    application: Application
) : AndroidViewModel(application) {

    // Получаем зависимости через Application (как в твоих предыдущих примерах)
    private val app = application as YapApp
    private val userRepository = app.userRepository

    private val _state = MutableStateFlow(MyProfileUiState())
    val state = _state.asStateFlow()

    init {
        observeProfile()
    }


    companion object {
        fun provideFactory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(MyProfileViewModel::class.java)) {
                        return MyProfileViewModel(application) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class")
                }
            }
    }

    private fun observeProfile() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            try {
                // Получаем Flow с профилем текущего пользователя
                val profileFlow = userRepository.observeMyProfile()

                if (profileFlow == null) {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = "Не удалось загрузить профиль. Пожалуйста, перезайдите в аккаунт."
                        )
                    }
                    return@launch
                }

                profileFlow
                    .catch { e ->
                        Log.e("MyProfileVM", "Ошибка при загрузке профиля", e)
                        _state.update {
                            it.copy(
                                isLoading = false,
                                error = "Произошла ошибка при загрузке данных: ${e.message}"
                            )
                        }
                    }
                    .collect { currentUser ->
                        if (currentUser != null) {
                            _state.update {
                                it.copy(
                                    isLoading = false,
                                    user = currentUser,
                                    error = null
                                )
                            }
                        } else {
                            _state.update {
                                it.copy(
                                    isLoading = false,
                                    error = "Пользователь не найден"
                                )
                            }
                        }
                    }
            } catch (e: Exception) {
                Log.e("MyProfileVM", "Критическая ошибка", e)
                _state.update {
                    it.copy(isLoading = false, error = "Внутренняя ошибка")
                }
            }
        }
    }

    fun toggleAvatarViewer(isOpen: Boolean) {
        _state.update { it.copy(isAvatarViewerOpen = isOpen) }
    }



    fun logout() {
        viewModelScope.launch {
            try {

            } catch (e: Exception) {
                Log.e("MyProfileVM", "Logout failed: ${e.message}")
            }
        }
    }

}