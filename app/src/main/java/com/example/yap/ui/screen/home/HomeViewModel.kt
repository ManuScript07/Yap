package com.example.yap.ui.screen.home

import androidx.lifecycle.ViewModel
import com.example.yap.R
import com.example.yap.data.UserItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class HomeViewModel : ViewModel() {

    private val _state = MutableStateFlow(
        HomeUiState(
            users = getInitialUsers(),
            currentAlertMessage = "Привет, познакомися?)",
            canCloseMessage = true
        )
    )
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    fun toggleUserYap(userId: Int) {
        _state.update { currentState ->
            val updatedUsers = currentState.users.map { user ->
                if (user.id == userId) {
                    user.copy(isYapActive = !user.isYapActive)
                } else {
                    user
                }
            }
            currentState.copy(users = updatedUsers)
        }
    }
    private fun getInitialUsers() = listOf(
        UserItem(1, "User1", false, R.drawable.avatar_1),
        UserItem(2, "User2", true, R.drawable.avatar_2),
        UserItem(3, "User3", false, R.drawable.avatar_3),
        UserItem(4, "User4", true, R.drawable.avatar_4)
    )

    fun toggleLocation(enabled: Boolean) {
        _state.update { it.copy(isLocationEnabled = enabled) }
    }

    // Функция для показа нового сообщения
    fun showAlert(message: String, canClose: Boolean = true) {
        _state.update { it.copy(
            currentAlertMessage = message,
            canCloseMessage = canClose
        ) }
    }

    // Функция для скрытия (вызывается при клике на крестик)
    fun dismissMessage() {
        _state.update { it.copy(
            currentAlertMessage = null
        ) }
    }

    // Самый гибкий вариант
    fun toggleEmojiPicker(open: Boolean) {
        _state.update { it.copy(
            isEmojiPickerOpen = open,
            // Если открываем эмодзи, чат ОБЯЗАН закрыться
            isChatPickerOpen = if (open) false else it.isChatPickerOpen
        ) }
    }



    fun selectEmoji(emoji: String) {
        _state.update { currentState ->
            val currentContent = currentState.currentAlertMessage ?: ""

            // Считаем реальное количество символов/эмодзи
            // В Kotlin/Java для эмодзи лучше использовать codePointCount
            val emojiCount = currentContent.codePointCount(0, currentContent.length)

            // Проверяем, является ли текущее сообщение набором эмодзи или текстом
            val isJustEmojis = currentContent.all { it.isSurrogate() || it.code > 128 }

            when {
                // Если там был текст (длинная строка) — заменяем на первый эмодзи
                !isJustEmojis && currentContent.isNotEmpty() -> {
                    currentState.copy(currentAlertMessage = emoji)
                }

                // Если уже есть 5 эмодзи — ничего не меняем (игнорируем ввод)
                isJustEmojis && emojiCount >= 5 -> {
                    currentState
                }

                // В остальных случаях — добавляем в конец
                else -> {
                    val newContent = currentContent + emoji
                    currentState.copy(
                        currentAlertMessage = newContent,
                        // Закрываем панель только когда набрали ровно 5
                        isEmojiPickerOpen = (emojiCount + 1) < 5
                    )
                }
            }
        }
    }

    fun toggleChatPicker(open: Boolean) {
        _state.update { it.copy(
            isChatPickerOpen = open,
            isEmojiPickerOpen = if (open) false else it.isEmojiPickerOpen
        ) }
    }

    fun selectQuickMessage(message: String) {
        _state.update { it.copy(
            currentAlertMessage = message,
            isChatPickerOpen = false
        ) }
    }

    fun setSheetExpanded(expanded: Boolean) {
        _state.update { it.copy(isSheetExpanded = expanded) }
    }

}