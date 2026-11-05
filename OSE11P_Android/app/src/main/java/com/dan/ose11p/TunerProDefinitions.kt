package com.dan.ose11p

import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.ByteArrayInputStream
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

data class DefinitionItem(
    val id: String, val name: String, val kind: String, val address: Long?,
    val rows: Int = 1, val columns: Int = 1, val elementBits: Int? = null,
    val units: String = "", val equation: String = "", val notes: String = ""
)
data class ImportedDefinition(val format: String, val title: String, val items: List<DefinitionItem>, val warnings: List<String>)
data class TablePreview(val item: DefinitionItem, val values: List<List<Long>>, val warnings: List<String>)

object TunerProDefinitionImporter {
    /*
     * Android's built-in XML parser does not implement Xerces-specific
     * http://apache.org/xml/features/disallow-doctype-decl. Calling setFeature
     * with it throws the exact "feature ... not recognized" error seen on device.
     * XDF files may contain a DOCTYPE, so allow its declaration but never resolve
     * external entities or load external DTDs. Unsupported optional hardening
     * switches are ignored; the entity resolver remains a final deny-all barrier.
     */
    private fun document(bytes: ByteArray) = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = false
        isExpandEntityReferences = false
        safeFeature("http://xml.org/sax/features/external-general-entities", false)
        safeFeature("http://xml.org/sax/features/external-parameter-entities", false)
        safeFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        safeFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        safeAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "")
        safeAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "")
    }.newDocumentBuilder().apply {
        setEntityResolver { _, _ -> InputSource(StringReader("")) }
    }.parse(ByteArrayInputStream(bytes))

    private fun DocumentBuilderFactory.safeFeature(name: String, value: Boolean) {
        try { setFeature(name, value) } catch (_: Exception) { /* not implemented by this Android parser */ }
    }
    private fun DocumentBuilderFactory.safeAttribute(name: String, value: String) {
        try { setAttribute(name, value) } catch (_: Exception) { /* entity resolver still blocks external access */ }
    }
    private fun Element.attr(vararg names: String): String? {
        for (n in names) if (hasAttribute(n) && getAttribute(n).isNotBlank()) return getAttribute(n).trim()
        return null
    }
    private fun Element.descendantText(tag: String): String? =
        getElementsByTagName(tag).item(0)?.textContent?.trim()?.takeIf { it.isNotBlank() }
    private fun parseNumber(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val s = raw.trim().removePrefix("$")
        return try {
            when {
                s.startsWith("0x", true) -> s.substring(2).toLong(16)
                s.any { it in 'A'..'F' || it in 'a'..'f' } -> s.toLong(16)
                else -> s.toLong()
            }
        } catch (_: Exception) { null }
    }

    fun importXdf(bytes: ByteArray): ImportedDefinition {
        require(bytes.isNotEmpty()) { "XDF file is empty" }
        val root = document(bytes).documentElement
        require(root.tagName.equals("XDFFORMAT", true) || root.getElementsByTagName("XDFTABLE").length > 0) {
            "XML does not appear to be a TunerPro XDF"
        }
        val title = root.descendantText("XDFTITLE") ?: root.attr("title", "name") ?: "Imported XDF"
        val out = mutableListOf<DefinitionItem>()
        val warnings = mutableListOf<String>()
        val nodes = root.getElementsByTagName("XDFTABLE")
        for (i in 0 until nodes.length) {
            val e = nodes.item(i) as? Element ?: continue
            val embed = e.getElementsByTagName("EMBEDDEDDATA").item(0) as? Element
            val name = e.attr("name") ?: e.descendantText("title") ?: "XDF table ${i + 1}"
            val addr = embed?.attr("mmedaddress")?.let(::parseNumber)
            val bits = embed?.attr("mmedelementsizebits")?.toIntOrNull()
            val rows = embed?.attr("mmedrowcount")?.toIntOrNull()?.coerceIn(1, 512) ?: 1
            val cols = embed?.attr("mmedcolcount")?.toIntOrNull()?.coerceIn(1, 512) ?: 1
            val math = e.getElementsByTagName("MATH").item(0) as? Element
            val equation = math?.attr("equation") ?: ""
            val units = e.attr("units") ?: e.descendantText("units") ?: ""
            val id = e.attr("uniqueid") ?: "xdf-table-$i"
            val note = when {
                embed == null -> "No embedded-data block; preview unavailable."
                addr == null -> "Address could not be parsed; preview unavailable."
                bits !in listOf(8, 16, 32) -> "Element size unsupported for preview (only 8/16/32-bit values)."
                else -> "Imported metadata only. Confirm XDF address origin, endian, stride and axis definitions against the exact BIN."
            }
            out += DefinitionItem(id, name, "TABLE", addr, rows, cols, bits, units, equation, note)
            if (note != "Imported metadata only. Confirm XDF address origin, endian, stride and axis definitions against the exact BIN.") warnings += "$name: $note"
        }
        val constants = root.getElementsByTagName("XDFCONSTANT")
        for (i in 0 until constants.length) {
            val e = constants.item(i) as? Element ?: continue
            val name = e.attr("name") ?: e.descendantText("title") ?: "XDF constant ${i + 1}"
            val embed = e.getElementsByTagName("EMBEDDEDDATA").item(0) as? Element
            out += DefinitionItem(
                e.attr("uniqueid") ?: "xdf-constant-$i", name, "CONSTANT",
                embed?.attr("mmedaddress")?.let(::parseNumber),
                elementBits = embed?.attr("mmedelementsizebits")?.toIntOrNull(),
                equation = (e.getElementsByTagName("MATH").item(0) as? Element)?.attr("equation") ?: "",
                notes = "Metadata imported; scaling and signedness require review."
            )
        }
        if (out.isEmpty()) warnings += "No recognised XDFTABLE or XDFCONSTANT nodes found. This XDF variant may not be supported."
        warnings += "XDF addresses are not assumed to be BIN offsets. Verify address base, bank mapping, endian, strides, axes, scaling and checksum."
        return ImportedDefinition("XDF", title, out, warnings.distinct())
    }

    fun importAdx(bytes: ByteArray): ImportedDefinition {
        require(bytes.isNotEmpty()) { "ADX file is empty" }
        val root = document(bytes).documentElement
        val title = root.attr("name", "title") ?: root.descendantText("title") ?: "Imported ADX"
        val out = mutableListOf<DefinitionItem>()
        val warnings = mutableListOf<String>()
        val tags = listOf("Data", "Parameter", "Flag", "Item", "Packet", "Mode")
        var n = 0
        for (tag in tags) {
            val nodes = root.getElementsByTagName(tag)
            for (i in 0 until nodes.length) {
                val e = nodes.item(i) as? Element ?: continue
                val name = e.attr("name", "title", "id") ?: e.descendantText("Name") ?: "$tag ${n + 1}"
                val address = e.attr("offset", "address", "addr")?.let(::parseNumber)
                val equation = e.attr("equation", "formula", "math") ?: ""
                out += DefinitionItem(
                    e.attr("id") ?: "adx-$n", name, tag.uppercase(), address,
                    units = e.attr("units", "unit") ?: "", equation = equation,
                    notes = "ADX metadata only; packet layout, checksum, request timing and scaling must be verified."
                )
                n++
            }
        }
        if (out.isEmpty()) warnings += "No common ADX metadata nodes found. This ADX dialect may need a dedicated parser."
        warnings += "Importing an ADX does not alter the live ALDL decoder. Verify offsets, masks, signedness, formulas and request frames."
        return ImportedDefinition("ADX", title, out, warnings.distinct())
    }

    fun previewTable(item: DefinitionItem, bin: ByteArray, confirmedFileOffset: Long, bigEndian: Boolean = true): TablePreview {
        if (item.kind != "TABLE") return TablePreview(item, emptyList(), listOf("Selected item is not a table."))
        val bits = item.elementBits ?: return TablePreview(item, emptyList(), listOf("Missing element size."))
        if (bits !in listOf(8, 16, 32)) return TablePreview(item, emptyList(), listOf("Unsupported element size."))
        val bytesPer = bits / 8
        val count = item.rows.toLong() * item.columns.toLong()
        val start = confirmedFileOffset
        val end = start + count * bytesPer
        if (start < 0 || end > bin.size || end < start)
            return TablePreview(item, emptyList(), listOf("Selected file offset/range is outside the BIN. No data read."))
        val values = (0 until item.rows).map { r ->
            (0 until item.columns).map { c ->
                val index = start + (r.toLong() * item.columns + c) * bytesPer
                var v = 0L
                if (bigEndian) for (j in 0 until bytesPer) v = (v shl 8) or (bin[(index + j).toInt()].toLong() and 0xff)
                else for (j in bytesPer - 1 downTo 0) v = (v shl 8) or (bin[(index + j).toInt()].toLong() and 0xff)
                v
            }
        }
        return TablePreview(item, values, listOf("Raw unsigned values only. XDF math, signedness, axis, stride and endian metadata have not been applied."))
    }
}

data class VeCorrectionInput(val rpm: Double, val mapKpa: Double, val targetAfr: Double, val measuredAfr: Double, val currentVe: Double, val hits: Int)
data class VeCorrectionSuggestion(val rpm: Double, val mapKpa: Double, val currentVe: Double, val suggestedVe: Double, val correctionPercent: Double, val confidence: String)
object VeCorrectionAdvisor {
    fun suggest(input: VeCorrectionInput): VeCorrectionSuggestion? {
        if (input.rpm < 500 || input.mapKpa !in 20.0..400.0 || input.targetAfr !in 8.0..22.0 ||
            input.measuredAfr !in 8.0..22.0 || input.currentVe !in 1.0..250.0 || input.hits < 5) return null
        val raw = ((input.measuredAfr / input.targetAfr) - 1.0) * 100.0
        val correction = raw.coerceIn(-20.0, 20.0)
        val suggested = input.currentVe * (1.0 + correction / 100.0)
        val confidence = if (input.hits >= 30 && kotlin.math.abs(raw) <= 15) "MEDIUM (repeatable samples required)" else "LOW — collect more stable samples"
        return VeCorrectionSuggestion(input.rpm, input.mapKpa, input.currentVe, suggested, correction, confidence)
    }
}
