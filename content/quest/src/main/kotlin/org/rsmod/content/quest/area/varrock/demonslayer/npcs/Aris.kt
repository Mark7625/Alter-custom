package org.rsmod.content.quest.area.varrock.demonslayer.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.varrock.demonslayer.DemonSlayerQuest
import org.rsmod.content.quest.area.varrock.demonslayer.WallyVision
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Aris, the fortune teller in the tent on the west side of Varrock Square. Starts Demon Slayer. */
class Aris
@Inject
constructor(
    private val demonSlayer: DemonSlayerQuest,
    private val vision: WallyVision,
) : PluginScript() {

    private val quest
        get() = demonSlayer.quest

    private companion object {
        const val WALLY = "npc.qip_ds_wally"
    }

    override fun ScriptContext.startup() {
        onOpNpc1("npc.aris") { startDialogue(it.npc) { aris(it.npc) } }
    }

    private suspend fun Dialogue.aris(npc: Npc) {
        demonSlayer.syncVars(player)
        when {
            quest.isQuestCompleted(player) -> afterQuest()
            quest.isQuestInProgress(player) -> duringQuest(npc)
            else -> beforeQuest(npc)
        }
    }

    private suspend fun Dialogue.beforeQuest(npc: Npc) {
        chatNpc(happy, "Greetings, young one. Cross my palm with a coin and I shall reveal what the future holds for you.")
        if (access.invCoinTotal() < 1) {
            chatPlayer(sad, "I'm afraid I haven't got a single coin on me.")
            chatNpc(neutral, "Then come back when you do. The future can wait a little longer.")
            return
        }
        val pay =
            choice2(
                "Yes.", true,
                "No.", false,
                title = "Pay Aris one coin for a reading?",
            )
        if (!pay) {
            chatPlayer(neutral, "No thanks. I don't put much faith in fortune telling.")
            chatNpc(neutral, "Suit yourself, young one.")
            return
        }
        if (!access.invTakeFee(1)) {
            chatPlayer(sad, "I'm afraid I haven't got a single coin on me.")
            return
        }
        chatPlayer(happy, "Here you go, one coin.")
        npc.anim("seq.qip_ds_reading_crystalball")
        access.soundSynth("synth.crystal_sing")
        chatNpc(neutral, "Come closer and listen well, for the mists in the crystal ball are parting...")
        chatNpc(neutral, "I see shapes forming. I see... you. You are holding a very fine sword. I'm sure I know that blade from somewhere...")
        chatNpc(worried, "Now a great dark shadow looms behind you.")
        chatNpc(shocked, "Aaargh!")
        chatPlayer(worried, "Are you all right?")
        chatNpc(shocked, "It's Delrith! Delrith is coming!")
        chatPlayer(quiz, "Who is Delrith?")
        chatNpc(worried, "Delrith is a powerful demon. I only hope he didn't notice me watching him through the crystal ball!")
        chatNpc(neutral, "He tried to destroy this city a hundred and fifty years ago. He was stopped just in time by the great hero Wally.")
        chatNpc(neutral, "With his magic sword Silverlight, Wally trapped the demon inside the stone circle just south of the city.")
        chatNpc(shocked, "Ye gods! Silverlight was the very sword you were holding in my vision. You are the one destined to stop the demon this time!")

        demonSlayer.incantationOrder(player)
        demonSlayer.advance(access)
        questionsBeforeVision(npc)
    }

    private suspend fun Dialogue.questionsBeforeVision(npc: Npc) {
        while (true) {
            when (
                choice4(
                    "How am I meant to fight a demon who can destroy cities?", 1,
                    "Okay, where is he? I'll kill him for you!", 2,
                    "Wally doesn't sound like a very heroic name.", 3,
                    "So how did Wally kill Delrith?", 4,
                    title = "What would you like to say?",
                )
            ) {
                1 -> howToFight()
                2 -> whereIsHe()
                3 -> wallysName()
                4 -> {
                    howWallyWon()
                    questionsAfterVision(npc)
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.questionsAfterVision(npc: Npc) {
        while (true) {
            when (
                choice5(
                    "What is the magical incantation?", 1,
                    "Where can I find Silverlight?", 2,
                    "How am I meant to fight a demon who can destroy cities?", 3,
                    "Okay, where is he? I'll kill him for you!", 4,
                    "Okay, thanks. I'll do my best to stop the demon.", 5,
                    title = "What would you like to say?",
                )
            ) {
                1 -> incantation()
                2 -> whereIsSilverlight()
                3 -> howToFight()
                4 -> whereIsHe()
                5 -> {
                    chatPlayer(happy, "Okay, thanks. I'll do my best to stop the demon.")
                    chatNpc(happy, "Good luck, young one, and may Guthix watch over you!")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.howToFight() {
        chatPlayer(worried, "How am I meant to fight a demon who can destroy cities?!")
        chatNpc(neutral, "Face Delrith while he is still weak from being summoned, and use the right weapon, and the task will not be beyond you.")
        chatNpc(happy, "Do not fear. Follow the path of the great hero Wally and you are sure to defeat the demon.")
    }

    private suspend fun Dialogue.whereIsHe() {
        chatPlayer(happy, "Okay, where is he? I'll kill him for you!")
        chatNpc(laugh, "Ah, the confidence of the young!")
        chatNpc(neutral, "Delrith cannot be harmed by ordinary weapons. You must face him with the same sword Wally used.")
    }

    private suspend fun Dialogue.wallysName() {
        chatPlayer(quiz, "Wally doesn't sound like a very heroic name.")
        chatNpc(neutral, "Perhaps that is why history has forgotten him. He was a very great hero all the same.")
        chatNpc(worried, "Who knows what suffering Delrith would have caused had Wally not stopped him! It seems you will need to match his heroics.")
    }

    private suspend fun Dialogue.howWallyWon() {
        chatPlayer(quiz, "So how did Wally kill Delrith?")
        chatNpc(neutral, "Wally reached the stone circle just as a cult of dark wizards finished summoning Delrith...")
        val played = with(vision) { access.play(demonSlayer) }
        if (!played) {
            narrateVision()
        }
        chatNpc(neutral, "By speaking the correct incantation and thrusting Silverlight into the newly summoned demon, Wally sealed Delrith inside the stone block at the heart of the circle.")
        chatNpc(worried, "Delrith will come forth from that circle again. I expect some evil sorcerer is already at work on the rituals to summon him as we speak.")
    }

    /** Told in dialogue when the instanced vision cannot be shown, so the words are never lost. */
    private suspend fun Dialogue.narrateVision() {
        chatNpc(neutral, "The mists show me the scene as clearly as if I stood there. The wizards chant, the stone splits, and the demon rises...")
        chatNpcSpecific("Wally", WALLY, angry, "Begone, foul demon!")
        chatNpcSpecific("Wally", WALLY, confused, "Now, how did that incantation go again?")
        chatNpcSpecific("Wally", WALLY, angry, demonSlayer.incantationSpoken(player))
        chatNpc(neutral, "And with those words, and Silverlight driven into the demon, Delrith was dragged back into the stone.")
    }

    private suspend fun Dialogue.incantation() {
        chatPlayer(quiz, "What is the magical incantation?")
        chatNpc(neutral, "Oh yes, give me a moment to think...")
        chatNpc(happy, "Right, I have it now. It goes: ${demonSlayer.incantationSpoken(player)} Have you got that?")
        chatPlayer(neutral, "I think so, yes.")
    }

    private suspend fun Dialogue.whereIsSilverlight() {
        chatPlayer(quiz, "Where can I find Silverlight?")
        chatNpc(neutral, "Silverlight has passed down through Wally's descendants. I believe it is currently kept by one of the King's knights, Sir Prysin.")
        chatNpc(neutral, "He shouldn't be hard to find. He lives in the royal palace here in Varrock. Tell him Aris sent you.")
    }

    private suspend fun Dialogue.duringQuest(npc: Npc) {
        val stage = quest.getQuestStage(player)
        chatNpc(happy, "Greetings, young one. How goes your quest?")
        if (stage >= DemonSlayerQuest.STAGE_SILVERLIGHT) {
            chatPlayer(happy, "I have the sword now. I just need to deal with the demon.")
            chatNpc(happy, "Then hurry. Delrith will not wait for you to be ready.")
        } else {
            chatPlayer(neutral, "I found Sir Prysin, but I haven't got the sword yet. He has made it rather complicated.")
            chatNpc(worried, "Then make haste. We haven't much time.")
        }
        while (true) {
            val pick =
                if (stage >= DemonSlayerQuest.STAGE_SILVERLIGHT) {
                    choice4(
                        "What is the magical incantation?", 1,
                        "Where can I find the demon?", 3,
                        "Stop calling me that!", 4,
                        "Well, I'd better press on with it.", 5,
                        title = "What would you like to say?",
                    )
                } else {
                    choice4(
                        "What is the magical incantation?", 1,
                        "Where can I find Silverlight?", 2,
                        "Stop calling me that!", 4,
                        "Well, I'd better press on with it.", 5,
                        title = "What would you like to say?",
                    )
                }
            when (pick) {
                1 -> incantation()
                2 -> whereIsSilverlight()
                3 -> {
                    chatPlayer(quiz, "Where can I find the demon?")
                    chatNpc(neutral, "Head south out of the city gate. The stone circle lies just beyond it, east of the road.")
                }
                4 -> stopCallingMeThat()
                5 -> {
                    chatPlayer(neutral, "Well, I'd better press on with it.")
                    chatNpc(happy, "Until next time, young one.")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.stopCallingMeThat() {
        chatPlayer(angry, "Stop calling me that!")
        chatNpc(neutral, "In the great scheme of things, you are very young indeed.")
        when (
            choice2(
                "Okay, but how old are you?", 1,
                "Oh, if it's in the scheme of things, that's fine.", 2,
            )
        ) {
            1 -> {
                chatPlayer(quiz, "Okay, but how old are you?")
                chatNpc(shifty, "Count the legs on every stool in the Blue Moon Inn, then multiply by seven.")
                chatPlayer(confused, "Er... right. Whatever you say.")
            }
            2 -> {
                chatPlayer(neutral, "Oh, if it's in the scheme of things, that's fine.")
                chatNpc(happy, "You show wisdom for one so young.")
            }
        }
    }

    private suspend fun Dialogue.afterQuest() {
        chatNpc(happy, "Greetings, demon slayer! The crystal ball showed me everything. Varrock owes you a great debt.")
        chatPlayer(happy, "All in a day's work.")
        chatNpc(neutral, "Rest while you can, young one. The mists have a habit of clearing again.")
    }
}
