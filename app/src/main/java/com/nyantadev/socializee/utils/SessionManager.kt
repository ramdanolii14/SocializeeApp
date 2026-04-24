package com.nyantadev.socializee.utils

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.nyantadev.socializee.api.RetrofitClient
import com.nyantadev.socializee.models.User

class SessionManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val PREF_NAME = "SocializeeSession"
        private const val KEY_TOKEN = "auth_token"
        private const val KEY_USER = "current_user"
        private const val KEY_USER_ID = "user_id"
    }

    fun saveToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    fun saveUser(user: User) {
        prefs.edit()
            .putString(KEY_USER, gson.toJson(user))
            .putString(KEY_USER_ID, user.id)
            .apply()
    }

    fun getUser(): User? {
        val json = prefs.getString(KEY_USER, null) ?: return null
        return gson.fromJson(json, User::class.java)
    }

    fun getUserId(): String? = prefs.getString(KEY_USER_ID, null)

    fun isLoggedIn(): Boolean = getToken() != null

    fun logout() {
        prefs.edit().clear().apply()
        RetrofitClient.reset()
    }
}
