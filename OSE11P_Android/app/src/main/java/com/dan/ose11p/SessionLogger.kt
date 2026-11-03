package com.dan.ose11p

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Append-only CSV session logger. CSV fields are escaped and use invariant decimal formatting. */
class SessionLogger(context: Context, sessionName: String) {
    val file: File
    private val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)
    init {
        val safe = sessionName.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "session" }
        val dir = File(context.getExternalFilesDir(null), "MonitorTune/logs").apply { mkdirs() }
        file = File(dir, "${safe}_${System.currentTimeMillis()}.csv")
        file.writeText("timestamp,rpm,speed_kph,coolant_c,map_kpa,baro_kpa,intake_c,tps_pct,timing_deg,load_pct,maf_gps,module_voltage,fuel_rail_kpa\n")
    }
    @Synchronized fun append(sample: ObdLive) {
        fun n(v: Double?) = v?.takeIf { it.isFinite() }?.let { String.format(Locale.US, "%.4f", it) } ?: ""
        val row = listOf(formatter.format(Date()), n(sample.rpm), n(sample.speedKph), n(sample.coolantC),
            n(sample.mapKpa), n(sample.baroKpa), n(sample.intakeC), n(sample.tpsPct), n(sample.timingDeg),
            n(sample.loadPct), n(sample.mafGps), n(sample.moduleVoltage), n(sample.fuelRailKpa)).joinToString(",")
        file.appendText(row + "\n")
    }
}

object SensorSanity {
    data class Finding(val severity: String, val sensor: String, val message: String)
    fun inspect(s: ObdLive): List<Finding> = buildList {
        fun check(name: String, value: Double?, low: Double, high: Double) {
            if (value != null && (!value.isFinite() || value < low || value > high))
                add(Finding("WARNING", name, "Reading ${"%.2f".format(Locale.US, value)} is outside generic plausibility range $low..$high. Confirm PID scaling and vehicle data before diagnosing."))
        }
        check("RPM", s.rpm, 0.0, 12000.0); check("Vehicle speed", s.speedKph, 0.0, 400.0)
        check("Coolant temperature", s.coolantC, -40.0, 215.0); check("Intake temperature", s.intakeC, -40.0, 150.0)
        check("MAP", s.mapKpa, 0.0, 500.0); check("TPS", s.tpsPct, 0.0, 100.0)
        check("Module voltage", s.moduleVoltage, 5.0, 20.0)
    }
}