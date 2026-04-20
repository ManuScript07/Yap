package com.example.yap.ui.screen.notification

import UserPreferences
import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
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
    private val energyPrefs = UserPreferences(application)
    private val _state = MutableStateFlow(NotificationsUiState())
    private val processedIds = mutableSetOf<String>()
    private val voiceManager = VoiceManager(application)
    private val muteJobs = mutableMapOf<String, Job>()
    val state: StateFlow<NotificationsUiState> = _state.asStateFlow()

    init {
//        loadNotifications()
        observeMessages()
    }
    private fun observeMessages() {
        val currentUserId = userRepository.currentUserId ?: return

        viewModelScope.launch {
            combine(
                chatRepository.observeUserMessages(currentUserId),
                userRepository.observeMyProfile(),
                energyPrefs.usersData
            ) { messages, myProfileSnapshot, _ ->

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

                    NotificationItemModel(
                        id = entity.id,
                        user = senderProfile.copy(
                            isYapActive = isInCloudList,
                            isMuted = isMuted
                        ),
                        messageText = entity.text,
                        hasLocation = entity.latitude != null,
                        latitude = entity.latitude,
                        longitude = entity.longitude,
                        timestamp = formatTime(entity.timestamp),
                        timeAgo = getTimeAgo(entity.timestamp),
                        isUserInQuickList = isInCloudList,
                        isMuted = isMuted,
                        audioUrl = entity.audioUrl,
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
        _state.update { it.copy(selectedNotification = notification) }
        // Если закрываем диалог — стопаем звук
        if (notification == null) {
            voiceManager.stopPlayback()
            _state.update { it.copy(isPlaying = false) }
        }
    }

    fun togglePlayback(url: String) {
        viewModelScope.launch {
            // Если уже играет — ставим на паузу (твоя стандартная логика)
            if (voiceManager.isActuallyPlaying()) {
                voiceManager.pausePlaybackOnly()
                _state.update { it.copy(isPlaying = false) }
                return@launch
            }

            // Проверяем кэш в DataStore
            val cacheMap = energyPrefs.voiceCacheMap.first()
            val localPath = cacheMap[url]

            if (localPath != null && File(localPath).exists()) {
                Log.d("API1", "Играем из кэша (локально): $localPath")
                voiceManager.playUrl(localPath,
                    onStateChanged = { playing -> _state.update { it.copy(isPlaying = playing) } },
                    onCompletion = { _state.update { it.copy(isPlaying = false) } }
                )
            } else {
                Log.d("API1", "Кэша нет, стримим из Supabase...")
                voiceManager.playUrl(url,
                    onStateChanged = { playing -> _state.update { it.copy(isPlaying = playing) } },
                    onCompletion = { _state.update { it.copy(isPlaying = false) } }
                )

                // Фоновое кэширование, чтобы в следующий раз не дергать сервер
                downloadToCache(url)
            }
        }
    }

    private fun downloadToCache(url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Используем hashCode для уникального имени файла
                val file = File(getApplication<Application>().cacheDir, "voice_${url.hashCode()}.m4a")
                if (!file.exists()) {
                    URL(url).openStream().use { input ->
                        file.outputStream().use { output -> input.copyTo(output) }
                    }
                    // Сохраняем маппинг URL -> Path в DataStore
                    energyPrefs.saveFileToCacheMap(url, file.absolutePath)
                    Log.d("API1", "Файл успешно закеширован: ${file.absolutePath}")
                }
            } catch (e: Exception) {
                Log.e("API1", "Ошибка загрузки в кэш: ${e.message}")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceManager.stopPlayback()
    }


}