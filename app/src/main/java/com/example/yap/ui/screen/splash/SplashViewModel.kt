package com.example.yap.ui.screen.splash

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.yap.ui.main.YapApp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

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

            if (currentUser == null) {
                _entryState.value = EntryState.NotAuthenticated
            } else {
                // ПРОВЕРЯЕМ: есть ли профиль в базе?
                try {
                    val doc = repository.usersCollection.document(currentUser.uid).get().await()
                    if (doc.exists() && doc.contains("username")) {
                        _entryState.value = EntryState.FullyReady
                    } else {
                        _entryState.value = EntryState.NeedsRegistration
                    }
                } catch (e: Exception) {
                    // Если ошибка сети, можем временно считать неавторизованным
                    // или оставить Loading
                    _entryState.value = EntryState.NotAuthenticated
                }
            }

            // Держим сплэш минимум 1.2 сек для красоты
            delay(1200)
            _isSplashVisible.value = false
        }
    }
}