package com.example.yap.ui.screen.friends

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.yap.data.model.UserItem
import com.example.yap.ui.main.YapApp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

class FriendsViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val app = application as YapApp
    private val userRepository = app.userRepository

    private val _state = MutableStateFlow(FriendsUiState())
    val state = _state.asStateFlow()


    private var muteJobs = mutableMapOf<String, Job>()

    init {
        observeFriends()
    }

    private fun observeFriends() {
        viewModelScope.launch {
            Log.d("FRIENDS_DEBUG", "--- Запуск observeFriends ---")
            _state.update { it.copy(isLoading = true) }

            // Подписываемся на наш профиль
            userRepository.observeMyProfile()
//                .distinctUntilChangedBy { it?.friends } // Сработает только если изменился состав друзей
                .collect { myProfile ->
                    if (myProfile == null) {
                        Log.w("FRIENDS_DEBUG", "Профиль пуст (null)")
                        _state.update { it.copy(isLoading = false) }
                        return@collect
                    }

                    try {
                        // 1. Берем список ID именно друзей (не быстрый список)
                        val friendIds = myProfile.friends
                        Log.d("FRIENDS_DEBUG", "Получен профиль. Друзей в списке: ${friendIds.size}")

                        // 2. Загружаем детали профилей через твой эффективный getUsersByIds
                        // (который, как мы помним, использует кэш L1/L2 и сеть)
                        val startTime = System.currentTimeMillis()
                        val users = userRepository.getUsersByIds(friendIds)
                        val duration = System.currentTimeMillis() - startTime
                        Log.d("FRIENDS_DEBUG", "Загружены детали профилей за ${duration}ms. Кол-во: ${users.size}")
                        // 3. Маппим в UI-модели с актуальными статусами мута и квик-листа
                        val friendModels = users.map { user ->
                            val isInQuickList = myProfile.quickList.contains(user.id)
                            val isMuted = myProfile.mutedUsers.contains(user.id)
                            FriendItemModel(
                                user = user.copy(
                                    isYapActive = isInQuickList,
                                    isMuted = isMuted
                                ),
                                isMuted = isMuted,
                                isInQuickList = isInQuickList
                            )
                        }

                        // 4. Обновляем стейт
                        _state.update { currentState ->
                            Log.d("FRIENDS_DEBUG", "Обновление UI стейта: ${friendModels.size} друзей")
                            currentState.copy(
                                isLoading = false,
                                myUserCode = myProfile.userCode,
                                friends = friendModels,
                                filteredFriends = filterLocal(currentState.searchQuery, friendModels)
                            )
                        }
                    } catch (e: Exception) {
                        Log.e("FriendsVM", "Ошибка при загрузке друзей: ${e.message}")
                        Log.e("FRIENDS_DEBUG", "Критическая ошибка в observeFriends", e)
                        _state.update { it.copy(isLoading = false) }
                    }
                }
        }
    }

    fun onSearchChanged(query: String) {
        _state.update { currentState ->
            val filtered = filterLocal(query, currentState.friends)
            currentState.copy(
                searchQuery = query,
                filteredFriends = filtered,
                // Сбрасываем удаленные из этого экрана стейты на всякий случай
                remoteSearchResult = null,
                isSearchingRemote = false
            )
        }
    }

    private fun filterLocal(query: String, list: List<FriendItemModel>): List<FriendItemModel> {
        if (query.isBlank()) return list

        // Предварительно готовим query для сравнения (trim и lowercase один раз)
        val trimmedQuery = query.trim()

        return list.filter { item ->
            item.user.name.contains(trimmedQuery, ignoreCase = true) ||
                    item.user.username.contains(trimmedQuery, ignoreCase = true)
        }
    }


    fun toggleMute(friendId: String) {
        val currentItem = _state.value.friends.find { it.user.id == friendId } ?: return
        val wasMuted = currentItem.isMuted

        // Мгновенное обновление (Optimistic)
        updateFriendStatus(friendId) { it.copy(isMuted = !wasMuted) }

        muteJobs[friendId]?.cancel()
        val currentJob = viewModelScope.launch {
            try {
                delay(500)
                userRepository.toggleMute(friendId, !wasMuted)
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    // Откат при ошибке
                    updateFriendStatus(friendId) { it.copy(isMuted = wasMuted) }
                }
            } finally {
                // Атомарная проверка: удаляем только если в мапе лежит именно ЭТОТ Job
                // (защита от случая, если пользователь нажал кнопку 10 раз подряд)
                if (muteJobs[friendId] == coroutineContext[Job]) {
                    muteJobs.remove(friendId)
                }
            }
        }
        muteJobs[friendId] = currentJob
    }

    private fun updateFriendStatus(userId: String, transform: (FriendItemModel) -> FriendItemModel) {
        _state.update { s ->
            s.copy(
                friends = s.friends.map { if (it.user.id == userId) transform(it) else it },
                filteredFriends = s.filteredFriends.map { if (it.user.id == userId) transform(it) else it }
            )
        }
    }



    fun toggleQuickList(user: UserItem, isCurrentlyInList: Boolean) {
        viewModelScope.launch {
            userRepository.toggleQuickList(user.id, add = !isCurrentlyInList)
        }
    }

    fun removeFriend(friendId: String) {
        // Сохраняем текущее состояние для возможного отката
        val currentFriends = _state.value.friends

        // 1. Optimistic Update: Мгновенно удаляем из локального стейта
        _state.update { currentState ->
            val updatedFriends = currentState.friends.filter { it.user.id != friendId }
            currentState.copy(
                friends = updatedFriends,
                // Сразу применяем фильтрацию (если пользователь был в режиме поиска)
                filteredFriends = filterLocal(currentState.searchQuery, updatedFriends)
            )
        }

        // 2. Запрос на сервер
        viewModelScope.launch {
            try {
                userRepository.removeFriend(friendId)
            } catch (_: Exception) {
                // ROLLBACK: Если произошла ошибка, возвращаем список как было
                _state.update { currentState ->
                    currentState.copy(
                        friends = currentFriends,
                        filteredFriends = filterLocal(currentState.searchQuery, currentFriends)
                    )
                }
                // Здесь можно бросить Event (SharedFlow) для показа Toast "Ошибка сети"
            }
        }
    }

}