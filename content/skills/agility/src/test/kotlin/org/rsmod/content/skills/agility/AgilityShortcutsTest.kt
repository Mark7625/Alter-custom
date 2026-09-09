package org.rsmod.content.skills.agility

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.content.skills.agility.shortcuts.AgilityShortcuts
import org.rsmod.content.skills.agility.shortcuts.ShortcutMove
import org.rsmod.map.CoordGrid

class AgilityShortcutsTest {
    @Test
    fun `shortcuts are listed in level order with sane requirements`() {
        val levels = AgilityShortcuts.all.map { it.level }
        assertEquals(levels.sorted(), levels, "shortcuts should be in level order")
        assertTrue(levels.all { it in 1..99 })
        assertTrue(AgilityShortcuts.all.all { it.xp >= 0.0 })
        assertTrue(AgilityShortcuts.all.flatMap { it.locs }.all { it.startsWith("loc.") })
    }

    @Test
    fun `balance and hop paths connect the two sides tile by tile`() {
        for (shortcut in AgilityShortcuts.all) {
            val tiles =
                when (val move = shortcut.move) {
                    is ShortcutMove.Balance -> move.tiles
                    is ShortcutMove.Hop -> move.stones
                    else -> continue
                }
            assertTrue(tiles.isNotEmpty(), "${shortcut.name} has an empty path")
            val full = listOf(shortcut.sideA) + tiles + shortcut.sideB
            val maxStep = if (shortcut.move is ShortcutMove.Hop) 3 else 1
            for (index in 1 until full.size) {
                val step = full[index - 1].chebyshevDistance(full[index])
                assertTrue(step in 1..maxStep, "${shortcut.name}: step $step from ${full[index - 1]}")
            }
        }
    }

    @Test
    fun `shared locs are told apart by distance`() {
        val cracks = AgilityShortcuts.all.filter { "loc.zeah_cata_crack" in it.locs }
        assertEquals(2, cracks.size)
        val nearSouthWest = CoordGrid(1646, 10000, 0)
        val chosen = cracks.minBy { it.distanceTo(nearSouthWest) }
        assertEquals(17, chosen.level)
        assertTrue(chosen.startsFromA(nearSouthWest))
        assertFalse(chosen.startsFromA(CoordGrid(1648, 10010, 0)))
    }

    @Test
    fun `level changes count as far away when picking a side`() {
        val chain = AgilityShortcuts.all.first { it.name == "Spikey chain" && it.level == 61 }
        assertTrue(chain.startsFromA(CoordGrid(3421, 3551, 0)))
        assertFalse(chain.startsFromA(CoordGrid(3421, 3551, 1)))
    }
}
