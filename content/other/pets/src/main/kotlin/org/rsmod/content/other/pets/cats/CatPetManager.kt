package org.rsmod.content.other.pets.cats

import dev.openrune.types.NpcMode
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.npc.owner.assignSpawnOwner
import org.rsmod.api.npc.owner.isSpawnOwnedBy
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.resyncVar
import org.rsmod.api.random.GameRandom
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
 * Owns the per-player state of a following cat: which cat is out, the npc that represents it,
 * and the growth / hunger / attention counters that tick while it follows.
 *
 * Counters are stored as cycle counts in persistent attributes so they survive logout. Only one
 * cat can follow a player at a time.
 */
@Singleton
class CatPetManager
@Inject
constructor(
    private val npcRepo: NpcRepository,
    private val mapClock: MapClock,
    private val random: GameRandom,
    private val collision: CollisionFlagMap,
) {

    fun following(player: Player): Npc? = player.attr[PET_NPC]

    fun followingPet(player: Player): CatPet? =
        player.attr[FOLLOWING_OBJ]?.let(CatPet::fromObj)

    fun hasFollower(player: Player): Boolean = following(player) != null

    fun isOwnedBy(npc: Npc, player: Player): Boolean =
        npc.isSpawnOwnedBy(player) && following(player) === npc

    /** Spawns [pet] beside [player] and starts the per-cycle upkeep timer. */
    fun spawn(player: Player, pet: CatPet): Npc {
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

    /**
     * A free tile beside the player so the cat is not hidden under them when it appears. Cardinal
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

    /** Removes the follower npc but keeps the cat "out", so it respawns on the next login. */
    fun despawn(player: Player) {
        val npc = following(player) ?: return
        remove(npc)
        clearFollowerVarp(player)
        player.attr.remove(PET_NPC)
        player.clearSoftTimer(TICK_TIMER)
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

    /** The cat is gone for good: picked up, shooed, run away or lost on death. */
    fun release(player: Player) {
        despawn(player)
        player.attr.remove(FOLLOWING_OBJ)
        resetCounters(player)
    }

    /**
     * Like [release], but the npc trots off for a few cycles before disappearing instead of
     * vanishing on the spot.
     */
    fun runAway(player: Player) {
        val npc = following(player)
        if (npc == null) {
            release(player)
            return
        }
        clearFollowerVarp(player)
        player.attr.remove(PET_NPC)
        player.attr.remove(FOLLOWING_OBJ)
        player.clearSoftTimer(TICK_TIMER)
        resetCounters(player)

        npc.mode = NpcMode.None
        npc.resetFaceEntity()
        val away = Direction.CARDINAL[mapClock.cycle % Direction.CARDINAL.size]
        npc.walk(npc.coords.translate(away.xOff * RUN_AWAY_DISTANCE, away.zOff * RUN_AWAY_DISTANCE))
        npc.lifecycleDelCycle = mapClock.cycle + RUN_AWAY_CYCLES
    }

    /** Called once per cycle while a cat is out. */
    fun tick(player: Player) {
        val pet = followingPet(player)
        val npc = following(player)
        if (pet == null || npc == null) {
            despawn(player)
            return
        }
        if (!npc.isSlotAssigned) {
            // The engine dropped the npc (e.g. an exception in its cycle); put the cat back.
            spawn(player, pet)
            return
        }
        if (player.attr[RESYNC_FOLLOWER_VARP] == true) {
            player.attr.remove(RESYNC_FOLLOWER_VARP)
            player.resyncVar(FOLLOWER_VARP)
        }
        if (player.attr[CHASING] != true) {
            ensureFollowing(npc, player)
        }
        if (tickGrowth(player, pet)) {
            // The cat was replaced by its grown form; `npc` and `pet` are stale from here on.
            return
        }
        if (pet.isKitten) {
            tickHunger(player, npc)
            tickAttention(player, npc)
        }
        tickAmbientNoise(player, npc, pet)
    }

    /** Every few minutes on average the cat mews or purrs of its own accord. */
    private fun tickAmbientNoise(player: Player, npc: Npc, pet: CatPet) {
        if (random.of(maxExclusive = AMBIENT_NOISE_CHANCE) != 0) {
            return
        }
        if (random.of(maxExclusive = 3) == 0) {
            npc.say("Purr...purr...")
            player.soundSynth("synth.purr")
        } else {
            npc.say(if (pet.isKitten) "Meow!" else "Miaow!")
            player.soundSynth(if (pet.isKitten) "synth.kittens_mew" else "synth.meeoow")
        }
    }

    private fun ensureFollowing(npc: Npc, player: Player) {
        if (!npc.isFacingPlayer) {
            npc.facePlayer(player)
        }
        if (npc.mode != NpcMode.PlayerFollow) {
            npc.mode = NpcMode.PlayerFollow
        }
    }

    /** Returns `true` if the cat grew into its next stage this cycle. */
    private fun tickGrowth(player: Player, pet: CatPet): Boolean {
        val threshold = pet.stage.growthCycles ?: return false
        val grown = pet.grown() ?: return false
        val growth = player.attr.getOrDefault(GROWTH, 0) + 1
        if (growth < threshold) {
            player.attr[GROWTH] = growth
            return false
        }
        val grownNpc = spawn(player, grown)
        resetCounters(player)
        val message =
            when (grown.stage) {
                CatStage.CAT -> "Your kitten has grown into a healthy cat that can hunt for itself."
                CatStage.OVERGROWN ->
                    "Your cat has grown into a mighty feline, but it will no longer be able to " +
                        "chase vermin."
                else -> "Your cat has grown."
            }
        player.mes(message)
        // The grown cat announces itself: a meow now and a purr shortly after.
        grownNpc.say("Meeoow!")
        player.soundSynth("synth.meeoow")
        player.soundSynth("synth.purr", delay = GROWTH_PURR_DELAY)
        return true
    }

    /** Debug helper: the cat grows on its next cycle. Returns `false` if it cannot grow. */
    fun forceGrowth(player: Player): Boolean {
        val pet = followingPet(player) ?: return false
        val threshold = pet.stage.growthCycles ?: return false
        player.attr[GROWTH] = threshold - 1
        return true
    }

    /** Debug helper: one-line summary of the follower's counters. */
    fun status(player: Player): String {
        val pet = followingPet(player) ?: return "No cat is following you."
        val growth = player.attr.getOrDefault(GROWTH, 0)
        val threshold = pet.stage.growthCycles
        val growthText =
            if (threshold == null) "fully grown" else "growth $growth/$threshold cycles"
        val kittenText =
            if (pet.isKitten) {
                ", hunger ${player.attr.getOrDefault(HUNGER, 0)}/$HUNGER_RUN" +
                    ", attention ${player.attr.getOrDefault(ATTENTION, 0)}/$ATTENTION_RUN"
            } else {
                ""
            }
        return "${pet.stage.name.lowercase()} (${pet.colour.name.lowercase()}): $growthText$kittenText"
    }

    private fun tickHunger(player: Player, npc: Npc) {
        val hunger = player.attr.getOrDefault(HUNGER, 0) + 1
        player.attr[HUNGER] = hunger
        when (hunger) {
            HUNGER_WARN -> player.mes("Your kitten is hungry.")
            HUNGER_CRY -> {
                npc.say("Meeeooowww!")
                player.soundSynth("synth.sad_meeoow")
                player.mes("Your kitten is very hungry. Feed it some fish or milk before it runs away.")
            }
            HUNGER_RUN -> {
                npc.say("Meeeooowww!")
                player.soundSynth("synth.sad_meeoow")
                player.mes("Your kitten has run away because you didn't feed it.")
                runAway(player)
            }
        }
    }

    private fun tickAttention(player: Player, npc: Npc) {
        val attention = player.attr.getOrDefault(ATTENTION, 0) + 1
        player.attr[ATTENTION] = attention
        when (attention) {
            ATTENTION_WARN -> player.mes("Your kitten wants some attention.")
            ATTENTION_CRY -> {
                npc.say("Meeeooowww...")
                player.soundSynth("synth.sad_meeoow")
                player.mes("Your kitten is lonely. Stroke it or give it a ball of wool.")
            }
            ATTENTION_RUN -> {
                npc.say("Meeeooowww...")
                player.soundSynth("synth.sad_meeoow")
                player.mes("Your kitten has run away because it felt neglected.")
                runAway(player)
            }
        }
    }

    fun isHungry(player: Player): Boolean = player.attr.getOrDefault(HUNGER, 0) >= HUNGER_WARN

    fun isLonely(player: Player): Boolean =
        player.attr.getOrDefault(ATTENTION, 0) >= ATTENTION_WARN

    /** Fraction of the way (0-100) to the next growth stage, for the talk-to lines. */
    fun growthPercent(player: Player, pet: CatPet): Int {
        val threshold = pet.stage.growthCycles ?: return 100
        return (player.attr.getOrDefault(GROWTH, 0) * 100 / threshold).coerceIn(0, 100)
    }

    fun feed(player: Player) {
        player.attr[HUNGER] = 0
    }

    /**
     * A stroke keeps a kitten content for 18 minutes; stroking it again while it is already content
     * tops that up to the full 25.
     */
    fun stroke(player: Player) {
        val attention = player.attr.getOrDefault(ATTENTION, 0)
        player.attr[ATTENTION] = if (attention <= STROKE_ATTENTION) 0 else STROKE_ATTENTION
    }

    /** Playing with a ball of wool keeps a kitten content for 51 minutes. */
    fun play(player: Player) {
        player.attr[ATTENTION] = WOOL_ATTENTION
    }

    fun setChasing(player: Player, chasing: Boolean) {
        if (chasing) {
            player.attr[CHASING] = true
        } else {
            player.attr.remove(CHASING)
        }
    }

    private fun resetCounters(player: Player) {
        player.attr.remove(GROWTH)
        player.attr.remove(HUNGER)
        player.attr.remove(ATTENTION)
    }

    private fun remove(npc: Npc) {
        if (npc.isSlotAssigned) {
            npcRepo.del(npc, Int.MAX_VALUE)
        }
    }

    companion object {
        const val TICK_TIMER: String = "timer.pet_cat_tick"

        private const val FOLLOWER_VARP: String = "varp.follower_npc"
        private const val NO_FOLLOWER: Int = -1

        /** Minutes to cycles: one cycle is 0.6 seconds. */
        private const val MINUTE: Int = 100

        const val HUNGER_WARN: Int = 24 * MINUTE
        const val HUNGER_CRY: Int = 27 * MINUTE
        const val HUNGER_RUN: Int = 30 * MINUTE

        const val ATTENTION_WARN: Int = 25 * MINUTE
        const val ATTENTION_CRY: Int = 32 * MINUTE
        const val ATTENTION_RUN: Int = 39 * MINUTE

        /** 25 minutes to the warning minus the 18 minutes a stroke buys. */
        private const val STROKE_ATTENTION: Int = ATTENTION_WARN - 18 * MINUTE

        /** 51 minutes of contentment: 25 to the warning plus 26 in credit. */
        private const val WOOL_ATTENTION: Int = ATTENTION_WARN - 51 * MINUTE

        /** Client cycles (20ms) between the growth meow and the purr that follows it. */
        private const val GROWTH_PURR_DELAY: Int = 50

        /** One-in-N chance per cycle: on average one noise every five minutes. */
        private const val AMBIENT_NOISE_CHANCE: Int = 500

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

        private const val RUN_AWAY_DISTANCE: Int = 6
        private const val RUN_AWAY_CYCLES: Int = 6

        /** Obj id of the cat currently out following (persists so it respawns on login). */
        private val FOLLOWING_OBJ = AttributeKey<Int>(persistenceKey = "pets.cat.following_obj")
        private val GROWTH = AttributeKey<Int>(persistenceKey = "pets.cat.growth")
        private val HUNGER = AttributeKey<Int>(persistenceKey = "pets.cat.hunger")
        private val ATTENTION = AttributeKey<Int>(persistenceKey = "pets.cat.attention")

        private val PET_NPC = AttributeKey<Npc>(temp = true)
        private val CHASING = AttributeKey<Boolean>(temp = true)
        private val RESYNC_FOLLOWER_VARP = AttributeKey<Boolean>(temp = true)
    }
}
