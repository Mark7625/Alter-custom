package org.rsmod.content.bosses.vorkath

import kotlin.math.max

internal object VorkathRules {
    fun isCraterWall(localX: Int, localZ: Int): Boolean =
        localZ in VORKATH_WALL_APPROACH_MIN_LOCAL_Z..VORKATH_WALL_APPROACH_MAX_LOCAL_Z &&
            localX in VORKATH_WALL_MIN_LOCAL_X..VORKATH_WALL_MAX_LOCAL_X

    fun isPublicCraterApproach(worldX: Int, worldZ: Int, level: Int): Boolean =
        level == 0 &&
            (worldX ushr 6) == VORKATH_SOURCE_REGION_X &&
            (worldZ ushr 6) == VORKATH_SOURCE_REGION_Z &&
            isCraterWall(localX = worldX and 0x3F, localZ = worldZ and 0x3F)

    fun firstSpecial(roll: Int): VorkathSpecial =
        when (roll and 1) {
            0 -> VorkathSpecial.ACID
            else -> VorkathSpecial.ZOMBIFIED_SPAWN
        }

    fun nextSpecial(current: VorkathSpecial): VorkathSpecial =
        when (current) {
            VorkathSpecial.ACID -> VorkathSpecial.ZOMBIFIED_SPAWN
            VorkathSpecial.ZOMBIFIED_SPAWN -> VorkathSpecial.ACID
        }

    /**
     * Wiki/Mod Ash (20 February 2018): magic:ranged relative weights are 3:4. The complete
     * melee/dragonfire distribution is not established by the supplied captures: their positive
     * unit weights are retained as inferred policy, never a melee-only override.
     */
    fun standardWeights(adjacent: Boolean): Map<VorkathStandardAttack, Int> = buildMap {
        if (adjacent) put(VorkathStandardAttack.MELEE, 1)
        put(VorkathStandardAttack.RANGED, 4)
        put(VorkathStandardAttack.MAGIC, 3)
        put(VorkathStandardAttack.DRAGONFIRE, 1)
        put(VorkathStandardAttack.VENOM_DRAGONFIRE, 1)
        put(VorkathStandardAttack.PRAYER_DRAGONFIRE, 1)
        put(VorkathStandardAttack.FIREBALL, 1)
    }

    fun fireballMaximum(distanceFromTarget: Int): Int = fireballDamage(121, distanceFromTarget)

    /** Wiki-derived: halve the same sampled bomb hit on adjacent tiles; two tiles is safe. */
    fun fireballDamage(rawDamage: Int, distanceFromTarget: Int): Int =
        when {
            distanceFromTarget <= 0 -> rawDamage.coerceAtLeast(0)
            distanceFromTarget == 1 -> rawDamage.coerceAtLeast(0) / 2
            else -> 0
        }

    fun dragonfireMaximum(baseMaximum: Int, attack: VorkathStandardAttack): Int =
        when (attack) {
            VorkathStandardAttack.VENOM_DRAGONFIRE,
            VorkathStandardAttack.PRAYER_DRAGONFIRE -> (baseMaximum - 5).coerceAtLeast(0)
            else -> baseMaximum.coerceAtLeast(0)
        }

    fun zombifiedSpawnMaximum(remainingHitpoints: Int): Int =
        zombifiedSpawnDamage(remainingHitpoints)

    /**
     * Wiki-derived fixed health-scaled explosion, not a random roll up to this value. Full health
     * is 60; integer truncation for partial health remains inferred.
     */
    fun zombifiedSpawnDamage(remainingHitpoints: Int): Int =
        ((remainingHitpoints.coerceIn(0, 38) * 60) / 38).coerceIn(0, 60)

    fun acidDamage(rawDamage: Int): Int = max(0, rawDamage) / 2

    fun formatTicks(ticks: Int): String {
        val tenths = ticks.coerceAtLeast(0) * 6
        val minutes = tenths / 600
        val seconds = (tenths / 10) % 60
        val decimal = tenths % 10
        return if (minutes > 0) "%d:%02d.%d".format(minutes, seconds, decimal)
        else "%d.%d seconds".format(seconds, decimal)
    }

    fun isGuaranteedHeadKill(killcount: Int): Boolean = killcount == 50

    fun storageFeeAffordable(coins: Int): Boolean = coins >= VORKATH_DEATH_FEE
}
