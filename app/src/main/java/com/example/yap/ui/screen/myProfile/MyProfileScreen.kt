package com.example.yap.ui.screen.myProfile

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.yap.R
import com.example.yap.ui.components.FullScreenAvatarViewer
import com.example.yap.ui.main.MainActivity
import com.example.yap.ui.screen.home.HomeViewModel
import com.example.yap.ui.screen.userProfile.ProfileInfoItem
import com.example.yap.ui.theme.LocalAdditionColors
import com.example.yap.ui.theme.LocalBaseScale
import com.example.yap.util.compose.SystemBarsIconsColor
import com.example.yap.util.compose.rememberLambda
import com.example.yap.util.formatBirthday

@SuppressLint("LocalContextGetResourceValueCall")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyProfileScreen(
    viewModel: MyProfileViewModel = viewModel(
        factory = MyProfileViewModel.provideFactory(
            LocalContext.current.applicationContext as Application
        )
    ),
    onNavigateToEditProfile: () -> Unit,
    onSupportClick: () -> Unit,
) {
    SystemBarsIconsColor(isLight = true)

    val state by viewModel.state.collectAsState()
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val baseScale = LocalBaseScale.current

    var showLogoutDialog by remember { mutableStateOf(false) }

    val guardedOnShareProfile = rememberLambda<Unit> {
        val user = state.user ?: return@rememberLambda
        val userId = user.id
        val userCode = user.userCode
        val deepLinkUrl = "https://yap.app/profile/$userId"

        val shareMessage = context.getString(R.string.share_profile_message, userCode, deepLinkUrl)

        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, shareMessage)
            type = "text/plain"
        }

        context.startActivity(Intent.createChooser(sendIntent, null))
    }

    val guardedOnEditProfile = rememberLambda<Unit> {
        onNavigateToEditProfile()
    }

    val guardedOnSupportClick = rememberLambda<Unit> {
        onSupportClick()
    }

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

    val backgroundColor = LocalAdditionColors.current.speedBottomDialog
    val cardColor = LocalAdditionColors.current.descriptionSurfaceColor

    val logoutState by viewModel.logoutState.collectAsState()

    LaunchedEffect(logoutState) {
        when (logoutState) {
            is MyProfileViewModel.LogoutState.Success -> {
                viewModel.resetLogoutState()

                // Идеальный паттерн для Compose: очищаем весь стек навигации
                // и перезапускаем приложение с чистого листа
                val intent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                context.startActivity(intent)
                (context as? Activity)?.finish()
            }
            is MyProfileViewModel.LogoutState.Error -> {
                val errorMessage = (logoutState as MyProfileViewModel.LogoutState.Error).message
                Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
                viewModel.resetLogoutState()
            }
            else -> {}
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().background(backgroundColor)) {

            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = LocalAdditionColors.current.toggleButtonColor
                )
            } else if (state.error != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp * baseScale),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = state.error ?: stringResource(R.string.error_generic),
                        fontSize = 20.sp * baseScale,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                }
            } else if (state.user != null) {
                val user = state.user!!

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        // 1. Шапка (Градиент)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(240.dp * baseScale)
                                .background(gradientBrush)
                        )

                        // 2. Тело профиля
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 190.dp * baseScale),
                            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                            color = backgroundColor
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp * baseScale),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Spacer(modifier = Modifier.height(84.dp * baseScale))

                                Text(
                                    text = user.name,
                                    fontSize = 24.sp * baseScale,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Spacer(modifier = Modifier.height(16.dp * baseScale))

                                // Кнопки действий: Редактировать и Поделиться
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp * baseScale),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = { guardedOnEditProfile(Unit) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = LocalAdditionColors.current.darkYapButtonBackgroundColor
                                        ),
                                        shape = RoundedCornerShape(50),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(56.dp * baseScale),
                                        contentPadding = PaddingValues(horizontal = 16.dp * baseScale)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                text = stringResource(R.string.edit_profile),
                                                color = Color.Black,
                                                fontSize = 18.sp * baseScale,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )

                                            Spacer(modifier = Modifier.width(8.dp * baseScale))

                                            Icon(
                                                painter = painterResource(id = R.drawable.outline_edit_24),
                                                contentDescription = null,
                                                tint = Color.Black,
                                                modifier = Modifier.size(24.dp * baseScale)
                                            )
                                        }
                                    }

                                    Button(
                                        onClick = { guardedOnShareProfile(Unit) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = LocalAdditionColors.current.purpleButtonColor
                                        ),
                                        shape = RoundedCornerShape(12.dp * baseScale),
                                        modifier = Modifier
                                            .width(75.dp * baseScale)
                                            .height(56.dp * baseScale),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.outline_upload_24),
                                            contentDescription = stringResource(R.string.share),
                                            tint = Color.White,
                                            modifier = Modifier.size(32.dp * baseScale)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp * baseScale))

                                // Блок с описанием (О себе, Никнейм, День рождения)
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(20.dp),
                                    color = cardColor
                                ) {
                                    Column(modifier = Modifier.padding(20.dp * baseScale)) {
                                        if (user.bio.isNotEmpty()) {
                                            ProfileInfoItem(user.bio, stringResource(R.string.about_me), baseScale)
                                        }

                                        if (user.username.isNotEmpty()) {
                                            if (user.bio.isNotEmpty()) Spacer(modifier = Modifier.height(20.dp * baseScale))
                                            ProfileInfoItem("@${user.username}", stringResource(R.string.user_name), baseScale)
                                        }

                                        val birthday = formatBirthday(user.dobTimestamp, user.showOnlyDay)
                                        if (birthday.isNotEmpty()) {
                                            if (user.bio.isNotEmpty() || user.username.isNotEmpty()) {
                                                Spacer(modifier = Modifier.height(20.dp * baseScale))
                                            }
                                            ProfileInfoItem(birthday, stringResource(R.string.your_birthday), baseScale)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp * baseScale))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp * baseScale),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Кнопка Поддержка
                                    Button(
                                        onClick = { guardedOnSupportClick(Unit) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = LocalAdditionColors.current.checkBackgroundColor
                                        ),
                                        shape = RoundedCornerShape(12.dp * baseScale),
                                        modifier = Modifier
                                            .widthIn(min = 120.dp * baseScale)
                                            .height(56.dp * baseScale),
                                        contentPadding = PaddingValues(horizontal = 20.dp * baseScale)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.support),
                                            color = Color.White,
                                            letterSpacing = 1.sp * baseScale,
                                            fontSize = 20.sp * baseScale,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1
                                        )
                                    }

                                    // Кнопка Выход
                                    Button(
                                        onClick = { showLogoutDialog = true },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.errorContainer,
                                            contentColor = MaterialTheme.colorScheme.error // Цвет для текста и иконки
                                        ),
                                        shape = RoundedCornerShape(50.dp * baseScale),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(56.dp * baseScale)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                painter = painterResource(id = R.drawable.outline_logout_24), // Замени на свой ID ресурса иконки выхода
                                                contentDescription = null,
                                                modifier = Modifier.size(24.dp * baseScale)
                                            )

                                            Spacer(modifier = Modifier.width(8.dp * baseScale))

                                            Text(
                                                text = stringResource(R.string.logout),
                                                fontSize = 18.sp * baseScale,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }

                            }
                        }

                        // 3. Аватарка
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 112.dp * baseScale),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = user.avatarUrl ?: R.drawable.avatar_1,
                                contentDescription = "Avatar",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(160.dp * baseScale)
                                    .border(
                                        width = 4.dp * baseScale,
                                        color = LocalAdditionColors.current.speedBottomDialog,
                                        shape = CircleShape
                                    )
                                    .padding(0.5.dp)
                                    .clip(CircleShape)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = ripple(bounded = false, radius = 80.dp * baseScale),
                                        onClick = { viewModel.toggleAvatarViewer(true) }
                                    ),
                                placeholder = painterResource(id = R.drawable.avatar_1),
                                error = painterResource(id = R.drawable.avatar_1),
                                fallback = painterResource(R.drawable.avatar_1)
                            )
                        }
                    }
                }
            }
        }

        if (showLogoutDialog) {
            LogoutDialog(
                onConfirm = {
                    viewModel.logout()
                },
                onDismiss = { showLogoutDialog = false },
                baseScale = baseScale
            )
        }

        if (state.isAvatarViewerOpen && state.user != null) {
            FullScreenAvatarViewer(
                avatarUrl = state.user?.avatarUrl,
                userName = state.user?.name ?: "",
                onClose = { viewModel.toggleAvatarViewer(false) },
                baseScale = baseScale
            )
        }
        if (logoutState is MyProfileViewModel.LogoutState.Loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .pointerInput(Unit) {},
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

    }
}


@Composable
fun LogoutDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    baseScale: Float = LocalBaseScale.current
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.logout_title),
                fontSize = 24.sp * baseScale,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Text(
                text = stringResource(R.string.logout_confirm_message),
                fontSize = 16.sp * baseScale,
                color = LocalAdditionColors.current.searchTextColor
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm()
                    onDismiss()
                }
            ) {
                Text(
                    text = stringResource(R.string.logout_action), // Или R.string.yes
                    fontSize = 20.sp * baseScale,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.cancel),
                    fontSize = 20.sp * baseScale,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        containerColor = LocalAdditionColors.current.surfaceDialogColor,
        shape = RoundedCornerShape(28.dp * baseScale)
    )
}

