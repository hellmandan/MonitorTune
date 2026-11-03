package com.dan.ose11p

/** Protocol-independent OBD response utilities. These parse adapter output; they do not
 * claim that every vehicle exposes every SAE PID or diagnostic service. */
object ObdProtocolSupport {
    data class Frame(val header: String?, val data: ByteArray)

    /** Extracts hex bytes from common ELM output, with or without CAN headers/spaces. */
    fun frames(response: String): List<Frame> {
        val result = mutableListOf<Frame>()
        response.lineSequence().forEach { raw ->
            val line = raw.trim().uppercase().replace(Regex("\\s+"), " ")
            if (line.isBlank() || line.startsWith("SEARCHING") || line.contains("NODATA") || line.contains("ERROR")) return@forEach
            val tokens = line.split(' ').filter { it.isNotBlank() }
            val byteTokens = tokens.filter { it.matches(Regex("[0-9A-F]{2}")) }
            if (byteTokens.isNotEmpty()) {
                val header = tokens.firstOrNull()?.takeIf { it.matches(Regex("[0-9A-F]{3,8}")) && it !in byteTokens }
                result += Frame(header, byteTokens.map { it.toInt(16).toByte() }.toByteArray())
            } else {
                val compact = line.replace(Regex("[^0-9A-F]"), "")
                if (compact.length >= 2 && compact.length % 2 == 0) {
                    val bytes = compact.chunked(2).mapNotNull { it.toIntOrNull(16)?.toByte() }.toByteArray()
                    if (bytes.isNotEmpty()) result += Frame(null, bytes)
                }
            }
        }
        return result
    }

    /** Reassembles ISO-TP single/first/consecutive frames from one ECU response.
     * Flow-control frames are intentionally not generated here; ELM adapters generally
     * handle ISO-TP flow control for standard OBD requests. */
    fun reassembleIsoTp(response: String): ByteArray? {
        val fs = frames(response)
        if (fs.isEmpty()) return null
        val payload = ArrayList<Byte>()
        var expectedLength: Int? = null
        var nextSequence = 1
        for (frame in fs) {
            val b = frame.data
            if (b.isEmpty()) continue
            when ((b[0].toInt() and 0xF0) ushr 4) {
                0 -> {
                    val len = b[0].toInt() and 0x0F
                    if (len == 0 || b.size < len + 1) continue
                    return b.copyOfRange(1, len + 1)
                }
                1 -> {
                    if (b.size < 3) continue
                    expectedLength = ((b[0].toInt() and 0x0F) shl 8) or (b[1].toInt() and 0xFF)
                    payload.clear(); payload.addAll(b.copyOfRange(2, b.size).toList())
                    nextSequence = 1
                }
                2 -> {
                    if (expectedLength == null) continue
                    val seq = b[0].toInt() and 0x0F
                    if (seq != nextSequence) return null
                    payload.addAll(b.copyOfRange(1, b.size).toList())
                    nextSequence = (nextSequence + 1) and 0x0F
                    if (payload.size >= expectedLength) return payload.take(expectedLength).toByteArray()
                }
            }
        }
        return if (expectedLength != null && payload.size >= expectedLength) payload.take(expectedLength).toByteArray() else null
    }

    fun decodeVin(response: String): String? {
        val data = reassembleIsoTp(response) ?: return null
        if (data.size < 18 || (data[0].toInt() and 0xFF) != 0x49 || (data[1].toInt() and 0xFF) != 0x02) return null
        val chars = data.drop(3).map { (it.toInt() and 0xFF).toChar() }.joinToString("")
        val vin = chars.filter { it.isLetterOrDigit() }.take(17)
        return vin.takeIf { it.length == 17 && it.all { c -> c in 'A'..'Z' || c in '0'..'9' } }
    }
}

object DtcCatalog {
    private val known = mapOf(
        "P0100" to "Mass/volume air-flow circuit malfunction",
        "P0101" to "Mass/volume air-flow range/performance",
        "P0102" to "Mass/volume air-flow circuit low input",
        "P0103" to "Mass/volume air-flow circuit high input",
        "P0110" to "Intake-air temperature circuit malfunction",
        "P0115" to "Engine coolant temperature circuit malfunction",
        "P0120" to "Throttle/pedal position sensor circuit malfunction",
        "P0130" to "Oxygen sensor circuit malfunction (Bank 1 Sensor 1)",
        "P0171" to "System too lean (Bank 1)",
        "P0172" to "System too rich (Bank 1)",
        "P0201" to "Injector circuit malfunction (Cylinder 1)",
        "P0300" to "Random/multiple cylinder misfire detected",
        "P0301" to "Cylinder 1 misfire detected",
        "P0325" to "Knock sensor 1 circuit malfunction",
        "P0335" to "Crankshaft position sensor A circuit malfunction",
        "P0340" to "Camshaft position sensor A circuit malfunction",
        "P0401" to "Exhaust-gas recirculation flow insufficient",
        "P0420" to "Catalyst system efficiency below threshold (Bank 1)",
        "P0440" to "Evaporative-emission system malfunction",
        "P0500" to "Vehicle speed sensor malfunction",
        "P0505" to "Idle-control system malfunction",
        "P0601" to "Control-module memory checksum error",
        "P0700" to "Transmission control system request / MIL",
        "P1101" to "Manufacturer-specific powertrain fault; consult vehicle definition"
    )
    fun description(code: String): String = known[code.uppercase()] ?: when {
        code.startsWith("P1") || code.startsWith("P2") || code.startsWith("P3") -> "Manufacturer-specific or extended powertrain DTC; use the correct ECU definition."
        code.startsWith("C") -> "Chassis-system DTC; description depends on manufacturer and model year."
        code.startsWith("B") -> "Body-system DTC; description depends on manufacturer and model year."
        code.startsWith("U") -> "Network/communication DTC; inspect module network and wiring information."
        else -> "Unknown DTC. Verify the code and vehicle-specific service information."
    }
}