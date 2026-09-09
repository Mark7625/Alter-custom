package org.rsmod.content.skills.agility.rooftop

import org.rsmod.content.skills.agility.AgilityAnims
import org.rsmod.content.skills.agility.BalanceStyle
import org.rsmod.map.CoordGrid

/**
 * The rooftop courses, with the Agility level needed to start them and the marks of grace chance
 * ([markNumerator] in [markDenominator]) rolled when a full lap is completed.
 */
enum class RooftopCourse(
    val displayName: String,
    val level: Int,
    val markNumerator: Int,
    val markDenominator: Int,
) {
    Draynor("Draynor Village", 1, 1, 3),
    AlKharid("Al Kharid", 20, 1, 3),
    Varrock("Varrock", 30, 1, 3),
    Canifis("Canifis", 40, 2, 3),
    Falador("Falador", 50, 1, 5),
    Seers("Seers' Village", 60, 1, 3),
    Pollnivneach("Pollnivneach", 70, 1, 3),
    Rellekka("Rellekka", 80, 1, 3),
    Ardougne("Ardougne", 90, 1, 3),
}

/** How the player is moved once an obstacle is started. */
sealed class ObstacleMove {
    abstract val destination: CoordGrid

    /**
     * Play [seq] for [ticks] cycles, then appear on [dest]. Used for walls, trees and crates. When
     * [ticks] is null the move waits for the whole animation.
     */
    data class Climb(
        val dest: CoordGrid,
        val seq: String = AgilityAnims.WALL_CLIMB,
        val ticks: Int? = null,
    ) : ObstacleMove() {
        override val destination: CoordGrid get() = dest
    }

    /**
     * Glide to [dest] over [ticks] cycles with an `exactmove`. Used for gaps, swings and edges. When
     * [ticks] is null the glide lasts as long as the animation, rounded down to whole ticks.
     *
     * A leap to another level glides on [glideLevel], the starting level unless given, and changes
     * level on landing. Set it where the starting level has no terrain past the edge (the client
     * then draws the glide rising instead of falling) or where the drop was authored into another
     * level's tile heights.
     */
    data class Leap(
        val dest: CoordGrid,
        val seq: String = AgilityAnims.JUMP,
        val ticks: Int? = null,
        val glideLevel: Int? = null,
    ) : ObstacleMove() {
        override val destination: CoordGrid get() = dest
    }

    /** Walk [path] one tile per tick with the [style] animations. Used for ropes and bars. */
    data class Balance(
        val path: List<CoordGrid>,
        val style: BalanceStyle = BalanceStyle.Tightrope,
    ) : ObstacleMove() {
        init {
            require(path.isNotEmpty()) { "Balance path must not be empty." }
        }

        override val destination: CoordGrid get() = path.last()
    }

    /** Grab a zip line and slide to [dest] over [ticks] cycles. */
    data class Zipline(val dest: CoordGrid, val ticks: Int = 3) : ObstacleMove() {
        override val destination: CoordGrid get() = dest
    }
}

/**
 * Failure rules for an obstacle: it can be failed until the player reaches [noFailLevel], dropping
 * them on [landing] for [minDamage]..[maxDamage] damage.
 */
data class ObstacleFailure(
    val noFailLevel: Int,
    val landing: CoordGrid,
    val minDamage: Int,
    val maxDamage: Int,
)

/**
 * One obstacle of a rooftop course.
 *
 * @param locs Every loc gameval that starts this obstacle (some ledges are split over two locs).
 * @param start The tile the player is placed on before the movement begins.
 * @param xp Experience granted every time the obstacle is completed.
 * @param lapBonusXp Extra experience granted when this obstacle completes a full, in-order lap.
 *   Only the final obstacle of a course has a bonus; it also rolls for a mark of grace.
 * @param apRange When greater than zero the obstacle also starts once the player is within this
 *   many tiles of the loc with a line of sight to it, without having to reach it. Needed where the
 *   map fences the loc off from the tile it is used from (a railing between a landing platform
 *   and the tree that is swung from), which makes the loc unreachable to the route finder.
 */
data class RooftopObstacle(
    val locs: List<String>,
    val name: String,
    val xp: Double,
    val start: CoordGrid,
    val move: ObstacleMove,
    val failure: ObstacleFailure? = null,
    val lapBonusXp: Double = 0.0,
    val apRange: Int = 0,
) {
    val isFinish: Boolean get() = lapBonusXp > 0.0

    val totalXp: Double get() = xp + lapBonusXp
}

/** A course together with its obstacles (in lap order) and the tiles marks of grace spawn on. */
class CourseLayout(
    val course: RooftopCourse,
    val obstacles: List<RooftopObstacle>,
    val markTiles: List<CoordGrid>,
) {
    /** Bit mask with one bit per obstacle; a lap is complete when every bit has been set. */
    val fullMask: Int = (1 shl obstacles.size) - 1

    val lapXp: Double get() = obstacles.sumOf { it.totalXp }
}
