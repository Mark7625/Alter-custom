package org.rsmod.content.bosses.vorkath

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VorkathTimelineTest {
    @Test
    fun sixNormalLaunchesAreExactlyFiveTicksApartBeforeTheSpecial() {
        val timeline = activeTimeline(VorkathSpecial.ACID)
        val normals = mutableListOf<Int>()
        val specials = mutableListOf<Int>()
        for (tick in 8..38) {
            if (!timeline.attackDue(tick)) continue
            if (timeline.standardAttacks == 6) {
                timeline.beginSpecial(tick)
                specials += tick
            } else {
                timeline.standardLaunched(tick)
                normals += tick
            }
        }
        assertEquals(listOf(8, 13, 18, 23, 28, 33), normals)
        assertEquals(listOf(5, 5, 5, 5, 5), normals.zipWithNext { a, b -> b - a })
        assertEquals(listOf(38), specials)
        assertFalse(timeline.attackDue(43))
        assertFailsWith<IllegalStateException> { timeline.standardLaunched(43) }
    }

    @Test
    fun bothInitialSelectionsAlternateThroughSeveralCompleteNormalCycles() {
        for (initial in VorkathSpecial.entries) {
            val timeline = activeTimeline(initial)
            val actual = mutableListOf<VorkathSpecial>()
            var tick = 8
            repeat(4) {
                repeat(6) {
                    timeline.standardLaunched(tick)
                    tick += 5
                }
                actual += timeline.beginSpecial(tick)
                timeline.finishSpecial(tick + 40, recoveryTicks = 5)
                tick += 45
            }
            val alternate = VorkathRules.nextSpecial(initial)
            assertEquals(listOf(initial, alternate, initial, alternate), actual)
        }
    }

    @Test
    fun acidStartsItsTwentyFiveOneTickShotsFourTicksAfterTheCast() {
        val timeline = activeTimeline(VorkathSpecial.ACID)
        launchSix(timeline)
        timeline.beginSpecial(38)
        val launches = mutableListOf<Int>()
        for (tick in 38..100) {
            if (!timeline.rapidShotDue(tick)) continue
            timeline.rapidLaunched(tick)
            launches += tick
            assertFalse(timeline.rapidShotDue(tick), "Duplicate rapid shot at tick $tick")
            assertFalse(timeline.attackDue(tick), "Normal attack during acid at tick $tick")
        }
        assertEquals((42..66).toList(), launches)
        assertEquals(25, timeline.shotsFired)
        assertEquals(List(24) { 1 }, launches.zipWithNext { a, b -> b - a })
        assertFailsWith<IllegalStateException> { timeline.rapidLaunched(101) }
    }

    @Test
    fun wakeStartsAtTheCapturedTickAndDoesNotAttackBeforeActivation() {
        val timeline = VorkathTimeline(VorkathSpecial.ACID)
        timeline.wake(5085)
        assertEquals(5092, timeline.wakeCycle)
        assertFalse(timeline.attackDue(5092))
        timeline.activate(5092)
        assertFalse(timeline.attackDue(5092))
        assertTrue(timeline.attackDue(5093))
    }

    @Test
    fun invalidStatesCannotLaunchOrResolveAnAttack() {
        val sleeping = VorkathTimeline(VorkathSpecial.ACID)
        assertFailsWith<IllegalStateException> { sleeping.standardLaunched(100) }
        assertFailsWith<IllegalStateException> { sleeping.beginSpecial(100) }
        assertFailsWith<IllegalStateException> { sleeping.rapidLaunched(100) }
        assertFailsWith<IllegalStateException> { sleeping.finishSpecial(100, 1) }
        assertFailsWith<IllegalStateException> { sleeping.activate(100) }
        val active = activeTimeline(VorkathSpecial.ZOMBIFIED_SPAWN)
        assertFailsWith<IllegalStateException> { active.beginSpecial(8) }
        assertFailsWith<IllegalStateException> { active.standardLaunched(7) }
        assertFailsWith<IllegalStateException> { active.rapidLaunched(8) }
        assertFailsWith<IllegalStateException> { active.wake(8) }
        launchSix(active)
        assertFailsWith<IllegalStateException> { active.standardLaunched(38) }
        active.beginSpecial(38)
        assertFalse(active.rapidShotDue(42))
    }

    @Test
    fun allResetStatesDiscardDeadlinesAndReentryHasAFreshCycle() {
        for (state in VorkathState.entries) {
            val timeline = activeTimeline(VorkathSpecial.ACID)
            launchSix(timeline)
            timeline.beginSpecial(38)
            timeline.rapidLaunched(42)
            timeline.state = state
            timeline.reset(VorkathSpecial.ZOMBIFIED_SPAWN)
            assertEquals(VorkathState.SLEEPING, timeline.state)
            assertEquals(VorkathSpecial.ZOMBIFIED_SPAWN, timeline.nextSpecial)
            assertEquals(0, timeline.standardAttacks)
            assertEquals(0, timeline.shotsFired)
            assertEquals(Int.MAX_VALUE, timeline.wakeCycle)
            assertEquals(Int.MAX_VALUE, timeline.nextAttackCycle)
            assertEquals(Int.MAX_VALUE, timeline.specialStartCycle)
            assertFalse(timeline.attackDue(1000))
            assertFalse(timeline.rapidShotDue(1000))
            timeline.wake(1000)
            timeline.activate(1007)
            assertFalse(timeline.attackDue(1007))
            assertTrue(timeline.attackDue(1008))
            timeline.standardLaunched(1008)
            assertEquals(1, timeline.standardAttacks)
        }
    }

    @Test
    fun specialResolutionRestoresNormalAttacksAtItsRecoveryDeadline() {
        for (special in VorkathSpecial.entries) {
            val timeline = activeTimeline(special)
            launchSix(timeline)
            timeline.beginSpecial(38)
            timeline.finishSpecial(70, recoveryTicks = 5)
            assertEquals(VorkathState.ACTIVE, timeline.state)
            assertFalse(timeline.attackDue(74))
            assertTrue(timeline.attackDue(75))
            assertFalse(timeline.rapidShotDue(75))
            assertEquals(Int.MAX_VALUE, timeline.specialStartCycle)
        }
    }

    private fun activeTimeline(first: VorkathSpecial): VorkathTimeline =
        VorkathTimeline(first).also {
            it.wake(0)
            it.activate(7)
        }

    private fun launchSix(timeline: VorkathTimeline) {
        for (tick in 8..33 step 5) timeline.standardLaunched(tick)
    }
}
