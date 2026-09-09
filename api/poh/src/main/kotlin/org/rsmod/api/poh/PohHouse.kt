package org.rsmod.api.poh

import org.rsmod.map.CoordGrid

/**
 * A room position within the house: [level] is the region destination level (0 = dungeon, 1 =
 * ground floor, 2 = upper floor) and [gridX]/[gridZ] address the 7x7 room grid.
 */
public data class PohRoomSlot(val level: Int, val gridX: Int, val gridZ: Int) {
    public fun translate(edge: PohDoorEdge): PohRoomSlot =
        PohRoomSlot(level, gridX + edge.deltaX, gridZ + edge.deltaZ)

    public fun persistenceKey(): String = "${level}_${gridX}_$gridZ"

    public companion object {
        public fun fromPersistenceKey(key: String): PohRoomSlot? {
            val parts = key.split('_')
            if (parts.size != 3) return null
            val (level, gridX, gridZ) = parts.map { it.toIntOrNull() ?: return null }
            return PohRoomSlot(level, gridX, gridZ)
        }
    }
}

/** A built room: [type] is a [PohRoomType.key]; [rotation] is quarter-turns clockwise (0-3). */
public data class PohRoom(val type: String, val rotation: Int)

/** A furniture position: the room slot plus the [PohHotspot.index] within that room's template. */
public data class PohHotspotSlot(
    val level: Int,
    val gridX: Int,
    val gridZ: Int,
    val hotspotIndex: Int,
) {
    public val roomSlot: PohRoomSlot
        get() = PohRoomSlot(level, gridX, gridZ)

    public fun persistenceKey(): String = "${level}_${gridX}_${gridZ}_$hotspotIndex"

    public companion object {
        public fun fromPersistenceKey(key: String): PohHotspotSlot? {
            val parts = key.split('_')
            if (parts.size != 4) return null
            val (level, gridX, gridZ, index) = parts.map { it.toIntOrNull() ?: return null }
            return PohHotspotSlot(level, gridX, gridZ, index)
        }
    }
}

/**
 * The in-memory model of a player's house. [size], [style] and [location] mirror the real varbits
 * (`poh_house_size`, `poh_house_style`, `poh_house_location`); rooms and furniture persist through
 * [PohAttributes].
 */
public class PohHouse(
    public var size: Int,
    public var style: Int,
    public var location: Int,
    public val rooms: MutableMap<PohRoomSlot, PohRoom> = mutableMapOf(),
    public val furniture: MutableMap<PohHotspotSlot, String> = mutableMapOf(),
) {
    /** Grid cells outside the current buildable window reject room placement. */
    public fun inBuildableArea(slot: PohRoomSlot): Boolean {
        val start = (PohConstants.GRID_LENGTH - size) / 2
        val end = start + size - 1
        return slot.gridX in start..end && slot.gridZ in start..end
    }

    public fun furnitureAt(slot: PohRoomSlot): Map<PohHotspotSlot, String> =
        furniture.filterKeys { it.roomSlot == slot }
}

/** The nine house locations; [varValue] is stored in `varbit.poh_house_location` (0 = no house). */
public enum class PohLocation(
    public val varValue: Int,
    public val displayName: String,
    public val exitCoords: CoordGrid,
    public val relocateCost: Int,
) {
    RIMMINGTON(1, "Rimmington", CoordGrid(2953, 3224, 0), 5_000),
    TAVERLEY(2, "Taverley", CoordGrid(2893, 3465, 0), 5_000),
    POLLNIVNEACH(3, "Pollnivneach", CoordGrid(3340, 3003, 0), 7_500),
    RELLEKKA(4, "Rellekka", CoordGrid(2670, 3631, 0), 10_000),
    BRIMHAVEN(5, "Brimhaven", CoordGrid(2757, 3178, 0), 15_000),
    YANILLE(6, "Yanille", CoordGrid(2544, 3095, 0), 25_000),
    KOUREND(7, "Hosidius", CoordGrid(1742, 3517, 0), 8_750),
    PRIFDDINAS(8, "Prifddinas", CoordGrid(3239, 6079, 0), 25_000),
    ALDARIN(9, "Aldarin", CoordGrid(1422, 2964, 0), 7_500);

    public companion object {
        public fun forVarValue(value: Int): PohLocation? =
            entries.firstOrNull { it.varValue == value }
    }
}

public object PohConstants {
    /** The room grid is always addressed as 7x7; lower Construction levels shrink the window. */
    public const val GRID_LENGTH: Int = 7

    public const val ZONE_TILE_LENGTH: Int = 8

    /**
     * The client renders a 13x13-zone build area centred on the player's zone at rebuild time, so
     * every zone a build area can reach from a walkable tile must be landscaped or its height cliff
     * at the mapped/unmapped boundary shows inside the draw distance. A 16x16 small-region slot
     * cannot cover that (16 > 13 leaves bare corners); houses therefore allocate a 40x40
     * large-region slot, centre the grid, and landscape the lawn ring plus six zones on every
     * side - the maximum reach of a build area centred anywhere on the walkable area.
     */
    public const val BUILD_AREA_ZONE_RADIUS: Int = 6

    /**
     * Region zone of room grid cell (0, 0). Centres the 7x7 grid plus its one-zone lawn ring (9
     * zones) inside the 40-zone large-region slot: rooms at zones 16-22, lawn ring at 15 and 23.
     */
    public const val GRID_ZONE_OFFSET: Int = 16

    /** Landscaped zone range: the lawn ring extended by the build-area reach on every side. */
    public const val LANDSCAPE_MIN_ZONE: Int = GRID_ZONE_OFFSET - 1 - BUILD_AREA_ZONE_RADIUS
    public const val LANDSCAPE_MAX_ZONE: Int =
        GRID_ZONE_OFFSET + GRID_LENGTH + BUILD_AREA_ZONE_RADIUS

    /** Region-local tile bounds of the walkable house area (rooms plus the lawn ring). */
    public const val WALKABLE_MIN_TILE: Int = (GRID_ZONE_OFFSET - 1) * ZONE_TILE_LENGTH
    public const val WALKABLE_MAX_TILE: Int =
        (GRID_ZONE_OFFSET + GRID_LENGTH + 1) * ZONE_TILE_LENGTH - 1

    public const val HOUSE_COST: Int = 1_000

    public const val EXIT_PORTAL_LOC: String = "loc.poh_exit_portal"

    /** Garden centrepiece hotspot loc; the exit portal is built here on house creation. */
    public const val GARDEN_CENTREPIECE_LOC: String = "loc.poh_crude_garden_1"

    /** Buildable grid dimension for a Construction level: 3x3 at 1 up to 7x7 at 60+. */
    public fun gridSize(constructionLevel: Int): Int =
        when {
            constructionLevel >= 60 -> 7
            constructionLevel >= 45 -> 6
            constructionLevel >= 30 -> 5
            constructionLevel >= 15 -> 4
            else -> 3
        }

    private val ROOM_COUNT_GATES =
        intArrayOf(26, 32, 38, 44, 50, 56, 62, 68, 74, 80, 86, 92, 96, 99)

    /** Maximum number of rooms for a Construction level: 20 at level 1 up to 34 at 99. */
    public fun maxRooms(constructionLevel: Int): Int =
        20 + ROOM_COUNT_GATES.count { constructionLevel >= it }
}
