package com.tbrpro.widget

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import org.json.JSONObject
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private val intervals = listOf(15L, 30L, 60L)
    private var latestUpdateInfo: UpdateReleaseInfo? = null

    private val qrScannerLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            handleScannedQr(result.contents)
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchQrScanner()
        else findViewById<TextView>(R.id.tv_status).text = "⚠️ Разрешение на камеру не предоставлено"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val etUrl = findViewById<EditText>(R.id.et_url)
        val etToken = findViewById<EditText>(R.id.et_token)
        val etLogin = findViewById<EditText>(R.id.et_login)
        val etPassword = findViewById<EditText>(R.id.et_password)
        val spInt = findViewById<Spinner>(R.id.sp_interval)
        val btnScan = findViewById<Button>(R.id.btn_scan_qr)
        val btnExchange = findViewById<Button>(R.id.btn_exchange)
        val btnSave = findViewById<Button>(R.id.btn_save)
        val btnCheckUpd = findViewById<Button>(R.id.btn_check_update)
        val btnUpdNow = findViewById<Button>(R.id.btn_update_now)
        val tvVersion = findViewById<TextView>(R.id.tv_app_version)
        val tvStatus = findViewById<TextView>(R.id.tv_status)

        tvVersion.text = "Версия виджета: v${AppUpdater.getCurrentVersion(this)}"

        spInt.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            listOf("Каждые 15 мин", "Каждые 30 мин", "Каждый 1 час")
        )

        etUrl.setText(Prefs.getUrl(this))
        etToken.setText(Prefs.getToken(this))
        spInt.setSelection(intervals.indexOf(Prefs.getIntervalMin(this)).coerceAtLeast(0))

        btnScan.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                launchQrScanner()
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        btnExchange.setOnClickListener {
            val url = etUrl.text.toString().trim().trimEnd('/')
            val login = etLogin.text.toString().trim()
            val pwd = etPassword.text.toString().trim()
            if (url.isBlank() || login.isBlank() || pwd.isBlank()) {
                tvStatus.text = "❌ Укажите URL, логин и пароль"
                return@setOnClickListener
            }
            btnExchange.isEnabled = false
            tvStatus.text = "⏳ Получение API-токена..."
            thread {
                val res = ApiClient.exchangeLoginPassword(url, login, pwd)
                runOnUiThread {
                    btnExchange.isEnabled = true
                    res.onSuccess { tok ->
                        etToken.setText(tok)
                        etPassword.setText("")
                        tvStatus.text = "✅ Токен получен! Нажмите «Проверить и сохранить»"
                    }.onFailure { err ->
                        tvStatus.text = "❌ Ошибка входа: ${err.message}"
                    }
                }
            }
        }

        btnSave.setOnClickListener {
            val url = etUrl.text.toString().trim().trimEnd('/')
            val token = etToken.text.toString().trim()
            val interval = intervals[spInt.selectedItemPosition]

            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                tvStatus.text = "❌ URL должен начинаться с http:// или https://"
                return@setOnClickListener
            }

            btnSave.isEnabled = false
            tvStatus.text = "⏳ Проверка подключения к $url..."

            thread {
                val res = ApiClient.fetchWidgetJson(url, token)
                runOnUiThread {
                    btnSave.isEnabled = true
                    res.onSuccess { rawJson ->
                        val payload = ApiClient.parseWidgetJson(rawJson)
                        Prefs.saveConfig(this, url, token, interval)
                        Prefs.saveCachedJson(this, rawJson)
                        WidgetWorker.schedulePeriodic(this, interval)
                        TbrWidgetProvider.renderFromCache(this, null)
                        val spd = payload?.speedFmt ?: "-"
                        val up24 = payload?.up24hFmt ?: "-"
                        tvStatus.text = "✅ Подключено! Скорость: $spd, за 24ч: $up24.\nКэш обновлён."
                    }.onFailure { err ->
                        tvStatus.text = "❌ Ошибка: ${err.message}"
                    }
                }
            }
        }

        btnCheckUpd.setOnClickListener {
            checkForWidgetUpdates(manual = true)
        }

        btnUpdNow.setOnClickListener {
            val info = latestUpdateInfo ?: return@setOnClickListener
            btnUpdNow.isEnabled = false
            tvStatus.text = "⏳ Скачивание tbr-widget v${info.latestVersion}..."
            thread {
                val res = AppUpdater.downloadAndInstallApk(this, info)
                runOnUiThread {
                    btnUpdNow.isEnabled = true
                    res.onSuccess { msg -> tvStatus.text = "🚀 $msg" }
                        .onFailure { err -> tvStatus.text = "❌ Ошибка обновления: ${err.message}" }
                }
            }
        }

        checkForWidgetUpdates(manual = false)
    }

    private fun checkForWidgetUpdates(manual: Boolean) {
        val banner = findViewById<LinearLayout>(R.id.banner_update)
        val tvBanner = findViewById<TextView>(R.id.tv_update_banner)
        val tvStatus = findViewById<TextView>(R.id.tv_status)
        if (manual) tvStatus.text = "⏳ Проверяем обновления на GitHub..."

        thread {
            val res = AppUpdater.checkLatestRelease(this)
            runOnUiThread {
                res.onSuccess { info ->
                    latestUpdateInfo = info
                    if (info.updateAvailable) {
                        banner.visibility = View.VISIBLE
                        tvBanner.text = "⬆️ Доступно обновление v${info.latestVersion}"
                        if (manual) tvStatus.text = "🔥 Доступен релиз v${info.latestVersion}! Нажмите «Обновить» сверху."
                    } else {
                        banner.visibility = View.GONE
                        if (manual) tvStatus.text = "✅ Установлена актуальная версия v${info.currentVersion}."
                    }
                }.onFailure { err ->
                    if (manual) tvStatus.text = "❌ Не удалось проверить обновления: ${err.message}"
                }
            }
        }
    }

    private fun launchQrScanner() {
        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt("Наведите камеру на QR-код из настроек tbr PRO")
            setBeepEnabled(false)
            setOrientationLocked(false)
        }
        qrScannerLauncher.launch(options)
    }

    private fun handleScannedQr(contents: String) {
        val tvStatus = findViewById<TextView>(R.id.tv_status)
        try {
            val obj = JSONObject(contents)
            val url = obj.optString("url", "")
            val token = obj.optString("token", "")
            if (url.isNotBlank()) findViewById<EditText>(R.id.et_url).setText(url)
            if (token.isNotBlank()) findViewById<EditText>(R.id.et_token).setText(token)
            tvStatus.text = "✅ Параметры подставлены из QR! Нажмите «Проверить и сохранить»"
        } catch (_: Exception) {
            if (contents.startsWith("http://") || contents.startsWith("https://")) {
                findViewById<EditText>(R.id.et_url).setText(contents.trimEnd('/'))
                tvStatus.text = "✅ URL подставлен из QR! Введите токен и сохраните"
            } else {
                tvStatus.text = "⚠️ Неизвестный формат QR-кода"
            }
        }
    }
}