package org.rsmod.api.combat

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ProjAnimType
import jakarta.inject.Inject
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.npc.attackRate
import org.rsmod.api.combat.commons.player.finishNpcHit
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.combat.formulas.MaxHitFormulae
import org.rsmod.api.combat.npc.attackingPlayer
import org.rsmod.api.combat.npc.lastAttack
import org.rsmod.api.combat.player.aggressiveNpc
import org.rsmod.api.combat.player.lastCombat
import org.rsmod.api.config.refs.params
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.isInCombat
import org.rsmod.api.player.hit.modifier.PlayerHitModifier
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.proj.ProjAnim

internal class NvPCombat
@Inject
constructor(
    private val accuracy: AccuracyFormulae,
    private val maxHits: MaxHitFormulae,
    private val worldRepo: WorldRepository,
    private val hitModifier: PlayerHitModifier,
) {
    fun attack(access: StandardNpcAccess, target: Player, attack: CombatAttack.NpcAttack) {
        when (attack) {
            is CombatAttack.NpcMelee -> access.attackMelee(target, attack)
            is CombatAttack.NpcRanged -> access.attackRanged(target, attack)
            is CombatAttack.NpcMagic -> access.attackMagic(target, attack)
        }
    }

    private fun StandardNpcAccess.attackMelee(target: Player, attack: CombatAttack.NpcMelee) {
        if (!beginAttack(target)) {
            return
        }
        playAttackFx(target)

        val successfulHit = accuracy.rollMeleeAccuracy(npc, target, attack.type, random)
        val damage =
            if (successfulHit) random.of(0..maxHits.getMeleeMaxHit(npc, target, attack.type)) else 0

        finishAttack(target, delay = MELEE_HIT_DELAY, type = HitType.Melee, damage = damage)
    }

    private fun StandardNpcAccess.attackRanged(target: Player, attack: CombatAttack.NpcRanged) {
        if (!beginAttack(target)) {
            return
        }
        playAttackFx(target)
        val hitDelay = spawnProjectile(target)

        val successfulHit = accuracy.rollRangedAccuracy(npc, target, random)
        val damage = if (successfulHit) random.of(0..maxHits.getRangedMaxHit(npc, target)) else 0

        finishAttack(target, delay = hitDelay, type = HitType.Ranged, damage = damage)
    }

    private fun StandardNpcAccess.attackMagic(target: Player, attack: CombatAttack.NpcMagic) {
        if (!beginAttack(target)) {
            return
        }
        playAttackFx(target)
        val hitDelay = spawnProjectile(target)

        // A fixed `maxHit` (e.g. from a scripted attack) takes precedence; otherwise derive it from
        // the npc's magic level and magic strength.
        val maxHit = if (attack.maxHit > 0) attack.maxHit else maxHits.getMagicMaxHit(npc, target)
        val successfulHit = accuracy.rollMagicAccuracy(npc, target, random)
        val damage = if (successfulHit) random.of(0..maxHit) else 0

        finishAttack(target, delay = hitDelay, type = HitType.Magic, damage = damage)
    }

    /**
     * Shared attack gating and attack-rate arming. Returns `false` when the npc cannot attack this
     * cycle (invalid target, still on cooldown, or no longer in combat).
     */
    private fun StandardNpcAccess.beginAttack(target: Player): Boolean {
        if (!canAttack(target)) {
            resetMode()
            return false
        }

        // Note: We do not need to explicitly call `opplayer2`/`applayer2` because npcs will
        // automatically repeat their last interaction until it is canceled (e.g., by changing their
        // `npcmode`).
        if (actionDelay > mapClock) {
            return false
        }

        if (!npc.isInCombat()) {
            resetMode()
            return false
        }

        actionDelay = mapClock + npc.attackRate()
        return true
    }

    private fun StandardNpcAccess.playAttackFx(target: Player) {
        val attackAnim =
            RSCM.getReverseMapping(RSCMType.SEQ, npc.visType.param(params.attack_anim).id)
        anim(attackAnim)
        // Attack sounds are area sounds so that everyone nearby hears the npc strike, not only its
        // target.
        val attackSound = npc.visType.paramOrNull(params.attack_sound) ?: return
        worldRepo.soundArea(npc, attackSound.id, radius = ATTACK_SOUND_RADIUS)
    }

    /**
     * Spawns a projectile from the npc to [target], returning the number of cycles until impact.
     *
     * The projectile graphic comes from the npc's `proj_travel` (spotanim) param; its flight is
     * described by the `proj_type` (projanim) param when present, otherwise
     * [DEFAULT_PROJECTILE_TYPE]. When the npc defines no `proj_travel`, no projectile is spawned
     * and the hit lands after [DEFAULT_PROJECTILE_HIT_DELAY] cycles.
     */
    private fun StandardNpcAccess.spawnProjectile(target: Player): Int {
        val travelSpot =
            npc.visType.paramOrNull(params.proj_travel) ?: return DEFAULT_PROJECTILE_HIT_DELAY
        val projType = npc.visType.paramOrNull(params.proj_type) ?: DEFAULT_PROJECTILE_TYPE
        val proj = ProjAnim.fromNpcToPlayer(npc, target, travelSpot.id, projType)
        worldRepo.projAnim(proj)
        return proj.serverCycles
    }

    private fun StandardNpcAccess.finishAttack(
        target: Player,
        delay: Int,
        type: HitType,
        damage: Int,
    ) {
        setAttackVars(target)
        target.finishNpcHit(npc, delay, type, damage, hitModifier)
    }

    private fun canAttack(target: Player): Boolean {
        return target.isValidTarget()
    }

    private fun StandardNpcAccess.setAttackVars(target: Player) {
        npc.lastAttack = mapClock
        npc.attackingPlayer = target.uid
        target.lastCombat = mapClock
        target.aggressiveNpc = npc.uid
    }

    private companion object {
        private const val MELEE_HIT_DELAY = 1
        private const val ATTACK_SOUND_RADIUS = 10
        private const val DEFAULT_PROJECTILE_HIT_DELAY = 2

        /**
         * Standard projectile arc used when an npc provides a `proj_travel` graphic but no
         * `proj_type`.
         */
        private val DEFAULT_PROJECTILE_TYPE =
            ProjAnimType(
                startHeight = 43,
                endHeight = 31,
                delay = 51,
                angle = 10,
                lengthAdjustment = 56,
                progress = 15,
                stepMultiplier = 5,
            )
    }
}
