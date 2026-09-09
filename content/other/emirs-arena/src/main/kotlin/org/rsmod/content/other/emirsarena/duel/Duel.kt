package org.rsmod.content.other.emirsarena.duel

import org.rsmod.game.entity.Player

enum class DuelStage {
    /** Both players are picking rules on the duel options screen. */
    Options,
    /** Both players are offering items on the stake screen (legacy duels only). */
    Stakes,
    /** Both players are checking the stake summary. */
    StakeConfirm,
    /** Both players are looking at the confirmation screen. */
    Confirm,
    /** The players stand in the arena while the "3, 2, 1, FIGHT!" countdown runs. */
    Countdown,
    Fighting,
    Finished,
}

enum class DuelEnd {
    Death,
    Forfeit,
    Logout,
}

/**
 * One duel between two players, from the moment both accepted the challenge until one of them
 * leaves the arena. The same instance is attached to both players.
 */
class Duel(val challenger: Player, val opponent: Player, val ranked: Boolean) {
    var rules: DuelRules = if (ranked) RANKED_RULES else DuelRules.NONE
    var stage: DuelStage = DuelStage.Options
    var arena: DuelArena? = null
    var winner: Player? = null

    private val accepted = BooleanArray(2)

    val players: List<Player>
        get() = listOf(challenger, opponent)

    fun contains(player: Player): Boolean = player === challenger || player === opponent

    fun other(player: Player): Player = if (player === challenger) opponent else challenger

    /** 0 for the challenger, 1 for the opponent; decides which side of the arena each starts on. */
    fun side(player: Player): Int = if (player === challenger) 0 else 1

    fun hasAccepted(player: Player): Boolean = accepted[side(player)]

    /** Marks [player] as accepting the current screen and returns `true` once both have. */
    fun accept(player: Player): Boolean {
        accepted[side(player)] = true
        return accepted[0] && accepted[1]
    }

    fun resetAcceptance() {
        accepted.fill(false)
    }

    val isActive: Boolean
        get() = stage == DuelStage.Countdown || stage == DuelStage.Fighting

    companion object {
        /** Ranked duels are fought with everything allowed and both inventories visible. */
        val RANKED_RULES: DuelRules = DuelRules.NONE.with(DuelRule.ShowInventories)
    }
}
