package com.example.yap.ui.screen.friends

import android.widget.Toast
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yap.R
import com.example.yap.data.model.UserItem
import com.example.yap.ui.components.BaseUserListItem
import com.example.yap.ui.components.NotificationBadge
import com.example.yap.ui.screen.home.HomeViewModel
import com.example.yap.ui.screen.home.YapType
import com.example.yap.ui.theme.LocalAdditionColors
import com.example.yap.ui.theme.LocalBaseScale
import com.example.yap.util.compose.SystemBarsIconsColor
import com.example.yap.util.compose.rememberLambda
import com.example.yap.util.extension.SystemStatusPill
import com.example.yap.util.extension.shareUserProfile
import com.example.yap.util.fetchLocationAndSendDirectYap

@Composable
fun FriendsScreen(
    viewModel: FriendsViewModel = viewModel(),
    homeViewModel: HomeViewModel,
    onNavigateToProfile: (String) -> Unit,
    onNavigateToAddFriend: () -> Unit,
) {
    SystemBarsIconsColor(true)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val homeState by homeViewModel.state.collectAsStateWithLifecycle()

    val baseScale = LocalBaseScale.current
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val isLocationEnabled by remember { derivedStateOf { homeState.isLocationEnabled } }

    val guardedNavigateToProfile = rememberLambda<String> { userId ->
        onNavigateToProfile(userId)
    }
    val guardedNavigateToAddFriends = rememberLambda<Unit> {
        onNavigateToAddFriend()
    }


    val onMuteFriend = remember(viewModel) { { id: String -> viewModel.toggleMute(id) } }


    val onYapClick = remember(viewModel) {
        { model: FriendItemModel ->
            viewModel.toggleQuickList(model.user, model.isInQuickList)
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

    var friendIdToDelete by remember { mutableStateOf<String?>(null) }

    RemoveFriendDialog(
        friendId = friendIdToDelete,
        onConfirm = { id -> viewModel.removeFriend(id) },
        onDismiss = { friendIdToDelete = null }
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .padding(horizontal = 14.dp * baseScale)
        ) {

            // --- ВЕРХНИЙ БЛОК (ПЛАШКИ) ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp * baseScale)
                    .padding(bottom = 12.dp * baseScale),
                horizontalArrangement = Arrangement.spacedBy(10.dp * baseScale)
            ) {
                CodeCard(
                    modifier = Modifier.weight(1f),
                    code = state.myUserCode,
                    baseScale = baseScale,
                    onCopy = {
                        clipboardManager.setText(AnnotatedString(state.myUserCode))
                        Toast.makeText(context, R.string.code_copied, Toast.LENGTH_SHORT).show()
                    },
                    onShare = {
                        val myId = homeState.currentUserId
                        if (myId != null) {
                            context.shareUserProfile(myId, state.myUserCode)
                        }
                    }
                )

                Box(modifier = Modifier.size(65.dp * baseScale)) {
                    Card(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { guardedNavigateToAddFriends(Unit) },
                        shape = RoundedCornerShape(12.dp * baseScale),
                        colors = CardDefaults.cardColors(containerColor = LocalAdditionColors.current.purpleButtonColor),
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.outline_person_add_32),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(32.dp * baseScale)
                            )
                        }
                    }
                    NotificationBadge(
                        count = state.incomingRequests,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 4.dp, y = (-4).dp),
                        baseScale = baseScale
                    )
                }
            }

            // --- ПОИСК ---
            SearchField(
                query = state.searchQuery,
                onQueryChange = { viewModel.onSearchChanged(it) },
                baseScale = baseScale,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = searchPaddingBottom)
            )

            // --- СПИСОК ---
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp * baseScale),
                verticalArrangement = Arrangement.spacedBy(4.dp * baseScale)
            ) {
                // Заголовок списка друзей
                item {
                    Text(
                        text = stringResource(R.string.friends),
                        fontSize = 20.sp * baseScale,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(vertical = 8.dp * baseScale)
                    )
                }


                if (state.isLoading && state.friends.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(16.dp),
                                color = LocalAdditionColors.current.toggleButtonColor
                            )
                        }
                    }
                }

                else if (!state.isLoading && state.filteredFriends.isEmpty()) {
                    item {
                        EmptyFriendsCard(
                            baseScale = baseScale,
                            onAddClick = { guardedNavigateToAddFriends(Unit) },
                            modifier = Modifier
                                .padding(top = 12.dp * baseScale)
                                .animateItem()
                        )
                    }
                }

                items(
                    items = state.filteredFriends,
                    key = { it.user.id }
                ) { itemModel ->
                    FriendListItem(
                        modifier = Modifier.animateItem(
                            fadeInSpec = tween(400),
                            placementSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessLow
                            ),
                            fadeOutSpec = tween(300)
                        ),
                        model = itemModel,
                        onYapClick = { onYapClick(itemModel) },
                        onYapSend = { onYapSend(itemModel.user) },
                        onUserClick = guardedNavigateToProfile,
                        onMuteClick = { onMuteFriend(itemModel.user.id) },
                        onRemoveClick = { friendIdToDelete = itemModel.user.id }
                    )
                }
            }
        }
        SystemStatusPill(
            statusResource = homeState.systemStatusResource,
            statusMessage = homeState.systemStatusMessage,
            statusId = homeState.statusId,
            isSuccess = homeState.isStatusSuccess,
            baseScale = baseScale
        )
    }
}

@Composable
fun CodeCard(
    modifier: Modifier = Modifier,
    code: String,
    baseScale: Float,
    onCopy: () -> Unit,
    onShare: () -> Unit
) {
    val formattedCode = if (code.length == 8) code.chunked(4).joinToString(" ") else code

    Card(
        modifier = modifier.height(65.dp * baseScale),
        shape = RoundedCornerShape(12.dp * baseScale),
        colors = CardDefaults.cardColors(containerColor = LocalAdditionColors.current.purpleBackColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp * baseScale, end = 8.dp * baseScale),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Левая часть с текстом
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp * baseScale),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = formattedCode.ifEmpty { "        " }.uppercase(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp * baseScale,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    style = LocalTextStyle.current.copy(
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                        lineHeightStyle = LineHeightStyle(
                            alignment = LineHeightStyle.Alignment.Center,
                            trim = LineHeightStyle.Trim.Both
                        )
                    )
                )

                 Spacer(modifier = Modifier.height(4.dp * baseScale))

                Text(
                    text = stringResource(R.string.your_invite_code),
                    fontSize = 13.sp * baseScale,
                    color = LocalAdditionColors.current.secondTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = LocalTextStyle.current.copy(
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                        lineHeightStyle = LineHeightStyle(
                            alignment = LineHeightStyle.Alignment.Center,
                            trim = LineHeightStyle.Trim.Both
                        )
                    )
                )
            }


            // Правая часть с кнопками
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                    IconButton(
                        onClick = onCopy,
                        modifier = Modifier.padding(4.dp * baseScale)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.outline_content_copy_32),
                            contentDescription = "Copy",
                            modifier = Modifier.size(32.dp * baseScale),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(
                        onClick = onShare,
                        modifier = Modifier.padding(4.dp * baseScale)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.outline_share_32),
                            contentDescription = "Share",
                            modifier = Modifier.size(32.dp * baseScale),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FriendListItem(
    modifier: Modifier = Modifier,
    model: FriendItemModel,
    onUserClick: (String) -> Unit,
    onYapClick: (String) -> Unit,
    onYapSend: (String) -> Unit,
    onMuteClick: () -> Unit,
    onRemoveClick: () -> Unit
) {
    BaseUserListItem(
        modifier = modifier,
        user = model.user,
        onUserClick = onUserClick,
        onYapClick = onYapClick,
        onLongYapClick = onYapSend,
        actionPopup = { isVisible, onDismiss ->
            // Здесь ты можешь вызвать либо DropdownMenu,
            // либо создать кастомный FriendActionsPopup по аналогии с DeleteUserPopup
            FriendActionsPopup(
                isVisible = isVisible,
                isMuted = model.isMuted,
                onDismiss = onDismiss,
                onMuteClick = onMuteClick,
                onDeleteClick = onRemoveClick
            )
        }
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    baseScale: Float,
    modifier: Modifier = Modifier,
    placeholderText: String? = null
) {
    val actualPlaceholder = placeholderText ?: stringResource(R.string.search_friends)
    val interactionSource = remember { MutableInteractionSource() }
    val customCursorColor = LocalAdditionColors.current.borderFieldColor

    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp * baseScale),
        interactionSource = interactionSource,
        singleLine = true,
        // Жирный текст (SemiBold) и удаление системного декорирования
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            fontSize = 16.sp * baseScale,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
            textDecoration = TextDecoration.None
        ),
        // Убираем подчеркивание при наборе (предиктивный ввод)
        keyboardOptions = KeyboardOptions(
            autoCorrectEnabled = false,
            imeAction = ImeAction.Search
        ),
        // Цвет самой палочки курсора
        cursorBrush = SolidColor(customCursorColor),
        decorationBox = { innerTextField ->
            TextFieldDefaults.DecorationBox(
                value = query,
                innerTextField = innerTextField,
                enabled = true,
                singleLine = true,
                visualTransformation = VisualTransformation.None,
                interactionSource = interactionSource,
                placeholder = {
                    Text(
                        text = actualPlaceholder,
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp * baseScale,
                        color = LocalAdditionColors.current.searchTextColor
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(start = 12.dp * baseScale)
                            .size(24.dp * baseScale),
                        tint = LocalAdditionColors.current.searchTextColor
                    )
                },
                shape = CircleShape,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = LocalAdditionColors.current.searchSurfaceColor,
                    unfocusedContainerColor = LocalAdditionColors.current.searchSurfaceColor,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    // Цвет курсора и элементов выделения
                    cursorColor = customCursorColor,
                    selectionColors = TextSelectionColors(
                        handleColor = customCursorColor,
                        backgroundColor = customCursorColor.copy(alpha = 0.3f)
                    )
                ),
                // Убираем внутренние отступы, чтобы текст центрировался по высоте 56.dp
                contentPadding = PaddingValues(horizontal = 0.dp)
            )
        }
    )
}

@Composable
fun EmptyFriendsCard(
    baseScale: Float,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val startGradient = MaterialTheme.colorScheme.primary
    val centerGradient = LocalAdditionColors.current.centerGradientColor
    val endGradient = LocalAdditionColors.current.pinkForGradientColor


    val gradientBrush = remember {
        Brush.linearGradient(
            colors = listOf(
                startGradient,
                centerGradient,
                endGradient
            )
        )
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp * baseScale),
        shape = RoundedCornerShape(20.dp * baseScale),
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(brush = gradientBrush),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.no_friends_yet),
                fontSize = 32.sp * baseScale,
                fontWeight = FontWeight.Black,
                fontStyle = FontStyle.Italic,
                color = Color.Black,
                lineHeight = 24.sp * baseScale
            )

            Spacer(modifier = Modifier.height(14.dp * baseScale))

            Text(
                text = stringResource(R.string.use_search_or_press),
                fontSize = 20.sp * baseScale,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp * baseScale
            )

            Spacer(modifier = Modifier.height(20.dp * baseScale))

            Button(
                modifier = Modifier
                    .width(180.dp * baseScale)
                    .height(44.dp * baseScale),
                onClick = onAddClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                shape = RoundedCornerShape(50),
                contentPadding = PaddingValues(horizontal = 32.dp * baseScale, vertical = 8.dp * baseScale)
            ) {
                Text(
                    text = stringResource(R.string.add),
                    color = MaterialTheme.colorScheme.background,
                    fontSize = 20.sp * baseScale,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}