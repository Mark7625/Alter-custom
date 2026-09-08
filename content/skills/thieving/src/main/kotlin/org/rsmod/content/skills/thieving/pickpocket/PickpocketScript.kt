package org.rsmod.content.skills.thieving.pickpocket

import jakarta.inject.Inject
import org.rsmod.api.combat.commons.CombatEffects
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.thievingLvl
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc2
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpc4
import org.rsmod.api.script.onOpNpc5
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.thieving.Thieving
import org.rsmod.content.skills.thieving.Thieving.giveLoot
import org.rsmod.content.skills.thieving.Thieving.giveThievingXp
import org.rsmod.content.skills.thieving.equipment.ThievingEquipment
import org.rsmod.content.skills.thieving.pets.RockyPetManager
import org.rsmod.game.entity.Npc
import org.rsmod.game.hit.HitType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Pickpocketing. Every npc with a `Pickpocket` op in the cache is grouped under a
 * [PickpocketTarget]; a successful attempt hands out the target's loot (coins arrive as the
 * target's coin pouch), a failed one has the npc shout, deals the stun damage and holds the
 * player for [Thieving.STUN_CYCLES].
 *
 * Worn gear matters: gloves of silence and the Thieving cape raise the success chance, the dodgy
 * necklace can absorb a failure, and the rogue outfit can double the loot. See
 * [ThievingEquipment]. Every success also rolls for Rocky.
 */
class PickpocketScript
@Inject
constructor(
    private val xpMods: XpModifiers,
    private val objRepo: ObjRepository,
    private val equipment: ThievingEquipment,
    private val rocky: RockyPetManager,
) : PluginScript() {

    override fun ScriptContext.startup() {
        for (target in PickpocketTarget.entries) {
            for ((npc, op) in target.npcs) {
                when (op) {
                    1 -> onOpNpc1(npc) { pickpocket(it.npc, target) }
                    2 -> onOpNpc2(npc) { pickpocket(it.npc, target) }
                    3 -> onOpNpc3(npc) { pickpocket(it.npc, target) }
                    4 -> onOpNpc4(npc) { pickpocket(it.npc, target) }
                    5 -> onOpNpc5(npc) { pickpocket(it.npc, target) }
                }
            }
        }
        for ((pouch, target) in PickpocketTarget.byPouch) {
            onOpHeld1(pouch) { openPouches(target) }
        }
    }

    private suspend fun ProtectedAccess.pickpocket(npc: Npc, target: PickpocketTarget) {
        val name = npc.type.name.lowercase()
        if (player.thievingLvl < target.level) {
            mes("You need to be at least level ${target.level} Thieving to pick the $name's pocket.")
            return
        }
        if (Thieving.STUN_TIMER in player.timerMap) {
            mes("You're still stunned.")
            return
        }
        if (!hasRoomFor(target)) {
            mes("You don't have enough inventory space to steal anything.")
            return
        }

        arriveDelay()
        faceEntitySquare(npc)
        anim(Thieving.STEAL_ANIM)
        spam("You attempt to pick the $name's pocket.")
        delay(2)

        if (!npc.isSlotAssigned) {
            return
        }

        val chance = target.successChance(player.thievingLvl) * equipment.successMultiplier(player)
        if (random.of(maxExclusive = 256) < chance.toInt()) {
            succeed(npc, target, name)
        } else {
            fail(npc, target, name)
        }
    }

    private fun ProtectedAccess.succeed(npc: Npc, target: PickpocketTarget, name: String) {
        // Holding 28 pouches already: they burst open to make room for the next one.
        val pouch = target.pouch
        if (pouch != null && invTotal(inv, pouch) >= MAX_POUCHES) {
            spam("You can't carry any more coin pouches, so you open the ones you have.")
            openPouches(target)
        }

        val multiplier = if (equipment.rollRogueDoubleLoot(player, random)) 2 else 1
        giveLoot(objRepo, target.loot.roll(random), multiplier)
        giveThievingXp(xpMods, target.xp)
        soundSynth(Thieving.STEAL_SYNTH)
        spam("You pick the $name's pocket.")
        if (multiplier > 1) {
            spam("Your rogue outfit lets you steal twice as much!")
        }
        rocky.roll(this, target.petBase)
    }

    private fun ProtectedAccess.fail(npc: Npc, target: PickpocketTarget, name: String) {
        equipment.wearGlovesOfSilence(this)
        spam("You fail to pick the $name's pocket.")
        npc.facePlayer(player)
        npc.say(target.shout)

        if (equipment.dodgyNecklaceProtects(this)) {
            return
        }

        val damage = random.of(target.stunDamage)
        queueHit(source = npc, delay = 1, type = HitType.Typeless, damage = damage)
        spotanim(Thieving.STUN_SPOTANIM, height = STUN_SPOTANIM_HEIGHT)
        soundSynth(Thieving.STUN_SYNTH)
        CombatEffects.stun(player, Thieving.STUN_CYCLES)
        mes("You've been stunned!")
    }

    /** Coin pouches stack, so an existing stack counts as room; anything else needs a slot. */
    private fun ProtectedAccess.hasRoomFor(target: PickpocketTarget): Boolean {
        if (inv.hasFreeSpace()) {
            return true
        }
        val pouch = target.pouch ?: return false
        return target.loot.guaranteed.size == 1 && invTotal(inv, pouch) > 0
    }

    /** `Open-all` on a coin pouch: every pouch of that kind is emptied into a single coin stack. */
    private fun ProtectedAccess.openPouches(target: PickpocketTarget) {
        val pouch = target.pouch ?: return
        val count = invTotal(inv, pouch)
        if (count <= 0) {
            return
        }
        if (invDel(inv, pouch, count).failure) {
            return
        }
        var coins = 0
        repeat(count) { coins += random.of(target.coins) }
        invAddOrDrop(objRepo, "obj.coins", coins)
        val pouches = if (count == 1) "the coin pouch" else "all $count coin pouches"
        mes("You open $pouches and find $coins coins.")
    }

    private companion object {
        const val MAX_POUCHES: Int = 28
        const val STUN_SPOTANIM_HEIGHT: Int = 100
    }
}
