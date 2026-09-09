package org.rsmod.api.combat

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
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
import org.rsmod.game.entity.Npc
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
        val flight = spawnProjectile(target)

        val successfulHit = accuracy.rollRangedAccuracy(npc, target, random)
        val damage = if (successfulHit) random.of(0..maxHits.getRangedMaxHit(npc, target)) else 0

        finishAttack(
            target,
            delay = flight.serverCycles,
            type = HitType.Ranged,
            damage = damage,
            defendClientDelay = flight.clientCycles,
        )
    }

    private fun StandardNpcAccess.attackMagic(target: Player, attack: CombatAttack.NpcMagic) {
        if (!beginAttack(target)) {
            return
        }
        val spell = npc.spellFx()
        val playedAttackSound = playAttackFx(target)
        if (!playedAttackSound && spell != null) {
            worldRepo.soundArea(npc, spell.castSound, radius = ATTACK_SOUND_RADIUS)
        }
        val flight = spawnProjectile(target)

        // A fixed `maxHit` (e.g. from a scripted attack) takes precedence; otherwise derive it from
        // the npc's magic level and magic strength.
        val maxHit = if (attack.maxHit > 0) attack.maxHit else maxHits.getMagicMaxHit(npc, target)
        val successfulHit = accuracy.rollMagicAccuracy(npc, target, random)
        val damage = if (successfulHit) random.of(0..maxHit) else 0

        if (successfulHit) {
            spell?.playImpact(target, flight)
        } else if (spell != null) {
            playSplash(target, flight)
        }

        finishAttack(
            target,
            delay = flight.serverCycles,
            type = HitType.Magic,
            damage = damage,
            defendClientDelay = flight.clientCycles,
        )
    }

    /**
     * The graphics and sounds of the spell an npc casts, worked out from its `proj_travel` param:
     * a projectile named `spotanim.<spell>_travel` belongs to the spell whose casting sound is
     * `synth.<spell>_cast_and_fire`, whose landing graphic is `spotanim.<spell>_impact` and whose
     * landing sound is `synth.<spell>_hit`. Returns `null` when the npc has no projectile or the
     * names do not exist, so only real spells gain the extra effects.
     */
    private fun Npc.spellFx(): SpellFx? {
        val travel = visType.paramOrNull(params.proj_travel) ?: return null
        val travelName = RSCM.getReverseMapping(RSCMType.SPOTANIM, travel.id)
        if (!travelName.endsWith(TRAVEL_SUFFIX)) {
            return null
        }
        val spell = travelName.removePrefix("spotanim.").removeSuffix(TRAVEL_SUFFIX)
        return spellFxCache.getOrPut(spell) {
            val castSound = "synth.${spell}_cast_and_fire"
            val hitSound = "synth.${spell}_hit"
            val impact = "spotanim.${spell}_impact"
            val known = listOf(castSound, hitSound, impact).all(::gamevalExists)
            if (known) SpellFx(castSound, hitSound, impact) else null
        }
    }

    private fun gamevalExists(name: String): Boolean = runCatching { name.asRSCM() }.isSuccess

    private fun SpellFx.playImpact(target: Player, flight: ProjectileFlight) {
        target.spotanim(impact, delay = flight.clientCycles, height = IMPACT_HEIGHT)
        worldRepo.soundArea(target, hitSound, delay = flight.clientCycles, radius = ATTACK_SOUND_RADIUS)
    }

    private fun playSplash(target: Player, flight: ProjectileFlight) {
        target.spotanim(SPLASH_SPOTANIM, delay = flight.clientCycles, height = IMPACT_HEIGHT)
        worldRepo.soundArea(target, SPLASH_SOUND, delay = flight.clientCycles, radius = ATTACK_SOUND_RADIUS)
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

    /** Plays the npc's attack animation and sound. Returns `true` if it had a sound to play. */
    private fun StandardNpcAccess.playAttackFx(target: Player): Boolean {
        val attackAnim =
            RSCM.getReverseMapping(RSCMType.SEQ, npc.visType.param(params.attack_anim).id)
        anim(attackAnim)
        // Attack sounds are area sounds so that everyone nearby hears the npc strike, not only its
        // target.
        val attackSound = npc.visType.paramOrNull(params.attack_sound) ?: return false
        worldRepo.soundArea(npc, attackSound.id, radius = ATTACK_SOUND_RADIUS)
        return true
    }

    /**
     * Spawns a projectile from the npc to [target], returning the number of cycles until impact.
     *
     * The projectile graphic comes from the npc's `proj_travel` (spotanim) param; its flight is
     * described by the `proj_type` (projanim) param when present, otherwise
     * [DEFAULT_PROJECTILE_TYPE]. When the npc defines no `proj_travel`, no projectile is spawned
     * and the hit lands after [DEFAULT_PROJECTILE_FLIGHT].
     */
    private fun StandardNpcAccess.spawnProjectile(target: Player): ProjectileFlight {
        val travelSpot =
            npc.visType.paramOrNull(params.proj_travel) ?: return DEFAULT_PROJECTILE_FLIGHT
        val projType = npc.visType.paramOrNull(params.proj_type) ?: DEFAULT_PROJECTILE_TYPE
        val proj = ProjAnim.fromNpcToPlayer(npc, target, travelSpot.id, projType)
        worldRepo.projAnim(proj)
        return ProjectileFlight(proj.serverCycles, proj.clientCycles)
    }

    /** How long a projectile takes to land, in server cycles and in client cycles (20ms). */
    private data class ProjectileFlight(val serverCycles: Int, val clientCycles: Int)

    private data class SpellFx(val castSound: String, val hitSound: String, val impact: String)

    private fun StandardNpcAccess.finishAttack(
        target: Player,
        delay: Int,
        type: HitType,
        damage: Int,
        defendClientDelay: Int = 0,
    ) {
        setAttackVars(target)
        target.finishNpcHit(npc, delay, type, damage, hitModifier, defendClientDelay)
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

    private val spellFxCache = HashMap<String, SpellFx?>()

    private companion object {
        private const val MELEE_HIT_DELAY = 1
        private const val ATTACK_SOUND_RADIUS = 10
        private const val CLIENT_CYCLES_PER_SERVER_CYCLE = 30
        private val DEFAULT_PROJECTILE_FLIGHT =
            ProjectileFlight(serverCycles = 2, clientCycles = 2 * CLIENT_CYCLES_PER_SERVER_CYCLE)

        private const val TRAVEL_SUFFIX = "_travel"
        private const val IMPACT_HEIGHT = 124
        private const val SPLASH_SPOTANIM = "spotanim.failedspell_impact"
        private const val SPLASH_SOUND = "synth.spellfail"

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
