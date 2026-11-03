package com.dan.ose11p

import org.junit.Assert.*
import org.junit.Test

class WorkshopEngineTest {
    @Test fun decodesStandardDtcBytes() {
        val result = DtcDecoder.decode(byteArrayOf(0x01, 0x71), DtcStatus.STORED)
        assertEquals("P0171", result.single().code)
        assertEquals(DtcStatus.STORED, result.single().status)
    }
    @Test fun decodesCommonPids() {
        assertEquals(2000.0, StandardPidDecoder.decode(0x0C, intArrayOf(0x1F, 0x40))!!.value!!, 0.001)
        assertEquals(90.0, StandardPidDecoder.decode(0x05, intArrayOf(130))!!.value!!, 0.001)
        assertNull(StandardPidDecoder.decode(0x99, intArrayOf(1)))
    }
    @Test fun tuneGuardFailsClosed() {
        val result = TuneGuard.validate("OBD2_J1979", 65536, true, true, true, true)
        assertFalse(result.allowed)
        assertTrue(result.reasons.isNotEmpty())
    }
    @Test fun tuneGuardRequiresEverySafetyGate() {
        assertTrue(TuneGuard.validate("GM_DELPHI_11P_V104", 65536, true, true, true, true).allowed)
        assertFalse(TuneGuard.validate("GM_DELPHI_11P_V104", 65536, true, false, true, true).allowed)
    }
    @Test fun dynoRejectsInvalidInputsAndComputesEstimate() {
        assertNull(DynoMath.estimate(0.0, 80.0, 1.0, 3000.0))
        assertNotNull(DynoMath.estimate(1500.0, 100.0, 1.0, 4000.0))
        assertEquals(10.0, DynoMath.comparePercent(100.0, 110.0)!!, 0.001)
    }

    @Test fun multiMakeCatalogDoesNotClaimOemEnhancedCoverage() {
        assertTrue(VehicleSupportCatalog.manufacturers.size >= 10)
        assertTrue(VehicleSupportCatalog.manufacturers.all { it.enhancedStatus.contains("required") })
    }

    @Test fun delcoFamiliesFailClosedWithoutVerifiedDefinition() {
        val unsupported808 = VehicleSupportCatalog.matchDelco("DELCO_808", null, null)
        assertFalse(unsupported808.supported)
        val wrongOs = VehicleSupportCatalog.matchDelco("OSE_11P_V104", "OSE $11P V103", "abc")
        assertFalse(wrongOs.supported)
        val noFingerprint = VehicleSupportCatalog.matchDelco("OSE_11P_V104", "OSE $11P V104", null)
        assertFalse(noFingerprint.supported)
        val identityMatch = VehicleSupportCatalog.matchDelco("OSE_11P_V104", "OSE $11P V104", "sha256:" + "a".repeat(64))
        assertFalse(identityMatch.supported)
        assertTrue(identityMatch.explanation.contains("allowlist"))
    }

    @Test fun genericVehicleSelectionDoesNotUnlockWriteSupport() {
        val ford = VehicleSupportCatalog.manufacturer("FORD")!!
        assertTrue(ford.genericObd2Note.contains("Generic emissions"))
        assertFalse(TuneGuard.validate("OBD2_J1979", 65536, true, true, true, true).allowed)
    }
}