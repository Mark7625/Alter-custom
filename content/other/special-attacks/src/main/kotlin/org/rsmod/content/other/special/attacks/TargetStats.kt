package org.rsmod.content.other.special.attacks

import kotlin.math.max
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.stat.statSub
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player

/**
 * Stat drains that work on either kind of combat target. Players go through the stat api so the
 * client is updated; npcs have their current levels lowered directly, which is what the combat
 * formulas read.
 */
internal object TargetStats {
    const val ATTACK = "stat.attack"
    const val STRENGTH = "stat.strength"
    const val DEFENCE = "stat.defence"
    const val RANGED = "stat.ranged"
    const val MAGIC = "stat.magic"
    const val PRAYER = "stat.prayer"

    fun current(target: PathingEntity, stat: String): Int =
        when (target) {
            is Player -> target.stat(stat)
            is Npc ->
                when (stat) {
                    ATTACK -> target.attackLvl
                    STRENGTH -> target.strengthLvl
                    DEFENCE -> target.defenceLvl
                    RANGED -> target.rangedLvl
                    MAGIC -> target.magicLvl
                    else -> 0
                }
        }

    fun base(target: PathingEntity, stat: String): Int =
        when (target) {
            is Player -> target.statBase(stat)
            is Npc ->
                when (stat) {
                    ATTACK -> target.baseAttackLvl
                    STRENGTH -> target.baseStrengthLvl
                    DEFENCE -> target.baseDefenceLvl
                    RANGED -> target.baseRangedLvl
                    MAGIC -> target.baseMagicLvl
                    else -> 0
                }
        }

    /** Lowers [stat] on [target] by a flat [amount], never below zero. Returns the amount drained. */
    fun drain(target: PathingEntity, stat: String, amount: Int): Int {
        if (amount <= 0) {
            return 0
        }
        val before = current(target, stat)
        val drained = amount.coerceAtMost(before)
        when (target) {
            is Player -> target.statSub(stat, constant = drained, percent = 0)
            is Npc ->
                when (stat) {
                    ATTACK -> target.attackLvl = max(0, target.attackLvl - drained)
                    STRENGTH -> target.strengthLvl = max(0, target.strengthLvl - drained)
                    DEFENCE -> target.defenceLvl = max(0, target.defenceLvl - drained)
                    RANGED -> target.rangedLvl = max(0, target.rangedLvl - drained)
                    MAGIC -> target.magicLvl = max(0, target.magicLvl - drained)
                }
        }
        return drained
    }

    /** Lowers [stat] on [target] by [percent] of its **current** level. Returns the amount drained. */
    fun drainPercentOfCurrent(target: PathingEntity, stat: String, percent: Int): Int =
        drain(target, stat, (current(target, stat) * percent) / 100)

    /** Lowers [stat] on [target] by [percent] of its **base** level. Returns the amount drained. */
    fun drainPercentOfBase(target: PathingEntity, stat: String, percent: Int): Int =
        drain(target, stat, (base(target, stat) * percent) / 100)
}
