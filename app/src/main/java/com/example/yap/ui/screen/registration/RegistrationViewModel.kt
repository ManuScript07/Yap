package com.example.yap.ui.screen.registration

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.yap.ui.main.YapApp
import com.example.yap.util.extension.compressToByteArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

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
        photoUri: Uri?,
        isPhotoRemoved: Boolean
    ) {
        viewModelScope.launch {
            _registrationState.value = RegistrationState.Loading

            try {
                // 1. Уходим с главного потока сразу для всех тяжелых операций
                val result = withContext(Dispatchers.IO) {
                    val currentUser = repository.getCurrentUser()
                        ?: return@withContext Result.failure(Exception("Пользователь не найден"))

                    val userId = currentUser.uid

                    // 2. Запускаем загрузку фото и генерацию кода ПАРАЛЛЕЛЬНО (если это возможно)
                    // Но так как код нужен для регистрации, оставим его здесь

                    var avatarUrl: String? = null
                    if (photoUri != null) {
                        val context = getApplication<Application>().applicationContext
                        Log.d("RegLog", "Starting compression...")
                        // Сжатие теперь в Dispatchers.IO — UI не зависнет
                        val photoBytes = photoUri.compressToByteArray(context)
                            ?: throw Exception("Ошибка обработки фото")


                        Log.d("RegLog", "Complete compression...")
                        Log.d("RegLog", "Size to upload: ${photoBytes.size}")
                        Log.d("RegLog", "Starting avatar upload...")
                        val uploadResult = repository.uploadAvatar(photoBytes, userId)
                        avatarUrl = uploadResult.getOrNull()
                            ?: throw Exception("Не удалось загрузить фото")
                        Log.d("RegLog", "Avatar uploaded: $avatarUrl")
                    }

                    val userCode = repository.generateUniqueUserCode().getOrThrow()
                    Log.d("RegLog", "Code generated: $userCode")

                    val userData = mapOf(
                        "name" to name.trim(),
                        "username" to username.trim(),
                        "userCode" to userCode.trim(),
                        "dobTimestamp" to dob,
                        "showOnlyDay" to showOnlyDay,
                        "bio" to bio.trim(),
                        "avatarUrl" to avatarUrl
                    )

                    Log.d("RegLog", "Transaction started...")

                    withTimeout(15000) {
                        repository.completeUserRegistration(userId, currentUser.email, userData)

                    }



                }
                Log.d("RegLog", "Transaction Cpmplite")

                if (result.isSuccess) {
                    _registrationState.value = RegistrationState.Success
                } else {
                    _registrationState.value = RegistrationState.Error(result.exceptionOrNull()?.message ?: "Ошибка")
                }

            } catch (e: TimeoutCancellationException) {
                _registrationState.value = RegistrationState.Error("Слишком долгое ожидание. Проверьте интернет.")
            } catch (e: Exception) {
                _registrationState.value = RegistrationState.Error(e.localizedMessage ?: "Ошибка")
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