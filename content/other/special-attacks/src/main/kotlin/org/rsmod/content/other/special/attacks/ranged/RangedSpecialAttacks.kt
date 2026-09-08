package org.rsmod.content.other.special.attacks.ranged

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import dev.openrune.types.hunt.HuntVis
import jakarta.inject.Inject
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.manager.RangedAmmoManager
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.params
import org.rsmod.api.hunt.NpcSearch
import org.rsmod.api.npc.isValidTarget
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.quiver
import org.rsmod.api.player.righthand
import org.rsmod.api.specials.SpecialAttackManager
import org.rsmod.api.specials.SpecialAttackMap
import org.rsmod.api.specials.SpecialAttackRepository
import org.rsmod.api.specials.combat.RangedSpecialAttack
import org.rsmod.content.other.special.attacks.TargetStats
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.type.getInvObj
import org.rsmod.game.type.getOrNull

/**
 * The bow, crossbow and thrown-weapon specials: Snapshot (magic shortbow), Powershot (magic
 * longbow and comp bow), Soulshot (Seercull), Snipe (Dorgeshuun crossbow), Annihilate (dragon
 * crossbow), Armadyl Eye (Armadyl crossbow), Concentrated Shot (ballistas), Duality (dragon
 * knife) and Momentum Throw (dragon thrownaxe).
 */
class RangedSpecialAttacks
@Inject
constructor(private val ammunition: RangedAmmoManager, private val npcSearch: NpcSearch) :
    SpecialAttackMap {
    override fun SpecialAttackRepository.register(manager: SpecialAttackManager) {
        val snapshot = Snapshot(manager, ammunition)
        registerRanged("obj.magic_shortbow", snapshot)
        registerRanged("obj.magic_shortbow_i", snapshot)

        val powershot = Powershot(manager, ammunition)
        registerRanged("obj.magic_longbow", powershot)
        registerRanged("obj.trail_composite_bow_magic", powershot)

        registerRanged("obj.daganoth_cave_magic_shortbow", Soulshot(manager, ammunition))
        registerRanged("obj.dttd_bone_crossbow", Snipe(manager, ammunition))
        registerRanged("obj.xbows_crossbow_dragon", Annihilate(manager, ammunition, npcSearch))
        registerRanged("obj.acb", ArmadylEye(manager, ammunition))

        val concentratedShot = ConcentratedShot(manager, ammunition)
        registerRanged("obj.light_ballista", concentratedShot)
        registerRanged("obj.heavy_ballista", concentratedShot)
        registerRanged("obj.heavy_ballista_ornament", concentratedShot)

        registerRanged("obj.dragon_knife", Duality(manager, ammunition, DRAGON_KNIFE_TRAVEL))
        registerRanged("obj.dragon_knife_p", Duality(manager, ammunition, DRAGON_KNIFE_TRAVEL_P))
        registerRanged("obj.dragon_knife_p+", Duality(manager, ammunition, DRAGON_KNIFE_TRAVEL_P))
        registerRanged("obj.dragon_knife_p++", Duality(manager, ammunition, DRAGON_KNIFE_TRAVEL_P))

        registerRanged("obj.dragon_thrownaxe", MomentumThrow(manager, ammunition))
    }

    /** Shared plumbing for specials fired from a bow or crossbow with ammunition in the quiver. */
    private abstract class AmmoSpecial(
        protected val manager: SpecialAttackManager,
        protected val ammunition: RangedAmmoManager,
    ) : RangedSpecialAttack {
        override suspend fun ProtectedAccess.attack(target: Npc, attack: CombatAttack.Ranged) =
            fire(target, attack)

        override suspend fun ProtectedAccess.attack(target: Player, attack: CombatAttack.Ranged) =
            fire(target, attack)

        private fun ProtectedAccess.fire(target: PathingEntity, attack: CombatAttack.Ranged): Boolean {
            val weaponType = getInvObj(attack.weapon)
            val quiverType = getOrNull(player.quiver)
            if (!ammunition.attemptAmmoUsage(player, weaponType, quiverType)) {
                manager.stopCombat(this)
                return false
            }
            if (quiverType == null) {
                manager.stopCombat(this)
                mes("You have no ammunition to fire.")
                return false
            }
            val count = player.quiver?.count ?: 0
            if (count < ammoRequired) {
                manager.stopCombat(this)
                mes("You need at least $ammoRequired pieces of ammunition for this special attack.")
                return false
            }
            val travel = travelSpot(quiverType)
            if (travel == null) {
                manager.stopCombat(this)
                mes("You are unable to fire your ammunition.")
                return false
            }
            shoot(target, attack, weaponType, quiverType, travel)
            manager.continueCombat(this, target)
            return true
        }

        /** How many pieces of ammunition the special needs loaded. */
        protected open val ammoRequired: Int = 1

        /** The projectile graphic; defaults to the ammunition's own. */
        protected open fun travelSpot(quiver: ItemServerType): String? =
            quiver.paramOrNull(params.proj_travel)?.let {
                RSCM.getReverseMapping(RSCMType.SPOTANIM, it.id)
            }

        protected abstract fun ProtectedAccess.shoot(
            target: PathingEntity,
            attack: CombatAttack.Ranged,
            weapon: ItemServerType,
            quiver: ItemServerType,
            travel: String,
        )

        /** Fires one piece of ammunition at [target] and applies [damage] when it lands. */
        protected fun ProtectedAccess.launch(
            target: PathingEntity,
            quiver: ItemServerType,
            travel: String,
            projanim: String,
            damage: Int,
            firstHit: Boolean = true,
        ) {
            val proj = manager.spawnProjectile(this, target, travel, projanim)
            ammunition.useQuiverAmmo(player, quiver, target.coords, dropDelay = proj.serverCycles)
            if (firstHit) {
                manager.queueRangedHit(this, target, quiver, damage, proj.clientCycles, proj.serverCycles)
            } else {
                manager.queueRangedDamage(this, target, quiver, damage, proj.serverCycles)
            }
        }

        protected fun ProtectedAccess.guaranteedDamage(
            target: PathingEntity,
            attack: CombatAttack.Ranged,
            multiplier: Double = 1.0,
        ): Int = manager.rollRangedMaxHit(this, target, attack.type, attack.style, multiplier, 0)
    }

    /** Magic shortbow: two arrows in quick succession. */
    private class Snapshot(manager: SpecialAttackManager, ammunition: RangedAmmoManager) :
        AmmoSpecial(manager, ammunition) {
        override val ammoRequired: Int = 2

        override fun travelSpot(quiver: ItemServerType): String = GLOW_ARROW_TRAVEL

        override fun ProtectedAccess.shoot(
            target: PathingEntity,
            attack: CombatAttack.Ranged,
            weapon: ItemServerType,
            quiver: ItemServerType,
            travel: String,
        ) {
            anim("seq.snapshot")
            spotanim("spotanim.sp_attack_snapshot_spotanim", height = 96, slot = COMBAT_SLOT)
            val first = manager.rollRangedDamage(this, target, attack)
            val second = manager.rollRangedDamage(this, target, attack)
            manager.giveCombatXp(this, target, attack, first + second)
            launch(target, quiver, travel, "projanim.doublearrow_one", first)
            launch(target, quiver, travel, "projanim.doublearrow_two", second, firstHit = false)
        }
    }

    /** Magic longbow and comp bow: one arrow that cannot miss. */
    private class Powershot(manager: SpecialAttackManager, ammunition: RangedAmmoManager) :
        AmmoSpecial(manager, ammunition) {
        override fun travelSpot(quiver: ItemServerType): String = GLOW_ARROW_TRAVEL

        override fun ProtectedAccess.shoot(
            target: PathingEntity,
            attack: CombatAttack.Ranged,
            weapon: ItemServerType,
            quiver: ItemServerType,
            travel: String,
        ) {
            anim("seq.human_bow")
            spotanim("spotanim.sp_attack_glow_arrow_launch", height = 96, slot = COMBAT_SLOT)
            val damage = guaranteedDamage(target, attack)
            manager.giveCombatXp(this, target, attack, damage)
            launch(target, quiver, travel, "projanim.arrow", damage)
        }
    }

    /** Seercull: one arrow that cannot miss and drains the target's Magic by the damage dealt. */
    private class Soulshot(manager: SpecialAttackManager, ammunition: RangedAmmoManager) :
        AmmoSpecial(manager, ammunition) {
        override fun travelSpot(quiver: ItemServerType): String =
            "spotanim.sp_attack_glow_arrow_travel_white"

        override fun ProtectedAccess.shoot(
            target: PathingEntity,
            attack: CombatAttack.Ranged,
            weapon: ItemServerType,
            quiver: ItemServerType,
            travel: String,
        ) {
            anim("seq.human_bow")
            spotanim("spotanim.sp_attack_glow_arrow_launch_white", height = 96, slot = COMBAT_SLOT)
            val damage = guaranteedDamage(target, attack)
            manager.giveCombatXp(this, target, attack, damage)
            launch(target, quiver, travel, "projanim.arrow", damage)
            TargetStats.drain(target, TargetStats.MAGIC, damage)
        }
    }

    /**
     * Dorgeshuun crossbow: a bolt that cannot miss an npc not already fighting the player, and
     * drains the target's Defence by the damage dealt.
     */
    private class Snipe(manager: SpecialAttackManager, ammunition: RangedAmmoManager) :
        AmmoSpecial(manager, ammunition) {
        override fun travelSpot(quiver: ItemServerType): String =
            "spotanim.dttd_bone_crossbowbolt_travel_sp_attack"

        override fun ProtectedAccess.shoot(
            target: PathingEntity,
            attack: CombatAttack.Ranged,
            weapon: ItemServerType,
            quiver: ItemServerType,
            travel: String,
        ) {
            anim("seq.xbows_human_fire_and_reload")
            val unaware = target is Npc && target.vars["varn.attacking_player"] != player.uid.packed
            val damage =
                if (unaware) guaranteedDamage(target, attack)
                else manager.rollRangedDamage(this, target, attack)
            manager.giveCombatXp(this, target, attack, damage)
            launch(target, quiver, travel, "projanim.bolt", damage)
            if (damage > 0) {
                TargetStats.drain(target, TargetStats.DEFENCE, damage)
            }
        }
    }

    /**
     * Dragon crossbow: the bolt hits the target for +20% damage and, in a multi-combat area, every
     * attackable npc standing next to the target for -20% damage.
     */
    private class Annihilate(
        manager: SpecialAttackManager,
        ammunition: RangedAmmoManager,
        private val npcSearch: NpcSearch,
    ) : AmmoSpecial(manager, ammunition) {
        override fun ProtectedAccess.shoot(
            target: PathingEntity,
            attack: CombatAttack.Ranged,
            weapon: ItemServerType,
            quiver: ItemServerType,
            travel: String,
        ) {
            anim("seq.xbows_human_fire_and_reload")
            val damage = manager.rollRangedDamage(this, target, attack, maxHitMultiplier = 1.2)
            manager.giveCombatXp(this, target, attack, damage)
            launch(target, quiver, travel, "projanim.bolt", damage)
            if (!mapMultiway()) {
                return
            }
            val others =
                npcSearch
                    .findAllAny(target.coords, distance = 1, vis = HuntVis.Off)
                    .filter { it != target && it.isValidTarget() && it.type.hasOp(2) }
                    .take(ANNIHILATE_MAX_EXTRA_TARGETS)
            for (npc in others) {
                val splash = manager.rollRangedDamage(this, npc, attack, maxHitMultiplier = 0.8)
                manager.giveCombatXp(this, npc, attack, splash)
                val proj = manager.spawnProjectile(this, npc, travel, "projanim.bolt")
                manager.queueRangedDamage(this, npc, quiver, splash, proj.serverCycles)
            }
        }
    }

    /**
     * Armadyl crossbow: a bolt at double accuracy. Officially it also doubles the chance of an
     * enchanted bolt effect; bolt effects are not implemented, so only the accuracy applies.
     */
    private class ArmadylEye(manager: SpecialAttackManager, ammunition: RangedAmmoManager) :
        AmmoSpecial(manager, ammunition) {
        override fun ProtectedAccess.shoot(
            target: PathingEntity,
            attack: CombatAttack.Ranged,
            weapon: ItemServerType,
            quiver: ItemServerType,
            travel: String,
        ) {
            anim("seq.xbows_human_fire_and_reload")
            spotanim("spotanim.acb_specialattack", height = 96, slot = COMBAT_SLOT)
            val damage = manager.rollRangedDamage(this, target, attack, accuracyMultiplier = 2.0)
            manager.giveCombatXp(this, target, attack, damage)
            launch(target, quiver, travel, "projanim.bolt", damage)
        }
    }

    /** Ballistas: a javelin at +25% accuracy and +25% damage. */
    private class ConcentratedShot(manager: SpecialAttackManager, ammunition: RangedAmmoManager) :
        AmmoSpecial(manager, ammunition) {
        override fun ProtectedAccess.shoot(
            target: PathingEntity,
            attack: CombatAttack.Ranged,
            weapon: ItemServerType,
            quiver: ItemServerType,
            travel: String,
        ) {
            anim("seq.ballista_special_attack")
            spotanim("spotanim.ballista_special", height = 96, slot = COMBAT_SLOT)
            val projanim =
                weapon.paramOrNull(params.proj_type)?.let {
                    RSCM.getReverseMapping(RSCMType.PROJANIM, it.id)
                } ?: "projanim.bolt"
            val damage = manager.rollRangedDamage(this, target, attack, 1.25, 1.25)
            manager.giveCombatXp(this, target, attack, damage)
            launch(target, quiver, travel, projanim, damage)
        }
    }

    /** Shared plumbing for specials that throw the wielded weapon itself. */
    private abstract class ThrownSpecial(
        protected val manager: SpecialAttackManager,
        protected val ammunition: RangedAmmoManager,
        private val required: Int,
    ) : RangedSpecialAttack {
        override suspend fun ProtectedAccess.attack(target: Npc, attack: CombatAttack.Ranged) =
            fire(target, attack)

        override suspend fun ProtectedAccess.attack(target: Player, attack: CombatAttack.Ranged) =
            fire(target, attack)

        private fun ProtectedAccess.fire(target: PathingEntity, attack: CombatAttack.Ranged): Boolean {
            val weaponType = getInvObj(attack.weapon)
            val count = player.righthand?.count ?: 0
            if (count < required) {
                manager.stopCombat(this)
                mes("You need at least $required of them to use this special attack.")
                return false
            }
            throwAt(target, attack, weaponType)
            if (player.righthand == null) {
                mes("That was your last one!")
                return true
            }
            manager.continueCombat(this, target)
            return true
        }

        protected abstract fun ProtectedAccess.throwAt(
            target: PathingEntity,
            attack: CombatAttack.Ranged,
            weapon: ItemServerType,
        )

        protected fun ProtectedAccess.throwOne(
            target: PathingEntity,
            weapon: ItemServerType,
            travel: String,
            damage: Int,
            firstHit: Boolean = true,
        ) {
            val proj = manager.spawnProjectile(this, target, travel, "projanim.thrown")
            ammunition.useThrownWeapon(player, weapon, target.coords, dropDelay = proj.serverCycles)
            if (firstHit) {
                manager.queueRangedHit(this, target, null, damage, proj.clientCycles, proj.serverCycles)
            } else {
                manager.queueRangedDamage(this, target, null, damage, proj.serverCycles)
            }
        }
    }

    /** Dragon knife: two knives thrown at once, each rolled separately. */
    private class Duality(
        manager: SpecialAttackManager,
        ammunition: RangedAmmoManager,
        private val travel: String,
    ) : ThrownSpecial(manager, ammunition, required = 2) {
        override fun ProtectedAccess.throwAt(
            target: PathingEntity,
            attack: CombatAttack.Ranged,
            weapon: ItemServerType,
        ) {
            anim("seq.human_dragon_tknives_spec")
            spotanim("spotanim.dragon_tknife_launch", height = 96, slot = COMBAT_SLOT)
            val first = manager.rollRangedDamage(this, target, attack)
            val second = manager.rollRangedDamage(this, target, attack)
            manager.giveCombatXp(this, target, attack, first + second)
            throwOne(target, weapon, travel, first)
            throwOne(target, weapon, travel, second, firstHit = false)
        }
    }

    /** Dragon thrownaxe: +25% accuracy, and the next attack is ready on the very next tick. */
    private class MomentumThrow(manager: SpecialAttackManager, ammunition: RangedAmmoManager) :
        ThrownSpecial(manager, ammunition, required = 1) {
        override fun ProtectedAccess.throwAt(
            target: PathingEntity,
            attack: CombatAttack.Ranged,
            weapon: ItemServerType,
        ) {
            anim("seq.human_dragon_taxe_spec")
            spotanim("spotanim.dragon_taxe_launch_spec", height = 96, slot = COMBAT_SLOT)
            val damage = manager.rollRangedDamage(this, target, attack, accuracyMultiplier = 1.25)
            manager.giveCombatXp(this, target, attack, damage)
            throwOne(target, weapon, "spotanim.dragon_taxe_travel_spec", damage)
            manager.setNextAttackDelay(this, MOMENTUM_ATTACK_DELAY)
        }
    }

    private companion object {
        private const val COMBAT_SLOT = constants.spotanim_slot_combat
        private const val GLOW_ARROW_TRAVEL = "spotanim.sp_attack_glow_arrow_travel"
        private const val DRAGON_KNIFE_TRAVEL = "spotanim.dragon_tknife_travel_spec"
        private const val DRAGON_KNIFE_TRAVEL_P = "spotanim.dragon_tknife_travel_spec_p"
        private const val ANNIHILATE_MAX_EXTRA_TARGETS = 8
        private const val MOMENTUM_ATTACK_DELAY = 1
    }
}
