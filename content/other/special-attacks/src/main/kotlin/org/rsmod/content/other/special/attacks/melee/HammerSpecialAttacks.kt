package org.rsmod.content.other.special.attacks.melee

import jakarta.inject.Inject
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.config.constants
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.statHeal
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
 * The hammer and mace specials: Smash (dragon warhammer), Pulverize (elder maul), Quick Smash
 * (granite maul), Hammer Blow (granite hammer), Sunder (barrelchest anchor) and Favour of the War
 * God (ancient mace).
 */
class HammerSpecialAttacks @Inject constructor(private val worldRepo: WorldRepository) :
    SpecialAttackMap {
    override fun SpecialAttackRepository.register(manager: SpecialAttackManager) {
        val smash = Smash(manager)
        registerMelee("obj.dragon_warhammer", smash)
        registerMelee("obj.dragon_warhammer_ornament", smash)

        val pulverize = Pulverize(manager)
        registerMelee("obj.elder_maul", pulverize)
        registerMelee("obj.elder_maul_ornament", pulverize)

        val quickSmash = QuickSmash(manager, worldRepo)
        registerMelee("obj.granite_maul", quickSmash)
        registerMelee("obj.granite_maul_pretty", quickSmash)
        registerMelee("obj.granite_maul_plus", quickSmash)
        registerMelee("obj.granite_maul_pretty_plus", quickSmash)

        registerMelee("obj.granite_hammer", HammerBlow(manager))

        val sunder = Sunder(manager)
        registerMelee("obj.brain_anchor", sunder)
        registerMelee("obj.bh_brain_anchor_imbue", sunder)

        registerMelee("obj.ancient_goblin_mace", FavourOfTheWarGod(manager))
    }

    /** Dragon warhammer: +50% damage; a successful hit lowers the target's Defence by 30%. */
    private class Smash(private val manager: SpecialAttackManager) : MeleeSpecialAttack {
        override suspend fun ProtectedAccess.attack(target: Npc, attack: CombatAttack.Melee) =
            smash(target, attack)

        override suspend fun ProtectedAccess.attack(target: Player, attack: CombatAttack.Melee) =
            smash(target, attack)

        private fun ProtectedAccess.smash(target: PathingEntity, attack: CombatAttack.Melee): Boolean {
            anim("seq.dragon_warhammer_sa_player")
            spotanim("spotanim.dragon_warhammer_sa_spotanim", height = 96, slot = COMBAT_SLOT)
            val damage = manager.rollMeleeDamage(this, target, attack, 1.0, 1.5)
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueMeleeHit(this, target, damage)
            if (damage > 0) {
                TargetStats.drainPercentOfCurrent(target, TargetStats.DEFENCE, SMASH_DEFENCE_PERCENT)
            }
            manager.continueCombat(this, target)
            return true
        }
    }

    /** Elder maul: +25% accuracy; a successful hit lowers the target's Defence by 35%. */
    private class Pulverize(private val manager: SpecialAttackManager) : MeleeSpecialAttack {
        override suspend fun ProtectedAccess.attack(target: Npc, attack: CombatAttack.Melee) =
            pulverize(target, attack)

        override suspend fun ProtectedAccess.attack(target: Player, attack: CombatAttack.Melee) =
            pulverize(target, attack)

        private fun ProtectedAccess.pulverize(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ): Boolean {
            anim("seq.human_elder_maul_spec")
            spotanim("spotanim.spotanim_elder_maul_special", height = 96, slot = COMBAT_SLOT)
            val damage = manager.rollMeleeDamage(this, target, attack, 1.25, 1.0)
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueMeleeHit(this, target, damage)
            if (damage > 0) {
                target.spotanim("spotanim.spotanim_elder_maul_special_impact", height = 96)
                TargetStats.drainPercentOfCurrent(
                    target,
                    TargetStats.DEFENCE,
                    PULVERIZE_DEFENCE_PERCENT,
                )
            }
            manager.continueCombat(this, target)
            return true
        }
    }

    /**
     * Granite maul: the maul's special is an instant extra blow. It lands as a second hit straight
     * after the regular swing, so activating it in combat still doubles up the damage.
     */
    private class QuickSmash(
        private val manager: SpecialAttackManager,
        private val worldRepo: WorldRepository,
    ) : MeleeSpecialAttack {
        override suspend fun ProtectedAccess.attack(target: Npc, attack: CombatAttack.Melee) =
            quickSmash(target, attack)

        override suspend fun ProtectedAccess.attack(target: Player, attack: CombatAttack.Melee) =
            quickSmash(target, attack)

        private fun ProtectedAccess.quickSmash(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ): Boolean {
            anim("seq.slayer_granite_maul_special_attack")
            spotanim("spotanim.sp_attack_maul_spotanim", height = 96, slot = COMBAT_SLOT)
            worldRepo.soundArea(player, QUICK_SMASH_SOUND, radius = SOUND_RADIUS)
            val first = manager.rollMeleeDamage(this, target, attack, 1.0, 1.0)
            val second = manager.rollMeleeDamage(this, target, attack, 1.0, 1.0)
            manager.giveCombatXp(this, target, attack, first + second)
            manager.queueMeleeHit(this, target, first)
            manager.queueMeleeHit(this, target, second)
            manager.continueCombat(this, target)
            return true
        }
    }

    /** Granite hammer: +50% accuracy, and five extra damage even when the blow misses. */
    private class HammerBlow(private val manager: SpecialAttackManager) : MeleeSpecialAttack {
        override suspend fun ProtectedAccess.attack(target: Npc, attack: CombatAttack.Melee) =
            hammerBlow(target, attack)

        override suspend fun ProtectedAccess.attack(target: Player, attack: CombatAttack.Melee) =
            hammerBlow(target, attack)

        private fun ProtectedAccess.hammerBlow(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ): Boolean {
            anim("seq.dragon_warhammer_sa_player")
            spotanim("spotanim.granite_hammer_sa_spotanim", height = 96, slot = COMBAT_SLOT)
            val damage = manager.rollMeleeDamage(this, target, attack, 1.5, 1.0) + HAMMER_BLOW_BONUS
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueMeleeHit(this, target, damage)
            manager.continueCombat(this, target)
            return true
        }
    }

    /**
     * Barrelchest anchor: double accuracy and +10% damage; a successful hit drains one of the
     * target's combat stats by a tenth of the damage dealt.
     */
    private class Sunder(private val manager: SpecialAttackManager) : MeleeSpecialAttack {
        override suspend fun ProtectedAccess.attack(target: Npc, attack: CombatAttack.Melee) =
            sunder(target, attack)

        override suspend fun ProtectedAccess.attack(target: Player, attack: CombatAttack.Melee) =
            sunder(target, attack)

        private fun ProtectedAccess.sunder(target: PathingEntity, attack: CombatAttack.Melee): Boolean {
            anim("seq.brain_player_anchor_special_attack")
            spotanim("spotanim.brain_anchor_special_attack_spot", height = 96, slot = COMBAT_SLOT)
            val damage = manager.rollMeleeDamage(this, target, attack, 2.0, 1.1)
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueMeleeHit(this, target, damage)
            if (damage > 0) {
                val stat = random.pick(SUNDER_STATS)
                TargetStats.drain(target, stat, damage / 10)
            }
            manager.continueCombat(this, target)
            return true
        }
    }

    /**
     * Ancient mace: a successful hit drains a player target's Prayer by the damage dealt and
     * restores the same amount to the attacker, even above their base level.
     */
    private class FavourOfTheWarGod(private val manager: SpecialAttackManager) :
        MeleeSpecialAttack {
        override suspend fun ProtectedAccess.attack(target: Npc, attack: CombatAttack.Melee) =
            favour(target, attack)

        override suspend fun ProtectedAccess.attack(target: Player, attack: CombatAttack.Melee) =
            favour(target, attack)

        private fun ProtectedAccess.favour(target: PathingEntity, attack: CombatAttack.Melee): Boolean {
            anim("seq.slice_player_mace_special_attack")
            spotanim(
                "spotanim.slice_player_mace_special_attack_spotanim",
                height = 96,
                slot = COMBAT_SLOT,
            )
            val damage = manager.rollMeleeDamage(this, target, attack, 1.0, 1.0)
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueMeleeHit(this, target, damage)
            if (damage > 0) {
                if (target is Player) {
                    TargetStats.drain(target, TargetStats.PRAYER, damage)
                }
                player.statHeal(TargetStats.PRAYER, constant = damage, percent = 0)
            }
            manager.continueCombat(this, target)
            return true
        }
    }

    private companion object {
        private const val COMBAT_SLOT = constants.spotanim_slot_combat
        private const val SOUND_RADIUS = 10
        private const val QUICK_SMASH_SOUND = 2715
        private const val SMASH_DEFENCE_PERCENT = 30
        private const val PULVERIZE_DEFENCE_PERCENT = 35
        private const val HAMMER_BLOW_BONUS = 5
        private val SUNDER_STATS =
            listOf(
                TargetStats.ATTACK,
                TargetStats.STRENGTH,
                TargetStats.DEFENCE,
                TargetStats.RANGED,
                TargetStats.MAGIC,
            )
    }
}
