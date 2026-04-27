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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class UserProfileViewModel(
    application: Application,
    private val targetUserId: String
) : AndroidViewModel(application) {


    private val app = application as YapApp
    private val userRepository = app.userRepository
    private val friendRequestRepository = app.friendsRequestRepository


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

                // 2. Слушаем наш профиль, чтобы понимать статус (в быстром списке ли он, в друзьях ли)
                userRepository.observeMyProfile().collectLatest { myProfile ->
                    if (myProfile != null) {
                        myUser = myProfile
                        val inQuickList = myProfile.quickList.contains(targetUserId)
                        val inFriends = myProfile.friends.contains(targetUserId)

                        _state.update {
                            it.copy(
                                isLoading = false,
                                user = targetUser,
                                isUserInQuickList = inQuickList,
                                isFriend = inFriends
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
        val sender = myUser ?: return

        // Если уже друзья — ничего не делаем
        if (state.value.isFriend) return

        viewModelScope.launch {
            val result = friendRequestRepository.sendRequest(
                receiverId = targetUserId,
                senderName = sender.name,
                senderAvatar = sender.avatarUrl
            )

            result.onSuccess {
                onResult(R.string.request_sent_success, true)
            }.onFailure { exception ->
                val errorRes = if (exception.message?.contains("permission") == true) {
                    R.string.error_already_sent
                } else {
                    R.string.no_internet
                }
                onResult(errorRes, false)
            }
        }
    }

}