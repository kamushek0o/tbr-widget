package com.tbrpro.widget

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class WidgetWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        val url = Prefs.getUrl(applicationContext)
        val token = Prefs.getToken(applicationContext)
        if (url.isBlank()) return Result.success()

        val chartType = Prefs.getChartType(applicationContext)
        val res = ApiClient.fetchWidgetData(url, token, chartType)
        res.onSuccess { payload ->
            TbrWidgetProvider.updateAllWidgets(applicationContext, payload, null)
        }.onFailure { err ->
            TbrWidgetProvider.updateAllWidgets(applicationContext, null, err.message ?: "Ошибка сети")
        }
        return Result.success()
    }

    companion object {
        private const val PERIODIC_NAME = "tbr_widget_periodic"

        fun schedulePeriodic(ctx: Context, intervalMin: Long) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val req = PeriodicWorkRequestBuilder<WidgetWorker>(
                intervalMin.coerceAtLeast(15L), TimeUnit.MINUTES
            ).setConstraints(constraints).build()

            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                PERIODIC_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                req
            )
        }

        fun runNow(ctx: Context) {
            val req = OneTimeWorkRequestBuilder<WidgetWorker>().build()
            WorkManager.getInstance(ctx).enqueue(req)
        }
    }
}