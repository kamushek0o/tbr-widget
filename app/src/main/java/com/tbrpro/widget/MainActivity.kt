package com.tbrpro.widget

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
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
        val tvStatus = findViewById<TextView>(R.id.tv_status)

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