package com.example.yap.ui.screen.splash

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.yap.ui.main.YapApp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.google.firebase.firestore.Source

class SplashViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as YapApp
    private val repository = app.userRepository

    private val _isSplashVisible = MutableStateFlow(true)
    val isSplashVisible = _isSplashVisible.asStateFlow()
    private val _pendingRoute = MutableStateFlow<String?>(null)
    val pendingRoute = _pendingRoute.asStateFlow()

    // Превращаем Flow из репозитория в StateFlow для UI
    val isReady: StateFlow<Boolean> = repository.currentUserFlow
        .map { it != null }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = FirebaseAuth.getInstance().currentUser != null
        )

    sealed class EntryState {
        object Loading : EntryState()
        object NotAuthenticated : EntryState() // Нужно на экран логина
        object NeedsRegistration : EntryState() // Залогинен, но нет профиля
        object FullyReady : EntryState() // Всё ок, на главную
    }

    private val _entryState = MutableStateFlow<EntryState>(EntryState.Loading)
    val entryState = _entryState.asStateFlow()

    init {
        // Убираем сплэш через задержку
        viewModelScope.launch {
            delay(1200)
            _isSplashVisible.value = false
        }
        checkUserStatus()
    }

    fun navigateTo(route: String) {
        _pendingRoute.value = route
    }

    // Вызываем, когда успешно перешли, чтобы не зацикливаться
    fun onRouteConsumed() {
        _pendingRoute.value = null
    }

    private fun checkUserStatus() {
        viewModelScope.launch {
            val currentUser = FirebaseAuth.getInstance().currentUser

            // 1. Проверяем Auth (Единственный источник правды об авторизации)
            if (currentUser == null) {
                _entryState.value = EntryState.NotAuthenticated
                delay(1200)
                _isSplashVisible.value = false
                return@launch
            }

            // Пользователь АВТОРИЗОВАН. Теперь определяем, куда его пустить.
            try {
                // 2. Пытаемся достать профиль из КЭША (работает моментально и без интернета)
                val docCache = repository.usersCollection.document(currentUser.uid)
                    .get(Source.CACHE)
                    .await()

                if (docCache.exists() && docCache.contains("username")) {
                    _entryState.value = EntryState.FullyReady
                } else {
                    _entryState.value = EntryState.NeedsRegistration
                }

            } catch (cacheException: Exception) {
                // 3. В кэше пусто (например, первый вход с нового устройства). Идем в СЕТЬ.
                try {
                    val docServer = repository.usersCollection.document(currentUser.uid)
                        .get(Source.SERVER)
                        .await()

                    if (docServer.exists() && docServer.contains("username")) {
                        _entryState.value = EntryState.FullyReady
                    } else {
                        _entryState.value = EntryState.NeedsRegistration
                    }

                } catch (networkException: Exception) {
                    // 4. НЕТ СЕТИ И НЕТ КЭША.
                    // КРИТИЧЕСКИ ВАЖНО: Мы НЕ переводим в NotAuthenticated!
                    // Мы предполагаем, что если юзер авторизован, он скорее всего имеет профиль.
                    // Пускаем его на главный экран. Firestore сам синхронизируется, когда появится сеть.
                    Log.e("SplashViewModel", "Network error, letting user in offline mode", networkException)
                    _entryState.value = EntryState.FullyReady
                }
            }

            // Держим сплэш минимум 1.2 сек для красоты (если проверки прошли быстрее)
            delay(1200)
            _isSplashVisible.value = false
        }
    }
}