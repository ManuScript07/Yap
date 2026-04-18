package com.example.yap.ui.screen.auth

import android.content.Context


import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.example.yap.R
import kotlinx.coroutines.launch
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import androidx.credentials.exceptions.GetCredentialException


@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    viewModel: AuthViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.authState.collectAsState()

    // Наблюдаем за успехом, чтобы перейти на следующий экран
    LaunchedEffect(state) {
        if (state is AuthViewModel.AuthState.Success) {
            onAuthSuccess()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = "Добро пожаловать в Yap",
                fontWeight = FontWeight.Bold
            )

            if (state is AuthViewModel.AuthState.Loading) {
                CircularProgressIndicator()
            } else {
                Button(
                    onClick = {
                        // Здесь запускается логика выбора аккаунта (ниже)
                        startGoogleSignIn(context) { token ->
                            viewModel.handleGoogleSignIn(token)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Войти через Google")
                }
            }

            if (state is AuthViewModel.AuthState.Error) {
                Text(
                    text = (state as AuthViewModel.AuthState.Error).message,
                    color = MaterialTheme.colors.onError
                )
            }
        }
    }
}




fun startGoogleSignIn(context: Context, onTokenReceived: (String) -> Unit) {
    // Нам нужен scope, чтобы запустить suspend функцию.
    // Обычно в Activity это lifecycleScope.
    val activity = context as? ComponentActivity ?: return
    val credentialManager = CredentialManager.create(context)

    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(false)
        .setServerClientId(context.getString(R.string.default_web_client_id))
        .setAutoSelectEnabled(true)
        .build()

    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

    // Запускаем корутину
    activity.lifecycleScope.launch {
        try {
            val result = credentialManager.getCredential(context, request)
            val credential = result.credential

            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {

                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                onTokenReceived(googleIdTokenCredential.idToken)
            }
        } catch (e: GetCredentialException) {
            Log.e("Auth", "Ошибка Credential Manager: ${e.message}")
            // Здесь можно добавить callback для обработки ошибки в UI
        } catch (e: Exception) {
            Log.e("Auth", "Непредвиденная ошибка: ${e.message}")
        }
    }
}