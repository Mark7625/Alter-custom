package org.rsmod.content.quest.area.varrock.demonslayer

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcMode
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.instances.InstanceAccess
import org.rsmod.api.instances.InstanceArea
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceNpc
import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.instances.InstanceSpec
import org.rsmod.api.instances.RegionLocal
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.game.entity.Npc
import org.rsmod.game.map.Direction
import org.rsmod.map.CoordGrid

/**
 * Private copies of the stone circle south of Varrock (map square 50,52). Both Demon Slayer
 * cutscenes run in one: Aris' vision of Wally, and the summoning and banishing of Delrith.
 *
 * World coordinates are used everywhere in the quest scripts; [Visit.at] translates them into
 * the copied region, which keeps the same local layout as the world map square.
 */
@Singleton
class StoneCircle
@Inject
constructor(
    private val manager: InstanceManager,
    private val npcRepo: NpcRepository,
    private val locRepo: LocRepository,
    private val worldRepo: WorldRepository,
) {
    private val tableType = ServerCacheManager.getObject(TABLE_LOC.asRSCM(RSCMType.LOC))
        ?: error("Missing loc type: $TABLE_LOC")

    class Visit(val session: InstanceSession, val enter: CoordGrid) {
        private val dx = enter.x - ENTER.x
        private val dz = enter.z - ENTER.z

        /** The instance tile matching world tile ([x], [z]). */
        fun at(x: Int, z: Int): CoordGrid = CoordGrid(x + dx, z + dz, enter.level)

        fun at(world: CoordGrid): CoordGrid = at(world.x, world.z)
    }

    /**
     * Creates a private copy of the circle for the player and telejumps them to [ENTER]. Returns
     * null (after messaging the player) when no instance could be made.
     */
    fun ProtectedAccess.enterCircle(key: String, spawns: List<InstanceNpc>): Visit? {
        if (manager.sessionForPlayer(player) != null) {
            mes("You are already inside an instance.")
            return null
        }
        val area =
            InstanceArea.copyRegions(
                regionIds = listOf(REGION_ID),
                level = 0,
                enterCoord = RegionLocal(0, ENTER.mx, ENTER.mz, ENTER.lx, ENTER.lz),
                exitCoord = EXIT,
                npcSpawns = spawns,
            )
        val spec =
            InstanceSpec(
                fee = 0,
                maxPlayers = 1,
                reclaimTicks = RECLAIM_TICKS,
                graceTicks = GRACE_TICKS,
                destroyWhenEmpty = true,
                area = area,
                settingsRowId = -1,
                bossName = "Delrith",
            )
        return when (val result = manager.create(player, key, spec, InstanceAccess.Private, mapClock)) {
            is InstanceManager.Result.Failed -> {
                mes(result.reason)
                null
            }
            is InstanceManager.Result.Created -> settle(result.session, result.enter)
            is InstanceManager.Result.Joined -> settle(result.session, result.enter)
        }
    }

    /**
     * Moves the player into the copy. Cutscene teleports are exempt from the teleport validator:
     * the world's dark wizards are usually mid-attack when the player walks into the circle, and
     * a refused teleport would leave the scene playing to an empty camera.
     */
    private fun ProtectedAccess.settle(session: InstanceSession, enter: CoordGrid): Visit? {
        telejump(enter, TeleportType.Exempt)
        if (player.coords != enter) {
            mes("You can't enter the stone circle right now.")
            manager.leave(player, session, mapClock)
            return null
        }
        manager.finalizeEntry(player, session, mapClock)
        return Visit(session, enter)
    }

    /** Removes the player from their visit; the instance is destroyed once empty. */
    fun ProtectedAccess.leaveCircle(): CoordGrid? {
        val session = manager.sessionForPlayer(player) ?: return null
        return manager.leave(player, session, mapClock)
    }

    /** Leaves any circle copy the player is in and puts them back on the road outside. */
    fun ProtectedAccess.exitToRoad() {
        leaveCircle()
        telejump(EXIT, TeleportType.Exempt)
    }

    /** Spawns a cutscene npc at world tile [world] inside the visit and ties it to the instance. */
    fun spawn(visit: Visit, type: String, world: CoordGrid, face: Direction? = null): Npc {
        val npc = Npc(type, visit.at(world))
        npc.mode = NpcMode.None
        if (face != null) {
            npc.respawnDir = face
        }
        npcRepo.add(npc, Int.MAX_VALUE)
        manager.attachNpc(visit.session.id, npc)
        if (face != null) {
            npc.lockFacingDirection(face)
        }
        return npc
    }

    fun remove(npc: Npc) {
        if (npc.isSlotAssigned) {
            npcRepo.del(npc, Int.MAX_VALUE)
        }
    }

    /** The npcs the instance itself spawned (the dark wizards), plus anything attached since. */
    fun npcsIn(visit: Visit): List<Npc> = manager.npcsForInstance(visit.session.id)

    fun animateTable(visit: Visit, seq: String) {
        val loc = locRepo.findExact(visit.at(TABLE), tableType) ?: return
        worldRepo.locAnim(loc, seq)
    }

    companion object {
        const val REGION_ID = 12852

        /**
         * Just north of the circle, on the path down from Varrock's south gate. Close enough that
         * every actor in the summoning scene is inside npc view range of the player.
         */
        val ENTER = CoordGrid(3227, 3378, 0)

        /** Where the player lands after leaving an instance. */
        val EXIT = CoordGrid(3227, 3382, 0)

        /** South-west tile of the 2x2 stone table in the middle of the circle. */
        val TABLE = CoordGrid(3227, 3369, 0)

        const val TABLE_LOC = "loc.qip_ds_stone_table"

        private const val RECLAIM_TICKS = 100
        private const val GRACE_TICKS = 50
    }
}
