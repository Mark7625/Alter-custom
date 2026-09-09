package org.rsmod.content.quest.area.varrock.gertrudescat.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.varrock.gertrudescat.GertrudesCatQuest
import org.rsmod.content.quest.area.varrock.gertrudescat.GertrudesCatQuest.Companion.KIDS_FEE
import org.rsmod.content.quest.area.varrock.gertrudescat.GertrudesCatQuest.Companion.STAGE_GAVE_MILK
import org.rsmod.content.quest.area.varrock.gertrudescat.GertrudesCatQuest.Companion.STAGE_PAID_KIDS
import org.rsmod.content.quest.area.varrock.gertrudescat.GertrudesCatQuest.Companion.STAGE_STARTED
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Shilop and Wilough, Gertrude's sons, loitering in Varrock's market place. Either of them will
 * sell the location of their secret play area; the other chips in from the side.
 */
class GertrudesKids @Inject constructor(private val gertrudesCat: GertrudesCatQuest) : PluginScript() {

    private val quest
        get() = gertrudesCat.quest

    override fun ScriptContext.startup() {
        onOpNpc1(SHILOP) { startDialogue(it.npc) { kid(it.npc, SHILOP) } }
        onOpNpc1(WILOUGH) { startDialogue(it.npc) { kid(it.npc, WILOUGH) } }
    }

    private suspend fun Dialogue.kid(npc: Npc, type: String) {
        val other = if (type == SHILOP) WILOUGH else SHILOP
        val otherName = if (type == SHILOP) "Wilough" else "Shilop"
        when (val stage = quest.getQuestStage(player)) {
            0 -> {
                chatPlayer(happy, "Hello there.")
                chatNpc(angry, "Get lost! Can't you see we're busy?")
            }
            STAGE_STARTED -> secretPlayArea(other, otherName)
            STAGE_PAID_KIDS -> {
                chatPlayer(quiz, "Where did you say you saw Fluffs?")
                chatNpc(bored, "Weren't you listening? The flea bag is in the old lumber mill north-east of here. Walk past the Jolly Boar Inn and you'll find it.")
            }
            in STAGE_GAVE_MILK until GertrudesCatQuest.STAGE_COMPLETE -> toughGuy(other, otherName)
            else -> {
                chatPlayer(happy, "Hello again.")
                chatNpc(happy, "Mum's stopped fussing over that cat, so I suppose we owe you one. Don't tell her about the ${KIDS_FEE} coins though.")
                if (stage >= GertrudesCatQuest.STAGE_COMPLETE) {
                    chatPlayer(shifty, "My lips are sealed.")
                }
            }
        }
    }

    private suspend fun Dialogue.secretPlayArea(other: String, otherName: String) {
        chatPlayer(happy, "Hello there. I've been looking for you.")
        chatNpc(worried, "I didn't mean to take it! I just forgot to pay!")
        chatPlayer(confused, "What? I'm trying to help your mum find Fluffs.")
        chatNpc(neutral, "Ohh... well, in that case I might be able to help. Fluffs followed me to my secret play area, and I haven't seen her since.")
        chatPlayer(quiz, "Where is this play area?")
        chatNpc(shifty, "If I told you that, it wouldn't be a secret.")
        when (
            choice3(
                "Tell me sonny, or I will hurt you.", 1,
                "What will make you tell me?", 2,
                "Well, never mind, it's Fluffs' loss.", 3,
            )
        ) {
            1 -> {
                chatPlayer(angry, "Tell me sonny, or I will hurt you.")
                chatNpc(shocked, "W..wh..what?! You wouldn't! A young lad like me! I'd have you behind bars before nightfall!")
                mesbox("You decide it's best not to hurt the boy.")
            }
            2 -> haggle(other, otherName)
            3 -> {
                chatPlayer(neutral, "Well, never mind, it's Fluffs' loss.")
                chatNpc(neutral, "I'm sure my mum will get over it.")
            }
        }
    }

    private suspend fun Dialogue.haggle(other: String, otherName: String) {
        chatPlayer(quiz, "What will make you tell me?")
        chatNpc(shifty, "Well... now that you ask, I am a bit short on cash.")
        chatPlayer(quiz, "How much?")
        chatNpc(neutral, "10 coins.")
        chatNpcSpecific(otherName, other, shocked, "10 coins?!")
        chatNpcSpecific(otherName, other, neutral, "I'll handle this.")
        chatNpcSpecific(otherName, other, shifty, "$KIDS_FEE coins should cover it.")
        chatPlayer(shocked, "$KIDS_FEE coins! Why should I pay you?")
        chatNpcSpecific(otherName, other, neutral, "You shouldn't, but we won't help otherwise. We never liked that cat anyway. So what do you say?")
        when (
            choice2(
                "I'm not paying you a penny.", 1,
                "Okay then, I'll pay.", 2,
            )
        ) {
            1 -> {
                chatPlayer(angry, "I'm not paying you a penny.")
                chatNpc(neutral, "Okay then. I'll find another way to make money.")
            }
            2 -> pay(other, otherName)
        }
    }

    private suspend fun Dialogue.pay(other: String, otherName: String) {
        chatPlayer(neutral, "Okay then, I'll pay.")
        if (!access.invTakeFee(KIDS_FEE)) {
            chatPlayer(sad, "Oh. I don't seem to have $KIDS_FEE coins on me.")
            chatNpc(bored, "Then come back when you do. No coins, no secret.")
            return
        }
        access.soundSynth("synth.coins_jingle_1")
        mesbox("You give the lad $KIDS_FEE coins.")
        quest.advanceQuestStage(access)
        chatPlayer(quiz, "There you go. Now, where did you see Fluffs?")
        chatNpc(happy, "I play at an abandoned lumber mill to the north-east, just beyond the Jolly Boar Inn. I saw Fluffs running around in there.")
        chatPlayer(quiz, "Anything else?")
        chatNpc(neutral, "Well, you'll have to find the broken fence to get in. I'm sure you can manage that.")
    }

    private suspend fun Dialogue.toughGuy(other: String, otherName: String) {
        chatPlayer(happy, "Hello again.")
        chatNpc(angry, "You think you're tough, do you?")
        chatPlayer(confused, "Pardon?")
        chatNpc(angry, "I can beat anyone up!")
        chatNpcSpecific(otherName, other, happy, "He can, you know!")
        chatPlayer(quiz, "Really?")
        mesbox("The boy begins to jump around with his fists up. You decide it's best not to kill him just yet.")
    }

    private companion object {
        const val SHILOP = "npc.shilop"
        const val WILOUGH = "npc.wilough"
    }
}
