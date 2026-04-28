package com.example.yap.ui.screen.editProfile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yap.R
import com.example.yap.data.model.UserItem
import com.example.yap.ui.components.BaseTopAppBar
import com.example.yap.ui.screen.registration.ProfileRegistrationScreen
import com.example.yap.ui.theme.LocalAdditionColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    currentUser: UserItem,
    onBack: () -> Unit,
    viewModel: EditProfileViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

    LaunchedEffect(state) {
        if (state is EditProfileViewModel.EditState.Success) {
            onBack()
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            BaseTopAppBar(
                title = stringResource(R.string.edit_profile_title),
                onBack = onBack,
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {

            ProfileRegistrationScreen(
                initialName = currentUser.name,
                initialUsername = currentUser.username,
                initialBio = currentUser.bio,
                initialDob = currentUser.dobTimestamp,
                initialShowOnlyDay = currentUser.showOnlyDay,
                initialAvatarUrl = currentUser.avatarUrl,
                onComplete = { name, username, dob, showOnlyDay, bio, photoUri, isRemoved ->
                    viewModel.updateProfile(
                        newName = name,
                        newUsername = username,
                        newDob = dob,
                        newShowOnlyDay = showOnlyDay,
                        newBio = bio,
                        newPhotoUri = photoUri,
                        isPhotoRemoved = isRemoved,
                        currentUser = currentUser
                    )
                },
                isEdit = true
            )

            if (state is EditProfileViewModel.EditState.Loading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .pointerInput(Unit) {},
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = LocalAdditionColors.current.toggleButtonColor)
                }
            }
        }
    }
}