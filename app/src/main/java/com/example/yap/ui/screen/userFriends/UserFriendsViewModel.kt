package com.example.yap.ui.screen.userFriends

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.yap.data.model.UserItem
import com.example.yap.ui.main.YapApp
import com.example.yap.ui.screen.addUser.AddFriendStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class UserFriendsViewModel(
    application: Application,
    private val targetUserId: String // ID пользователя, чьих друзей мы смотрим
) : AndroidViewModel(application) {

    private val app = application as YapApp
    private val userRepository = app.userRepository
    private val friendRequestRepository = app.friendsRequestRepository
    private val _state = MutableStateFlow(UserFriendsUiState())
    val state = _state.asStateFlow()

    init {
        observeUserFriends()
    }

    companion object {
        fun provideFactory(application: Application, userId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return UserFriendsViewModel(application, userId) as T
                }
            }
    }

    private fun observeUserFriends() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }

            // 1. Получаем целевого пользователя разово
            val targetUser = userRepository.getUsersByIds(listOf(targetUserId)).firstOrNull()

            if (targetUser == null) {
                _state.update { it.copy(isLoading = false) }
                return@launch
            }

            _state.update { it.copy(targetUserName = targetUser.name) }

            // 2. Используем combine, чтобы следить и за моим профилем, и за сессионными заявками
            // Это гарантирует, что кнопка мгновенно станет неактивной (PENDING) после нажатия
            combine(
                userRepository.observeMyProfile(),
                friendRequestRepository.sessionSentRequests // Следим за списком ID, кому мы отправили запрос в этой сессии
            ) { myProfile, sentRequests ->
                if (myProfile == null) return@combine

                try {
                    // 3. Грузим детали друзей (из кэша)
                    val friendsDetails = userRepository.getUsersByIds(targetUser.friends)

                    // 4. Маппим с полноценной логикой статуса
                    val models = friendsDetails.map { friend ->
                        val isFriend = myProfile.friends.contains(friend.id)
                        val isSelf = friend.id == userRepository.currentUserId
                        val isSentByMeInSession = sentRequests.contains(friend.id)
                        val isInQuickList = myProfile.quickList.contains(friend.id)
                        val updatedUser = friend.copy(isYapActive = isInQuickList)

                        // Вычисляем статус аналогично поиску и профилю
                        val status = when {
                            isSelf -> AddFriendStatus.ALREADY_FRIEND
                            isFriend -> AddFriendStatus.ALREADY_FRIEND
                            isSentByMeInSession -> AddFriendStatus.PENDING
                            else -> AddFriendStatus.CAN_ADD

                        }

                        val mutualCount = friend.friends.intersect(myProfile.friends.toSet()).size

                        FoundUser(
                            user = updatedUser,
                            status = status,
                            mutualFriendsCount = mutualCount,
                            isInQuickList = isInQuickList
                        )
                    }

                    _state.update {
                        it.copy(isLoading = false, friends = models)
                    }
                } catch (e: Exception) {
                    Log.e("UserFriendsVM", "Error mapping: ${e.message}")
                    _state.update { it.copy(isLoading = false) }
                }
            }.collect()
        }
    }

    fun toggleQuickList(user: UserItem, isCurrentlyInList: Boolean) {
        viewModelScope.launch {
            userRepository.toggleQuickList(user.id, add = !isCurrentlyInList)
        }
    }

    fun sendFriendRequest(userId: String) {
        // Логика отправки заявки
    }

    fun sendYap(userId: String) {
        // Логика отправки япа
    }
}