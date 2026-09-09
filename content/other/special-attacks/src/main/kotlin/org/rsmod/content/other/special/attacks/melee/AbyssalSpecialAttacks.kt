package org.rsmod.content.other.special.attacks.melee

import jakarta.inject.Inject
import kotlin.math.min
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.CombatEffects
import org.rsmod.api.config.Constants
import org.rsmod.api.config.constants
import org.rsmod.api.mechanics.toxins.impl.PlayerPoison
import org.rsmod.api.player.output.UpdateRun
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.specials.SpecialAttackManager
import org.rsmod.api.specials.SpecialAttackMap
import org.rsmod.api.specials.SpecialAttackRepository
import org.rsmod.api.specials.combat.MeleeSpecialAttack
import org.rsmod.content.other.special.attacks.TargetStats
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player

/**
 * The abyssal weapon specials: Energy Drain (whip), Binding Tentacle (tentacle), Abyssal Puncture
 * (dagger) and Penance (bludgeon).
 */
class AbyssalSpecialAttacks @Inject constructor(private val worldRepo: WorldRepository) :
    SpecialAttackMap {
    override fun SpecialAttackRepository.register(manager: SpecialAttackManager) {
        val energyDrain = EnergyDrain(manager, worldRepo)
        registerMelee("obj.abyssal_whip", energyDrain)
        registerMelee("obj.abyssal_whip_lava", energyDrain)
        registerMelee("obj.abyssal_whip_ice", energyDrain)

        registerMelee("obj.abyssal_tentacle", BindingTentacle(manager, worldRepo))

        val abyssalPuncture = AbyssalPuncture(manager)
        registerMelee("obj.abyssal_dagger", abyssalPuncture)
        registerMelee("obj.abyssal_dagger_p", abyssalPuncture)
        registerMelee("obj.abyssal_dagger_p+", abyssalPuncture)
        registerMelee("obj.abyssal_dagger_p++", abyssalPuncture)

        registerMelee("obj.abyssal_bludgeon", Penance(manager))
    }

    /**
     * Abyssal whip: a hit at +25% accuracy. Against a player it also transfers a tenth of their
     * run energy to the attacker.
     */
    private class EnergyDrain(
        private val manager: SpecialAttackManager,
        private val worldRepo: WorldRepository,
    ) : MeleeSpecialAttack {
        override suspend fun ProtectedAccess.attack(target: Npc, attack: CombatAttack.Melee): Boolean {
            lash(target, attack)
            return true
        }

        override suspend fun ProtectedAccess.attack(target: Player, attack: CombatAttack.Melee): Boolean {
            val damage = lash(target, attack)
            if (damage > 0) {
                val stolen = target.runEnergy / 10
                target.runEnergy -= stolen
                UpdateRun.energy(target, target.runEnergy)
                player.runEnergy = min(Constants.run_max_energy, player.runEnergy + stolen)
                UpdateRun.energy(player, player.runEnergy)
            }
            return true
        }

        private fun ProtectedAccess.lash(target: PathingEntity, attack: CombatAttack.Melee): Int {
            anim("seq.slayer_whip_sp_attack")
            target.spotanim("spotanim.sp_attack_abyssal_whip", height = 96)
            worldRepo.soundArea(player, WHIP_SOUND, radius = SOUND_RADIUS)
            val damage = manager.rollMeleeDamage(this, target, attack, 1.25, 1.0)
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueMeleeHit(this, target, damage)
            manager.continueCombat(this, target)
            return damage
        }
    }

    /**
     * Abyssal tentacle: a hit at +25% accuracy that freezes a player target for five seconds and
     * has an even chance of poisoning them whether or not it lands.
     */
    private class BindingTentacle(
        private val manager: SpecialAttackManager,
        private val worldRepo: WorldRepository,
    ) : MeleeSpecialAttack {
        override suspend fun ProtectedAccess.attack(target: Npc, attack: CombatAttack.Melee): Boolean {
            lash(target, attack)
            return true
        }

        override suspend fun ProtectedAccess.attack(target: Player, attack: CombatAttack.Melee): Boolean {
            val damage = lash(target, attack)
            if (damage > 0) {
                CombatEffects.freeze(target, TENTACLE_BIND_TICKS)
            }
            if (random.of(2) == 0) {
                PlayerPoison.tryPoison(target, initialDamage = TENTACLE_POISON_DAMAGE)
            }
            return true
        }

        private fun ProtectedAccess.lash(target: PathingEntity, attack: CombatAttack.Melee): Int {
            anim("seq.slayer_whip_sp_attack")
            target.spotanim("spotanim.sp_attack_abyssal_whip", height = 96)
            worldRepo.soundArea(player, WHIP_SOUND, radius = SOUND_RADIUS)
            val damage = manager.rollMeleeDamage(this, target, attack, 1.25, 1.0)
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueMeleeHit(this, target, damage)
            manager.continueCombat(this, target)
            return damage
        }
    }

    /** Abyssal dagger: two hits at +25% accuracy and -15% damage that both land or both miss. */
    private class AbyssalPuncture(private val manager: SpecialAttackManager) : MeleeSpecialAttack {
        override suspend fun ProtectedAccess.attack(target: Npc, attack: CombatAttack.Melee) =
            puncture(target, attack)

        override suspend fun ProtectedAccess.attack(target: Player, attack: CombatAttack.Melee) =
            puncture(target, attack)

        private fun ProtectedAccess.puncture(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ): Boolean {
            anim("seq.abyssal_dagger_special")
            spotanim(
                "spotanim.abyssal_dagger_special_spotanim",
                height = 96,
                slot = constants.spotanim_slot_combat,
            )
            val landed =
                manager.rollMeleeAccuracy(this, target, attack.type, attack.style, attack.type, 1.25)
            val first =
                if (landed) manager.rollMeleeMaxHit(this, target, attack.type, attack.style, 0.85) else 0
            val second =
                if (landed) manager.rollMeleeMaxHit(this, target, attack.type, attack.style, 0.85) else 0
            manager.giveCombatXp(this, target, attack, first + second)
            manager.queueMeleeHit(this, target, first)
            manager.queueMeleeHit(this, target, second)
            manager.continueCombat(this, target)
            return true
        }
    }

    /** Abyssal bludgeon: +0.5% damage for every prayer point the player is missing. */
    private class Penance(private val manager: SpecialAttackManager) : MeleeSpecialAttack {
        override suspend fun ProtectedAccess.attack(target: Npc, attack: CombatAttack.Melee) =
            penance(target, attack)

        override suspend fun ProtectedAccess.attack(target: Player, attack: CombatAttack.Melee) =
            penance(target, attack)

        private fun ProtectedAccess.penance(target: PathingEntity, attack: CombatAttack.Melee): Boolean {
            anim("seq.abyssal_bludgeon_special_attack")
            spotanim(
                "spotanim.abyssal_miasma_spotanim_bludgeon",
                height = 96,
                slot = constants.spotanim_slot_combat,
            )
            val missing = player.statBase(TargetStats.PRAYER) - player.stat(TargetStats.PRAYER)
            val multiplier = 1.0 + (missing.coerceAtLeast(0) * PENANCE_PER_POINT)
            val damage = manager.rollMeleeDamage(this, target, attack, 1.0, multiplier)
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueMeleeHit(this, target, damage)
            manager.continueCombat(this, target)
            return true
        }
    }

    private companion object {
        private const val SOUND_RADIUS = 10
        private const val WHIP_SOUND = 2713
        private const val TENTACLE_BIND_TICKS = 8
        private const val TENTACLE_POISON_DAMAGE = 4
        private const val PENANCE_PER_POINT = 0.005
    }
}
