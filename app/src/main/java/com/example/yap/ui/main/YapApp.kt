package com.example.yap.ui.main

import UserPreferences
import android.app.Application
import com.example.yap.data.manager.RemoteConfigManager
import com.example.yap.data.repository.ChatRepository
import com.example.yap.data.repository.UserRepository
import com.example.yap.service.GroqTranscriptionService
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache

class YapApp : Application(), ImageLoaderFactory {

    val configManager by lazy { RemoteConfigManager() }

    val userPrefs by lazy { UserPreferences(applicationContext) }

    val userRepository by lazy {
        UserRepository(
            userPrefs = userPrefs,
            configManager = configManager
        )
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

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            // 1. ГЛАВНАЯ МАГИЯ: Игнорируем `max-age=3600` от Supabase!
            // Теперь Coil будет вечно хранить картинку, пока ее не вытеснят новые.
            .respectCacheHeaders(false)

            // 2. Настраиваем мощный кэш на диске (для работы оффлайн)
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("yap_image_cache"))
                    .maxSizePercent(0.05) // Отдаем 5% свободного места на устройстве под кэш
                    .build()
            }
            // 3. Настраиваем кэш в оперативной памяти (чтобы список не мерцал при скролле)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.2) // 20% доступной памяти приложению
                    .build()
            }
            // 4. Включаем плавное появление картинок глобально
            .crossfade(true)
            .build()
    }

}