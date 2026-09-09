package org.rsmod.content.quest.area.lumbridge.restlessghost.npcs

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.quest.area.lumbridge.restlessghost.RestlessGhostQuest
import org.rsmod.content.quest.area.lumbridge.restlessghost.RestlessGhostQuest.Companion.GHOST_SKULL
import org.rsmod.content.quest.area.lumbridge.restlessghost.RestlessGhostQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.lumbridge.restlessghost.RestlessGhostQuest.Companion.STAGE_GOT_SKULL
import org.rsmod.content.quest.area.lumbridge.restlessghost.RestlessGhostQuest.Companion.STAGE_SPOKE_TO_GHOST
import org.rsmod.content.quest.area.lumbridge.restlessghost.RestlessGhostQuest.Companion.STAGE_STARTED
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Restless ghost, drifting about the little building in the south-east corner of the
 * Lumbridge graveyard. Only an Amulet of Ghostspeak makes sense of him.
 */
class RestlessGhost @Inject constructor(private val restlessGhost: RestlessGhostQuest) : PluginScript() {

    private val quest
        get() = restlessGhost.quest

    private val skullId = GHOST_SKULL.asRSCM(RSCMType.OBJ)

    override fun ScriptContext.startup() {
        onOpNpc1(GHOST) { startDialogue(it.npc) { ghost(it.npc) } }
        onOpNpcU(GHOST) { useItem(it.npc, it.objType.id) }
    }

    private suspend fun ProtectedAccess.useItem(npc: Npc, objId: Int) {
        arriveDelay()
        faceEntitySquare(npc)
        if (objId != skullId) {
            mes("The ghost doesn't seem interested in that.")
            return
        }
        mesbox("I can't give it to him. It goes right through him.")
        if (restlessGhost.isWearingAmulet(player)) {
            startDialogue(npc) {
                chatNpc(neutral, "If you just put it in my coffin, that should do the trick...")
            }
        } else {
            npc.say("Woooo woo!")
        }
    }

    private suspend fun Dialogue.ghost(npc: Npc) {
        chatPlayer(happy, "Hello ghost, how are you?")
        if (!restlessGhost.isWearingAmulet(player) || quest.getQuestStage(player) < STAGE_STARTED) {
            wooing()
            return
        }
        when (quest.getQuestStage(player)) {
            in STAGE_STARTED until STAGE_SPOKE_TO_GHOST -> firstConversation()
            STAGE_SPOKE_TO_GHOST, STAGE_GOT_SKULL -> skullHunt()
            else -> {
                chatNpc(happy, "Thank you again, stranger. At last I can rest.")
                chatPlayer(happy, "Sleep well.")
            }
        }
    }

    /** What the ghost sounds like to anyone without the amulet. */
    private suspend fun Dialogue.wooing() {
        chatNpc(neutral, "Wooo wooo wooooo!")
        when (
            choice3(
                "Sorry, I don't speak ghost.", 1,
                "Ooh, THAT'S interesting.", 2,
                "Any hints where I can find some treasure?", 3,
            )
        ) {
            1 -> dontSpeakGhost()
            2 -> {
                chatPlayer(happy, "Ooh, THAT'S interesting.")
                chatNpc(neutral, "Woo wooo. Woooooooooooooooooo!")
                when (
                    choice2(
                        "Did he really?", 1,
                        "Yeah, that's what I thought.", 2,
                    )
                ) {
                    1 -> {
                        chatPlayer(quiz, "Did he really?")
                        chatNpc(neutral, "Woo.")
                        when (
                            choice2(
                                "My brother had EXACTLY the same problem.", 1,
                                "Goodbye. Thanks for the chat.", 2,
                            )
                        ) {
                            1 -> {
                                chatPlayer(neutral, "My brother had EXACTLY the same problem.")
                                chatNpc(neutral, "Woo Wooooo!")
                                chatNpc(neutral, "Wooooo Woo woo woo!")
                                when (
                                    choice2(
                                        "You'll have to give me the recipe some time...", 1,
                                        "Goodbye. Thanks for the chat.", 2,
                                    )
                                ) {
                                    1 -> {
                                        chatPlayer(happy, "You'll have to give me the recipe some time...")
                                        chatNpc(neutral, "Wooooooo woo woooooooo.")
                                        notSoSure()
                                    }
                                    2 -> goodbye()
                                }
                            }
                            2 -> goodbye()
                        }
                    }
                    2 -> {
                        chatPlayer(neutral, "Yeah, that's what I thought.")
                        chatNpc(neutral, "Wooo woooooooooooooo...")
                        when (
                            choice2(
                                "Goodbye. Thanks for the chat.", 1,
                                "Hmm, I'm not so sure about that.", 2,
                            )
                        ) {
                            1 -> goodbye()
                            2 -> notSoSure()
                        }
                    }
                }
            }
            3 -> {
                chatPlayer(quiz, "Any hints where I can find some treasure?")
                chatNpc(neutral, "Wooooooo woo! Wooooo woo wooooo woowoowoo woo Woo wooo. Wooooo woo woo? Woooooooooooooooooo!")
                when (
                    choice2(
                        "Sorry, I don't speak ghost.", 1,
                        "Thank you. You've been very helpful.", 2,
                    )
                ) {
                    1 -> dontSpeakGhost()
                    2 -> {
                        chatPlayer(happy, "Thank you. You've been very helpful.")
                        chatNpc(neutral, "Wooooooo.")
                    }
                }
            }
        }
    }

    private suspend fun Dialogue.dontSpeakGhost() {
        chatPlayer(neutral, "Sorry, I don't speak ghost.")
        chatNpc(neutral, "Woo woo?")
        chatPlayer(confused, "Nope, still don't understand you.")
        chatNpc(angry, "WOOOOOOOOO!")
        chatPlayer(neutral, "Never mind.")
    }

    private suspend fun Dialogue.notSoSure() {
        chatPlayer(quiz, "Hmm... I'm not so sure about that.")
        chatNpc(neutral, "Wooo woo?")
        chatPlayer(neutral, "Well, if you INSIST.")
        chatNpc(happy, "Wooooooooo!")
        chatPlayer(neutral, "Ah well, better be off now...")
        chatNpc(neutral, "Woo.")
        chatPlayer(neutral, "Bye.")
    }

    private suspend fun Dialogue.goodbye() {
        chatPlayer(happy, "Goodbye. Thanks for the chat.")
        chatNpc(quiz, "Wooo wooo?")
    }

    /** The first time the ghost is understood: he explains about the stolen skull. */
    private suspend fun Dialogue.firstConversation() {
        chatNpc(sad, "Not very good, actually.")
        chatPlayer(quiz, "What's the problem then?")
        chatNpc(shocked, "Did you just understand what I said???")
        when (
            choice3(
                "Yep, now tell me what the problem is.", 1,
                "No, you sound like you're speaking nonsense to me.", 2,
                "Wow, this amulet works!", 3,
            )
        ) {
            1 -> {
                chatPlayer(neutral, "Yep, now tell me what the problem is.")
                chatNpc(happy, "WOW! This is INCREDIBLE! I didn't expect anyone to ever understand me again!")
                chatPlayer(neutral, "Okay, okay, I can understand you! But have you any idea WHY you're doomed to be a ghost?")
                explainSkull()
            }
            2 -> {
                chatPlayer(neutral, "No, you sound like you're speaking nonsense to me.")
                chatNpc(sad, "Oh, that's a pity. You got my hopes up there.")
                chatPlayer(neutral, "Yeah, it is a pity. Sorry about that.")
                chatNpc(shocked, "Hang on a second... you CAN understand me!")
                when (
                    choice2(
                        "No I can't.", 1,
                        "Yep, clever aren't I?", 2,
                    )
                ) {
                    1 -> {
                        chatPlayer(neutral, "No I can't.")
                        chatNpc(bored, "Great. The first person I can speak to in ages... and they're a moron.")
                    }
                    2 -> {
                        chatPlayer(happy, "Yep, clever aren't I?")
                        chatNpc(happy, "I'm impressed. You must be very powerful. I don't suppose you can stop me being a ghost?")
                        offerHelp()
                    }
                }
            }
            3 -> {
                chatPlayer(happy, "Wow, this amulet works!")
                chatNpc(happy, "Oh! It's your amulet that's doing it! I did wonder. I don't suppose you can help me? I don't like being a ghost.")
                offerHelp()
            }
        }
    }

    private suspend fun Dialogue.offerHelp() {
        when (
            choice2(
                "Yes, okay. Do you know WHY you're a ghost?", 1,
                "No, you're scary!", 2,
            )
        ) {
            1 -> {
                chatPlayer(quiz, "Yes, okay. Do you know WHY you're a ghost?")
                chatNpc(sad, "Nope. I just know I can't do much of anything like this!")
                explainSkull()
            }
            2 -> {
                chatPlayer(worried, "No, you're scary!")
                chatNpc(bored, "Great. The first person I can speak to in ages...")
                chatNpc(bored, "...and they're an idiot.")
            }
        }
    }

    private suspend fun Dialogue.explainSkull() {
        chatNpc(sad, "Well, to be honest... I'm not sure.")
        chatPlayer(neutral, "I've been told a certain task may need to be completed so you can rest in peace.")
        chatNpc(neutral, "I should think it is probably because a warlock has come along and stolen my skull. If you look inside my coffin there, you'll find my corpse without a head on it.")
        chatPlayer(quiz, "Do you know where this warlock might be now?")
        chatNpc(neutral, "I think it was one of the warlocks who lives in the big tower by the sea, south-west from here.")
        chatPlayer(happy, "Okay. I will try and get the skull back for you, then you can rest in peace.")
        chatNpc(happy, "Ooh, thank you. That would be such a great relief!")
        chatNpc(sad, "It is so dull being a ghost...")
        val stage = quest.getQuestStage(player)
        if (stage < STAGE_SPOKE_TO_GHOST) {
            quest.advanceQuestStage(access, STAGE_SPOKE_TO_GHOST - stage)
        }
    }

    private suspend fun Dialogue.skullHunt() {
        chatNpc(quiz, "How are you doing finding my skull?")
        if (player.inv.count(GHOST_SKULL) > 0) {
            chatPlayer(happy, "I have found it!")
            chatNpc(happy, "Hurrah! Now I can stop being a ghost! You just need to put it in my coffin there, then I'll be free!")
            return
        }
        chatPlayer(sad, "Sorry, I can't find it at the moment.")
        chatNpc(neutral, "Ah well. Keep on looking.")
        chatNpc(neutral, "I'm pretty sure it's somewhere in the tower south-west from here. There's a lot of levels to the tower, though. I suppose it might take a little while to find.")
        if (quest.getQuestStage(player) >= STAGE_COMPLETE) {
            return
        }
    }

    private companion object {
        const val GHOST = "npc.ghostx"
    }
}
