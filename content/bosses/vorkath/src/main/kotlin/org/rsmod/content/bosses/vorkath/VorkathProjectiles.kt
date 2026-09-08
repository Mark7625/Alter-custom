package org.rsmod.content.bosses.vorkath

import kotlin.math.hypot
import kotlin.math.roundToInt
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.proj.ProjAnim
import org.rsmod.map.CoordGrid

/**
 * Native revision-240 spot effects and raw packet geometry from the five RSProx captures.
 * End time is in client cycles. Gameplay impacts use floor(end / 30), as witnessed in capture 3279.
 */
internal data class VorkathProjectile(
    val spot: Int,
    val start: Int,
    val end: Int,
    val angle: Int,
    val startHeight: Int,
    val endHeight: Int,
    val progress: Int = 128,
    val distanceAdjusted: Boolean = false,
    val tileTargeted: Boolean = false,
) {
    val impactTicks: Int get() = end / 30

    fun build(
        source: Npc,
        tile: CoordGrid,
        target: Player? = null,
        facingTile: CoordGrid = target?.coords ?: tile,
    ): ProjAnim = build(source.coords, source.size, tile, target?.slotId, facingTile)

    fun build(
        source: CoordGrid,
        sourceSize: Int,
        tile: CoordGrid,
        targetSlot: Int? = null,
        facingTile: CoordGrid = tile,
    ): ProjAnim = fromMouth(VorkathProjectiles.mouth(source, sourceSize, facingTile), tile, targetSlot)

    /** A fixed launch tile also permits direct replay of the captured mouth coordinates. */
    fun fromMouth(mouth: CoordGrid, tile: CoordGrid, targetSlot: Int? = null): ProjAnim =
        ProjAnim(
            spotanim = spot,
            startHeight = startHeight,
            endHeight = endHeight,
            startTime = start,
            endTime = if (distanceAdjusted) 70 + 5 * mouth.chebyshevDistance(tile) else end,
            angle = angle,
            progress = progress,
            // Inferred: keep the captured launch tile fixed instead of reattaching it to the NPC.
            // The decoded source block does not expose the raw source attachment index.
            sourceIndex = 0,
            targetIndex = if (tileTargeted) 0 else targetSlot?.let { -(it + 1) } ?: 0,
            startCoord = mouth,
            endCoord = tile,
        )
}

internal object VorkathProjectiles {
    // The stored end values are the request's capture witnesses. Flight uses the observed
    // distance formula (2,547 matching packets), including ice; they are not fixed style speeds.
    val RANGED = VorkathProjectile(1477, 30, 95, 14, 142, 124, distanceAdjusted = true)
    val MAGIC = VorkathProjectile(1479, 30, 80, 14, 142, 124, distanceAdjusted = true)
    val DRAGONFIRE = VorkathProjectile(393, 30, 80, 14, 142, 124, distanceAdjusted = true)
    val VENOM = VorkathProjectile(1470, 30, 80, 14, 142, 124, distanceAdjusted = true)
    val PRAYER = VorkathProjectile(1471, 30, 80, 14, 142, 124, distanceAdjusted = true)
    val ICE = VorkathProjectile(395, 30, 80, 14, 142, 124, distanceAdjusted = true)
    val FIREBALL = VorkathProjectile(1481, 0, 120, 46, 340, 38, tileTargeted = true)
    val ACID = VorkathProjectile(1483, 32, 90, 46, 340, 0, tileTargeted = true)
    val RAPID_FIRE = VorkathProjectile(1482, 0, 30, 22, 138, 30, tileTargeted = true)
    val SPAWN = VorkathProjectile(1484, 32, 120, 46, 340, 0, tileTargeted = true)

    /**
     * The captures place the mouth two tiles ahead of the seven-tile boss's centre and turn it
     * with the player, including during acid. This smooth projection's rounding between observed
     * directions is inferred: decoded packets do not expose the NPC's interpolated model facing.
     * [facingTile] is the player position, not an individual pool/spawn landing coordinate.
     */
    fun mouth(source: CoordGrid, sourceSize: Int, facingTile: CoordGrid): CoordGrid {
        val centre = source.translate(sourceSize / 2, sourceSize / 2)
        val dx = (facingTile.x - centre.x).toDouble()
        val dz = (facingTile.z - centre.z).toDouble()
        val distance = hypot(dx, dz)
        if (distance == 0.0) return centre.translate(0, -2)
        return centre.translate((2.0 * dx / distance).roundToInt(), (2.0 * dz / distance).roundToInt())
    }

    fun standard(attack: VorkathStandardAttack): VorkathProjectile? = when (attack) {
        VorkathStandardAttack.MELEE -> null
        VorkathStandardAttack.RANGED -> RANGED
        VorkathStandardAttack.MAGIC -> MAGIC
        VorkathStandardAttack.DRAGONFIRE -> DRAGONFIRE
        VorkathStandardAttack.VENOM_DRAGONFIRE -> VENOM
        VorkathStandardAttack.PRAYER_DRAGONFIRE -> PRAYER
        VorkathStandardAttack.FIREBALL -> FIREBALL
    }

    fun impact(attack: VorkathStandardAttack): Int? = when (attack) {
        VorkathStandardAttack.RANGED -> 1478
        VorkathStandardAttack.MAGIC -> 1480
        VorkathStandardAttack.DRAGONFIRE -> 1466
        VorkathStandardAttack.VENOM_DRAGONFIRE -> 1472
        VorkathStandardAttack.PRAYER_DRAGONFIRE -> 1473
        else -> null
    }

    val all = listOf(RANGED, MAGIC, DRAGONFIRE, VENOM, PRAYER, ICE, FIREBALL, ACID, RAPID_FIRE, SPAWN)
}
