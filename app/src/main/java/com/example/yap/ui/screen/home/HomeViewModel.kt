package com.example.yap.ui.screen.home

import UserPreferences
import com.example.yap.data.manager.VoiceManager
import android.app.Application
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.yap.ChatRepository
import com.example.yap.MessageEntity
import com.example.yap.R
import com.example.yap.UserRepository
import com.example.yap.data.model.UserItem
import com.example.yap.service.GroqTranscriptionService
import com.example.yap.ui.components.YapButtonState
import com.example.yap.ui.main.YapApp
import com.example.yap.util.NetworkMonitor
import com.example.yap.util.extension.countGraphemeClusters
import com.example.yap.util.extension.isEmojiOnly
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

class HomeViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val app = application as YapApp
    private val userRepository = app.userRepository
    private val chatRepository = app.chatRepository

//    private val CURRENT_USER_ID = "1"
//    val currentUserUid = FirebaseAuth.getInstance().currentUser?.uid

    private val energyPrefs = UserPreferences(application)
    private val voiceManager = VoiceManager(application)
//    private val transcriptionService = VoskTranscriptionService(application)
//    private val transcriptionService = ServerTranscriptionService()
    private val transcriptionService = GroqTranscriptionService()

    private val networkMonitor = NetworkMonitor(application)

    private val _state = MutableStateFlow(
        HomeUiState(
//            users = getInitialUsers(),
        )
    )
    val state: StateFlow<HomeUiState> = _state.asStateFlow()
    private var alertJob: Job? = null
    private var regenJob: Job? = null
    private var isActivatingLocation = false
    private var lastAnchorTime: Long = 0L
    private var statusJob: Job? = null

    init {
        updateStateWithPrice { it }
//        loadPersistedData()
        observeQuickList()
        observeEnergy()
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


//    private fun observeQuickList() {
//        viewModelScope.launch {
//            userRepository.observeMyProfile().collect { snapshot ->
//                Log.d("NAV_DEBUG", "Snapshot received: ${snapshot?.id}, exists: ${snapshot?.exists()}")
//
//                if (snapshot != null && snapshot.exists()) {
//                    val quickListIds = snapshot.get("quickList") as? List<String> ?: emptyList()
//                    Log.d("NAV_DEBUG", "IDs in quickList: $quickListIds")
//
//                    val realUsers = withContext(Dispatchers.IO) {
//                        quickListIds.mapNotNull { id ->
//                            val profile = userRepository.getUserProfile(id)
//                            Log.d("NAV_DEBUG", "Loaded profile for $id: ${profile?.name}")
//                            profile?.copy(isYapActive = true)
//                        }
//                    }
//
//                    _state.update { it.copy(users = realUsers) }
//                    updateStateWithPrice { it }
//                } else {
//                    Log.d("NAV_DEBUG", "Snapshot is null or doesn't exist")
//                }
//            }
//        }
//    }
    private fun observeQuickList() {
        viewModelScope.launch {
            // Слушаем профиль из Firebase
            userRepository.observeMyProfile().collect { snapshot ->
                val quickListIds = snapshot?.get("quickList") as? List<String> ?: emptyList()

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
                        baseStars to REGEN_DELAY_MS
                    } else {
                        val timePassed = currentTime - savedTime
                        val restoredStars = (timePassed / REGEN_DELAY_MS).toInt()
                        val timeSpentInCurrentCycle = timePassed % REGEN_DELAY_MS
                        lastAnchorTime = currentTime - timeSpentInCurrentCycle
                        (baseStars + restoredStars).coerceAtMost(max) to (REGEN_DELAY_MS - timeSpentInCurrentCycle)
                    }

                    currentState.copy(
                        currentStars = finalStars,
                        progress = finalStars.toFloat() / max.toFloat()
                    )
                }

                // Запуск регенерации
                if (_state.value.currentStars < _state.value.maxStars) {
                    startEnergyRegeneration(REGEN_DELAY_MS)
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


//    fun addUser() {
//        _state.update { currentState ->
//            if (currentState.users.size >= 20) return@update currentState
//
//            val newId = (currentState.users.maxOfOrNull { it.id } ?: 0).toString() + 1
//            val randomAvatar = listOf(R.drawable.avatar_1, R.drawable.avatar_2, R.drawable.avatar_3, R.drawable.avatar_4).random()
//            val newUser = UserItem(newId, "User $newId", false, randomAvatar)
//
//            val updatedList = currentState.users + newUser
//            saveUsersToStore(updatedList)
//
//            currentState.copy(users = updatedList)
//        }
//        updateStateWithPrice { it }
//    }

    fun addUser() {
        viewModelScope.launch {
            val currentState = _state.value
            if (currentState.users.size >= 20) return@launch

            // 1. Генерируем ID и данные для нового "фиктивного" юзера
            val newId = "user_" + System.currentTimeMillis()
            val randomNames = listOf("Алексей", "Мария", "Иван", "София")
            val newUserMap = mapOf(
                "name" to randomNames.random(),
                "quickList" to emptyList<String>(),
                "mutedUsers" to emptyList<String>()
            )

            try {
                // 2. Создаем этого юзера в глобальной коллекции users
                userRepository.usersCollection.document(newId).set(newUserMap).await()

                // 3. Добавляем его ID в наш собственный Quick List
                userRepository.toggleQuickList(newId, add = true)

                // ПРИМЕЧАНИЕ: Нам не нужно вручную обновлять _state.update { ... }
                // Наш Flow в observeQuickList() сам увидит обновление документа в Firebase
                // и перерисует экран. Это и есть "Single Source of Truth".
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Failed to add fake user", e)
            }
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

    fun showStatus(@StringRes resId: Int? = null, message: String? = null, durationMs: Long = 2000) {
        statusJob?.cancel()
        statusJob = viewModelScope.launch {
            // 1. Сначала принудительно обнуляем
            _state.update { it.copy(systemStatusResource = null, systemStatusMessage = null) }

            // 2. Даем Compose время понять, что надо начать анимацию выхода (exit)
            // 50-100мс достаточно, чтобы AnimatedVisibility начал закрываться
            delay(50)

            // 3. Ставим новые данные
            _state.update { it.copy(
                systemStatusResource = resId,
                systemStatusMessage = message,
                statusId = System.currentTimeMillis()
            ) }

            delay(durationMs)

            // 4. Убираем
            _state.update { it.copy(systemStatusResource = null, systemStatusMessage = null) }
        }
    }

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

    fun toggleEmojiPicker(open: Boolean) {
        _state.update { it.copy(
            isEmojiPickerOpen = open,
            isChatPickerOpen = if (open) false else it.isChatPickerOpen
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
            YapType.VOICE -> PRICE_VOICE
            YapType.YAP -> PRICE_SIMPLE_YAP
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
                    startEnergyRegeneration(REGEN_DELAY_MS)
                }

                val receiverId = state.users.find { it.isYapActive }?.id ?: return@launch

                val senderId = FirebaseAuth.getInstance().currentUser?.uid ?: return@launch
                Log.d("API1", "Receiver ID: $receiverId")
                var messageToSend = MessageEntity(
                    senderId = senderId,
                    receiverId = receiverId,
                    type = currentYapType.name,
                    latitude = if (state.isLocationEnabled) latitude else null,
                    longitude = if (state.isLocationEnabled) longitude else null,
                    timestamp = com.google.firebase.Timestamp.now()
                )

                when (currentYapType) {
                    YapType.VOICE -> {
                        Log.d("API1", "МГНОВЕННАЯ ОТПРАВКА ГОЛОСА: $audioPath")
                        // Текст пока пустой, мы обновим его в Firebase позже
                        messageToSend = messageToSend.copy(audioUrl = audioPath)
                    }
                    YapType.TEXT, YapType.EMOJI -> {
                        Log.d("API1", "Отправляем ТЕКСТ: ${state.userGeneratedContent}")
                        messageToSend = messageToSend.copy(text = state.userGeneratedContent)
                    }
                    YapType.YAP -> {
                        Log.d("API1", "Отправляем простой YAP")
                        messageToSend = messageToSend.copy(text = "Отправил(а) Yap")
                    }
                }

                // 1. Отправляем сообщение в базу
                val result = chatRepository.sendMessage(messageToSend)

                // 2. Если успешно отправлено и это голос — запускаем расшифровку
                result.onSuccess { messageId ->
                    if (currentYapType == YapType.VOICE && fileToSend != null) {
                        // Теперь мы передаем правильный файл и ID сообщения!
                        runTranscription(fileToSend, messageId)
                    }
                }

                saveEnergyToStore(newStars, lastAnchorTime)

                val shouldShowSuccess = _state.value.showSuccessAlert

                dismissMessage()
                resetYapButton()

                if (shouldShowSuccess) {
                    showStatus(resId = R.string.yap_sent_success, durationMs = 3000)
                }

                _state.update { it.copy(
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

        val isLocEnabled = _state.value.isLocationEnabled




        if (_state.value.currentStars < PRICE_SIMPLE_YAP) {
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
                yapRecordTimeMs = 0L
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
                    val currentPrice = _state.value.yapPrice
                    val shouldTranscribe = currentPrice > 0

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
        voiceManager.stopRecording()
        voiceManager.stopPlayback()

        _state.update { currentState ->
            val shouldResetType = currentState.yapType == YapType.VOICE

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
                transcribedText = null
            )
        }
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
            YapType.EMOJI -> PRICE_EMOJI
            YapType.TEXT -> PRICE_TEXT
            YapType.VOICE -> PRICE_VOICE
            YapType.YAP -> PRICE_SIMPLE_YAP
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




    private fun runTranscription(file: File, messageId: String) {

        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isTranscribing = true, transcribedText = null) }

            Log.d("STT1", "Начинаем расшифровку файла: ${file.name} (${file.length()} байт)")

            transcriptionService.transcribe(file)
                .onSuccess { text ->
                    Log.d("STT1", "Groq успешно вернул текст: $text")


                    _state.update { it.copy(
                        transcribedText = text,
                        isTranscribing = false
                    ) }
                    chatRepository.updateMessageText(messageId, text)

                    // --- ЛОГИКА ДЛЯ БУДУЩЕГО (Firebase) ---
                    // Если ты уже отправил сообщение (например, сохранил ID последнего сообщения),
                    // здесь можно вызвать:
                    // repository.updateMessageText(lastMessageId, text)
                }
                .onFailure { error ->
                    Log.e("STT1", "Ошибка расшифровки (возможно, нет сети): ${error.message}")
                    _state.update { it.copy(isTranscribing = false) }
                }
        }
    }

    fun isNetworkAvailable(): Boolean = networkMonitor.isOnline


}