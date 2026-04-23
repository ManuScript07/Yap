package com.example.yap.ui.screen.registration

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.yap.ui.main.YapApp
import com.example.yap.util.extension.compressToByteArray
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RegistrationViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as YapApp
    private val repository = app.userRepository

    private val _registrationState = MutableStateFlow<RegistrationState>(RegistrationState.Idle)
    val registrationState = _registrationState.asStateFlow()

    fun completeRegistration(
        name: String,
        username: String,
        dob: Long?,
        showOnlyDay: Boolean,
        bio: String,
        photoUri: Uri?
    ) {
        viewModelScope.launch {
            _registrationState.value = RegistrationState.Loading

            try {
                val currentUser = FirebaseAuth.getInstance().currentUser
                if (currentUser == null) {
                    _registrationState.value = RegistrationState.Error("Пользователь не найден")
                    return@launch
                }

                val userId = currentUser.uid
                val email = currentUser.email

                var avatarUrl: String? = null

                // 1. Сжимаем и загружаем фото, если оно есть
                if (photoUri != null) {
                    val context = getApplication<Application>().applicationContext
                    val photoBytes = photoUri.compressToByteArray(context)

                    if (photoBytes != null) {
                        val uploadResult = repository.uploadAvatar(photoBytes, userId)
                        if (uploadResult.isSuccess) {
                            avatarUrl = uploadResult.getOrNull()
                        } else {
                            // Если фото не загрузилось, можно либо прервать регу, либо продолжить без фото.
                            // Для надежности прервем, чтобы юзер попробовал снова.
                            _registrationState.value = RegistrationState.Error("Не удалось загрузить фото")
                            return@launch
                        }
                    }
                }

                // 2. Формируем данные профиля
                val userData = mapOf(
                    "name" to name,
                    "username" to username,
                    "dobTimestamp" to dob,
                    "showOnlyDay" to showOnlyDay,
                    "bio" to bio,
                    "avatarUrl" to avatarUrl
                )

                // 3. Пишем в Firestore
                val saveResult = repository.completeUserRegistration(userId, email, userData)

                if (saveResult.isSuccess) {
                    _registrationState.value = RegistrationState.Success
                } else {
                    _registrationState.value = RegistrationState.Error(saveResult.exceptionOrNull()?.message ?: "Ошибка сохранения")
                }

            } catch (e: Exception) {
                _registrationState.value = RegistrationState.Error(e.localizedMessage ?: "Неизвестная ошибка")
            }
        }
    }

    sealed class RegistrationState {
        object Idle : RegistrationState()
        object Loading : RegistrationState()
        object Success : RegistrationState()
        data class Error(val message: String) : RegistrationState()
    }
}