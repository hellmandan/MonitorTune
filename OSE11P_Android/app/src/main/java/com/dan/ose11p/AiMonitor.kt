package com.dan.ose11p

/** Deterministic on-device monitor. These are alert thresholds, not ECU calibration targets. */
class AiMonitor {
    data class Settings(
        var warningCoolantC: Double = 105.0,
        var criticalCoolantC: Double = 115.0,
        var warningBoostPsi: Double = 26.0,
        var criticalBoostPsi: Double = 29.0,
        var boostAfrLimit: Double = 12.5,
        var warningKnockDeg: Double = 3.0,
        var criticalKnockDeg: Double = 6.0
    )
    data class Alert(val level: Level, val title: String, val detail: String)
    enum class Level { RED, AMBER, INFO }

    var settings = Settings()
    var widebandAvailable = true
    var boostMonitoringAvailable = true
    var knockMonitoringAvailable = true
    var rpmLimitAvailable = true
    var staleAfterMs = 1500L
    var lastSampleAt: Long = 0L
        private set
    var alerts: List<Alert> = emptyList()
        private set
    var summary: String = "Waiting for ECU data"
        private set

    fun update(s: Sample, now: Long = System.currentTimeMillis()) {
        lastSampleAt = now
        val a = mutableListOf<Alert>()
        if (s.coolant >= settings.criticalCoolantC) a += Alert(Level.RED, "COOLANT CRITICAL", "${fmt(s.coolant)} °C — reduce load and stop safely as soon as possible.")
        else if (s.coolant >= settings.warningCoolantC) a += Alert(Level.AMBER, "Coolant temperature high", "${fmt(s.coolant)} °C — monitor temperature and reduce load.")

        if (boostMonitoringAvailable && s.boostPsi >= settings.criticalBoostPsi) a += Alert(Level.RED, "OVERBOOST", "${fmt(s.boostPsi)} psi exceeds the configured ${fmt(settings.criticalBoostPsi)} psi alert threshold.")
        else if (boostMonitoringAvailable && s.boostPsi >= settings.warningBoostPsi) a += Alert(Level.AMBER, "Boost above warning threshold", "${fmt(s.boostPsi)} psi — verify boost control and intended limit.")

        if (widebandAvailable && s.boostPsi > 2.0 && s.wbAfr in 10.0..18.0 && s.wbAfr > settings.boostAfrLimit) {
            a += Alert(if (s.wbAfr >= settings.boostAfrLimit + 0.7) Level.RED else Level.AMBER,
                "LEAN AFR UNDER BOOST", "Wideband ${fmt(s.wbAfr)}:1 at ${fmt(s.boostPsi)} psi; configured alert is ${fmt(settings.boostAfrLimit)}:1. Lift off safely and investigate fuel delivery, sensor and calibration.")
        }
        if (knockMonitoringAvailable && s.knockRetard >= settings.criticalKnockDeg) a += Alert(Level.RED, "HIGH KNOCK RETARD", "${fmt(s.knockRetard)}° retard detected. Reduce load and investigate before further boost operation.")
        else if (knockMonitoringAvailable && s.knockRetard >= settings.warningKnockDeg) a += Alert(Level.AMBER, "Knock retard detected", "${fmt(s.knockRetard)}° — correlate with RPM, MAP, AFR and logged knock counts.")
        if (rpmLimitAvailable && s.rpm > 6500.0) a += Alert(Level.AMBER, "High engine speed", "${fmt(s.rpm)} rpm — configured monitor reminder at 6500 rpm.")
        if (widebandAvailable && s.wbAfr !in 10.0..18.0) a += Alert(Level.INFO, "Wideband reading invalid/out of range", "WB value ${fmt(s.wbAfr)}:1 is outside the app's 10–18 AFR plausibility window. AFR-based warnings are inhibited.")
        alerts = a.sortedBy { it.level.ordinal }
        summary = when {
            a.any { it.level == Level.RED } -> "CRITICAL — ${a.count { it.level == Level.RED }} critical alert(s)"
            a.any { it.level == Level.AMBER } -> "CAUTION — ${a.size} alert(s)"
            else -> "NO RULE-BASED ALERTS — keep monitoring"
        }
    }

    fun addObdCoverage(o: ObdLive) {
        val missing = listOf(
            (o.rpm != null) to "RPM", (o.coolantC != null) to "coolant temperature",
            (o.mapKpa != null) to "MAP", (o.tpsPct != null) to "throttle position"
        ).filter { !it.first }.map { it.second }
        if (missing.isNotEmpty()) {
            alerts = alerts + Alert(Level.INFO, "Limited standard PID coverage", "This ECU/adapter did not report: ${missing.joinToString()}. Those values cannot be monitored in this session.")
            summary = "LIMITED DATA — ${missing.size} core reading(s) unavailable"
        }
        val volts = o.moduleVoltage
        if (volts != null && volts < 11.5) alerts = alerts + Alert(Level.AMBER, "Low control-module voltage", "OBD PID 0x42 reports ${fmt(volts)} V. Check charging system and connections; interpretation depends on engine state.")
        else if (volts != null && volts > 15.2) alerts = alerts + Alert(Level.AMBER, "High control-module voltage", "OBD PID 0x42 reports ${fmt(volts)} V. Verify with a calibrated meter and check charging regulation.")
        val iat = o.intakeC
        if (iat != null && iat >= 70.0) alerts = alerts + Alert(Level.AMBER, "High intake-air temperature", "OBD PID 0x0F reports ${fmt(iat)} °C. Reduce load if performance or knock issues occur; threshold is a generic advisory value.")
        if (alerts.any { it.level == Level.RED }) summary = "CRITICAL — review live warnings"
        else if (alerts.any { it.level == Level.AMBER }) summary = "CAUTION — review live warnings"
        else if (missing.isEmpty()) summary = "NO GENERIC RULE ALERTS — supported PIDs only; not a full vehicle health check"
    }

    fun dataStatus(now: Long = System.currentTimeMillis()): String = when {
        lastSampleAt == 0L -> "NO DATA — connect to ECU or enable simulation"
        now - lastSampleAt > staleAfterMs -> "DATA STALE — no valid ECU sample for ${(now-lastSampleAt)/1000.0}s"
        else -> "LIVE DATA — sample age ${now-lastSampleAt} ms"
    }
    private fun fmt(v: Double) = String.format(java.util.Locale.US, "%.1f", v)
}