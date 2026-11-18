package com.tbrpro.widget

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private val intervals = listOf(15L, 30L, 60L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val etUrl = findViewById<EditText>(R.id.et_url)
        val etToken = findViewById<EditText>(R.id.et_token)
        val spInt = findViewById<Spinner>(R.id.sp_interval)
        val btnSave = findViewById<Button>(R.id.btn_save)
        val tvStatus = findViewById<TextView>(R.id.tv_status)

        spInt.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf("Каждые 15 мин", "Каждые 30 мин", "Каждый 1 час")
        )

        etUrl.setText(Prefs.getUrl(this))
        etToken.setText(Prefs.getToken(this))
        val curInt = Prefs.getIntervalMin(this)
        spInt.setSelection(intervals.indexOf(curInt).coerceAtLeast(0))

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
                        tvStatus.text = "✅ Подключено! Скорость: $spd, за 24ч: $up24.\nКэш сохранён — добавляйте виджет на рабочий стол."
                    }.onFailure { err ->
                        tvStatus.text = "❌ Ошибка: ${err.message}"
                    }
                }
            }
        }
    }
}