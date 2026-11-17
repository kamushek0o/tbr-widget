package com.tbrpro.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TbrWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        WidgetWorker.runNow(ctx)
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        if (intent.action == ACTION_REFRESH) {
            WidgetWorker.runNow(ctx)
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.tbrpro.widget.ACTION_REFRESH"

        fun updateAllWidgets(ctx: Context, data: WidgetPayload?, errorMsg: String?) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, TbrWidgetProvider::class.java))
            if (ids.isEmpty()) return

            val views = RemoteViews(ctx.packageName, R.layout.widget_tbr)
            val nowStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

            if (data != null) {
                views.setTextViewText(R.id.tv_qb_badge, if (data.qbRunning) "● qB OK" else "● qB OFF")
                views.setTextColor(R.id.tv_qb_badge, Color.parseColor(if (data.qbRunning) "#2ECC71" else "#E74C3C"))
                views.setTextViewText(R.id.tv_speed, data.speedFmt)
                views.setTextViewText(R.id.tv_24h, data.up24hFmt)
                views.setTextViewText(R.id.tv_disk, data.diskFreeFmt)
                if (data.chart != null) {
                    views.setImageViewBitmap(R.id.iv_chart, data.chart)
                }
                views.setTextViewText(R.id.tv_footer, "Раздач: ${data.activeTorrents}/${data.totalTorrents} • $nowStr")
            } else if (!errorMsg.isNullOrBlank()) {
                views.setTextViewText(R.id.tv_footer, "⚠️ $errorMsg ($nowStr)")
            }

            // Кнопка ↻ принудительного обновления
            val refIntent = Intent(ctx, TbrWidgetProvider::class.java).apply { action = ACTION_REFRESH }
            val refPi = PendingIntent.getBroadcast(
                ctx, 1, refIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_refresh, refPi)

            // Тап по телу виджета открывает PWA/веб-дашборд
            val url = Prefs.getUrl(ctx)
            val tapIntent = if (url.isNotBlank()) {
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
            } else {
                Intent(ctx, MainActivity::class.java)
            }
            val tapPi = PendingIntent.getActivity(
                ctx, 2, tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, tapPi)

            mgr.updateAppWidget(ids, views)
        }
    }
}