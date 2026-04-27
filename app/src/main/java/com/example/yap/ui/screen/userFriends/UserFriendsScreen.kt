package com.example.yap.ui.screen.userFriends

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.yap.data.model.UserItem
import com.example.yap.ui.components.BaseTopAppBar
import com.example.yap.ui.theme.LocalAdditionColors
import com.example.yap.ui.theme.LocalBaseScale
import com.example.yap.util.compose.rememberLambda

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserFriendsScreen(
    viewModel: UserFriendsViewModel,
    onBack: () -> Unit,
    onNavigateToProfile: (String) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val baseScale = LocalBaseScale.current
    val context = LocalContext.current

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    // --- ОПТИМИЗАЦИЯ ЛЯМБД ---
    val guardedNavigateToProfile = rememberLambda<String> { userId ->
        onNavigateToProfile(userId)
    }

    val onBackLambda = remember(onBack) { { onBack() } }

    val onUserClick = remember(onNavigateToProfile) {
        { userId: String -> onNavigateToProfile(userId) }
    }

    val onToggleQuickList = remember(viewModel) {
        { foundUser: FoundUser ->
            viewModel.toggleQuickList(foundUser.user, foundUser.isInQuickList)
        }
    }

// Методы ViewModel (зависят только от viewModel)
    val onAddFriend = remember(viewModel) {
        { userId: String -> viewModel.sendFriendRequest(userId) }
    }

// Логика отправки Yap (если она требует локацию, как в твоем примере)
    val onYapSend = remember(viewModel, context) {
        { userId: String ->
            // Если у тебя во ViewModel уже есть метод отправки:
            viewModel.sendYap(userId)

            // Либо, если нужна логика с локацией как в FriendsScreen:
            /*
            fetchLocationAndSendDirectYap(
                context = context,
                isLocationEnabled = isLocationEnabled,
                onLocationReady = { lat, lon ->
                    viewModel.handleSendYap(userId, lat, lon)
                }
            )
            */
        }
    }

    val onYapLongClick = remember(viewModel, context) {
        { user: UserItem ->
            // Либо прямой вызов, либо через fetchLocationAndSendDirectYap как в примере
            viewModel.sendYap(user.id)
        }
    }

    Scaffold(
        topBar = {
            BaseTopAppBar(
                title = state.targetUserName.ifEmpty { "Друзья" },
                onBack = onBack,
                scrollBehavior = scrollBehavior
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = LocalAdditionColors.current.toggleButtonColor)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                items(
                    items = state.friends,
                    key = { it.user.id }
                ) { foundUser ->
                    UserFriendListItem(
                        foundUser = foundUser,
                        baseScale = baseScale,
                        onUserClick = guardedNavigateToProfile,
                        onAddClick = { onAddFriend(foundUser.user.id) },
                        onYapClick = { onYapSend(foundUser.user.id) },
                        onToggleQuickList = onToggleQuickList,
                        onYapLongClick = { onYapLongClick(foundUser.user) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}