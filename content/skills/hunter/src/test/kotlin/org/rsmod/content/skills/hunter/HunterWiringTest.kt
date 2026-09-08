package org.rsmod.content.skills.hunter

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.controller.events.ControllerAIEvents
import org.rsmod.api.player.events.interact.HeldObjEvents
import org.rsmod.api.player.events.interact.LocContentEvents
import org.rsmod.api.player.events.interact.LocEvents
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.interact.HeldOp
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.type.hasInvOp
import org.rsmod.game.type.hasOp
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * What the hunter scripts actually put on the event bus. The rest of the suite calls the op bodies
 * directly, so a handler registered on the wrong group, the wrong op index, or not at all would
 * leave everything green and the feature dead in game. Each script's `startup()` is run against a
 * fresh [EventBus] - the same mechanism `PluginScriptLoader` uses at boot - and every lookup
 * resolves its key through the same call the matching `on...` helper uses. The second half walks
 * the loc states the dispatch branches on and asserts the *packed cache* carries the group and ops
 * each handler is registered for. Serialised: `ServerCacheManager` is a singleton and `RSCM`
 * memoises into a plain `HashMap`.
 */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(HUNTER_TEST_WORLD_LOCK)
class HunterWiringTest {
    private lateinit var scripts: HunterScripts

    @BeforeEach
    fun setUp() {
        HunterTestCache.load()
        scripts = HunterScripts()
    }

    /* Per-technique registration. */

    @Test
    fun `bird snare registers its lay op, both loc ops and the shared trap tick`() {
        val bus = Wiring().start(scripts.birdSnare)

        assertTrue(bus.hasOpHeld1(SNARE_OBJ), "`Lay` on $SNARE_OBJ")
        assertTrue(bus.hasOpContentLoc1(SNARE_GROUP), "op1 (`Dismantle`/`Check`) on $SNARE_GROUP")
        assertTrue(bus.hasOpContentLoc2(SNARE_GROUP), "op2 (`Investigate`) on $SNARE_GROUP")
        assertTrue(bus.hasAiConTimer(TRAP_CONTROLLER), "the trap tick on $TRAP_CONTROLLER")

        // Its three op1 states share one group, so nothing may leak onto another family's.
        assertFalse(bus.hasOpContentLoc1(BOX_GROUP), "the snare must not claim $BOX_GROUP")
    }

    @Test
    fun `box trap registers its lay op and both loc ops, and not the shared trap tick`() {
        val bus = Wiring().start(scripts.boxTrap)

        assertTrue(bus.hasOpHeld1(BOX_OBJ), "`Lay` on $BOX_OBJ")
        assertTrue(bus.hasOpContentLoc1(BOX_GROUP), "op1 (`Dismantle`/`Check`) on $BOX_GROUP")
        assertTrue(bus.hasOpContentLoc2(BOX_GROUP), "op2 (`Investigate`) on $BOX_GROUP")

        // Deliberate: registering it here as well would run every laid trap's tick twice a cycle.
        assertFalse(bus.hasAiConTimer(TRAP_CONTROLLER), "the trap tick belongs to BirdSnareEvents")
        assertFalse(bus.hasOpContentLoc1(SNARE_GROUP), "the box trap must not claim $SNARE_GROUP")
    }

    /* The once-only invariant. */

    /**
     * `onAiConTimer(TRAP_CONTROLLER)` is registered by exactly one trap script. Counted per script
     * on its own bus: `EventBus.subscribeKeyed` throws on a duplicate key, so on a shared bus this
     * would assert the engine's guard instead of the invariant.
     */
    @Test
    fun `the shared trap tick is registered exactly once across the whole trap family`() {
        val registrars =
            scripts.trapFamily.filter { Wiring().start(it).hasAiConTimer(TRAP_CONTROLLER) }

        assertEquals(
            listOf(BirdSnareEvents::class.java),
            registrars.map { it.javaClass },
            "exactly one trap script may register $TRAP_CONTROLLER",
        )
    }

    /** The same invariant, seen from the boot path: both scripts share one bus at startup. */
    @Test
    fun `both scripts start together on one bus without a duplicate key`() {
        val bus = Wiring().start(*scripts.all.toTypedArray())

        assertTrue(bus.hasAiConTimer(TRAP_CONTROLLER))
        for (group in ALL_TRAP_GROUPS) {
            assertTrue(bus.hasOpContentLoc1(group), "op1 on $group")
            assertTrue(bus.hasOpContentLoc2(group), "op2 on $group")
        }
    }

    /* Op-index and shadowing guards. */

    /**
     * No hunter handler sits on an op slot the client never draws.
     *
     * `onOpContentLocN` dispatches on the group and slot, not on the op's label, so a handler on op3
     * would be silently unreachable rather than wrong-looking.
     */
    @Test
    fun `no loc handler is registered on an op index the hunter locs do not carry`() {
        val bus = Wiring().start(*scripts.all.toTypedArray())

        for (group in ALL_TRAP_GROUPS) {
            val id = group.asRSCM(RSCMType.CONTENT)
            assertFalse(bus.eventBus.contains(LocContentEvents.Op3::class.java, id), "op3 on $group")
            assertFalse(bus.eventBus.contains(LocContentEvents.Op4::class.java, id), "op4 on $group")
            assertFalse(bus.eventBus.contains(LocContentEvents.Op5::class.java, id), "op5 on $group")
        }
    }

    /**
     * No hunter loc is claimed by loc **id** as well as by content group.
     *
     * `LocInteractions.opTrigger` tries the type-level `LocEvents.OpN` first and returns as soon as
     * it hits, so a per-id registration on any hunter state would shadow the content handler for
     * that state alone - the one shape of wiring bug that presents as "the trap works except when
     * it's full".
     */
    @Test
    fun `no per-loc-id registration shadows a hunter content group`() {
        val bus = Wiring().start(*scripts.all.toTypedArray())

        for ((loc, _) in dispatchedLocStates()) {
            val id = loc.asRSCM(RSCMType.LOC)
            assertFalse(bus.eventBus.contains(LocEvents.Op1::class.java, id), "op1 shadow on $loc")
            assertFalse(bus.eventBus.contains(LocEvents.Op2::class.java, id), "op2 shadow on $loc")
        }
    }

    /* The packed-cache half: a registration is unreachable without the declaration behind it. */

    /**
     * Every loc state a hunter handler dispatches on carries the group it is registered under, and
     * the op slot it is dispatched for.
     *
     * This is the half a bus assertion cannot see. `content.hunter_box_trap` resolving to an id and
     * `hunting_boxtrap_failed` carrying that id are two independent declarations; the second was
     * missing once already, and a collapsed box trap was unclearable until it was added.
     */
    @Test
    fun `every dispatched loc state carries its content group and the op it is dispatched for`() {
        for ((loc, expected) in dispatchedLocStates()) {
            val type =
                ServerCacheManager.getObject(loc.asRSCM(RSCMType.LOC))
                    ?: error("No packed loc definition for $loc")
            assertTrue(
                type.isContentType(expected.group),
                "$loc must carry ${expected.group}, has contentGroup=${type.contentGroup}",
            )
            for (op in expected.ops) {
                assertTrue(type.hasOp(op), "$loc must carry ${op.name} (${type.actions})")
            }
        }
    }

    /**
     * The op-less transient frames are under none of the hunter groups, and carry no ops - a group
     * would put a state under a handler with no branch for it. The check is "not one of ours"
     * rather than `contentGroup == -1` because `-1` never survives the pack: opcode 6 is a USHORT,
     * so an unset group reads back as `65535` (measured: 59,717 of 60,000 locs).
     */
    @Test
    fun `the op-less transient loc states are under none of the hunter content groups`() {
        val claimed =
            transientLocStates().mapNotNull { loc ->
                val type =
                    ServerCacheManager.getObject(loc.asRSCM(RSCMType.LOC))
                        ?: error("No packed loc definition for $loc")
                val group = ALL_TRAP_GROUPS.firstOrNull(type::isContentType)
                group?.let { loc to it }
            }
        assertEquals(emptyList<Pair<String, String>>(), claimed, "transient frames must be ungrouped")

        for (loc in transientLocStates()) {
            val type = checkNotNull(ServerCacheManager.getObject(loc.asRSCM(RSCMType.LOC)))
            assertFalse(type.hasOp(InteractionOp.Op1), "$loc must carry no op1")
            assertFalse(type.hasOp(InteractionOp.Op2), "$loc must carry no op2")
        }
    }

    /** Every obj a lay op is registered on really draws an inventory op1. */
    @Test
    fun `every lay obj carries an inventory op1 on the packed obj definition`() {
        for (obj in listOf(SNARE_OBJ, BOX_OBJ)) {
            val type =
                ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ))
                    ?: error("No packed obj definition for $obj")
            assertTrue(type.hasInvOp(HeldOp.Op1), "$obj must carry iop1")
        }
    }

    /* Fixtures. */

    /** A loc state the dispatch branches on: which group routes it, and which ops it must draw. */
    private data class LocExpectation(val group: String, val ops: List<InteractionOp>)

    /**
     * Every loc state a hunter handler can be reached through, mapped to the group that routes it.
     *
     * Built from [HunterTrapStates] and [HunterCreatures] - the same tables the production `when`
     * branches read - rather than from a transcribed list, so a creature added to a table joins this
     * matrix on its own.
     */
    private fun dispatchedLocStates(): Map<String, LocExpectation> = buildMap {
        fun put(loc: String, group: String, vararg ops: InteractionOp) {
            put(loc, LocExpectation(group, ops.toList()))
        }

        // Bird snare: `Dismantle` on the armed and broken states, `Check` on each `_full_`, and
        // `Investigate` on the armed one alone.
        put(checkNotNull(HunterTrapStates.setLoc(TrapFamily.SNARE)), SNARE_GROUP, Op1, Op2)
        put(HunterTrapStates.failedLoc(TrapFamily.SNARE), SNARE_GROUP, Op1)
        for (creature in creatures(TrapFamily.SNARE)) {
            put(HunterTrapStates.fullLoc(creature), SNARE_GROUP, Op1)
        }

        // Box trap: the same shape. `hunting_boxtrap_failed` is the state that was missing its group.
        put(checkNotNull(HunterTrapStates.setLoc(TrapFamily.BOX)), BOX_GROUP, Op1, Op2)
        put(HunterTrapStates.failedLoc(TrapFamily.BOX), BOX_GROUP, Op1)
        for (creature in creatures(TrapFamily.BOX)) {
            put(HunterTrapStates.fullLoc(creature), BOX_GROUP, Op1)
        }
    }

    /**
     * The frames shown mid-spring, which carry no ops and therefore no content group.
     *
     * The four compass offsets are there for the box trap alone: it is the only family with one
     * `_trapping_` loc per side, and [HunterTrapStates.trappingLoc] is the function that picks one.
     * Every other family ignores the offsets, so the set collapses on its own.
     */
    private fun transientLocStates(): Set<String> = buildSet {
        add(HunterTrapStates.failingLoc(TrapFamily.SNARE))
        add(HunterTrapStates.failingLoc(TrapFamily.BOX))

        val offsets = listOf(0 to 1, 0 to -1, 1 to 0, -1 to 0)
        for (creature in HunterCreatures.all) {
            for ((dx, dz) in offsets) {
                add(HunterTrapStates.trappingLoc(creature, dx, dz))
            }
        }
    }

    private fun creatures(family: TrapFamily): List<HunterCreature> =
        HunterCreatures.all.filter { it.family == family }

    /**
     * The ten scripts, built over the same worlds the rest of the suite uses.
     *
     * The collaborators are only there to satisfy the constructors - `startup()` never touches them,
     * because every handler body it registers is a lambda that is not run here.
     */
    private class HunterScripts {
        private val trapWorld = HunterTrapTestWorld()

        val birdSnare = BirdSnareEvents(trapWorld.trap, trapWorld.conRepo)
        val boxTrap = BoxTrapEvents(trapWorld.trap, trapWorld.conRepo)

        /** The five families that share [TRAP_CONTROLLER], in declaration order. */
        val trapFamily: List<PluginScript> = listOf(birdSnare, boxTrap)

        val all: List<PluginScript> = trapFamily
    }

    /**
     * A fresh [EventBus] and [EngineQueueCache], and the readers for what a script put in them.
     *
     * Each reader resolves its key through the same `asRSCM` / `composeLongKey` call the matching
     * `on…` helper uses, so the test and the game ask the bus the same question.
     */
    private class Wiring {
        val eventBus = EventBus()
        private val engineQueue = EngineQueueCache()
        private val context = ScriptContext(eventBus, CheatCommandMap(), engineQueue)

        fun start(vararg scripts: PluginScript): Wiring = apply {
            for (script in scripts) {
                with(script) { context.startup() }
            }
        }

        fun hasOpContentLoc1(content: String): Boolean =
            eventBus.contains(LocContentEvents.Op1::class.java, content.asRSCM(RSCMType.CONTENT))

        fun hasOpContentLoc2(content: String): Boolean =
            eventBus.contains(LocContentEvents.Op2::class.java, content.asRSCM(RSCMType.CONTENT))

        fun hasOpHeld1(obj: String): Boolean =
            eventBus.contains(HeldObjEvents.Op1::class.java, obj.asRSCM(RSCMType.OBJ))

        /**
         * `onAiConTimer` subscribes a [org.rsmod.events.KeyedEvent], not a suspending one, so
         * `EventBus.contains` - which only reads the suspend map - cannot answer this. The keyed
         * map's own `get` can, and a non-null handler is the same thing the `AiConTimerProcessor`
         * looks up each cycle.
         */
        fun hasAiConTimer(controller: String): Boolean =
            eventBus.keyed[
                ControllerAIEvents.Timer::class.java, controller.asRSCM(RSCMType.CONTROLLER)] != null
    }

    private companion object {
        private val Op1 = InteractionOp.Op1
        private val Op2 = InteractionOp.Op2

        private const val SNARE_GROUP = "content.hunter_bird_snare"
        private const val BOX_GROUP = "content.hunter_box_trap"

        private val ALL_TRAP_GROUPS = listOf(SNARE_GROUP, BOX_GROUP)

        private const val SNARE_OBJ = "obj.hunting_ojibway_bird_snare"
        private const val BOX_OBJ = "obj.hunting_box_trap"
    }
}
