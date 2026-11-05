package com.dan.ose11p

import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.ByteArrayInputStream
import java.io.StringReader
import java.util.Locale
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Independent EFILive-V4-style ALDL scanner subsystem.
 *
 * This is a data-driven scanner framework, not EFILive software and not a claim
 * of compatibility with every proprietary V4 vehicle-definition XML dialect.
 * Every wire request and decoder offset must be supplied by a verified vehicle
 * definition. No guessed command, checksum, DTC-clear or ECM-write sequence.
 */
data class V4Parameter(
    val id: String,
    val name: String,
    val offset: Int,
    val width: Int = 1,
    val endian: V4Endian = V4Endian.BIG,
    val signed: Boolean = false,
    val scale: Double = 1.0,
    val add: Double = 0.0,
    val units: String = "",
    val description: String = ""
)

enum class V4Endian { BIG, LITTLE }
enum class V4Checksum { NONE, GM_8BIT_COMPLEMENT, GM_8BIT_SUM }

data class V4Request(
    val id: String,
    val name: String,
    val txHex: String,
    val responseOffset: Int = 0,
    val responseLength: Int = 0,
    val checksum: V4Checksum = V4Checksum.NONE,
    val notes: String = "",
    val pollIntervalMs: Long = 0,
    val requiresConfirmation: Boolean = false,
    val module: String = "ECM",
    val responseTimeoutMs: Int = 1200,
    val quietTimeMs: Int = 60
)

data class V4DtcRule(val code: String, val description: String, val offset: Int, val mask: Int = 0xff, val value: Int)
data class V4MacroStep(val requestId: String, val delayMs: Long = 100)
data class V4Macro(val id: String, val name: String, val steps: List<V4MacroStep>, val requiresConfirmation: Boolean = false)
data class V4VehicleDefinition(
    val id: String,
    val name: String,
    val baud: Int,
    val dataBits: Int = 8,
    val stopBits: Int = 1,
    val parity: String = "NONE",
    val parameters: List<V4Parameter> = emptyList(),
    val requests: List<V4Request> = emptyList(),
    val macros: List<V4Macro> = emptyList(),
    val warnings: List<String> = emptyList(),
    val dtcReadRequestId: String? = null,
    val dtcClearRequestId: String? = null,
    val dtcRules: List<V4DtcRule> = emptyList(),
    val connectHex: String? = null,
    val disconnectHex: String? = null
)

data class V4DecodedValue(val parameter: V4Parameter, val raw: Long, val value: Double)
data class V4FrameResult(val bytes: ByteArray, val checksumValid: Boolean?, val values: List<V4DecodedValue>, val warnings: List<String>)

object V4Hex {
    fun parse(text: String): ByteArray {
        val cleaned = text.replace(Regex("(?i)0x"), "").replace(Regex("[^0-9a-fA-F]"), "")
        require(cleaned.isNotEmpty()) { "Enter hexadecimal bytes." }
        require(cleaned.length % 2 == 0) { "Hex data must contain complete byte pairs." }
        return ByteArray(cleaned.length / 2) { i -> cleaned.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }

    fun format(bytes: ByteArray): String = bytes.joinToString(" ") { "%02X".format(it.toInt() and 0xff) }
}

object V4Protocol {
    fun checksum(bytes: ByteArray, mode: V4Checksum): Int? {
        if (bytes.isEmpty()) return null
        val sum = bytes.sumOf { it.toInt() and 0xff } and 0xff
        return when (mode) {
            V4Checksum.NONE -> null
            V4Checksum.GM_8BIT_SUM -> sum
            V4Checksum.GM_8BIT_COMPLEMENT -> (0xff - sum) and 0xff
        }
    }

    fun appendChecksum(payload: ByteArray, mode: V4Checksum): ByteArray {
        val check = checksum(payload, mode) ?: return payload.copyOf()
        return payload + byteArrayOf(check.toByte())
    }

    fun validateChecksum(frame: ByteArray, mode: V4Checksum): Boolean? {
        if (mode == V4Checksum.NONE) return null
        if (frame.size < 2) return false
        val payload = frame.copyOf(frame.size - 1)
        return (frame.last().toInt() and 0xff) == checksum(payload, mode)
    }

    fun decode(frame: ByteArray, definition: V4VehicleDefinition, request: V4Request? = null): V4FrameResult {
        val warnings = mutableListOf<String>()
        val valid = request?.let { validateChecksum(frame, it.checksum) }
        if (valid == false) warnings += "Checksum mismatch for selected request; decoded values may be invalid."
        val base = request?.responseOffset ?: 0
        val length = request?.responseLength?.takeIf { it > 0 } ?: frame.size
        val end = (base + length).coerceAtMost(frame.size)
        val values = mutableListOf<V4DecodedValue>()
        if (base < 0 || base >= end) {
            warnings += "Response offset/length does not select data in this frame."
        } else {
            definition.parameters.forEach { p ->
                val start = base + p.offset
                val n = p.width
                if (n !in 1..4 || start < base || start + n > end) {
                    warnings += "${p.name}: byte range outside received response."
                } else {
                    var raw = 0L
                    if (p.endian == V4Endian.BIG) {
                        for (i in 0 until n) raw = (raw shl 8) or (frame[start + i].toLong() and 0xff)
                    } else {
                        for (i in n - 1 downTo 0) raw = (raw shl 8) or (frame[start + i].toLong() and 0xff)
                    }
                    val signedRaw = if (p.signed) {
                        val bits = n * 8
                        if ((raw and (1L shl (bits - 1))) != 0L) raw - (1L shl bits) else raw
                    } else raw
                    values += V4DecodedValue(p, signedRaw, signedRaw.toDouble() * p.scale + p.add)
                }
            }
        }
        return V4FrameResult(frame.copyOf(), valid, values, warnings)
    }
}

/** Small, secure, documented XML interchange format for MonitorTune V4 definitions. */
object V4DefinitionImporter {
    private fun document(bytes: ByteArray) = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = false
        isExpandEntityReferences = false
        safeFeature("http://xml.org/sax/features/external-general-entities", false)
        safeFeature("http://xml.org/sax/features/external-parameter-entities", false)
        safeFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        safeFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        safeAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
        safeAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
    }.newDocumentBuilder().apply {
        setEntityResolver { _, _ -> InputSource(StringReader("")) }
    }.parse(ByteArrayInputStream(bytes))

    private fun DocumentBuilderFactory.safeFeature(n: String, v: Boolean) {
        try { setFeature(n, v) } catch (_: Exception) {}
    }
    private fun DocumentBuilderFactory.safeAttribute(n: String, v: String) {
        try { setAttribute(n, v) } catch (_: Exception) {}
    }
    private fun Element.attr(name: String, fallback: String = ""): String = getAttribute(name).takeIf { it.isNotBlank() } ?: fallback
    private fun Element.children(tag: String): List<Element> {
        val nodes = getElementsByTagName(tag)
        return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
    }

    fun import(bytes: ByteArray): V4VehicleDefinition {
        require(bytes.isNotEmpty() && bytes.size <= 2 * 1024 * 1024) { "Definition file must be 1 byte–2 MB." }
        val root = document(bytes).documentElement
        require(root.tagName.equals("MONITORTUNEV4", true)) {
            "Unsupported XML dialect. This importer accepts MonitorTuneV4 XML, not arbitrary EFILive proprietary XML."
        }
        val parameters = root.children("parameter").mapIndexed { i, e ->
            V4Parameter(
                e.attr("id", "p$i"), e.attr("name", "Parameter ${i + 1}"),
                e.attr("offset").toIntOrNull() ?: error("Parameter ${i + 1}: invalid offset."),
                e.attr("width", "1").toInt(), V4Endian.valueOf(e.attr("endian", "BIG").uppercase()),
                e.attr("signed", "false").toBoolean(), e.attr("scale", "1").toDouble(),
                e.attr("add", "0").toDouble(), e.attr("units"), e.attr("description")
            ).also { require(it.offset >= 0 && it.width in 1..4 && it.scale.isFinite() && it.add.isFinite()) { "Invalid parameter ${it.name}." } }
        }
        val requests = root.children("request").mapIndexed { i, e ->
            V4Request(
                e.attr("id", "r$i"), e.attr("name", "Request ${i + 1}"),
                e.attr("tx"), e.attr("responseOffset", "0").toInt(),
                e.attr("responseLength", "0").toInt(),
                V4Checksum.valueOf(e.attr("checksum", "NONE").uppercase()), e.attr("notes"), e.attr("pollIntervalMs", "0").toLong(), e.attr("requiresConfirmation", "false").toBoolean(), e.attr("module", "ECM"), e.attr("responseTimeoutMs", "1200").toInt(), e.attr("quietTimeMs", "60").toInt()
            ).also { V4Hex.parse(it.txHex); require(it.responseOffset >= 0 && it.responseLength >= 0 && it.pollIntervalMs in 0..60000 && it.responseTimeoutMs in 50..10000 && it.quietTimeMs in 5..500) }
        }
        val macros = root.children("macro").mapIndexed { i, e ->
            val steps = e.children("step").map { V4MacroStep(it.attr("request"), it.attr("delayMs", "100").toLong()) }
            require(steps.isNotEmpty() && steps.all { step -> requests.any { it.id == step.requestId } && step.delayMs in 0..60000 }) {
                "Macro ${e.attr("name", "Macro ${i + 1}")} has invalid or missing request steps."
            }
            V4Macro(e.attr("id", "m$i"), e.attr("name", "Macro ${i + 1}"), steps, e.attr("requiresConfirmation", "false").toBoolean())
        }
        require(root.attr("dataBits", "8").toInt() in 5..8) { "Unsupported data bit count." }
        require(root.attr("stopBits", "1").toInt() in 1..2) { "Unsupported stop bit count." }
        require(root.attr("parity", "NONE").uppercase() in setOf("NONE", "ODD", "EVEN", "MARK", "SPACE")) { "Unsupported parity." }
        val dtcRules = root.children("dtc").map { e ->
            V4DtcRule(e.attr("code"), e.attr("description"), e.attr("offset").toInt(), e.attr("mask", "255").toInt(), e.attr("value").toInt())
                .also { require(it.code.isNotBlank() && it.offset >= 0 && it.mask in 0..255 && it.value in 0..255) }
        }
        val readDtc = root.attr("dtcReadRequestId").ifBlank { null }
        val clearDtc = root.attr("dtcClearRequestId").ifBlank { null }
        require(readDtc == null || requests.any { it.id == readDtc }) { "DTC read request id is not defined." }
        require(clearDtc == null || requests.any { it.id == clearDtc }) { "DTC clear request id is not defined." }
        val connectHex = root.attr("connectHex").takeIf { it.isNotBlank() }?.also { V4Hex.parse(it) }
        val disconnectHex = root.attr("disconnectHex").takeIf { it.isNotBlank() }?.also { V4Hex.parse(it) }\n        val baud = root.attr("baud", "8192").toInt()
        require(baud in 300..1000000) { "Invalid serial baud rate." }
        val ids = (parameters.map { it.id } + requests.map { it.id } + macros.map { it.id })
        require(ids.size == ids.distinct().size) { "Definition contains duplicate ids." }
        return V4VehicleDefinition(root.attr("id", "custom"), root.attr("name", "Imported vehicle"), baud,
            root.attr("dataBits", "8").toInt(), root.attr("stopBits", "1").toInt(), root.attr("parity", "NONE"),
            parameters, requests, macros,
            listOf("Custom MonitorTuneV4 XML loaded. It is not a verified EFILive V4 definition unless independently translated and checked.",
                "Confirm baud, serial electrical interface, command bytes, response layout, checksum and scaling against vehicle-specific documentation before connecting."), readDtc, clearDtc, dtcRules, connectHex, disconnectHex)
    }
}

interface V4RawTransport : AutoCloseable {
    fun write(bytes: ByteArray)
    fun read(timeoutMs: Int, quietTimeMs: Int = 60): ByteArray
}

data class V4LogRow(val elapsedMs: Long, val values: Map<String, Double>)
class V4SessionRecorder {
    private val started = System.currentTimeMillis()
    private val rows = mutableListOf<V4LogRow>()
    fun add(values: List<V4DecodedValue>) {
        rows += V4LogRow(System.currentTimeMillis() - started, values.associate { it.parameter.id to it.value })
    }
    fun snapshot(): List<V4LogRow> = rows.toList()
    fun toCsv(definition: V4VehicleDefinition): String {
        val ids = definition.parameters.map { it.id }
        val header = listOf("elapsed_ms") + definition.parameters.map { it.name }
        return buildString {
            appendLine(header.joinToString(",") { csv(it) })
            rows.forEach { row -> appendLine((listOf(row.elapsedMs.toString()) + ids.map { row.values[it]?.toString().orEmpty() }).joinToString(",") { csv(it) }) }
        }
    }
    private fun csv(v: String) = "\"${v.replace("\"", "\"\"")}\""
}

data class V4ChartPoint(val elapsedMs: Long, val value: Double)
class V4ChartHistory(private val maxPoints: Int = 3000) {
    private val series = linkedMapOf<String, ArrayDeque<V4ChartPoint>>()
    fun add(id: String, point: V4ChartPoint) {
        val q = series.getOrPut(id) { ArrayDeque() }
        q.addLast(point)
        while (q.size > maxPoints) q.removeFirst()
    }
    fun get(id: String): List<V4ChartPoint> = series[id]?.toList().orEmpty()
    fun clear() = series.clear()
}


/** Runs only explicitly configured requests; no protocol commands are invented. */
class V4MacroExecutor(private val definition: V4VehicleDefinition, private val transport: V4RawTransport) {
    fun execute(macro: V4Macro, onStep: (Int, V4Request, V4FrameResult) -> Unit) {
        macro.steps.forEachIndexed { index, step ->
            val request = definition.requests.firstOrNull { it.id == step.requestId }
                ?: error("Macro references missing request ${step.requestId}")
            require(!request.requiresConfirmation || macro.requiresConfirmation) { "Macro requires explicit confirmation because it contains a state-changing request." }
            val tx = V4Hex.parse(request.txHex)
            transport.write(tx)
            val rx = transport.read(request.responseTimeoutMs, request.quietTimeMs)
            require(rx.isNotEmpty()) { "No response to ${request.name}" }
            onStep(index, request, V4Protocol.decode(rx, definition, request))
            if (step.delayMs > 0) Thread.sleep(step.delayMs)
        }
    }
}

object V4DtcDecoder {
    fun decode(frame: ByteArray, definition: V4VehicleDefinition, request: V4Request): List<Pair<String, String>> {
        val base = request.responseOffset.coerceAtLeast(0)
        val end = if (request.responseLength > 0) (base + request.responseLength).coerceAtMost(frame.size) else frame.size
        if (base >= end) return emptyList()
        return definition.dtcRules.filter { rule ->
            rule.offset in base until end && ((frame[rule.offset].toInt() and 0xff) and rule.mask) == rule.value
        }.map { it.code to it.description }
    }
}
