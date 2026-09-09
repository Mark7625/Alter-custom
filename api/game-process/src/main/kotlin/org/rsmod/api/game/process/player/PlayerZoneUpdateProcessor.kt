package org.rsmod.api.game.process.player

import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntList
import jakarta.inject.Inject
import java.util.ArrayList
import net.rsprot.protocol.common.client.OldSchoolClientType
import net.rsprot.protocol.game.outgoing.zone.header.UpdateZoneFullFollows
import net.rsprot.protocol.game.outgoing.zone.header.UpdateZonePartialEnclosed
import net.rsprot.protocol.message.ZoneProt
import org.rsmod.api.registry.loc.LocRegistry
import org.rsmod.api.registry.obj.ObjRegistry
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.registry.zone.ZoneUpdateTransformer
import org.rsmod.api.utils.map.BuildAreaUtils
import org.rsmod.api.utils.zone.SharedZoneEnclosedBuffers
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.obj.Obj
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey

public class PlayerZoneUpdateProcessor
@Inject
constructor(
    private val updates: ZoneUpdateMap,
    private val locReg: LocRegistry,
    private val objReg: ObjRegistry,
    private val enclosedBuffers: SharedZoneEnclosedBuffers,
) {
    public fun computeEnclosedBuffers() {
        enclosedBuffers.computeSharedBuffers()
    }

    public fun process(player: Player) {
        player.processZoneUpdates()
    }

    public fun clearEnclosedBuffers() {
        enclosedBuffers.clear()
    }

    public fun clearPendingZoneUpdates() {
        updates.clear()
    }

    private fun Player.processZoneUpdates() {
        val currZone = ZoneKey.from(coords)
        val visibleZones = visibleZoneKeys
        val prevZone = lastProcessedZone
        val buildArea = buildArea

        if (currZone != prevZone) {
            // Compute neighbouring zones based on the player's current zone.
            val currZones =
                currZone.computeVisibleNeighbouringZones().filterWithinBuildArea(buildArea)

            // Determine the newly visible zones that were not previously visible.
            // These are zones that need to be reset and have persistent updates/entities sent.
            val newZones = IntArrayList(currZones).apply { removeAll(visibleZones) }
            processNewVisibleZones(buildArea, newZones)

            // Update the player's cached visible zone keys to reflect the current visible zones.
            refreshVisibleZoneKeys(currZones)

            // Identify zones that have been visible for more than one cycle (or one call to this
            // processor). These zones will have their transient updates sent. This prevents a newly
            // visible zone from immediately sending a transient update (e.g., an `ObjAdd` update)
            // right after a persistent entity update, which could occur if an obj is spawned on the
            // ground the same cycle the zone becomes visible to the player.
            val oldZones = IntArrayList(currZones).apply { removeAll(newZones) }
            processVisibleZoneUpdates(buildArea, oldZones)
        } else {
            // If the player hasn't moved to a new zone, process updates for currently visible
            // zones.
            processVisibleZoneUpdates(buildArea, visibleZones)
        }

        lastProcessedZone = currZone
    }

    private fun Player.processNewVisibleZones(buildArea: CoordGrid, zones: IntList) {
        for (zone in zones.intIterator()) {
            val key = ZoneKey(zone)
            val zoneBase = key.toCoords()
            sendZoneResetUpdate(buildArea, zoneBase)
            sendZonePersistentUpdates(buildArea, zoneBase, key)
        }
    }

    private fun Player.sendZoneResetUpdate(buildArea: CoordGrid, zoneBase: CoordGrid) {
        val deltaX = zoneBase.x - buildArea.x
        val deltaZ = zoneBase.z - buildArea.z
        val message = UpdateZoneFullFollows(deltaX, deltaZ, zoneBase.level)
        client.write(message)
    }

    private fun Player.sendZonePersistentUpdates(
        buildArea: CoordGrid,
        zoneBase: CoordGrid,
        zone: ZoneKey,
    ) {
        val spawnedLocs = locReg.findAllSpawned(zone)
        sendPersistentLocs(spawnedLocs)

        val spawnedObjs = objReg.findAll(zone)
        sendPersistentObjs(buildArea, zoneBase, spawnedObjs, observerUUID)
    }

    private fun Player.sendPersistentLocs(locs: Sequence<LocInfo>) {
        for (loc in locs) {
            val prot = ZoneUpdateTransformer.toPersistentLocChange(loc)
            client.write(prot)
        }
    }

    private fun Player.sendPersistentObjs(
        buildArea: CoordGrid,
        zoneBase: CoordGrid,
        objs: Sequence<Obj>,
        observerId: Long?,
    ) {
        val updates = ArrayList<ZoneProt>()
        for (obj in objs) {
            val prot = ZoneUpdateTransformer.toPersistentObjAdd(obj, observerId) ?: continue
            updates += prot
        }
        if (updates.isNotEmpty()) {
            sendZonePlayerEnclosedUpdates(buildArea, zoneBase, updates)
        }
    }

    private fun Player.refreshVisibleZoneKeys(zones: IntList) {
        visibleZoneKeys.clear()
        visibleZoneKeys.addAll(zones)
    }

    private fun Player.processVisibleZoneUpdates(buildArea: CoordGrid, currZones: List<Int>) {
        for (zone in currZones) {
            val zoneKey = ZoneKey(zone)
            val zoneBase = zoneKey.toCoords()
            sendZoneSharedEnclosedUpdates(buildArea, zoneKey, zoneBase)
            sendZonePlayerEnclosedUpdates(buildArea, zoneKey, zoneBase)
        }
    }

    private fun Player.sendZonePlayerEnclosedUpdates(
        buildArea: CoordGrid,
        zone: ZoneKey,
        zoneBase: CoordGrid,
    ) {
        val updates = updates[zone] ?: return
        check(updates.isNotEmpty) { "`updates` for zone should not be empty: $zone" }
        val playerSpecific = updates.toPlayerSpecificEnclosed(observerUUID)
        sendZonePlayerEnclosedUpdates(buildArea, zoneBase, playerSpecific)
    }

    private fun Player.sendZonePlayerEnclosedUpdates(
        buildArea: CoordGrid,
        zoneBase: CoordGrid,
        updates: List<ZoneProt>,
    ) {
        if (updates.isEmpty()) {
            return
        }
        val buffer = enclosedBuffers.computeBufferForClient(OldSchoolClientType.DESKTOP, updates)
        val deltaX = zoneBase.x - buildArea.x
        val deltaZ = zoneBase.z - buildArea.z
        val message = UpdateZonePartialEnclosed(deltaX, deltaZ, zoneBase.level, buffer)
        client.write(message)
    }

    private fun Player.sendZoneSharedEnclosedUpdates(
        buildArea: CoordGrid,
        zone: ZoneKey,
        zoneBase: CoordGrid,
    ) {
        val enclosed = enclosedBuffers[zone] ?: return
        val buffer = enclosed[OldSchoolClientType.DESKTOP] ?: return
        val deltaX = zoneBase.x - buildArea.x
        val deltaZ = zoneBase.z - buildArea.z
        val prot = UpdateZonePartialEnclosed(deltaX, deltaZ, zoneBase.level, buffer)
        client.write(prot)
    }

    private fun Iterable<ZoneProt>.toPlayerSpecificEnclosed(observerId: Long?): List<ZoneProt> {
        val enclosed = ArrayList<ZoneProt>()
        for (update in this) {
            val prot =
                (update as? ZoneUpdateTransformer.PartialFollowsZoneProt)
                    ?.toEnclosed(observerId)
            if (prot != null) {
                enclosed += prot
            }
        }
        return enclosed
    }

    private fun ZoneUpdateTransformer.PartialFollowsZoneProt.toEnclosed(
        observerId: Long?,
    ): ZoneProt? =
        when (this) {
            is ZoneUpdateTransformer.ObjPrivateZoneProt ->
                if (isVisibleTo(observerId)) backing else null
            is ZoneUpdateTransformer.ObjReveal ->
                if (observerId == obj.receiverId) null else backing
            else -> backing
        }

    /**
     * Every zone within view range of this zone's column, across **all four levels**. Zone
     * updates are level-addressed (`UpdateZoneFullFollows`/`UpdateZonePartialEnclosed` carry a
     * level), and the client renders every level of the scene: a loc changed on another level -
     * e.g. a house's dungeon staircase replacing its baked hotspot below the player - must reach
     * observers on other levels or their client keeps showing the stale baked state until they
     * happen to visit that level.
     */
    private fun ZoneKey.computeVisibleNeighbouringZones(): IntList {
        val zones = IntArrayList(ZONE_VIEW_TOTAL_COUNT)
        for (x in -ZONE_VIEW_RADIUS..ZONE_VIEW_RADIUS) {
            for (z in -ZONE_VIEW_RADIUS..ZONE_VIEW_RADIUS) {
                val zone = translate(x, z)
                for (level in 0 until LEVEL_COUNT) {
                    zones.add(ZoneKey(zone.x, zone.z, level).packed)
                }
            }
        }
        return zones
    }

    private fun IntList.filterWithinBuildArea(buildArea: CoordGrid): IntList {
        val zones = IntArrayList(size)
        forEach { zone ->
            val zoneBase = ZoneKey(zone).toCoords()
            val deltaX = zoneBase.x - buildArea.x
            val deltaZ = zoneBase.z - buildArea.z
            val viewable = deltaX in BUILD_AREA_BOUNDS && deltaZ in BUILD_AREA_BOUNDS
            if (viewable) {
                zones.add(zone)
            }
        }
        return zones
    }

    public companion object {
        public const val ZONE_VIEW_RADIUS: Int = 3
        public const val LEVEL_COUNT: Int = 4
        public const val ZONE_VIEW_TOTAL_COUNT: Int =
            (2 * ZONE_VIEW_RADIUS + 1) * (2 * ZONE_VIEW_RADIUS + 1) * LEVEL_COUNT

        public val BUILD_AREA_BOUNDS: IntRange = 0 until BuildAreaUtils.SIZE
    }
}
