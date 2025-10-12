package org.fossify.phone.helpers

import android.content.Context
import android.content.SharedPreferences

object AuthHelper {
    private const val PREF_NAME = "auth"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_USER_EMAIL = "user_email"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_CALL_DETAILS_ID = "call_details_id"

    /**
     * Check if user is authenticated
     */
    fun isUserAuthenticated(context: Context): Boolean {
        val sharedPref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val accessToken = sharedPref.getString(KEY_ACCESS_TOKEN, null)
        return !accessToken.isNullOrEmpty()
    }

    /**
     * Get user access token
     */
    fun getAccessToken(context: Context): String? {
        val sharedPref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return sharedPref.getString(KEY_ACCESS_TOKEN, null)
    }

    /**
     * Get user email
     */
    fun getUserEmail(context: Context): String? {
        val sharedPref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return sharedPref.getString(KEY_USER_EMAIL, null)
    }

    /**
     * Get user name
     */
    fun getUserName(context: Context): String? {
        val sharedPref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return sharedPref.getString(KEY_USER_NAME, null)
    }

    /**
     * Get user ID
     */
    fun getUserId(context: Context): Int {
        val sharedPref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return sharedPref.getInt(KEY_USER_ID, -1)
    }

    /**
     * Save Call Details Id
     */
    fun saveCallDetailsId(context: Context, callDetailsId: Int) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_CALL_DETAILS_ID, callDetailsId).apply()
    }

    /**
     * Get Call Details Id
     */
    fun getCallDetailsId(context: Context): Int {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_CALL_DETAILS_ID, -1)
    }

    /**
     * Save authentication data
     */
    fun saveAuthData(context: Context, token: String, email: String, name: String, userId: Int) {
        val sharedPref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString(KEY_ACCESS_TOKEN, token)
            putString(KEY_USER_EMAIL, email)
            putString(KEY_USER_NAME, name)
            putInt(KEY_USER_ID, userId)
            apply()
        }
    }

    /**
     * Clear all authentication data (logout)
     */
    fun logout(context: Context) {
        val sharedPref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            clear()
            apply()
        }

    }

    /**
     * Check if user has valid authentication data
     */
    fun isValidAuthData(context: Context): Boolean {
        return isUserAuthenticated(context) &&
               !getUserEmail(context).isNullOrEmpty() &&
               !getUserName(context).isNullOrEmpty() &&
               getUserId(context) > 0
    }
}
