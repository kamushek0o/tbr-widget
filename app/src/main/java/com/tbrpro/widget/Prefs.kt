package com.tbrpro.widget

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object Prefs {
    private const val FILE_NAME = "tbr_widget_secure"

    private fun getPrefs(ctx: Context): SharedPreferences {
        return try {
            val mk = MasterKey.Builder(ctx)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                ctx, FILE_NAME, mk,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (_: Exception) {
            ctx.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
        }
    }

    fun saveConfig(ctx: Context, url: String, token: String, intervalMin: Long) {
        val cleanUrl = url.trim().trimEnd('/')
        getPrefs(ctx).edit()
            .putString("server_url", cleanUrl)
            .putString("token", token.trim())
            .putLong("interval_min", intervalMin.coerceAtLeast(15L))
            .apply()
    }

    fun getUrl(ctx: Context): String = getPrefs(ctx).getString("server_url", "") ?: ""
    fun getToken(ctx: Context): String = getPrefs(ctx).getString("token", "") ?: ""
    fun getIntervalMin(ctx: Context): Long = getPrefs(ctx).getLong("interval_min", 30L)
}