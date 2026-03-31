package com.example.yap.ui.screen.home

import androidx.lifecycle.ViewModel
import com.example.yap.R
import com.example.yap.data.UserItem
import com.example.yap.ui.util.countGraphemeClusters
import com.example.yap.ui.util.isEmojiOnly
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

            // 1. Проверяем, что БЫЛО в поле до нажатия
            val wasEmojiOnly = currentContent.isEmojiOnly()
            val currentCount = currentContent.countGraphemeClusters()

            when {
                // СЛУЧАЙ А: В поле был текст (Да, Гоу)
                // Мы заменяем текст на эмодзи и ставим флаг true
                !wasEmojiOnly && currentContent.isNotEmpty() -> {
                    currentState.copy(
                        currentAlertMessage = emoji,
                        isEmojiOnly = true, // Теперь только эмодзи
                        isEmojiPickerOpen = true
                    )
                }

                // СЛУЧАЙ Б: Достигнут лимит 5 эмодзи
                wasEmojiOnly && currentCount >= 5 -> {
                    currentState
                }

                // СЛУЧАЙ В: Добавляем эмодзи к уже существующим эмодзи
                else -> {
                    val newContent = currentContent + emoji
                    currentState.copy(
                        currentAlertMessage = newContent,
                        isEmojiOnly = true, // Подтверждаем, что это всё еще эмодзи
                        isEmojiPickerOpen = (currentCount + 1) < 5
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
        _state.update { currentState ->
            currentState.copy(
                currentAlertMessage = message,
                // ОБЯЗАТЕЛЬНО: Проверяем новый текст.
                // Для "Да", "Гоу" и т.д. это вернет false, и шрифт уменьшится.
                isEmojiOnly = message.isEmojiOnly(),
                isChatPickerOpen = false
            )
        }
    }

    fun setSheetExpanded(expanded: Boolean) {
        _state.update { it.copy(isSheetExpanded = expanded) }
    }

}