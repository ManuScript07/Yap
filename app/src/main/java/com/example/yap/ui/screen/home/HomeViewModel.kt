package com.example.yap.ui.screen.home

import android.annotation.SuppressLint
import android.app.Application
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.yap.R
import com.example.yap.data.manager.VoiceManager
import com.example.yap.data.model.MessageEntity
import com.example.yap.data.model.UserItem
import com.example.yap.ui.components.YapButtonState
import com.example.yap.ui.main.YapApp
import com.example.yap.util.NetworkMonitor
import com.example.yap.util.extension.countGraphemeClusters
import com.example.yap.util.extension.isEmojiOnly
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class HomeViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val app = application as YapApp
    private val configManager = app.configManager
    private val userRepository = app.userRepository
    private val chatRepository = app.chatRepository

    private val transcriptionService = app.transcriptionService

    private val energyPrefs = app.userPrefs
    private val voiceManager = VoiceManager(application)



    private val networkMonitor = NetworkMonitor(application)

    private val _state = MutableStateFlow(
        HomeUiState(
        )
    )
    val state: StateFlow<HomeUiState> = _state.asStateFlow()
    private var alertJob: Job? = null
    private var regenJob: Job? = null
    private var isActivatingLocation = false
    private var lastAnchorTime: Long = 0L
    private var statusJob: Job? = null

    private val regenDelay: Long
        get() = configManager.regenDelayMs

    private val priceSimpleYap: Int
        get() = configManager.priceSimpleYap

    private val priceEmoji: Int
        get() = configManager.priceEmoji

    private val priceText: Int
        get() = configManager.priceText

    private val priceVoice: Int
        get() = configManager.priceVoice

    private val maxLengthInputText: Int
        get() = configManager.maxLengthInputText


    init {
        Log.d("API1", "Инициализация")
        updateStateWithPrice { it }
        observeQuickList()
        observeEnergy()
        observeNotificationsCount()
        observeAuthStateAndSyncFcmToken()
    }


    override fun onCleared() {
        super.onCleared()
        voiceManager.stopPlayback()
        voiceManager.cancelRecording()
    }

    private fun observeAuthStateAndSyncFcmToken() {
        viewModelScope.launch {
            userRepository.currentUserFlow.collect { user ->
                _state.update { it.copy(currentUserId = user?.uid) }
                if (user != null) {
                    Log.d("FCM_SYNC", "User authenticated: ${user.uid}. Checking FCM token...")
                    userRepository.updateFcmTokenIfNeeded()
                } else {
                    Log.d("FCM_SYNC", "No active user. Skipping FCM sync.")
                }
            }
        }
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


    private fun observeQuickList() {
        viewModelScope.launch {
            // Слушаем профиль из Firebase
            userRepository.observeMyProfile().collect { myProfile ->
                val quickListIds = myProfile?.quickList ?: emptyList()

                if (quickListIds.isNotEmpty()) {
                    val usersFromDb = userRepository.getUsersByIds(quickListIds)

                    // Достаем один раз то, что сохранено в DataStore
                    val persistedUsers = energyPrefs.usersData.first() ?: emptyList()

                    updateStateWithPrice { currentState ->
                        val sortedUsers = quickListIds.mapNotNull { id ->
                            val dbUser = usersFromDb.find { it.id == id }

                            // ПРИОРИТЕТЫ для isYapActive:
                            // 1. Сначала смотрим в текущем стейте (если юзер уже на экране)
                            // 2. Если в стейте нет, смотрим в DataStore (после перезагрузки)
                            // 3. Если нигде нет — по умолчанию false

                            val isCurrentlyActive = currentState.users.find { it.id == id }?.isYapActive
                                ?: persistedUsers.find { it.id == id }?.isYapActive
                                ?: false

                            dbUser?.copy(isYapActive = isCurrentlyActive)
                        }
                        currentState.copy(users = sortedUsers)
                    }
                } else {
                    updateStateWithPrice { it.copy(users = emptyList()) }
                }
            }
        }
    }

    private fun observeEnergy() {
        viewModelScope.launch {
            energyPrefs.energyData.collect { (savedStars, savedTime) ->
                val currentTime = System.currentTimeMillis()

                _state.update { currentState ->
                    val max = currentState.maxStars
                    val baseStars = savedStars ?: currentState.currentStars

                    val (finalStars, _) = if (savedTime == null || savedTime == 0L) {
                        lastAnchorTime = currentTime
                        baseStars to regenDelay
                    } else {
                        val timePassed = currentTime - savedTime
                        val restoredStars = (timePassed / regenDelay).toInt()
                        val timeSpentInCurrentCycle = timePassed % regenDelay
                        lastAnchorTime = currentTime - timeSpentInCurrentCycle
                        (baseStars + restoredStars).coerceAtMost(max) to (regenDelay - timeSpentInCurrentCycle)
                    }

                    currentState.copy(
                        currentStars = finalStars,
                        progress = finalStars.toFloat() / max.toFloat()
                    )
                }

                // Запуск регенерации
                if (_state.value.currentStars < _state.value.maxStars) {
                    startEnergyRegeneration(regenDelay)
                }
            }
        }
    }


    fun toggleUserYap(userId: String) {
        updateStateWithPrice { currentState ->
            val updatedUsers = currentState.users.map {
                if (it.id == userId) it.copy(isYapActive = !it.isYapActive) else it
            }
            saveUsersToStore(updatedUsers)
            currentState.copy(users = updatedUsers)
        }
    }


    fun removeUser(userId: String) {
        viewModelScope.launch {
            // Удаляем из Firebase. observeMyProfile сам поймает изменение и обновит список!
            userRepository.toggleQuickList(userId, add = false)
        }
    }

    private fun saveUsersToStore(users: List<UserItem>) {
        viewModelScope.launch {
            energyPrefs.saveUsers(users)
        }
    }


    fun setLocationToggle(isEnabled: Boolean, isManualAction: Boolean = false) {
        if (isManualAction) isActivatingLocation = true

        _state.update { it.copy(isLocationEnabled = isEnabled) }

        if (isManualAction) {
            viewModelScope.launch {
                delay(1000)
                isActivatingLocation = false
            }
        }
    }



    fun showAlert(
        message: String? = null,
        @StringRes resId: Int? = null,
        canClose: Boolean = true,
        durationMs: Long? = null
    ) {
        alertJob?.cancel()

        val newId = System.currentTimeMillis()

        _state.update { it.copy(
            currentAlertMessage = message,
            currentAlertResource = resId,
            canCloseMessage = canClose,
            isEmojiOnly = false,
            alertId = newId
        ) }

        if (durationMs != null) {
            alertJob = viewModelScope.launch {
                delay(durationMs)
                if (_state.value.alertId == newId) {
                    dismissMessage()
                }
            }
        }
    }

    fun showStatus(
        @StringRes resId: Int? = null,
        message: String? = null,
        isSuccess: Boolean = false,
        durationMs: Long = 2000
    ) {
        statusJob?.cancel()
        statusJob = viewModelScope.launch {
            _state.update { it.copy(systemStatusResource = null, systemStatusMessage = null) }


            delay(50)

            _state.update { it.copy(
                systemStatusResource = resId,
                systemStatusMessage = message,
                isStatusSuccess = isSuccess,
                statusId = System.currentTimeMillis()
            ) }

            delay(durationMs)

            _state.update { it.copy(systemStatusResource = null, systemStatusMessage = null) }
        }
    }

    fun dismissMessage() {
        alertJob?.cancel()
        updateStateWithPrice { currentState ->
            currentState.copy(
                currentAlertMessage = null,
                currentAlertResource = null,
                userGeneratedContent = null,
                isEmojiOnly = false,
                isQuickMessage = false,
                isCustomInputActive = false,
                yapType = YapType.YAP
            )
        }
    }

    fun toggleEmojiPicker(open: Boolean) {
        _state.update { it.copy(
            isEmojiPickerOpen = open,
            isChatPickerOpen = if (open) false else it.isChatPickerOpen,
            isCustomInputActive = if (open) false else it.isCustomInputActive
        ) }
    }



    fun selectEmoji(emoji: String) {
        if (_state.value.yapType == YapType.VOICE) return
        alertJob?.cancel()
        updateStateWithPrice { currentState ->
            val currentContent = currentState.currentAlertMessage ?: ""

            val wasEmojiOnly = currentContent.isEmojiOnly()
            val currentCount = currentContent.countGraphemeClusters()

            when {
                wasEmojiOnly && currentCount >= 5 -> currentState

                !wasEmojiOnly && currentContent.isNotEmpty() -> {
                    currentState.copy(
                        currentAlertMessage = emoji,
                        userGeneratedContent = emoji,
                        isEmojiOnly = true,
                        isQuickMessage = false,
                        isCustomInputActive = false,
                        yapType = YapType.EMOJI,
                        canCloseMessage = true
                    )
                }

                else -> {
                    val newContent = currentContent + emoji
                    val newCount = currentCount + 1
                    currentState.copy(
                        currentAlertMessage = newContent,
                        userGeneratedContent = newContent,
                        isEmojiOnly = true,
                        isQuickMessage = false,
                        isCustomInputActive = false,
                        yapType = YapType.EMOJI,
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
            isEmojiPickerOpen = if (open) false else it.isEmojiPickerOpen,
            isCustomInputActive = if (open) false else it.isCustomInputActive
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
                isQuickMessage = true,
                isChatPickerOpen = false,
                canCloseMessage = true,
                yapType = YapType.TEXT,
                isCustomInputActive = false
            )
        }
    }

    fun startCustomTextInput() {
        if (_state.value.yapType == YapType.VOICE) return

        _state.update { currentState ->
            // Проверяем: если текущий текст был НАПЕЧАТАН юзером, оставляем его.
            // Если это были эмодзи или шаблон — начинаем с чистого листа.
            val isTypedText = currentState.yapType == YapType.TEXT && !currentState.isEmojiOnly && !currentState.isQuickMessage
            val initialText = if (isTypedText) currentState.userGeneratedContent ?: "" else ""

            currentState.copy(
                isChatPickerOpen = false,
                yapType = YapType.TEXT,
                currentAlertMessage = initialText,
                userGeneratedContent = initialText,
                isCustomInputActive = true,
                isEmojiOnly = false,
                isQuickMessage = false
            )
        }
    }


    @SuppressLint("SuspiciousIndentation")
    fun onCustomTextChanged(newText: String) {
        if (newText.length <= maxLengthInputText)
        updateStateWithPrice { currentState ->
            currentState.copy(
                currentAlertMessage = newText,
                userGeneratedContent = newText,
                isEmojiOnly = false,
                isQuickMessage = false,
                canCloseMessage = true
            )
        }
    }

    fun closeCustomTextInput() {
        _state.update { it.copy(isCustomInputActive = false) }
    }



    private fun calculatePrice(users: List<UserItem>, type: YapType): Int {
        val selectedCount = users.count { it.isYapActive }
        if (selectedCount == 0) return 0

        val pricePerUser = when (type) {
            YapType.EMOJI -> priceEmoji
            YapType.TEXT -> priceText
            YapType.VOICE -> priceVoice
            YapType.YAP -> priceSimpleYap
        }
        return selectedCount * pricePerUser
    }



    fun sendYap(latitude: Double?, longitude: Double?) {
        viewModelScope.launch {
            Log.d("API1","Старт")
            val initialState = _state.value

            if (initialState.yapPrice == 0) {
                dismissMessage()
                resetYapButton()
                return@launch
            }

            // ВЫНОСИМ ПЕРЕМЕННЫЕ НА УРОВЕНЬ ВЫШЕ, ЧТОБЫ ИХ ВИДЕЛ ВЕСЬ МЕТОД
            val currentYapType = initialState.yapType
            val audioPath = initialState.voiceAudioUri
            val fileToSend = audioPath?.let { File(it) } // Теперь fileToSend доступен везде

            if (currentYapType == YapType.VOICE) {
                if (_state.value.recordStartDate != null) {
                    Log.d("API1", "Ожидание формирования аудиофайла...")
                    var waitCount = 0
                    while (_state.value.recordStartDate != null && waitCount < 20) {
                        delay(50)
                        waitCount++
                    }
                }

                // Проверяем наш вынесенный fileToSend
                if (fileToSend == null || !fileToSend.exists() || fileToSend.length() < 500) {
                    Log.e("API1", "Ошибка: Файл не готов даже после ожидания")
                    resetYapButton()
                    return@launch
                }
            }

            // Берем свежий стейт после возможных задержек
            val state = _state.value

            if (state.currentStars >= state.yapPrice) {
                // Расчет энергии
                val newStars = state.currentStars - state.yapPrice
                val newProgress = newStars.toFloat() / state.maxStars.toFloat()

                if (state.currentStars == state.maxStars) {
                    lastAnchorTime = System.currentTimeMillis()
                    startEnergyRegeneration(regenDelay)
                }


                val senderId = FirebaseAuth.getInstance().currentUser?.uid ?: return@launch

                val activeReceivers = state.users.filter { it.isYapActive && it.id != senderId}
                if (activeReceivers.isEmpty()) return@launch



                Log.d("API1", "Receiver ID: $activeReceivers")
                val messageToSend = MessageEntity(
                    senderId = senderId,
                    type = currentYapType.name,
                    latitude = if (state.isLocationEnabled) latitude else null,
                    longitude = if (state.isLocationEnabled) longitude else null,
                    timestamp = Timestamp.now()
                )

                when (currentYapType) {
                    YapType.VOICE -> {
                        Log.d("API1", "МГНОВЕННАЯ ОТПРАВКА ГОЛОСА: $audioPath")
                        if (fileToSend != null) {
                            viewModelScope.launch {
                                // 1. ЗАГРУЖАЕМ ФАЙЛ ОДИН РАЗ
                                val uploadResult = chatRepository.uploadVoiceFile(fileToSend, senderId)

                                uploadResult.onSuccess { audioUrl ->
                                    Log.d("API1", "Голос успешно загружен. URL: $audioUrl")

                                    val generatedMessageIds = mutableListOf<String>()

                                    // 2. РАССЫЛАЕМ СООБЩЕНИЯ В FIRESTORE С ЭТИМ URL
                                    activeReceivers.forEach { receiver ->
                                        val voiceMessage = messageToSend.copy(
                                            receiverId = receiver.id,
                                            audioUrl = audioUrl,
                                            text = null // Ждем транскрипцию
                                        )

                                        // Используем обычный sendMessage для создания записи в базе
                                        val dbResult = chatRepository.sendMessage(voiceMessage)
                                        dbResult.onSuccess { msgId ->
                                            generatedMessageIds.add(msgId)
                                        }
                                    }

                                    // 3. ЗАПУСКАЕМ ТРАНСКРИБАЦИЮ ОДИН РАЗ, ПЕРЕДАВАЯ ВСЕ ID
                                    if (generatedMessageIds.isNotEmpty()) {
                                        runTranscription(fileToSend, generatedMessageIds)
                                    }
                                }.onFailure {
                                    Log.e("API1", "Не удалось загрузить аудиофайл: ${it.message}")
                                    showAlert(resId = R.string.nework_error, durationMs = 2000L)
                                }
                            }
                        }
                    }
                    else -> {
                        activeReceivers.forEach { receiver ->
                            val textMessage = messageToSend.copy(
                                receiverId = receiver.id,
                                text = if (currentYapType == YapType.YAP) "Отправил(а) Yap" else state.userGeneratedContent
                            )
                            viewModelScope.launch {
                                chatRepository.sendMessage(textMessage)
                            }
                        }
                    }
                }


                saveEnergyToStore(newStars, lastAnchorTime)

                val shouldShowSuccess = _state.value.showSuccessAlert

                dismissMessage()
                resetYapButton()

                if (shouldShowSuccess) {
                    showStatus(
                        resId = R.string.yap_sent_success,
                        durationMs = 3000,
                        isSuccess = true
                    )
                }

                _state.update { it.copy(
                    lastYapSentTrigger = System.currentTimeMillis(),
                    currentStars = newStars,
                    progress = newProgress,
                    showSuccessAlert = false
                ) }

            } else {
                showAlert(resId = R.string.not_enough_stars, durationMs = 2000)
                resetYapButton()
            }
        }
    }

    fun handleSendRequest(latitude: Double?, longitude: Double?, finalType: YapType) {
        Log.d("API1", "HandleSendRequest type: $finalType")
        updateStateWithPrice { it.copy(yapType = finalType) }
        sendYap(latitude, longitude)
    }


    fun handleDirectSend(userId: String, latitude: Double?, longitude: Double?, type: YapType) {
        if (!networkMonitor.isOnline) {
            showStatus(resId = R.string.no_internet, durationMs = 3000)
            return
        }


        if (_state.value.currentStars < priceSimpleYap) {
            showStatus(resId = R.string.not_enough_stars, durationMs = 2000)
            return
        }

        _state.update { currentState ->
            currentState.copy(
                users = currentState.users.map { it.copy(isYapActive = it.id == userId) },
                yapType = type,
                showSuccessAlert = true
            )
        }

        updateStateWithPrice { it }

        sendYap(latitude, longitude)

    }

    private fun startEnergyRegeneration(initialDelay: Long = regenDelay) {
        regenJob?.cancel()
        regenJob = viewModelScope.launch {
            var currentDelay = initialDelay

            while (true) {
                delay(currentDelay)
                currentDelay = regenDelay

                val max = _state.value.maxStars
                val current = _state.value.currentStars

                if (current < max) {
                    val newStars = current + 1

                    lastAnchorTime = System.currentTimeMillis()

                    saveEnergyToStore(newStars, lastAnchorTime)

                    _state.update { state ->
                        state.copy(
                            currentStars = newStars,
                            progress = newStars.toFloat() / state.maxStars.toFloat()
                        )
                    }

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
                yapRecordTimeMs = 0L,
                isCustomInputActive = false,

                currentAlertMessage = null,
                userGeneratedContent = null,
            ) }
        voiceManager.startRecording()
    }

    fun stopVoiceRecording() {
        viewModelScope.launch {
            val state = _state.value
            val startTime = state.recordStartDate ?: return@launch

            val finalDuration = System.currentTimeMillis() - startTime
            voiceManager.stopRecording()
            if (finalDuration < 300) {
                voiceManager.cancelRecording()
                resetYapButton()
                Log.d("API1", "Запись слишком короткая ($finalDuration мс), игнорируем")
                return@launch
            }

            delay(250)

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
                        transcribedText = null,
                        recordStartDate = null,
                        isTranscribing = false
//                        isTranscribing = shouldTranscribe
                    ) }
//                    if (shouldTranscribe)
//                        runTranscription(file)
//                    else
//                        Log.d("STT1", "Расшифровка пропущена: цена сообщения 0 (нет получателей)")
                }
            }
        }
    }

    fun toggleVoicePlayback() {
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

        Log.d("API1", "Отмена")
        voiceManager.stopRecording()
        voiceManager.stopPlayback()
        // Если что это основная проблема
        updateStateWithPrice { currentState ->
            val shouldResetType = currentState.yapType == YapType.VOICE

            Log.d("API1", "Тип Yap: $shouldResetType")
            currentState.copy(
                yapButtonState = YapButtonState.IDLE,
                yapType = if (shouldResetType) YapType.YAP else currentState.yapType,
                yapRecordTimeMs = 0L,
                totalDurationMs = 0L,
                recordStartDate = null,
                yapOffsetY = 0f,
                voiceAudioUri = null,
                didOverrideMessage = false,
                isPlayingVoice = false,
                transcribedText = null,
                isCustomInputActive = false
            )
        }
    }

    fun consumeYapPulse() {
        _state.update { it.copy(lastYapSentTrigger = 0L) }
    }


    fun clearSystemAlertOnly() {
        _state.update { it.copy(
            currentAlertMessage = if (it.yapType == YapType.TEXT || it.yapType == YapType.EMOJI)
                it.userGeneratedContent else null,
            currentAlertResource = null,
            canCloseMessage = it.yapType == YapType.TEXT || it.yapType == YapType.EMOJI
        ) }
    }

    fun getPriceForType(type: YapType): Int {
        val selectedCount = _state.value.users.count { it.isYapActive }
        return selectedCount * when (type) {
            YapType.EMOJI -> priceEmoji
            YapType.TEXT -> priceText
            YapType.VOICE -> priceVoice
            YapType.YAP -> priceSimpleYap
        }
    }

    fun setAlertOverridden(overridden: Boolean) {
        _state.update { it.copy(isSystemAlertOverridden = overridden) }
    }

    fun finishRecordingAndGoToReview() {
        val currentState = _state.value

        // Проверяем, находимся ли мы в состоянии, которое требует завершения записи
        if (currentState.yapButtonState == YapButtonState.RECORDING ||
            currentState.yapButtonState == YapButtonState.LOCKED) {


            updateYapButtonState(YapButtonState.REVIEW)
            updateYapOffsetY(0f)
            _state.update { it.copy(
                yapType = YapType.VOICE,
                currentAlertMessage = "Запись сохранена. Нажмите YAP для отправки",
                canCloseMessage = false
            ) }
        }
    }



    fun isVoiceRecordValid(): Boolean {
        val state = _state.value
        val startTime = state.recordStartDate

        val duration = if (startTime != null) {
            System.currentTimeMillis() - startTime
        } else {
            state.totalDurationMs
        }

        Log.d("API1", "Проверка длительности: $duration мс (startTime был $startTime)")
        return duration > 600
    }


    fun cancelVoiceRecording() {
        voiceManager.cancelRecording()
        resetYapButton()
    }

    fun getPlaybackPosition(): Long {
        return voiceManager.getCurrentPosition().toLong()
    }




    private fun runTranscription(file: File, messageIds: List<String>) {

        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isTranscribing = true, transcribedText = null) }

            Log.d("STT1", "Начинаем расшифровку файла: ${file.name} (${file.length()} байт)")

            transcriptionService.transcribe(file)
                .onSuccess { text ->
                    Log.d("STT1", "Groq успешно вернул текст: $text")


                    _state.update { it.copy(
                        transcribedText = text.trim(),
                        isTranscribing = false
                    ) }
                    messageIds.forEach { msgId ->
                        viewModelScope.launch {
                            chatRepository.updateMessageText(msgId, text.trim())
                        }
                    }
                }
                .onFailure { error ->
                    Log.e("STT1", "Ошибка расшифровки (возможно, нет сети): ${error.message}")
                    _state.update { it.copy(isTranscribing = false) }
                }
        }
    }

    fun isNetworkAvailable(): Boolean = networkMonitor.isOnline


    private fun observeNotificationsCount() {
        Log.d("NOTIF_DEBUG", "Функция вызвана")
        val currentUserId = userRepository.currentUserId
        Log.d("NOTIF_DEBUG", "Current User ID: $currentUserId")
        if (currentUserId == null) return

        viewModelScope.launch {
            // Вызываем один раз и держим collect активным
            chatRepository.observeUserMessages(currentUserId)
                .combine(energyPrefs.readMessageIds) { messages, readIds ->
                    val firstUnread = messages.firstOrNull { !readIds.contains(it.id) }
                    Log.d("NOTIF_DEBUG", "Total: ${messages.size}, Read: ${readIds.size}")
                    if (firstUnread != null) {
                        Log.d("NOTIF_DEBUG", "Example Unread ID: ${firstUnread.id}")
                        Log.d("NOTIF_DEBUG", "Available ReadIds: $readIds")
                    }
                    // Логика подсчета: исключаем те ID, что сохранены локально в DataStore
                    messages.count { !readIds.contains(it.id) }
                }
                .distinctUntilChanged() // Пропускаем только если цифра РЕАЛЬНО изменилась
                .collect { unreadCount ->
                    _state.update { it.copy(notificationsCount = unreadCount) }
                }
        }
    }

}