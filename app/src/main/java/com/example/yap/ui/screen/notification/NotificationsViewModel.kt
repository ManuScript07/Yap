package com.example.yap.ui.screen.notification

import UserPreferences
import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.yap.R
import com.example.yap.data.model.UserItem
import com.example.yap.ui.main.YapApp
import com.example.yap.util.formatTime
import com.example.yap.util.getTimeAgo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NotificationsViewModel(
    application: Application,
) : AndroidViewModel(application) {


    private val app = application as YapApp
    private val userRepository = app.userRepository
    private val chatRepository = app.chatRepository
    private val energyPrefs = UserPreferences(application)
    private val _state = MutableStateFlow(NotificationsUiState())
    private var lastProcessedIds = emptySet<String>()
    private val processedIds = mutableSetOf<String>()
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
            ) { messages, myProfileSnapshot, persistedUsers ->

                // 1. Собираем все уникальные ID отправителей из списка сообщений
                val senderIds = messages.map { it.senderId }.distinct()

                // 2. Подгружаем их профили одним пакетом (UserRepository теперь кэширует их)
                val loadedProfiles = userRepository.getUsersByIds(senderIds)

                val cloudQuickListIds = myProfileSnapshot?.get("quickList") as? List<String> ?: emptyList()
                val mutedIds = myProfileSnapshot?.get("mutedUsers") as? List<String> ?: emptyList()
                val localQuickList = persistedUsers ?: emptyList()

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
                        isMuted = isMuted
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
        viewModelScope.launch {
            // Находим уведомление в текущем стейте, чтобы понять, какой юзер отправил его
            val targetNotification = _state.value.notifications.find { it.id == notificationId }
            val targetUserId = targetNotification?.user?.id ?: return@launch

            // Берем текущий статус мута из модели уведомления
            val currentlyMuted = targetNotification.isMuted

            try {
                // Отправляем в Firebase.
                // Наш observeMessages подхватит изменение профиля и обновит список автоматически!
                userRepository.toggleMute(targetUserId, !currentlyMuted)
            } catch (e: Exception) {
                Log.e("NotificationsVM", "Ошибка при муте: ${e.message}")
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


}