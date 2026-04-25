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



    sealed class EntryState {
        object Loading : EntryState()
        object NotAuthenticated : EntryState() // Нужно на экран логина
        object NeedsRegistration : EntryState() // Залогинен, но нет профиля
        object FullyReady : EntryState() // Всё ок, на главную
    }

    private val _entryState = MutableStateFlow<EntryState>(EntryState.Loading)
    val entryState = _entryState.asStateFlow()

    init {
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
            // Засекаем, сколько времени заняла логика проверок
            val startTime = System.currentTimeMillis()

            // 1. Спрашиваем репозиторий (а не Firebase напрямую)
            val currentUser = repository.getCurrentUser()

            if (currentUser == null) {
                _entryState.value = EntryState.NotAuthenticated
            } else {
                // Пользователь АВТОРИЗОВАН. Проверяем профиль.
                try {
                    val docCache = repository.usersCollection.document(currentUser.uid)
                        .get(Source.CACHE).await()

                    if (docCache.exists() && docCache.contains("username")) {
                        _entryState.value = EntryState.FullyReady
                    } else {
                        // Кэш пуст, идем в сеть
                        val docServer = repository.usersCollection.document(currentUser.uid)
                            .get(Source.SERVER).await()

                        if (docServer.exists() && docServer.contains("username")) {
                            _entryState.value = EntryState.FullyReady
                        } else {
                            _entryState.value = EntryState.NeedsRegistration
                        }
                    }
                } catch (e: Exception) {
                    // ФОЛЛБЕК ОФЛАЙНА
                    Log.e("SplashViewModel", "Network error, offline mode", e)
                    _entryState.value = EntryState.FullyReady
                }
            }

            // Вычисляем, сколько еще нужно подождать до 1200мс
            val elapsedTime = System.currentTimeMillis() - startTime
            val remainingDelay = 1200L - elapsedTime

            if (remainingDelay > 0) {
                delay(remainingDelay)
            }

            // Снимаем сплэш только один раз, строго здесь!
            _isSplashVisible.value = false
        }
    }
}