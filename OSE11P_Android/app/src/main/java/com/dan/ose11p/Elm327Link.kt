package com.dan.ose11p

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import java.io.IOException
import java.util.UUID

/** Generic SAE J1979 / OBD-II live data over Bluetooth Classic ELM327 SPP.
 * ATSP0 asks the adapter/ECU to negotiate the vehicle's supported OBD protocol.
 */
data class ObdLive(
    val rpm: Double? = null,
    val speedKph: Double? = null,
    val coolantC: Double? = null,
    val mapKpa: Double? = null,
    val baroKpa: Double? = null,
    val intakeC: Double? = null,
    val tpsPct: Double? = null,
    val timingDeg: Double? = null,
    val loadPct: Double? = null,
    val mafGps: Double? = null,
    val moduleVoltage: Double? = null,
    val fuelRailKpa: Double? = null,
    val supportedPids: Set<Int> = emptySet(),
    val protocol: String = "Automatic protocol detection",
    val storedDtc: List<String> = emptyList(),
    val pendingDtc: List<String> = emptyList(),
    val permanentDtc: List<String> = emptyList(),
    val vin: String? = null,
    val freezeFrameAvailable: Boolean = false
)

class Elm327Link(
    private val adapter: BluetoothAdapter,
    private val device: BluetoothDevice,
    private val onSample: (ObdLive) -> Unit,
    private val onStatus: (String) -> Unit
) : Thread("ELM327-OBDII") {
    @Volatile var stopFlag = false
    private var socket: BluetoothSocket? = null
    private val spp = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    override fun run() {
        try {
            onStatus("connecting Bluetooth ELM327")
            val s = device.createRfcommSocketToServiceRecord(spp)
            socket = s
            adapter.cancelDiscovery()
            s.connect()
            val input = s.inputStream
            val output = s.outputStream
            fun command(cmd: String, timeoutMs: Long = 1800): String {
                while (input.available() > 0) input.read()
                output.write((cmd.trim() + "\r").toByteArray(Charsets.US_ASCII)); output.flush()
                val b = StringBuilder(); val start = System.currentTimeMillis()
                while (!stopFlag && System.currentTimeMillis() - start < timeoutMs) {
                    if (input.available() > 0) {
                        val c = input.read()
                        if (c < 0) break
                        if (c == '>'.code) break
                        if (c != '\r'.code && c != '\n'.code) b.append(c.toChar())
                    } else sleep(10)
                }
                return b.toString().uppercase().replace(" ", "").replace("SEARCHING...", "")
            }
            command("ATZ", 3500)
            command("ATE0"); command("ATL0"); command("ATS0"); command("ATH0")
            command("ATAT2"); command("ATST32"); command("ATSP0", 3000)
            val protoRaw = command("ATDPN", 1200).trim()
            val protocol = protocolName(protoRaw)
            val supported = linkedSetOf<Int>()
            val first = command("0100", 3000)
            supported.addAll(parseSupported(first, 0x00))
            if (0x20 in supported) supported.addAll(parseSupported(command("0120", 2200), 0x20))
            if (0x40 in supported) supported.addAll(parseSupported(command("0140", 2200), 0x40))
            onStatus("connected ELM327 • $protocol")
            // Read identification once. Mode 09 PID 02 may be multi-frame on some adapters;
            // parser accepts a single-frame response and reports unavailable otherwise.
            val vin = parseVin(command("0902", 3000))
            var storedDtc = readDtcs(command("03", 2500), "43")
            var pendingDtc = readDtcs(command("07", 2500), "47")
            var permanentDtc = readDtcs(command("0A", 2500), "4A")
            val freezeAvailable = !command("020200", 1800).contains("NODATA")

            val corePids = listOf(0x0C, 0x0D, 0x05, 0x0B, 0x0F, 0x11, 0x0E, 0x33).filter { it in supported }
            val optionalPids = listOf(0x04, 0x10, 0x42, 0x23).filter { it in supported }
            if (corePids.isEmpty() && optionalPids.isEmpty()) throw IOException("ECU did not report supported standard live-data PIDs. Check adapter, ignition and OBD compatibility.")
            var cycle = 0
            while (!stopFlag) {
                val values = HashMap<Int, IntArray>()
                val poll = corePids + if (cycle % 4 == 0) optionalPids else emptyList()
                for (pid in poll) {
                    if (stopFlag) break
                    val raw = command("01" + pid.toString(16).padStart(2, '0'), 600)
                    val bytes = parsePid(raw, pid)
                    if (bytes != null) values[pid] = bytes
                }
                cycle++
                // Diagnostic trouble codes are refreshed less often to avoid slowing live gauges.
                if (cycle % 30 == 0) {
                    storedDtc = readDtcs(command("03", 2200), "43")
                    pendingDtc = readDtcs(command("07", 2200), "47")
                    permanentDtc = readDtcs(command("0A", 2200), "4A")
                }
                if (stopFlag) break
                fun one(pid: Int): Int? = values[pid]?.getOrNull(0)
                fun pair(pid: Int): Int? = values[pid]?.let { if (it.size >= 2) (it[0] shl 8) or it[1] else null }
                val baro = one(0x33)
                onSample(ObdLive(
                    rpm = pair(0x0C)?.div(4.0), speedKph = one(0x0D)?.toDouble(),
                    coolantC = one(0x05)?.minus(40.0), mapKpa = one(0x0B)?.toDouble(),
                    baroKpa = baro?.toDouble(), intakeC = one(0x0F)?.minus(40.0),
                    tpsPct = one(0x11)?.times(100.0 / 255.0),
                    timingDeg = one(0x0E)?.minus(64.0), loadPct = one(0x04)?.times(100.0 / 255.0),
                    mafGps = pair(0x10)?.div(100.0), moduleVoltage = pair(0x42)?.div(1000.0),
                    fuelRailKpa = pair(0x23)?.times(10.0), supportedPids = supported, protocol = protocol, storedDtc = storedDtc,
                    pendingDtc = pendingDtc, permanentDtc = permanentDtc, vin = vin,
                    freezeFrameAvailable = freezeAvailable
                ))
            }
        } catch (e: SecurityException) {
            onStatus("error: Bluetooth permission missing — allow Nearby devices")
        } catch (e: Exception) {
            if (!stopFlag) onStatus("error: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            try { socket?.close() } catch (_: Exception) {}
            socket = null
            onStatus("disconnected")
        }
    }


    private fun readDtcs(response: String, marker: String): List<String> {
        if (response.contains("NODATA") || response.contains("ERROR") || response.contains("UNABLE")) return emptyList()
        val hex = response.replace(Regex("[^0-9A-F]"), "")
        val at = hex.indexOf(marker)
        if (at < 0) return emptyList()
        val data = hex.substring(at + marker.length)
        val out = ArrayList<String>()
        var i = 0
        while (i + 4 <= data.length) {
            val a = data.substring(i, i + 2).toIntOrNull(16) ?: break
            val b = data.substring(i + 2, i + 4).toIntOrNull(16) ?: break
            i += 4
            if (a == 0 && b == 0) continue
            val family = when ((a shr 6) and 3) { 0 -> 'P'; 1 -> 'C'; 2 -> 'B'; else -> 'U' }
            val digit = (a shr 4) and 3
            out.add("$family$digit${a.toString(16).uppercase().takeLast(1)}${b.toString(16).uppercase().padStart(2, '0')}")
        }
        return out.distinct()
    }

    private fun parseVin(response: String): String? = ObdProtocolSupport.decodeVin(response)

    private fun parseSupported(response: String, base: Int): Set<Int> {
        val b = parsePid(response, 0x00, expectedMode = "41") ?: return emptySet()
        if (b.size < 4) return emptySet()
        val mask = (b[0].toLong() shl 24) or (b[1].toLong() shl 16) or (b[2].toLong() shl 8) or b[3].toLong()
        return (1..32).filter { (mask and (1L shl (32 - it))) != 0L }.map { base + it }.toSet()
    }

    private fun parsePid(response: String, pid: Int, expectedMode: String = "41"): IntArray? {
        if (response.contains("NODATA") || response.contains("UNABLE") || response.contains("ERROR") || response.contains("STOPPED")) return null
        val hex = response.replace(Regex("[^0-9A-F]"), "")
        val marker = expectedMode + pid.toString(16).padStart(2, '0').uppercase()
        val at = hex.indexOf(marker)
        if (at < 0 || at + marker.length + 2 > hex.length) return null
        val data = hex.substring(at + marker.length)
        val count = (data.length / 2).coerceAtMost(8)
        return IntArray(count) { i -> data.substring(i * 2, i * 2 + 2).toIntOrNull(16) ?: 0 }
    }

    private fun protocolName(raw: String): String {
        val rawValue = raw.trim()
        val n = if (rawValue.length > 1 && rawValue.startsWith("A")) rawValue.substring(1) else rawValue
        return when (n) {
            "1" -> "SAE J1850 PWM"; "2" -> "SAE J1850 VPW"; "3" -> "ISO 9141-2"
            "4" -> "ISO 14230-4 KWP (5-baud)"; "5" -> "ISO 14230-4 KWP (fast)"
            "6" -> "ISO 15765-4 CAN 11-bit / 500k"; "7" -> "ISO 15765-4 CAN 29-bit / 500k"
            "8" -> "ISO 15765-4 CAN 11-bit / 250k"; "9" -> "ISO 15765-4 CAN 29-bit / 250k"
            "A" -> "SAE J1939 CAN"; else -> "Protocol $raw (adapter auto-detected)"
        }
    }
}