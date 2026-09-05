package org.rsmod.content.skills.agility

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.BasType
import kotlin.math.sign
import org.rsmod.api.config.constants
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.game.hit.HitType
import org.rsmod.map.CoordGrid

/** Sequence gamevals shared by the rooftop courses and the agility shortcuts. */
object AgilityAnims {
    /** Climbing loop used for trees and rock faces. */
    const val CLIMB = "seq.human_climbing"

    /** Climb up a building wall; used for the rooftop course rough walls and beams. */
    const val WALL_CLIMB = "seq.human_climbing"
    const val CLIMB_LADDER = "seq.human_reachforladder"
    const val JUMP_UP = "seq.agility_shortcut_wall_jump2"

    /** Forward leap across a gap between roofs. */
    const val JUMP = "seq.human_longjump"

    /** Drop down from a roof to the ground. */
    const val JUMP_DOWN = "seq.agility_shortcut_wall_jumpdown2"
    const val WALL_CLIMB_OVER = "seq.human_walk_style"
    const val ROPE_SWING = "seq.human_ropeswing_long"
    const val ZIPLINE_GRAB = "seq.zipline_bite"
    const val ZIPLINE_SLIDE = "seq.zipline_slide"
    const val POLE_VAULT = "seq.rooftops_pole_vault"
    const val BALANCE_WALK = "seq.human_walk_logbalance"
    const val BALANCE_READY = "seq.human_walk_logbalance_ready"
    const val BALANCE_STUMBLE = "seq.human_walk_logbalance_stumble"
    const val MONKEYBARS_ON = "seq.human_monkeybars_on"
    const val MONKEYBARS_OFF = "seq.human_monkeybars_off"
    const val MONKEYBARS_WALK = "seq.human_monkeybars_walk"
    const val MONKEYBARS_READY = "seq.human_monkeybars_ready"
    const val HANDHOLDS = "seq.agilityarena_handholds_middle"
    const val PIPE_SQUEEZE = "seq.human_pipesqueeze"
    const val PIPE_UNSQUEEZE = "seq.human_pipeunsqueeze"
    const val CRACK_ENTER = "seq.agility_shortcut_crack_enter"
    const val CRACK_LEAVE = "seq.agility_shortcut_crack_leave"
    const val STEPPING_STONE = "seq.human_steppingstonejump"
}

/**
 * Base animation set applied while a player is moved along a balance-type obstacle (tightropes,
 * monkey bars, hand holds). Every walk direction maps to the same [walk] sequence so the client
 * shows the balancing animation no matter which way the obstacle runs.
 */
enum class BalanceStyle(
    val ready: String,
    val walk: String,
    val mount: String? = null,
    val dismount: String? = null,
) {
    Tightrope(AgilityAnims.BALANCE_READY, AgilityAnims.BALANCE_WALK),
    Monkeybars(
        AgilityAnims.MONKEYBARS_READY,
        AgilityAnims.MONKEYBARS_WALK,
        AgilityAnims.MONKEYBARS_ON,
        AgilityAnims.MONKEYBARS_OFF,
    ),
    Handholds(AgilityAnims.HANDHOLDS, AgilityAnims.HANDHOLDS),
}

/** Client cycles (20ms) in one server tick; `exactmove` delays are expressed in client cycles. */
internal const val CLIENT_CYCLES_PER_TICK = 30

/**
 * Chance (0-100) to pass a failable obstacle. It rises linearly from [baseChance] at the course's
 * [requiredLevel] to 100 at [noFailLevel], the level at which the obstacle can no longer be failed.
 */
fun successChance(level: Int, requiredLevel: Int, noFailLevel: Int, baseChance: Int = 60): Int {
    if (level >= noFailLevel) {
        return 100
    }
    val span = (noFailLevel - requiredLevel).coerceAtLeast(1)
    val progress = (level - requiredLevel).coerceIn(0, span)
    return baseChance + (100 - baseChance) * progress / span
}

/** The `em_face_*` direction a player should face when moving from [from] towards [to]. */
fun emFaceTowards(from: CoordGrid, to: CoordGrid): Int {
    val dx = (to.x - from.x).sign
    val dz = (to.z - from.z).sign
    return when {
        dx == 0 && dz > 0 -> constants.em_face_north
        dx == 0 && dz < 0 -> constants.em_face_south
        dx > 0 && dz == 0 -> constants.em_face_east
        dx < 0 && dz == 0 -> constants.em_face_west
        dx > 0 && dz > 0 -> constants.em_face_northeast
        dx < 0 && dz > 0 -> constants.em_face_northwest
        dx > 0 && dz < 0 -> constants.em_face_southeast
        dx < 0 && dz < 0 -> constants.em_face_southwest
        else -> constants.em_face_south
    }
}

/**
 * Every tile from [from] (exclusive) to [to] (inclusive), moving diagonally while both axes still
 * differ and then straight along the remaining axis. Used to spell out balance paths tersely.
 */
fun line(from: CoordGrid, to: CoordGrid): List<CoordGrid> {
    require(from.level == to.level) { "Balance paths cannot change level: from=$from, to=$to" }
    val tiles = mutableListOf<CoordGrid>()
    var x = from.x
    var z = from.z
    while (x != to.x || z != to.z) {
        x += (to.x - x).sign
        z += (to.z - z).sign
        tiles += CoordGrid(x, z, from.level)
    }
    return tiles
}

/** Number of server ticks [seq] plays for, or [fallback] when the cache holds no duration. */
internal fun seqTicks(seq: String, fallback: Int): Int {
    val type = ServerCacheManager.getAnim(seq.asRSCM(RSCMType.SEQ))
    val ticks = type?.tickDuration ?: 0
    return if (ticks > 0) ticks else fallback
}

/**
 * Puts the player on [start] before an obstacle begins so every forced movement starts from a
 * known tile. Adjacent moves are sent as a walk step; anything further is an instant jump.
 */
internal suspend fun ProtectedAccess.stepOnto(start: CoordGrid) {
    if (coords == start) {
        return
    }
    if (coords.level == start.level && coords.chebyshevDistance(start) <= 2) {
        teleport(start, TeleportType.Exempt)
    } else {
        telejump(start, TeleportType.Exempt)
    }
    delay(1)
}

/** Plays [seq] for [ticks] cycles and then places the player on [dest]. */
internal suspend fun ProtectedAccess.climbTo(dest: CoordGrid, seq: String, ticks: Int) {
    faceSquare(dest)
    anim(seq)
    delay(ticks)
    telejump(dest, TeleportType.Exempt)
}

/**
 * Moves the player from their current tile to [dest] with an `exactmove` lasting [ticks] cycles
 * while [seq] plays. The server position is updated immediately, so nothing can interrupt the
 * landing. When [dest] is on another level the visual glide runs on the starting level and the
 * level change is applied on landing, because the client cannot interpolate across levels.
 */
internal suspend fun ProtectedAccess.leapTo(dest: CoordGrid, seq: String, ticks: Int) {
    val start = coords
    val glideEnd = if (dest.level == start.level) dest else CoordGrid(dest.x, dest.z, start.level)
    anim(seq)
    exactMove(
        start = start,
        end = glideEnd,
        delay1 = 0,
        delay2 = ticks * CLIENT_CYCLES_PER_TICK,
        dir = emFaceTowards(start, dest),
        teleportType = TeleportType.Exempt,
    )
    delay(ticks)
    if (glideEnd != dest) {
        telejump(dest, TeleportType.Exempt)
    }
}

/** Grabs a zip line and slides down it to [dest]. */
internal suspend fun ProtectedAccess.zipTo(dest: CoordGrid, ticks: Int) {
    faceSquare(dest)
    anim(AgilityAnims.ZIPLINE_GRAB)
    delay(1)
    leapTo(dest, AgilityAnims.ZIPLINE_SLIDE, ticks)
}

internal fun ProtectedAccess.setBalanceStyle(style: BalanceStyle) {
    val ready = style.ready.asRSCM(RSCMType.SEQ)
    val walk = style.walk.asRSCM(RSCMType.SEQ)
    player.bas =
        BasType(
            id = -1,
            readyAnim = ready,
            turnOnSpot = ready,
            walkForward = walk,
            walkBack = walk,
            walkLeft = walk,
            walkRight = walk,
            running = walk,
        )
    rebuildAppearance()
}

internal fun ProtectedAccess.clearBalanceStyle() {
    if (player.bas != null) {
        player.bas = null
        rebuildAppearance()
    }
}

/**
 * Walks the player along [path] one tile per tick. Each step is a teleport move, which the client
 * renders as a walk step but which bypasses the route finder entirely, so the player can never be
 * pushed off the obstacle by collision or by their own clicks. Returns `false` when the walk was
 * cut short at index [stopAt] (the player is left standing on that tile for a fall to follow).
 *
 * The tiles of a balance obstacle are unwalkable, so a player left on one of them could not move
 * again. If the coroutine is torn down mid-walk for any reason (logout, a competing action, an
 * error) the player is therefore placed on the last tile of [path] before the walk unwinds.
 */
internal suspend fun ProtectedAccess.balanceAlong(
    path: List<CoordGrid>,
    style: BalanceStyle,
    stopAt: Int = -1,
): Boolean {
    setBalanceStyle(style)
    var finished = false
    try {
        style.mount?.let {
            anim(it)
            delay(1)
        }
        for ((index, tile) in path.withIndex()) {
            if (index == stopAt) {
                finished = true
                return false
            }
            teleport(tile, TeleportType.Exempt)
            delay(1)
        }
        style.dismount?.let { anim(it) }
        finished = true
        return true
    } finally {
        clearBalanceStyle()
        if (!finished && player.coords != path.last()) {
            logger.warn {
                "Balance walk interrupted at ${player.coords}; placing $player on ${path.last()}."
            }
            telejump(path.last(), TeleportType.Exempt)
        }
    }
}

private val logger = InlineLogger()

/**
 * Fails an obstacle: plays [seq], drops the player on [landing] and deals between [minDamage] and
 * [maxDamage] damage. Callers reset any course progress themselves.
 */
internal suspend fun ProtectedAccess.fallTo(
    landing: CoordGrid,
    seq: String,
    minDamage: Int,
    maxDamage: Int,
) {
    anim(seq)
    delay(1)
    telejump(landing, TeleportType.Exempt)
    val damage = random.of(minDamage, maxDamage)
    queueHit(delay = 1, type = HitType.Typeless, damage = damage)
    mes("You lose your footing and fall to the ground below.")
    delay(1)
}
