package org.rsmod.content.skills.magic.spell.attacks.standard

import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.manager.MagicRuneManager.Companion.isFailure
import org.rsmod.api.config.refs.params
import org.rsmod.api.player.bonus.WornBonuses
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.spells.attack.SpellAttack
import org.rsmod.api.spells.attack.SpellAttackManager
import org.rsmod.api.spells.attack.SpellAttackMap
import org.rsmod.api.spells.attack.SpellAttackRepository
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.type.getOrNull

/** Shared Crumble Undead spell, including Vorkath's zombified-spawn accuracy exception. */
internal class CrumbleUndeadSpells @Inject constructor(private val bonuses: WornBonuses) :
    SpellAttackMap {
    override fun SpellAttackRepository.register(manager: SpellAttackManager) {
        register(spell = "obj.39_crumble_undead", attack = CrumbleUndeadAttack(manager, bonuses))
    }
}

internal object CrumbleUndeadRules {
    const val MAX_HIT = 15
    const val SPAWN_ACCURACY_THRESHOLD = -64

    fun isVorkathSpawn(internalName: String): Boolean =
        internalName.removePrefix("npc.") == "vorkath_spawn"

    fun forceHitSpawn(internalName: String, magicAttackBonus: Int): Boolean =
        isVorkathSpawn(internalName) && magicAttackBonus > SPAWN_ACCURACY_THRESHOLD
}

private class CrumbleUndeadAttack(
    private val manager: SpellAttackManager,
    private val bonuses: WornBonuses,
) : SpellAttack {
    override suspend fun ProtectedAccess.attack(target: Npc, attack: CombatAttack.Spell) {
        val vorkathSpawn = CrumbleUndeadRules.isVorkathSpawn(target.type.internalName)
        // Revision 240 does not set the generic undead parameter on Vorkath's spawn.
        if (!vorkathSpawn && target.visType.param(params.undead) == 0) {
            mes("This spell only affects the undead.")
            manager.stopCombat(this)
            return
        }
        val castResult = manager.attemptCast(this, attack)
        if (castResult.isFailure()) return

        player.anim(getOrNull(attack.weapon).castAnim(), priority = 6)
        spotanim("spotanim.crumbleundead_casting", height = 92)
        val proj =
            manager.spawnProjectile(
                this,
                target,
                "spotanim.crumbleundead_travel",
                "projanim.magic_spell",
            )
        val (serverDelay, clientDelay) = proj.durations
        val forceHit =
            CrumbleUndeadRules.forceHitSpawn(
                target.type.internalName,
                bonuses.offensiveMagicBonus(player),
            )
        val splash = !forceHit && manager.rollSplash(this, target, attack, castResult)
        if (splash) {
            manager.playSplashFx(this, target, clientDelay, "synth.crumble_cast_and_fire", 8)
            manager.queueSplashHit(this, target, attack.spell.obj, clientDelay, serverDelay)
            manager.continueCombatIfAutocast(this, target)
            return
        }

        val damage =
            if (vorkathSpawn) {
                target.hitpoints
            } else {
                manager.rollMaxHit(this, target, attack, castResult, CrumbleUndeadRules.MAX_HIT)
            }
        manager.playHitFx(
            source = this,
            target = target,
            clientDelay = clientDelay,
            castSound = "synth.crumble_cast_and_fire",
            soundRadius = 8,
            hitSpot = "spotanim.crumbleundead_impact",
            hitSpotHeight = 124,
            hitSound = "synth.crumble_hit",
        )
        manager.giveCombatXp(this, target, attack, damage)
        manager.queueMagicHit(this, target, attack.spell.obj, damage, clientDelay, serverDelay)
        manager.continueCombatIfAutocast(this, target)
    }

    override suspend fun ProtectedAccess.attack(target: Player, attack: CombatAttack.Spell) {
        mes("This spell only affects the undead.")
        manager.stopCombat(this)
    }

    private fun ItemServerType?.castAnim(): String =
        if (this != null && isCategoryType("category.staff")) {
            "seq.human_castcrumbleundead_staff"
        } else {
            "seq.human_castcrumbleundead"
        }
}
