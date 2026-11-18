package com.tbrpro.widget

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class SpeedPt(val label: String, val speedMbs: Float)
data class BarPt(val label: String, val gb: Float)

data class WidgetData(
    val qbRunning: Boolean,
    val activeTorrents: Int,
    val totalTorrents: Int,
    val speedFmt: String,
    val up24hFmt: String,
    val diskFreeFmt: String,
    val speedSeries: List<SpeedPt>,
    val dailySeries: List<BarPt>,
    val topSeries: List<BarPt>
)

object ApiClient {
    fun fetchWidgetJson(baseUrl: String, token: String): Result<String> {
        return try {
            val conn = (URL("$baseUrl/api/widget").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                if (token.isNotBlank()) setRequestProperty("Authorization", "Bearer $token")
            }
            try {
                val code = conn.responseCode
                if (code !in 200..299) {
                    throw IllegalStateException("HTTP $code")
                }
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                JSONObject(text)
                Result.success(text)
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun parseWidgetJson(rawJson: String?): WidgetData? {
        if (rawJson.isNullOrBlank()) return null
        return try {
            val obj = JSONObject(rawJson)
            val charts = obj.optJSONObject("charts")

            val speeds = mutableListOf<SpeedPt>()
            val spArr = charts?.optJSONArray("speed_24h")
            if (spArr != null) {
                for (i in 0 until spArr.length()) {
                    val item = spArr.optJSONObject(i) ?: continue
                    speeds.add(SpeedPt(item.optString("label", ""), item.optDouble("speed_mbs", 0.0).toFloat()))
                }
            }

            val dailies = mutableListOf<BarPt>()
            val dlArr = charts?.optJSONArray("daily_7d")
            if (dlArr != null) {
                for (i in 0 until dlArr.length()) {
                    val item = dlArr.optJSONObject(i) ?: continue
                    dailies.add(BarPt(item.optString("label", ""), item.optDouble("gb", 0.0).toFloat()))
                }
            }

            val tops = mutableListOf<BarPt>()
            val topArr = charts?.optJSONArray("top_5")
            if (topArr != null) {
                for (i in 0 until topArr.length()) {
                    val item = topArr.optJSONObject(i) ?: continue
                    tops.add(BarPt(item.optString("name", ""), item.optDouble("gb", 0.0).toFloat()))
                }
            }

            WidgetData(
                qbRunning = obj.optBoolean("qb_running", false),
                activeTorrents = obj.optInt("active_torrents", 0),
                totalTorrents = obj.optInt("total_torrents", 0),
                speedFmt = obj.optString("current_speed_fmt", "0 B/s"),
                up24hFmt = obj.optString("uploaded_24h_fmt", "0 B"),
                diskFreeFmt = obj.optString("disk_free_fmt", "0 B"),
                speedSeries = speeds,
                dailySeries = dailies,
                topSeries = tops
            )
        } catch (_: Exception) {
            null
        }
    }
}