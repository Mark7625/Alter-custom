package org.rsmod.content.quest.area.varrock.gertrudescat.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.content.quest.area.varrock.gertrudescat.GertrudesCatQuest
import org.rsmod.content.quest.area.varrock.gertrudescat.GertrudesCatQuest.Companion.KITTEN_PRICE
import org.rsmod.content.quest.area.varrock.gertrudescat.GertrudesCatQuest.Companion.STAGE_GAVE_MILK
import org.rsmod.content.quest.area.varrock.gertrudescat.GertrudesCatQuest.Companion.STAGE_GAVE_SARDINE
import org.rsmod.content.quest.area.varrock.gertrudescat.GertrudesCatQuest.Companion.STAGE_KITTEN_RETURNED
import org.rsmod.content.quest.area.varrock.gertrudescat.GertrudesCatQuest.Companion.STAGE_PAID_KIDS
import org.rsmod.content.quest.area.varrock.gertrudescat.GertrudesCatQuest.Companion.STAGE_STARTED
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Gertrude, in her house west of Varrock. The cache swaps her between a quest form and a
 * post-quest form (with a "Kitten" option) on the quest varp, so both forms are handled here.
 */
class Gertrude @Inject constructor(private val gertrudesCat: GertrudesCatQuest) : PluginScript() {

    private val quest
        get() = gertrudesCat.quest

    override fun ScriptContext.startup() {
        // Ops on a multi-npc are dispatched with the *base* npc's id even though the client shows
        // the transformed form's options, so the hooks go on the base type. The "Kitten" option
        // only exists on the post-quest form, so op3 never arrives before completion.
        onOpNpc1(GERTRUDE) { startDialogue(it.npc) { gertrude(it.npc) } }
        onOpNpc3(GERTRUDE) { startDialogue(it.npc) { buyKitten() } }
    }

    private suspend fun Dialogue.gertrude(npc: Npc) {
        when (quest.getQuestStage(player)) {
            0 -> beforeQuest()
            STAGE_STARTED -> lookingForShilop()
            STAGE_PAID_KIDS -> foundShilop()
            STAGE_GAVE_MILK -> sardineAdvice()
            STAGE_GAVE_SARDINE -> afterSardine()
            STAGE_KITTEN_RETURNED -> finishQuest()
            else -> afterQuest()
        }
    }

    private suspend fun Dialogue.beforeQuest() {
        chatPlayer(quiz, "Hello, are you okay?")
        chatNpc(angry, "Do I look okay? Those kids drive me crazy.")
        chatNpc(sad, "I'm sorry. It's just that I've lost her.")
        chatPlayer(quiz, "Lost who?")
        chatNpc(sad, "Fluffs. Poor Fluffs. She never hurt anyone.")
        chatPlayer(quiz, "Who's Fluffs?")
        chatNpc(sad, "My beloved feline friend, Fluffs. She's been purring by my side for almost a decade. Please, could you go and search for her while I look after the kids?")
        when (
            choice2(
                "Yes.", 1,
                "No.", 2,
                title = "Help Gertrude find Fluffs?",
            )
        ) {
            1 -> {
                chatPlayer(happy, "Well, I suppose I could.")
                chatNpc(happy, "Really? Thank you so much! I really have no idea where she could be!")
                chatNpc(neutral, "I think my sons, Shilop and Wilough, saw the cat last. They'll be out in the market place.")
                chatPlayer(neutral, "Alright then, I'll see what I can do.")
                quest.advanceQuestStage(access)
            }
            2 -> {
                chatPlayer(neutral, "Sorry, I'm too busy to play pet rescue.")
                chatNpc(sad, "Well, okay then. I'll have to find someone else.")
            }
        }
    }

    private suspend fun Dialogue.lookingForShilop() {
        if (gertrudesCat.foundFluffs.get(player)) {
            sardineAdvice()
            return
        }
        chatPlayer(happy, "Hello Gertrude.")
        chatNpc(quiz, "Have you seen my poor Fluffs?")
        chatPlayer(sad, "I'm afraid not.")
        chatNpc(quiz, "What about Shilop?")
        chatPlayer(neutral, "No sign of him either.")
        chatNpc(confused, "Hmmm... strange. He should be at the market.")
    }

    private suspend fun Dialogue.foundShilop() {
        if (gertrudesCat.foundFluffs.get(player)) {
            sardineAdvice()
            return
        }
        chatPlayer(happy, "Hello Gertrude.")
        chatNpc(quiz, "Hello again. Did you manage to find Shilop? I can't keep an eye on him for the life of me.")
        chatPlayer(neutral, "He does seem quite a handful.")
        chatNpc(neutral, "You have no idea! Did he help at all?")
        chatPlayer(neutral, "I think so. I'm just going to look now.")
        chatNpc(happy, "Thanks again, adventurer.")
    }

    private suspend fun Dialogue.sardineAdvice() {
        chatPlayer(happy, "Hello again.")
        chatNpc(quiz, "Hello. How's it going? Any luck?")
        chatPlayer(happy, "Yes, I've found Fluffs!")
        chatNpc(happy, "Well, well, you are clever! Did you bring her back?")
        chatPlayer(worried, "Well, that's the thing. She refuses to leave.")
        chatNpc(worried, "Oh dear, oh dear! Maybe she's just hungry. She loves doogle sardines, but I'm all out.")
        chatPlayer(quiz, "Doogle sardines?")
        chatNpc(neutral, "Yes, raw sardines seasoned with doogle leaves. Unfortunately I've used all my doogle leaves, but you may find some in the woods out the back.")
        gertrudesCat.toldSardines.set(player, true)
    }

    private suspend fun Dialogue.afterSardine() {
        chatPlayer(happy, "Hi!")
        chatNpc(quiz, "Hey, traveller. Did Fluffs eat the sardines?")
        chatPlayer(neutral, "Yeah, she loved them, but she still won't leave.")
        chatNpc(confused, "Well, that is strange. There must be a reason.")
    }

    private suspend fun Dialogue.finishQuest() {
        chatPlayer(happy, "Hello Gertrude. Fluffs ran off with her kitten.")
        chatNpc(happy, "You're back! Thank you! Thank you! Fluffs just came home! I think she was just upset because she couldn't find her kitten.")
        if (player.inv.freeSpace() < REWARD_SLOTS) {
            chatNpc(neutral, "I've got something for you, but you'll need $REWARD_SLOTS free spaces in your pack first. Come straight back!")
            return
        }
        mesbox("Gertrude gives you a hug.")
        chatNpc(happy, "If you hadn't found her kitten it would have died out there!")
        chatPlayer(happy, "That's okay, I like to do my bit.")
        chatNpc(neutral, "I don't know how to thank you. I have no real material possessions. I do have kittens, though! I can only really look after one.")
        chatPlayer(happy, "Well, if it needs a home...")
        chatNpc(neutral, "I would sell it to my cousin in West Ardougne. I hear there's a rat epidemic there. But it's too far.")
        chatNpc(happy, "Here you go. Look after her, and thank you again!")
        chatNpc(neutral, "Oh, by the way: the kitten can live in your backpack, but to make it grow you must take it out and feed and stroke it often.")

        val kitten = gertrudesCat.randomKitten()
        access.invAdd(access.inv, kitten.obj)
        access.soundSynth("synth.kittens_mew")
        objbox(kitten.obj, "Gertrude gives you a kitten.")
        mesbox("...and some food!")
        quest.advanceQuestStage(access)
    }

    private suspend fun Dialogue.afterQuest() {
        chatPlayer(happy, "Hello Gertrude.")
        chatNpc(happy, "Hello, dear! Fluffs is curled up by the fire, thanks to you.")
        when (
            choice2(
                "How is the kitten doing?", 1,
                "Could I have another kitten?", 2,
            )
        ) {
            1 -> {
                chatPlayer(quiz, "How is the kitten doing?")
                if (with(gertrudesCat) { access.hasAnyCat() }) {
                    chatNpc(happy, "You'd know better than me! Just keep feeding it and giving it plenty of attention, and it'll grow up big and strong.")
                } else {
                    chatNpc(worried, "Don't tell me you've lost it already? If you need another one, I can spare a kitten for $KITTEN_PRICE coins.")
                }
            }
            2 -> buyKitten()
        }
    }

    /** The "Kitten" option on post-quest Gertrude: one kitten at a time, for a small fee. */
    private suspend fun Dialogue.buyKitten() {
        chatPlayer(quiz, "Could I have another kitten?")
        if (with(gertrudesCat) { access.hasAnyCat() }) {
            chatNpc(neutral, "You've already got a cat to look after! One is quite enough for anyone. Come back if you ever lose it.")
            return
        }
        chatNpc(neutral, "I've got a few kittens that need a home. I can let you have one for $KITTEN_PRICE coins, to cover the food it's eaten.")
        val buy =
            choice2(
                "Yes, I'll pay $KITTEN_PRICE coins.", true,
                "No thanks.", false,
                title = "Buy a kitten for $KITTEN_PRICE coins?",
            )
        if (!buy) {
            chatPlayer(neutral, "No thanks, not right now.")
            return
        }
        if (player.inv.freeSpace() < 1) {
            chatNpc(neutral, "You'll need a free space in your pack to carry it.")
            return
        }
        if (!access.invTakeFee(KITTEN_PRICE)) {
            chatPlayer(sad, "I don't have $KITTEN_PRICE coins on me.")
            chatNpc(neutral, "Then come back when you do, dear.")
            return
        }
        val kitten = gertrudesCat.randomKitten()
        access.invAdd(access.inv, kitten.obj)
        access.soundSynth("synth.kittens_mew")
        objbox(kitten.obj, "Gertrude hands you a kitten.")
        chatNpc(happy, "Take good care of her. Feed her and stroke her often, or she'll run away!")
    }

    private companion object {
        /** The base multi-npc; the cache swaps its form on `varp.fluffs`. */
        const val GERTRUDE = "npc.gertrude"

        /** Kitten, chocolate cake and stew. */
        const val REWARD_SLOTS = 3
    }
}
