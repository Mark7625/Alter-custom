package org.rsmod.content.other.emirsarena.duel

import org.rsmod.map.CoordGrid

/**
 * One of the four walled combat areas at Emir's Arena. Bounds are the walkable interior read from
 * the map's collision flags (the walls are two tiles thick on the long sides, so the interior is a
 * little smaller than the wall outline).
 */
data class DuelArena(
    val name: String,
    val minX: Int,
    val maxX: Int,
    val minZ: Int,
    val maxZ: Int,
    val level: Int = 0,
) {
    val centreX: Int
        get() = (minX + maxX) / 2

    val centreZ: Int
        get() = (minZ + maxZ) / 2

    fun contains(coords: CoordGrid): Boolean =
        coords.level == level && coords.x in minX..maxX && coords.z in minZ..maxZ

    /**
     * Where the two duellists start. Adjacent for "No Movement" duels so melee can connect, a few
     * tiles apart otherwise.
     */
    fun startPositions(noMovement: Boolean): Pair<CoordGrid, CoordGrid> {
        val gap = if (noMovement) 1 else START_GAP
        val westX = centreX - gap / 2
        val eastX = westX + gap
        return CoordGrid(westX, centreZ, level) to CoordGrid(eastX, centreZ, level)
    }

    /** The forfeit trapdoors: a 1x3 loc against the west and east walls. */
    val trapdoors: List<CoordGrid>
        get() = listOf(CoordGrid(minX, centreZ - 1, level), CoordGrid(maxX, centreZ - 1, level))

    companion object {
        private const val START_GAP = 6

        val ALL: List<DuelArena> =
            listOf(
                DuelArena("north-west", minX = 3334, maxX = 3351, minZ = 3246, maxZ = 3256),
                DuelArena("north-east", minX = 3370, maxX = 3387, minZ = 3246, maxZ = 3256),
                DuelArena("south-west", minX = 3334, maxX = 3351, minZ = 3208, maxZ = 3218),
                DuelArena("south-east", minX = 3370, maxX = 3387, minZ = 3208, maxZ = 3218),
            )

        fun at(coords: CoordGrid): DuelArena? = ALL.firstOrNull { it.contains(coords) }
    }
}
