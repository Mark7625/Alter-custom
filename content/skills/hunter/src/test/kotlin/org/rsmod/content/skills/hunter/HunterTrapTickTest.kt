package org.rsmod.content.skills.hunter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.content.skills.hunter.HunterTrapTestWorld.Companion.TRAP_CREATURE_FAILED
import org.rsmod.content.skills.hunter.HunterTrapTestWorld.Companion.TRAP_CREATURE_NONE
import org.rsmod.content.skills.hunter.HunterTrapTestWorld.Companion.TRAP_TILE
import org.rsmod.game.entity.Controller
import org.rsmod.game.entity.Npc
import org.rsmod.game.loc.LocShape

/**
 * [HunterTrap.hunterTrapTick] driven cycle by cycle against real repositories and a scripted
 * [ScriptedRandom] - the parts of the engine a live client cannot reach, since a failed catch
 * cannot be forced when the roll is genuinely random. The `ProtectedAccess` half has its own file,
 * [HunterTrapOpsTest]; traps are set up here by the harness in the same shape those paths leave
 * them in. Serialised: `ServerCacheManager` is a singleton and `RSCM` memoises into a plain `HashMap`,
 * which is not safe to fill from the parallel execution `test-conventions` turns on.
 */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(HUNTER_TEST_WORLD_LOCK)
class HunterTrapTickTest {
    private lateinit var world: HunterTrapTestWorld

    @BeforeEach
    fun setUp() {
        HunterTestCache.load()
        world = HunterTrapTestWorld()
    }

    /** A corrupt ordinal on a portable trap is tidied away instead, which is the whole contrast. */
    @Test
    fun `a corrupt family ordinal clears a portable trap and deletes its controller`() {
        val owner = world.addPlayer(TRAP_TILE.translate(3, 3), hunterLvl = 99)
        val controller = world.layPortableTrap(TrapFamily.BOX, TRAP_TILE, owner)

        controller.trapFamily = TrapFamily.entries.size
        world.tick(controller)

        assertNull(world.locAt(TRAP_TILE))
        assertNull(world.controllerAt(TRAP_TILE))
    }

    /* The occupancy guard. */

    /**
     * "Box traps won't trap prey if players are standing on the trap itself." (wiki, *Box trap >
     * Mechanics*.) Any player, not just the owner.
     */
    @Test
    fun `a player standing on a box trap suppresses the catch`() {
        val controller = boxTrapWithChinchompaInRange()
        world.addPlayer(TRAP_TILE, hunterLvl = 99)
        world.random.nextDouble = ScriptedRandom.ALWAYS_CATCH

        world.tick(controller)

        assertEquals(TRAP_CREATURE_NONE, controller.trapCreature)
        assertEquals(0, world.random.doubleDraws, "The roll must not even happen.")
    }

    /** "A bird snare will not catch birds if the user is standing directly on the bird snare." */
    @Test
    fun `a player standing on a bird snare suppresses the catch`() {
        val owner = world.addPlayer(TRAP_TILE.translate(3, 3), hunterLvl = 99)
        val controller = world.layPortableTrap(TrapFamily.SNARE, TRAP_TILE, owner)
        world.addNpc("npc.hunting_bird_jungle", TRAP_TILE.translate(1, 0))
        world.addPlayer(TRAP_TILE, hunterLvl = 99)
        world.random.nextDouble = ScriptedRandom.ALWAYS_CATCH

        world.tick(controller)

        assertEquals(TRAP_CREATURE_NONE, controller.trapCreature)
    }

    /**
     * `PlayerRegistry.findAll` does not filter hidden or logging-out players, so the tick applies
     * `isValidTarget()` on top. Without it an invisible player parked on a trap would suppress every
     * roll with nothing observable to diagnose it by.
     */
    @Test
    fun `a hidden player on the tile does not suppress the catch`() {
        val controller = boxTrapWithChinchompaInRange()
        val camper = world.addPlayer(TRAP_TILE, hunterLvl = 99)
        world.playerRepo.hide(camper)
        world.random.nextDouble = ScriptedRandom.ALWAYS_CATCH

        world.tick(controller)

        assertTrue(controller.trapCreature >= 0, "Expected a catch, got ${controller.trapCreature}")
    }

    /* Catch success and failure. */

    @Test
    fun `a successful catch shows the trapping loc, then settles into the full loc`() {
        val controller = boxTrapWithChinchompaInRange()
        world.random.nextDouble = ScriptedRandom.ALWAYS_CATCH

        world.tick(controller)

        val creature = HunterCreatures.all[controller.trapCreature]
        assertEquals("npc.hunting_chinchompa", creature.npc)
        // The chinchompa was placed one tile north, so the north-side trapping loc is expected.
        assertEquals("loc.hunting_boxtrap_trapping_chinchompa_n", world.locNameAt(TRAP_TILE))

        world.advance(TRAP_SPRING_CYCLES)
        world.tick(controller)
        assertEquals("loc.hunting_boxtrap_full_chinchompa", world.locNameAt(TRAP_TILE))
        assertNotNull(world.controllerAt(TRAP_TILE), "A caught trap waits for its owner.")
    }

    /**
     * The branch a live client cannot force. A failed catch must take the failing/failed pair, not
     * the trapping/full pair, and must record [TRAP_CREATURE_FAILED] rather than a creature index -
     * getting that wrong would hand out a free chinchompa on a miss.
     */
    @Test
    fun `a failed catch takes the failing loc, then settles into the failed loc`() {
        val controller = boxTrapWithChinchompaInRange(CHINCHOMPA_LEVEL)
        world.random.nextDouble = ScriptedRandom.HIGHEST_DRAW

        world.tick(controller)

        assertEquals(TRAP_CREATURE_FAILED, controller.trapCreature)
        assertEquals("loc.hunting_boxtrap_failing", world.locNameAt(TRAP_TILE))

        world.advance(TRAP_SPRING_CYCLES)
        world.tick(controller)
        assertEquals("loc.hunting_boxtrap_failed", world.locNameAt(TRAP_TILE))
    }

    /** Either outcome springs the trap, so either outcome must take the creature off the map. */
    @Test
    fun `both outcomes despawn the creature`() {
        val owner = world.addPlayer(TRAP_TILE.translate(3, 3), hunterLvl = 99)
        val caught = world.layPortableTrap(TrapFamily.BOX, TRAP_TILE, owner)
        val prey = world.addNpc("npc.hunting_chinchompa", TRAP_TILE.translate(0, 1))
        world.random.nextDouble = ScriptedRandom.ALWAYS_CATCH
        world.tick(caught)
        assertFalse(world.npcIsSpawned(prey), "A caught creature is despawned.")

        val missed = HunterTrapTestWorld()
        val missedOwner = missed.addPlayer(TRAP_TILE.translate(3, 3), hunterLvl = CHINCHOMPA_LEVEL)
        val controller = missed.layPortableTrap(TrapFamily.BOX, TRAP_TILE, missedOwner)
        val escapee = missed.addNpc("npc.hunting_chinchompa", TRAP_TILE.translate(0, 1))
        missed.random.nextDouble = ScriptedRandom.HIGHEST_DRAW
        missed.tick(controller)
        assertFalse(missed.npcIsSpawned(escapee), "A creature that escaped is despawned too.")
    }

    /**
     * A creature that has already been caught must not be caught again: despawn only *hides* an
     * npc and `NpcRegistry.findAll` does not filter hidden ones, so without the sweep's own
     * visibility filter two traps catch one animal on the same cycle (docs/hunter.md).
     */
    @Test
    fun `a despawned creature cannot be caught by a second trap`() {
        val owner = world.addPlayer(TRAP_TILE.translate(4, 4), hunterLvl = 99)
        val first = world.layPortableTrap(TrapFamily.BOX, TRAP_TILE, owner)
        val secondTile = TRAP_TILE.translate(0, 2)
        val second = world.layPortableTrap(TrapFamily.BOX, secondTile, owner)
        // One tile from both traps, so both are in range of it on the same cycle.
        val prey = world.addNpc("npc.hunting_chinchompa", TRAP_TILE.translate(0, 1))
        world.random.nextDouble = ScriptedRandom.ALWAYS_CATCH

        world.tick(first)
        assertTrue(first.trapCreature >= 0, "The first trap should catch it.")
        assertFalse(world.npcIsSpawned(prey))

        world.tick(second)

        assertEquals(
            TRAP_CREATURE_NONE,
            second.trapCreature,
            "The chinchompa was already caught; the second trap must find nothing.",
        )
        assertEquals(1, world.random.doubleDraws, "Only the first trap should have rolled.")
    }

    /**
     * "If the player's Hunter level is too low, the trap will always fail." (wiki.) The regular
     * chinchompa's positive `successLow` makes the explicit gate load-bearing, and the draw
     * counter pins that the gate short-circuits before the roll.
     */
    @Test
    fun `an under-levelled owner never catches and never rolls`() {
        val owner = world.addPlayer(TRAP_TILE.translate(3, 3), hunterLvl = 1)
        val controller = world.layPortableTrap(TrapFamily.BOX, TRAP_TILE, owner)
        world.addNpc("npc.hunting_chinchompa", TRAP_TILE.translate(0, 1))
        world.random.nextDouble = ScriptedRandom.ALWAYS_CATCH

        world.tick(controller)

        assertEquals(TRAP_CREATURE_FAILED, controller.trapCreature)
        assertEquals(0, world.random.doubleDraws)
    }

    /* Cadence and range. */

    /**
     * "Once a box trap has been set, it will make an attempt every 3 ticks." (wiki.) The trap and
     * the chinchompa are restored between cycles, which is what keeps the off-cycle assertions
     * non-vacuous: a roll that happened is a cadence failure, not a missing target.
     */
    @Test
    fun `a box trap rolls only once every three cycles`() {
        val owner = world.addPlayer(TRAP_TILE.translate(3, 3), hunterLvl = CHINCHOMPA_LEVEL)
        val controller = world.layPortableTrap(TrapFamily.BOX, TRAP_TILE, owner)
        val prey = world.addNpc("npc.hunting_chinchompa", TRAP_TILE.translate(0, 1))
        world.random.nextDouble = ScriptedRandom.HIGHEST_DRAW

        // Cycle 0 is the trap's own creation cycle, so it rolls; 1 and 2 must not.
        world.tick(controller)
        assertEquals(1, world.random.doubleDraws)

        repeat(2) {
            rearm(controller, prey)
            world.advance()
            world.tick(controller)
        }
        assertEquals(1, world.random.doubleDraws, "No roll on the two off-cycles.")

        rearm(controller, prey)
        world.advance()
        world.tick(controller)
        assertEquals(2, world.random.doubleDraws, "Rolls again on the third cycle.")
    }

    /**
     * The box trap's sourced 2-tile radius against the snare's conservative adjacency. A creature
     * two tiles away is in range of one and out of range of the other, which is the only behavioural
     * difference between the two constants.
     */
    @Test
    fun `a creature two tiles away is in range of a box trap but not a snare`() {
        val owner = world.addPlayer(TRAP_TILE.translate(4, 4), hunterLvl = 99)
        val box = world.layPortableTrap(TrapFamily.BOX, TRAP_TILE, owner)
        world.addNpc("npc.hunting_chinchompa", TRAP_TILE.translate(2, 0))
        world.random.nextDouble = ScriptedRandom.ALWAYS_CATCH
        world.tick(box)
        assertTrue(box.trapCreature >= 0, "Box trap should reach two tiles.")

        val other = HunterTrapTestWorld()
        val snareOwner = other.addPlayer(TRAP_TILE.translate(4, 4), hunterLvl = 99)
        val snare = other.layPortableTrap(TrapFamily.SNARE, TRAP_TILE, snareOwner)
        other.addNpc("npc.hunting_bird_jungle", TRAP_TILE.translate(2, 0))
        other.random.nextDouble = ScriptedRandom.ALWAYS_CATCH
        other.tick(snare)
        assertEquals(TRAP_CREATURE_NONE, snare.trapCreature, "Snare should not reach two tiles.")
    }

    /** A trap only catches its own family's creatures, whatever wanders past. */
    @Test
    fun `a box trap ignores a bird and a snare ignores a chinchompa`() {
        val owner = world.addPlayer(TRAP_TILE.translate(4, 4), hunterLvl = 99)
        val box = world.layPortableTrap(TrapFamily.BOX, TRAP_TILE, owner)
        world.addNpc("npc.hunting_bird_jungle", TRAP_TILE.translate(1, 0))
        world.random.nextDouble = ScriptedRandom.ALWAYS_CATCH
        world.tick(box)
        assertEquals(TRAP_CREATURE_NONE, box.trapCreature)

        val other = HunterTrapTestWorld()
        val snareOwner = other.addPlayer(TRAP_TILE.translate(4, 4), hunterLvl = 99)
        val snare = other.layPortableTrap(TrapFamily.SNARE, TRAP_TILE, snareOwner)
        other.addNpc("npc.hunting_chinchompa", TRAP_TILE.translate(1, 0))
        other.random.nextDouble = ScriptedRandom.ALWAYS_CATCH
        other.tick(snare)
        assertEquals(TRAP_CREATURE_NONE, snare.trapCreature)
    }

    /* Lifetime, collapse and expiry. */

    /**
     * "`duration` is the trap's remaining lifetime. ControllerRepository deletes an expired
     * controller silently, which would strand the loc, so collapse one cycle early instead." A
     * portable trap leaves its wreck on the ground so the owner can still come back for the item.
     */
    @Test
    fun `an expiring portable trap leaves a wreck and deletes its controller`() {
        val owner = world.addPlayer(TRAP_TILE.translate(3, 3), hunterLvl = 99)
        val controller = world.layPortableTrap(TrapFamily.BOX, TRAP_TILE, owner)

        controller.duration = 1
        world.tick(controller)

        assertEquals("loc.hunting_boxtrap_failed", world.locNameAt(TRAP_TILE))
        assertNull(world.controllerAt(TRAP_TILE))
    }

    /** "Traps belong to a logged-in owner ... live despawns a player's traps when they leave." */
    @Test
    fun `a trap whose owner logged out collapses on the next tick`() {
        val owner = world.addPlayer(TRAP_TILE.translate(3, 3), hunterLvl = 99)
        val controller = world.layPortableTrap(TrapFamily.BOX, TRAP_TILE, owner)

        world.removePlayer(owner)
        world.tick(controller)

        assertEquals("loc.hunting_boxtrap_failed", world.locNameAt(TRAP_TILE))
        assertNull(world.controllerAt(TRAP_TILE))
    }

    /**
     * "A sprung trap waits for its owner rather than continuing to decay from wherever its lifetime
     * happened to be when the creature arrived." Without the reset, a trap that sprang late in its
     * life would collapse before the player could walk back to it and take the catch with it.
     */
    @Test
    fun `springing a trap resets its remaining lifetime`() {
        val controller = boxTrapWithChinchompaInRange()
        controller.duration = 5
        world.random.nextDouble = ScriptedRandom.ALWAYS_CATCH

        world.tick(controller)

        assertEquals(TRAP_LIFETIME_CYCLES, controller.duration)
    }

    /**
     * An unattended trap must *not* reset its duration, or a trap laid in an empty field would sit
     * armed forever and the cap would never free up.
     */
    @Test
    fun `an unattended trap does not reset its lifetime`() {
        val owner = world.addPlayer(TRAP_TILE.translate(3, 3), hunterLvl = 99)
        val controller = world.layPortableTrap(TrapFamily.BOX, TRAP_TILE, owner)

        controller.duration = 42
        world.tick(controller)

        assertEquals(42, controller.duration)
    }

    /** A controller whose loc has gone is deleted rather than left ticking against nothing. */
    @Test
    fun `a controller whose trap loc vanished deletes itself`() {
        val owner = world.addPlayer(TRAP_TILE.translate(3, 3), hunterLvl = 99)
        val controller = world.layPortableTrap(TrapFamily.BOX, TRAP_TILE, owner)

        val loc = world.locRepo.findExact(TRAP_TILE, LocShape.CentrepieceStraight)
        world.locRepo.del(loc!!, Int.MAX_VALUE)
        // The tick asserts the controller outlived a single cycle before accepting a missing loc.
        world.advance(2)

        world.tick(controller)

        assertNull(world.controllerAt(TRAP_TILE))
    }

    /* Helpers. */

    /** Puts a sprung trap and the creature it sprang on back the way they were. */
    private fun rearm(controller: Controller, prey: Npc) {
        controller.trapCreature = TRAP_CREATURE_NONE
        if (!prey.isVisible) {
            world.revealNpc(prey)
        }
    }

    /**
     * A box trap with its owner standing clear of it and a chinchompa one tile north.
     *
     * [hunterLvl] defaults to 99, where the chinchompa's rate exceeds `1.0` and the catch is
     * certain. Tests that need a miss pass [CHINCHOMPA_LEVEL] instead - see
     * [ScriptedRandom.HIGHEST_DRAW].
     */
    private fun boxTrapWithChinchompaInRange(hunterLvl: Int = 99): Controller {
        val owner = world.addPlayer(TRAP_TILE.translate(3, 3), hunterLvl = hunterLvl)
        val controller = world.layPortableTrap(TrapFamily.BOX, TRAP_TILE, owner)
        world.addNpc("npc.hunting_chinchompa", TRAP_TILE.translate(0, 1))
        return controller
    }

    private companion object {
        /** The regular chinchompa's Hunter requirement, where its rate is a real fraction (~57%). */
        const val CHINCHOMPA_LEVEL: Int = 53

        /** The wild kebbit's, i.e. the lowest deadfall creature's (~43%). */
        const val WILD_KEBBIT_LEVEL: Int = 23
    }
}
