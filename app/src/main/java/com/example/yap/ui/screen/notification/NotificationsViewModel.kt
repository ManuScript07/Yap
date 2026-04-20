package com.example.yap.ui.screen.notification

import UserPreferences
import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.application
import androidx.lifecycle.viewModelScope
import com.example.yap.R
import com.example.yap.data.manager.VoiceManager
import com.example.yap.data.model.UserItem
import com.example.yap.ui.main.YapApp
import com.example.yap.util.formatTime
import com.example.yap.util.getTimeAgo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.net.URL
import kotlin.coroutines.cancellation.CancellationException

class NotificationsViewModel(
    application: Application,
) : AndroidViewModel(application) {


    private val app = application as YapApp
    private val userRepository = app.userRepository
    private val chatRepository = app.chatRepository
    private val transcriptionService = app.transcriptionService
    private val energyPrefs = UserPreferences(application)
    private val _state = MutableStateFlow(NotificationsUiState())
    private val processedIds = mutableSetOf<String>()
    private val voiceManager = VoiceManager(application)
    private val muteJobs = mutableMapOf<String, Job>()
    private val activeDownloads = mutableSetOf<String>()


    val state: StateFlow<NotificationsUiState> = _state.asStateFlow()

    init {
        observeMessages()
    }

    override fun onCleared() {
        super.onCleared()
        voiceManager.stopPlayback()
    }

    private fun observeMessages() {
        val currentUserId = userRepository.currentUserId ?: return

        viewModelScope.launch {
            combine(
                chatRepository.observeUserMessages(currentUserId),
                userRepository.observeMyProfile(),
                energyPrefs.usersData,
                energyPrefs.transcriptionsCache
            ) { messages, myProfileSnapshot, _, transCache ->

                // 1. Собираем все уникальные ID отправителей из списка сообщений
                val senderIds = messages.map { it.senderId }.distinct()

                // 2. Подгружаем их профили одним пакетом (UserRepository теперь кэширует их)
                val loadedProfiles = userRepository.getUsersByIds(senderIds)

                val cloudQuickListIds = myProfileSnapshot?.get("quickList") as? List<String> ?: emptyList()
                val mutedIds = myProfileSnapshot?.get("mutedUsers") as? List<String> ?: emptyList()

                // 3. Маппим сообщения, используя уже загруженные данные
                messages.map { entity ->
                    val senderProfile = loadedProfiles.find { it.id == entity.senderId }
                        ?: UserItem(entity.senderId, "Unknown", false, R.drawable.avatar_1)

                    val isInCloudList = cloudQuickListIds.contains(entity.senderId)
                    val isMuted = mutedIds.contains(entity.senderId)
                    val displayShortText = entity.text ?: transCache[entity.audioUrl]

                    NotificationItemModel(
                        id = entity.id,
                        user = senderProfile.copy(
                            isYapActive = isInCloudList,
                            isMuted = isMuted
                        ),
                        messageText = displayShortText?.trim(),
                        hasLocation = entity.latitude != null,
                        latitude = entity.latitude,
                        longitude = entity.longitude,
                        timestamp = formatTime(entity.timestamp),
                        timeAgo = getTimeAgo(entity.timestamp),
                        isUserInQuickList = isInCloudList,
                        isMuted = isMuted,
                        audioUrl = entity.audioUrl,
                        isTranscribing = false
                    )
                }
            }
                .catch { e -> /* ... */ }
                .collect { updatedNotifications ->
                    _state.update { it.copy(notifications = updatedNotifications, isLoading = false) }
                }
        }
    }



    fun deleteNotification(id: String) {
        viewModelScope.launch {
            try {
                // "Мягкое" удаление в базе.
                // SnapshotListener сам исключит это сообщение из списка, когда придет обновление.
                chatRepository.hideMessageForReceiver(id)
            } catch (e: Exception) {
                Log.e("NotificationsVM", "Ошибка при удалении: ${e.message}")
            }
        }
    }

    fun muteNotification(notificationId: String) {
        // 1. Находим уведомление
        val targetNotification = _state.value.notifications.find { it.id == notificationId }
        val targetUserId = targetNotification?.user?.id ?: return
        val currentlyMuted = targetNotification.isMuted

        // --- OPTIMISTIC UI UPDATE ---
        // Сразу меняем состояние в локальном State, не дожидаясь ответа сервера.
        // Пользователь мгновенно видит результат нажатия.
        _state.update { currentState ->
            currentState.copy(
                notifications = currentState.notifications.map {
                    if (it.id == notificationId) it.copy(isMuted = !currentlyMuted) else it
                }
            )
        }

        // --- DEBOUNCE LOGIC ---
        // Если пользователь кликнул еще раз по этому же юзеру в течение 500мс — отменяем прошлый запрос
        muteJobs[targetUserId]?.cancel()

        muteJobs[targetUserId] = viewModelScope.launch {
            try {
                delay(500) // "Полка" ожидания. Если за это время прилетит новый клик, этот Job умрет.

                Log.d("NotificationsVM", "Отправка запроса в Firebase: target=$targetUserId, mute=${!currentlyMuted}")
                userRepository.toggleMute(targetUserId, !currentlyMuted)

            } catch (e: CancellationException) {
                // Это норма: просто пользователь кликнул еще раз, не считаем за ошибку
                Log.d("NotificationsVM", "Запрос отменен: пользователь передумал")
            } catch (e: Exception) {
                Log.e("NotificationsVM", "Ошибка при муте: ${e.message}")

                // --- ROLLBACK (Откат) ---
                // Если сервер вернул ошибку, возвращаем иконку в исходное состояние
                _state.update { currentState ->
                    currentState.copy(
                        notifications = currentState.notifications.map {
                            if (it.id == notificationId) it.copy(isMuted = currentlyMuted) else it
                        }
                    )
                }
            } finally {
                // Чистим карту Job после завершения (успешного или нет)
                if (muteJobs[targetUserId] == coroutineContext[Job]) {
                    muteJobs.remove(targetUserId)
                }
            }
        }
    }






    fun toggleUserQuickList(userFromNotification: UserItem, isCurrentlyInList: Boolean) {
        viewModelScope.launch {
            // Если уже в списке — удаляем (false), если нет — добавляем (true)
            userRepository.toggleQuickList(userFromNotification.id, add = !isCurrentlyInList)
        }
    }

    // В NotificationsViewModel
    fun markAsRead(ids: List<String>) {
        // Оставляем только те, которые мы еще не пытались сохранить в этой сессии
        val newlyVisible = ids.filter { it !in processedIds }
        if (newlyVisible.isEmpty()) return

        processedIds.addAll(newlyVisible)

        viewModelScope.launch(NonCancellable + Dispatchers.IO) {
            // Увеличиваем задержку. Пусть копит ID, пока юзер скроллит
            delay(2000)
            energyPrefs.addReadIds(newlyVisible)
        }
    }

    fun selectNotification(notification: NotificationItemModel?) {

        _state.update { it.copy(
            selectedNotification = notification,
            currentProgressMs = 0,
            totalDurationMs = 0
        ) }

        if (notification == null) {
            voiceManager.stopPlayback()
            _state.update { it.copy(isPlaying = false) }
        }
    }

    fun togglePlayback(url: String) {
        viewModelScope.launch {
            // 1. Определяем финальный источник (Кэш или URL)
            val cacheMap = energyPrefs.voiceCacheMap.first()
            val localPath = cacheMap[url]

            val finalSource = if (localPath != null && File(localPath).exists()) {
                Log.d("VOICE", "Используем локальный кэш: $localPath")
                localPath
            } else {
                Log.d("VOICE", "Кэша нет, стримим: $url")
                // Если кэша нет, запускаем загрузку в фоне на будущее
                downloadToCache(url)
                url
            }

            // 2. Просто просим менеджер "обработать" этот источник
            voiceManager.togglePlayback(
                source = finalSource,
                onStateChanged = { playing ->
                    _state.update { it.copy(isPlaying = playing) }
                },
                onProgress = { current, total ->
                    // ОБНОВЛЯЕМ ПРОГРЕСС В СТЕЙТЕ
                    _state.update { it.copy(
                        currentProgressMs = current,
                        totalDurationMs = total
                    ) }
                },
                onCompletion = {
                    _state.update { it.copy(
                        isPlaying = false,
                        currentProgressMs = 0
                    ) }
                }
            )
        }
    }

    fun seekTo(positionMs: Float) {
        voiceManager.seekTo(positionMs.toInt())
        _state.update { it.copy(currentProgressMs = positionMs.toInt()) }
    }

    private fun downloadToCache(url: String) {
        if (!activeDownloads.add(url)) return
        viewModelScope.launch(Dispatchers.IO) {
            // Используем константный префикс для легкой очистки кэша в будущем
            val cacheDir = getApplication<Application>().cacheDir
            val finalFile = File(cacheDir, "voice_${url.hashCode()}.m4a")
            val tempFile = File(cacheDir, "temp_${url.hashCode()}.tmp")

            // 1. Если файл уже есть, ничего не делаем
            if (finalFile.exists() && finalFile.length() > 0) return@launch

            try {
                Log.d("API1", "Начинаем загрузку: $url")

                // 2. Качаем во временный файл
                URL(url).openStream().use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                // 3. Атомарная операция: если докачали полностью, переименовываем
                if (tempFile.exists() && tempFile.length() > 0) {
                    if (tempFile.renameTo(finalFile)) {
                        // 4. Только после успеха обновляем маппинг в DataStore
                        energyPrefs.saveFileToCacheMap(url, finalFile.absolutePath)
                        Log.d("API1", "Кэш готов: ${finalFile.name}")
                    }
                }
            } catch (e: Exception) {
                Log.e("API1", "Ошибка загрузки: ${url.hashCode()}", e)
                // Чистим временный файл, если что-то пошло не так
                if (tempFile.exists()) tempFile.delete()
            } finally {
                activeDownloads.remove(url)
                Log.d("API1", "Загрузка завершена или прервана для: $url")
            }
        }
    }


    fun requestTranscription(notificationId: String, audioUrl: String) {
        viewModelScope.launch(Dispatchers.IO) {
            // 1. Включаем анимацию загрузки (Shimmer) для конкретного сообщения
            updateNotificationState(notificationId, isTranscribing = true)

            try {
                // 2. Получаем файл (скачиваем или берем из кэша)
                val file = downloadOrGetVoiceFile(audioUrl)

                if (file == null || !file.exists()) {
                    Log.e("NotificationsVM", "Не удалось получить аудиофайл")
                    updateNotificationState(notificationId, isTranscribing = false)
                    return@launch
                }

                // 3. Отправляем в Groq API
                Log.d("NotificationsVM", "Начинаем расшифровку файла: ${file.name}")
                transcriptionService.transcribe(file)
                    .onSuccess { text ->
                        // 4. Успех! Обновляем UI (текст появился, загрузка выключена)
                        updateNotificationText(notificationId, text)

                        // 5. Сохраняем в базу данных!
                        // Чтобы при следующем входе текст уже был и мы не тратили API лимиты
                        energyPrefs.saveTranscriptionToCache(audioUrl, text)
                        Log.d("NotificationsVM", "Текст сохранен в локальный кэш")
                    }
                    .onFailure { error ->
                        Log.e("NotificationsVM", "Ошибка Groq: ${error.message}")
                        updateNotificationState(notificationId, isTranscribing = false)
                        // Тут можно кинуть сайд-эффект для Toast'а "Ошибка сети"
                    }

            } catch (e: Exception) {
                Log.e("NotificationsVM", "Критическая ошибка: ${e.message}")
                updateNotificationState(notificationId, isTranscribing = false)
            }
        }
    }

    // --- ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ---

    // Метод для безопасного скачивания во временный файл (похож на то, что мы делали для кэша плеера)
    private fun downloadOrGetVoiceFile(url: String): File? {
        val cacheDir = application.cacheDir
        val finalFile = File(cacheDir, "transcribe_${url.hashCode()}.m4a")
        val tempFile = File(cacheDir, "transcribe_${url.hashCode()}.tmp")

        // Проверяем наличие уже готового файла
        if (finalFile.exists() && finalFile.length() > 100) return finalFile

        return try {
            URL(url).openStream().use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            // Атомарная операция: переименовываем только если загрузка прошла успешно
            if (tempFile.exists() && tempFile.length() > 100) {
                tempFile.renameTo(finalFile)
                finalFile
            } else {
                null
            }
        } catch (e: Exception) {
            if (tempFile.exists()) tempFile.delete()
            Log.e("NotificationsVM", "Ошибка загрузки файла", e)
            null
        }
    }

    // Обновляем флаг загрузки в списке И в открытом диалоге
    private fun updateNotificationState(id: String, isTranscribing: Boolean) {
        _state.update { currentState ->
            val updatedList = currentState.notifications.map {
                if (it.id == id) it.copy(isTranscribing = isTranscribing) else it
            }
            val updatedSelected = if (currentState.selectedNotification?.id == id) {
                currentState.selectedNotification.copy(isTranscribing = isTranscribing)
            } else currentState.selectedNotification

            currentState.copy(
                notifications = updatedList,
                selectedNotification = updatedSelected
            )
        }
    }

    // Обновляем текст в списке И в открытом диалоге
    private fun updateNotificationText(id: String, text: String) {
        _state.update { currentState ->
            val updatedList = currentState.notifications.map {
                if (it.id == id) it.copy(messageText = text, isTranscribing = false) else it
            }
            val updatedSelected = if (currentState.selectedNotification?.id == id) {
                currentState.selectedNotification.copy(messageText = text, isTranscribing = false)
            } else currentState.selectedNotification

            currentState.copy(
                notifications = updatedList,
                selectedNotification = updatedSelected
            )
        }
    }

    fun prepareAudio(url: String) {
        viewModelScope.launch {
            val cacheMap = energyPrefs.voiceCacheMap.first()
            val localPath = cacheMap[url]
            val finalSource = if (localPath != null && File(localPath).exists()) localPath else url

            voiceManager.prepareTrack(finalSource) { duration ->
                _state.update { it.copy(
                    totalDurationMs = duration,
                    currentProgressMs = 0
                ) }
            }
        }
    }
}