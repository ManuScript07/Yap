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
import com.example.yap.data.repository.FriendRequestRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.storage.Storage
import okhttp3.OkHttpClient

class YapApp : Application(), ImageLoaderFactory {

    val gson by lazy { Gson() }
    val okHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    val configManager by lazy { RemoteConfigManager() }

    val userPrefs by lazy { UserPreferences(applicationContext) }


    val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    val firebaseAuth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    val supabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = configManager.supabaseUrl,
            supabaseKey = configManager.supabaseAnonKey
        ) {
            install(Storage)
            httpEngine = io.ktor.client.engine.okhttp.OkHttp.create {
                preconfigured = okHttpClient
            }
        }
    }


    val userRepository by lazy {
        UserRepository(
            firestore = firestore,
            auth = firebaseAuth,
            userPrefs = userPrefs,
            configManager = configManager,
            supabase = supabaseClient
        )
    }

    val chatRepository by lazy {
        ChatRepository(
            firestore = firestore,
            configManager = configManager,
            supabase = supabaseClient,
            client = okHttpClient,
            gson = gson
        )
    }

    val friendsRequestRepository by lazy {
        FriendRequestRepository(
            firestore = firestore,
            auth = firebaseAuth,
            client = okHttpClient,
            gson = gson
        )
    }
    val transcriptionService by lazy{ GroqTranscriptionService(configManager) }

    override fun onCreate() {
        super.onCreate()

        FirebaseApp.initializeApp(this)

        // Современный синтаксис для включения кэша (без deprecated)
        val settings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
            .build()

        firestore.firestoreSettings = settings


        val _triggerFetch = configManager
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .respectCacheHeaders(false)
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("yap_image_cache"))
                    .maxSizePercent(0.05)
                    .build()
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.2)
                    .build()
            }
            .crossfade(true)
            .build()
    }

}