package com.tbrpro.widget

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File

object Prefs {
    private const val SECURE_FILE = "tbr_widget_secure"
    private const val PLAIN_FILE = "tbr_widget_plain"
    private const val CACHE_FILE = "widget_data.json"

    private fun getPrefs(ctx: Context): SharedPreferences {
        return try {
            val mk = MasterKey.Builder(ctx)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                ctx, SECURE_FILE, mk,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (_: Exception) {
            ctx.getSharedPreferences(PLAIN_FILE, Context.MODE_PRIVATE)
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

    fun getChartMode(ctx: Context): String = getPrefs(ctx).getString("chart_mode", "1") ?: "1"
    fun setChartMode(ctx: Context, mode: String) {
        getPrefs(ctx).edit().putString("chart_mode", mode).apply()
    }

    fun saveCachedJson(ctx: Context, rawJson: String) {
        try {
            File(ctx.cacheDir, CACHE_FILE).writeText(rawJson, Charsets.UTF_8)
        } catch (_: Exception) {
        }
    }

    fun loadCachedJson(ctx: Context): String? {
        return try {
            val f = File(ctx.cacheDir, CACHE_FILE)
            if (f.exists()) f.readText(Charsets.UTF_8) else null
        } catch (_: Exception) {
            null
        }
    }
}