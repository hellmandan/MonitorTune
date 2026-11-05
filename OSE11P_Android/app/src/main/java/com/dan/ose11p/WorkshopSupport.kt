package com.dan.ose11p

import kotlin.math.abs

data class StandardPidReading(val pid: Int, val name: String, val value: Double?, val units: String)

object StandardPidDecoder {
    fun decode(pid: Int, data: IntArray): StandardPidReading? {
        if (data.any { it !in 0..255 }) return null
        val a = data.getOrNull(0) ?: return null
        val b = data.getOrNull(1) ?: 0
        return when (pid) {
            0x04 -> StandardPidReading(pid, "Calculated engine load", a * 100.0 / 255.0, "%")
            0x05 -> StandardPidReading(pid, "Engine coolant temperature", a - 40.0, "°C")
            0x0B -> StandardPidReading(pid, "Intake manifold absolute pressure", a.toDouble(), "kPa")
            0x0C -> if (data.size >= 2) StandardPidReading(pid, "Engine speed", ((a shl 8) or b) / 4.0, "rpm") else null
            0x0D -> StandardPidReading(pid, "Vehicle speed", a.toDouble(), "km/h")
            0x0F -> StandardPidReading(pid, "Intake air temperature", a - 40.0, "°C")
            0x10 -> if (data.size >= 2) StandardPidReading(pid, "Mass air flow", ((a shl 8) or b) / 100.0, "g/s") else null
            0x11 -> StandardPidReading(pid, "Throttle position", a * 100.0 / 255.0, "%")
            0x42 -> if (data.size >= 2) StandardPidReading(pid, "Control module voltage", ((a shl 8) or b) / 1000.0, "V") else null
            else -> null
        }
    }
}

object DtcDecoder {
    fun decode(bytes: ByteArray, status: DtcStatus): List<TroubleCode> {
        val out = mutableListOf<TroubleCode>()
        var i = 0
        while (i + 1 < bytes.size) {
            val a = bytes[i].toInt() and 0xff
            val b = bytes[i + 1].toInt() and 0xff
            i += 2
            if (a == 0 && b == 0) continue
            val family = when ((a shr 6) and 0x03) {
                0 -> "P"
                1 -> "C"
                2 -> "B"
                else -> "U"
            }
            val code = family + ((a shr 4) and 0x03).toString() +
                (a and 0x0f).toString(16).uppercase() +
                ((b shr 4) and 0x0f).toString(16).uppercase() +
                (b and 0x0f).toString(16).uppercase()
            out += TroubleCode(code, status, DtcCatalog.description(code))
        }
        return out
    }
}

data class TuneGuardResult(val allowed: Boolean, val reasons: List<String>)

object TuneGuard {
    /**
     * Calibration writes are fail-closed. Only the exact specialist workflow can pass,
     * and every independent safety gate must be true. This does not implement flashing.
     */
    fun validate(
        protocol: String,
        binSize: Int,
        exactDefinitionVerified: Boolean,
        checksumVerified: Boolean,
        recoveryVerified: Boolean,
        userConfirmed: Boolean
    ): TuneGuardResult {
        val reasons = mutableListOf<String>()
        if (protocol != "GM_DELPHI_11P_V104") reasons += "Protocol is not an explicitly supported calibration-write target."
        if (binSize != 65536) reasons += "BIN size does not match the expected 64 KB image."
        if (!exactDefinitionVerified) reasons += "Exact ECM operating system and definition are not verified."
        if (!checksumVerified) reasons += "Checksum strategy is not verified."
        if (!recoveryVerified) reasons += "Recovery procedure is not verified."
        if (!userConfirmed) reasons += "User confirmation is missing."
        return TuneGuardResult(reasons.isEmpty(), reasons)
    }
}

data class DynoEstimate(val horsepower: Double, val torqueNm: Double, val accelerationMps2: Double, val speedKph: Double)

object DynoMath {
    /** Simple kinetic power estimate from a speed change; not a substitute for a chassis dyno. */
    fun estimate(massKg: Double, speedKph: Double, seconds: Double, rpm: Double): DynoEstimate? {
        if (!massKg.isFinite() || !speedKph.isFinite() || !seconds.isFinite() || !rpm.isFinite()) return null
        if (massKg <= 0.0 || speedKph <= 0.0 || seconds <= 0.0 || rpm <= 0.0) return null
        val speed = speedKph / 3.6
        val acceleration = speed / seconds
        val watts = massKg * acceleration * speed
        val hp = watts / 745.7
        if (!hp.isFinite() || hp <= 0.0 || hp > 10000.0) return null
        return DynoEstimate(hp, hp * 7121.0 / rpm, acceleration, speedKph)
    }

    fun comparePercent(reference: Double, measured: Double): Double? {
        if (!reference.isFinite() || !measured.isFinite() || abs(reference) < 1e-9) return null
        return (measured - reference) / reference * 100.0
    }
}
