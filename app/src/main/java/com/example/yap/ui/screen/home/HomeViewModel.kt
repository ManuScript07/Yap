package com.example.yap.ui.screen.home

import UserPreferences
import VoiceManager
import android.app.Application
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.yap.R
import com.example.yap.data.model.UserItem
import com.example.yap.ui.components.YapButtonState
import com.example.yap.util.extension.countGraphemeClusters
import com.example.yap.util.extension.isEmojiOnly
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val energyPrefs = UserPreferences(application)
    private val voiceManager = VoiceManager(application)

    private val _state = MutableStateFlow(
        HomeUiState(
            users = getInitialUsers(),
//            currentAlertMessage = "Привет, познакомися?)",
        )
    )
    val state: StateFlow<HomeUiState> = _state.asStateFlow()
    private var alertJob: Job? = null
    private var regenJob: Job? = null
    private var isActivatingLocation = false
    private var lastAnchorTime: Long = 0L


    companion object {
        const val PRICE_TEXT = 4
        const val PRICE_EMOJI = 2
        const val PRICE_VOICE = 12
        const val PRICE_SIMPLE_YAP = 1
        const val REGEN_DELAY_MS = 1000L
    }


    private fun updateStateWithPrice(update: (HomeUiState) -> HomeUiState) {
        _state.update { currentState ->
            val newState = update(currentState)
            val calculatedPrice = calculatePrice(
                users = newState.users,
                type = newState.yapType
            )
            newState.copy(yapPrice = calculatedPrice)
        }
    }

    init {
        updateStateWithPrice { it }
//        // Запускаем бесконечный цикл восстановления энергии при старте ViewModel
//        startEnergyRegeneration()
        loadPersistedData()
    }

    private fun loadPersistedData() {
        viewModelScope.launch {
            val (savedStars, savedTime) = energyPrefs.energyData.first()
            val savedUsers = energyPrefs.usersData.first()

            val currentTime = System.currentTimeMillis()

            // Объявляем переменную ЗАРАНЕЕ с дефолтным значением
            var initialDelay = REGEN_DELAY_MS

            _state.update { currentState ->

                val baseStars = savedStars ?: currentState.currentStars
                val max = currentState.maxStars

                if (savedTime == null || savedTime == 0L) {
                    lastAnchorTime = currentTime
                    return@update currentState.copy(
                        currentStars = baseStars,
                        progress = baseStars.toFloat() / max.toFloat()
                    )
                }

                // Вычисляем задержку здесь, она запишется в переменную выше
                val timePassed = currentTime - savedTime
                val restoredStars = (timePassed / REGEN_DELAY_MS).toInt()
                val timeSpentInCurrentCycle = timePassed % REGEN_DELAY_MS

                initialDelay = REGEN_DELAY_MS - timeSpentInCurrentCycle
                lastAnchorTime = currentTime - timeSpentInCurrentCycle

                val finalStars = (baseStars + restoredStars).coerceAtMost(max)
                val finalUsers = savedUsers ?: getInitialUsers()
                currentState.copy(
                    users = finalUsers,
                    currentStars = finalStars,
                    progress = finalStars.toFloat() / max.toFloat()
                )


            }

            updateStateWithPrice { it }

            // Теперь initialDelay виден здесь
            if (_state.value.currentStars < _state.value.maxStars) {
                startEnergyRegeneration(initialDelay)
            }
        }
    }

    // 2. ОБНОВЛЯЕМ ВЫБОР И ПЕРЕСЧИТЫВАЕМ ЦЕНУ
    fun toggleUserYap(userId: Int) {
        updateStateWithPrice { currentState ->
            val updatedUsers = currentState.users.map {
                if (it.id == userId) it.copy(isYapActive = !it.isYapActive) else it
            }
            saveUsersToStore(updatedUsers)
            currentState.copy(users = updatedUsers)
        }
    }


    fun addUser() {
        _state.update { currentState ->
            if (currentState.users.size >= 20) return@update currentState

            val newId = (currentState.users.maxOfOrNull { it.id } ?: 0) + 1
            val randomAvatar = listOf(R.drawable.avatar_1, R.drawable.avatar_2, R.drawable.avatar_3, R.drawable.avatar_4).random()
            val newUser = UserItem(newId, "User $newId", false, randomAvatar)

            val updatedList = currentState.users + newUser
            saveUsersToStore(updatedList) // Сохраняем

            currentState.copy(users = updatedList)
        }
        updateStateWithPrice { it }
    }

    fun removeUser(userId: Int) {
        // Используем нашу защищенную функцию с пересчетом
        updateStateWithPrice { currentState ->
            val updatedUsers = currentState.users.filter { it.id != userId }
            saveUsersToStore(updatedUsers)
            // Логика: если мы удаляем юзера, он автоматически перестает быть получателем.
            // updateStateWithPrice сама вызовет calculatePrice для нового списка.
            currentState.copy(users = updatedUsers)
        }
    }

    private fun saveUsersToStore(users: List<UserItem>) {
        viewModelScope.launch {
            energyPrefs.saveUsers(users)
        }
    }

    private fun getInitialUsers() = listOf(
        UserItem(1, "User1", false, R.drawable.avatar_1),
        UserItem(2, "User2", true, R.drawable.avatar_2),
        UserItem(3, "User3", false, R.drawable.avatar_3),
        UserItem(4, "User4", true, R.drawable.avatar_4)
    )



    fun setLocationToggle(isEnabled: Boolean, isManualAction: Boolean = false) {
        if (isManualAction) isActivatingLocation = true

        _state.update { it.copy(isLocationEnabled = isEnabled) }

        // Сбрасываем флаг через секунду, когда GPS точно проснется
        if (isManualAction) {
            viewModelScope.launch {
                delay(1000)
                isActivatingLocation = false
            }
        }
    }

    // Добавь проверку для защиты
//    fun shouldDisableLocation(isAvailable: Boolean): Boolean {
//        return !isAvailable && !isActivatingLocation
//    }

    // Функция для показа нового сообщения
    fun showAlert(
        message: String? = null,
        @StringRes resId: Int? = null,
        canClose: Boolean = true,
        durationMs: Long? = null
    ) {
        alertJob?.cancel()

        val newId = System.currentTimeMillis() // Генерируем уникальный ключ

        _state.update { it.copy(
            currentAlertMessage = message,
            currentAlertResource = resId,
            canCloseMessage = canClose,
            isEmojiOnly = false,
            alertId = newId // Сохраняем его в стейт
        ) }

        if (durationMs != null) {
            alertJob = viewModelScope.launch {
                delay(durationMs)
                // ПРОВЕРКА: удаляем только если ID совпадает
                if (_state.value.alertId == newId) {
                    dismissMessage()
                }
            }
        }
    }

    // Функция для скрытия (вызывается при клике на крестик)
    fun dismissMessage() {
        alertJob?.cancel()
        updateStateWithPrice { currentState ->
            currentState.copy(
                currentAlertMessage = null,
                currentAlertResource = null,
                isEmojiOnly = false,
                yapType = YapType.YAP
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
        if (_state.value.yapType == YapType.VOICE) return
        alertJob?.cancel()
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
                        userGeneratedContent = emoji,
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
                        userGeneratedContent = newContent,
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
        if (_state.value.yapType == YapType.VOICE) return

        alertJob?.cancel()
        updateStateWithPrice { currentState ->
            currentState.copy(
                currentAlertMessage = message,
                userGeneratedContent = message,
                isEmojiOnly = false,
                isChatPickerOpen = false,
                canCloseMessage = true,
                yapType = YapType.TEXT,
            )
        }
    }



    private fun calculatePrice(users: List<UserItem>, type: YapType): Int {
        val selectedCount = users.count { it.isYapActive }
        if (selectedCount == 0) return 0

        val pricePerUser = when (type) {
            YapType.EMOJI -> PRICE_EMOJI
            YapType.TEXT -> PRICE_TEXT
            YapType.VOICE -> PRICE_VOICE // Добавь константу, например 5 или 10 звезд
            YapType.YAP -> PRICE_SIMPLE_YAP
        }
        return selectedCount * pricePerUser
    }



    // 3. ОТПРАВКА YAP И СПИСАНИЕ ЗВЕЗД
    fun sendYap(latitude: Double?, longitude: Double?) {
        val state = _state.value

        if (state.yapPrice == 0) {
            dismissMessage()
            resetYapButton()
            return
        }

        if (state.currentStars >= state.yapPrice) {
            // 1. РАССЧИТЫВАЕМ НОВЫЙ БАЛАНС
            val newStars = state.currentStars - state.yapPrice
            val newProgress = newStars.toFloat() / state.maxStars.toFloat()

            // 2. ЕСЛИ ЭНЕРГИЯ БЫЛА ПОЛНОЙ, А ТЕПЕРЬ УПАЛА — ЗАПУСКАЕМ РЕГЕНЕРАЦИЮ
            if (state.currentStars == state.maxStars) {
                lastAnchorTime = System.currentTimeMillis()
                startEnergyRegeneration(REGEN_DELAY_MS)
            }



            // --- ЛОГИКА ОТПРАВКИ КОНТЕНТА ---
            when (state.yapType) {
                YapType.VOICE -> {
                    Log.d("API1", "Отправляем ГОЛОС: ${state.voiceAudioUri}")
                }
                YapType.TEXT, YapType.EMOJI -> {
                    // Берем либо текст пользователя, либо то, что в алерте (для обратной совместимости)
                    val content = state.userGeneratedContent
                    Log.d("API1", "Отправляем ТЕКСТ: $content")
                }
                YapType.YAP -> {
                    Log.d("API1", "Отправляем простой YAP, локация $latitude $longitude")
                }
            }
            // 3. СОХРАНЯЕМ В ХРАНИЛИЩЕ (DataStore)
            saveEnergyToStore(newStars, lastAnchorTime)



            // 5. ОЧИСТКА И СБРОС
            dismissMessage() // Это также сбросит цену и тип на дефолтные через updateStateWithPrice
            resetYapButton()
            _state.update {
                it.copy(
                    currentStars = newStars,
                    progress = newProgress
                )
            }

        } else {
            // Если звезд не хватает
            showAlert(message = "Недостаточно звезд!", durationMs = 2000)
            resetYapButton()
        }
    }

    fun handleSendRequest(latitude: Double?, longitude: Double?, finalType: YapType) {
        updateStateWithPrice { it.copy(yapType = finalType) }
        sendYap(latitude, longitude)
    }

    // 4. ТАЙМЕР РЕГЕНЕРАЦИИ ЗВЕЗД
    private fun startEnergyRegeneration(initialDelay: Long = REGEN_DELAY_MS) {
        regenJob?.cancel()
        regenJob = viewModelScope.launch {
            var currentDelay = initialDelay

            while (true) {
                delay(currentDelay)
                currentDelay = REGEN_DELAY_MS

                val max = _state.value.maxStars
                val current = _state.value.currentStars

                if (current < max) {
                    val newStars = current + 1

                    lastAnchorTime = System.currentTimeMillis()

                    // Сохраняем новые данные в DataStore
                    saveEnergyToStore(newStars, lastAnchorTime)

                    _state.update { state ->
                        state.copy(
                            currentStars = newStars,
                            progress = newStars.toFloat() / state.maxStars.toFloat()
                        )
                    }

                    // ИСПРАВЛЕНИЕ 2: Если достигли 100 - убиваем таймер.
                    // Он не должен работать в фоне!
                    if (newStars >= max) {
                        regenJob?.cancel()
                        break
                    }
                } else {
                    regenJob?.cancel()
                    break
                }
            }
        }
    }


    private fun saveEnergyToStore(stars: Int, anchorTime: Long) {
        viewModelScope.launch {
            energyPrefs.saveEnergy(stars, anchorTime)
        }
    }



    fun updateYapButtonState(newState: YapButtonState) {
        _state.update { it.copy(yapButtonState = newState) }
    }

    fun updateYapOffsetY(offset: Float) {
        _state.update { it.copy(yapOffsetY = offset) }
    }

    fun updateYapRecordTime(timeMs: Long) {
        _state.update { it.copy(yapRecordTimeMs = timeMs) }
    }

    fun prepareVoiceForReview(audioPath: String? = null) {
        stopVoiceRecording()
        updateStateWithPrice { currentState ->
            currentState.copy(
                yapType = YapType.VOICE,
                voiceAudioUri = audioPath,
                currentAlertMessage = "Нажмите YAP, чтобы отправить",
                canCloseMessage = false
            )
        }
    }



    fun startVoiceRecording() {
        updateStateWithPrice { it.copy(yapType = YapType.VOICE) }
        voiceManager.startRecording()
    }

    fun stopVoiceRecording() {
        voiceManager.stopRecording()
        val path = voiceManager.currentRecordPath
        if (path != null) {
            updateVoicePath(path)
        }
    }

    fun toggleVoicePlayback() {
        val currentState = _state.value
        if (currentState.isPlayingVoice) {
            voiceManager.playPausePlayback {} // Ставим на паузу
            _state.update { it.copy(isPlayingVoice = false) }
        } else {
            _state.update { it.copy(isPlayingVoice = true) }
            voiceManager.playPausePlayback {
                // Этот блок вызовется, когда аудио доиграет до конца
                _state.update { it.copy(isPlayingVoice = false) }
            }
        }
    }

    fun updateVoicePath(path: String) {
        _state.update { it.copy(voiceAudioUri = path) }
    }

    fun resetYapButton() {
        voiceManager.cancelRecording()
        voiceManager.stopPlayback()
        updateStateWithPrice { currentState ->
            val shouldResetType = currentState.yapType == YapType.VOICE

            currentState.copy(
                yapButtonState = YapButtonState.IDLE,
                yapType = if (shouldResetType) YapType.YAP else currentState.yapType,
                yapRecordTimeMs = 0L,
                yapOffsetY = 0f,
                voiceAudioUri = if (shouldResetType) null else currentState.voiceAudioUri,
                didOverrideMessage = false,
                isPlayingVoice = false,
            )
        }
    }

    fun clearSystemAlertOnly() {
        _state.update { it.copy(
            currentAlertMessage = if (it.yapType == YapType.TEXT || it.yapType == YapType.EMOJI)
                it.userGeneratedContent else null,
            canCloseMessage = it.yapType == YapType.TEXT || it.yapType == YapType.EMOJI
        ) }
    }

    fun getPriceForType(type: YapType): Int {
        val selectedCount = _state.value.users.count { it.isYapActive }
        return selectedCount * when (type) {
            YapType.EMOJI -> PRICE_EMOJI
            YapType.TEXT -> PRICE_TEXT
            YapType.VOICE -> PRICE_VOICE
            YapType.YAP -> PRICE_SIMPLE_YAP
        }
    }

    fun setAlertOverridden(overridden: Boolean) {
        _state.update { it.copy(isSystemAlertOverridden = overridden) }
    }


}