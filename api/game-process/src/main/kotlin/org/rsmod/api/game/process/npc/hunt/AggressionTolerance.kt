package org.rsmod.api.game.process.npc.hunt

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey

/**
 * Tracks how long each player has stayed in one map square, so that aggressive monsters can
 * become tolerant of them.
 *
 * In the real game an aggressive monster stops attacking a player who has spent ten minutes in
 * its area, and starts again once the player leaves and returns. The area is taken to be the
 * 64x64 map square the player stands in: the timer restarts whenever that square changes. Only
 * hunts that apply the combat-level rule honour tolerance; monsters that hunt everyone
 * regardless of level (bosses, the always-aggressive kinds) never tire.
 */
@Singleton
public class AggressionTolerance @Inject constructor() {
    private class Stay(var square: MapSquareKey, var since: Int, var lastSeen: Int)

    private val stays = HashMap<PlayerUid, Stay>()
    private var lastPrune = 0

    /** Records where [player] is on [cycle]; call once per cycle for every online player. */
    public fun tick(player: Player, cycle: Int) {
        record(player.uid, player.coords, cycle)
        if (cycle - lastPrune >= PRUNE_INTERVAL) {
            lastPrune = cycle
            stays.values.removeIf { cycle - it.lastSeen > PRUNE_INTERVAL }
        }
    }

    /** Whether monsters that respect tolerance should ignore [player] on [cycle]. */
    public fun isTolerant(player: Player, cycle: Int): Boolean = isTolerant(player.uid, cycle)

    internal fun record(uid: PlayerUid, coords: CoordGrid, cycle: Int) {
        val square = MapSquareKey.from(coords)
        val stay = stays[uid]
        if (stay == null || stay.square != square) {
            stays[uid] = Stay(square, since = cycle, lastSeen = cycle)
            return
        }
        stay.lastSeen = cycle
    }

    internal fun isTolerant(uid: PlayerUid, cycle: Int): Boolean {
        val stay = stays[uid] ?: return false
        return cycle - stay.since >= TOLERANCE_CYCLES
    }

    public companion object {
        /** Ten minutes of game cycles. */
        public const val TOLERANCE_CYCLES: Int = 1000

        private const val PRUNE_INTERVAL = 500
    }
}
