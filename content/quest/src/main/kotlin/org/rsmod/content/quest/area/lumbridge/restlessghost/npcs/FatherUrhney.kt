package org.rsmod.content.quest.area.lumbridge.restlessghost.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.lumbridge.restlessghost.RestlessGhostQuest
import org.rsmod.content.quest.area.lumbridge.restlessghost.RestlessGhostQuest.Companion.GHOSTSPEAK_AMULET
import org.rsmod.content.quest.area.lumbridge.restlessghost.RestlessGhostQuest.Companion.STAGE_GOT_AMULET
import org.rsmod.content.quest.area.lumbridge.restlessghost.RestlessGhostQuest.Companion.STAGE_STARTED
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Father Urhney, the hermit in the shack at the west edge of Lumbridge swamp. */
class FatherUrhney @Inject constructor(private val restlessGhost: RestlessGhostQuest) : PluginScript() {

    private val quest
        get() = restlessGhost.quest

    override fun ScriptContext.startup() {
        onOpNpc1("npc.father_urhney") { startDialogue(it.npc) { urhney() } }
    }

    private suspend fun Dialogue.urhney() {
        chatNpc(angry, "Go away! I'm meditating!")
        val stage = quest.getQuestStage(player)
        val pick =
            when {
                stage >= STAGE_GOT_AMULET ->
                    choice3(
                        "Well, that's friendly.", 1,
                        "I've lost the Amulet of Ghostspeak.", 3,
                        "I've come to repossess your house.", 4,
                    )
                stage == STAGE_STARTED ->
                    choice3(
                        "Well, that's friendly.", 1,
                        "Father Aereck sent me to talk to you.", 2,
                        "I've come to repossess your house.", 4,
                    )
                else ->
                    choice2(
                        "Well, that's friendly.", 1,
                        "I've come to repossess your house.", 4,
                    )
            }
        when (pick) {
            1 -> {
                chatPlayer(neutral, "Well, that's friendly.")
                chatNpc(verymad, "I SAID go AWAY.")
                chatPlayer(bored, "Okay, okay... sheesh, what a grouch.")
            }
            2 -> sentByAereck()
            3 -> lostAmulet()
            4 -> repossess()
        }
    }

    private suspend fun Dialogue.sentByAereck() {
        chatPlayer(neutral, "Father Aereck sent me to talk to you.")
        chatNpc(bored, "I suppose I'd better talk to you then. What problems has he got himself into this time?")
        when (
            choice2(
                "He's got a ghost haunting his graveyard.", 1,
                "You mean he gets himself into lots of problems?", 2,
            )
        ) {
            2 -> {
                chatPlayer(quiz, "You mean he gets himself into lots of problems?")
                chatNpc(laugh, "Yeah. For example, when we were trainee priests he kept on getting stuck up bell ropes.")
                chatNpc(bored, "Anyway. I don't have time for chitchat. What's his problem THIS time?")
            }
        }
        chatPlayer(neutral, "He's got a ghost haunting his graveyard.")
        chatNpc(bored, "Oh, the silly fool.")
        chatNpc(angry, "I leave town for just five months, and ALREADY he can't manage.")
        chatNpc(bored, "(sigh)")
        chatNpc(neutral, "Well, I can't go back and exorcise it. I vowed not to leave this place until I had done a full two years of prayer and meditation.")
        chatNpc(neutral, "Tell you what I can do though. Take this amulet.")
        if (access.invAdd(access.inv, GHOSTSPEAK_AMULET).failure) {
            chatNpc(angry, "Or I would, if you had any room in your pack for it. Clear a space and come back.")
            return
        }
        access.soundSynth("synth.pick2")
        objbox(GHOSTSPEAK_AMULET, "Father Urhney hands you an amulet.")
        quest.advanceQuestStage(access)
        chatNpc(neutral, "It is an Amulet of Ghostspeak.")
        chatNpc(neutral, "So called because when you wear it you can speak to ghosts. A lot of ghosts are doomed to be ghosts because they have left some important task uncompleted.")
        chatNpc(neutral, "Maybe if you know what this task is, you can get rid of the ghost. I'm not making any guarantees, mind you, but it is the best I can do right now.")
        chatPlayer(happy, "Thank you. I'll give it a try!")
    }

    private suspend fun Dialogue.lostAmulet() {
        chatPlayer(sad, "I've lost the Amulet of Ghostspeak.")
        mesbox("Father Urhney sighs.")
        when {
            with(restlessGhost) { access.carriesAmulet() } ->
                chatNpc(angry, "What are you talking about? I can see you've got it with you!")
            with(restlessGhost) { access.hasAmuletAnywhere() } ->
                chatNpc(angry, "You come here wasting my time... Has it even occurred to you that you've got it stored somewhere? Now GO AWAY!")
            player.inv.freeSpace() < 1 ->
                chatNpc(angry, "How careless can you get? Those things aren't easy to come by, you know! Now clear some space in your pack and I'll give you another one.")
            else -> {
                chatNpc(angry, "How careless can you get? Those things aren't easy to come by, you know! It's a good job I've got a spare.")
                access.invAdd(access.inv, GHOSTSPEAK_AMULET)
                access.soundSynth("synth.pick2")
                objbox(GHOSTSPEAK_AMULET, "Father Urhney hands you an amulet.")
                chatNpc(neutral, "Be more careful this time.")
                chatPlayer(neutral, "Okay, I'll try to be.")
            }
        }
    }

    private suspend fun Dialogue.repossess() {
        chatPlayer(neutral, "I've come to repossess your house.")
        chatNpc(shocked, "Under what grounds???")
        when (
            choice2(
                "Repeated failure on mortgage repayments.", 1,
                "I don't know. I just wanted this house.", 2,
            )
        ) {
            1 -> {
                chatPlayer(neutral, "Repeated failure on mortgage repayments.")
                chatNpc(shocked, "What? But... I don't have a mortgage! I built this house myself!")
                chatPlayer(shifty, "Sorry. I must have got the wrong address. All the houses look the same around here.")
                chatNpc(confused, "What? What houses? What ARE you talking about???")
                chatPlayer(neutral, "Never mind.")
            }
            2 -> {
                chatPlayer(shifty, "I don't know. I just wanted this house...")
                chatNpc(angry, "Oh... go away and stop wasting my time!")
            }
        }
    }
}
