package com.example.yap.ui.screen.auth


import android.content.Context
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material.Text
import com.example.yap.R
import com.example.yap.ui.theme.LocalBaseScale
import com.example.yap.util.compose.SystemBarsIconsColor
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch


@Composable
fun AuthScreen(
    onAuthSuccessExisting: () -> Unit,
    onAuthSuccessNew: (String) -> Unit,
    viewModel: AuthViewModel = viewModel()
) {
    SystemBarsIconsColor(isLight = true)
    val context = LocalContext.current
    val state by viewModel.authState.collectAsState()
    val isLoading = state is AuthViewModel.AuthState.Loading

    val scale = LocalBaseScale.current

    LaunchedEffect(state) {
        when (val currentState = state) {
            is AuthViewModel.AuthState.SuccessExisting -> onAuthSuccessExisting()
            is AuthViewModel.AuthState.SuccessNew -> onAuthSuccessNew(currentState.defaultName)
            else -> {}
        }
    }


    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.primary
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = (32.dp * scale)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Верхняя часть: Приветствие
            Column(
                modifier = Modifier.padding(top = (120.dp * scale)),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.hey_yap),

                    fontSize = (32.sp * scale),
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,

                    textAlign = TextAlign.Center,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(12.dp * scale))
            }

            // Нижняя часть: Кнопка и статусы
            Column(
                modifier = Modifier.padding(bottom = (60.dp * scale)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp * scale)
            ) {
                if (state is AuthViewModel.AuthState.Loading) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onSurface)
                } else {
                    Surface(
                        onClick = {
                            // Блокируем клик, если уже грузимся
                            if (!isLoading) {
                                viewModel.handleGoogleSignIn(context)
                            }
                        },
                        modifier = Modifier.width(280.dp * scale),
                        shape = RoundedCornerShape(28.dp * scale),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) {
                        Row(
                            modifier = Modifier
                                .height(60.dp * scale)
                                .padding(horizontal = 16.dp * scale),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            // Иконка Google (замени на свою, если есть в ресурсах)
                            Icon(
                                painter = painterResource(id = R.drawable.ic_google_logo),
                                contentDescription = null,
                                modifier = Modifier.size(32.dp * scale),
                                tint = MaterialTheme.colorScheme.background
                            )

                            Spacer(modifier = Modifier.width(14.dp * scale))

                            Text(
                                text = stringResource(R.string.sign_google),
                                fontSize = (18.sp * scale),
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.background

                            )
                        }
                    }
                }

                // Вывод ошибки, если она есть
                if (state is AuthViewModel.AuthState.Error) {
                    Text(
                        text = (state as AuthViewModel.AuthState.Error).message,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = (14.sp * scale),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }
        }
    }
}




suspend fun startGoogleSignIn(context: Context): String? {
    val credentialManager = CredentialManager.create(context)

    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(false)
        .setServerClientId(context.getString(R.string.default_web_client_id))
        .setAutoSelectEnabled(false)
        .build()

    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

    return try {
        Log.d("AuthDebug", "Запуск диалога CredentialManager...")
        val result = credentialManager.getCredential(context, request)
        val credential = result.credential

        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            Log.d("AuthDebug", "ID Token успешно получен")
            googleIdTokenCredential.idToken
        } else {
            Log.w("AuthDebug", "Получен неизвестный тип креативности: ${credential.type}")
            null
        }
    } catch (e: GetCredentialException) {
        // Важно: если пользователь просто закрыл диалог (отмена)
        when (e) {
            is GetCredentialCancellationException -> {
                Log.e("AuthDebug", "Пользователь закрыл диалог (отмена)")
            }
            is GetCredentialInterruptedException -> {
                Log.e("AuthDebug", "Процесс прерван (Interrupted): ${e.message}")
            }
            is GetCredentialUnknownException -> {
                Log.e("AuthDebug", "Неизвестная ошибка (возможно, неверный SHA-1 или настройки в Console): ${e.message}")
            }
            else -> {
                Log.e("AuthDebug", "Ошибка CredentialManager: [${e::class.java.simpleName}] ${e.message}")
            }
        }
        null
    } catch (e: Exception) {
        Log.e("AuthDebug", "Критическая ошибка: ${e.stackTraceToString()}")
        null
    }
}