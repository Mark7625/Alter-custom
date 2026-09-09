package org.rsmod.content.generic.locs.passages

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import kotlin.math.sign
import org.rsmod.api.config.constants
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.map.CoordGrid

/** Client cycles (20ms) in one server tick; `exactmove` delays are expressed in client cycles. */
private const val CLIENT_CYCLES_PER_TICK = 30

/**
 * Glides the player from their tile to [dest] over [ticks] cycles while [seq] plays, then leaves
 * them standing on [dest]. Used for hopping stiles and jumping the Wilderness ditch.
 */
internal suspend fun ProtectedAccess.hopTo(dest: CoordGrid, seq: String, ticks: Int) {
    val start = coords
    anim(seq)
    exactMove(
        start = start,
        end = dest,
        delay1 = 0,
        delay2 = ticks * CLIENT_CYCLES_PER_TICK,
        dir = faceTowards(start, dest),
        teleportType = TeleportType.Exempt,
    )
    delay(ticks)
}

/** Number of server ticks [seq] plays for, or [fallback] when the cache holds no duration. */
internal fun seqTicks(seq: String, fallback: Int): Int {
    val type = ServerCacheManager.getAnim(seq.asRSCM(RSCMType.SEQ))
    val ticks = type?.tickDuration ?: 0
    return if (ticks > 0) ticks else fallback
}

private fun faceTowards(from: CoordGrid, to: CoordGrid): Int {
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
