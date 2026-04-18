package com.example.yap.ui.screen.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.yap.UserRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SplashViewModel(private val repository: UserRepository = UserRepository()) : ViewModel() {

    private val _isSplashVisible = MutableStateFlow(true)
    val isSplashVisible = _isSplashVisible.asStateFlow()

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
}