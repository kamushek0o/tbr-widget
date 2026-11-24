package com.tbrpro.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import java.util.Locale
import kotlin.math.log10

object WidgetChartRenderer {
    private val BG_COLOR = Color.rgb(26, 26, 32)
    private val GRID_COLOR = Color.rgb(44, 44, 54)
    private val TEXT_MUTED = Color.rgb(149, 165, 166)
    private val TEXT_MAIN = Color.rgb(235, 238, 242)

    private fun newBitmap(w: Int, h: Int): Bitmap {
        val safeW = w.coerceIn(300, 960)
        val safeH = h.coerceIn(120, 460)
        return Bitmap.createBitmap(safeW, safeH, Bitmap.Config.RGB_565).also {
            Canvas(it).drawColor(BG_COLOR)
        }
    }

    private fun drawHeaderAndNoData(canvas: Canvas, title: String, w: Int, h: Int, empty: Boolean) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_MUTED
            textSize = (h * 0.105f).coerceIn(18f, 28f)
        }
        canvas.drawText(title, 20f, p.textSize + 8f, p)
        if (empty) {
            p.textAlign = Paint.Align.CENTER
            canvas.drawText("Нет данных", w / 2f, h / 2f + p.textSize / 2f, p)
        }
    }

    private fun drawGrid(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float) {
        val gridP = Paint().apply { color = GRID_COLOR; strokeWidth = 1.8f }
        for (i in 0..3) {
            val y = top + (bottom - top) * (i / 3f)
            canvas.drawLine(left, y, right, y, gridP)
        }
    }

    fun renderSpeed(points: List<SpeedPt>, w: Int, h: Int): Bitmap {
        val bmp = newBitmap(w, h)
        val canvas = Canvas(bmp)
        if (points.size < 2) {
            drawHeaderAndNoData(canvas, "⚡ Скорость (24ч)", bmp.width, bmp.height, true)
            return bmp
        }

        val maxVal = points.maxOfOrNull { it.speedMbs } ?: 0f
        val safeMax = if (maxVal > 0f) maxVal else 1f
        val title = String.format(Locale.US, "⚡ Скорость (пик: %.2f MiB/s)", maxVal)
        drawHeaderAndNoData(canvas, title, bmp.width, bmp.height, false)

        val padL = 20f; val padR = 20f
        val padT = (bmp.height * 0.22f).coerceAtLeast(36f)
        val padB = (bmp.height * 0.14f).coerceIn(26f, 38f)
        val plotW = (bmp.width - padL - padR).coerceAtLeast(10f)
        val plotH = (bmp.height - padT - padB).coerceAtLeast(10f)
        drawGrid(canvas, padL, padT, bmp.width - padR, padT + plotH)

        val linePath = Path()
        val fillPath = Path()
        val n = points.size - 1
        points.forEachIndexed { idx, pt ->
            val x = padL + (idx.toFloat() / n.toFloat()) * plotW
            val y = if (maxVal > 0f) {
                padT + plotH - ((pt.speedMbs.coerceAtLeast(0f) / safeMax) * plotH)
            } else {
                padT + plotH
            }
            if (idx == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, padT + plotH)
                fillPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
            if (idx == n) fillPath.lineTo(x, padT + plotH)
        }
        fillPath.close()

        if (maxVal > 0f) {
            canvas.drawPath(fillPath, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(65, 52, 152, 219)
                style = Paint.Style.FILL
            })
        }
        canvas.drawPath(linePath, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(52, 152, 219)
            strokeWidth = (bmp.height * 0.016f).coerceIn(3f, 5f)
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
        })

        val lblSize = (bmp.height * 0.085f).coerceIn(15f, 22f)
        val lblP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TEXT_MUTED; textSize = lblSize }
        canvas.drawText(points.first().label, padL, bmp.height - 8f, lblP)
        lblP.textAlign = Paint.Align.RIGHT
        canvas.drawText(points.last().label, bmp.width - padR, bmp.height - 8f, lblP)
        return bmp
    }

    fun renderDaily(bars: List<BarPt>, w: Int, h: Int): Bitmap {
        val bmp = newBitmap(w, h)
        val canvas = Canvas(bmp)
        if (bars.isEmpty()) {
            drawHeaderAndNoData(canvas, "📅 По дням (GiB)", bmp.width, bmp.height, true)
            return bmp
        }

        val maxVal = bars.maxOfOrNull { it.gb } ?: 0f
        val safeMax = if (maxVal > 0f) maxVal else 1f
        val title = String.format(Locale.US, "📅 По дням (макс: %.1f GiB)", maxVal)
        drawHeaderAndNoData(canvas, title, bmp.width, bmp.height, false)

        val padL = 20f; val padR = 20f
        val padT = (bmp.height * 0.22f).coerceAtLeast(36f)
        val padB = (bmp.height * 0.14f).coerceIn(26f, 38f)
        val plotW = (bmp.width - padL - padR).coerceAtLeast(10f)
        val plotH = (bmp.height - padT - padB).coerceAtLeast(10f)
        drawGrid(canvas, padL, padT, bmp.width - padR, padT + plotH)

        val barP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(46, 204, 113) }
        val lblSize = (bmp.height * 0.08f).coerceIn(14f, 21f)
        val lblP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TEXT_MUTED; textSize = lblSize; textAlign = Paint.Align.CENTER }
        val slotW = plotW / bars.size.toFloat()
        val barW = (slotW * 0.62f).coerceAtLeast(10f)

        bars.forEachIndexed { idx, b ->
            val cx = padL + (idx + 0.5f) * slotW
            val bh = if (maxVal > 0f) ((b.gb.coerceAtLeast(0f) / safeMax) * plotH) else 2f
            canvas.drawRoundRect(cx - barW / 2f, padT + plotH - bh, cx + barW / 2f, padT + plotH, 6f, 6f, barP)
            canvas.drawText(b.label, cx, bmp.height - 8f, lblP)
        }
        return bmp
    }

    fun renderTop(bars: List<BarPt>, w: Int, h: Int, period: String = "1d", scale: String = "lin"): Bitmap {
        val bmp = newBitmap(w, h)
        val canvas = Canvas(bmp)
        val isLog = (scale == "log10")
        val mapped = bars.map { b ->
            val safe = b.gb.coerceAtLeast(0f)
            if (isLog) log10(1f + safe) else safe
        }
        val maxPlot = mapped.maxOrNull() ?: 0f
        if (bars.isEmpty() || maxPlot <= 0f) {
            drawHeaderAndNoData(canvas, "🏆 Топ раздач", bmp.width, bmp.height, true)
            return bmp
        }

        val perLbl = when (period) { "7d" -> "7д"; "30d" -> "30д"; "all" -> "Всё"; else -> "1д" }
        val scLbl = if (isLog) "Log10" else "Lin"
        drawHeaderAndNoData(canvas, "🏆 Топ-${bars.size} ($perLbl • $scLbl, GiB)", bmp.width, bmp.height, false)

        val padL = 18f; val padR = 18f
        val padT = (bmp.height * 0.20f).coerceAtLeast(34f); val padB = 10f
        val nameColW = bmp.width * 0.45f
        val valColW = (bmp.width * 0.13f).coerceIn(66f, 96f)
        val barMaxW = (bmp.width - padL - padR - nameColW - valColW - 14f).coerceAtLeast(20f)
        val rowH = ((bmp.height - padT - padB) / bars.size.toFloat()).coerceAtLeast(18f)

        val txtP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TEXT_MAIN; textSize = (rowH * 0.54f).coerceIn(15f, 24f) }
        val valP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TEXT_MUTED; textSize = txtP.textSize * 0.9f; textAlign = Paint.Align.RIGHT }
        val barP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(230, 126, 34) }

        bars.forEachIndexed { idx, item ->
            val cy = padT + idx * rowH + rowH * 0.5f
            canvas.drawText(fitText(item.label, txtP, nameColW), padL, cy + txtP.textSize * 0.35f, txtP)

            val barLeft = padL + nameColW + 8f
            val ratio = (mapped[idx] / maxPlot).takeIf { it.isFinite() } ?: 0f
            val bw = (ratio * barMaxW).coerceAtLeast(6f)
            val barHalfH = (rowH * 0.28f).coerceAtLeast(5f)
            canvas.drawRoundRect(barLeft, cy - barHalfH, barLeft + bw, cy + barHalfH, 5f, 5f, barP)

            val valStr = String.format(Locale.US, "%.1fG", item.gb.coerceAtLeast(0f))
            canvas.drawText(valStr, bmp.width - padR, cy + valP.textSize * 0.35f, valP)
        }
        return bmp
    }

    private fun fitText(raw: String, paint: Paint, maxWidth: Float): String {
        val count = paint.breakText(raw, true, maxWidth, null)
        if (count >= raw.length) return raw
        return raw.substring(0, (count - 1).coerceAtLeast(1)) + "…"
    }
}