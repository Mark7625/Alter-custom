package org.rsmod.content.bosses.vorkath

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.rsmod.map.CoordGrid

class VorkathSafeLaneTest {
    private val boss = CoordGrid(2269, 4062)

    @Test
    fun capturedCentreExitLaneContainsSevenContiguousTiles() {
        val lane = VorkathAcidLayout.exitLane(boss)
        assertEquals((2269..2275).map { CoordGrid(it, 4054) }, lane.toList())
    }

    @Test
    fun blockedExitTileIsExcludedFromUsableLane() {
        val blocked = CoordGrid(2272, 4054)
        val player = CoordGrid(2271, 4061)
        val pools = VorkathAcidLayout.select(boss, player, { it != blocked }, List<CoordGrid>::first)
        val usable = VorkathAcidLayout.exitLane(boss).filter { it != blocked && it !in pools }
        assertEquals(6, usable.size)
        assertFalse(blocked in usable)
    }

    @Test
    fun standingOnTheExitLanePlacesOnePoolWithoutRandomlyBlockingTheRemainingLane() {
        val player = CoordGrid(2272, 4054)
        val pools = VorkathAcidLayout.select(boss, player, { true }, List<CoordGrid>::first)
        val lane = VorkathAcidLayout.exitLane(boss)
        assertEquals(setOf(player), pools.intersect(lane))
        assertTrue((2269..2271).all { CoordGrid(it, 4054) !in pools })
        assertTrue((2273..2275).all { CoordGrid(it, 4054) !in pools })
    }
}
