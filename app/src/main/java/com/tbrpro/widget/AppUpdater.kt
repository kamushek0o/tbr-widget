package com.tbrpro.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class UpdateReleaseInfo(
    val currentVersion: String,
    val latestVersion: String,
    val updateAvailable: Boolean,
    val apkUrl: String,
    val sha256: String,
    val changelog: String
)

object AppUpdater {
    private const val GITHUB_LATEST_URL = "https://api.github.com/repos/kamushek0o/tbr-widget/releases/latest"
    private val SEMVER_REGEX = Regex("""(\d+)\.(\d+)\.(\d+)""")

    fun getCurrentVersion(ctx: Context): String {
        return try {
            @Suppress("DEPRECATION")
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "1.3.0"
        } catch (_: Exception) {
            "1.3.0"
        }
    }

    private fun parseSemver(ver: String): Triple<Int, Int, Int> {
        val m = SEMVER_REGEX.find(ver.trim().trimStart('v', 'V')) ?: return Triple(0, 0, 0)
        return Triple(
            m.groupValues[1].toIntOrNull() ?: 0,
            m.groupValues[2].toIntOrNull() ?: 0,
            m.groupValues[3].toIntOrNull() ?: 0
        )
    }

    private fun isNewer(remote: String, local: String): Boolean {
        val r = parseSemver(remote)
        val l = parseSemver(local)
        if (r.first != l.first) return r.first > l.first
        if (r.second != l.second) return r.second > l.second
        return r.third > l.third
    }

    fun checkLatestRelease(ctx: Context): Result<UpdateReleaseInfo> {
        val curVer = getCurrentVersion(ctx)
        return try {
            val conn = (URL(GITHUB_LATEST_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("User-Agent", "tbr-widget/$curVer")
                setRequestProperty("Accept", "application/vnd.github+json")
            }
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream).bufferedReader().use { it.readText() }
            conn.disconnect()
            if (code !in 200..299) throw IllegalStateException("GitHub API HTTP $code")

            val rel = JSONObject(body)
            val tag = rel.optString("tag_name", curVer).trimStart('v', 'V')
            val assets = rel.optJSONArray("assets")
            var apkUrl = ""
            var sha256 = ""
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.optJSONObject(i) ?: continue
                    val nm = a.optString("name", "").lowercase()
                    if (nm.endsWith(".apk")) {
                        apkUrl = a.optString("browser_download_url", "")
                        val digest = a.optString("digest", "")
                        if (digest.startsWith("sha256:", ignoreCase = true)) {
                            sha256 = digest.substringAfter(":").trim().lowercase()
                        }
                        break
                    }
                }
            }

            Result.success(
                UpdateReleaseInfo(
                    currentVersion = curVer,
                    latestVersion = tag.ifBlank { curVer },
                    updateAvailable = isNewer(tag, curVer) && apkUrl.isNotBlank(),
                    apkUrl = apkUrl,
                    sha256 = sha256,
                    changelog = rel.optString("body", "")
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun downloadAndInstallApk(ctx: Context, info: UpdateReleaseInfo): Result<String> {
        if (info.apkUrl.isBlank()) {
            return Result.failure(IllegalStateException("APK файл не найден в релизе"))
        }
        val updatesDir = File(ctx.filesDir, "updates").apply { mkdirs() }
        val apkFile = File(updatesDir, "tbr-widget-update.apk")
        return try {
            val conn = (URL(info.apkUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 30000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "tbr-widget/${info.currentVersion}")
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                conn.disconnect()
                throw IllegalStateException("Ошибка скачивания HTTP $code")
            }

            val digest = MessageDigest.getInstance("SHA-256")
            conn.inputStream.use { input ->
                apkFile.outputStream().use { out ->
                    val buf = ByteArray(16384)
                    while (true) {
                        val read = input.read(buf)
                        if (read <= 0) break
                        out.write(buf, 0, read)
                        digest.update(buf, 0, read)
                    }
                }
            }
            conn.disconnect()

            if (info.sha256.isNotBlank()) {
                val actualHex = digest.digest().joinToString("") { "%02x".format(it) }
                if (!actualHex.equals(info.sha256, ignoreCase = true)) {
                    apkFile.delete()
                    throw IllegalStateException("SHA-256 не совпал! Файл удалён.")
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !ctx.packageManager.canRequestPackageInstalls()) {
                val permIntent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${ctx.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(permIntent)
                return Result.success("Разрешите установку из этого источника в настройках и нажмите «Обновить» снова.")
            }

            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", apkFile)
            @Suppress("DEPRECATION")
            val installIntent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            }
            ctx.startActivity(installIntent)
            Result.success("Запущен установщик v${info.latestVersion}...")
        } catch (e: Exception) {
            if (apkFile.exists()) apkFile.delete()
            Result.failure(e)
        }
    }
}