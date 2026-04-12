package com.example.yap.ui.screen.home

import UserPreferences
import com.example.yap.data.manager.VoiceManager
import android.app.Application
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.yap.R
import com.example.yap.data.model.UserItem
import com.example.yap.service.GroqTranscriptionService
import com.example.yap.ui.components.YapButtonState
import com.example.yap.util.extension.countGraphemeClusters
import com.example.yap.util.extension.isEmojiOnly
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val energyPrefs = UserPreferences(application)
    private val voiceManager = VoiceManager(application)
//    private val transcriptionService = VoskTranscriptionService(application)
//    private val transcriptionService = ServerTranscriptionService()
    private val transcriptionService = GroqTranscriptionService()

    private val _state = MutableStateFlow(
        HomeUiState(
            users = getInitialUsers(),
//            currentAlertMessage = "Привет, познакомися?)",
        )
    )
    val state: StateFlow<HomeUiState> = _state.asStateFlow()
    private var alertJob: Job? = null
    private var regenJob: Job? = null
    private var transcriptionJob: Job? = null
    private var isActivatingLocation = false
    private var lastAnchorTime: Long = 0L

    init {
        updateStateWithPrice { it }

        loadPersistedData()

        viewModelScope.launch {
//            transcriptionService.initModel()
        }
    }
    companion object {
        const val PRICE_TEXT = 4
        const val PRICE_EMOJI = 2
        const val PRICE_VOICE = 12
        const val PRICE_SIMPLE_YAP = 1
        const val REGEN_DELAY_MS = 1000L
    }

    override fun onCleared() {
        super.onCleared()
        voiceManager.stopPlayback()
        voiceManager.cancelRecording()
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
        viewModelScope.launch {
            val state = _state.value

            if (state.yapPrice == 0) {
                dismissMessage()
                resetYapButton()
                return@launch
            }

            // Локальные переменные, чтобы не зависеть от изменений стейта в процессе
            val currentYapType = state.yapType
            val audioPath = state.voiceAudioUri

            // --- 1. ПРОВЕРКА ФАЙЛА (БЕЗ ОЖИДАНИЯ ТЕКСТА) ---
            if (currentYapType == YapType.VOICE) {
                val file = audioPath?.let { File(it) }
                // Проверяем только наличие файла, а не текст
                if (file == null || !file.exists() || file.length() < 500) {
                    Log.e("API1", "Ошибка: Файл не готов")
                    resetYapButton()
                    return@launch
                }
            }

            if (state.currentStars >= state.yapPrice) {
                // Расчет энергии (оставляем как было)
                val newStars = state.currentStars - state.yapPrice
                val newProgress = newStars.toFloat() / state.maxStars.toFloat()

                if (state.currentStars == state.maxStars) {
                    lastAnchorTime = System.currentTimeMillis()
                    startEnergyRegeneration(REGEN_DELAY_MS)
                }

                // --- 2. МГНОВЕННАЯ ОТПРАВКА ---
                when (currentYapType) {
                    YapType.VOICE -> {
                        // Мы НЕ ждем текст. Если он уже есть — берем, если нет — отправляем заглушку
                        val currentText = _state.value.transcribedText ?: "[Голосовое сообщение...]"
                        Log.d("API1", "МГНОВЕННАЯ ОТПРАВКА ГОЛОСА: $audioPath")
                        Log.d("API1", "Текущий текст (может быть пустым): $currentText")

                        // Если расшифровка еще идет, она сама обновит сообщение позже
                        // (Здесь обычно вызывается метод репозитория: repository.sendVoice(file, currentText))
                    }
                    YapType.TEXT, YapType.EMOJI -> {
                        Log.d("API1", "Отправляем ТЕКСТ: ${state.userGeneratedContent}")
                    }
                    YapType.YAP -> {
                        Log.d("API1", "Отправляем простой YAP")
                    }
                }

                // --- 3. МГНОВЕННЫЙ СБРОС UI ---
                saveEnergyToStore(newStars, lastAnchorTime)
                dismissMessage()
                resetYapButton()

                _state.update { it.copy(
                    currentStars = newStars,
                    progress = newProgress,
                    // voiceAudioUri = null, // НЕ зануляй сразу, если хочешь дождаться текста!
                    // transcribedText = null
                ) }

            } else {
                showAlert("Недостаточно звезд!", durationMs = 2000)
                resetYapButton()
            }
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
        val oldState = _state.value.yapButtonState
        _state.update { it.copy(yapButtonState = newState) }

        val wasRecording = oldState == YapButtonState.RECORDING || oldState == YapButtonState.LOCKED
        val isStillRecording = newState == YapButtonState.RECORDING || newState == YapButtonState.LOCKED

        if (wasRecording && !isStillRecording) {
            // Обязательно вызываем стоп, куда бы мы ни ушли
            stopVoiceRecording()
        }
    }

    fun updateYapOffsetY(offset: Float) {
        _state.update { it.copy(yapOffsetY = offset) }
    }

    fun updateYapRecordTime(timeMs: Long) {
        _state.update { it.copy(yapRecordTimeMs = timeMs) }
    }





    fun startVoiceRecording() {
        val startTime = System.currentTimeMillis()
        updateStateWithPrice {
            it.copy(
                yapType = YapType.VOICE,
                recordStartDate = startTime,
                yapRecordTimeMs = 0L
            ) }
        voiceManager.startRecording()
    }

    fun stopVoiceRecording() {
        viewModelScope.launch {
            voiceManager.stopRecording() // Здесь внутри должен быть recorder.stop() и release()
            val finalDuration = _state.value.yapRecordTimeMs

            if (finalDuration < 300) {
                voiceManager.cancelRecording() // Метод, который просто удаляет файл и стопает рекордер
                resetYapButton()
                Log.d("API1", "Запись слишком короткая ($finalDuration мс), игнорируем")
                return@launch
            }

            voiceManager.stopRecording()
            delay(200)

            val path = voiceManager.currentRecordPath
            if (path != null) {
                val file = File(path)
                if (file.exists() && file.length() > 0) {
                    Log.d("API1", "Файл записан. Размер: ${file.length()} байт")

                    _state.update { it.copy(
                        voiceAudioUri = path,
                        isPlayingVoice = false,
                        totalDurationMs = finalDuration,
                        yapRecordTimeMs = 0L,
                        transcribedText = null
                    ) }
                    runTranscription(file)
                }
            }
        }
    }

    fun toggleVoicePlayback() {
        // Мы не меняем стейт здесь вручную,
        // доверяем это коллбэкам от com.example.yap.data.manager.VoiceManager
        viewModelScope.launch {
            voiceManager.playPausePlayback(
                onStateChanged = { isPlaying ->
                    _state.update { it.copy(isPlayingVoice = isPlaying) }
                },
                onCompletion = {
                    _state.update {
                        it.copy(
                            isPlayingVoice = false,
                            yapRecordTimeMs = it.totalDurationMs
                        ) }
                }
            )
        }
    }



    fun resetYapButton() {
        voiceManager.cancelRecording()
        voiceManager.stopPlayback()
        transcriptionJob?.cancel()
        updateStateWithPrice { currentState ->
            val shouldResetType = currentState.yapType == YapType.VOICE

            currentState.copy(
                yapButtonState = YapButtonState.IDLE,
                yapType = if (shouldResetType) YapType.YAP else currentState.yapType,
                yapRecordTimeMs = 0L,
                recordStartDate = null,
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

    // В HomeViewModel.kt
    fun finishRecordingAndGoToReview() {
        val currentState = _state.value

        // Проверяем, находимся ли мы в состоянии, которое требует завершения записи
        if (currentState.yapButtonState == YapButtonState.RECORDING ||
            currentState.yapButtonState == YapButtonState.LOCKED) {

            // 1. Физически останавливаем запись через менеджер
            voiceManager.stopRecording()
            val finalPath = voiceManager.currentRecordPath

            // 2. Атомарно обновляем стейт через наш метод с пересчетом цены
            updateStateWithPrice { it.copy(
                yapButtonState = YapButtonState.REVIEW,
                yapType = YapType.VOICE, // Гарантируем тип VOICE для цены
                voiceAudioUri = finalPath,
                yapOffsetY = 0f,
                currentAlertMessage = "Запись сохранена. Нажмите YAP для отправки",
                canCloseMessage = false
            ) }

            // Вибрируем, так как это важный переход
            // (Если есть доступ к haptic во ViewModel, если нет — оставим в LaunchedEffect)
        }
    }


    // В ViewModel
    fun getVoicePath(): String? = voiceManager.currentRecordPath

    fun isVoiceRecordValid(): Boolean {
        val path = getVoicePath() ?: return false
        val file = File(path)
        return file.exists() && file.length() > 1000 // Примерно 1кб минимум для AAC
    }

    fun cancelVoiceRecording() {
        voiceManager.cancelRecording() // Удаляет файл физически
        resetYapButton()
    }

    fun getPlaybackPosition(): Long {
        return voiceManager.getCurrentPosition().toLong()
    }




    private fun runTranscription(file: File) {
        // Отменяем старую расшифровку, если пользователь начал записывать новый "яп"
//        transcriptionJob?.cancel()

        viewModelScope.launch(Dispatchers.IO) {
            // Устанавливаем флаг загрузки, но это теперь не блокирует кнопку Send
            _state.update { it.copy(isTranscribing = true, transcribedText = null) }

            Log.d("STT", "Начинаем расшифровку файла: ${file.name} (${file.length()} байт)")

            transcriptionService.transcribe(file)
                .onSuccess { text ->
                    Log.d("STT", "Groq успешно вернул текст: $text")

                    // Обновляем состояние. Если пользователь еще не нажал Send,
                    // при нажатии он подтянет этот готовый текст.
                    _state.update { it.copy(
                        transcribedText = text,
                        isTranscribing = false
                    ) }

                    // --- ЛОГИКА ДЛЯ БУДУЩЕГО (Firebase) ---
                    // Если ты уже отправил сообщение (например, сохранил ID последнего сообщения),
                    // здесь можно вызвать:
                    // repository.updateMessageText(lastMessageId, text)
                }
                .onFailure { error ->
                    // Если интернета нет, Groq упадет сюда.
                    // Интерфейс при этом не зависнет, просто текст останется null.
                    Log.e("STT", "Ошибка расшифровки (возможно, нет сети): ${error.message}")
                    _state.update { it.copy(isTranscribing = false) }
                }
        }
    }

}