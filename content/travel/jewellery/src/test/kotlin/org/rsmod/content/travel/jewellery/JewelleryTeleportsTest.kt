package org.rsmod.content.travel.jewellery

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JewelleryTeleportsTest {
    @Test
    fun `no obj belongs to two pieces of jewellery`() {
        val objs = JewelleryTeleports.all.flatMap { it.objs }
        assertEquals(objs.toSet().size, objs.size)
    }

    @Test
    fun `charges run down to the uncharged form or to dust`() {
        val glory = JewelleryTeleports.all.first { "obj.amulet_of_glory_4" in it.charged }
        assertEquals("obj.amulet_of_glory_3", glory.afterCharge("obj.amulet_of_glory_4"))
        assertEquals("obj.amulet_of_glory", glory.afterCharge("obj.amulet_of_glory_1"))
        assertEquals(0, glory.chargesAfter("obj.amulet_of_glory_1"))
        assertEquals(3, glory.chargesAfter("obj.amulet_of_glory_4"))
        assertNull(glory.chargesAfter("obj.amulet_of_glory_inf"))
        assertEquals("obj.amulet_of_glory_inf", glory.afterCharge("obj.amulet_of_glory_inf"))

        val dueling = JewelleryTeleports.all.first { "obj.ring_of_dueling_8" in it.charged }
        assertNull(dueling.afterCharge("obj.ring_of_dueling_1"))
    }

    @Test
    fun `every destination has a name and known destinations sit on the map`() {
        for (item in JewelleryTeleports.all) {
            for (destination in item.destinations) {
                assertTrue(destination.name.isNotBlank())
                val coords = destination.coords ?: continue
                assertTrue(coords.x in 1024..4096, "${destination.name} x=${coords.x}")
                assertTrue(coords.z in 2048..12800, "${destination.name} z=${coords.z}")
            }
        }
    }
}
