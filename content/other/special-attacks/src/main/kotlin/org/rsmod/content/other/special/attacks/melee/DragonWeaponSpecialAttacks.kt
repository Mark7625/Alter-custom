package org.rsmod.content.other.special.attacks.melee

import dev.openrune.types.hunt.HuntVis
import jakarta.inject.Inject
import kotlin.math.max
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.CombatEffects
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.config.constants
import org.rsmod.api.hunt.NpcSearch
import org.rsmod.api.npc.isValidTarget
import org.rsmod.api.player.protect.ProtectedAccess
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
 * The dragon melee weapon specials: Puncture (dagger), Shatter (mace), Sever (scimitar), Wild
 * Stab (sword), Powerstab (2h sword), Sweep (halberd), Shove (spear, Zamorakian spear and hasta),
 * Rampage (battleaxe) and Slice and Dice (claws).
 */
class DragonWeaponSpecialAttacks
@Inject
constructor(private val worldRepo: WorldRepository, private val npcSearch: NpcSearch) :
    SpecialAttackMap {
    override fun SpecialAttackRepository.register(manager: SpecialAttackManager) {
        val puncture = Puncture(manager, worldRepo)
        registerMelee("obj.dragon_dagger", puncture)
        registerMelee("obj.dragon_dagger_p", puncture)
        registerMelee("obj.dragon_dagger_p+", puncture)
        registerMelee("obj.dragon_dagger_p++", puncture)

        registerMelee("obj.dragon_mace", Shatter(manager))

        val sever = Sever(manager, worldRepo)
        registerMelee("obj.dragon_scimitar", sever)
        registerMelee("obj.dragon_scimitar_ornament", sever)

        registerMelee("obj.dragon_shortsword", WildStab(manager))
        registerMelee("obj.dragon_2h_sword", Powerstab(manager, npcSearch))
        registerMelee("obj.dragon_halberd", Sweep(manager))

        val shove = Shove(manager, "seq.shove")
        registerMelee("obj.dragon_spear", shove)
        registerMelee("obj.dragon_spear_p", shove)
        registerMelee("obj.dragon_spear_p+", shove)
        registerMelee("obj.dragon_spear_p++", shove)
        registerMelee("obj.zamorak_spear", shove)
        registerMelee("obj.zamorak_hasta", Shove(manager, "seq.shove_1h"))

        registerInstant("obj.dragon_battleaxe") { rampage(worldRepo) }

        val sliceAndDice = SliceAndDice(manager)
        registerMelee("obj.dragon_claws", sliceAndDice)
        registerMelee("obj.dragon_claws_ornament", sliceAndDice)
    }

    /** Dragon dagger: two quick stabs at +15% accuracy and +15% damage. */
    private class Puncture(
        private val manager: SpecialAttackManager,
        private val worldRepo: WorldRepository,
    ) : MeleeSpecialAttack {
        override suspend fun ProtectedAccess.attack(target: Npc, attack: CombatAttack.Melee) =
            puncture(target, attack)

        override suspend fun ProtectedAccess.attack(target: Player, attack: CombatAttack.Melee) =
            puncture(target, attack)

        private fun ProtectedAccess.puncture(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ): Boolean {
            anim("seq.puncture")
            spotanim("spotanim.sp_attack_puncture_spotanim", height = 96, slot = COMBAT_SLOT)
            worldRepo.soundArea(player, PUNCTURE_SOUND, radius = SOUND_RADIUS)

            val first = manager.rollMeleeDamage(this, target, attack, 1.15, 1.15)
            val second = manager.rollMeleeDamage(this, target, attack, 1.15, 1.15)
            manager.giveCombatXp(this, target, attack, first + second)
            manager.queueMeleeHit(this, target, first)
            manager.queueMeleeHit(this, target, second)
            manager.continueCombat(this, target)
            return true
        }
    }

    /** Dragon mace: one hit at +25% accuracy and +50% damage. */
    private class Shatter(private val manager: SpecialAttackManager) : MeleeSpecialAttack {
        override suspend fun ProtectedAccess.attack(target: Npc, attack: CombatAttack.Melee) =
            shatter(target, attack)

        override suspend fun ProtectedAccess.attack(target: Player, attack: CombatAttack.Melee) =
            shatter(target, attack)

        private fun ProtectedAccess.shatter(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ): Boolean {
            anim("seq.shatter")
            spotanim("spotanim.sp_attack_shatter_spotanim", height = 96, slot = COMBAT_SLOT)
            singleHit(manager, target, attack, accuracy = 1.25, damage = 1.5)
            return true
        }
    }

    /**
     * Dragon scimitar: one hit at +25% accuracy. Officially it also disables a player's protection
     * prayers for five seconds; prayer deactivation is not exposed to content yet, so the hit
     * stands alone.
     */
    private class Sever(
        private val manager: SpecialAttackManager,
        private val worldRepo: WorldRepository,
    ) : MeleeSpecialAttack {
        override suspend fun ProtectedAccess.attack(target: Npc, attack: CombatAttack.Melee) =
            sever(target, attack)

        override suspend fun ProtectedAccess.attack(target: Player, attack: CombatAttack.Melee) =
            sever(target, attack)

        private fun ProtectedAccess.sever(target: PathingEntity, attack: CombatAttack.Melee): Boolean {
            anim("seq.sp_attack_dragon_scimitar")
            spotanim(
                "spotanim.sp_attack_dragon_scimitar_trail_spotanim",
                height = 96,
                slot = COMBAT_SLOT,
            )
            worldRepo.soundArea(player, SEVER_SOUND, radius = SOUND_RADIUS)
            singleHit(manager, target, attack, accuracy = 1.25, damage = 1.0)
            return true
        }
    }

    /**
     * Dragon sword: one stab at +25% accuracy and +25% damage. The official special also ignores
     * Protect from Melee; that bypass is not exposed to content yet.
     */
    private class WildStab(private val manager: SpecialAttackManager) : MeleeSpecialAttack {
        override suspend fun ProtectedAccess.attack(target: Npc, attack: CombatAttack.Melee) =
            wildStab(target, attack)

        override suspend fun ProtectedAccess.attack(target: Player, attack: CombatAttack.Melee) =
            wildStab(target, attack)

        private fun ProtectedAccess.wildStab(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ): Boolean {
            anim("seq.human_dragon_sword_spec")
            spotanim("spotanim.dragon_sword_spec_spotanim", height = 96, slot = COMBAT_SLOT)
            singleHit(
                manager,
                target,
                attack,
                accuracy = 1.25,
                damage = 1.25,
                blockType = MeleeAttackType.Stab,
            )
            return true
        }
    }

    /**
     * Dragon 2h sword: a spinning stab that hits the target and, in a multi-combat area, every
     * other attackable npc standing next to the player (up to fourteen).
     */
    private class Powerstab(
        private val manager: SpecialAttackManager,
        private val npcSearch: NpcSearch,
    ) : MeleeSpecialAttack {
        override suspend fun ProtectedAccess.attack(target: Npc, attack: CombatAttack.Melee) =
            powerstab(target, attack)

        override suspend fun ProtectedAccess.attack(target: Player, attack: CombatAttack.Melee) =
            powerstab(target, attack)

        private fun ProtectedAccess.powerstab(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ): Boolean {
            anim("seq.dragon_two_handed_sword")
            spotanim("spotanim.dragon_two_handed_sword_blast", height = 96, slot = COMBAT_SLOT)

            val damage = manager.rollMeleeDamage(this, target, attack, 1.0, 1.0)
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueMeleeHit(this, target, damage)

            if (mapMultiway()) {
                val others =
                    npcSearch
                        .findAllZone(coords, distance = 1, vis = HuntVis.Off)
                        .filter { it != target && it.isValidTarget() && it.type.isAttackable() }
                        .take(POWERSTAB_MAX_EXTRA_TARGETS)
                for (npc in others) {
                    val extra = manager.rollMeleeDamage(this, npc, attack, 1.0, 1.0)
                    manager.giveCombatXp(this, npc, attack, extra)
                    manager.queueMeleeHit(this, npc, extra)
                }
            }
            manager.continueCombat(this, target)
            return true
        }

        private fun dev.openrune.types.NpcServerType.isAttackable(): Boolean =
            actions.getOpOrNull(1)?.equals("attack", ignoreCase = true) == true
    }

    /**
     * Dragon halberd: a wide sweep at +10% damage. Large monsters (bigger than one tile) are hit a
     * second time at -25% accuracy. The sweep graphic faces the target.
     */
    private class Sweep(private val manager: SpecialAttackManager) : MeleeSpecialAttack {
        override suspend fun ProtectedAccess.attack(target: Npc, attack: CombatAttack.Melee) =
            sweep(target, attack)

        override suspend fun ProtectedAccess.attack(target: Player, attack: CombatAttack.Melee) =
            sweep(target, attack)

        private fun ProtectedAccess.sweep(target: PathingEntity, attack: CombatAttack.Melee): Boolean {
            anim("seq.dragon_halberd_special_attack")
            spotanim(sweepSpot(target), height = 96, slot = COMBAT_SLOT)

            val first = manager.rollMeleeDamage(this, target, attack, 1.0, 1.1)
            var total = first
            manager.queueMeleeHit(this, target, first)
            if (target is Npc && target.size > 1) {
                val second = manager.rollMeleeDamage(this, target, attack, 0.75, 1.1)
                total += second
                manager.queueMeleeHit(this, target, second)
            }
            manager.giveCombatXp(this, target, attack, total)
            manager.continueCombat(this, target)
            return true
        }

        private fun ProtectedAccess.sweepSpot(target: PathingEntity): String {
            val dx = target.coords.x - coords.x
            val dz = target.coords.z - coords.z
            return when {
                kotlin.math.abs(dx) > kotlin.math.abs(dz) && dx > 0 ->
                    "spotanim.dragon_halberd_special_east_red"
                kotlin.math.abs(dx) > kotlin.math.abs(dz) -> "spotanim.dragon_halberd_special_west_red"
                dz > 0 -> "spotanim.dragon_halberd_special_north_red"
                else -> "spotanim.dragon_halberd_special_south_red"
            }
        }
    }

    /**
     * Dragon spear, Zamorakian spear and hasta: no damage, but the target is stunned for three
     * seconds - a player is held in place with the stunned graphic and animation; an npc has its
     * next attack delayed by the same time.
     */
    private class Shove(private val manager: SpecialAttackManager, private val seq: String) :
        MeleeSpecialAttack {
        override suspend fun ProtectedAccess.attack(target: Npc, attack: CombatAttack.Melee): Boolean {
            shove(target)
            target.actionDelay = max(target.actionDelay, mapClock + STUN_TICKS)
            manager.continueCombat(this, target)
            return true
        }

        override suspend fun ProtectedAccess.attack(target: Player, attack: CombatAttack.Melee): Boolean {
            shove(target)
            target.anim("seq.stunned_shove")
            CombatEffects.freeze(target, STUN_TICKS)
            manager.continueCombat(this, target)
            return true
        }

        private fun ProtectedAccess.shove(target: PathingEntity) {
            anim(seq)
            spotanim("spotanim.sp_attack_shove_spotanim", height = 96, slot = COMBAT_SLOT)
            target.spotanim("spotanim.stunned_shove", height = 124)
        }
    }

    /**
     * Dragon claws: up to four accuracy rolls. The first roll to succeed sets the damage of the
     * remaining hits, so an early success is worth far more than a late one.
     */
    private class SliceAndDice(private val manager: SpecialAttackManager) : MeleeSpecialAttack {
        override suspend fun ProtectedAccess.attack(target: Npc, attack: CombatAttack.Melee) =
            sliceAndDice(target, attack)

        override suspend fun ProtectedAccess.attack(target: Player, attack: CombatAttack.Melee) =
            sliceAndDice(target, attack)

        private fun ProtectedAccess.sliceAndDice(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ): Boolean {
            anim("seq.human_dragon_claws_spec")
            spotanim("spotanim.dragon_claws_spot", height = 96, slot = COMBAT_SLOT)

            val maxHit = manager.calculateMeleeMaxHit(this, target, attack.type, attack.style, 1.0)
            val hits = rollClaws(target, attack, maxHit)
            manager.giveCombatXp(this, target, attack, hits.sum())
            manager.queueMeleeHit(this, target, hits[0], delay = 1)
            manager.queueMeleeHit(this, target, hits[1], delay = 1)
            manager.queueMeleeHit(this, target, hits[2], delay = 2)
            manager.queueMeleeHit(this, target, hits[3], delay = 2)
            manager.continueCombat(this, target)
            return true
        }

        private fun ProtectedAccess.rollClaws(
            target: PathingEntity,
            attack: CombatAttack.Melee,
            maxHit: Int,
        ): IntArray {
            fun accurate(): Boolean =
                manager.rollMeleeAccuracy(this, target, attack.type, attack.style, attack.type, 1.0)
            if (accurate()) {
                val first = random.of(maxHit / 2..maxHit)
                val second = first / 2
                val third = second / 2
                return intArrayOf(first, second, third, first - second - third)
            }
            if (accurate()) {
                val second = random.of((maxHit * 3) / 8..(maxHit * 7) / 8)
                val third = second / 2
                return intArrayOf(0, second, third, second - third)
            }
            if (accurate()) {
                val third = random.of(maxHit / 4..(maxHit * 3) / 4)
                return intArrayOf(0, 0, third, third)
            }
            if (accurate()) {
                val fourth = random.of(maxHit / 4..(maxHit * 5) / 4)
                return intArrayOf(0, 0, 0, fourth)
            }
            return intArrayOf(0, 0, 1, 1)
        }
    }

    private companion object {
        private const val COMBAT_SLOT = constants.spotanim_slot_combat
        private const val SOUND_RADIUS = 10
        private const val PUNCTURE_SOUND = 2537
        private const val SEVER_SOUND = 2540
        private const val STUN_TICKS = 5
        private const val POWERSTAB_MAX_EXTRA_TARGETS = 13

        /** Dragon battleaxe: drains the other combat stats by 10% to fuel a big Strength boost. */
        private fun ProtectedAccess.rampage(worldRepo: WorldRepository): Boolean {
            var drained = 0
            for (stat in
                listOf(
                    TargetStats.ATTACK,
                    TargetStats.DEFENCE,
                    TargetStats.RANGED,
                    TargetStats.MAGIC,
                )) {
                drained += TargetStats.drainPercentOfCurrent(player, stat, RAMPAGE_DRAIN_PERCENT)
            }
            val boost = RAMPAGE_BASE_BOOST + drained / 4
            statBoost(TargetStats.STRENGTH, constant = boost, percent = 0)
            say("Raarrrrrgggggghhhhhhh!")
            anim("seq.rampage")
            spotanim("spotanim.sp_attackglow_red", height = 96, slot = COMBAT_SLOT)
            soundArea(worldRepo, coords, "synth.rampage", radius = SOUND_RADIUS)
            return true
        }

        private const val RAMPAGE_DRAIN_PERCENT = 10
        private const val RAMPAGE_BASE_BOOST = 10

        private fun ProtectedAccess.singleHit(
            manager: SpecialAttackManager,
            target: PathingEntity,
            attack: CombatAttack.Melee,
            accuracy: Double,
            damage: Double,
            blockType: MeleeAttackType? = attack.type,
        ): Int {
            val rolled =
                manager.rollMeleeDamage(
                    source = this,
                    target = target,
                    attack = attack,
                    accuracyMultiplier = accuracy,
                    maxHitMultiplier = damage,
                    blockType = blockType,
                )
            manager.giveCombatXp(this, target, attack, rolled)
            manager.queueMeleeHit(this, target, rolled)
            manager.continueCombat(this, target)
            return rolled
        }
    }
}
