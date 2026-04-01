package com.example.yap.ui.screen.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.yap.R
import com.example.yap.data.UserItem
import com.example.yap.ui.util.countGraphemeClusters
import com.example.yap.ui.util.isEmojiOnly
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {

    private val _state = MutableStateFlow(
        HomeUiState(
            users = getInitialUsers(),
//            currentAlertMessage = "Привет, познакомися?)",
        )
    )
    val state: StateFlow<HomeUiState> = _state.asStateFlow()


    companion object {
        const val PRICE_TEXT = 3
        const val PRICE_EMOJI = 2
        const val PRICE_SIMPLE_YAP = 1
        const val REGEN_DELAY_MS = 5000L // 1 звезда каждые 5 секунд
    }


    private fun updateStateWithPrice(update: (HomeUiState) -> HomeUiState) {
        _state.update { currentState ->
            val newState = update(currentState)
            val calculatedPrice = calculatePrice(
                users = newState.users,
                isEmojiOnly = newState.isEmojiOnly,
                type = newState.yapType
            )
            newState.copy(yapPrice = calculatedPrice)
        }
    }

    init {
        updateStateWithPrice { it }
        // Запускаем бесконечный цикл восстановления энергии при старте ViewModel
        startEnergyRegeneration()
    }

    // 2. ОБНОВЛЯЕМ ВЫБОР И ПЕРЕСЧИТЫВАЕМ ЦЕНУ
    fun toggleUserYap(userId: Int) {
        updateStateWithPrice { currentState ->
            val updatedUsers = currentState.users.map {
                if (it.id == userId) it.copy(isYapActive = !it.isYapActive) else it
            }
            currentState.copy(users = updatedUsers)
        }
    }


    fun addUser() {
        _state.update { currentState ->
            if (currentState.users.size >= 20) return@update currentState

            val newId = (currentState.users.maxOfOrNull { it.id } ?: 0) + 1
            val randomAvatar = listOf(R.drawable.avatar_1, R.drawable.avatar_2, R.drawable.avatar_3, R.drawable.avatar_4).random()
            val newUser = UserItem(newId, "User $newId", false, randomAvatar)

            currentState.copy(users = currentState.users + newUser)
        }
    }

    fun removeUser(userId: Int) {
        // Используем нашу защищенную функцию с пересчетом
        updateStateWithPrice { currentState ->
            val updatedUsers = currentState.users.filter { it.id != userId }

            // Логика: если мы удаляем юзера, он автоматически перестает быть получателем.
            // updateStateWithPrice сама вызовет calculatePrice для нового списка.
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
            canCloseMessage = canClose,
            isEmojiOnly = false
        ) }
    }

    // Функция для скрытия (вызывается при клике на крестик)
    fun dismissMessage() {
        updateStateWithPrice { currentState ->
            currentState.copy(
                currentAlertMessage = null,
                yapType = YapType.YAP,
                isEmojiOnly = false // Лучше сбрасывать и его, раз мы возвращаемся к обычному YAP
            )
        }
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
        updateStateWithPrice { currentState ->
            val currentContent = currentState.currentAlertMessage ?: ""

            // Вспомогательные расчеты
            val wasEmojiOnly = currentContent.isEmojiOnly() // твоя функция расширения
            val currentCount = currentContent.countGraphemeClusters() // твоя функция счета

            when {
                // Лимит 5 эмодзи — ничего не меняем
                wasEmojiOnly && currentCount >= 5 -> currentState

                // Был текст — заменяем его на первый эмодзи
                !wasEmojiOnly && currentContent.isNotEmpty() -> {
                    currentState.copy(
                        currentAlertMessage = emoji,
                        isEmojiOnly = true,
                        yapType = YapType.EMOJI,
                        canCloseMessage = true
                    )
                }

                // Добавляем эмодзи к существующим или в пустую строку
                else -> {
                    val newContent = currentContent + emoji
                    val newCount = currentCount + 1
                    currentState.copy(
                        currentAlertMessage = newContent,
                        isEmojiOnly = true,
                        yapType = YapType.EMOJI,
                        // Автоматически закрываем пикер, если набрали 5
                        isEmojiPickerOpen = newCount < 5,
                        canCloseMessage = true
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
        updateStateWithPrice { currentState ->
            currentState.copy(
                currentAlertMessage = message,
                // Для быстрых сообщений ("Гоу", "Ты где?") всегда false
                isEmojiOnly = false,
                isChatPickerOpen = false,
                canCloseMessage = true,
                yapType = YapType.TEXT
            )
        }
    }

    fun setSheetExpanded(expanded: Boolean) {
        _state.update { it.copy(isSheetExpanded = expanded) }
    }




    // 1. КАЛЬКУЛЯТОР ЦЕНЫ
    private fun calculatePrice(users: List<UserItem>, isEmojiOnly: Boolean, type: YapType): Int {
        val selectedCount = users.count { it.isYapActive }
        val pricePerUser = when {
            isEmojiOnly -> PRICE_EMOJI
            type == YapType.TEXT -> PRICE_TEXT // Замени на свою проверку текстового сообщения
            else -> PRICE_SIMPLE_YAP
        }
        return selectedCount * pricePerUser
    }



    // 3. ОТПРАВКА YAP И СПИСАНИЕ ЗВЕЗД
    fun sendYap() {
        val state = _state.value
        if (state.yapPrice == 0) return // Защита: нет получателей

        if (state.currentStars >= state.yapPrice) {
            // Хватает звезд -> Списываем
            val newStars = state.currentStars - state.yapPrice
            val newProgress = newStars.toFloat() / state.maxStars.toFloat()

            _state.update {
                it.copy(
                    currentStars = newStars,
                    progress = newProgress,
                    // Опционально: сбросить выделение получателей после отправки
                    // users = it.users.map { u -> u.copy(isYapActive = false) },
                    // yapPrice = 0
                )
            }

            Log.d("ViewModel", "Сообщение отправлено. Списано: ${state.yapPrice}")
            // TODO: Вызвать отправку сообщения на сервер/в БД
        } else {
            // Не хватает звезд -> Показываем ошибку
            // TODO: Затриггерить показ AlertMessage
            Log.d("ViewModel", "Недостаточно звезд!")
        }
    }

    // 4. ТАЙМЕР РЕГЕНЕРАЦИИ ЗВЕЗД
    private fun startEnergyRegeneration() {
        viewModelScope.launch {
            while (true) {
                delay(REGEN_DELAY_MS)
                _state.update { state ->
                    if (state.currentStars < state.maxStars) {
                        val newStars = state.currentStars + 1
                        state.copy(
                            currentStars = newStars,
                            progress = newStars.toFloat() / state.maxStars.toFloat()
                        )
                    } else {
                        state // Если полная шкала - ничего не делаем
                    }
                }
            }
        }
    }


}