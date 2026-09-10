package org.rsmod.content.bosses.vorkath

import org.rsmod.map.CoordGrid

/**
 * Capture-derived layout: all 200 casts use 48 three-by-three cells, eight edge segments, then
 * the player tile if it was not already selected (56 or 57 distinct pools).
 *
 * Grid/ranges/order and boss exclusion are observed. Uniform choice among each cell's walkable
 * candidates is inferred; decoded packets cannot establish the server's random-number routine.
 */
internal object VorkathAcidLayout {
    private const val BOSS_SIZE = 7

    fun select(
        boss: CoordGrid,
        player: CoordGrid,
        isWalkable: (CoordGrid) -> Boolean,
        choose: (List<CoordGrid>) -> CoordGrid,
    ): Set<CoordGrid> {
        val selected = linkedSetOf<CoordGrid>()
        fun available(tile: CoordGrid): Boolean =
            !occupiesBoss(boss, tile) && isWalkable(tile)

        fun sample(x: IntRange, z: IntRange) {
            val candidates = buildList {
                for (offsetZ in z) {
                    for (offsetX in x) {
                        val tile = boss.translate(offsetX, offsetZ)
                        if (available(tile)) add(tile)
                    }
                }
            }
            // A changed collision map must not create unreachable pools or an endless reroll.
            if (candidates.isEmpty()) return
            val tile = choose(candidates)
            require(tile in candidates) { "Acid selection returned a tile outside its capture cell" }
            selected += tile
        }

        // Canonical anchor (2269,4062): grid origin (2262,4055), outer bounds (2282,4075).
        for (row in 0..6) {
            for (column in 0..6) {
                if (row == 3 && column == 3) continue
                val x = -7 + column * 3
                val z = -7 + row * 3
                sample(x..x + 2, z..z + 2)
            }
        }

        // South, north, west, east; exactly this packet order in every supplied cast.
        sample(-3..-1, -8..-8)
        sample(7..9, -8..-8)
        sample(-3..-1, 14..14)
        sample(7..9, 14..14)
        sample(-8..-8, -3..-1)
        sample(-8..-8, 7..9)
        sample(14..14, -3..-1)
        sample(14..14, 7..9)

        if (available(player)) selected += player
        return selected
    }

    /** Seven centre tiles of the southern edge are untouched except for the forced player pool. */
    fun exitLane(boss: CoordGrid): Set<CoordGrid> =
        (0 until BOSS_SIZE).mapTo(linkedSetOf()) { boss.translate(it, -8) }

    private fun occupiesBoss(boss: CoordGrid, tile: CoordGrid): Boolean =
        tile.x in boss.x until boss.x + BOSS_SIZE &&
            tile.z in boss.z until boss.z + BOSS_SIZE
}
