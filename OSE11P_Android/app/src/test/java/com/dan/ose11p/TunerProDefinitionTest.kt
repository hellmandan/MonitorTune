package com.dan.ose11p

import org.junit.Assert.*
import org.junit.Test

class TunerProDefinitionTest {
    @Test fun importsXdfTableMetadataAndPreviewsConfirmedOffset() {
        val xml = """<XDFFORMAT><XDFTITLE>Test Delco</XDFTITLE><XDFTABLE uniqueid="1" name="Main VE"><EMBEDDEDDATA mmedaddress="0x10" mmedelementsizebits="8" mmedrowcount="2" mmedcolcount="2"/><MATH equation="X*0.5"/></XDFTABLE></XDFFORMAT>""".toByteArray()
        val def = TunerProDefinitionImporter.importXdf(xml)
        assertEquals("Test Delco", def.title)
        val table = def.items.single()
        assertEquals(2, table.rows)
        val preview = TunerProDefinitionImporter.previewTable(table, ByteArray(64) { it.toByte() }, 16)
        assertEquals(listOf(listOf(16L, 17L), listOf(18L, 19L)), preview.values)
        assertTrue(preview.warnings.single().contains("Raw unsigned"))
    }

    @Test fun refusesOutOfRangeXdfPreview() {
        val item = DefinitionItem("x", "Bad", "TABLE", 0, 8, 8, 16)
        val preview = TunerProDefinitionImporter.previewTable(item, ByteArray(10), 4)
        assertTrue(preview.values.isEmpty())
        assertTrue(preview.warnings.single().contains("outside"))
    }

    @Test fun importsAdxMetadataWithoutClaimingDecoderSupport() {
        val xml = """<ADX name="OSE test"><Data name="RPM" offset="0x12"/><Parameter name="Coolant"/></ADX>""".toByteArray()
        val def = TunerProDefinitionImporter.importAdx(xml)
        assertEquals("ADX", def.format)
        assertEquals(2, def.items.size)
        assertTrue(def.warnings.any { it.contains("does not alter") })
    }

    @Test fun afrCorrectionIsCappedAndRequiresEnoughHits() {
        val insufficient = VeCorrectionAdvisor.suggest(VeCorrectionInput(3000.0, 150.0, 11.5, 13.0, 80.0, 4))
        assertNull(insufficient)
        val result = VeCorrectionAdvisor.suggest(VeCorrectionInput(3000.0, 150.0, 11.5, 13.0, 80.0, 10))!!
        assertEquals(((13.0 / 11.5) - 1.0) * 100.0, result.correctionPercent, 0.001)
        assertEquals(80.0 * (13.0 / 11.5), result.suggestedVe, 0.001)
    }
}