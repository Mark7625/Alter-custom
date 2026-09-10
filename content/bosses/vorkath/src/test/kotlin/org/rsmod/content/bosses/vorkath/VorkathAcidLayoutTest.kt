package org.rsmod.content.bosses.vorkath

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.rsmod.map.CoordGrid

class VorkathAcidLayoutTest {
    private val boss = CoordGrid(2269, 4062)

    @Test
    fun firstCapturedLayoutReplaysEveryProjectileDestinationInOrder() {
        // rsprox-3279:118924..119092, acid cast t5169. Final destination is the player tile.
        val captured = listOf(
            2263 to 4056, 2267 to 4057, 2270 to 4056, 2272 to 4057, 2274 to 4055,
            2277 to 4055, 2282 to 4055, 2263 to 4060, 2266 to 4060, 2270 to 4058,
            2271 to 4059, 2276 to 4058, 2278 to 4058, 2281 to 4060, 2262 to 4062,
            2266 to 4063, 2269 to 4061, 2272 to 4061, 2276 to 4063, 2278 to 4062,
            2282 to 4063, 2264 to 4065, 2265 to 4066, 2268 to 4065, 2276 to 4064,
            2278 to 4064, 2281 to 4066, 2264 to 4067, 2266 to 4068, 2268 to 4067,
            2273 to 4069, 2274 to 4069, 2279 to 4069, 2282 to 4069, 2264 to 4072,
            2267 to 4070, 2268 to 4072, 2272 to 4071, 2274 to 4071, 2277 to 4071,
            2281 to 4072, 2264 to 4075, 2266 to 4075, 2269 to 4075, 2272 to 4074,
            2276 to 4073, 2277 to 4075, 2282 to 4075,
            2266 to 4054, 2277 to 4054, 2267 to 4076, 2277 to 4076,
            2261 to 4060, 2261 to 4071, 2283 to 4060, 2283 to 4070,
            2271 to 4061,
        ).map { (x, z) -> CoordGrid(x, z) }
        var index = 0
        val selected = VorkathAcidLayout.select(boss, captured.last(), { true }) { candidates ->
            val tile = captured[index++]
            assertTrue(tile in candidates, "Capture destination $tile missing from cell $index")
            tile
        }
        assertEquals(56, index)
        assertEquals(captured, selected.toList())
    }

    @Test
    fun playerAlreadySelectedProduces56PoolsWithoutADuplicateProjectile() {
        val player = CoordGrid(2262, 4055)
        val pools = VorkathAcidLayout.select(boss, player, { true }, List<CoordGrid>::first)
        assertEquals(56, pools.size)
        assertTrue(player in pools)
    }

    @Test
    fun centreExitLaneSurvivesEveryCellChoiceExceptTheForcedPlayerTile() {
        val expectedLane = (2269..2275).mapTo(linkedSetOf()) { CoordGrid(it, 4054) }
        assertEquals(expectedLane, VorkathAcidLayout.exitLane(boss))
        for (offset in 0..8) {
            val pools = VorkathAcidLayout.select(boss, CoordGrid(2271, 4061), { true }) {
                it[offset % it.size]
            }
            assertTrue(pools.size in 56..57)
            assertTrue(pools.none(expectedLane::contains))
            assertTrue(pools.none { it.z == 4053 })
            assertTrue(pools.none { it.x in 2269..2275 && it.z in 4062..4068 })
        }
        val standingInLane = CoordGrid(2272, 4054)
        val pools = VorkathAcidLayout.select(boss, standingInLane, { true }, List<CoordGrid>::first)
        assertEquals(setOf(standingInLane), pools.intersect(expectedLane))
    }

    @Test
    fun instanceTranslationPreservesTheNativeLayout() {
        val player = CoordGrid(2271, 4061)
        val native = VorkathAcidLayout.select(boss, player, { true }, List<CoordGrid>::first)
        val translated = VorkathAcidLayout.select(
            boss.translate(4096, 2048), player.translate(4096, 2048),
            { true }, List<CoordGrid>::first,
        )
        assertEquals(native.mapTo(linkedSetOf()) { it.translate(4096, 2048) }, translated)
    }

    @Test
    fun blockedCellsAndPlayerTileCannotCreateUnwalkablePoolsOrRerollForever() {
        val player = CoordGrid(2271, 4061)
        val blocked = (2262..2264).flatMap { x ->
            (4055..4057).map { z -> CoordGrid(x, z) }
        }.toSet() + player
        val pools = VorkathAcidLayout.select(boss, player, { it !in blocked }, List<CoordGrid>::first)
        assertEquals(55, pools.size)
        assertFalse(pools.any(blocked::contains))
    }
}
