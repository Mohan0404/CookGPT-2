package com.example.cookgpt

import android.content.Context
import android.content.SharedPreferences

object SessionManager {
    private const val PREFS_NAME = "session_prefs"
    private const val KEY_IS_LOGGED_IN = "is_logged_in"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_IS_NEW_USER = "is_new_user"

    fun isLoggedIn(context: Context): Boolean =
        prefs(context).getBoolean(KEY_IS_LOGGED_IN, false)

    fun setLogin(context: Context, userId: String) {
        prefs(context).edit()
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .putBoolean(KEY_IS_NEW_USER, false)
            .putString(KEY_USER_ID, userId)
            .apply()
    }

    fun getUserId(context: Context): String =
        prefs(context).getString(KEY_USER_ID, "") ?: ""

    fun setNewUser(context: Context, userId: String) {
        prefs(context).edit()
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .putBoolean(KEY_IS_NEW_USER, true)
            .putString(KEY_USER_ID, userId)
            .apply()
    }

    fun isNewUser(context: Context): Boolean =
        prefs(context).getBoolean(KEY_IS_NEW_USER, false)

    fun logout(context: Context) {
        prefs(context).edit().clear().apply()
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
