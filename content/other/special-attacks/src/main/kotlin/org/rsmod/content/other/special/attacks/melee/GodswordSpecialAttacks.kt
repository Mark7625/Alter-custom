package org.rsmod.content.other.special.attacks.melee

import jakarta.inject.Inject
import kotlin.math.max
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.CombatEffects
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
 * The godsword specials - The Judgement (Armadyl), Warstrike (Bandos), Healing Blade (Saradomin)
 * and Ice Cleave (Zamorak) - and the Saradomin sword's Saradomin's Lightning. Every godsword
 * special doubles accuracy; what differs is the damage bonus and the effect on a successful hit.
 */
class GodswordSpecialAttacks @Inject constructor(private val worldRepo: WorldRepository) :
    SpecialAttackMap {
    override fun SpecialAttackRepository.register(manager: SpecialAttackManager) {
        registerMelee("obj.ags", Godsword(manager, worldRepo, "seq.ags_special_player", ARMADYL))
        registerMelee("obj.agsg", Godsword(manager, worldRepo, "seq.ags_special_player", ARMADYL))
        registerMelee("obj.bgs", Godsword(manager, worldRepo, "seq.bgs_special_player", BANDOS))
        registerMelee(
            "obj.bgsg",
            Godsword(manager, worldRepo, "seq.bgs_special_ornate_player", BANDOS),
        )
        registerMelee("obj.sgs", Godsword(manager, worldRepo, "seq.sgs_special_player", SARADOMIN))
        registerMelee(
            "obj.sgsg",
            Godsword(manager, worldRepo, "seq.sgs_special_ornate_player", SARADOMIN),
        )
        registerMelee("obj.zgs", Godsword(manager, worldRepo, "seq.zgs_special_player", ZAMORAK))
        registerMelee(
            "obj.zgsg",
            Godsword(manager, worldRepo, "seq.zgs_special_ornate_player", ZAMORAK),
        )

        registerMelee(
            "obj.saradomin_sword",
            SaradominsLightning(manager, "seq.saradomin_sword_special_player"),
        )
        registerMelee(
            "obj.blessed_saradomin_sword",
            SaradominsLightning(manager, "seq.blessed_saradomin_sword_special_player"),
        )
        registerMelee(
            "obj.blessed_saradomin_sword_degraded",
            SaradominsLightning(manager, "seq.blessed_saradomin_sword_special_player"),
        )
    }

    private data class GodswordEffect(
        val spot: String,
        val damageMultiplier: Double,
        val onHit: ProtectedAccess.(target: PathingEntity, damage: Int) -> Unit,
    )

    private class Godsword(
        private val manager: SpecialAttackManager,
        private val worldRepo: WorldRepository,
        private val seq: String,
        private val effect: GodswordEffect,
    ) : MeleeSpecialAttack {
        override suspend fun ProtectedAccess.attack(target: Npc, attack: CombatAttack.Melee) =
            strike(target, attack)

        override suspend fun ProtectedAccess.attack(target: Player, attack: CombatAttack.Melee) =
            strike(target, attack)

        private fun ProtectedAccess.strike(target: PathingEntity, attack: CombatAttack.Melee): Boolean {
            anim(seq)
            spotanim(effect.spot, height = 96, slot = constants.spotanim_slot_combat)
            worldRepo.soundArea(player, GODSWORD_SOUND, radius = SOUND_RADIUS)

            val damage =
                manager.rollMeleeDamage(
                    source = this,
                    target = target,
                    attack = attack,
                    accuracyMultiplier = 2.0,
                    maxHitMultiplier = effect.damageMultiplier,
                )
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueMeleeHit(this, target, damage)
            if (damage > 0) {
                effect.onHit(this, target, damage)
            }
            manager.continueCombat(this, target)
            return true
        }
    }

    /**
     * Saradomin sword: a melee hit at +10% damage plus a burst of lightning that always lands
     * for 1-16 magic damage.
     */
    private class SaradominsLightning(
        private val manager: SpecialAttackManager,
        private val seq: String,
    ) : MeleeSpecialAttack {
        override suspend fun ProtectedAccess.attack(target: Npc, attack: CombatAttack.Melee) =
            lightning(target, attack)

        override suspend fun ProtectedAccess.attack(target: Player, attack: CombatAttack.Melee) =
            lightning(target, attack)

        private fun ProtectedAccess.lightning(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ): Boolean {
            anim(seq)
            target.spotanim("spotanim.godwars_saradomin_sword_attack_spot", height = 96)

            val melee = manager.rollMeleeDamage(this, target, attack, 1.0, 1.1)
            val magic = random.of(LIGHTNING_MIN..LIGHTNING_MAX)
            manager.giveCombatXp(this, target, attack, melee + magic)
            manager.queueMeleeHit(this, target, melee)
            manager.queueMagicHit(this, target, magic, clientDelay = 0, hitDelay = 1)
            manager.continueCombat(this, target)
            return true
        }
    }

    private companion object {
        private const val SOUND_RADIUS = 10
        private const val GODSWORD_SOUND = 3869
        private const val LIGHTNING_MIN = 1
        private const val LIGHTNING_MAX = 16
        private const val ICE_CLEAVE_FREEZE_TICKS = 33
        private const val HEALING_BLADE_MIN_HEAL = 10
        private const val HEALING_BLADE_MIN_PRAYER = 5

        /** Armadyl: double accuracy and +37.5% damage, nothing else. */
        private val ARMADYL =
            GodswordEffect(
                spot = "spotanim.dh_sword_update_armadyl_special_spotanim",
                damageMultiplier = 1.375,
                onHit = { _, _ -> },
            )

        /**
         * Bandos: +21% damage; the damage dealt is drained from the target's Defence, and whatever
         * is left over spills into Strength, Prayer, Attack, Magic and Ranged in turn.
         */
        private val BANDOS =
            GodswordEffect(
                spot = "spotanim.dh_sword_update_bandos_special_spotanim",
                damageMultiplier = 1.21,
                onHit = { target, damage -> warstrike(target, damage) },
            )

        /** Saradomin: +10% damage; heals half the damage and restores a quarter as prayer. */
        private val SARADOMIN =
            GodswordEffect(
                spot = "spotanim.dh_sword_update_saradomin_special_spotanim",
                damageMultiplier = 1.1,
                onHit = { _, damage ->
                    val heal = max(HEALING_BLADE_MIN_HEAL, damage / 2)
                    val prayer = max(HEALING_BLADE_MIN_PRAYER, damage / 4)
                    player.statHeal("stat.hitpoints", constant = heal, percent = 0)
                    player.statHeal(TargetStats.PRAYER, constant = prayer, percent = 0)
                },
            )

        /** Zamorak: +10% damage; a player hit is frozen for twenty seconds. */
        private val ZAMORAK =
            GodswordEffect(
                spot = "spotanim.dh_sword_update_zamorak_special_spotanim",
                damageMultiplier = 1.1,
                onHit = { target, _ ->
                    if (target is Player) {
                        target.spotanim("spotanim.ice_barrage_impact", height = 0)
                        CombatEffects.freeze(target, ICE_CLEAVE_FREEZE_TICKS)
                    }
                },
            )

        private fun warstrike(target: PathingEntity, damage: Int) {
            var remaining = damage
            val order =
                listOf(
                    TargetStats.DEFENCE,
                    TargetStats.STRENGTH,
                    TargetStats.PRAYER,
                    TargetStats.ATTACK,
                    TargetStats.MAGIC,
                    TargetStats.RANGED,
                )
            for (stat in order) {
                if (remaining <= 0) {
                    return
                }
                if (stat == TargetStats.PRAYER && target !is Player) {
                    continue
                }
                remaining -= TargetStats.drain(target, stat, remaining)
            }
        }
    }
}
