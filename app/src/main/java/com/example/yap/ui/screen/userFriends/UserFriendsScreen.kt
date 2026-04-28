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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.yap.R
import com.example.yap.data.model.UserItem
import com.example.yap.ui.components.BaseTopAppBar
import com.example.yap.ui.screen.home.HomeViewModel
import com.example.yap.ui.screen.home.YapType
import com.example.yap.ui.theme.LocalAdditionColors
import com.example.yap.ui.theme.LocalBaseScale
import com.example.yap.util.compose.rememberLambda
import com.example.yap.util.extension.SystemStatusPill
import com.example.yap.util.fetchLocationAndSendDirectYap

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserFriendsScreen(
    viewModel: UserFriendsViewModel,
    onBack: () -> Unit,
    onNavigateToProfile: (String) -> Unit,
    homeViewModel: HomeViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val homeState by homeViewModel.state.collectAsStateWithLifecycle()

    val baseScale = LocalBaseScale.current
    val context = LocalContext.current

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    val isLocationEnabled by remember { derivedStateOf { homeState.isLocationEnabled } }

    val guardedNavigateToProfile = rememberLambda<String> { userId ->
        onNavigateToProfile(userId)
    }


    val onToggleQuickList = remember(viewModel) {
        { foundUser: FoundUser ->
            viewModel.toggleQuickList(foundUser.user, foundUser.isInQuickList)
        }
    }

    val onAddFriend = remember(viewModel, homeViewModel) {
        { foundUser: FoundUser ->
            viewModel.sendFriendRequest(foundUser.user) { resId, isSuccess ->
                homeViewModel.showStatus(
                    resId = resId,
                    isSuccess = isSuccess
                )
            }
        }
    }
    val onYapSend = remember(homeViewModel, context, isLocationEnabled) {
        { user: UserItem ->
            fetchLocationAndSendDirectYap(
                context = context,
                isLocationEnabled = isLocationEnabled,
                onLocationReady = { lat, lon ->
                    homeViewModel.handleDirectSend(
                        userId = user.id,
                        latitude = lat,
                        longitude = lon,
                        type = YapType.YAP
                    )
                }
            )
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                BaseTopAppBar(
                    title = state.targetUserName.ifEmpty { stringResource(R.string.friends) },
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
                    if (state.friends.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillParentMaxSize()
                                    .padding(bottom = 64.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.no_common_friends),
                                    fontSize = 20.sp * baseScale,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 32.dp)
                                )
                            }
                        }
                    } else {
                        items(
                            items = state.friends,
                            key = { it.user.id }
                        ) { foundUser ->
                            UserFriendListItem(
                                foundUser = foundUser,
                                baseScale = baseScale,
                                onUserClick = guardedNavigateToProfile,
                                onAddClick = { onAddFriend(foundUser) },
                                onYapClick = { onToggleQuickList(foundUser) },
                                onLongYapClick = { onYapSend(foundUser.user) },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }
        SystemStatusPill(
            statusResource = homeState.systemStatusResource,
            statusMessage = homeState.systemStatusMessage,
            statusId = homeState.statusId,
            isSuccess = homeState.isStatusSuccess
        )
    }
}