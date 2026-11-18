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
        val safeW = w.coerceIn(240, 560)
        val safeH = h.coerceIn(90, 220)
        return Bitmap.createBitmap(safeW, safeH, Bitmap.Config.RGB_565).also {
            Canvas(it).drawColor(BG_COLOR)
        }
    }

    private fun drawHeaderAndNoData(canvas: Canvas, title: String, w: Int, h: Int, empty: Boolean) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_MUTED
            textSize = (h * 0.11f).coerceIn(14f, 20f)
        }
        canvas.drawText(title, 16f, p.textSize + 6f, p)
        if (empty) {
            p.textAlign = Paint.Align.CENTER
            canvas.drawText("Нет данных", w / 2f, h / 2f + p.textSize / 2f, p)
        }
    }

    private fun drawGrid(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float) {
        val gridP = Paint().apply { color = GRID_COLOR; strokeWidth = 1.5f }
        for (i in 0..3) {
            val y = top + (bottom - top) * (i / 3f)
            canvas.drawLine(left, y, right, y, gridP)
        }
    }

    fun renderSpeed(points: List<SpeedPt>, w: Int, h: Int): Bitmap {
        val bmp = newBitmap(w, h)
        val canvas = Canvas(bmp)
        val maxVal = points.maxOfOrNull { it.speedMbs } ?: 0f
        if (points.size < 2 || maxVal <= 0f) {
            drawHeaderAndNoData(canvas, "⚡ Скорость (24ч)", bmp.width, bmp.height, true)
            return bmp
        }

        val title = String.format(Locale.US, "⚡ Скорость (пик: %.2f MiB/s)", maxVal)
        drawHeaderAndNoData(canvas, title, bmp.width, bmp.height, false)

        val padL = 16f; val padR = 16f
        val padT = (bmp.height * 0.22f).coerceAtLeast(26f); val padB = 24f
        val plotW = (bmp.width - padL - padR).coerceAtLeast(10f)
        val plotH = (bmp.height - padT - padB).coerceAtLeast(10f)
        drawGrid(canvas, padL, padT, bmp.width - padR, padT + plotH)

        val linePath = Path()
        val fillPath = Path()
        val n = points.size - 1
        points.forEachIndexed { idx, pt ->
            val x = padL + (idx.toFloat() / n.toFloat()) * plotW
            val y = padT + plotH - ((pt.speedMbs.coerceAtLeast(0f) / maxVal) * plotH)
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

        canvas.drawPath(fillPath, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(65, 52, 152, 219); style = Paint.Style.FILL })
        canvas.drawPath(linePath, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(52, 152, 219); strokeWidth = 3f; style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND })

        val lblP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TEXT_MUTED; textSize = 14f }
        canvas.drawText(points.first().label, padL, bmp.height - 6f, lblP)
        lblP.textAlign = Paint.Align.RIGHT
        canvas.drawText(points.last().label, bmp.width - padR, bmp.height - 6f, lblP)
        return bmp
    }

    fun renderDaily(bars: List<BarPt>, w: Int, h: Int): Bitmap {
        val bmp = newBitmap(w, h)
        val canvas = Canvas(bmp)
        val maxVal = bars.maxOfOrNull { it.gb } ?: 0f
        if (bars.isEmpty() || maxVal <= 0f) {
            drawHeaderAndNoData(canvas, "📅 По дням (GiB)", bmp.width, bmp.height, true)
            return bmp
        }

        val title = String.format(Locale.US, "📅 По дням (макс: %.1f GiB)", maxVal)
        drawHeaderAndNoData(canvas, title, bmp.width, bmp.height, false)

        val padL = 16f; val padR = 16f
        val padT = (bmp.height * 0.22f).coerceAtLeast(26f); val padB = 24f
        val plotW = (bmp.width - padL - padR).coerceAtLeast(10f)
        val plotH = (bmp.height - padT - padB).coerceAtLeast(10f)
        drawGrid(canvas, padL, padT, bmp.width - padR, padT + plotH)

        val barP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(46, 204, 113) }
        val lblP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TEXT_MUTED; textSize = 13f; textAlign = Paint.Align.CENTER }
        val slotW = plotW / bars.size.toFloat()
        val barW = (slotW * 0.62f).coerceAtLeast(8f)

        bars.forEachIndexed { idx, b ->
            val cx = padL + (idx + 0.5f) * slotW
            val bh = (b.gb.coerceAtLeast(0f) / maxVal) * plotH
            canvas.drawRoundRect(cx - barW / 2f, padT + plotH - bh, cx + barW / 2f, padT + plotH, 5f, 5f, barP)
            canvas.drawText(b.label, cx, bmp.height - 6f, lblP)
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

        val padL = 14f; val padR = 14f
        val padT = (bmp.height * 0.20f).coerceAtLeast(24f); val padB = 8f
        val nameColW = bmp.width * 0.44f
        val valColW = 58f
        val barMaxW = (bmp.width - padL - padR - nameColW - valColW - 12f).coerceAtLeast(20f)
        val rowH = ((bmp.height - padT - padB) / bars.size.toFloat()).coerceAtLeast(16f)

        val txtP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TEXT_MAIN; textSize = (rowH * 0.55f).coerceIn(13f, 17f) }
        val valP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TEXT_MUTED; textSize = txtP.textSize * 0.9f; textAlign = Paint.Align.RIGHT }
        val barP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(230, 126, 34) }

        bars.forEachIndexed { idx, item ->
            val cy = padT + idx * rowH + rowH * 0.5f
            canvas.drawText(fitText(item.label, txtP, nameColW), padL, cy + txtP.textSize * 0.35f, txtP)

            val barLeft = padL + nameColW + 6f
            val ratio = (mapped[idx] / maxPlot).takeIf { it.isFinite() } ?: 0f
            val bw = (ratio * barMaxW).coerceAtLeast(4f)
            val barHalfH = (rowH * 0.30f).coerceAtLeast(4f)
            canvas.drawRoundRect(barLeft, cy - barHalfH, barLeft + bw, cy + barHalfH, 4f, 4f, barP)

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