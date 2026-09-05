package org.rsmod.content.skills.magic.spell.attacks.lunar

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.manager.MagicRuneManager
import org.rsmod.api.combat.manager.MagicRuneManager.Companion.isFailure
import org.rsmod.api.npc.hit.modifier.NpcHitModifier
import org.rsmod.api.npc.hit.queueHit
import org.rsmod.api.player.events.PlayerHitEvents
import org.rsmod.api.player.hit.modifier.NoopPlayerHitModifier
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.api.script.onNpcTimer
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.api.spells.attack.SpellAttack
import org.rsmod.api.spells.attack.SpellAttackManager
import org.rsmod.api.spells.attack.SpellAttackMap
import org.rsmod.api.spells.attack.SpellAttackRepository
import org.rsmod.content.skills.magic.spell.attacks.NpcFreeze
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.hit.Hit
import org.rsmod.game.hit.HitType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Lunar spellbook's combat spell, Vengeance, and its Vengeance Other. A player armed with
 * vengeance returns three quarters of the next damage they take to whoever dealt it, shouting
 * "Taste vengeance!"; the spell can be cast again thirty seconds after the last cast.
 *
 * Vengeance is cast on the caster from the spellbook button; Vengeance Other is cast on another
 * player like an attack spell, arming them instead.
 */
class VengeanceScript
@Inject
constructor(
    private val spells: MagicSpellRegistry,
    private val runes: MagicRuneManager,
    private val npcList: NpcList,
    private val playerList: PlayerList,
) : PluginScript() {
    override fun ScriptContext.startup() {
        val vengeance =
            ServerCacheManager.getItem(VENGEANCE_OBJ.asRSCM(RSCMType.OBJ))?.let(spells::getObjSpell)
        if (vengeance != null) {
            onIfOverlayButton(vengeance.component) { castVengeance() }
        }
        onEvent<PlayerHitEvents.Impact> { onHit(player, hit) }
        onNpcTimer(NpcFreeze.TIMER) { NpcFreeze.release(npc) }
    }

    private suspend fun ProtectedAccess.castVengeance() {
        val spellObj = ServerCacheManager.getItem(VENGEANCE_OBJ.asRSCM(RSCMType.OBJ)) ?: return
        val spell = spells.getObjSpell(spellObj) ?: return
        if (!Vengeance.canCast(player, mapClock)) {
            mes("You can only cast vengeance spells every 30 seconds.")
            return
        }
        val result = runes.attemptCast(player, spell)
        if (result.isFailure()) {
            return
        }
        statAdvance("stat.magic", spell.castXp)
        anim(VENGEANCE_ANIM)
        spotanim(VENGEANCE_SPOT, height = VENGEANCE_SPOT_HEIGHT)
        Vengeance.arm(player, mapClock)
    }

    private fun onHit(player: Player, hit: Hit) {
        if (hit.damage <= 0 || !Vengeance.isArmed(player)) {
            return
        }
        if (hit.type == HitType.Typeless) {
            return
        }
        val reflected = (hit.damage * VENGEANCE_REFLECT_PERCENT) / 100
        if (reflected <= 0) {
            return
        }
        Vengeance.disarm(player)
        player.say("Taste vengeance!")
        when {
            hit.isFromNpc -> {
                val npc = hit.resolveNpcSource(npcList) ?: return
                npc.queueHit(
                    source = player,
                    delay = 1,
                    type = HitType.Typeless,
                    damage = reflected,
                    modifier = NpcHitModifier {},
                )
            }
            hit.isFromPlayer -> {
                val attacker = hit.resolvePlayerSource(playerList) ?: return
                attacker.queueHit(
                    source = player,
                    delay = 1,
                    type = HitType.Typeless,
                    damage = reflected,
                    modifier = NoopPlayerHitModifier,
                )
            }
        }
    }

    private companion object {
        private const val VENGEANCE_OBJ = "obj.94_vengeance"
        private const val VENGEANCE_ANIM = "seq.vengeance_spell_anim_nostalling"
        private const val VENGEANCE_SPOT = "spotanim.quest_lunar_spellbook_vengeance_spot_anim"
        private const val VENGEANCE_SPOT_HEIGHT = 0
        private const val VENGEANCE_REFLECT_PERCENT = 75
    }
}

/** Vengeance Other: arms the target player with vengeance, subject to their own cooldown. */
class VengeanceOtherSpell : SpellAttackMap {
    override fun SpellAttackRepository.register(manager: SpellAttackManager) {
        register("obj.93_vengeance_other", VengeanceOther(manager))
    }

    private class VengeanceOther(private val manager: SpellAttackManager) : SpellAttack {
        override suspend fun ProtectedAccess.attack(target: Npc, attack: CombatAttack.Spell) {
            manager.stopCombat(this)
            mes("You can only cast that spell on another player.")
        }

        override suspend fun ProtectedAccess.attack(target: Player, attack: CombatAttack.Spell) {
            manager.stopCombat(this)
            if (!Vengeance.canCast(target, mapClock)) {
                mes("That player has cast a vengeance spell too recently.")
                return
            }
            val castResult = manager.attemptCast(this, attack)
            if (castResult.isFailure()) {
                return
            }
            anim("seq.vengeance_spell_anim_nostalling")
            target.spotanim("spotanim.quest_lunar_spellbook_vengeance_other_spot_anim")
            Vengeance.arm(target, mapClock)
            target.mes("${player.displayName} has cast Vengeance on you.")
        }
    }
}

internal object Vengeance {
    private val ARMED = AttributeKey<Boolean>()
    private val LAST_CAST_TICK = AttributeKey<Int>()
    private const val COOLDOWN_TICKS = 50

    fun canCast(player: Player, mapClock: Int): Boolean {
        val last = player.attr[LAST_CAST_TICK] ?: return true
        return mapClock - last >= COOLDOWN_TICKS
    }

    fun arm(player: Player, mapClock: Int) {
        player.attr[ARMED] = true
        player.attr[LAST_CAST_TICK] = mapClock
    }

    fun isArmed(player: Player): Boolean = player.attr[ARMED] == true

    fun disarm(player: Player) {
        player.attr.remove(ARMED)
    }
}
