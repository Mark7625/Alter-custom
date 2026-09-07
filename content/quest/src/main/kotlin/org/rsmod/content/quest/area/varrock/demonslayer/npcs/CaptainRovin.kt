package org.rsmod.content.quest.area.varrock.demonslayer.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.varrock.demonslayer.DemonSlayerQuest
import org.rsmod.content.quest.area.varrock.demonslayer.DemonSlayerQuest.Companion.KEY_ROVIN
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Captain Rovin, at the top of the north-west tower of Varrock Palace. Holds one key. */
class CaptainRovin @Inject constructor(private val demonSlayer: DemonSlayerQuest) : PluginScript() {

    private val quest
        get() = demonSlayer.quest

    override fun ScriptContext.startup() {
        onOpNpc1("npc.captain_rovin") { startDialogue(it.npc) { rovin() } }
    }

    private suspend fun Dialogue.rovin() {
        chatNpc(angry, "What are you doing up here? Only the palace guards are allowed up here.")
        val onQuest = quest.getQuestStage(player) == DemonSlayerQuest.STAGE_STARTED
        val pick =
            if (onQuest) {
                choice4(
                    "I am one of the palace guards.", 1,
                    "What about the King?", 2,
                    "Yes I know, but this is important.", 3,
                    "Erm... I forgot.", 4,
                )
            } else {
                choice3(
                    "I am one of the palace guards.", 1,
                    "What about the King?", 2,
                    "Erm... I forgot.", 4,
                )
            }
        when (pick) {
            1 -> {
                chatPlayer(shifty, "I am one of the palace guards.")
                chatNpc(angry, "No, you're not! I know every guard in this palace and you're not one of them. Now get out!")
            }
            2 -> {
                chatPlayer(quiz, "What about the King?")
                chatNpc(neutral, "Well, yes, the King can come up here too. He's the King, after all. You are not.")
            }
            3 -> important()
            4 -> {
                chatPlayer(confused, "Erm... I forgot.")
                chatNpc(angry, "Then go and remember somewhere else!")
            }
        }
    }

    private suspend fun Dialogue.important() {
        chatPlayer(neutral, "Yes I know, but this is important.")
        chatNpc(neutral, "Alright, I'm listening. What's so important?")
        chatPlayer(worried, "There's a demon who wants to invade the city.")
        if (demonSlayer.rovinKeyGiven.get(player)) {
            keyAlreadyGiven()
            return
        }
        chatNpc(quiz, "Is it a powerful demon?")
        when (
            choice2(
                "Not really.", 1,
                "Yes, very.", 2,
            )
        ) {
            1 -> {
                chatPlayer(neutral, "Not really.")
                chatNpc(neutral, "Then I'm sure the palace guards can handle it. Thank you for the warning.")
                return
            }
        }
        chatPlayer(worried, "Yes, very.")
        chatNpc(worried, "The palace guards are good, but I'm not sure they're up to fighting a very powerful demon.")
        when (
            choice2(
                "Yeah, the palace guards are rubbish!", 1,
                "It's not them who are going to fight the demon, it's me.", 2,
            )
        ) {
            1 -> {
                chatPlayer(laugh, "Yeah, the palace guards are rubbish!")
                chatNpc(neutral, "Yeah, they're...")
                chatNpc(verymad, "Wait! How dare you insult the palace guards? Get out of my sight!")
                return
            }
        }
        chatPlayer(neutral, "It's not them who are going to fight the demon, it's me.")
        chatNpc(quiz, "What, all by yourself? And how do you plan to do that?")
        chatPlayer(neutral, "With the sword Silverlight. I believe you hold one of the keys to its case?")
        chatNpc(neutral, "I do. But why should I hand it over to you?")
        while (true) {
            when (
                choice3(
                    "Fortune-teller Aris said I was destined to kill the demon.", 1,
                    "Otherwise the demon will destroy the city!", 2,
                    "Sir Prysin said you would give me the key.", 3,
                )
            ) {
                1 -> {
                    chatPlayer(neutral, "Fortune-teller Aris said I was destined to kill the demon.")
                    chatNpc(angry, "A fortune-teller? Destiny? I don't hold with that nonsense. I got where I am by hard work, not destiny! Why should I care what that old woman says?")
                }
                2 -> {
                    chatPlayer(worried, "Otherwise the demon will destroy the city!")
                    chatNpc(angry, "You can't fool me. How do I know you haven't made the whole story up just to get my key?")
                }
                3 -> {
                    chatPlayer(neutral, "Sir Prysin said you would give me the key.")
                    chatNpc(angry, "Oh, did he? Well, I don't answer to Sir Prysin. I answer to the King!")
                    chatNpc(verymad, "I didn't work my way up through the ranks to take orders from a puffed-up fool who only has his post because his great-grandfather was a hero with a silly name!")
                    chatPlayer(quiz, "Then why did he give you one of the keys?")
                    chatNpc(neutral, "Only because the King ordered it! The King couldn't make Sir Prysin part with his precious ancestral sword, so he made him lock it up where he couldn't lose it.")
                    chatNpc(neutral, "I got one key, and some wizard got another. What happened to the third?")
                    chatPlayer(laugh, "Sir Prysin dropped it down a drain!")
                    chatNpc(madlaugh, "Ha ha ha! The idiot!")
                    chatNpc(happy, "Fine, I'll give you the key, if only so that it's you who kills the demon and not Sir Prysin!")
                    giveKey()
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.giveKey() {
        if (access.invAdd(access.inv, KEY_ROVIN).failure) {
            chatNpc(neutral, "Your pack is full. Clear some space and come back for it.")
            return
        }
        demonSlayer.rovinKeyGiven.set(player, true)
        access.soundSynth("synth.pick2")
        objbox(KEY_ROVIN, "Captain Rovin hands you a key.")
    }

    private suspend fun Dialogue.keyAlreadyGiven() {
        chatNpc(neutral, "Yes, you said before. Haven't you killed it yet?")
        chatPlayer(neutral, "I'm going to use the sword Silverlight, and I believe you hold one of the keys to its case?")
        when {
            player.inv.count(KEY_ROVIN) > 0 ->
                chatNpc(neutral, "I already gave you my key. Check your pockets.")
            access.bank.count(KEY_ROVIN) > 0 ->
                chatNpc(neutral, "I already gave you my key. Perhaps you left it somewhere. Have you checked your bank?")
            else -> {
                chatNpc(angry, "I already gave you my key, and you've lost it? Luckily for you I keep a spare. Don't lose this one.")
                giveKey()
            }
        }
    }
}
