package com.example.yap.ui.screen.userFriends

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.yap.R
import com.example.yap.data.model.UserItem
import com.example.yap.ui.main.YapApp
import com.example.yap.ui.screen.addUser.AddFriendStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
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

    private val initialPendingIds = MutableStateFlow<Set<String>>(emptySet())

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

            try {
                // 1. Получаем целевого пользователя
                val targetUser = userRepository.getUsersByIds(listOf(targetUserId)).firstOrNull()

                if (targetUser == null) {
                    _state.update { it.copy(isLoading = false) }
                    return@launch
                }
                _state.update { it.copy(targetUserName = targetUser.name) }

                // 2. Фоновая загрузка старых заявок (защищенная)
                launch {
                    try {
                        val pendingFromDb = friendRequestRepository.getAllSentPendingRequests()
                        initialPendingIds.value = pendingFromDb
                    } catch (e: Exception) {
                        Log.e("UserFriendsVM", "Error fetching DB requests: ${e.message}")
                    }
                }

                // 3. БЕЗОПАСНЫЙ сбор данных
                // Проверяем на null каждый поток перед передачей в combine
                val myProfileFlow = userRepository.observeMyProfile() ?: flowOf(null)
                val sessionRequestsFlow = friendRequestRepository.sessionSentRequests // это StateFlow, он не null

                combine(
                    myProfileFlow,
                    sessionRequestsFlow,
                    initialPendingIds
                ) { myProfile, sessionSent, dbSent ->
                    if (myProfile == null) return@combine emptyList<FoundUser>()

                    val currentUserId = userRepository.currentUserId
                    val friendsDetails = userRepository.getUsersByIds(targetUser.friends)

                    friendsDetails
                        .filter { it.id != currentUserId }
                        .map { friend ->
                        val isFriend = myProfile.friends.contains(friend.id)
                        val isSelf = friend.id == userRepository.currentUserId
                        val isPending = sessionSent.contains(friend.id) || dbSent.contains(friend.id)
                        val isInQuickList = myProfile.quickList.contains(friend.id)

                        val status = when {
                            isSelf || isFriend -> AddFriendStatus.ALREADY_FRIEND
                            isPending -> AddFriendStatus.PENDING
                            else -> AddFriendStatus.CAN_ADD
                        }

                        FoundUser(
                            user = friend.copy(isYapActive = isInQuickList),
                            status = status,
                            mutualFriendsCount = friend.friends.intersect(myProfile.friends.toSet()).size,
                            isInQuickList = isInQuickList
                        )
                    }
                }.collect { updatedFriends ->
                    _state.update {
                        it.copy(isLoading = false, friends = updatedFriends)
                    }
                }

            } catch (e: Exception) {
                Log.e("UserFriendsVM", "Critical error in observeUserFriends: ${e.message}")
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    fun toggleQuickList(user: UserItem, isCurrentlyInList: Boolean) {
        viewModelScope.launch {
            userRepository.toggleQuickList(user.id, add = !isCurrentlyInList)
        }
    }

    fun sendFriendRequest(friend: UserItem, onResult: (Int, Boolean) -> Unit) {
        // 1. Проверяем сессионный кэш (чтобы не спамить запросами)
        if (friendRequestRepository.sessionSentRequests.value.contains(friend.id)) return

        viewModelScope.launch {
            // Получаем текущий профиль из потока
            val sender = userRepository.observeMyProfile().firstOrNull()

            // ФИКС: Безопасная проверка отправителя
            if (sender == null) {
                onResult(R.string.error_generic, false)
                return@launch
            }

            // Теперь sender гарантированно не null (smart cast к UserItem)
            val result = friendRequestRepository.sendRequest(
                receiverId = friend.id,
                senderName = sender.name,
                senderAvatar = sender.avatarUrl
            )

            result.onSuccess {
                onResult(R.string.request_sent_success, true)
            }.onFailure { exception ->
                val errorRes = when {
                    exception.message?.contains("already_sent") == true -> R.string.error_already_sent
                    else -> R.string.no_internet
                }
                onResult(errorRes, false)
            }
        }
    }


}