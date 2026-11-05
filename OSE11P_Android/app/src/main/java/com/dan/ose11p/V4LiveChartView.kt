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


/** Compact definition-scaled dial for the live V4 dashboard. */
class V4MiniGaugeView(context: Context, private val parameter: V4Parameter) : View(context) {
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xff475569.toInt(); style = Paint.Style.STROKE; strokeWidth = 8f; strokeCap = Paint.Cap.ROUND }
    private val active = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xff22c55e.toInt(); style = Paint.Style.STROKE; strokeWidth = 8f; strokeCap = Paint.Cap.ROUND }
    private val needle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xfff8fafc.toInt(); strokeWidth = 2.5f; strokeCap = Paint.Cap.ROUND }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xffe2e8f0.toInt(); textAlign = Paint.Align.CENTER; textSize = 11f }
    private var current: Double? = null
    private val low = parameter.minValue ?: if (parameter.signed) -(1L shl (parameter.width * 8 - 1)).toDouble() * parameter.scale + parameter.add else parameter.add
    private val high = parameter.maxValue ?: if (parameter.signed) ((1L shl (parameter.width * 8 - 1)) - 1).toDouble() * parameter.scale + parameter.add else ((1L shl (parameter.width * 8)) - 1).toDouble() * parameter.scale + parameter.add

    fun setValue(value: Double) { current = value; invalidate() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height * 0.68f
        val radius = min(width * 0.36f, height * 0.48f)
        val oval = android.graphics.RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        canvas.drawArc(oval, 135f, 270f, false, track)
        val value = current
        if (value != null) {
            val ratio = if (high > low) ((value - low) / (high - low)).coerceIn(0.0, 1.0) else 0.0
            canvas.drawArc(oval, 135f, (270.0 * ratio).toFloat(), false, active)
            val angle = Math.toRadians(135.0 + 270.0 * ratio)
            canvas.drawLine(cx, cy, cx + (radius * 0.78f * kotlin.math.cos(angle)).toFloat(), cy + (radius * 0.78f * kotlin.math.sin(angle)).toFloat(), needle)
            text.textSize = 14f
            canvas.drawText(String.format(java.util.Locale.US, "%.2f", value), cx, cy + radius * 0.55f, text)
        } else {
            text.textSize = 12f
            canvas.drawText("—", cx, cy + radius * 0.55f, text)
        }
        text.textSize = 10f
        canvas.drawText(parameter.name.take(18), cx, height - 15f, text)
        canvas.drawText(parameter.units, cx, height - 3f, text)
    }
}
