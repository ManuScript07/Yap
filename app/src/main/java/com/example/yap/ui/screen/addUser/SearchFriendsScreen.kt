package com.example.yap.ui.screen.addUser

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yap.R
import com.example.yap.data.model.UserItem
import com.example.yap.ui.components.BaseTopAppBar
import com.example.yap.ui.screen.friends.SearchField
import com.example.yap.ui.theme.LocalAdditionColors
import com.example.yap.ui.theme.LocalBaseScale
import com.example.yap.util.compose.SystemBarsIconsColor
import com.example.yap.util.compose.rememberLambda
import com.example.yap.util.extension.SystemStatusPill
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchFriendsScreen(
    viewModel: AddUserViewModel = viewModel(),
    onBack: () -> Unit,
    onNavigateToProfile: (String) -> Unit,
    // добавить homeview model чрезе навигацию, сдалть уведомления
) {
    SystemBarsIconsColor(true)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val baseScale = LocalBaseScale.current


    // --- ОПТИМИЗАЦИЯ ЛЯМБД (как в FriendsScreen) ---
    val guardedNavigateToProfile = rememberLambda<String> { userId ->
        onNavigateToProfile(userId)
    }

    val onAcceptRequest = remember(viewModel) {
        { id: String, senderId: String -> viewModel.acceptRequest(id, senderId) }
    }

    val onDeclineRequest = remember(viewModel) {
        { id: String -> viewModel.declineRequest(id)}
    }

    val onSendRequest = remember(viewModel) {
        { id: String -> viewModel.sendFriendRequest(id)}
    }

    val onExecuteSearch = remember(viewModel) {
        { viewModel.executeSearch()  }
    }
    // -----------------------------------------------

    val listState = rememberLazyListState()
    val isScrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
        }
    }

    val searchPaddingBottom by animateDpAsState(
        targetValue = if (isScrolled) 8.dp * baseScale else 16.dp * baseScale,
        label = "SearchPaddingAnimation"
    )

    var statusMessage by remember { mutableStateOf<String?>(null) }
    var statusResId by remember { mutableStateOf<Int?>(null) }
    var statusId by remember { mutableLongStateOf(0L) }
    var isStatusSuccess by remember { mutableStateOf(true) }


    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is AddUserEvent.ShowStatus -> {
                    statusMessage = event.message
                    statusResId = event.resId
                    isStatusSuccess = event.isSuccess
                    statusId = viewModel.currentStatusId

                    delay(3000)

                    statusMessage = null
                    statusResId = null
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
        ) {
            BaseTopAppBar(
                title = stringResource(R.string.add_friends),
                onBack = onBack
            )

            SearchField(
                query = state.searchQuery,
                onQueryChange = { viewModel.onQueryChange(it) },
                baseScale = baseScale,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp * baseScale)
                    .padding(bottom = searchPaddingBottom),
                placeholderText = stringResource(R.string.search_invite_code)
            )

            if (state.searchQuery.isEmpty()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 14.dp * baseScale,
                        end = 14.dp * baseScale,
                        bottom = 24.dp * baseScale
                    ),
                    verticalArrangement = Arrangement.spacedBy(4.dp * baseScale)
                ) {
                    item {
                        Text(
                            text = stringResource(R.string.friends_requests),
                            fontSize = 20.sp * baseScale,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(vertical = 8.dp * baseScale)
                        )
                    }
                    if (state.incomingRequests.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(top = 32.dp * baseScale),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.no_new_applications),
                                    fontSize = 20.sp * baseScale,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    } else {
                        items(
                            items = state.incomingRequests,
                            key = { it.id }
                        ) { request ->
                            val userForUi = remember(request) {
                                UserItem(
                                    id = request.senderId,
                                    name = request.senderName,
                                    avatarUrl = request.senderAvatarUrl,
                                )
                            }
                            AnimatedVisibility(
                                visible = true,
                                enter = fadeIn(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                FriendRequestItem(
                                    user = userForUi,
                                    baseScale = baseScale,
                                    onUserClick = { userId -> guardedNavigateToProfile(userId) },
                                    onAccept = { onAcceptRequest(request.id, request.senderId) },
                                    onDecline = { onDeclineRequest(request.id) },
                                )
                            }
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp * baseScale)
                ) {
                    AnimatedVisibility(
                        visible = state.isValidCode && !state.isSearchPerformed,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp * baseScale)
                                .clip(RoundedCornerShape(8.dp * baseScale))
                                .background(LocalAdditionColors.current.purpleLightBackColor)
                                .clickable { onExecuteSearch() }
                                .padding(
                                    horizontal = 12.dp * baseScale,
                                    vertical = 12.dp * baseScale
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.baseline_search_24),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(32.dp * baseScale)
                            )
                            Spacer(modifier = Modifier.width(12.dp * baseScale))

                            Text(
                                text = stringResource(
                                    R.string.search_code,
                                    state.formattedCodeForUI
                                ),
                                fontSize = 20.sp * baseScale,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }

                    if (state.isSearchPerformed) {
                        val result = state.remoteSearchResult

                        if (result != null) {
                            Text(
                                text = stringResource(R.string.search_result),
                                fontSize = 20.sp * baseScale,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(vertical = 8.dp * baseScale)
                            )

                            FoundUserItem(
                                foundUser = result,
                                baseScale = baseScale,
                                onUserClick = guardedNavigateToProfile,
                                onAddClick = { onSendRequest(result.user.id) }
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(top = 32.dp * baseScale),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.no_result),
                                    fontSize = 20.sp * baseScale,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }
        }
        SystemStatusPill(
            statusResource = statusResId,
            statusMessage = statusMessage,
            statusId = statusId,
            isSuccess = isStatusSuccess
        )
    }
}

