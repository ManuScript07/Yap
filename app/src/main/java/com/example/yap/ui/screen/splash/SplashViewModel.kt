package com.example.yap.ui.screen.splash

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.yap.UserRepository
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

class SplashViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as YapApp
    private val repository = app.userRepository

    private val _isSplashVisible = MutableStateFlow(true)

    private val _navigationEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val navigationEvent = _navigationEvent.asSharedFlow()
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

    init {
        // Убираем сплэш через задержку
        viewModelScope.launch {
            delay(1200)
            _isSplashVisible.value = false
        }
    }

    fun navigateTo(route: String) {
        _pendingRoute.value = route
    }

    // Вызываем, когда успешно перешли, чтобы не зацикливаться
    fun onRouteConsumed() {
        _pendingRoute.value = null
    }
}