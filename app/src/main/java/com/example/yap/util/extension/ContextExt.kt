package com.example.yap.util.extension

import android.content.Context
import android.content.Intent
import com.example.yap.R

/**
 * Универсальная функция для вызова системного диалога "Поделиться"
 */
fun Context.shareUserProfile(userId: String, userCode: String) {
    val deepLinkUrl = "https://yap.app/profile/$userId"

    val shareMessage = this.getString(R.string.share_profile_message, userCode, deepLinkUrl)

    val sendIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, shareMessage)
        type = "text/plain"
    }

    val shareIntent = Intent.createChooser(sendIntent, null)
    this.startActivity(shareIntent)
}