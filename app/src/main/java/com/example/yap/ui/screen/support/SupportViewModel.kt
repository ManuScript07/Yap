package com.example.yap.ui.screen.support

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.yap.ui.main.YapApp

class SupportViewModel(application: Application) : AndroidViewModel(application) {

    private val configManager by lazy { (application as YapApp).configManager }

    val telegramUrl: String get() = configManager.telegramUrl
    val gitHubUrl: String get() = configManager.githubUrl

}