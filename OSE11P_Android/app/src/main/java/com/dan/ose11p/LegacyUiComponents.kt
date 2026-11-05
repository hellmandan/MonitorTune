package com.dan.ose11p

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.max
import kotlin.math.min

/** Shared dark workshop palette used by the existing MainActivity screens. */
object Theme {
    val BG = 0xff0b1220.toInt()
    val CARD = 0xff172235.toInt()
    val CARD_HI = 0xff24344d.toInt()
    val TEXT = 0xffe5edf7.toInt()
    val MUTED = 0xff94a3b8.toInt()
    val ACCENT = 0xff38bdf8.toInt()
    val GOOD = 0xff22c55e.toInt()
    val WARN = 0xfff59e0b.toInt()
    val BAD = 0xffef4444.toInt()
    val BLUE = 0xff2563eb.toInt()
}

class StatusDot(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var tint = Theme.MUTED
    private var active = false
    fun set(color: Int, lit: Boolean) { tint = color; active = lit; invalidate() }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = (16 * resources.displayMetrics.density).toInt()
        setMeasuredDimension(size, size)
    }
    override fun onDraw(canvas: Canvas) {
        val r = min(width, height) * 0.34f
        paint.color = tint
        paint.alpha = if (active) 255 else 110
        canvas.drawCircle(width / 2f, height / 2f, r, paint)
        if (active) {
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.5f; paint.alpha = 180
            canvas.drawCircle(width / 2f, height / 2f, r + 3f, paint)
            paint.style = Paint.Style.FILL
        }
    }
}

/** Reusable semicircular dial for the main OSE dashboard. */
class ArcGauge(
    context: Context,
    private val title: String,
    private val units: String,
    private val minValue: Float,
    private val maxValue: Float,
    private val warningValue: Float,
    private val decimals: Int,
    private val majorStep: Float,
    private val minorStep: Float = 0f
) : View(context) {
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Theme.CARD_HI; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeWidth = 12f }
    private val active = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeWidth = 12f }
    private val needle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Theme.TEXT; strokeWidth = 2.5f; strokeCap = Paint.Cap.ROUND }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Theme.TEXT; textAlign = Paint.Align.CENTER; textSize = 13f }
    private var value = minValue
    private var available = true
    private val rect = RectF()

    fun setValue(v: Float) { value = v; available = true; invalidate() }
    fun setUnavailable() { available = false; invalidate() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val radius = min(width * 0.39f, height * 0.57f)
        val cy = height * 0.62f
        rect.set(cx - radius, cy - radius, cx + radius, cy + radius)
        canvas.drawArc(rect, 135f, 270f, false, track)
        val ratio = if (maxValue > minValue) ((value - minValue) / (maxValue - minValue)).coerceIn(0f, 1f) else 0f
        active.color = if (warningValue.isFinite() && warningValue < Float.MAX_VALUE && value >= warningValue) Theme.BAD else Theme.ACCENT
        canvas.drawArc(rect, 135f, ratio * 270f, false, active)
        val angle = Math.toRadians(135.0 + ratio * 270.0)
        canvas.drawLine(cx, cy, cx + radius * 0.78f * kotlin.math.cos(angle).toFloat(), cy + radius * 0.78f * kotlin.math.sin(angle).toFloat(), needle)
        text.textSize = 13f
        canvas.drawText(title, cx, 18f, text)
        text.textSize = 20f
        canvas.drawText(String.format(java.util.Locale.US, "%.${decimals}f", value), cx, cy + radius * 0.40f, text)
        text.textSize = 11f
        canvas.drawText(units, cx, height - 8f, text)
        if (majorStep > 0f) {
            text.color = Theme.MUTED
            canvas.drawText(String.format(java.util.Locale.US, "%.0f", minValue), cx - radius * 0.72f, cy + radius * 0.45f, text)
            canvas.drawText(String.format(java.util.Locale.US, "%.0f", maxValue), cx + radius * 0.72f, cy + radius * 0.45f, text)
            text.color = Theme.TEXT
        }
    }
}

data class GridCell(
    val label: String,
    val value: Float,
    val measured: Boolean,
    val flagged: Boolean,
    val dim: Boolean
)

/** Scrollable two-axis calibration table used by VE and spark review screens. */
class HeatGrid(context: Context) : View(context) {
    var minV: Float = 0f
    var maxV: Float = 100f
    private var rows: List<String> = emptyList()
    private var columns: List<String> = emptyList()
    private var cells: List<GridCell> = emptyList()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f; color = Theme.CARD_HI }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Theme.TEXT; textAlign = Paint.Align.CENTER; textSize = 10f }

    fun setData(rowLabels: List<String>, columnLabels: List<String>, values: List<GridCell>) {
        rows = rowLabels.toList(); columns = columnLabels.toList(); cells = values.toList()
        requestLayout(); invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val density = resources.displayMetrics.density
        val cellW = max(54f, 54f * density)
        val cellH = 34f * density
        val w = (paddingLeft + paddingRight + cellW * (columns.size + 1)).toInt()
        val h = (paddingTop + paddingBottom + cellH * (rows.size + 1)).toInt()
        setMeasuredDimension(resolveSize(w, widthMeasureSpec), resolveSize(h, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (rows.isEmpty() || columns.isEmpty()) return
        val density = resources.displayMetrics.density
        val left = paddingLeft.toFloat()
        val top = paddingTop.toFloat()
        val cellW = max(54f, 54f * density)
        val cellH = 34f * density
        rows.forEachIndexed { r, row ->
            text.color = Theme.MUTED
            canvas.drawText(row, left + cellW * 0.5f, top + cellH * (r + 0.65f), text)
            columns.forEachIndexed { c, col ->
                val idx = r * columns.size + c
                val x = left + cellW * (c + 1)
                val y = top + cellH * (r + 1)
                if (idx < cells.size) {
                    val cell = cells[idx]
                    val ratio = if (maxV > minV) ((cell.value - minV) / (maxV - minV)).coerceIn(0f, 1f) else 0f
                    val red = (35 + ratio * 170).toInt().coerceIn(0, 255)
                    val green = (110 + (1f - ratio) * 100).toInt().coerceIn(0, 255)
                    fill.color = if (cell.flagged) 0xff7f1d1d.toInt() else android.graphics.Color.rgb(red, green, 65)
                    if (cell.dim) fill.alpha = 110 else fill.alpha = 230
                    canvas.drawRect(x, y, x + cellW - 2f, y + cellH - 2f, fill)
                    canvas.drawRect(x, y, x + cellW - 2f, y + cellH - 2f, stroke)
                    text.color = if (cell.dim) Theme.MUTED else Theme.TEXT
                    canvas.drawText(cell.label, x + cellW / 2f, y + cellH * 0.62f, text)
                }
                if (r == 0) {
                    text.color = Theme.MUTED
                    canvas.drawText(col, x + cellW / 2f, top + cellH * 0.65f, text)
                }
            }
        }
    }
}

class AiPulseView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Theme.TEXT; textAlign = Paint.Align.CENTER; textSize = 13f }
    private var tint = Theme.MUTED
    private var live = false
    private var busy = false
    private var caption = "STANDBY"
    fun set(color: Int, connected: Boolean, analysing: Boolean, label: String) {
        tint = color; live = connected; busy = analysing; caption = label; invalidate()
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height * 0.40f
        paint.color = tint; paint.alpha = if (live || busy) 55 else 28
        canvas.drawCircle(cx, cy, min(width * 0.16f, height * 0.30f), paint)
        paint.alpha = 255; paint.style = Paint.Style.STROKE; paint.strokeWidth = if (busy) 5f else 3f
        canvas.drawCircle(cx, cy, min(width * 0.16f, height * 0.30f), paint)
        paint.style = Paint.Style.FILL
        text.textSize = 13f
        canvas.drawText(caption, cx, height - 12f, text)
    }
}

class AlertOverlay(context: Context) : View(context) {
    private var severity = Sev.NONE
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xffffffff.toInt(); textSize = 13f; textAlign = Paint.Align.CENTER }
    init { isClickable = false; isFocusable = false }
    fun setSeverity(value: Sev) { severity = value; invalidate() }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (severity == Sev.NONE) return
        paint.color = if (severity == Sev.CRITICAL) Theme.BAD else Theme.WARN
        paint.alpha = 235
        canvas.drawRoundRect(10f, 10f, width - 10f, 42f, 8f, 8f, paint)
        canvas.drawText(if (severity == Sev.CRITICAL) "CRITICAL ENGINE ALERT" else "CAUTION — CHECK LIVE ENGINE DATA", width / 2f, 31f, text)
    }
}

data class EcuDefinition(val id: String, val label: String, val transport: String, val diagnosticLevel: String, val notes: String)
object EcuRegistry {
    val definitions = listOf(
        EcuDefinition("GM_DELPHI_11P_V104", "GM Delco OSE \$11P V104", "8192 baud ALDL / USB serial", "Project-specific specialist support", "Use matching OSE \$11P V104 ADX/XDF and verify ECM hardware/OS."),
        EcuDefinition("GM_DELPHI_OTHER", "Other GM Delco ECM families", "Vehicle-specific ALDL / serial", "Definition required", "Not interchangeable with OSE \$11P V104 without verified OS and protocol data."),
        EcuDefinition("GENERIC_OBD2", "Generic SAE OBD-II", "ELM327 / supported OBD interface", "Standard powertrain PIDs", "Enhanced OEM module access is not implied.")
    )
}

data class DelcoFamily(val id: String, val name: String, val transport: String, val supportLevel: String, val definitionStatus: String, val requirements: List<String>)
data class ManufacturerSupport(
    val id: String,
    val displayName: String,
    val commonProtocols: List<String>,
    val genericObd2Note: String,
    val enhancedStatus: String
)
data class DelcoMatch(val supported: Boolean, val explanation: String)

object VehicleSupportCatalog {
    val delcoFamilies = listOf(
        DelcoFamily("OSE_11P_V104", "GM Delco OSE \$11P V104", "8192 baud ALDL", "Specialist project support", "Matching V104 definitions required", listOf("Exact ECM OS", "Verified ADX/XDF", "Raw ALDL interface")),
        DelcoFamily("DELCO_OTHER", "Other Delco P4/P6/OBD-I families", "Vehicle-specific ALDL", "Catalogue only", "Not enabled by assumption", listOf("Exact ECM service definition", "Verified protocol and checksum")),
        DelcoFamily("GM_OBD2", "GM OBD-II controllers", "SAE J1979 / OEM enhanced", "Generic OBD-II only in this path", "Enhanced definitions required", listOf("Compatible adapter", "OEM-specific PID definition"))
    )
    val manufacturers = listOf(
        ManufacturerSupport("GM", "General Motors", listOf("SAE J1850 VPW", "ISO 9141-2", "ISO 14230", "CAN"), "Generic emissions PIDs are available where supported.", "Enhanced OEM module definitions are required for full diagnostics."),
        ManufacturerSupport("FORD", "Ford", listOf("ISO 9141", "PWM", "CAN"), "Generic emissions PIDs are available where supported.", "Enhanced OEM module definitions are required for full diagnostics."),
        ManufacturerSupport("TOYOTA", "Toyota", listOf("ISO 9141", "KWP2000", "CAN"), "Generic emissions PIDs are available where supported.", "Enhanced OEM module definitions are required for full diagnostics."),
        ManufacturerSupport("HONDA", "Honda", listOf("ISO 9141", "KWP2000", "CAN"), "Generic emissions PIDs are available where supported.", "Enhanced OEM module definitions are required for full diagnostics."),
        ManufacturerSupport("MAZDA", "Mazda", listOf("ISO 9141", "CAN"), "Generic emissions PIDs are available where supported.", "Enhanced OEM module definitions are required for full diagnostics."),
        ManufacturerSupport("NISSAN", "Nissan", listOf("ISO 9141", "KWP2000", "CAN"), "Generic emissions PIDs are available where supported.", "Enhanced OEM module definitions are required for full diagnostics."),
        ManufacturerSupport("HYUNDAI", "Hyundai", listOf("ISO 9141", "CAN"), "Generic emissions PIDs are available where supported.", "Enhanced OEM module definitions are required for full diagnostics."),
        ManufacturerSupport("KIA", "Kia", listOf("ISO 9141", "CAN"), "Generic emissions PIDs are available where supported.", "Enhanced OEM module definitions are required for full diagnostics."),
        ManufacturerSupport("VW", "Volkswagen", listOf("ISO 9141", "KWP2000", "CAN"), "Generic emissions PIDs are available where supported.", "Enhanced OEM module definitions are required for full diagnostics."),
        ManufacturerSupport("BMW", "BMW", listOf("ISO 9141", "CAN"), "Generic emissions PIDs are available where supported.", "Enhanced OEM module definitions are required for full diagnostics."),
        ManufacturerSupport("MERCEDES", "Mercedes-Benz", listOf("ISO 9141", "CAN"), "Generic emissions PIDs are available where supported.", "Enhanced OEM module definitions are required for full diagnostics."),
        ManufacturerSupport("SUBARU", "Subaru", listOf("ISO 9141", "CAN"), "Generic emissions PIDs are available where supported.", "Enhanced OEM module definitions are required for full diagnostics.")
    )
    val protocolNotes = listOf(
        "ELM327 is a command interpreter and is not a transparent raw UART ALDL bridge.",
        "USB serial adapter chipset support does not prove correct ALDL voltage levels or wiring.",
        "Confirm protocol, baud, checksum, pinout and ECM service data before connection."
    )

    fun manufacturer(id: String): ManufacturerSupport? =
        manufacturers.firstOrNull { it.id.equals(id, true) || it.displayName.equals(id, true) }

    fun matchDelco(id: String, operatingSystem: String?, fingerprint: String?): DelcoMatch {
        if (id != "OSE_11P_V104") return DelcoMatch(false, "ECM family is not on the verified OSE 11P V104 allowlist.")
        if (operatingSystem != "OSE \$11P V104") return DelcoMatch(false, "Operating system does not exactly match OSE 11P V104.")
        if (fingerprint == null || !fingerprint.matches(Regex("sha256:[0-9a-fA-F]{64}")))
            return DelcoMatch(false, "A verified SHA-256 definition fingerprint is required.")
        return DelcoMatch(false, "Definition fingerprint is not on the signed allowlist; write support remains locked.")
    }
}

enum class DtcStatus { STORED, PENDING, PERMANENT }
data class TroubleCode(val code: String, val status: DtcStatus, val description: String)
object DiagnosticReportText {
    fun build(
        profile: VehicleProfile?,
        vin: String?,
        protocol: String,
        dtcs: List<TroubleCode>,
        findings: List<SensorSanity.Finding>,
        note: String
    ): String = buildString {
        appendLine("MONITORTUNE WORKSHOP DIAGNOSTIC REPORT")
        appendLine("Generated: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())}")
        appendLine("Vehicle: ${profile?.let { "${it.year ?: ""} ${it.make} ${it.model}".trim() } ?: "No saved profile selected"}")
        appendLine("Engine: ${profile?.engine?.ifBlank { "Not recorded" } ?: "Not recorded"}")
        appendLine("ECU: ${profile?.ecu?.ifBlank { "Not recorded" } ?: "Not recorded"}")
        appendLine("VIN: ${vin ?: "Not supplied"}")
        appendLine("Protocol: $protocol")
        appendLine()
        appendLine("TROUBLE CODES")
        if (dtcs.isEmpty()) appendLine("No standard OBD-II codes supplied in the latest sample.")
        dtcs.forEach { appendLine("${it.code} [${it.status}] — ${it.description}") }
        appendLine()
        appendLine("SENSOR PLAUSIBILITY")
        if (findings.isEmpty()) appendLine("No generic plausibility flags from the available sample.")
        findings.forEach { appendLine("${it.severity}: ${it.sensor} — ${it.message}") }
        appendLine()
        appendLine("NOTES")
        appendLine(note)
        appendLine()
        appendLine("This report is advisory. Confirm codes, sensor scaling and diagnostic procedures against OEM service information.")
    }
}
