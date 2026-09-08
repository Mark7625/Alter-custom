package org.rsmod.api.game.process.npc.hunt

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.map.CoordGrid

class AggressionToleranceTest {
    private val uid = PlayerUid(1)

    @Test
    fun `a player becomes tolerated after ten minutes in one map square`() {
        val tolerance = AggressionTolerance()
        val tile = CoordGrid(3222, 3218)
        tolerance.record(uid, tile, cycle = 0)
        assertFalse(tolerance.isTolerant(uid, cycle = 0))
        tolerance.record(uid, tile.translateX(5), cycle = 999)
        assertFalse(tolerance.isTolerant(uid, cycle = 999))
        tolerance.record(uid, tile.translateX(5), cycle = 1000)
        assertTrue(tolerance.isTolerant(uid, cycle = 1000))
    }

    @Test
    fun `leaving the map square restarts the timer`() {
        val tolerance = AggressionTolerance()
        tolerance.record(uid, CoordGrid(3222, 3218), cycle = 0)
        tolerance.record(uid, CoordGrid(3222, 3218), cycle = 1500)
        assertTrue(tolerance.isTolerant(uid, cycle = 1500))
        // 3264 is the first tile of the next map square east.
        tolerance.record(uid, CoordGrid(3264, 3218), cycle = 1501)
        assertFalse(tolerance.isTolerant(uid, cycle = 1501))
        assertFalse(tolerance.isTolerant(uid, cycle = 2400))
        tolerance.record(uid, CoordGrid(3270, 3218), cycle = 2501)
        assertTrue(tolerance.isTolerant(uid, cycle = 2501))
    }

    @Test
    fun `unknown players are never tolerated`() {
        val tolerance = AggressionTolerance()
        assertFalse(tolerance.isTolerant(PlayerUid(7), cycle = 5000))
    }
}
