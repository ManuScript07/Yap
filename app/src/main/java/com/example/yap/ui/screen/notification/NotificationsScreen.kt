package com.example.yap.ui.screen.notification


import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yap.R
import com.example.yap.ui.theme.LocalAdditionColors
import com.example.yap.ui.theme.LocalBaseScale
import com.example.yap.util.compose.StatusBarIconsColor
import com.example.yap.util.compose.rememberLambda
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onNavigateToProfile: (Int) -> Unit,
    viewModel: NotificationsViewModel = viewModel()
) {
    StatusBarIconsColor(isLight = true)

    val state by viewModel.state.collectAsState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val baseScale = LocalBaseScale.current

    val guardedNavigateToProfile = rememberLambda<Int> { userId ->
        onNavigateToProfile(userId)
    }

    val pullToRefreshState = rememberPullToRefreshState()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.notification),
                        fontSize = (28 * baseScale).sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    Box(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clickable(
                                onClick = onBack,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.baseline_arrow_back_24),
                            contentDescription = "Назад",
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = LocalAdditionColors.current.headerColor
                ),
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.refresh() },
            state = pullToRefreshState,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
            indicator = {
                // Кастомный контейнер для твоего Expressive LoadingIndicator
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .graphicsLayer {
                            // Читаем state здесь (0 рекомпозиций)
                            val fraction = pullToRefreshState.distanceFraction.coerceIn(0f, 1f)

                            // Максимальное пространство, которое откроется сверху (например, 80dp)
                            val maxShiftPx = 80.dp.toPx()

                            // Центрируем индикатор в открывающемся пространстве
                            translationY = (fraction * maxShiftPx) / 2f

                            // Плавное проявление
                            alpha = fraction
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (state.isRefreshing) {
                        // Индетерминантный режим (анимация загрузки)
                        LoadingIndicator()
                    } else {
                        // Детерминантный режим (морфинг в зависимости от натяжения)
                        // Передаем лямбду progress = { ... }, чтобы избежать рекомпозиций!
                        LoadingIndicator(
                            progress = { pullToRefreshState.distanceFraction.coerceIn(0f, 1f) }
                        )
                    }
                }
            }
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // Физически оттягиваем сам список вниз вместе с пальцем.
                        // Опять же: чтение distanceFraction внутри graphicsLayer не вызывает лагов.
                        val maxShiftPx = 80.dp.toPx()
                        translationY = pullToRefreshState.distanceFraction * maxShiftPx
                    },
                contentPadding = PaddingValues(
                    top = 12.dp * baseScale,
                    bottom = innerPadding.calculateBottomPadding() + (20.dp * baseScale)
                )
            ) {
                items(
                    items = state.notifications,
                    key = { it.id }
                ) { notification ->
                    NotificationRow(
                        item = notification,
                        onDelete = { viewModel.deleteNotification(notification.id) },
                        onMute = { viewModel.muteNotification(notification.id) },
                        onYapClick = {},
                        onLocationClick = {},
                        onNavigateToProfile = guardedNavigateToProfile,
                    )
                }
            }
        }
    }
}
