package com.example.yap.ui.screen.addUser

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.yap.data.model.UserItem
import com.example.yap.ui.main.YapApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch


class AddUserViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(AddUserUiState())
    val state = _state.asStateFlow()


    private val app = application as YapApp
    private val userRepository = app.userRepository

    private var currentUser: UserItem? = null


    init {
        viewModelScope.launch {
            // Подписываемся на данные текущего пользователя (L1 кэш из репозитория)
            userRepository.currentUserId?.let { uid ->
                currentUser = userRepository.getUsersByIds(listOf(uid)).firstOrNull()
            }
        }
    }

    fun onQueryChange(newQuery: String) {
        // Очищаем ввод: оставляем только латиницу и цифры
        val sanitized = newQuery.filter { it.isLetterOrDigit() && it.code <= 127 }

        // Код валиден, если в нем ровно 8 символов
        val isValid = sanitized.length == 8

        // Форматируем для UI: ВЕРХНИЙ РЕГИСТР и пробел посередине
        val formatted =
            if (isValid)
                sanitized.uppercase().chunked(4).joinToString(" ")
            else ""

        _state.update {
            it.copy(
                searchQuery = newQuery,
                isValidCode = isValid,
                formattedCodeForUI = formatted,
                remoteSearchResult = null,
                isSearchPerformed = false
            )
        }
    }

    fun executeSearch() {
        val currentState = _state.value
        if (!currentState.isValidCode || currentState.isSearchPerformed) return

        val codeForBackend = currentState.formattedCodeForUI.replace(" ", "").lowercase()
        val tag = "Search_Debug"

        viewModelScope.launch {
            Log.i(tag, "🏁 Начало поиска для кода: $codeForBackend")

            // 1. Ищем ID владельца кода
            val ownerId = userRepository.findUserIdByInviteCode(codeForBackend)

            if (ownerId == null) {
                Log.w(tag, "🔚 Поиск завершен: ID не найден")
                _state.update { it.copy(remoteSearchResult = null, isSearchPerformed = true) }
                return@launch
            }

            // 2. Получаем данные пользователя (здесь сработают твои логи из getUsersByIds)
            Log.d(tag, "👤 ID найден: $ownerId. Запрашиваем данные профиля...")
            val userItem = userRepository.getUsersByIds(listOf(ownerId)).firstOrNull()

            if (userItem != null) {
                // 3. Определяем статус отношений
                val status = when {
                    userItem.id == userRepository.currentUserId -> {
                        Log.d(tag, "ℹ️ Статус: Это текущий пользователь")
                        AddFriendStatus.ALREADY_FRIEND
                    }
                    currentUser?.friends?.contains(userItem.id) == true -> {
                        Log.d(tag, "ℹ️ Статус: Уже в друзьях")
                        AddFriendStatus.ALREADY_FRIEND
                    }
                    else -> {
                        Log.d(tag, "ℹ️ Статус: Можно добавить")
                        AddFriendStatus.CAN_ADD
                    }
                }

                Log.i(tag, "✅ Поиск успешно завершен для ${userItem.name} (${userItem.id})")
                _state.update {
                    it.copy(
                        remoteSearchResult = FoundUser(userItem, status),
                        isSearchPerformed = true
                    )
                }
            } else {
                Log.e(tag, "🔚 Ошибка: ID $ownerId существует в инвайтах, но профиль в users отсутствует")
                _state.update { it.copy(remoteSearchResult = null, isSearchPerformed = true) }
            }
        }
    }
}