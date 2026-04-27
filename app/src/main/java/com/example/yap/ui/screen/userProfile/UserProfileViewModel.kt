package com.example.yap.ui.screen.userProfile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.yap.R
import com.example.yap.data.model.UserItem
import com.example.yap.data.repository.UserRepository
import com.example.yap.ui.main.YapApp
import com.example.yap.ui.screen.addUser.AddFriendStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

class UserProfileViewModel(
    application: Application,
    private val targetUserId: String
) : AndroidViewModel(application) {


    private val app = application as YapApp
    private val userRepository = app.userRepository
    private val friendRequestRepository = app.friendsRequestRepository

    private var muteJob: Job? = null


    private val _state = MutableStateFlow(UserProfileUiState())
    val state: StateFlow<UserProfileUiState> = _state.asStateFlow()

    private var myUser: UserItem? = null

    init {
        loadUserProfile()
    }

    companion object {
        fun provideFactory(
            application: Application,
            userId: String
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return UserProfileViewModel(application, userId) as T
            }
        }
    }


    private fun loadUserProfile() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            try {
                // 1. Загружаем данные целевого пользователя
                val users = userRepository.getUsersByIds(listOf(targetUserId))
                val targetUser = users.firstOrNull()

                if (targetUser == null) {
                    _state.update { it.copy(isLoading = false, error = "Пользователь не найден") }
                    return@launch
                }

                // 2. Слушаем наш профиль
                userRepository.observeMyProfile().collectLatest { myProfile ->
                    if (myProfile != null) {
                        myUser = myProfile
                        val inQuickList = myProfile.quickList.contains(targetUserId)
                        val isUserMuted = myProfile.mutedUsers.contains(targetUserId)
                        val inFriends = myProfile.friends.contains(targetUserId)

                        // --- ИНТЕГРАЦИЯ ЛОГИКИ СТАТУСА ---
                        // Используем те же проверки, что и в поиске:
                        val isAlreadySentByMe = friendRequestRepository.sessionSentRequests.value.contains(targetUserId)

                        val status = when {
                            inFriends -> AddFriendStatus.ALREADY_FRIEND
                            isAlreadySentByMe -> AddFriendStatus.PENDING
                            else -> {
                                // Проверка через твой существующий метод в репозитории
                                val isPending = friendRequestRepository.checkIsRequestPending(targetUserId)
                                if (isPending) AddFriendStatus.PENDING else AddFriendStatus.CAN_ADD
                            }
                        }
                        // ---------------------------------

                        _state.update {
                            it.copy(
                                isLoading = false,
                                user = targetUser,
                                isMuted = isUserMuted,
                                isUserInQuickList = inQuickList,
                                isFriend = inFriends,
                                addFriendStatus = status // Присваиваем вычисленный статус
                            )
                        }
                    }
                }

            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun toggleQuickList() {
        val currentState = _state.value
        if (currentState.user == null) return

        viewModelScope.launch {
            try {
                val isAdding = !currentState.isUserInQuickList
                userRepository.toggleQuickList(targetUserId, isAdding)
            } catch (_: Exception) {
            }
        }
    }


    fun toggleAvatarViewer(isOpen: Boolean) {
        _state.update { it.copy(isAvatarViewerOpen = isOpen) }
    }

    fun sendFriendRequest(onResult: (Int, Boolean) -> Unit) {
        // 1. Проверки на возможность отправки
        if (state.value.addFriendStatus != AddFriendStatus.CAN_ADD || state.value.isFriend) return

        val sender = myUser ?: return

        viewModelScope.launch {
            // 2. Установка PENDING (блокирует кнопку визуально)
            _state.update { it.copy(addFriendStatus = AddFriendStatus.PENDING) }

            val result = friendRequestRepository.sendRequest(
                receiverId = targetUserId,
                senderName = sender.name,
                senderAvatar = sender.avatarUrl
            )

            result.onSuccess {
                // 3. Успех: меняем статус на ALREADY_FRIEND (или создай SENT, если нужно)
                _state.update { it.copy(addFriendStatus = AddFriendStatus.PENDING) }
                onResult(R.string.request_sent_success, true)
            }.onFailure { exception ->
                // 4. Ошибка: возвращаем CAN_ADD, чтобы можно было попробовать снова
                _state.update { it.copy(addFriendStatus = AddFriendStatus.CAN_ADD) }

                val errorRes = when {
                    exception.message?.contains("permission") == true -> R.string.error_already_sent
                    // Здесь можно добавить проверку на "невозможно отправить"
                    exception.message?.contains("not_found") == true -> R.string.error_generic
                    else -> R.string.no_internet
                }
                onResult(errorRes, false)
            }
        }
    }

    fun toggleMute() {
        val currentState = _state.value

        val wasMuted = currentState.isMuted
        _state.update { it.copy(isMuted = !wasMuted) }

        muteJob?.cancel()
        muteJob = viewModelScope.launch {
            try {
                delay(500)
                userRepository.toggleMute(targetUserId, !wasMuted)
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    // Rollback при ошибке
                    _state.update { it.copy(isMuted = wasMuted) }
                }
            }
        }
    }

    fun removeFriend(onResult: (Boolean) -> Unit) {
        // Optimistic Update: мгновенно делаем статус "CAN_ADD" и убираем из друзей
        _state.update {
            it.copy(
                isFriend = false,
                addFriendStatus = AddFriendStatus.CAN_ADD
            )
        }

        viewModelScope.launch {
            try {
                userRepository.removeFriend(targetUserId)
                onResult(true)
            } catch (e: Exception) {
                // Rollback при ошибке
                _state.update {
                    it.copy(
                        isFriend = true,
                        addFriendStatus = AddFriendStatus.ALREADY_FRIEND
                    )
                }
                onResult(false)
            }
        }
    }


}