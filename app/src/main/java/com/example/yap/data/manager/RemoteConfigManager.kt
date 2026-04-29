package com.example.yap.data.manager

import android.util.Log
import com.example.yap.R
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings

class RemoteConfigManager {

    private val remoteConfig: FirebaseRemoteConfig = FirebaseRemoteConfig.getInstance()

    init {
        val configSettings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(3600)
            .build()
        remoteConfig.setConfigSettingsAsync(configSettings)

        remoteConfig.setDefaultsAsync(R.xml.remote_config_defaults)

        fetchAndActivate()
    }

    private fun fetchAndActivate() {
        remoteConfig.fetchAndActivate()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d("RemoteConfig", "Получено")
                    val updated = task.result
                    println("Remote Config updated: $updated")
                }
            }
    }


    val priceText: Int get() = remoteConfig.getLong("price_text").toInt()
    val priceEmoji: Int get() = remoteConfig.getLong("price_emoji").toInt()
    val priceVoice: Int get() = remoteConfig.getLong("price_voice").toInt()
    val priceSimpleYap: Int get() = remoteConfig.getLong("price_simple_yap").toInt()
    val regenDelayMs: Long get() = remoteConfig.getLong("regen_delay_ms")

    val maxLengthInputText: Int get() = remoteConfig.getLong("max_length_input_text").toInt()

    val supabaseBucket: String get() = remoteConfig.getString("supabase_bucket_name")
    val supabaseUrl: String get() = remoteConfig.getString("supabase_project_url")
    val supabaseAnonKey: String get() = remoteConfig.getString("supabase_anon_key")

    val groqApiKey: String get() = remoteConfig.getString("groq_api_key")
    val telegramUrl: String get() = remoteConfig.getString("support_telegram")
    val githubUrl: String get() = remoteConfig.getString("support_github")



}