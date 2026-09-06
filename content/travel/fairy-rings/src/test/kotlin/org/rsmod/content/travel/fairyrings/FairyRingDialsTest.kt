package org.rsmod.content.travel.fairyrings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class FairyRingDialsTest {
    @Test
    fun `dial values spell the codes the cache table numbers them by`() {
        assertEquals("AIP", FairyRingDials.code(listOf(0, 0, 0)))
        assertEquals("AIQ", FairyRingDials.code(listOf(0, 0, 3)))
        assertEquals("BKS", FairyRingDials.code(listOf(3, 2, 1)))
        assertEquals("DKR", FairyRingDials.code(listOf(1, 2, 2)))
        assertEquals("CLQ", FairyRingDials.code(listOf(2, 1, 3)))
    }

    @Test
    fun `codes round-trip through dial values and table ids`() {
        for (first in 0 until FairyRingDials.POSITIONS) {
            for (second in 0 until FairyRingDials.POSITIONS) {
                for (third in 0 until FairyRingDials.POSITIONS) {
                    val dials = listOf(first, second, third)
                    val code = FairyRingDials.code(dials)
                    assertEquals(dials, FairyRingDials.dials(code), code)
                    assertEquals(dials, FairyRingDials.dials("${code[0]} ${code[1]} ${code[2]}"))
                    assertEquals(dials, FairyRingDials.fromTableId(FairyRingDials.tableId(dials)))
                }
            }
        }
        assertEquals(321, FairyRingDials.tableId(listOf(3, 2, 1)))
    }

    @Test
    fun `letters that are not on a dial are rejected`() {
        assertNull(FairyRingDials.dials("AIX"))
        assertNull(FairyRingDials.dials("IAP"))
        assertNull(FairyRingDials.dials("AI"))
        assertNull(FairyRingDials.dials("AIPS"))
    }

    @Test
    fun `rotation wraps around the four positions`() {
        assertEquals(1, FairyRingDials.rotate(0, 1))
        assertEquals(0, FairyRingDials.rotate(3, 1))
        assertEquals(3, FairyRingDials.rotate(0, -1))
        assertEquals(2, FairyRingDials.rotate(3, -1))
    }
}
