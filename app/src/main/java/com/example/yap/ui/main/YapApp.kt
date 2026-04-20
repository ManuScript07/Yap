package com.example.yap.ui.main

import android.app.Application
import com.example.yap.ChatRepository
import com.example.yap.UserRepository
import com.example.yap.service.GroqTranscriptionService
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings

class YapApp : Application() {

    val userRepository by lazy { UserRepository() }
    val chatRepository by lazy { ChatRepository() }
    val transcriptionService by lazy{ GroqTranscriptionService() }

    override fun onCreate() {
        super.onCreate()

        FirebaseApp.initializeApp(this)

        // Современный синтаксис для включения кэша (без deprecated)
        val settings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
            .build()

        FirebaseFirestore.getInstance().firestoreSettings = settings


    }
}