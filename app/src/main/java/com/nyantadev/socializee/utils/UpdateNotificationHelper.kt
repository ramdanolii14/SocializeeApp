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
        // Buat channel dulu (wajib Android 8+, no-op di bawahnya)
        createChannel(context)

        // Cek izin notifikasi (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!nm.areNotificationsEnabled()) return
        }

        // Tap notifikasi → buka GitHub release di browser
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(info.releaseUrl)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, browserIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action button "Download Sekarang"
        val downloadAction = NotificationCompat.Action.Builder(
            R.drawable.ic_launcher_foreground,
            "Download Sekarang",
            pendingIntent
        ).build()

        val releaseNotesTrimmed = if (info.releaseNotes.isNotBlank())
            "\n\nApa yang baru:\n${info.releaseNotes.take(300)}"
        else ""

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("✨ Update Tersedia — v${info.latestVersion}")
            .setContentText("Versi baru Socializee sudah rilis! Ketuk untuk download.")
            // BigText agar isi changelog terlihat saat notif dibuka
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(
                        "Socializee v${info.latestVersion} sudah tersedia.$releaseNotesTrimmed"
                    )
                    .setBigContentTitle("✨ Update Tersedia — v${info.latestVersion}")
                    .setSummaryText("Ketuk untuk download")
            )
            .setContentIntent(pendingIntent)
            .addAction(downloadAction)
            .setAutoCancel(true)
            // ── Heads-up (muncul di atas layar seperti notif pesan) ──────────
            .setPriority(NotificationCompat.PRIORITY_HIGH)   // ← kunci heads-up
            .setDefaults(NotificationCompat.DEFAULT_ALL)     // suara + getar
            // ─────────────────────────────────────────────────────────────────
            .setColor(context.getColor(R.color.primary))
            .setColorized(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIF_ID, notification)
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                // IMPORTANCE_HIGH wajib agar heads-up muncul di Android 8+
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description        = "Notifikasi ketika ada versi baru aplikasi"
                enableVibration(true)
                enableLights(true)
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }
}