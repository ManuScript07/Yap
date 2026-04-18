package com.example.yap.ui.screen.notification

import UserPreferences
import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.yap.ChatRepository
import com.example.yap.R
import com.example.yap.data.model.UserItem
import com.example.yap.util.formatTime
import com.example.yap.util.getTimeAgo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NotificationsViewModel @JvmOverloads constructor(
    application: Application,
    private val chatRepository: ChatRepository = ChatRepository()
) : AndroidViewModel(application) {


    private val CURRENT_USER_ID = 1
    private val cachedUsers = listOf(
        UserItem(1, "User1", false, R.drawable.avatar_1),
        UserItem(2, "User2", true, R.drawable.avatar_2),
        UserItem(3, "User3", false, R.drawable.avatar_3),
        UserItem(4, "User4", true, R.drawable.avatar_4)
    )
    private val energyPrefs = UserPreferences(application)
    private val _state = MutableStateFlow(NotificationsUiState())
    val state: StateFlow<NotificationsUiState> = _state.asStateFlow()

    init {
//        loadNotifications()
        observeMessages()
        observeUsersAndNotifications()
    }
    private fun observeMessages() {
        viewModelScope.launch {
            // 1. Устанавливаем загрузку
            _state.update { it.copy(isLoading = true) }

            chatRepository.observeUserMessages(CURRENT_USER_ID)
                // 2. Обработка ошибок (теперь при ошибке индекса приложение просто выведет лог, а не упадет)
                .catch { exception ->
                    Log.e("NotificationsVM", "Ошибка при получении сообщений", exception)
                    _state.update { it.copy(isLoading = false) }
                    // Здесь можно добавить в стейт поле error: String? и показать Snackbar
                }
                .collect { messagesEntities ->
                    // 3. Выносим тяжелый маппинг в фоновый поток (Default),
                    // чтобы UI не подлагивал, если сообщений станет очень много
                    val notificationsList = withContext(Dispatchers.Default) {
                        messagesEntities.map { entity ->
                            val sender = cachedUsers.find { it.id == entity.senderId }
                                ?: UserItem(entity.senderId, "Unknown", false, R.drawable.avatar_1)

                            NotificationItemModel(
                                id = entity.id,
                                user = sender,
                                messageText = entity.text,
                                hasLocation = entity.latitude != null && entity.longitude != null,
                                timestamp = formatTime(entity.timestamp),
                                timeAgo = getTimeAgo(entity.timestamp)
                            )
                        }
                    }

                    // 4. Обновляем состояние
                    _state.update {
                        it.copy(
                            notifications = notificationsList,
                            isLoading = false
                        )
                    }
                }
        }
    }



    fun deleteNotification(id: String) {
        _state.update { currentState ->
            currentState.copy(
                notifications = currentState.notifications.filter { it.id != id }
            )
        }
    }

    fun muteNotification(notificationId: String) {
        _state.update { currentState ->
            // 1. Находим уведомление, по которому кликнули, чтобы узнать ID пользователя
            val targetNotification = currentState.notifications.find { it.id == notificationId }
            val targetUserId = targetNotification?.user?.id

            if (targetUserId != null) {
                // Новое состояние (инвертируем текущее)
                val newMuteState = !targetNotification.user.isMuted

                // 2. Обновляем ВСЕ уведомления этого пользователя в списке
                currentState.copy(
                    notifications = currentState.notifications.map { notif ->
                        if (notif.user.id == targetUserId) {
                            notif.copy(user = notif.user.copy(isMuted = newMuteState))
                        } else {
                            notif
                        }
                    }
                )
            } else {
                currentState
            }
        }
    }




    private fun observeUsersAndNotifications() {
        viewModelScope.launch {
            energyPrefs.usersData.collect { persistedUsers ->
                val usersList = persistedUsers ?: emptyList()

                _state.update { currentState ->
                    val updatedNotifications = currentState.notifications.map { notif ->
                        val userInQuickList = usersList.find { it.id == notif.user.id }

                        notif.copy(
                            user = notif.user.copy(
                                isYapActive = userInQuickList != null
                            )
                        )
                    }
                    currentState.copy(notifications = updatedNotifications)
                }
            }
        }
    }


    fun toggleUserQuickList(userFromNotification: UserItem) {
        viewModelScope.launch {
            val currentUsers = energyPrefs.usersData.first() ?: emptyList()
            val isAlreadyInList = currentUsers.any { it.id == userFromNotification.id }

            val updatedUsers = if (isAlreadyInList) {
                currentUsers.filter { it.id != userFromNotification.id }
            } else {
                currentUsers + userFromNotification.copy(isYapActive = true)
            }

            energyPrefs.saveUsers(updatedUsers)
        }
    }
}