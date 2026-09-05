package org.rsmod.content.skills.agility.rooftop

import jakarta.inject.Inject
import org.rsmod.api.player.stat.baseAgilityLvl
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.game.entity.Player

/** Spawns marks of grace on a course's rooftops when a player completes a lap. */
class MarksOfGrace @Inject constructor(private val objRepo: ObjRepository) {
    /**
     * Rolls the course's mark chance for [player] and, on success, drops a mark only they can see
     * on one of the course's roofs. Players 20 or more levels above the course requirement spawn
     * marks at a fifth of the normal rate.
     */
    fun roll(player: Player, random: GameRandom, layout: CourseLayout): Boolean {
        val course = layout.course
        var denominator = course.markDenominator
        if (player.baseAgilityLvl >= course.level + OVERLEVEL_THRESHOLD) {
            denominator *= OVERLEVEL_PENALTY
        }
        if (random.of(denominator) >= course.markNumerator) {
            return false
        }
        val tile = random.pick(layout.markTiles)
        objRepo.add(MARK_OF_GRACE, tile, duration = MARK_DURATION, receiver = player)
        return true
    }

    companion object {
        const val MARK_OF_GRACE = "obj.grace"

        /** Marks stay on the roof for ten minutes before vanishing. */
        const val MARK_DURATION = 1000

        const val OVERLEVEL_THRESHOLD = 20
        const val OVERLEVEL_PENALTY = 5
    }
}
