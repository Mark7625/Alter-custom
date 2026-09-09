package org.rsmod.api.combat.commons

import org.rsmod.api.mechanics.toxins.impl.PlayerPoison
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.statSub
import org.rsmod.game.entity.Player

public object CombatEffects {

    public fun poison(target: Player, damage: Int) {
        PlayerPoison.tryPoison(target, initialDamage = damage)
    }

    private const val FREEZE_IMMUNITY_TICKS = 5
    private const val FREEZE_TIMER = "timer.combat_freeze"
    private const val STUN_TIMER = "timer.combat_stun"

    /**
     * Freezes [target] in place for [ticks], after which they are immune to further freezes for
     * a short while. A running freeze is not replaced; a stun ([stun]) that is running does not
     * block a freeze, the two simply overlap.
     */
    public fun freeze(target: Player, ticks: Int) {
        if (FREEZE_TIMER in target.timerMap) return
        if (target.freezeImmune) return
        target.frozen = true
        target.routeDestination.clear()
        target.timer(FREEZE_TIMER, ticks)
        target.mes("You have been frozen!", ChatType.Spam)
    }

    public fun unfreeze(target: Player) {
        // A stun that is still running keeps the player held.
        target.frozen = STUN_TIMER in target.timerMap
        target.freezeImmune = true
        target.clearTimer(FREEZE_TIMER)
        target.timer("timer.combat_freeze_immunity", FREEZE_IMMUNITY_TICKS)
    }

    /**
     * Stuns [target] for [ticks]: they cannot move or attack, as with the dragon spear's Shove.
     * Unlike a freeze, a stun ignores freeze immunity, grants none when it ends, and a new stun
     * restarts the timer.
     */
    public fun stun(target: Player, ticks: Int) {
        target.frozen = true
        target.routeDestination.clear()
        target.actionDelay = maxOf(target.actionDelay, target.currentMapClock + ticks)
        target.timer(STUN_TIMER, ticks)
    }

    public fun unstun(target: Player) {
        target.clearTimer(STUN_TIMER)
        // A freeze that is still running keeps the player held.
        target.frozen = FREEZE_TIMER in target.timerMap
    }

    public fun clearFreezeImmunity(target: Player) {
        target.freezeImmune = false
    }

    public fun statDrain(target: Player, stats: List<String>, amount: Int) {
        for (stat in stats) {
            target.statSub(stat, constant = amount, percent = 0)
        }
    }
}
