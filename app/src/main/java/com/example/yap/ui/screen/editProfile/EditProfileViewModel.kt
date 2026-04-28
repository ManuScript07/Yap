package com.example.yap.ui.screen.editProfile

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.yap.data.model.UserItem
import com.example.yap.ui.main.YapApp
import com.example.yap.util.extension.compressToByteArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as YapApp
    private val repository = app.userRepository

    private val _uiState = MutableStateFlow<EditState>(EditState.Idle)
    val uiState = _uiState.asStateFlow()

    fun updateProfile(
        newName: String,
        newUsername: String,
        newDob: Long?,
        newShowOnlyDay: Boolean,
        newBio: String,
        newPhotoUri: Uri?,
        isPhotoRemoved: Boolean, // Добавляем этот флаг
        currentUser: UserItem
    ) {
        viewModelScope.launch {
            _uiState.value = EditState.Loading
            try {
                withContext(Dispatchers.IO) {
                    val startTime = System.currentTimeMillis()
                    val userId = repository.currentUserId ?: throw Exception("User not found")
                    val updates = mutableMapOf<String, Any?>()

                    // 1. Текстовые поля (без изменений)
                    val trimmedName = newName.trim()
                    val trimmedUsername = newUsername.trim()
                    val trimmedBio = newBio.trim()

                    if (trimmedName != currentUser.name) updates["name"] = trimmedName
                    if (trimmedUsername != currentUser.username) updates["username"] = trimmedUsername
                    if (trimmedBio != currentUser.bio) updates["bio"] = trimmedBio
                    if (newDob != currentUser.dobTimestamp) updates["dobTimestamp"] = newDob
                    if (newShowOnlyDay != currentUser.showOnlyDay) updates["showOnlyDay"] = newShowOnlyDay

                    // 2. КОРРЕКТНАЯ ОБРАБОТКА ФОТО
                    when {
                        // Сценарий А: Выбрано НОВОЕ фото
                        newPhotoUri != null -> {
                            val context = getApplication<Application>().applicationContext
                            val photoBytes = newPhotoUri.compressToByteArray(context)
                                ?: throw Exception("Ошибка обработки фото")

                            val uploadResult = repository.uploadAvatar(photoBytes, userId)
                            val newAvatarUrl = uploadResult.getOrThrow()
                            updates["avatarUrl"] = newAvatarUrl
                        }

                        // Сценарий Б: Пользователь нажал "Удалить" (крестик)
                        isPhotoRemoved -> {
                            // Если текущий URL не пустой, значит нам нужно его занулить в БД
                            if (!currentUser.avatarUrl.isNullOrEmpty()) {
                                updates["avatarUrl"] = null
                            }
                        }

                        // Сценарий В: photoUri == null и isPhotoRemoved == false
                        // Мы ничего не добавляем в updates["avatarUrl"], и старое фото сохраняется
                    }

                    if (updates.isEmpty()) {
                        withContext(Dispatchers.Main) { _uiState.value = EditState.Success }
                        return@withContext
                    }

                    repository.updateUserProfile(userId, updates).getOrThrow()

                    val elapsedTime = System.currentTimeMillis() - startTime
                    val remainingTime = 800L - elapsedTime
                    if (remainingTime > 0) delay(remainingTime)
                }
                _uiState.value = EditState.Success
            } catch (e: Exception) {
                _uiState.value = EditState.Error(e.localizedMessage ?: "Ошибка обновления")
            }
        }
    }

    sealed class EditState {
        object Idle : EditState()
        object Loading : EditState()
        object Success : EditState()
        data class Error(val message: String) : EditState()
    }
}