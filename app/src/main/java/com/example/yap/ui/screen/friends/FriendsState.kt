package com.example.yap.ui.screen.friends

import androidx.compose.runtime.Immutable
import com.example.yap.data.model.UserItem

@Immutable
data class FriendItemModel(
    val user: UserItem,
    val isMuted: Boolean,
    val isInQuickList: Boolean
)

data class FriendsUiState(
    val myUserCode: String = "",
    val friends: List<FriendItemModel> = emptyList(), // Основной список
    val filteredFriends: List<FriendItemModel> = emptyList(), // Список после поиска
    val remoteSearchResult: UserItem? = null, // Найденный по коду (еще не друг)
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val isSearchingRemote: Boolean = false
)