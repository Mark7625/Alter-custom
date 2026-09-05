package org.rsmod.content.skills.agility.shortcuts

import org.rsmod.content.skills.agility.AgilityAnims
import org.rsmod.map.CoordGrid

/** How a player crosses a shortcut. Paths are given from side A to side B and reversed as needed. */
sealed class ShortcutMove {
    /** Play [seq] for [ticks] cycles and appear on the far side. Rocks, chains, ladders. */
    data class Climb(val seq: String = AgilityAnims.CLIMB, val ticks: Int = 2) : ShortcutMove()

    /** Glide to the far side over [ticks] cycles while [seq] plays. Fences, walls, ledges. */
    data class Jump(val seq: String = AgilityAnims.JUMP_UP, val ticks: Int = 1) : ShortcutMove()

    /** Play [enter], pass through after [ticks] cycles and play [leave]. Tunnels, pipes, cracks. */
    data class Squeeze(
        val enter: String = AgilityAnims.CRACK_ENTER,
        val leave: String = AgilityAnims.CRACK_LEAVE,
        val ticks: Int = 2,
    ) : ShortcutMove()

    /** Balance across [tiles] (the tiles between the two sides) one per tick. Log balances. */
    data class Balance(val tiles: List<CoordGrid>) : ShortcutMove()

    /** Hop from stone to stone across [stones] (the tiles between the two sides). */
    data class Hop(val stones: List<CoordGrid>) : ShortcutMove()

    companion object {
        val PIPE: Squeeze = Squeeze(AgilityAnims.PIPE_SQUEEZE, AgilityAnims.PIPE_UNSQUEEZE, ticks = 3)
    }
}

/**
 * A two-way agility shortcut between [sideA] and [sideB]. The player is taken to whichever side is
 * further from them. A loc may be shared by several shortcuts (the same crack model is reused
 * around the world), in which case the shortcut nearest the player is used.
 */
data class AgilityShortcut(
    val name: String,
    val level: Int,
    val xp: Double,
    val locs: List<String>,
    val sideA: CoordGrid,
    val sideB: CoordGrid,
    val move: ShortcutMove,
) {
    init {
        require(sideA != sideB) { "Shortcut '$name' needs two distinct sides." }
        require(locs.isNotEmpty()) { "Shortcut '$name' needs at least one loc." }
    }

    /** Distance from [coords] to the nearest side, with other levels pushed far away. */
    fun distanceTo(coords: CoordGrid): Int = minOf(coords.distanceTo(sideA), coords.distanceTo(sideB))

    /** True when a player at [coords] should be taken from [sideA] to [sideB]. */
    fun startsFromA(coords: CoordGrid): Boolean =
        coords.distanceTo(sideA) <= coords.distanceTo(sideB)

    private fun CoordGrid.distanceTo(other: CoordGrid): Int =
        chebyshevDistance(other) + LEVEL_PENALTY * kotlin.math.abs(level - other.level)

    private companion object {
        const val LEVEL_PENALTY = 1000
    }
}
