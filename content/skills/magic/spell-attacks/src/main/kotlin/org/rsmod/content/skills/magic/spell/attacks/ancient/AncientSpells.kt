package org.rsmod.content.skills.magic.spell.attacks.ancient

import dev.openrune.types.hunt.HuntVis
import jakarta.inject.Inject
import kotlin.math.max
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.manager.MagicRuneManager
import org.rsmod.api.combat.manager.MagicRuneManager.Companion.isFailure
import org.rsmod.api.hunt.NpcSearch
import org.rsmod.api.mechanics.toxins.impl.PlayerPoison
import org.rsmod.api.npc.isValidTarget
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.stat.statHeal
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.spells.attack.SpellAttack
import org.rsmod.api.spells.attack.SpellAttackManager
import org.rsmod.api.spells.attack.SpellAttackMap
import org.rsmod.api.spells.attack.SpellAttackRepository
import org.rsmod.content.skills.magic.spell.attacks.SpellEffects
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player

/**
 * The Ancient Magicks combat spells: smoke, shadow, blood and ice; rush, burst, blitz and barrage.
 *
 * Every spell is a magic attack with the book's own casting animation, projectile and impact
 * graphics. Bursts and barrages are area spells: in a multi-combat area they also hit every
 * attackable npc standing next to the target, up to nine targets in all. The element decides the
 * effect of a hit that lands:
 * - **Smoke** poisons the target (2 damage from a rush or burst, 4 from a blitz or barrage).
 * - **Shadow** lowers the target's Attack (10% from a rush or burst, 15% from a blitz or barrage).
 * - **Blood** heals the caster for a quarter of the damage dealt.
 * - **Ice** freezes the target in place (5, 10, 15 or 20 seconds by tier).
 */
class AncientSpells @Inject constructor(private val npcSearch: NpcSearch) : SpellAttackMap {
    override fun SpellAttackRepository.register(manager: SpellAttackManager) {
        for (tier in Tier.entries) {
            for (element in Element.entries) {
                register(
                    spell = "obj.${element.spellObj(tier)}",
                    attack = AncientSpellAttack(manager, npcSearch, element, tier),
                )
            }
        }
    }

    private enum class Tier(
        val name0: String,
        val castAnim: String,
        val maxHitOffset: Int,
        val area: Boolean,
        val freezeTicks: Int,
        val poisonDamage: Int,
        val attackDrainPercent: Int,
    ) {
        Rush("rush", "seq.zaros_casting", 0, false, 8, 2, 10),
        Burst("burst", "seq.zaros_vertical_casting", 4, true, 16, 2, 10),
        Blitz("blitz", "seq.zaros_casting", 10, false, 25, 4, 15),
        Barrage("barrage", "seq.zaros_vertical_casting", 14, true, 33, 4, 15),
    }

    private enum class Element(val name0: String, val baseMaxHit: Int) {
        Smoke("smoke", 13),
        Shadow("shadow", 14),
        Blood("blood", 15),
        Ice("ice", 16);

        /** `50_smoke_rush`, `94_ice_barrage`: the spell obj's gameval name. */
        fun spellObj(tier: Tier): String {
            val level = SPELL_LEVELS.getValue(tier).getValue(this)
            return "${level}_${name0}_${tier.name0}"
        }

        fun maxHit(tier: Tier): Int = baseMaxHit + tier.maxHitOffset

        fun travel(tier: Tier): String? =
            when {
                // Blood and shadow area spells strike from above and have no projectile.
                this == Blood && tier.area -> null
                this == Shadow && tier.area -> null
                else -> "spotanim.${name0}_${tier.name0}_travel"
            }

        fun impact(tier: Tier): String = "spotanim.${name0}_${tier.name0}_impact"

        fun castSound(): String = "synth.${name0}_cast"

        fun hitSound(tier: Tier): String = "synth.${name0}_${tier.name0}_impact"
    }

    private class AncientSpellAttack(
        private val manager: SpellAttackManager,
        private val npcSearch: NpcSearch,
        private val element: Element,
        private val tier: Tier,
    ) : SpellAttack {
        override suspend fun ProtectedAccess.attack(target: Npc, attack: CombatAttack.Spell) {
            cast(target, attack)
        }

        override suspend fun ProtectedAccess.attack(target: Player, attack: CombatAttack.Spell) {
            cast(target, attack)
        }

        private fun ProtectedAccess.cast(target: PathingEntity, attack: CombatAttack.Spell) {
            val castResult = manager.attemptCast(this, attack)
            if (castResult.isFailure()) {
                return
            }
            player.anim(tier.castAnim, priority = 6)

            val travel = element.travel(tier)
            val clientDelay: Int
            val serverDelay: Int
            if (travel != null) {
                val proj = manager.spawnProjectile(this, target, travel, "projanim.magic_spell")
                clientDelay = proj.clientCycles
                serverDelay = proj.serverCycles
            } else {
                clientDelay = NO_PROJECTILE_CLIENT_DELAY
                serverDelay = NO_PROJECTILE_SERVER_DELAY
            }

            strike(target, attack, castResult, clientDelay, serverDelay, primary = true)

            if (tier.area && mapMultiway()) {
                val others =
                    npcSearch
                        .findAllAny(target.coords, distance = 1, vis = HuntVis.Off)
                        .filter { it != target && it.isValidTarget() && it.type.hasOp(2) }
                        .take(MAX_AREA_TARGETS - 1)
                for (npc in others) {
                    strike(npc, attack, castResult, clientDelay, serverDelay, primary = false)
                }
            }
            manager.continueCombatIfAutocast(this, target)
        }

        /** Rolls the spell against one target and applies the element's effect if it lands. */
        private fun ProtectedAccess.strike(
            target: PathingEntity,
            attack: CombatAttack.Spell,
            castResult: MagicRuneManager.CastResult,
            clientDelay: Int,
            serverDelay: Int,
            primary: Boolean,
        ) {
            val spell = attack.spell.obj
            val castSound = if (primary) element.castSound() else null
            val splash = manager.rollSplash(this, target, attack, castResult)
            if (splash) {
                manager.playSplashFx(this, target, clientDelay, castSound, soundRadius = 8)
                manager.queueSplashHit(this, target, spell, clientDelay, serverDelay)
                return
            }

            val damage = manager.rollMaxHit(this, target, attack, castResult, element.maxHit(tier))
            manager.playHitFx(
                source = this,
                target = target,
                clientDelay = clientDelay,
                castSound = castSound,
                soundRadius = 8,
                hitSpot = element.impact(tier),
                hitSpotHeight = IMPACT_HEIGHT,
                hitSound = element.hitSound(tier),
            )
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueMagicHit(this, target, spell, damage, clientDelay, serverDelay)
            applyEffect(target, damage)
        }

        private fun ProtectedAccess.applyEffect(target: PathingEntity, damage: Int) {
            when (element) {
                Element.Ice -> SpellEffects.freeze(target, tier.freezeTicks)
                Element.Blood -> {
                    val heal = damage / 4
                    if (heal > 0) {
                        player.statHeal("stat.hitpoints", constant = heal, percent = 0)
                    }
                }
                Element.Smoke ->
                    if (damage > 0 && target is Player) {
                        PlayerPoison.tryPoison(target, initialDamage = tier.poisonDamage)
                    }
                Element.Shadow ->
                    if (damage > 0) {
                        drainAttack(target, tier.attackDrainPercent)
                    }
            }
        }

        /** Shadow spells lower Attack by a percentage of the base level, and do not stack. */
        private fun drainAttack(target: PathingEntity, percent: Int) {
            when (target) {
                is Player -> {
                    if (target.stat(ATTACK) >= target.statBase(ATTACK)) {
                        target.statSub(ATTACK, constant = 0, percent = percent)
                    }
                }
                is Npc -> {
                    if (target.attackLvl >= target.baseAttackLvl) {
                        val drain = (target.baseAttackLvl * percent) / 100
                        target.attackLvl = max(0, target.attackLvl - drain)
                    }
                }
            }
        }
    }

    private companion object {
        private const val ATTACK = "stat.attack"
        private const val IMPACT_HEIGHT = 124
        private const val MAX_AREA_TARGETS = 9

        /** Blood and shadow bursts and barrages have no projectile: the impact lands a moment later. */
        private const val NO_PROJECTILE_CLIENT_DELAY = 30
        private const val NO_PROJECTILE_SERVER_DELAY = 2

        /** The Magic level in each spell's gameval name, by tier then element. */
        private val SPELL_LEVELS: Map<Tier, Map<Element, Int>> =
            mapOf(
                Tier.Rush to
                    mapOf(Element.Smoke to 50, Element.Shadow to 52, Element.Blood to 56, Element.Ice to 58),
                Tier.Burst to
                    mapOf(Element.Smoke to 62, Element.Shadow to 64, Element.Blood to 68, Element.Ice to 70),
                Tier.Blitz to
                    mapOf(Element.Smoke to 74, Element.Shadow to 76, Element.Blood to 80, Element.Ice to 82),
                Tier.Barrage to
                    mapOf(Element.Smoke to 86, Element.Shadow to 88, Element.Blood to 92, Element.Ice to 94),
            )
    }
}
