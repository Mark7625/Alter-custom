package org.rsmod.content.skills.thieving.pets

import dev.openrune.types.NpcMode
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.npc.owner.assignSpawnOwner
import org.rsmod.api.npc.owner.isSpawnOwnedBy
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.resyncVar
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.map.Direction
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.game.map.collision.isZoneValid
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * Owns a player's following Rocky: which form is out, the npc that represents it, and the
 * per-cycle upkeep that keeps it at the owner's heels. The form that is out persists so it comes
 * back on login.
 */
@Singleton
class RockyPetManager
@Inject
constructor(
    private val npcRepo: NpcRepository,
    private val mapClock: MapClock,
    private val collision: CollisionFlagMap,
) {
    fun following(player: Player): Npc? = player.attr[PET_NPC]

    fun followingPet(player: Player): RockyPet? = player.attr[FOLLOWING_OBJ]?.let(RockyPet::fromObj)

    fun isOwnedBy(npc: Npc, player: Player): Boolean =
        npc.isSpawnOwnedBy(player) && following(player) === npc

    /** Whether anything at all follows the player: Rocky, a cat, any other pet. */
    fun hasAnyFollower(player: Player): Boolean =
        following(player) != null || player.vars[FOLLOWER_VARP] > 0

    /**
     * Rolls the pet at `1 in (base - level * 25)` after a successful pickpocket, stall or chest
     * theft. The pet starts following unless something already does, in which case it slips
     * into the backpack; it is lost only when both are impossible.
     */
    fun roll(access: ProtectedAccess, base: Int) {
        val player = access.player
        val chance = (base - player.statBase(THIEVING) * LEVEL_SCALE).coerceAtLeast(1)
        if (access.random.of(maxExclusive = chance) != 0) {
            return
        }
        if (followingPet(player) != null) {
            return
        }
        if (!hasAnyFollower(player)) {
            spawn(player, RockyPet.RACCOON)
            access.mes("You have a funny feeling like you're being followed.")
            return
        }
        if (access.inv.hasFreeSpace()) {
            access.invAdd(access.inv, RockyPet.RACCOON.obj, 1)
            access.mes("You feel something weird sneaking into your backpack.")
        }
    }

    /** Spawns [pet] beside [player] and starts the per-cycle follow timer. */
    fun spawn(player: Player, pet: RockyPet): Npc {
        following(player)?.let { remove(it) }
        val npc = Npc(pet.npc, spawnTile(player))
        npc.mode = NpcMode.PlayerFollow
        npcRepo.add(npc, Int.MAX_VALUE)
        // `add` with a permanent duration flags the npc as respawning; a pet never does.
        npc.respawns = false
        npc.assignSpawnOwner(player, mapClock.cycle)
        npc.facePlayer(player)
        setFollowerVarp(player, npc)

        player.attr[PET_NPC] = npc
        player.attr[FOLLOWING_OBJ] = pet.objId
        player.softTimer(TICK_TIMER, 1)
        return npc
    }

    /** Swaps the follower for another form in place. */
    fun transform(player: Player, pet: RockyPet): Npc = spawn(player, pet)

    /** Removes the follower npc but keeps the pet "out", so it respawns on the next login. */
    fun despawn(player: Player) {
        val npc = following(player) ?: return
        remove(npc)
        clearFollowerVarp(player)
        player.attr.remove(PET_NPC)
        player.clearSoftTimer(TICK_TIMER)
    }

    /** The pet is picked up: nothing follows any more. */
    fun release(player: Player) {
        despawn(player)
        player.attr.remove(FOLLOWING_OBJ)
    }

    /** Called once per cycle while Rocky is out. */
    fun tick(player: Player) {
        val pet = followingPet(player)
        val npc = following(player)
        if (pet == null || npc == null) {
            despawn(player)
            return
        }
        if (!npc.isSlotAssigned) {
            // The engine dropped the npc (e.g. an exception in its cycle); put the pet back.
            spawn(player, pet)
            return
        }
        if (player.attr[RESYNC_FOLLOWER_VARP] == true) {
            player.attr.remove(RESYNC_FOLLOWER_VARP)
            player.resyncVar(FOLLOWER_VARP)
        }
        if (!npc.isFacingPlayer) {
            npc.facePlayer(player)
        }
        if (npc.mode != NpcMode.PlayerFollow) {
            npc.mode = NpcMode.PlayerFollow
        }
    }

    /**
     * A free tile beside the player so the pet is not hidden under them when it appears. Cardinal
     * neighbours are tried before diagonals; the player's own tile is the last resort.
     */
    private fun spawnTile(player: Player): CoordGrid {
        val origin = player.coords
        for (direction in SPAWN_DIRECTIONS) {
            val tile = origin.translate(direction.xOff, direction.zOff)
            if (collision.isZoneValid(tile) && !collision.isWalkBlocked(tile)) {
                return tile
            }
        }
        return origin
    }

    /**
     * The client only shows right-click options on a follower-flagged npc when `varp.follower_npc`
     * names it: the npc type id in the high 16 bits and its slot index in the low 16 bits.
     */
    private fun setFollowerVarp(player: Player, npc: Npc) {
        val packed = (npc.visType.id shl 16) or (npc.slotId and 0xFFFF)
        VarPlayerIntMapSetter.set(player, FOLLOWER_VARP, packed)
        // The setter does not transmit before the player's first processed cycle (login spawns),
        // so the first tick pushes the value again.
        player.attr[RESYNC_FOLLOWER_VARP] = true
    }

    private fun clearFollowerVarp(player: Player) {
        VarPlayerIntMapSetter.set(player, FOLLOWER_VARP, NO_FOLLOWER)
    }

    private fun remove(npc: Npc) {
        if (npc.isSlotAssigned) {
            npcRepo.del(npc, Int.MAX_VALUE)
        }
    }

    companion object {
        const val TICK_TIMER: String = "timer.pet_rocky_tick"

        private const val THIEVING: String = "stat.thieving"
        private const val LEVEL_SCALE: Int = 25

        private const val FOLLOWER_VARP: String = "varp.follower_npc"
        private const val NO_FOLLOWER: Int = -1

        private val SPAWN_DIRECTIONS: List<Direction> =
            listOf(
                Direction.South,
                Direction.West,
                Direction.East,
                Direction.North,
                Direction.SouthWest,
                Direction.SouthEast,
                Direction.NorthWest,
                Direction.NorthEast,
            )

        /** Obj id of the form currently out following (persists so it respawns on login). */
        private val FOLLOWING_OBJ = AttributeKey<Int>(persistenceKey = "pets.rocky.following_obj")

        private val PET_NPC = AttributeKey<Npc>(temp = true)
        private val RESYNC_FOLLOWER_VARP = AttributeKey<Boolean>(temp = true)
    }
}
