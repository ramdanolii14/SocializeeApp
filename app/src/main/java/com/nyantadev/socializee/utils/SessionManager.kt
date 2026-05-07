package com.nyantadev.socializee.utils

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.nyantadev.socializee.api.RetrofitClient
import com.nyantadev.socializee.models.User

class SessionManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val PREF_NAME    = "SocializeeSession"
        private const val KEY_TOKEN    = "auth_token"
        private const val KEY_USER     = "current_user"
        private const val KEY_USER_ID  = "user_id"
        private const val KEY_ROLE     = "user_role"
        private const val KEY_DARK     = "dark_mode"   // [NEW] tema gelap
    }

    // ── Token ─────────────────────────────────────────────────────────────
    fun saveToken(token: String) = prefs.edit().putString(KEY_TOKEN, token).apply()
    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    // ── User ──────────────────────────────────────────────────────────────
    fun saveUser(user: User) {
        prefs.edit()
            .putString(KEY_USER, gson.toJson(user))
            .putString(KEY_USER_ID, user.id)
            .putString(KEY_ROLE, user.role ?: "user")
            .apply()
    }

    fun getUser(): User? {
        val json = prefs.getString(KEY_USER, null) ?: return null
        return gson.fromJson(json, User::class.java)
    }

    fun getUserId(): String? = prefs.getString(KEY_USER_ID, null)

    fun isAdmin(): Boolean = prefs.getString(KEY_ROLE, "user") == "admin"

    // ── Session ───────────────────────────────────────────────────────────
    fun isLoggedIn(): Boolean = getToken() != null

    fun logout() {
        // Pertahankan preferensi tema saat logout agar tidak reset
        val darkPref = prefs.getBoolean(KEY_DARK, false)
        prefs.edit().clear().putBoolean(KEY_DARK, darkPref).apply()
        RetrofitClient.reset()
    }

    // ── [NEW] Dark mode preference ────────────────────────────────────────
    /**
     * Simpan pilihan tema gelap/terang.
     * Dipanggil dari dialog Settings di ProfileFragment.
     */
    fun saveDarkMode(isDark: Boolean) =
        prefs.edit().putBoolean(KEY_DARK, isDark).apply()

    /**
     * Kembalikan preferensi tema.
     * Dipanggil di SocializeeApp.onCreate() untuk restore tema sebelum UI dibuat.
     */
    fun isDarkMode(): Boolean = prefs.getBoolean(KEY_DARK, false)
}