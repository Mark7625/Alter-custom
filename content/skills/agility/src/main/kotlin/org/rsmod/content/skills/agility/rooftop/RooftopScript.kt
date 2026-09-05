package org.rsmod.content.skills.agility.rooftop

import jakarta.inject.Inject
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.agilityLvl
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.skills.agility.AgilityAnims
import org.rsmod.content.skills.agility.balanceAlong
import org.rsmod.content.skills.agility.climbTo
import org.rsmod.content.skills.agility.fallTo
import org.rsmod.content.skills.agility.leapTo
import org.rsmod.content.skills.agility.stepOnto
import org.rsmod.content.skills.agility.successChance
import org.rsmod.content.skills.agility.zipTo
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Handles every obstacle of the rooftop agility courses.
 *
 * Lap progress is a per-session bit mask of the obstacles completed on the current course. The
 * final obstacle only pays its lap bonus (and rolls for a mark of grace) when every other obstacle
 * of that course has been completed since the last finish, which mirrors the live game where
 * skipping an obstacle forfeits the completion bonus.
 */
class RooftopScript @Inject constructor(private val marks: MarksOfGrace) : PluginScript() {
    override fun ScriptContext.startup() {
        for (layout in RooftopCourses.layouts) {
            for ((index, obstacle) in layout.obstacles.withIndex()) {
                for (loc in obstacle.locs) {
                    onOpLoc1(loc) { attempt(layout, index, obstacle) }
                }
            }
        }
    }

    private suspend fun ProtectedAccess.attempt(
        layout: CourseLayout,
        index: Int,
        obstacle: RooftopObstacle,
    ) {
        val course = layout.course
        arriveDelay()
        if (player.agilityLvl < course.level) {
            mes("You need an Agility level of ${course.level} to use this course.")
            return
        }
        stepOnto(obstacle.start)

        val failure = obstacle.failure?.takeIf { rollFailure(course, it) }
        val completed = perform(obstacle.move, failure)
        if (!completed) {
            player.courseProgress = 0
            return
        }

        statAdvance(AGILITY, obstacle.xp)
        val progress = player.progressOn(course) or (1 shl index)
        if (!obstacle.isFinish) {
            player.courseProgress = pack(course, progress)
            return
        }

        player.courseProgress = 0
        if (progress == layout.fullMask) {
            statAdvance(AGILITY, obstacle.lapBonusXp)
            marks.roll(player, random, layout)
        }
    }

    private fun ProtectedAccess.rollFailure(course: RooftopCourse, failure: ObstacleFailure): Boolean {
        val level = player.agilityLvl
        if (level >= failure.noFailLevel) {
            return false
        }
        val chance = successChance(level, course.level, failure.noFailLevel)
        return random.of(100) >= chance
    }

    /** Runs [move], or the failure sequence when [failure] is set. Returns `false` on a fail. */
    private suspend fun ProtectedAccess.perform(move: ObstacleMove, failure: ObstacleFailure?): Boolean {
        when (move) {
            is ObstacleMove.Climb -> climbTo(move.dest, move.seq, move.ticks)
            is ObstacleMove.Leap -> {
                if (failure != null) {
                    fallTo(failure.landing, move.seq, failure.minDamage, failure.maxDamage)
                    return false
                }
                leapTo(move.dest, move.seq, move.ticks)
            }
            is ObstacleMove.Zipline -> {
                if (failure != null) {
                    fallTo(failure.landing, AgilityAnims.ZIPLINE_GRAB, failure.minDamage, failure.maxDamage)
                    return false
                }
                zipTo(move.dest, move.ticks)
            }
            is ObstacleMove.Balance -> {
                val stopAt = if (failure != null) move.path.size / 2 else -1
                val crossed = balanceAlong(move.path, move.style, stopAt)
                if (!crossed && failure != null) {
                    fallTo(
                        failure.landing,
                        AgilityAnims.BALANCE_STUMBLE,
                        failure.minDamage,
                        failure.maxDamage,
                    )
                    return false
                }
            }
        }
        return true
    }

    private companion object {
        const val AGILITY = "stat.agility"

        /** Packed `course ordinal shl 16 or completed-obstacle mask`; not persisted. */
        val COURSE_PROGRESS = AttributeKey<Int>()

        var Player.courseProgress: Int
            get() = attr[COURSE_PROGRESS] ?: 0
            set(value) {
                attr[COURSE_PROGRESS] = value
            }

        fun Player.progressOn(course: RooftopCourse): Int {
            val packed = courseProgress
            return if (packed shr 16 == course.ordinal) packed and 0xFFFF else 0
        }

        fun pack(course: RooftopCourse, mask: Int): Int = (course.ordinal shl 16) or mask
    }
}
