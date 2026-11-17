package com.tbrpro.widget

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class WidgetPayload(
    val qbRunning: Boolean,
    val activeTorrents: Int,
    val totalTorrents: Int,
    val speedFmt: String,
    val up24hFmt: String,
    val diskFreeFmt: String,
    val chart: Bitmap?
)

object ApiClient {
    fun fetchWidgetData(baseUrl: String, token: String): Result<WidgetPayload> {
        return try {
            val jsonStr = httpGetText("$baseUrl/api/widget", token)
            val obj = JSONObject(jsonStr)
            val bmp = try {
                httpGetBitmap("$baseUrl/api/widget/chart.png?hours=24&w=600&h=200", token)
            } catch (_: Exception) {
                null
            }
            Result.success(
                WidgetPayload(
                    qbRunning = obj.optBoolean("qb_running", false),
                    activeTorrents = obj.optInt("active_torrents", 0),
                    totalTorrents = obj.optInt("total_torrents", 0),
                    speedFmt = obj.optString("current_speed_fmt", "0 B/s"),
                    up24hFmt = obj.optString("uploaded_24h_fmt", "0 B"),
                    diskFreeFmt = obj.optString("disk_free_fmt", "0 B"),
                    chart = bmp
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun openConn(urlStr: String, token: String): HttpURLConnection {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8000
            readTimeout = 8000
            if (token.isNotBlank()) setRequestProperty("Authorization", "Bearer $token")
        }
        val code = conn.responseCode
        if (code !in 200..299) {
            conn.disconnect()
            throw IllegalStateException("HTTP $code")
        }
        return conn
    }

    private fun httpGetText(urlStr: String, token: String): String {
        val conn = openConn(urlStr, token)
        return try {
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun httpGetBitmap(urlStr: String, token: String): Bitmap? {
        val conn = openConn(urlStr, token)
        return try {
            conn.inputStream.use { BitmapFactory.decodeStream(it) }
        } finally {
            conn.disconnect()
        }
    }
}