package org.rsmod.content.other.special.attacks.ranged

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.manager.RangedAmmoManager
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.params
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.quiver
import org.rsmod.api.specials.SpecialAttackManager
import org.rsmod.api.specials.SpecialAttackMap
import org.rsmod.api.specials.SpecialAttackRepository
import org.rsmod.api.specials.combat.RangedSpecialAttack
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.type.getInvObj
import org.rsmod.game.type.getOrNull

class DarkBowSpecialAttack @Inject constructor(private val ammunition: RangedAmmoManager) :
    SpecialAttackMap {
    override fun SpecialAttackRepository.register(manager: SpecialAttackManager) {
        registerRanged("obj.darkbow", DarkBow(manager, ammunition))
        registerRanged("obj.darkbow_green", DarkBow(manager, ammunition))
        registerRanged("obj.darkbow_blue", DarkBow(manager, ammunition))
        registerRanged("obj.darkbow_yellow", DarkBow(manager, ammunition))
        registerRanged("obj.darkbow_white", DarkBow(manager, ammunition))
        registerRanged("obj.bh_darkbow_imbue", DarkBow(manager, ammunition))
    }

    private class DarkBow(
        private val manager: SpecialAttackManager,
        private val ammunition: RangedAmmoManager,
    ) : RangedSpecialAttack {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Ranged,
        ): Boolean = selectAndShootSpecial(target, attack)

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Ranged,
        ): Boolean = selectAndShootSpecial(target, attack)

        private fun ProtectedAccess.selectAndShootSpecial(
            target: PathingEntity,
            attack: CombatAttack.Ranged,
        ): Boolean {
            val righthandType = getInvObj(attack.weapon)
            val quiverType = getOrNull(player.quiver)

            val canUseAmmo = ammunition.attemptAmmoUsage(player, righthandType, quiverType)
            if (!canUseAmmo) {
                manager.stopCombat(this)
                return false
            }

            // All valid ammunition requires a `proj_travel` param to build the projectiles.
            val travelSpotanim = quiverType?.paramOrNull(params.proj_travel)
            if (travelSpotanim == null) {
                manager.stopCombat(this)
                mes("You are unable to fire your ammunition.")
                return false
            }

            val quiverCount = player.quiver?.count ?: 0
            if (quiverCount < 2) {
                manager.stopCombat(this)
                mes("You need to have at least 2 arrows in your quiver for this special attack.")
                return false
            }

            val descentOfDragons = quiverType.isCategoryType("category.dragon_arrow")
            if (descentOfDragons) {
                descentOfDragons(target, attack, quiverType, RSCM.getReverseMapping(RSCMType.SPOTANIM, travelSpotanim.id))
                manager.continueCombat(this, target)
                return true
            }

            descentOfDarkness(target, attack, quiverType, RSCM.getReverseMapping(RSCMType.SPOTANIM, travelSpotanim.id))
            manager.continueCombat(this, target)
            return true
        }

        private fun ProtectedAccess.descentOfDarkness(
            target: PathingEntity,
            attack: CombatAttack.Ranged,
            quiverType: ItemServerType,
            travelSpot: String,
        ) {
            // Broad and ogre arrows carry no double-launch graphic; fall back to the single one.
            val launchSpot =
                quiverType.paramOrNull(params.proj_launch_double)
                    ?: quiverType.paramOrNull(params.proj_launch)
            anim("seq.human_bow")
            soundSynth("synth.darkbow_doublefire")
            soundSynth("synth.darkbow_shadow_attack")
            spotanim(
                launchSpot?.let { RSCM.getReverseMapping(RSCMType.SPOTANIM, it.id) },
                height = 96,
                slot = constants.spotanim_slot_combat,
            )

            val descentTravel = "spotanim.darkbow_generic_smoke_arrow_flight"
            val descentImpact = "spotanim.darkbow_smoke_arrow_impact"
            val impactSynth = "synth.darkbow_shadow_impact"

            manager.spawnProjectile(this, target, descentTravel, "projanim.doublearrow_one")
            val proj1 = manager.spawnProjectile(this, target, travelSpot, "projanim.doublearrow_one")
            val clientDelay1 = proj1.clientCycles
            manager.soundArea(target, impactSynth, delay = clientDelay1, radius = 10)

            manager.spawnProjectile(this, target, descentTravel, "projanim.doublearrow_two")
            val proj2 = manager.spawnProjectile(this, target, travelSpot, "projanim.doublearrow_two")
            val clientDelay2 = proj2.clientCycles
            manager.soundArea(target, impactSynth, delay = clientDelay2, radius = 10)

            target.spotanim(descentImpact, height = 96, delay = clientDelay2)

            val damage =
                calculateDamage(target, attack, damageRange = 5..48, multiplier = 1.3)
            val hitDelay1 = proj1.serverCycles
            val hitDelay2 = proj2.serverCycles

            manager.giveCombatXp(this, target, attack, damage.total)

            ammunition.useQuiverAmmo(
                player = player,
                quiverType = quiverType,
                dropCoord = target.coords,
                dropDelay = hitDelay1,
            )

            manager.queueRangedHit(this, target, quiverType, damage[0], clientDelay2, hitDelay1)

            ammunition.useQuiverAmmo(
                player = player,
                quiverType = quiverType,
                dropCoord = target.coords,
                dropDelay = hitDelay2,
            )

            manager.queueRangedDamage(this, target, quiverType, damage[1], hitDelay2)

            if (player.quiver?.count == 1) {
                mes("You now have only 1 arrow left in your quiver.")
            }
        }

        private fun ProtectedAccess.descentOfDragons(
            target: PathingEntity,
            attack: CombatAttack.Ranged,
            quiverType: ItemServerType,
            travelSpot: String,
        ) {
            // Broad and ogre arrows carry no double-launch graphic; fall back to the single one.
            val launchSpot =
                quiverType.paramOrNull(params.proj_launch_double)
                    ?: quiverType.paramOrNull(params.proj_launch)
            anim("seq.human_bow")
            soundSynth("synth.darkbow_doublefire")
            soundSynth("synth.darkbow_dragon_attack")
            spotanim(
                launchSpot?.let { RSCM.getReverseMapping(RSCMType.SPOTANIM, it.id) },
                height = 96,
                slot = constants.spotanim_slot_combat,
            )

            val descentTravel = "spotanim.darkbow_dragon_head_flying_projanim"
            val descentImpact = "spotanim.darkbow_dragon_head_flying_impact_anim"
            val impactSynth = "synth.darkbow_shadow_impact"

            manager.spawnProjectile(this, target, descentTravel, "projanim.doublearrow_one")
            val proj1 = manager.spawnProjectile(this, target, travelSpot, "projanim.doublearrow_one")
            val clientDelay1 = proj1.clientCycles
            manager.soundArea(target, impactSynth, delay = clientDelay1, radius = 10)

            manager.spawnProjectile(this, target, descentTravel, "projanim.doublearrow_two")
            val proj2 = manager.spawnProjectile(this, target, travelSpot, "projanim.doublearrow_two")
            val clientDelay2 = proj2.clientCycles
            manager.soundArea(target, impactSynth, delay = clientDelay2, radius = 10)

            target.spotanim(descentImpact, height = 96, delay = clientDelay2)

            val damage = calculateDamage(target, attack, damageRange = 8..48, multiplier = 1.5)
            val hitDelay1 = proj1.serverCycles
            val hitDelay2 = proj2.serverCycles

            manager.giveCombatXp(this, target, attack, damage.total)

            ammunition.useQuiverAmmo(
                player = player,
                quiverType = quiverType,
                dropCoord = target.coords,
                dropDelay = hitDelay1,
            )

            manager.queueRangedHit(this, target, quiverType, damage[0], clientDelay2, hitDelay1)

            ammunition.useQuiverAmmo(
                player = player,
                quiverType = quiverType,
                dropCoord = target.coords,
                dropDelay = hitDelay2,
            )

            manager.queueRangedDamage(this, target, quiverType, damage[1], hitDelay2)

            if (player.quiver?.count == 1) {
                mes("You now have only 1 arrow left in your quiver.")
            }
        }

        private fun ProtectedAccess.calculateDamage(
            target: PathingEntity,
            attack: CombatAttack.Ranged,
            damageRange: IntRange,
            multiplier: Double,
        ): DescentHit {
            fun accuracySuccess(): Boolean {
                return manager.rollRangedAccuracy(
                    source = this,
                    target = target,
                    attackType = attack.type,
                    attackStyle = attack.style,
                    blockType = attack.type,
                    multiplier = 1.0,
                )
            }
            val damage =
                manager.calculateRangedMaxHit(
                    source = this,
                    target = target,
                    attackType = attack.type,
                    attackStyle = attack.style,
                    multiplier = multiplier,
                    boltSpecDamage = 0,
                )
            // Each arrow always deals at least the floor of its range, even when the accuracy
            // roll fails, and never more than its cap.
            val first = (if (accuracySuccess()) random.of(0..damage) else 0).coerceIn(damageRange)
            val second = (if (accuracySuccess()) random.of(0..damage) else 0).coerceIn(damageRange)
            return DescentHit(first, second)
        }

        private data class DescentHit(val first: Int, val second: Int) {
            val total: Int
                get() = first + second

            operator fun get(index: Int): Int =
                when (index) {
                    0 -> first
                    1 -> second
                    else -> throw ArrayIndexOutOfBoundsException()
                }
        }
    }
}
