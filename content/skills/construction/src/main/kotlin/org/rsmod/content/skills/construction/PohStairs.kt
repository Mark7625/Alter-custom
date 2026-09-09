package org.rsmod.content.skills.construction

/**
 * Staircase connectors that exist as linked pairs across floors.
 *
 * A staircase is one unit spanning two rooms: hall staircases continue into a free top/bottom half,
 * while spiral staircases consume a second set of materials for the matching spiral (wiki: "the
 * player needs the resources to build a matching staircase"). Building or removing one end must
 * mirror to the other, or the orphaned half clips through the floor between the rooms.
 */
object PohStairs {
    /** Hotspot name that hosts staircases in halls and the dungeon stairs room. */
    const val STAIR_SPACE_NAME = "Stair Space"

    /** The connector mirrored into the linked room, with the extra materials it consumes. */
    data class Mirror(val builtLoc: String, val materials: List<Pair<String, Int>> = emptyList())

    val MIRRORS: Map<String, Mirror> =
        mapOf(
            "loc.poh_stairs_3" to Mirror("loc.poh_stairstop_3"),
            "loc.poh_stairs_4" to Mirror("loc.poh_stairstop_4"),
            "loc.poh_stairs_5" to Mirror("loc.poh_stairstop_5"),
            "loc.poh_stairstop_3" to Mirror("loc.poh_stairs_3"),
            "loc.poh_stairstop_4" to Mirror("loc.poh_stairs_4"),
            "loc.poh_stairstop_5" to Mirror("loc.poh_stairs_5"),
            "loc.poh_spiralstairs" to
                Mirror(
                    "loc.poh_spiralstairs",
                    listOf("obj.plank_teak" to 10, "obj.limestonebrick" to 7),
                ),
            "loc.poh_spiralstairs_2" to
                Mirror(
                    "loc.poh_spiralstairs_2",
                    listOf("obj.plank_teak" to 10, "obj.marble_block" to 7),
                ),
        )

    /** Levels a built connector continues toward: bottoms up, tops down, spirals both ways. */
    fun linkedDirections(builtLoc: String): List<Int> =
        when (builtLoc) {
            "loc.poh_stairs_3",
            "loc.poh_stairs_4",
            "loc.poh_stairs_5" -> listOf(1)
            "loc.poh_stairstop_3",
            "loc.poh_stairstop_4",
            "loc.poh_stairstop_5" -> listOf(-1)
            "loc.poh_spiralstairs",
            "loc.poh_spiralstairs_2" -> listOf(-1, 1)
            else -> emptyList()
        }
}
