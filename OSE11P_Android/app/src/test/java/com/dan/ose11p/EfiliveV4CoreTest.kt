package com.dan.ose11p

import org.junit.Assert.*
import org.junit.Test

class EfLiveV4CoreTest {
    @Test fun hexParsingAndFormatting() {
        val bytes = V4Hex.parse("0xF4, 57 0A-00 AB")
        assertEquals("F4 57 0A 00 AB", V4Hex.format(bytes))
    }

    @Test fun checksumPoliciesAreExplicit() {
        val payload = byteArrayOf(0x01, 0x02, 0x03)
        assertEquals(6, V4Protocol.checksum(payload, V4Checksum.GM_8BIT_SUM))
        assertEquals(249, V4Protocol.checksum(payload, V4Checksum.GM_8BIT_COMPLEMENT))
        assertTrue(V4Protocol.validateChecksum(V4Protocol.appendChecksum(payload, V4Checksum.GM_8BIT_SUM), V4Checksum.GM_8BIT_SUM)!!)
    }

    @Test fun decodesEndianSignedAndScaling() {
        val def = V4VehicleDefinition("x", "test", 8192, parameters = listOf(
            V4Parameter("rpm", "RPM", 0, 2, V4Endian.BIG, false, 0.25),
            V4Parameter("temp", "Temp", 2, 1, V4Endian.LITTLE, true, 1.0, -40.0)
        ))
        val decoded = V4Protocol.decode(byteArrayOf(0x13, 0x88, 0x80.toByte()), def)
        assertEquals(1250.0, decoded.values[0].value, 0.0)
        assertEquals(-168.0, decoded.values[1].value, 0.0)
    }

    @Test fun importsOnlyMonitorTuneV4Schema() {
        val xml = """<MonitorTuneV4 id="demo" name="Demo ECU" baud="8192">
          <parameter id="rpm" name="RPM" offset="0" width="2" scale="0.25" units="rpm"/>
          <request id="data" name="Data" tx="F4 57 0A 00 AB" checksum="NONE"/>
          <macro id="read" name="Read" requiresConfirmation="false"><step request="data" delayMs="50"/></macro>
        </MonitorTuneV4>""".toByteArray()
        val def = V4DefinitionImporter.import(xml)
        assertEquals("Demo ECU", def.name)
        assertEquals(1, def.parameters.size)
        assertEquals(1, def.requests.size)
        assertEquals(1, def.macros.size)
    }

    @Test fun rejectsUnknownOrUnsafeXmlDialect() {
        try {
            V4DefinitionImporter.import("<!DOCTYPE x [<!ENTITY e SYSTEM 'file:///etc/passwd'>]><x>&e;</x>".toByteArray())
            fail("Should reject unknown root")
        } catch (_: Exception) {}
    }

    @Test fun recorderExportsAndChartIsBounded() {
        val p = V4Parameter("rpm", "RPM", 0)
        val recorder = V4SessionRecorder()
        recorder.add(listOf(V4DecodedValue(p, 2000, 2000.0)))
        val csv = recorder.toCsv(V4VehicleDefinition("x", "Test", 8192, parameters = listOf(p)))
        assertTrue(csv.contains("elapsed_ms,"))
        val chart = V4ChartHistory(2)
        (1L..3L).forEach { chart.add("rpm", V4ChartPoint(it, it.toDouble())) }
        assertEquals(2, chart.get("rpm").size)
        assertEquals(2.0, chart.get("rpm").first().value, 0.0)
    }
}
