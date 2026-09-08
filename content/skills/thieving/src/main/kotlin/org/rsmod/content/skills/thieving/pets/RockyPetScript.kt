package org.rsmod.content.skills.thieving.pets

import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onOpHeld5
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpcU
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Rocky, the Thieving skill pet. Dropping the held pet releases it as a follower, `Pick-up`
 * returns it to the inventory, `Talk-to` chats, and using berries on it changes its form: white
 * berries for the raccoon, redberries for Red the red panda, poison ivy berries for Ziggy the
 * tanuki.
 */
class RockyPetScript @Inject constructor(private val rocky: RockyPetManager) : PluginScript() {

    override fun ScriptContext.startup() {
        onPlayerLogin { rocky.followingPet(player)?.let { rocky.spawn(player, it) } }
        onPlayerLogout { rocky.despawn(player) }
        onPlayerSoftTimer(RockyPetManager.TICK_TIMER) { rocky.tick(player) }

        for (pet in RockyPet.entries) {
            onOpHeld5(pet.obj) { release(pet, it.slot) }
            onOpNpc1(pet.npc) { talkTo(it.npc, pet) }
            onOpNpc3(pet.npc) { pickUp(it.npc, pet) }
            onOpNpcU(pet.npc) { useItem(it.npc, pet, it.objType.internalName, it.invSlot) }
        }

        onCommand("rocky") {
            desc = "Spawn Rocky, the Thieving pet, into your inventory"
            requiredRights = Rights.ADMINISTRATOR
            cheat {
                if (player.inv.isFull()) {
                    player.mes("You don't have enough inventory space.")
                    return@cheat
                }
                player.invAdd(player.inv, RockyPet.RACCOON.obj, count = 1)
                player.mes("Spawned Rocky. Drop it to have it follow you.")
            }
        }
    }

    private suspend fun ProtectedAccess.release(pet: RockyPet, slot: Int) {
        if (rocky.hasAnyFollower(player)) {
            mes("You already have a follower.")
            return
        }
        if (invDel(inv, pet.obj, count = 1, slot = slot).failure) {
            return
        }
        rocky.spawn(player, pet)
        anim("seq.human_pickupfloor")
        mes("You put down ${pet.petName} and it starts to follow you.")
    }

    private suspend fun ProtectedAccess.pickUp(npc: Npc, pet: RockyPet) {
        if (!rocky.isOwnedBy(npc, player)) {
            return
        }
        arriveDelay()
        if (inv.isFull()) {
            mes("You don't have enough inventory space to pick up ${pet.petName}.")
            return
        }
        faceEntitySquare(npc)
        anim("seq.human_pickupfloor")
        if (invAdd(inv, pet.obj, count = 1).failure) {
            return
        }
        rocky.release(player)
        mes("You pick up ${pet.petName}.")
    }

    private suspend fun ProtectedAccess.useItem(npc: Npc, current: RockyPet, obj: String, invSlot: Int) {
        if (!rocky.isOwnedBy(npc, player)) {
            return
        }
        val target = RockyPet.fromBerries(obj)
        if (target == null) {
            mes("${current.petName} doesn't seem interested in that.")
            return
        }
        arriveDelay()
        if (target == current) {
            mes("${current.petName} is already in that form.")
            return
        }
        if (invDel(inv, obj, count = 1, slot = invSlot).failure) {
            return
        }
        faceEntitySquare(npc)
        anim("seq.human_pickupfloor")
        rocky.transform(player, target)
        mes("${current.petName} gobbles up the berries and transforms into ${target.petName}!")
    }

    private suspend fun ProtectedAccess.talkTo(npc: Npc, pet: RockyPet) {
        if (!rocky.isOwnedBy(npc, player)) {
            return
        }
        arriveDelay()
        startDialogue(npc) {
            when (pet) {
                RockyPet.RACCOON -> raccoonChat(random.of(maxExclusive = 4))
                RockyPet.RED_PANDA -> redPandaChat(random.of(maxExclusive = 3))
                RockyPet.TANUKI -> tanukiChat(random.of(maxExclusive = 3))
            }
        }
    }

    private suspend fun Dialogue.raccoonChat(variant: Int) {
        when (variant) {
            0 -> {
                chatPlayer(happy, "*whistles*")
                chatNpc(shifty, "What are you doing?")
                chatPlayer(happy, "Nothing...")
                chatNpc(angry, "Then why is your hand in my pocket?")
                chatPlayer(sad, "Sorry, force of habit.")
            }
            1 -> {
                chatPlayer(quiz, "Do you steal shiny things like magpies do?")
                chatNpc(happy, "Magpies? Amateurs. They take anything that glitters.")
                chatNpc(happy, "A raccoon only takes what's valuable. And what isn't nailed down.")
            }
            2 -> {
                chatPlayer(quiz, "Fancy helping me rob the bank?")
                chatNpc(neutral, "No thanks. I've gone straight.")
                chatNpc(happy, "Besides, Rodney the banker is rather charming...")
                chatPlayer(confused, "Rodney?")
            }
            else -> {
                chatNpc(shifty, "Hey, is that your coin on the floor?")
                chatPlayer(confused, "Where? I don't see any... hey! Where did my coin go?")
                chatNpc(laugh, "Relax, here it is. Watch your pockets!")
            }
        }
    }

    private suspend fun Dialogue.redPandaChat(variant: Int) {
        when (variant) {
            0 -> {
                chatPlayer(quiz, "Aren't pandas supposed to be black and white?")
                chatNpc(angry, "Pandas are red. Red! Whoever heard of a blue panda?")
                chatPlayer(confused, "I didn't say blue...")
            }
            1 -> {
                chatNpc(happy, "Did you see my cartwheel?")
                chatPlayer(confused, "No?")
                chatNpc(happy, "Exactly. It's invisible. Faster than the eye can see.")
            }
            else -> {
                chatPlayer(quiz, "Do you know a raccoon called Dufresne?")
                chatNpc(shifty, "Never heard of him. Why would a panda know a raccoon?")
                chatNpc(shifty, "...Did he mention the tunnel?")
            }
        }
    }

    private suspend fun Dialogue.tanukiChat(variant: Int) {
        when (variant) {
            0 -> {
                chatPlayer(quiz, "Are you related to Rocky?")
                chatNpc(neutral, "Rocky? Never heard of him. I'm a tanuki.")
                chatNpc(shifty, "We are masters of disguise, you know.")
            }
            1 -> {
                chatPlayer(quiz, "Do you know any plumbers?")
                chatNpc(confused, "Why would I know any plumbers? I follow you around all day.")
            }
            else -> {
                chatPlayer(happy, "I've got a treat for you! Guess which hand.")
                chatNpc(happy, "That one!")
                chatPlayer(laugh, "Nope, it was empty. Both of them were.")
                chatNpc(angry, "Rrowl! Hiss!")
            }
        }
    }
}
