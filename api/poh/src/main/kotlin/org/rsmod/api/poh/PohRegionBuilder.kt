package org.rsmod.api.poh

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.repo.region.RegionStaticTemplate
import org.rsmod.api.repo.region.RegionTemplate
import org.rsmod.map.zone.ZoneKey

/**
 * Turns a [PohHouse] into a [RegionStaticTemplate].
 *
 * Destination levels: 0 = dungeon, 1 = ground floor, 2 = upper floor, 3 = roofs (reserved and
 * unused - see [buildTemplate]). Each room's grid cell maps to region zone `(gridX +
 * GRID_ZONE_OFFSET, gridZ + GRID_ZONE_OFFSET)` - the grid and its lawn ring are centred inside the
 * 40x40 large-region slot, with the surrounding zones landscaped as visual padding.
 *
 * The per-zone setter is used deliberately: POH copies from the *style* source level into a *floor*
 * destination level, which `RegionStaticTemplate.copy()` cannot express.
 */
@Singleton
public class PohRegionBuilder @Inject constructor(private val dataStore: PohDataStore) {
    /**
     * Builds the region template for [house]: one template zone per built room, plus grass fill for
     * every remaining ground-floor zone.
     *
     * Roofs are deliberately omitted, so houses render open-topped - matching what live OSRS shows
     * in building mode. The style roof palettes (template zones `(1,2)`/`(3,2)`/`(5,2)`, roomKeys
     * `roof_a`/`roof_b`/`roof_c`) only hold complete 64-piece roof zones for the RS2-era styles;
     * the OSRS-era styles (hosidius, xmas2020, deathly, twisted) carry stub palettes of ~4 locs, so
     * a whole-zone roof copy cannot ship for all 13 styles. No map square in the rev-240 cache
     * composes a multi-room roof either: the only placed roof pieces outside the palettes are the
     * region-7257 showcase house's single verbatim `roof_a` zone copy over one isolated room (at
     * room level + 1, not a fixed top level), and the `roof_b`/`roof_c` hip-corner pieces are never
     * placed anywhere, leaving no evidenced piece-to-footprint mapping for adjacent rooms.
     */
    public fun buildTemplate(house: PohHouse): RegionStaticTemplate {
        val style = dataStore.style(house.style)
        val grassZone = templateZone(style, dataStore.room("grass_filler"))
        val hiddenZone = hiddenFillZone(style)
        return RegionTemplate.createLarge {
            for ((slot, room) in house.rooms) {
                val type = dataStore.room(room.type)
                val srcZone = templateZone(style, type)
                val destX = slot.gridX + PohConstants.GRID_ZONE_OFFSET
                val destZ = slot.gridZ + PohConstants.GRID_ZONE_OFFSET
                when (room.rotation and 0x3) {
                    0 -> this[destX, destZ, slot.level] = srcZone
                    1 -> this[destX, destZ, slot.level] = srcZone.rotate90()
                    2 -> this[destX, destZ, slot.level] = srcZone.rotate180()
                    3 -> this[destX, destZ, slot.level] = srcZone.rotate270()
                }
            }
            // Fill every remaining ground-level zone with lawn, and every remaining dungeon-level
            // zone with an invisible height-only spacer zone. The under-fill is not cosmetic: the
            // client stacks instance plane heights on the plane below, so a partially filled
            // plane 0 raises only the ground rooms above dungeon rooms - a uniform plane-0 fill
            // is what keeps the ground floor flat. The spacer zone renders nothing (no ground, no
            // locs - the decorated rock zone's wall-top models poke through the lawn above), and
            // the fill extends over every zone a 13x13 client build area can reach from a
            // walkable tile, so the height cliff at the mapped/unmapped boundary is never
            // rendered. Levels 2/3 stay unset.
            for (zoneX in PohConstants.LANDSCAPE_MIN_ZONE..PohConstants.LANDSCAPE_MAX_ZONE) {
                for (zoneZ in PohConstants.LANDSCAPE_MIN_ZONE..PohConstants.LANDSCAPE_MAX_ZONE) {
                    val gridX = zoneX - PohConstants.GRID_ZONE_OFFSET
                    val gridZ = zoneZ - PohConstants.GRID_ZONE_OFFSET
                    val ground = PohRoomSlot(PohFloor.GROUND.destLevel, gridX, gridZ)
                    if (!house.rooms.containsKey(ground)) {
                        this[zoneX, zoneZ, PohFloor.GROUND.destLevel] = grassZone
                    }
                    val dungeon = PohRoomSlot(PohFloor.DUNGEON.destLevel, gridX, gridZ)
                    if (!house.rooms.containsKey(dungeon)) {
                        this[zoneX, zoneZ, PohFloor.DUNGEON.destLevel] = hiddenZone
                    }
                }
            }
        }
    }

    /**
     * An empty zone of the style's extra template square: explicit flat heights (the same 240 units
     * per level as every template zone) but no underlays, overlays, or locs - it contributes
     * stacking height while rendering as black void.
     */
    private fun hiddenFillZone(style: PohStyle): ZoneKey {
        val baseZoneX = (style.extraRegion shr 8) * PohConstants.ZONE_TILE_LENGTH
        val baseZoneZ = (style.extraRegion and 0xFF) * PohConstants.ZONE_TILE_LENGTH
        return ZoneKey(baseZoneX, baseZoneZ, style.level)
    }

    /** Resolves the normal-map zone holding [room]'s template for [style]. */
    public fun templateZone(style: PohStyle, room: PohRoomType): ZoneKey {
        val region = if (room.extraSquare) style.extraRegion else style.region
        val baseZoneX = (region shr 8) * PohConstants.ZONE_TILE_LENGTH
        val baseZoneZ = (region and 0xFF) * PohConstants.ZONE_TILE_LENGTH
        return ZoneKey(baseZoneX + room.templateZoneX, baseZoneZ + room.templateZoneZ, style.level)
    }
}
