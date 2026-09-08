package org.rsmod.api.bosses.runtime

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.bosses.spec.BossSpec
import org.rsmod.api.bosses.validation.SpecValidator
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.api.script.onAiApPlayer2
import org.rsmod.api.script.onAiOpPlayer2
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onModifyNpcHit
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.plugin.scripts.ScriptContext

object BossCombat {

    fun register(
        ctx: ScriptContext,
        spec: BossSpec,
        deps: BossDeps,
        onLethal: ((Npc) -> Unit)? = null,
        onModifyHit: (NpcHitEvents.Modify.() -> Unit)? = null,
    ) {
        val errors = SpecValidator.validate(spec)
        if (errors.isNotEmpty()) {
            val msg = errors.joinToString("\n") { "  - ${it.message}" }
            error("Boss spec validation failed for '${spec.npcTypes.joinToString()}':\n$msg")
        }

        val npcTypes =
            spec.npcTypes.map { name ->
                val npcId = name.asRSCM(RSCMType.NPC)
                ServerCacheManager.getNpc(npcId) ?: error("Boss NPC type not found: $name")
            }

        for (npcType in npcTypes) {
            deps.encounterRegistry.register(npcType.id, spec)
        }

        with(ctx) {
            for (npcType in npcTypes) {
                onAiOpPlayer2(npcType) { runCombatTick(it.target, spec, deps) }
                onAiApPlayer2(npcType) { runCombatTick(it.target, spec, deps) }
                onModifyNpcHit(npcType) {
                    val encounter = deps.encounterRegistry.of(npc)
                    hit.damage =
                        if (encounter.invulnerable) 0
                        else (hit.damage * encounter.damageScale).toInt()
                    onModifyHit?.invoke(this)
                    if (
                        onLethal != null &&
                            !encounter.lethalHandled &&
                            npc.hitpoints - hit.damage <= 0
                    ) {
                        encounter.lethalHandled = true
                        onLethal(npc)
                    }
                }
            }

            val bossIds = npcTypes.map { it.id }.toSet()
            // Ensures phases and other states are reset
            onEvent<NpcStateEvents.Respawn> {
                if (npc.type.id in bossIds) resetBoss(npc, deps)
            }
            onEvent<NpcStateEvents.Delete> {
                if (npc.type.id in bossIds) deps.encounterRegistry.remove(npc)
            }
        }
    }

    /**
     * Clears the boss's encounter and any scripted-control overrides left on the npc from the previous
     * fight, so a respawned boss starts clean in its first phase. The fresh [BossEncounter] is created
     * lazily on the next combat tick.
     */
    private fun resetBoss(npc: Npc, deps: BossDeps) {
        deps.encounterRegistry.remove(npc)
        npc.movementLocked = false
        npc.apRangeOverride = null
        npc.apRequiresLineOfSight = true
        npc.moveRestrict = npc.type.moveRestrict
        npc.ignoreCombatInteractions = false
        npc.clearFacingLock()
        npc.clearIdleAnim()
    }

    private suspend fun StandardNpcAccess.runCombatTick(
        target: Player,
        spec: BossSpec,
        deps: BossDeps,
    ) {
        val encounter = deps.encounterRegistry.of(npc)
        if (encounter.currentPhase == null) return
        val tick = deps.mapClock.cycle
        if (encounter.phaseEnteredTick < 0) {
            encounter.phaseEnteredTick = tick
        }

        checkAutoTransitions(this, target, encounter, tick, spec, deps)
        checkTriggers(this, target, spec, deps, encounter)

        val ticksSinceLastAttack = tick - encounter.lastAbilityTick
        val attackRate =
            encounter.attackRateOverride
                ?: encounter.currentPhase?.attackRate
                ?: spec.stats.attackRate
        if (ticksSinceLastAttack < attackRate) return

        val phase = encounter.currentPhase ?: return
        val abilityName = encounter.selectAbility(phase.selector, tick, target) ?: return
        val effect = spec.abilities[abilityName] ?: return

        encounter.lastAbilityTick = tick

        val interpreter = EffectInterpreter(npc, target, spec, encounter, deps)
        interpreter.run(this, effect)
    }

    private suspend fun checkTriggers(
        access: StandardNpcAccess,
        target: Player,
        spec: BossSpec,
        deps: BossDeps,
        encounter: BossEncounter,
    ) {
        if (spec.triggers.isEmpty()) return
        for ((index, trigger) in spec.triggers.withIndex()) {
            if (index in encounter.firedTriggers) continue
            if (encounter.evaluate(trigger.condition, target)) {
                encounter.firedTriggers += index
                val interpreter = EffectInterpreter(encounter.npc, target, spec, encounter, deps)
                interpreter.run(access, trigger.effect)
            }
        }
    }

    private suspend fun checkAutoTransitions(
        access: StandardNpcAccess,
        target: Player,
        encounter: BossEncounter,
        tick: Int,
        spec: BossSpec,
        deps: BossDeps,
    ) {
        for ((name, phase) in spec.phases) {
            if (name == encounter.currentPhaseName) continue
            val entryHp = phase.entryHp ?: continue
            // Ensures that phases are only entered once
            if (name in encounter.firedPhaseEntries) continue
            val hpFraction =
                encounter.npc.hitpoints.toDouble() / encounter.npc.baseHitpointsLvl.coerceAtLeast(1)
            if (hpFraction <= entryHp) {
                encounter.firedPhaseEntries += name
                encounter.transitionTo(name, tick)
                phase.entry?.let { runEntry(access, target, it, spec, encounter, deps) }
                return
            }
        }

        val phase = spec.phases[encounter.currentPhaseName] ?: return
        val exitAfter = phase.exitAfter ?: return
        val nextPhase = phase.nextPhase ?: return
        if (tick - encounter.phaseEnteredTick >= exitAfter) {
            val next = spec.phases[nextPhase] ?: return
            encounter.transitionTo(nextPhase, tick)
            next.entry?.let { runEntry(access, target, it, spec, encounter, deps) }
        }
    }

    private suspend fun runEntry(
        access: StandardNpcAccess,
        target: Player,
        abilityName: String,
        spec: BossSpec,
        encounter: BossEncounter,
        deps: BossDeps,
    ) {
        val effect = spec.abilities[abilityName] ?: return
        EffectInterpreter(access.npc, target, spec, encounter, deps).run(access, effect)
    }
}
