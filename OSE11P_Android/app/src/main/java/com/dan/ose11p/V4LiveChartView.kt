package com.dan.ose11p

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import kotlin.math.max
import kotlin.math.min

/** Lightweight scrolling trend chart for decoded ALDL parameters. */
class V4LiveChartView(context: Context) : View(context) {
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xff64748b.toInt(); strokeWidth = 1.5f; style = Paint.Style.STROKE }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33475569; strokeWidth = 1f }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xffcbd5e1.toInt(); textSize = 11f }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xff38bdf8.toInt(); strokeWidth = 2.5f; style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND }
    private val path = Path()
    private var points: List<V4ChartPoint> = emptyList()
    private var title: String = "Live ALDL trend"

    fun setSeries(name: String, values: List<V4ChartPoint>) {
        title = name
        points = values.takeLast(300)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val left = 48f; val right = width - 12f; val top = 28f; val bottom = height - 24f
        textPaint.textSize = 12f
        canvas.drawText(title, left, 17f, textPaint)
        if (points.size < 2 || right <= left || bottom <= top) {
            canvas.drawText("Waiting for live samples…", left, (top + bottom) / 2f, textPaint)
            return
        }
        val minV = points.minOf { it.value }
        val maxV = points.maxOf { it.value }
        val span = max(1e-9, maxV - minV)
        val pad = span * 0.08
        val low = minV - pad
        val high = maxV + pad
        for (i in 0..4) {
            val y = top + (bottom - top) * i / 4f
            canvas.drawLine(left, y, right, y, gridPaint)
            val v = high - (high - low) * i / 4.0
            canvas.drawText(String.format(java.util.Locale.US, "%.1f", v), 2f, y + 4f, textPaint)
        }
        val firstT = points.first().elapsedMs
        val lastT = max(firstT + 1, points.last().elapsedMs)
        path.reset()
        points.forEachIndexed { i, p ->
            val x = left + (right - left) * ((p.elapsedMs - firstT).toFloat() / (lastT - firstT).toFloat())
            val y = bottom - (bottom - top) * ((p.value - low) / (high - low)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, linePaint)
        canvas.drawLine(left, top, left, bottom, axisPaint)
        canvas.drawLine(left, bottom, right, bottom, axisPaint)
    }
}
