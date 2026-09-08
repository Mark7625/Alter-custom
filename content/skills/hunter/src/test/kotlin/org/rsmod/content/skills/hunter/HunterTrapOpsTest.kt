package org.rsmod.content.skills.hunter

import dev.openrune.rscm.RSCM
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
import org.rsmod.content.skills.hunter.HunterTrapTestWorld.Companion.TRAP_TILE
import org.rsmod.game.entity.Controller
import org.rsmod.game.entity.Player

/**
 * The player-facing half of the engine: the `ProtectedAccess` ops, run over
 * `ProtectedAccessContextFactory.empty()` since no hunter op touches a context dependency. Two
 * things stay out of reach and are covered nowhere: the *timed* half of a loc change
 * (`LocRepository.processDurations` is internal and game-loop-driven), and `mes`/`soundSynth`
 * output (a `NoopClient` records nothing, so a refusal is observable but its message is not).
 * Serialised: `ServerCacheManager` is a singleton and `RSCM` memoises into a plain `HashMap`,
 * which is not safe to fill from the parallel execution `test-conventions` turns on.
 */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(HUNTER_TEST_WORLD_LOCK)
class HunterTrapOpsTest {
    private lateinit var world: HunterTrapTestWorld

    @BeforeEach
    fun setUp() {
        HunterTestCache.load()
        world = HunterTrapTestWorld()
    }

    /* layTrap. */

    @Test
    fun `laying a box trap consumes the trap item and records the coord`() {
        val player = hunter(level = 99, carrying = listOf("obj.hunting_box_trap"))

        assertTrue(world.runProtected(player) { it.layTrap(TrapFamily.BOX, TRAP_TILE) })

        assertEquals("loc.hunting_boxtrap_empty", world.locNameAt(TRAP_TILE))
        assertFalse(player.inv.contains("obj.hunting_box_trap"))
        assertEquals(listOf(TRAP_TILE.packed), player.hunterTrapCoords)
        assertNotNull(world.controllerAt(TRAP_TILE))
    }

    @Test
    fun `laying a trap without the item is refused`() {
        val player = hunter(level = 99, carrying = emptyList())

        assertFalse(world.runProtected(player) { it.layTrap(TrapFamily.BOX, TRAP_TILE) })

        assertNull(world.locAt(TRAP_TILE))
        assertNull(world.controllerAt(TRAP_TILE))
    }

    @Test
    fun `a tile that already holds a trap cannot take another`() {
        val player =
            hunter(level = 99, carrying = listOf("obj.hunting_box_trap", "obj.hunting_box_trap"))

        assertTrue(world.runProtected(player) { it.layTrap(TrapFamily.BOX, TRAP_TILE) })
        assertFalse(world.runProtected(player) { it.layTrap(TrapFamily.BOX, TRAP_TILE) })

        assertTrue(player.inv.contains("obj.hunting_box_trap"), "The second trap is not consumed.")
    }

    /**
     * The cap is read from the *effective* level, and a level-1 hunter gets one trap. The stored
     * coords are what enforces it, so this is also the check that laying writes them.
     */
    @Test
    fun `a level-1 hunter can only lay one trap`() {
        val snare = "obj.hunting_ojibway_bird_snare"
        val player = hunter(level = 1, carrying = listOf(snare, snare))

        assertTrue(world.runProtected(player) { it.layTrap(TrapFamily.SNARE, TRAP_TILE) })
        assertFalse(
            world.runProtected(player) { it.layTrap(TrapFamily.SNARE, TRAP_TILE.translate(2, 0)) }
        )

        assertEquals(1, player.hunterTrapCoords.size)
        assertNull(world.locAt(TRAP_TILE.translate(2, 0)))
    }

    /* collectTrap and takeTrap. */

    @Test
    fun `collecting a sprung box trap awards the catch, returns the trap and grants xp`() {
        val player = hunter(level = 99, carrying = listOf("obj.hunting_box_trap"))
        val controller = springBoxTrapOn(player)

        val sprung = world.boundLocAt(TRAP_TILE)!!
        assertTrue(world.runProtected(player) { it.collectTrap(sprung) })

        assertTrue(player.inv.contains("obj.chinchompa_captured"), "The catch.")
        assertTrue(player.inv.contains("obj.hunting_box_trap"), "The trap item comes back.")
        assertNull(world.locAt(TRAP_TILE))
        assertNull(world.controllerAt(TRAP_TILE))
        assertEquals(emptyList<Int>(), player.hunterTrapCoords)

        // Creature xp is stored x10 in the packed table and divided by ten once, at the award.
        val creature = HunterCreatures.all[controller.trapCreature]
        assertEquals(creature.xp / 10, player.statMap.getXP("stat.hunter"))
    }

    /**
     * The Hunter xp modifier is *applied*, not merely injected.
     *
     * A world built with no modifiers is a flat 1.0, so the `* xpMods.get(player, "stat.hunter")`
     * on the award site could be deleted with the rest of the suite still green. Running the same
     * catch twice, once in a doubled world, is what makes the multiplication load-bearing.
     */
    @Test
    fun `the xp modifier scales the trap award`() {
        val plain = collectedChinchompaFineXp(hunterXpBonus = 0.0)
        val doubled = collectedChinchompaFineXp(hunterXpBonus = DOUBLE_HUNTER_XP)

        // The grey chinchompa's 198.4 xp, which is why the tables store tenths at all.
        assertEquals(1984, plain, "unmodified, a grey chinchompa is 198.4 xp")
        assertEquals(3968, doubled, "a +100% modifier makes it 396.8")
    }

    /**
     * One collected box trap holding a chinchompa, in tenths of a point.
     *
     * Replaces [world]: `setUp` puts a fresh default one back before the next test.
     */
    private fun collectedChinchompaFineXp(hunterXpBonus: Double): Int {
        world = HunterTrapTestWorld(hunterXpBonus = hunterXpBonus)
        val player = hunter(level = 99, carrying = listOf("obj.hunting_box_trap"))
        springBoxTrapOn(player)
        val sprung = world.boundLocAt(TRAP_TILE)!!

        assertTrue(world.runProtected(player) { it.collectTrap(sprung) })

        return player.statMap.getFineXP("stat.hunter")
    }

    @Test
    fun `collecting someone else's trap is refused and leaves it standing`() {
        val owner = hunter(level = 99, carrying = listOf("obj.hunting_box_trap"))
        springBoxTrapOn(owner)

        val thief = hunter(level = 99, carrying = emptyList())
        val sprung = world.boundLocAt(TRAP_TILE)!!
        assertFalse(world.runProtected(thief) { it.collectTrap(sprung) })

        assertFalse(thief.inv.contains("obj.chinchompa_captured"))
        assertNotNull(world.controllerAt(TRAP_TILE))
    }

    /**
     * A refused collect must be a no-op, not a partial one: the space check runs before anything is
     * awarded, so the trap is still there to try again with a slot free.
     */
    @Test
    fun `a full inventory refuses the collect and awards nothing`() {
        val player = hunter(level = 99, carrying = listOf("obj.hunting_box_trap"))
        springBoxTrapOn(player)
        val access = world.protectedAccess(player)
        while (player.inv.freeSpace() > 0) {
            access.invAdd(player.inv, "obj.bones", 1)
        }

        val sprung = world.boundLocAt(TRAP_TILE)!!
        assertFalse(world.runProtected(player) { it.collectTrap(sprung) })

        assertFalse(player.inv.contains("obj.chinchompa_captured"))
        assertNotNull(world.controllerAt(TRAP_TILE), "The trap is still there to come back to.")
        assertEquals(0, player.statMap.getXP("stat.hunter"), "No xp on a refused collect.")
    }

    /**
     * A collapsed trap outlives its controller, so `takeTrap` on one has nobody to check ownership
     * against; whoever clears the tile keeps the trap item. It cannot mint a second one because the
     * loc is deleted in the same call.
     */
    @Test
    fun `taking a collapsed trap hands the item back and clears the tile`() {
        val owner = hunter(level = 99, carrying = listOf("obj.hunting_box_trap"))
        world.runProtected(owner) { it.layTrap(TrapFamily.BOX, TRAP_TILE) }
        val controller = world.controllerAt(TRAP_TILE)!!
        controller.duration = 1
        world.tick(controller)
        assertNull(world.controllerAt(TRAP_TILE), "Collapsed.")

        val wreck = world.boundLocAt(TRAP_TILE)!!
        assertTrue(world.runProtected(owner) { it.takeTrap(wreck, TrapFamily.BOX) })

        assertTrue(owner.inv.contains("obj.hunting_box_trap"))
        assertNull(world.locAt(TRAP_TILE))
    }

    /** `takeTrap` on a trap that still has a controller routes to the collect transaction. */
    @Test
    fun `taking a sprung trap collects it instead`() {
        val player = hunter(level = 99, carrying = listOf("obj.hunting_box_trap"))
        springBoxTrapOn(player)

        val sprung = world.boundLocAt(TRAP_TILE)!!
        assertTrue(world.runProtected(player) { it.takeTrap(sprung, TrapFamily.BOX) })

        assertTrue(player.inv.contains("obj.chinchompa_captured"))
        assertTrue(player.inv.contains("obj.hunting_box_trap"))
    }

    /* Helpers. */

    private fun hunter(level: Int, carrying: List<String>): Player {
        val player = world.addPlayer(TRAP_TILE.translate(3, 3), hunterLvl = level)
        val access = world.protectedAccess(player)
        for (obj in carrying) {
            access.invAdd(player.inv, obj, 1)
        }
        return player
    }

    /** Lays [player]'s box trap on [TRAP_TILE] and springs it on a chinchompa, then settles it. */
    private fun springBoxTrapOn(player: Player): Controller {
        world.runProtected(player) { it.layTrap(TrapFamily.BOX, TRAP_TILE) }
        val controller = world.controllerAt(TRAP_TILE)!!
        world.addNpc("npc.hunting_chinchompa", TRAP_TILE.translate(0, 1))
        world.random.nextDouble = ScriptedRandom.ALWAYS_CATCH
        world.tick(controller)
        world.advance(TRAP_SPRING_CYCLES)
        world.tick(controller)
        return controller
    }

    private fun objId(internal: String): Int = RSCM.getRSCM(internal)
}
