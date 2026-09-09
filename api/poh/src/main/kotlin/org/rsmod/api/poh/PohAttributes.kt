package org.rsmod.api.poh

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.player.stat.baseConstructionLvl
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.game.entity.Player

/**
 * House layout persistence through the `character_attrs` JSON path.
 * - [ROOMS]: `"<level>_<gridX>_<gridZ>" -> roomTypeIndex or (rotation shl 16)`
 * - [FURNITURE]: `"<level>_<gridX>_<gridZ>_<hotspotIndex>" -> furniture loc id`
 *
 * Room type and hotspot indices are the stable indices of the datagen resources, guarded by
 * `poh_index_manifest.json`. House size / style / location / servant state live in the real varbits
 * and persist through varp persistence - they are never duplicated here.
 */
public object PohAttributes {
    public val ROOMS: AttributeKey<MutableMap<String, Int>> =
        AttributeKey(persistenceKey = "poh_rooms")

    public val FURNITURE: AttributeKey<MutableMap<String, Int>> =
        AttributeKey(persistenceKey = "poh_furniture")

    /**
     * Packed [org.rsmod.map.CoordGrid] to relocate to on the next login (set on in-house logout).
     */
    public val LOGIN_EXIT_COORD: AttributeKey<Int> =
        AttributeKey(persistenceKey = "poh_login_exit_coord")

    private const val ROTATION_SHIFT: Int = 16
    private const val ROOM_INDEX_MASK: Int = (1 shl ROTATION_SHIFT) - 1

    public fun encodeRoom(roomTypeIndex: Int, rotation: Int): Int =
        roomTypeIndex or (rotation shl ROTATION_SHIFT)

    public fun decodeRoomIndex(value: Int): Int = value and ROOM_INDEX_MASK

    public fun decodeRoomRotation(value: Int): Int = (value shr ROTATION_SHIFT) and 0x3

    /** Decodes the persisted house layout, or `null` when the player owns no house. */
    public fun load(player: Player, dataStore: PohDataStore): PohHouse? {
        val location = player.pohHouseLocation
        if (location == 0) {
            return null
        }
        val house =
            PohHouse(
                size = PohConstants.gridSize(player.constructionLevelForHouse()),
                style = player.pohHouseStyle,
                location = location,
            )
        val rooms = player.attr[ROOMS].orEmpty()
        for ((key, value) in rooms) {
            val slot = PohRoomSlot.fromPersistenceKey(key) ?: continue
            val room = dataStore.room(decodeRoomIndex(value))
            house.rooms[slot] = PohRoom(room.key, decodeRoomRotation(value))
        }
        val furniture = player.attr[FURNITURE].orEmpty()
        for ((key, value) in furniture) {
            val slot = PohHotspotSlot.fromPersistenceKey(key) ?: continue
            house.furniture[slot] = RSCM.getReverseMapping(RSCMType.LOC, value)
        }
        return house
    }

    /** Writes the full house layout back into the persistent attribute blobs. */
    public fun save(player: Player, house: PohHouse, dataStore: PohDataStore) {
        val rooms = mutableMapOf<String, Int>()
        for ((slot, room) in house.rooms) {
            val type = dataStore.room(room.type)
            rooms[slot.persistenceKey()] = encodeRoom(type.index, room.rotation)
        }
        player.attr[ROOMS] = rooms

        val furniture = mutableMapOf<String, Int>()
        for ((slot, loc) in house.furniture) {
            furniture[slot.persistenceKey()] = loc.asRSCM(RSCMType.LOC)
        }
        player.attr[FURNITURE] = furniture
    }
}

public var Player.pohHouseLocation: Int by intVarBit("varbit.poh_house_location")
public var Player.pohHouseStyle: Int by intVarBit("varbit.poh_house_style")
public var Player.pohHouseSize: Int by intVarBit("varbit.poh_house_size")
public var Player.pohBuildingMode: Int by intVarBit("varbit.poh_building_mode")
public var Player.pohServantType: Int by intVarBit("varbit.poh_servant_type")
public var Player.pohServantPay: Int by intVarBit("varbit.poh_servant_pay")
public var Player.pohDoorsOption: Int by intVarBit("varbit.poh_doors_option")

internal fun Player.constructionLevelForHouse(): Int = baseConstructionLvl
