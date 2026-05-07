package com.nyantadev.socializee

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.color.DynamicColors
import com.nyantadev.socializee.utils.SessionManager

/**
 * Application class.
 *
 * Tugas:
 *  1. Aktifkan Material You Dynamic Color (Android 12+).
 *  2. Terapkan tema gelap/terang yang tersimpan di SessionManager
 *     sebelum Activity manapun dibuat.
 */
class SocializeeApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // ── 1. Material You — Dynamic Color ──────────────────────────────
        // Android 12+ : warna di-generate dari wallpaper user.
        // Android <12  : tidak berlaku, gunakan seed color dari themes.xml.
        DynamicColors.applyToActivitiesIfAvailable(this)

        // ── 2. Restore tema yang tersimpan ────────────────────────────────
        val sessionManager = SessionManager(this)
        AppCompatDelegate.setDefaultNightMode(
            if (sessionManager.isDarkMode()) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )
    }
}