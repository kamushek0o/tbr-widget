package com.tbrpro.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TbrWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        renderFromCache(ctx, null)
        WidgetWorker.runNow(ctx)
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        when (intent.action) {
            ACTION_REFRESH -> WidgetWorker.runNow(ctx)
            ACTION_TOGGLE_CHART -> {
                val nextMode = when (Prefs.getChartMode(ctx)) {
                    "1" -> "2"
                    "2" -> "3"
                    "3" -> "all"
                    else -> "1"
                }
                Prefs.setChartMode(ctx, nextMode)
                renderFromCache(ctx, null)
            }
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.tbrpro.widget.ACTION_REFRESH"
        const val ACTION_TOGGLE_CHART = "com.tbrpro.widget.ACTION_TOGGLE_CHART"

        fun renderFromCache(ctx: Context, errorMsg: String?) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, TbrWidgetProvider::class.java))
            if (ids.isEmpty()) return

            val data = ApiClient.parseWidgetJson(Prefs.loadCachedJson(ctx))
            val mode = Prefs.getChartMode(ctx)
            val views = RemoteViews(ctx.packageName, R.layout.widget_tbr)
            val nowStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

            val modeBadge = when (mode) {
                "1" -> "1/3"
                "2" -> "2/3"
                "3" -> "3/3"
                else -> "Все"
            }
            views.setTextViewText(R.id.btn_chart_toggle, modeBadge)

            if (data != null) {
                views.setTextViewText(R.id.tv_qb_badge, if (data.qbRunning) "● qB OK" else "● qB OFF")
                views.setTextColor(R.id.tv_qb_badge, Color.parseColor(if (data.qbRunning) "#2ECC71" else "#E74C3C"))
                views.setTextViewText(R.id.tv_speed, data.speedFmt)
                views.setTextViewText(R.id.tv_24h, data.up24hFmt)
                views.setTextViewText(R.id.tv_disk, data.diskFreeFmt)

                when (mode) {
                    "1" -> {
                        views.setImageViewBitmap(R.id.iv_chart_1, WidgetChartRenderer.renderSpeed(data.speedSeries, 540, 200))
                        views.setViewVisibility(R.id.iv_chart_1, View.VISIBLE)
                        views.setViewVisibility(R.id.iv_chart_2, View.GONE)
                        views.setViewVisibility(R.id.iv_chart_3, View.GONE)
                    }
                    "2" -> {
                        views.setImageViewBitmap(R.id.iv_chart_1, WidgetChartRenderer.renderDaily(data.dailySeries, 540, 200))
                        views.setViewVisibility(R.id.iv_chart_1, View.VISIBLE)
                        views.setViewVisibility(R.id.iv_chart_2, View.GONE)
                        views.setViewVisibility(R.id.iv_chart_3, View.GONE)
                    }
                    "3" -> {
                        views.setImageViewBitmap(R.id.iv_chart_1, WidgetChartRenderer.renderTop(data.topSeries, 540, 200, data.topPeriod, data.topScale))
                        views.setViewVisibility(R.id.iv_chart_1, View.VISIBLE)
                        views.setViewVisibility(R.id.iv_chart_2, View.GONE)
                        views.setViewVisibility(R.id.iv_chart_3, View.GONE)
                    }
                    else -> {
                        views.setImageViewBitmap(R.id.iv_chart_1, WidgetChartRenderer.renderSpeed(data.speedSeries, 500, 125))
                        views.setImageViewBitmap(R.id.iv_chart_2, WidgetChartRenderer.renderDaily(data.dailySeries, 500, 125))
                        views.setImageViewBitmap(R.id.iv_chart_3, WidgetChartRenderer.renderTop(data.topSeries, 500, 140, data.topPeriod, data.topScale))
                        views.setViewVisibility(R.id.iv_chart_1, View.VISIBLE)
                        views.setViewVisibility(R.id.iv_chart_2, View.VISIBLE)
                        views.setViewVisibility(R.id.iv_chart_3, View.VISIBLE)
                    }
                }

                val statusTxt = if (!errorMsg.isNullOrBlank()) "⚠️ $errorMsg • $nowStr" else "Раздач: ${data.activeTorrents}/${data.totalTorrents} • $nowStr"
                views.setTextViewText(R.id.tv_footer, statusTxt)
            } else if (!errorMsg.isNullOrBlank()) {
                views.setTextViewText(R.id.tv_footer, "⚠️ $errorMsg ($nowStr)")
            }

            val refIntent = Intent(ctx, TbrWidgetProvider::class.java).apply { action = ACTION_REFRESH }
            val refPi = PendingIntent.getBroadcast(ctx, 101, refIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.btn_refresh, refPi)

            val toggleIntent = Intent(ctx, TbrWidgetProvider::class.java).apply { action = ACTION_TOGGLE_CHART }
            val togglePi = PendingIntent.getBroadcast(ctx, 103, toggleIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.btn_chart_toggle, togglePi)

            val url = Prefs.getUrl(ctx)
            val tapIntent = if (url.isNotBlank()) Intent(Intent.ACTION_VIEW, Uri.parse(url)) else Intent(ctx, MainActivity::class.java)
            val tapPi = PendingIntent.getActivity(ctx, 102, tapIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_root, tapPi)

            mgr.updateAppWidget(ids, views)
        }
    }
}`/