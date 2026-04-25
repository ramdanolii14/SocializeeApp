package com.nyantadev.socializee.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.nyantadev.socializee.R

object UpdateNotificationHelper {

    private const val CHANNEL_ID   = "update_channel"
    private const val CHANNEL_NAME = "Pembaruan Aplikasi"
    private const val NOTIF_ID     = 1001

    fun showUpdateNotification(context: Context, info: UpdateChecker.UpdateInfo) {
        createChannel(context)

        // Tap notifikasi → langsung buka GitHub release di browser
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(info.releaseUrl)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            browserIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)   // ganti dengan ic_notification jika ada
            .setContentTitle("✨ Pembaruan Tersedia — v${info.latestVersion}")
            .setContentText("Versi baru Socializee sudah rilis! Ketuk untuk download.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(
                        "Versi baru Socializee v${info.latestVersion} sudah tersedia.\n\n" +
                                if (info.releaseNotes.isNotBlank())
                                    "Apa yang baru:\n${info.releaseNotes.take(200)}"
                                else
                                    "Ketuk untuk melihat detail dan download di GitHub."
                    )
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)            // notif hilang setelah ditap
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            // Tombol aksi langsung di notifikasi
            .addAction(
                R.drawable.ic_launcher_foreground,
                "Download Sekarang",
                pendingIntent
            )
            .build()

        // Cek permission POST_NOTIFICATIONS (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!nm.areNotificationsEnabled()) return
        }

        NotificationManagerCompat.from(context).notify(NOTIF_ID, notification)
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifikasi ketika ada versi baru aplikasi"
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }
}