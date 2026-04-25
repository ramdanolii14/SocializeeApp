package com.nyantadev.socializee.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.nyantadev.socializee.R
import com.nyantadev.socializee.api.RetrofitClient
import com.nyantadev.socializee.models.DeviceTokenRequest
import com.nyantadev.socializee.ui.MainActivity
import com.nyantadev.socializee.utils.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SocializeeFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        const val CHANNEL_ID_SOCIAL  = "socializee_notifications"
        const val CHANNEL_ID_UPDATE  = "update_channel"
        const val CHANNEL_NAME_SOCIAL = "Socializee"
        const val CHANNEL_NAME_UPDATE = "Pembaruan Aplikasi"
    }

    // ── Token refresh ─────────────────────────────────────────────────────────
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val sessionManager = SessionManager(applicationContext)
        if (!sessionManager.isLoggedIn()) return

        RetrofitClient.init(sessionManager)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                RetrofitClient.getApiService()
                    .registerDeviceToken(DeviceTokenRequest(token))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // ── Pesan masuk ───────────────────────────────────────────────────────────
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val data  = message.data
        val type  = data["type"] ?: ""

        // Pisah handling: notif update vs notif sosial biasa
        if (type == "app_update") {
            handleUpdateNotification(message)
        } else {
            val title = message.notification?.title ?: return
            val body  = message.notification?.body  ?: return
            showSocialNotification(title, body, data)
        }
    }

    // ── Handler: notifikasi update aplikasi ───────────────────────────────────
    private fun handleUpdateNotification(message: RemoteMessage) {
        val data        = message.data
        val version     = data["version"]     ?: return
        val releaseUrl  = data["release_url"] ?: "https://github.com"

        val title = message.notification?.title ?: "✨ Update Tersedia — v$version"
        val body  = message.notification?.body  ?: "Socializee v$version sudah rilis!"

        createChannel(CHANNEL_ID_UPDATE, CHANNEL_NAME_UPDATE, NotificationManager.IMPORTANCE_HIGH)

        // Tap → buka URL release di browser
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(releaseUrl)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 1001, browserIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_UPDATE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_notification, "Download Sekarang", pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(1001, notification) // ID tetap 1001 agar tidak numpuk
    }

    // ── Handler: notifikasi sosial biasa (like, comment, follow, dll) ─────────
    private fun showSocialNotification(title: String, body: String, data: Map<String, String>) {
        createChannel(CHANNEL_ID_SOCIAL, CHANNEL_NAME_SOCIAL, NotificationManager.IMPORTANCE_DEFAULT)

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "notifications")
            putExtra("notification_type", data["type"])
            putExtra("post_id", data["postId"])
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_SOCIAL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    // ── Buat notification channel (idempotent) ────────────────────────────────
    private fun createChannel(id: String, name: String, importance: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(id) != null) return // sudah ada, skip
            val channel = NotificationChannel(id, name, importance).apply {
                description   = if (id == CHANNEL_ID_UPDATE) "Notifikasi versi baru aplikasi"
                else "Notifikasi aktivitas Socializee"
                enableVibration(true)
                enableLights(true)
            }
            manager.createNotificationChannel(channel)
        }
    }
}