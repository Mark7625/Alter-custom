package org.rsmod.content.other.poison

import kotlin.math.min
import org.rsmod.api.config.refs.done.hitmark_groups
import org.rsmod.api.mechanics.toxins.impl.PlayerPoison
import org.rsmod.api.npc.hit.modifier.NpcHitModifier
import org.rsmod.api.npc.hit.queueHit
import org.rsmod.game.entity.Npc
import org.rsmod.game.hit.HitType

/**
 * Poison on npcs, mirroring the player mechanic in `api/mechanics/toxins`: a severity that falls
 * by one every [TICK_INTERVAL] cycles, dealing [PlayerPoison.damageForSeverity] each time until it
 * runs out. The severity lives in `varn.npc_poison_severity`; the ticking is `timer.npc_poison`.
 */
internal object NpcPoison {
    const val TIMER: String = "timer.npc_poison"
    const val TICK_INTERVAL: Int = PlayerPoison.TICK_INTERVAL

    private const val SEVERITY_VARN = "varn.npc_poison_severity"

    /** Poison hits carry no attacker and must not be modified by combat hooks. */
    private val noModifier = NpcHitModifier {}

    fun isPoisoned(npc: Npc): Boolean = npc.vars[SEVERITY_VARN] > 0

    /**
     * Poisons [npc] with a poison whose first hit deals [initialDamage]. A stronger poison already
     * running is left alone, as it is for players; a weaker one is replaced.
     */
    fun tryPoison(npc: Npc, initialDamage: Int): Boolean {
        if (initialDamage <= 0 || npc.hitpoints <= 0) {
            return false
        }
        val severity = PlayerPoison.severityForInitialDamage(initialDamage)
        if (npc.vars[SEVERITY_VARN] >= severity) {
            return false
        }
        queuePoisonHit(npc, initialDamage)
        npc.vars[SEVERITY_VARN] = severity - 1
        npc.timer(TIMER, TICK_INTERVAL)
        return true
    }

    fun onTimerTick(npc: Npc) {
        var severity = npc.vars[SEVERITY_VARN]
        if (severity <= 0 || npc.hitpoints <= 0) {
            clear(npc)
            return
        }
        queuePoisonHit(npc, PlayerPoison.damageForSeverity(severity))
        severity--
        if (severity <= 0) {
            clear(npc)
            return
        }
        npc.vars[SEVERITY_VARN] = severity
        npc.timer(TIMER, TICK_INTERVAL)
    }

    fun clear(npc: Npc) {
        npc.vars[SEVERITY_VARN] = 0
        npc.clearTimer(TIMER)
    }

    private fun queuePoisonHit(npc: Npc, damage: Int) {
        val capped = min(damage, npc.hitpoints)
        if (capped <= 0) {
            return
        }
        npc.queueHit(
            delay = 1,
            type = HitType.Typeless,
            damage = capped,
            modifier = noModifier,
            hitmark = hitmark_groups.poison_damage,
        )
    }
}
