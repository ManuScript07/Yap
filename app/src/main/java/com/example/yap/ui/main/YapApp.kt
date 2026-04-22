package com.example.yap.ui.main

import UserPreferences
import android.app.Application
import com.example.yap.RemoteConfigManager
import com.example.yap.data.repository.ChatRepository
import com.example.yap.data.repository.UserRepository
import com.example.yap.service.GroqTranscriptionService
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings

class YapApp : Application() {

    val configManager by lazy { RemoteConfigManager() }

    val userPrefs by lazy { UserPreferences(applicationContext) }

    val userRepository by lazy {
        UserRepository(userPrefs = userPrefs)
    }

    val chatRepository by lazy { ChatRepository(configManager) }
    val transcriptionService by lazy{ GroqTranscriptionService(configManager) }

    override fun onCreate() {
        super.onCreate()

        FirebaseApp.initializeApp(this)

        // Современный синтаксис для включения кэша (без deprecated)
        val settings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
            .build()

        FirebaseFirestore.getInstance().firestoreSettings = settings

        val _triggerFetch = configManager
    }
}