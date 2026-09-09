package org.rsmod.content.skills.magic.spell.attacks

import kotlin.math.max
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.CombatEffects
import org.rsmod.api.combat.commons.npc.queueCombatRetaliate
import org.rsmod.api.combat.commons.player.queueCombatRetaliate
import org.rsmod.api.combat.manager.MagicRuneManager.Companion.isFailure
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.magicLvl
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.spells.attack.SpellAttack
import org.rsmod.api.spells.attack.SpellAttackManager
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.type.getOrNull

/**
 * A single-target spell that rolls magic accuracy, plays the book's graphics and sounds, and
 * applies an effect when it lands. Covers everything from a curse (no damage, only the effect) to
 * a god spell (damage and an effect).
 *
 * @param baseMaxHit the max hit at the caster's Magic level, or `0` for a spell that deals no
 *   damage. A damaging spell shows a hitsplat; a curse shows only its impact graphic.
 * @param targetCheck rejects targets the spell does not affect (Crumble Undead on the living, a
 *   demonbane on a goblin); the returned message is shown and the cast is cancelled.
 * @param onLand runs when the spell lands, with the damage dealt (`0` for a curse).
 */
class EffectSpellAttack(
    private val manager: SpellAttackManager,
    private val staffAnim: String,
    private val unarmedAnim: String = staffAnim,
    private val launch: String?,
    private val travel: String?,
    private val projanim: String = "projanim.magic_spell",
    private val impact: String?,
    private val impactHeight: Int = 124,
    private val castSound: String?,
    private val hitSound: String?,
    private val baseMaxHit: (magicLvl: Int) -> Int = { 0 },
    private val targetCheck: (PathingEntity) -> String? = { null },
    private val onLand: ProtectedAccess.(target: PathingEntity, damage: Int) -> Unit = { _, _ -> },
) : SpellAttack {
    override suspend fun ProtectedAccess.attack(target: Npc, attack: CombatAttack.Spell) {
        cast(target, attack)
    }

    override suspend fun ProtectedAccess.attack(target: Player, attack: CombatAttack.Spell) {
        cast(target, attack)
    }

    private fun ProtectedAccess.cast(target: PathingEntity, attack: CombatAttack.Spell) {
        val rejection = targetCheck(target)
        if (rejection != null) {
            manager.stopCombat(this)
            mes(rejection)
            return
        }
        val castResult = manager.attemptCast(this, attack)
        if (castResult.isFailure()) {
            return
        }
        val weaponType = getOrNull(attack.weapon)
        val castAnim =
            if (weaponType != null && weaponType.isCategoryType("category.staff")) staffAnim
            else unarmedAnim
        player.anim(castAnim, priority = 6)
        launch?.let { spotanim(it, height = LAUNCH_HEIGHT) }

        val clientDelay: Int
        val serverDelay: Int
        if (travel != null) {
            val proj = manager.spawnProjectile(this, target, travel, projanim)
            clientDelay = proj.clientCycles
            serverDelay = proj.serverCycles
        } else {
            clientDelay = NO_PROJECTILE_CLIENT_DELAY
            serverDelay = NO_PROJECTILE_SERVER_DELAY
        }
        val spell = attack.spell.obj

        val splash = manager.rollSplash(this, target, attack, castResult)
        if (splash) {
            manager.playSplashFx(this, target, clientDelay, castSound, soundRadius = SOUND_RADIUS)
            manager.queueSplashHit(this, target, spell, clientDelay, serverDelay)
            manager.continueCombatIfAutocast(this, target)
            return
        }

        val maxHit = baseMaxHit(player.magicLvl)
        val damage = if (maxHit > 0) manager.rollMaxHit(this, target, attack, castResult, maxHit) else 0
        manager.playHitFx(
            source = this,
            target = target,
            clientDelay = clientDelay,
            castSound = castSound,
            soundRadius = SOUND_RADIUS,
            hitSpot = impact,
            hitSpotHeight = impactHeight,
            hitSound = hitSound,
        )
        if (maxHit > 0) {
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueMagicHit(this, target, spell, damage, clientDelay, serverDelay)
        } else {
            // No hitsplat for a curse, but the target still turns on the caster.
            retaliate(target, serverDelay)
        }
        onLand(this, target, damage)
        manager.continueCombatIfAutocast(this, target)
    }

    private fun ProtectedAccess.retaliate(target: PathingEntity, delay: Int) {
        when (target) {
            is Npc -> target.queueCombatRetaliate(player, delay = max(1, delay))
            is Player -> target.queueCombatRetaliate(player, delay = max(1, delay))
        }
    }

    companion object {
        const val LAUNCH_HEIGHT: Int = 92
        const val SOUND_RADIUS: Int = 8
        private const val NO_PROJECTILE_CLIENT_DELAY = 30
        private const val NO_PROJECTILE_SERVER_DELAY = 2
    }
}

/** Effects shared by the curse, bind, god and Arceuus spells. */
object SpellEffects {
    const val ATTACK: String = "stat.attack"
    const val STRENGTH: String = "stat.strength"
    const val DEFENCE: String = "stat.defence"
    const val MAGIC: String = "stat.magic"
    const val PRAYER: String = "stat.prayer"

    /**
     * Lowers [stat] by [percent] of the target's base level, only when the stat is still at (or
     * above) its base: curses do not stack.
     */
    fun drainPercent(target: PathingEntity, stat: String, percent: Int) {
        when (target) {
            is Player -> {
                if (target.stat(stat) >= target.statBase(stat)) {
                    target.statSub(stat, constant = 0, percent = percent)
                }
            }
            is Npc -> {
                val base = npcBase(target, stat)
                if (npcCurrent(target, stat) >= base) {
                    setNpcCurrent(target, stat, max(0, npcCurrent(target, stat) - (base * percent) / 100))
                }
            }
        }
    }

    /** Lowers [stat] by a flat [amount]; used by Saradomin Strike's prayer drain. */
    fun drainFlat(target: PathingEntity, stat: String, amount: Int) {
        when (target) {
            is Player -> target.statSub(stat, constant = amount, percent = 0)
            is Npc -> setNpcCurrent(target, stat, max(0, npcCurrent(target, stat) - amount))
        }
    }

    /** Holds the target in place: players via the combat freeze, npcs via their movement lock. */
    fun freeze(target: PathingEntity, ticks: Int) {
        when (target) {
            is Player -> CombatEffects.freeze(target, ticks)
            is Npc -> NpcFreeze.freeze(target, ticks)
        }
    }

    fun isUndead(npc: Npc): Boolean = UNDEAD.containsMatchIn(npc.type.name.lowercase())

    fun isDemon(npc: Npc): Boolean = DEMON.containsMatchIn(npc.type.name.lowercase())

    private val UNDEAD =
        Regex("skeleton|zombie|ghost|ghast|shade|mummy|revenant|ankou|banshee|wight|undead|zogre|skogre|spectre|lich|crawling hand|bloodveld")
    private val DEMON =
        Regex("demon|imp\\b|nechryael|bloodveld|abyssal|tormented|balfrug|zakl|k'ril|tstanon|hellhound|jungle horror|cerberus|skotizo|porazdir|derwen|duke sucellus")

    private fun npcCurrent(npc: Npc, stat: String): Int =
        when (stat) {
            ATTACK -> npc.attackLvl
            STRENGTH -> npc.strengthLvl
            DEFENCE -> npc.defenceLvl
            MAGIC -> npc.magicLvl
            else -> 0
        }

    private fun npcBase(npc: Npc, stat: String): Int =
        when (stat) {
            ATTACK -> npc.baseAttackLvl
            STRENGTH -> npc.baseStrengthLvl
            DEFENCE -> npc.baseDefenceLvl
            MAGIC -> npc.baseMagicLvl
            else -> 0
        }

    private fun setNpcCurrent(npc: Npc, stat: String, value: Int) {
        when (stat) {
            ATTACK -> npc.attackLvl = value
            STRENGTH -> npc.strengthLvl = value
            DEFENCE -> npc.defenceLvl = value
            MAGIC -> npc.magicLvl = value
        }
    }
}

/**
 * Npcs have no freeze mechanic of their own; a frozen npc has its movement locked until the
 * combat freeze timer, handled in [SpellEffectsScript], releases it.
 */
object NpcFreeze {
    const val TIMER: String = "timer.combat_freeze"

    fun freeze(npc: Npc, ticks: Int) {
        if (npc.movementLocked) {
            return
        }
        npc.movementLocked = true
        npc.timer(TIMER, ticks)
    }

    fun release(npc: Npc) {
        npc.movementLocked = false
        npc.clearTimer(TIMER)
    }
}
