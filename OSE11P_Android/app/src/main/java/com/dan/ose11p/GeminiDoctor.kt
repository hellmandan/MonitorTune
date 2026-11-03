package com.dan.ose11p

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Manual, on-demand Gemini diagnosis. No telemetry is uploaded automatically. */
object GeminiDoctor {
    fun analyse(apiKey: String, s: Sample, localAlerts: List<AiMonitor.Alert>): String {
        require(apiKey.isNotBlank()) { "Enter a Gemini API key first." }
        val alertText = if (localAlerts.isEmpty()) "No active local rule alerts." else localAlerts.joinToString("\n") { "${it.level}: ${it.title} — ${it.detail}" }
        val prompt = """
You are an automotive diagnostic assistant reviewing live data from a modified turbocharged Buick/GM 3800 engine using a Delco OSE $11P V104 ECU.
Give cautious, technically grounded interpretation. Do not invent unavailable sensor readings or claim certainty. Distinguish measured values from hypotheses. If readings suggest risk, advise lifting off safely and investigating; do not recommend a hard pull. Never tell the user to change ECU tables blindly. This is advisory only and does not replace calibrated instruments, a dyno or a qualified tuner.

Current decoded ALDL snapshot:
RPM=${s.rpm}; speed=${s.speed} km/h; coolant=${s.coolant} C; MAP=${s.map} kPa; boost=${s.boostPsi} psi; TPS=${s.tps}%; spark=${s.spark} deg; current VE=${s.currentVe}; calculated VE=${s.calcVe}; wideband AFR=${s.wbAfr}; commanded AFR=${s.cmdAfr}; knock retard=${s.knockRetard} deg; knock counts=${s.knockCounts}; knock delta=${s.knockDelta}.

Local monitor alerts:
$alertText

Please provide: (1) immediate risk assessment, (2) what the readings indicate together, (3) likely causes ranked with evidence, (4) what to log/check next. Explicitly state that fuel pressure, oil pressure, IAT and battery voltage are unavailable in this snapshot. Keep it concise and do not assume the wideband calibration is correct.
""".trimIndent()

        val parts = JSONArray().put(JSONObject().put("text", prompt))
        val body = JSONObject().put("contents", JSONArray().put(JSONObject().put("parts", parts)))
            .put("generationConfig", JSONObject().put("temperature", 0.2).put("maxOutputTokens", 900))
        val conn = (URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent").openConnection() as HttpURLConnection)
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("x-goog-api-key", apiKey)
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val response = stream.bufferedReader().use { it.readText() }
            if (code !in 200..299) {
                val message = try { JSONObject(response).optJSONObject("error")?.optString("message") } catch (_: Exception) { null }
                throw IllegalStateException("Gemini API HTTP $code: ${message ?: response.take(500)}")
            }
            val root = JSONObject(response)
            val candidates = root.optJSONArray("candidates") ?: throw IllegalStateException("Gemini returned no candidates: ${response.take(500)}")
            val content = candidates.optJSONObject(0)?.optJSONObject("content")
            val responseParts = content?.optJSONArray("parts") ?: throw IllegalStateException("Gemini response had no text.")
            return (0 until responseParts.length()).mapNotNull { responseParts.optJSONObject(it)?.optString("text") }.joinToString("\n").ifBlank { "Gemini returned an empty response." }
        } finally {
            conn.disconnect()
        }
    }
    /** Generic SAE J1979 analysis. Only standard PIDs actually returned by the ECU are described as measured. */
    fun analyseObd(apiKey: String, s: ObdLive): String {
        require(apiKey.isNotBlank()) { "Enter a Gemini API key first." }
        fun v(x: Double?): String = x?.let { String.format(java.util.Locale.US, "%.2f", it) } ?: "NOT SUPPORTED / NOT AVAILABLE"
        val prompt = """
You are an experienced automotive diagnostic technician. Analyse a live SAE J1979 OBD-II snapshot collected through an ELM327 Bluetooth adapter. This is a generic vehicle, not necessarily a Buick/GM. The adapter negotiated the vehicle's OBD protocol automatically: ${s.protocol}.
Only interpret values listed below as measured. Do not infer missing values. Do not assume manufacturer-specific enhanced diagnostics are available. Explain uncertainty and distinguish likely causes from confirmed faults. Never recommend blindly changing ECU calibration. This is advisory and cannot replace proper scan-tool diagnosis or mechanical checks.

Live standard OBD-II data:
RPM: ${v(s.rpm)}
Vehicle speed: ${v(s.speedKph)} km/h
Engine coolant: ${v(s.coolantC)} °C
Intake air temperature: ${v(s.intakeC)} °C
Intake manifold absolute pressure: ${v(s.mapKpa)} kPa
Barometric pressure: ${v(s.baroKpa)} kPa
Throttle position: ${v(s.tpsPct)} %
Timing advance: ${v(s.timingDeg)} degrees
Calculated engine load: ${v(s.loadPct)} %
MAF: ${v(s.mafGps)} g/s
Control module voltage: ${v(s.moduleVoltage)} V
Fuel rail pressure PID 0x23: ${v(s.fuelRailKpa)} kPa
Supported standard PIDs: ${s.supportedPids.sorted().joinToString { "0x" + it.toString(16).uppercase().padStart(2, '0') }}

Important limitations: standard OBD-II does not guarantee wideband AFR/lambda, fuel pressure on all vehicles, oil pressure, transmission temperatures, misfire counters, manufacturer-specific DTCs or hybrid/EV battery data. Do not claim those were checked. No DTC scan was performed in this snapshot.

Return: (1) overall condition/risk based only on these readings, (2) abnormal readings and why, (3) likely causes ranked with evidence, (4) next scan-tool/live-data checks. If engine is under load and a serious fault is suspected, advise the driver to reduce load and stop safely.
""".trimIndent()
        return postPrompt(apiKey, prompt)
    }

    private fun postPrompt(apiKey: String, prompt: String): String {
        val parts = JSONArray().put(JSONObject().put("text", prompt))
        val body = JSONObject().put("contents", JSONArray().put(JSONObject().put("parts", parts)))
            .put("generationConfig", JSONObject().put("temperature", 0.2).put("maxOutputTokens", 900))
        val conn = (URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent").openConnection() as HttpURLConnection)
        try {
            conn.requestMethod = "POST"; conn.connectTimeout = 15000; conn.readTimeout = 30000; conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json"); conn.setRequestProperty("x-goog-api-key", apiKey)
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode; val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val response = stream.bufferedReader().use { it.readText() }
            if (code !in 200..299) {
                val message = try { JSONObject(response).optJSONObject("error")?.optString("message") } catch (_: Exception) { null }
                throw IllegalStateException("Gemini API HTTP $code: ${message ?: response.take(500)}")
            }
            val root = JSONObject(response)
            val candidates = root.optJSONArray("candidates") ?: throw IllegalStateException("Gemini returned no candidates: ${response.take(500)}")
            val responseParts = candidates.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                ?: throw IllegalStateException("Gemini response had no text.")
            return (0 until responseParts.length()).mapNotNull { responseParts.optJSONObject(it)?.optString("text") }.joinToString("\n").ifBlank { "Gemini returned an empty response." }
        } finally { conn.disconnect() }
    }

}