package com.example.yap.ui.screen.splash

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yap.R
import com.example.yap.ui.components.BaseTopAppBar
import com.example.yap.ui.navigation.NavigationApp
import com.example.yap.ui.screen.auth.AuthScreen
import com.example.yap.ui.screen.registration.ProfileRegistrationScreen
import com.example.yap.ui.screen.registration.RegistrationViewModel
import com.example.yap.ui.theme.LocalBaseScale
import com.example.yap.util.compose.SystemBarsIconsColor
import com.google.firebase.auth.FirebaseAuth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppEntryWithSplash(
    splashViewModel: SplashViewModel = viewModel(),
    registrationViewModel: RegistrationViewModel = viewModel()
) {
    val splashVisible by splashViewModel.isSplashVisible.collectAsState()
    val entryState by splashViewModel.entryState.collectAsState()

    val baseScale = LocalBaseScale.current

    val regState by registrationViewModel.registrationState.collectAsState()

    var currentScreen by remember { mutableStateOf("splash") }
    var initialNameForRegistration by remember { mutableStateOf("") }

    LaunchedEffect(regState) {
        if (regState is RegistrationViewModel.RegistrationState.Success) {
            currentScreen = "main"
        }
    }

    LaunchedEffect(splashVisible) {
        if (!splashVisible && currentScreen == "splash") {
            currentScreen = when (entryState) {
                is SplashViewModel.EntryState.FullyReady -> "main"
                is SplashViewModel.EntryState.NeedsRegistration -> {
                    initialNameForRegistration = FirebaseAuth.getInstance().currentUser?.displayName ?: ""
                    "registration"
                }
                else -> "auth"
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        when (currentScreen) {
            "main" -> NavigationApp(splashViewModel)

            "auth" -> AuthScreen(
                onAuthSuccessExisting = {
                    currentScreen = "main"
                },
                onAuthSuccessNew = { googleName ->
                    initialNameForRegistration = googleName
                    currentScreen = "registration"
                }
            )

            "registration" -> {
                val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    topBar = {
                        BaseTopAppBar(
                            title = stringResource(R.string.account_creation),
                            onBack = null,
                            scrollBehavior = scrollBehavior
                        )
                    }
                ) { paddingValues ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        ProfileRegistrationScreen(
                            initialName = initialNameForRegistration,
                            isEdit = false,
                            externalPadding = paddingValues,
                            onComplete = { name, username, dob, showOnlyDay, bio, photoUri, isRemoved ->
                                registrationViewModel.completeRegistration(
                                    name, username, dob, showOnlyDay, bio, photoUri, isRemoved
                                )
                            }
                        )

                        if (regState is RegistrationViewModel.RegistrationState.Loading) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.5f))
                                    .pointerInput(Unit) {},
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }
        }

        if (regState is RegistrationViewModel.RegistrationState.Error) {
            val context = LocalContext.current
            val errorMessage = (regState as RegistrationViewModel.RegistrationState.Error).message
            LaunchedEffect(regState) {
                Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
            }
        }


        AnimatedVisibility(
            visible = splashVisible,
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(tween())
        ) {
            SplashContent(baseScale = baseScale)
        }
    }
}

@Composable
fun SplashContent(baseScale: Float = 1f) {
    SystemBarsIconsColor(isLight = true)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = R.drawable.yap_button_big_text),
            contentDescription = "YAP Logo",
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(
                width = 140.dp * baseScale,
                height = 60.dp * baseScale
            )
        )
    }
}
