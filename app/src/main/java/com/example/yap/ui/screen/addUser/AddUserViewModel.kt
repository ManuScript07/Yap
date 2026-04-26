package com.example.yap.ui.screen.addUser

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.yap.R
import com.example.yap.data.model.UserItem
import com.example.yap.ui.main.YapApp
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch



sealed class AddUserEvent {
    data class ShowStatus(val message: String? = null, val resId: Int? = null) : AddUserEvent()
}

class AddUserViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(AddUserUiState())
    val state = _state.asStateFlow()


    private val app = application as YapApp
    private val userRepository = app.userRepository
    private val friendRequestRepository = app.friendsRequestRepository


    private val _events = MutableSharedFlow<AddUserEvent>()
    val events = _events.asSharedFlow()
    var currentStatusId by mutableStateOf(0L)
        private set


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

                // ЭКОНОМИЯ: Сначала проверяем самые "дешевые" условия (себя и кэш друзей)
                val isSelf = userItem.id == userRepository.currentUserId
                val isAlreadyFriend = currentUser?.friends?.contains(userItem.id) == true
                // 3. Определяем статус отношений
                val status = when {
                    isSelf -> AddFriendStatus.ALREADY_FRIEND // Или отдельный статус CANT_ADD_SELF
                    isAlreadyFriend -> AddFriendStatus.ALREADY_FRIEND
                    else -> {
                        // Если это не мы и не наш друг, проверяем, нет ли уже висящей заявки
                        // Эта функция внутри использует RAM-кэш и легкий запрос в сеть
                        val isPending = friendRequestRepository.checkIsRequestPending(userItem.id)
                        if (isPending) AddFriendStatus.PENDING else AddFriendStatus.CAN_ADD
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
                Log.e(
                    tag,
                    "🔚 Ошибка: ID $ownerId существует в инвайтах, но профиль в users отсутствует"
                )
                _state.update { it.copy(remoteSearchResult = null, isSearchPerformed = true) }
            }
        }
    }


    fun sendFriendRequest(receiverId: String) {
        val currentResult = _state.value.remoteSearchResult ?: return

        // Двойная проверка: отправляем только если кнопка активна (CAN_ADD)
        if (currentResult.status != AddFriendStatus.CAN_ADD) return

        viewModelScope.launch {
            // 1. Оптимистичное обновление UI: сразу делаем кнопку неактивной (PENDING)
            // Это дает пользователю мгновенный отклик, пока запрос летит по сети
            _state.update {
                it.copy(remoteSearchResult = currentResult.copy(status = AddFriendStatus.PENDING))
            }

            // 2. Выполняем сетевой запрос
            val result = friendRequestRepository.sendRequest(receiverId)

            result.onSuccess {
                // Генерируем новый ID события для Pill
                currentStatusId = System.currentTimeMillis()
                _events.emit(AddUserEvent.ShowStatus(resId = R.string.request_sent_success))
                result.onFailure { exception ->
                    // Если произошла ошибка (нет сети), откатываем статус обратно к CAN_ADD,
                    // чтобы пользователь мог попробовать еще раз.
                    _state.update {
                        it.copy(remoteSearchResult = currentResult.copy(status = AddFriendStatus.CAN_ADD))
                    }
                    currentStatusId = System.currentTimeMillis()
                    // Анализируем ошибку для более точного уведомления
                    val errorRes = if (exception.message?.contains("permission") == true) {
                        R.string.error_already_sent // или другое по смыслу
                    } else {
                        R.string.no_internet
                    }
                    _events.emit(AddUserEvent.ShowStatus(resId = errorRes))
                }
            }
        }
    }
}
