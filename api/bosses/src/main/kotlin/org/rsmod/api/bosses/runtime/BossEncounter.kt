package org.rsmod.api.bosses.runtime

import kotlin.random.Random
import org.rsmod.api.bosses.spec.*
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

class BossEncounter(
    val npc: Npc,
    val spec: BossSpec,
) {
    var currentPhaseName: String = spec.phases.keys.firstOrNull() ?: ""

    var phaseEnteredTick: Int = -1
    var lastAbilityTick: Int = 0
    var lastAbilityName: String? = null
    var invulnerable: Boolean = false
    var damageScale: Double = 1.0
    var lethalHandled: Boolean = false

    /**
     * Per-encounter attack-rate override (ticks between ability uses). Takes precedence over
     * [PhaseSpec.attackRate] and [BossStats.attackRate]. Null by default and recreated with the
     * encounter on respawn, so bosses that never set it are unaffected.
     */
    var attackRateOverride: Int? = null

    internal val firedTriggers = mutableSetOf<Int>()
    internal val firedPhaseEntries = mutableSetOf<String>()
    private val cooldowns = mutableMapOf<String, Int>()
    private val forcedTickLastFired = mutableMapOf<String, Int>()
    private var rotationCursor = 0
    private var basicAttackCount = 0
    private var forceAttackThreshold = -1

    init {
        npc.movementLocked = currentPhase?.lockMovement == true
    }

    val currentPhase: PhaseSpec?
        get() = spec.phases[currentPhaseName]

    fun transitionTo(phaseName: String, tick: Int) {
        val from = currentPhaseName
        currentPhaseName = phaseName
        phaseEnteredTick = tick
        rotationCursor = 0
        cooldowns.clear()
        forcedTickLastFired.clear()
        basicAttackCount = 0
        forceAttackThreshold = -1

        val phase = spec.phases[phaseName]

        val idle = phase?.idleAnim
        if (idle != null) npc.setIdleAnim(idle) else npc.clearIdleAnim()

        npc.movementLocked = phase?.lockMovement == true

        npc.clearFacingLock()
    }

    fun selectAbility(selector: Selector, tick: Int, target: Player? = null): String? {
        val phase = currentPhase ?: return null

        for (forced in phase.forceAbilities) {
            if (forced.attackMin != null) continue
            val lastFired = forcedTickLastFired[forced.ability] ?: phaseEnteredTick
            if (tick - lastFired >= forced.period) {
                forcedTickLastFired[forced.ability] = tick
                return forced.ability
            }
        }

        val attackForced = phase.forceAbilities.firstOrNull { it.attackMin != null }
        if (attackForced != null) {
            if (forceAttackThreshold < 0) {
                forceAttackThreshold = randomThreshold(attackForced)
            }
            if (basicAttackCount >= forceAttackThreshold) {
                basicAttackCount = 0
                forceAttackThreshold = randomThreshold(attackForced)
                return attackForced.ability
            }
        }

        val selected = when (selector) {
            is Selector.WeightedRandom -> selectWeightedRandom(selector, tick, target)
            is Selector.Rotation -> selectRotation(selector)
            is Selector.Conditional -> null
        }
        if (selected != null && attackForced != null) {
            basicAttackCount++
        }
        return selected
    }

    private fun randomThreshold(forced: ForcedAbility): Int {
        val min = forced.attackMin ?: return Int.MAX_VALUE
        val max = (forced.attackMax ?: min).coerceAtLeast(min)
        return if (max == min) min else Random.nextInt(min, max + 1)
    }

    private fun selectWeightedRandom(selector: Selector.WeightedRandom, tick: Int, target: Player? = null): String? {
        val available = selector.entries.filter { ref ->
            val onCooldown = cooldowns[ref.ability]?.let { tick - it < ref.cooldown } ?: false
            !onCooldown && evaluate(ref.requires, target)
        }

        if (available.isEmpty()) return null

        val totalWeight = available.sumOf { it.weight }
        if (totalWeight <= 0) return null

        var roll = Random.nextInt(totalWeight)
        for (ref in available) {
            roll -= ref.weight
            if (roll < 0) {
                cooldowns[ref.ability] = tick
                lastAbilityName = ref.ability
                return ref.ability
            }
        }
        return available.last().ability
    }

    private fun selectRotation(selector: Selector.Rotation): String? {
        if (selector.sequence.isEmpty()) return null
        val ability = selector.sequence[rotationCursor % selector.sequence.size]
        rotationCursor++
        return ability
    }

    fun evaluate(condition: Condition, target: Player? = null): Boolean {
        return when (condition) {
            is Condition.Always -> true
            is Condition.OnSpawn -> false
            is Condition.OnDeath -> false
            is Condition.WithinMeleeRange -> {
                target != null && npc.isWithinDistance(target, 1)
            }
            is Condition.HpBelow -> {
                val fraction = npc.hitpoints.toDouble() / npc.baseHitpointsLvl.coerceAtLeast(1)
                fraction < condition.fraction
            }
            is Condition.HpExact -> npc.hitpoints == condition.hp
            is Condition.InPhase -> currentPhaseName == condition.phase
            is Condition.Not -> !evaluate(condition.c, target)
            is Condition.And -> evaluate(condition.a, target) && evaluate(condition.b, target)
            is Condition.Or -> evaluate(condition.a, target) || evaluate(condition.b, target)
            is Condition.EveryNTicks -> false
            is Condition.OnPhaseTick -> false
            is Condition.IncomingHitDamageAtLeast -> false
            is Condition.PlayerEnterRange -> false
            is Condition.TargetPraying -> false
        }
    }
}
