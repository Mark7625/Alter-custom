package org.rsmod.api.poh

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ObjectServerType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.region.RegionRepository
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.game.map.collision.isZoneValid
import org.rsmod.game.region.Region
import org.rsmod.game.region.util.RegionRotations
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneGrid
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag.BLOCK_WALK
import org.rsmod.routefinder.loc.LocLayerConstants
import org.rsmod.routefinder.util.Rotations

/**
 * Owns the lifecycle of player-owned house regions: one region per owner, allocated on [enter],
 * reclaimed on [leave]/logout. Mirrors `InstanceManager`'s shape, but guests are out of scope - the
 * region map is keyed by the owner's account id only.
 */
@Singleton
public class PohManager
@Inject
constructor(
    private val regionRepo: RegionRepository,
    private val locRepo: LocRepository,
    private val dataStore: PohDataStore,
    private val regionBuilder: PohRegionBuilder,
    private val collision: CollisionFlagMap,
    private val eventBus: EventBus,
) {
    public class ActiveHouse(
        public val region: Region,
        public val house: PohHouse,
        public val owner: Long,
    )

    public sealed class EnterResult {
        public data class Success(val enterCoords: CoordGrid) : EnterResult()

        public data object NoHouse : EnterResult()

        public data object NoSpace : EnterResult()
    }

    private val active = HashMap<Long, ActiveHouse>()

    public fun hasHouse(player: Player): Boolean = player.pohHouseLocation != 0

    public fun activeHouse(player: Player): ActiveHouse? = active[player.ownerId()]

    public fun houseOf(player: Player): PohHouse? =
        activeHouse(player)?.house ?: PohAttributes.load(player, dataStore)

    /** True while the player is standing inside their own house region. */
    public fun isInOwnHouse(player: Player): Boolean {
        val current = activeHouse(player) ?: return false
        return player.coords.x in current.region.southWest.x..current.region.northEast.x &&
            player.coords.z in current.region.southWest.z..current.region.northEast.z
    }

    /**
     * Creates a fresh Rimmington-style starter house: a Garden (with the exit portal built on its
     * centrepiece) and a Parlour directly north of it.
     */
    public fun createHouse(player: Player) {
        check(!hasHouse(player)) { "Player already owns a house: ${player.username}" }
        player.pohHouseLocation = PohLocation.RIMMINGTON.varValue
        player.pohHouseStyle = 0
        val house =
            PohHouse(
                size = PohConstants.gridSize(player.constructionLevelForHouse()),
                style = 0,
                location = PohLocation.RIMMINGTON.varValue,
            )
        val gardenSlot = PohRoomSlot(PohFloor.GROUND.destLevel, gridX = 3, gridZ = 3)
        val parlourSlot = PohRoomSlot(PohFloor.GROUND.destLevel, gridX = 3, gridZ = 4)
        house.rooms[gardenSlot] = PohRoom("garden", rotation = 0)
        house.rooms[parlourSlot] = PohRoom("parlour", rotation = 0)

        val centrepiece =
            dataStore.hotspots("garden").first { it.loc == PohConstants.GARDEN_CENTREPIECE_LOC }
        house.furniture[
                PohHotspotSlot(
                    gardenSlot.level,
                    gardenSlot.gridX,
                    gardenSlot.gridZ,
                    centrepiece.index,
                )] = PohConstants.EXIT_PORTAL_LOC
        PohAttributes.save(player, house, dataStore)
    }

    /**
     * Allocates (or reuses) the player's house region and teleports them inside. Returns
     * [EnterResult.NoHouse] when the player owns no house and [EnterResult.NoSpace] when region
     * capacity is exhausted.
     */
    public fun enter(player: Player, buildMode: Boolean): EnterResult {
        val house = houseOf(player) ?: return EnterResult.NoHouse
        house.size = PohConstants.gridSize(player.constructionLevelForHouse())

        discard(player)

        val template = regionBuilder.buildTemplate(house)
        val region = regionRepo.add(template) ?: return EnterResult.NoSpace
        regionRepo.protect(region)
        blockLawnBoundary(region, house)

        val activeHouse = ActiveHouse(region, house, player.ownerId())
        active[player.ownerId()] = activeHouse

        player.pohBuildingMode = if (buildMode) 1 else 0
        player.pohHouseSize = house.size - 3
        refresh(activeHouse, buildMode)

        val enterCoords = enterCoords(activeHouse)
        PathingEntityCommon.telejump(player, collision, enterCoords)
        eventBus.publish(PohHouseEnteredEvent(player, buildMode))
        return EnterResult.Success(enterCoords)
    }

    /**
     * Drops the player's house region and returns the coordinates outside the house's world portal.
     * The caller is responsible for the teleport when invoked from protected access.
     */
    public fun leave(player: Player): CoordGrid {
        val house = houseOf(player)
        discard(player)
        player.pohBuildingMode = 0
        eventBus.publish(PohHouseExitedEvent(player))
        val location = house?.location?.let(PohLocation::forVarValue) ?: PohLocation.RIMMINGTON
        return location.exitCoords
    }

    /**
     * Called on logout: releases the region without teleporting. When the owner logs out inside
     * their house, the saved coordinates would point into a reclaimed region slot, so the next
     * login lands at the house's world portal instead (via [PohAttributes.LOGIN_EXIT_COORD]).
     */
    public fun handleLogout(player: Player) {
        if (active.containsKey(player.ownerId())) {
            if (isInOwnHouse(player)) {
                val location =
                    PohLocation.forVarValue(player.pohHouseLocation) ?: PohLocation.RIMMINGTON
                player.attr[PohAttributes.LOGIN_EXIT_COORD] = location.exitCoords.packed
            }
            eventBus.publish(PohHouseExitedEvent(player))
        }
        discard(player)
    }

    /**
     * Relocates a player who logged out inside their house to the world portal outside it. Also
     * self-heals saves whose coordinates point into the region working area without a pending exit
     * coordinate - hard server shutdowns skip the logout event, leaving raw in-region coordinates
     * behind.
     */
    public fun handleLogin(player: Player) {
        val packed = player.attr[PohAttributes.LOGIN_EXIT_COORD]
        if (packed != null) {
            player.attr.remove(PohAttributes.LOGIN_EXIT_COORD)
            PathingEntityCommon.telejump(player, collision, CoordGrid(packed))
            return
        }
        if (player.coords.x >= RegionRegistry.INSTANCE_MIN_X && activeHouse(player) == null) {
            val location =
                PohLocation.forVarValue(player.pohHouseLocation) ?: PohLocation.RIMMINGTON
            PathingEntityCommon.telejump(player, collision, location.exitCoords)
        }
    }

    /**
     * Rebuilds the house region from scratch after a structural change (room added/removed),
     * keeping the player at the same relative coordinates.
     */
    public fun rebuild(player: Player) {
        val current = activeHouse(player) ?: return
        val relative = player.coords - current.region.southWest
        val buildMode = player.pohBuildingMode == 1

        discard(player)

        val template = regionBuilder.buildTemplate(current.house)
        val region = regionRepo.add(template) ?: return
        regionRepo.protect(region)
        blockLawnBoundary(region, current.house)

        val activeHouse = ActiveHouse(region, current.house, current.owner)
        active[current.owner] = activeHouse
        refresh(activeHouse, buildMode)

        val dest =
            CoordGrid(
                region.southWest.x + relative.x,
                region.southWest.z + relative.z,
                player.coords.level,
            )
        PathingEntityCommon.telejump(player, collision, dest)
        eventBus.publish(PohHouseEnteredEvent(player, buildMode))
    }

    /** Reapplies furniture, hotspot visibility and open passages in the current region. */
    public fun refresh(player: Player) {
        val current = activeHouse(player) ?: return
        refresh(current, buildMode = player.pohBuildingMode == 1)
    }

    /** Persists the current house layout into the player's attribute blobs. */
    public fun persist(player: Player) {
        val house = activeHouse(player)?.house ?: return
        PohAttributes.save(player, house, dataStore)
    }

    /** Builds [furnitureLoc] on [slot], replacing the ghost hotspot loc client-side. */
    public fun applyFurniture(player: Player, slot: PohHotspotSlot, furnitureLoc: String) {
        val current = activeHouse(player) ?: return
        val room = current.house.rooms[slot.roomSlot] ?: return
        val hotspot = dataStore.hotspot(slot.hotspotIndex)
        current.house.furniture[slot] = furnitureLoc

        val coords = hotspotCoords(current.region, slot.roomSlot, room, hotspot)
        val angle = (hotspot.rotation + room.rotation) and 0x3
        locRepo.add(coords, furnitureLoc, Int.MAX_VALUE, LocAngle[angle], LocShape[hotspot.shape])
        PohAttributes.save(player, current.house, dataStore)
    }

    /**
     * Removes the furniture on [slot] and adds the ghost hotspot loc back - live implements removal
     * as an *add* of the hotspot loc, never a bare delete.
     */
    public fun removeFurniture(player: Player, slot: PohHotspotSlot) {
        val current = activeHouse(player) ?: return
        val room = current.house.rooms[slot.roomSlot] ?: return
        val hotspot = dataStore.hotspot(slot.hotspotIndex)
        current.house.furniture.remove(slot)

        val coords = hotspotCoords(current.region, slot.roomSlot, room, hotspot)
        // Restoring the baked map loc: template-space angle, the region registry rotates it.
        locRepo.add(
            coords,
            hotspot.loc,
            Int.MAX_VALUE,
            LocAngle[hotspot.rotation],
            LocShape[hotspot.shape],
        )
        PohAttributes.save(player, current.house, dataStore)
    }

    /** Adds [room] at [slot] and rebuilds the region. */
    public fun addRoom(player: Player, slot: PohRoomSlot, room: PohRoom) {
        val current = activeHouse(player) ?: return
        current.house.rooms[slot] = room
        PohAttributes.save(player, current.house, dataStore)
        rebuild(player)
    }

    /** Removes the room at [slot] (and its furniture) and rebuilds the region. */
    public fun removeRoom(player: Player, slot: PohRoomSlot) {
        val current = activeHouse(player) ?: return
        current.house.rooms.remove(slot)
        current.house.furniture.keys.removeAll { it.roomSlot == slot }
        PohAttributes.save(player, current.house, dataStore)
        rebuild(player)
    }

    /** Toggles building mode and refreshes hotspot visibility. */
    public fun setBuildingMode(player: Player, enabled: Boolean) {
        player.pohBuildingMode = if (enabled) 1 else 0
        refresh(player)
    }

    /* Coordinate helpers */

    public fun roomSlotAt(region: Region, coords: CoordGrid): PohRoomSlot? {
        val zoneX = (coords.x - region.southWest.x) / PohConstants.ZONE_TILE_LENGTH
        val zoneZ = (coords.z - region.southWest.z) / PohConstants.ZONE_TILE_LENGTH
        val gridX = zoneX - PohConstants.GRID_ZONE_OFFSET
        val gridZ = zoneZ - PohConstants.GRID_ZONE_OFFSET
        if (gridX !in 0 until PohConstants.GRID_LENGTH) return null
        if (gridZ !in 0 until PohConstants.GRID_LENGTH) return null
        return PohRoomSlot(coords.level, gridX, gridZ)
    }

    public fun roomBaseCoords(region: Region, slot: PohRoomSlot): CoordGrid =
        CoordGrid(
            region.southWest.x +
                (slot.gridX + PohConstants.GRID_ZONE_OFFSET) * PohConstants.ZONE_TILE_LENGTH,
            region.southWest.z +
                (slot.gridZ + PohConstants.GRID_ZONE_OFFSET) * PohConstants.ZONE_TILE_LENGTH,
            slot.level,
        )

    /**
     * Stamps a blocking ring on the outermost tile row of the lawn ring so players can't wander
     * into the visual lawn padding (real houses fence you into the buildable area, and the padding
     * exists only to keep the map-edge height cliff outside the draw distance). The dungeon level
     * is additionally blocked wherever no dungeon room exists: its filler zones are invisible
     * height spacers with no floor to stand on.
     */
    private fun blockLawnBoundary(region: Region, house: PohHouse) {
        val min = PohConstants.WALKABLE_MIN_TILE
        val max = PohConstants.WALKABLE_MAX_TILE
        for (level in PohFloor.DUNGEON.destLevel..PohFloor.UPPER.destLevel) {
            for (t in min..max) {
                collision.add(region.southWest.x + min, region.southWest.z + t, level, BLOCK_WALK)
                collision.add(region.southWest.x + max, region.southWest.z + t, level, BLOCK_WALK)
                collision.add(region.southWest.x + t, region.southWest.z + min, level, BLOCK_WALK)
                collision.add(region.southWest.x + t, region.southWest.z + max, level, BLOCK_WALK)
            }
        }
        for (gridX in 0 until PohConstants.GRID_LENGTH) {
            for (gridZ in 0 until PohConstants.GRID_LENGTH) {
                val slot = PohRoomSlot(PohFloor.DUNGEON.destLevel, gridX, gridZ)
                if (house.rooms.containsKey(slot)) {
                    continue
                }
                val base = roomBaseCoords(region, slot)
                for (dx in 0 until PohConstants.ZONE_TILE_LENGTH) {
                    for (dz in 0 until PohConstants.ZONE_TILE_LENGTH) {
                        collision.add(base.x + dx, base.z + dz, slot.level, BLOCK_WALK)
                    }
                }
            }
        }
    }

    public fun hotspotCoords(
        region: Region,
        slot: PohRoomSlot,
        room: PohRoom,
        hotspot: PohHotspot,
    ): CoordGrid {
        val type = locType(hotspot.loc)
        val width = Rotations.rotate(hotspot.rotation, type.width, type.length)
        val length = Rotations.rotate(hotspot.rotation, type.length, type.width)
        val translation =
            RegionRotations.translateLoc(
                room.rotation,
                ZoneGrid(hotspot.localX, hotspot.localZ, slot.level),
                width,
                length,
            )
        return roomBaseCoords(region, slot).translate(translation)
    }

    /** Resolves a clicked hotspot loc back to its [PohHotspotSlot]. */
    public fun findHotspot(
        player: Player,
        coords: CoordGrid,
        locId: Int,
    ): Pair<PohHotspotSlot, PohHotspot>? {
        val current = activeHouse(player) ?: return null
        val slot = roomSlotAt(current.region, coords) ?: return null
        val room = current.house.rooms[slot] ?: return null
        for (hotspot in dataStore.hotspots(room.type)) {
            if (hotspot.loc.asRSCM(RSCMType.LOC) != locId) {
                continue
            }
            if (hotspotCoords(current.region, slot, room, hotspot) == coords) {
                val hotspotSlot = PohHotspotSlot(slot.level, slot.gridX, slot.gridZ, hotspot.index)
                return hotspotSlot to hotspot
            }
        }
        return null
    }

    /** Resolves a clicked built-furniture loc back to its [PohHotspotSlot]. */
    public fun findBuiltFurniture(
        player: Player,
        coords: CoordGrid,
        locId: Int,
    ): Pair<PohHotspotSlot, String>? {
        val current = activeHouse(player) ?: return null
        val slot = roomSlotAt(current.region, coords) ?: return null
        val room = current.house.rooms[slot] ?: return null
        for ((hotspotSlot, furnitureLoc) in current.house.furnitureAt(slot)) {
            if (furnitureLoc.asRSCM(RSCMType.LOC) != locId) {
                continue
            }
            val hotspot = dataStore.hotspot(hotspotSlot.hotspotIndex)
            if (hotspotCoords(current.region, slot, room, hotspot) == coords) {
                return hotspotSlot to furnitureLoc
            }
        }
        return null
    }

    /** Resolves a clicked door hotspot to the room slot and the (rotated) edge it faces. */
    public fun findDoorEdge(player: Player, coords: CoordGrid): Pair<PohRoomSlot, PohDoorEdge>? {
        val current = activeHouse(player) ?: return null
        val slot = roomSlotAt(current.region, coords) ?: return null
        val room = current.house.rooms[slot] ?: return null
        val type = dataStore.room(room.type)
        val base = roomBaseCoords(current.region, slot)
        for (door in type.doorPlacements) {
            val translation =
                RegionRotations.translateCoords(
                    room.rotation,
                    ZoneGrid(door.localX, door.localZ, slot.level),
                )
            if (base.translate(translation) == coords) {
                return slot to door.edge.rotate(room.rotation)
            }
        }
        return null
    }

    /**
     * Re-hides the template door-hotspot map loc at [coords]. Deleting a spawned loc removes the
     * record that shadowed the baked map loc, resurrecting it — callers that despawn a real door
     * (the door swing) must re-delete the hotspot underneath.
     */
    public fun hideDoorHotspot(player: Player, coords: CoordGrid) {
        val current = activeHouse(player) ?: return
        val style = dataStore.style(current.house.style)
        val slot = roomSlotAt(current.region, coords) ?: return
        val room = current.house.rooms[slot] ?: return
        val type = dataStore.room(room.type)
        val base = roomBaseCoords(current.region, slot)
        for (door in type.doorPlacements) {
            val translation =
                RegionRotations.translateCoords(
                    room.rotation,
                    ZoneGrid(door.localX, door.localZ, slot.level),
                )
            if (base.translate(translation) == coords) {
                deleteDoorLoc(coords, style, door.rotation)
                return
            }
        }
    }

    /* Region population */

    private fun refresh(activeHouse: ActiveHouse, buildMode: Boolean) {
        val house = activeHouse.house
        val region = activeHouse.region
        val style = dataStore.style(house.style)

        for ((slot, room) in house.rooms) {
            refreshRoomHotspots(region, house, style, slot, room, buildMode)
            refreshSharedPassages(region, house, style, slot, room, buildMode)
            refreshWindows(region, style, slot, room)
        }
        ensureExitPortal(region, house)
    }

    /**
     * Swaps the `poh_dynamic_window` placeholders baked into the room templates for the style's
     * real wall window, in every mode (Zenyte `refreshWindows`).
     */
    private fun refreshWindows(region: Region, style: PohStyle, slot: PohRoomSlot, room: PohRoom) {
        val base = roomBaseCoords(region, slot)
        for (window in dataStore.windows(room.type)) {
            val translation =
                RegionRotations.translateCoords(
                    room.rotation,
                    ZoneGrid(window.localX, window.localZ, slot.level),
                )
            val coords = base.translate(translation)
            // Map-loc deletes match the template entity (the registry applies zone rotation).
            val entity =
                LocEntity(
                    WINDOW_PLACEHOLDER_LOC.asRSCM(RSCMType.LOC),
                    window.shape,
                    window.rotation,
                )
            locRepo.del(LocInfo(LocLayerConstants.of(window.shape), coords, entity), Int.MAX_VALUE)
            locRepo.add(
                coords,
                style.doors.window,
                Int.MAX_VALUE,
                LocAngle[(window.rotation + room.rotation) and 0x3],
                LocShape[window.shape],
            )
        }
    }

    private fun refreshRoomHotspots(
        region: Region,
        house: PohHouse,
        style: PohStyle,
        slot: PohRoomSlot,
        room: PohRoom,
        buildMode: Boolean,
    ) {
        for (hotspot in dataStore.hotspots(room.type)) {
            val hotspotSlot = PohHotspotSlot(slot.level, slot.gridX, slot.gridZ, hotspot.index)
            val built = house.furniture[hotspotSlot]
            val coords = hotspotCoords(region, slot, room, hotspot)
            val regionAngle = (hotspot.rotation + room.rotation) and 0x3
            // Wall-embedded hotspots (chapel "Window space") leave a hole in the wall when bare
            // deleted; Zenyte's removeHotspot swaps them to the style's wall piece instead.
            val wallEmbedded = hotspot.shape == WALL_SHAPE && hotspot.name == WINDOW_SPACE_NAME
            if (built != null) {
                // Spawned locs carry region-space angles; the registry stores them verbatim.
                locRepo.add(
                    coords,
                    built,
                    Int.MAX_VALUE,
                    LocAngle[regionAngle],
                    LocShape[hotspot.shape],
                )
            } else if (buildMode) {
                if (wallEmbedded) {
                    deleteSpawnedWallFiller(coords, style, regionAngle)
                }
                // Map-loc restores are matched against the *template* entity: the region registry
                // applies the zone rotation itself, so the angle here is the unrotated one.
                locRepo.add(
                    coords,
                    hotspot.loc,
                    Int.MAX_VALUE,
                    LocAngle[hotspot.rotation],
                    LocShape[hotspot.shape],
                )
            } else {
                // Same template-space rule for deletes; a rotated angle silently finds nothing.
                val entity =
                    LocEntity(hotspot.loc.asRSCM(RSCMType.LOC), hotspot.shape, hotspot.rotation)
                val loc = LocInfo(LocLayerConstants.of(hotspot.shape), coords, entity)
                locRepo.del(loc, Int.MAX_VALUE)
                if (wallEmbedded) {
                    locRepo.add(
                        coords,
                        style.doors.wallFiller,
                        Int.MAX_VALUE,
                        LocAngle[regionAngle],
                        LocShape[hotspot.shape],
                    )
                }
            }
        }
    }

    /** Deletes a spawned wall filler left by a previous normal-mode window-space swap. */
    private fun deleteSpawnedWallFiller(coords: CoordGrid, style: PohStyle, angle: Int) {
        val entity = LocEntity(style.doors.wallFiller.asRSCM(RSCMType.LOC), WALL_SHAPE, angle)
        val loc = LocInfo(LocLayerConstants.of(WALL_SHAPE), coords, entity)
        locRepo.del(loc, Int.MAX_VALUE)
    }

    /**
     * Manages the baked door-hotspot wall locs on every door edge of [room]. In building mode the
     * template's clickable door hotspots are restored (clicking one adds/removes rooms). Outside
     * building mode the hotspots are swapped for the style's real locs, as in the live captures
     * (rsprox-289/294): edges shared with a room whose door edges allow passage get the closed door
     * pair, walled-off edges get the style's wall filler, and exterior rooms (gardens, menagerie
     * habitat) are left open.
     */
    private fun refreshSharedPassages(
        region: Region,
        house: PohHouse,
        style: PohStyle,
        slot: PohRoomSlot,
        room: PohRoom,
        buildMode: Boolean,
    ) {
        val type = dataStore.room(room.type)
        val base = roomBaseCoords(region, slot)
        val exterior = room.type in EXTERIOR_ROOMS
        for (door in type.doorPlacements) {
            val rotatedEdge = door.edge.rotate(room.rotation)
            val neighbor = house.rooms[slot.translate(rotatedEdge)]
            val passage =
                neighbor != null &&
                    dataStore.room(neighbor.type).doorEdges.any {
                        it.rotate(neighbor.rotation) == rotatedEdge.opposite
                    }
            val neighborExterior = neighbor != null && neighbor.type in EXTERIOR_ROOMS
            val translation =
                RegionRotations.translateCoords(
                    room.rotation,
                    ZoneGrid(door.localX, door.localZ, slot.level),
                )
            val coords = base.translate(translation)
            val regionAngle = (door.rotation + room.rotation) and 0x3
            if (buildMode) {
                // Clear any real door or wall filler spawned by a previous normal-mode refresh,
                // then restore the map-loc hotspot (template-space angle; the registry rotates).
                deleteSpawnedDoorLoc(coords, style, regionAngle)
                if (passage && !ownsSharedDoorway(slot, exterior, rotatedEdge, neighborExterior)) {
                    // A shared doorway keeps a single ghost pair, on the owning face; keep the
                    // other face's baked hotspot hidden so the doorway isn't doubled up.
                    deleteDoorLoc(coords, style, door.rotation)
                    continue
                }
                val hotspotLoc =
                    if (door.side == PohDoorSide.LEFT) style.doorLeftLoc else style.doorRightLoc
                locRepo.add(
                    coords,
                    hotspotLoc,
                    Int.MAX_VALUE,
                    LocAngle[door.rotation],
                    LocShape[DOOR_SHAPE],
                )
            } else {
                deleteDoorLoc(coords, style, door.rotation)
                // A doorway only gets one door pair, on the interior room's wall face: doors
                // exist solely on the house's exterior boundary (garden <-> room). Interior
                // rooms connect through open archways and garden edges stay open lawn.
                val replacement =
                    when {
                        passage && !exterior && neighborExterior ->
                            if (door.side == PohDoorSide.LEFT) {
                                style.doors.closedLeft
                            } else {
                                style.doors.closedRight
                            }
                        passage -> null
                        exterior -> null
                        else -> style.doors.wallFiller
                    } ?: continue
                locRepo.add(
                    coords,
                    replacement,
                    Int.MAX_VALUE,
                    LocAngle[regionAngle],
                    LocShape[DOOR_SHAPE],
                )
            }
        }
    }

    /**
     * Decides which face of a shared doorway hosts the single door pair / ghost hotspot pair. The
     * interior room owns garden boundaries (where the real door lives outside building mode);
     * same-kind pairs fall back to a deterministic slot ordering so exactly one side wins.
     */
    private fun ownsSharedDoorway(
        slot: PohRoomSlot,
        exterior: Boolean,
        rotatedEdge: PohDoorEdge,
        neighborExterior: Boolean,
    ): Boolean {
        if (exterior != neighborExterior) {
            return !exterior
        }
        val neighbor = slot.translate(rotatedEdge)
        val ordering =
            compareValuesBy(
                slot,
                neighbor,
                PohRoomSlot::level,
                PohRoomSlot::gridX,
                PohRoomSlot::gridZ,
            )
        return ordering < 0
    }

    /** Deletes the template's door-hotspot map loc at [coords] (template-space [angle]). */
    private fun deleteDoorLoc(coords: CoordGrid, style: PohStyle, angle: Int) {
        for (doorLoc in listOf(style.doorLeftLoc, style.doorRightLoc)) {
            val entity = LocEntity(doorLoc.asRSCM(RSCMType.LOC), DOOR_SHAPE, angle)
            val loc = LocInfo(LocLayerConstants.of(DOOR_SHAPE), coords, entity)
            if (locRepo.del(loc, Int.MAX_VALUE)) {
                return
            }
        }
    }

    /** Deletes a spawned real door (closed or open) or wall filler at [coords] (region angle). */
    private fun deleteSpawnedDoorLoc(coords: CoordGrid, style: PohStyle, angle: Int) {
        val doors = style.doors
        val spawned =
            listOf(
                doors.closedLeft,
                doors.closedRight,
                doors.openLeft,
                doors.openRight,
                doors.wallFiller,
            )
        for (locName in spawned) {
            val entity = LocEntity(locName.asRSCM(RSCMType.LOC), DOOR_SHAPE, angle)
            val loc = LocInfo(LocLayerConstants.of(DOOR_SHAPE), coords, entity)
            if (locRepo.del(loc, Int.MAX_VALUE)) {
                return
            }
        }
    }

    /**
     * Guarantees the house has a way out: when no exit portal is built anywhere, one is placed on
     * the first garden's centrepiece spot (without persisting it as furniture).
     */
    private fun ensureExitPortal(region: Region, house: PohHouse) {
        if (house.furniture.values.any { it == PohConstants.EXIT_PORTAL_LOC }) {
            return
        }
        val (slot, room) = findGarden(house) ?: return
        val centrepiece = gardenCentrepiece(room) ?: return
        val coords = hotspotCoords(region, slot, room, centrepiece)
        val angle = (centrepiece.rotation + room.rotation) and 0x3
        locRepo.add(
            coords,
            PohConstants.EXIT_PORTAL_LOC,
            Int.MAX_VALUE,
            LocAngle[angle],
            LocShape[centrepiece.shape],
        )
    }

    private fun findGarden(house: PohHouse): Pair<PohRoomSlot, PohRoom>? {
        val entry =
            house.rooms.entries.firstOrNull { it.value.type == "garden" }
                ?: house.rooms.entries.firstOrNull { it.value.type == "superior_garden" }
                ?: return null
        return entry.key to entry.value
    }

    private fun gardenCentrepiece(room: PohRoom): PohHotspot? =
        dataStore.hotspots(room.type).firstOrNull { it.name == "Centrepiece space" }

    private fun enterCoords(activeHouse: ActiveHouse): CoordGrid {
        val house = activeHouse.house
        val garden = findGarden(house)
        if (garden != null) {
            val (slot, room) = garden
            val translation =
                RegionRotations.translateCoords(
                    room.rotation,
                    ZoneGrid(GARDEN_ENTER_X, GARDEN_ENTER_Z, slot.level),
                )
            val coords = roomBaseCoords(activeHouse.region, slot).translate(translation)
            return walkableNear(coords)
        }
        // No garden: fall back to the first ground-floor room's centre.
        val slot =
            house.rooms.keys.firstOrNull { it.level == PohFloor.GROUND.destLevel }
                ?: house.rooms.keys.first()
        return walkableNear(roomBaseCoords(activeHouse.region, slot).translate(3, 3))
    }

    private fun walkableNear(coords: CoordGrid): CoordGrid {
        if (!isBlocked(coords)) {
            return coords
        }
        for (radius in 1..3) {
            for (dx in -radius..radius) {
                for (dz in -radius..radius) {
                    val candidate = coords.translate(dx, dz)
                    if (!isBlocked(candidate)) {
                        return candidate
                    }
                }
            }
        }
        return coords
    }

    private fun isBlocked(coords: CoordGrid): Boolean =
        !collision.isZoneValid(coords) || collision.isWalkBlocked(coords)

    private fun discard(player: Player) {
        val current = active.remove(player.ownerId()) ?: return
        regionRepo.unprotect(current.region)
    }

    private fun locType(loc: String): ObjectServerType {
        val id = loc.asRSCM(RSCMType.LOC)
        return ServerCacheManager.getObject(id) ?: error("Missing loc type: $loc")
    }

    private fun Player.ownerId(): Long = checkNotNull(uuid) { "Player uuid not assigned: $this" }

    private companion object {
        /** Door hotspots are straight-wall locs (shape 0). */
        private const val DOOR_SHAPE: Int = 0

        /** Wall-embedded hotspots (chapel window spaces) share the straight-wall shape. */
        private const val WALL_SHAPE: Int = 0

        /** Cache name of the chapel's decorated-window wall hotspots. */
        private const val WINDOW_SPACE_NAME: String = "Window space"

        /** Template placeholder swapped to the style's real wall window in every mode. */
        private const val WINDOW_PLACEHOLDER_LOC: String = "loc.poh_dynamic_window"

        /** Enter tile inside the garden, just south of the 2x2 centrepiece. */
        private const val GARDEN_ENTER_X: Int = 3
        private const val GARDEN_ENTER_Z: Int = 1

        /** Rooms without full walls; their walled-off door edges stay open outside build mode. */
        private val EXTERIOR_ROOMS: Set<String> =
            setOf("garden", "formal_garden", "superior_garden", "menagerie_outdoor")
    }
}
