package com.example.yap.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.yap.R
import com.example.yap.data.repository.UserRepository
import com.example.yap.ui.main.MainActivity
import com.example.yap.ui.main.YapApp
import com.example.yap.ui.navigation.AppDestinations
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.random.Random

class MyFcmService : FirebaseMessagingService() {

    private val userRepository: UserRepository
        get() = (applicationContext as YapApp).userRepository

    override fun onMessageReceived(remoteMessage: RemoteMessage) {

        val recipientId = remoteMessage.data["recipient_id"]
        val currentUid = userRepository.currentUserId

        if (recipientId != null && recipientId != currentUid) return

        val targetScreen = remoteMessage.data["target_screen"]
        val type = remoteMessage.data["type"]

        remoteMessage.notification?.let {
            showNotification(
                title = it.title,
                message = it.body,
                targetScreen = targetScreen)
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                userRepository.updateFcmTokenIfNeeded()
                Log.d("FCM_SERVICE", "Token sync initiated via onNewToken")
            } catch (e: Exception) {
                Log.e("FCM_SERVICE", "Error during token sync: ${e.message}")
            }
        }
    }

    private fun showNotification(title: String?, message: String?, targetScreen: String?) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "notifications_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(channelId, "Уведомления", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            // КЛЮЧЕВОЕ ИЗМЕНЕНИЕ 3: Обязательно задаем Action
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            val destination = targetScreen ?: AppDestinations.NOTIFICATIONS
            putExtra("target_screen", destination)
            Log.d("FCM_SERVICE", "Push created with destination: $destination")
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.yap2)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(Random.nextInt(), notification)
    }
}