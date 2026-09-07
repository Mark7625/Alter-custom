package org.rsmod.content.quest.area.varrock.demonslayer.npcs

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.varrock.demonslayer.DemonSlayerQuest
import org.rsmod.content.quest.area.varrock.demonslayer.DemonSlayerQuest.Companion.KEY_DRAIN
import org.rsmod.content.quest.area.varrock.demonslayer.DemonSlayerQuest.Companion.KEY_ROVIN
import org.rsmod.content.quest.area.varrock.demonslayer.DemonSlayerQuest.Companion.KEY_TRAIBORN
import org.rsmod.content.quest.area.varrock.demonslayer.DemonSlayerQuest.Companion.SILVERLIGHT
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Sir Prysin, keeper of Silverlight, in the south-west of Varrock Palace's ground floor. */
class SirPrysin
@Inject
constructor(
    private val demonSlayer: DemonSlayerQuest,
    private val objRepo: ObjRepository,
) : PluginScript() {

    private val quest
        get() = demonSlayer.quest

    private val presentingType =
        ServerCacheManager.getNpc(PRESENTING_NPC.asRSCM(RSCMType.NPC))
            ?: error("Missing npc: $PRESENTING_NPC")

    override fun ScriptContext.startup() {
        onOpNpc1("npc.sir_prysin") { startDialogue(it.npc) { prysin(it.npc) } }
    }

    private suspend fun Dialogue.prysin(npc: Npc) {
        demonSlayer.syncVars(player)
        when (quest.getQuestStage(player)) {
            DemonSlayerQuest.STAGE_STARTED -> keyHunt(npc)
            DemonSlayerQuest.STAGE_SILVERLIGHT -> beforeFight()
            DemonSlayerQuest.STAGE_COMPLETE -> afterQuest()
            else -> beforeQuest()
        }
    }

    private suspend fun Dialogue.beforeQuest() {
        chatNpc(quiz, "Hello, who are you?")
        when (
            choice2(
                "I am a mighty adventurer. Who are you?", 1,
                "I'm not sure. I was hoping you could tell me.", 2,
            )
        ) {
            1 -> {
                chatPlayer(happy, "I am a mighty adventurer. Who are you?")
                chatNpc(happy, "I am Sir Prysin, a bold and famous knight of the realm.")
            }
            2 -> {
                chatPlayer(confused, "I'm not sure. I was hoping you could tell me.")
                chatNpc(confused, "Well, I've certainly never met you before.")
            }
        }
    }

    private suspend fun Dialogue.keyHunt(npc: Npc) {
        if (demonSlayer.hasAllKeys(player)) {
            handOverSilverlight(npc)
            return
        }
        val held = demonSlayer.keysHeld(player)
        if (!demonSlayer.prysinExplained.get(player)) {
            introduceKeys()
            return
        }
        chatNpc(quiz, "So, how are you getting on with those keys?")
        if (held.isEmpty()) {
            chatPlayer(sad, "I haven't found any of them yet.")
        } else {
            chatPlayer(happy, "I've got ${describeKeys(held)}.")
            chatNpc(happy, "Good. Bring me the rest and Silverlight is yours.")
        }
        when (
            choice2(
                "Can you remind me where all the keys were again?", 1,
                "I'm still looking.", 2,
            )
        ) {
            1 -> {
                chatPlayer(quiz, "Can you remind me where all the keys were again?")
                keyLocations()
            }
            2 -> {
                chatPlayer(neutral, "I'm still looking.")
                chatNpc(neutral, "Very well. Let me know when you have them all.")
            }
        }
    }

    private suspend fun Dialogue.introduceKeys() {
        chatNpc(quiz, "Hello, who are you?")
        when (
            choice3(
                "I am a mighty adventurer. Who are you?", 1,
                "I'm not sure. I was hoping you could tell me.", 2,
                "Aris said I should come and talk to you.", 3,
            )
        ) {
            1 -> {
                chatPlayer(happy, "I am a mighty adventurer. Who are you?")
                chatNpc(happy, "I am Sir Prysin, a bold and famous knight of the realm.")
                return
            }
            2 -> {
                chatPlayer(confused, "I'm not sure. I was hoping you could tell me.")
                chatNpc(confused, "Well, I've certainly never met you before.")
                return
            }
        }
        chatPlayer(neutral, "Aris said I should come and talk to you.")
        chatNpc(quiz, "Aris? Is she still alive? I remember her from when I was a boy. Well, what do you need from me?")
        chatPlayer(neutral, "I need to find Silverlight.")
        chatNpc(quiz, "Whatever do you need that for?")
        chatPlayer(neutral, "I need it to fight Delrith.")
        chatNpc(shocked, "Delrith? I thought my great-grandfather rid the world of him for good.")
        chatPlayer(neutral, "Aris' crystal ball seems to think otherwise.")
        chatNpc(neutral, "Well, if the ball says so, I had better help you. The trouble is getting hold of Silverlight.")
        chatPlayer(quiz, "You mean you don't have it?")
        chatNpc(neutral, "Oh, I have it. But it is so powerful that the King made me lock it in a special case that needs three different keys to open, so it can't fall into the wrong hands.")
        chatPlayer(neutral, "So give me the keys!")
        chatNpc(worried, "Um... well... it isn't quite that simple.")
        demonSlayer.prysinExplained.set(player, true)
        keyLocations()
    }

    private suspend fun Dialogue.keyLocations() {
        chatNpc(neutral, "I kept one of the keys myself and gave the other two to others for safe keeping. One went to Rovin, captain of the palace guard, and the other to the wizard Traiborn.")
        while (true) {
            when (
                choice4(
                    "Can you give me your key?", 1,
                    "Where can I find Captain Rovin?", 2,
                    "Where does the wizard live?", 3,
                    "Well, I'd better go key hunting.", 4,
                )
            ) {
                1 -> {
                    chatPlayer(quiz, "Can you give me your key?")
                    chatNpc(worried, "Um... ah... there is a small problem with that as well.")
                    chatNpc(sad, "I dropped it down the drain just outside the palace kitchen. I can see it, but I can't reach it.")
                    chatPlayer(quiz, "So what does the drain lead to?")
                    chatNpc(neutral, "It carries water from the kitchen sink down into the sewers beneath the palace. A good flush of water might carry the key down there, where you could fetch it.")
                }
                2 -> {
                    chatPlayer(quiz, "Where can I find Captain Rovin?")
                    chatNpc(neutral, "Captain Rovin lives at the top of the guards' quarters, in the north-west wing of this palace.")
                }
                3 -> {
                    chatPlayer(quiz, "Where does the wizard live?")
                    chatNpc(neutral, "Wizard Traiborn? He is one of the wizards in the tower on the little island off the south coast. I believe his quarters are on the first floor.")
                }
                4 -> {
                    chatPlayer(neutral, "Well, I'd better go key hunting.")
                    chatNpc(neutral, "Good luck, and do try not to lose any of them.")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.handOverSilverlight(npc: Npc) {
        chatNpc(quiz, "So, how are you getting on with those keys?")
        chatPlayer(happy, "I've got all three keys!")
        if (player.inv.freeSpace() < 1) {
            chatNpc(neutral, "Excellent! Make some room in your pack and I'll fetch Silverlight for you.")
            return
        }
        chatNpc(happy, "Excellent! Now I can give you Silverlight.")
        access.invDel(access.inv, KEY_ROVIN, 1, KEY_DRAIN, 1)
        access.invDel(access.inv, KEY_TRAIBORN)

        access.npcChangeType(npc, presentingType, PRESENTING_TICKS)
        npc.anim("seq.qip_ds_presenting_silverlight_start")
        access.soundSynth("synth.pillory_unlock")
        delay(2)
        npc.anim("seq.qip_ds_presenting_sword_middle")
        delay(2)
        npc.anim("seq.qip_ds_presenting_sword_end")
        access.anim("seq.qip_ds_recieving_silverlight")
        access.soundSynth("synth.found_gem")
        access.invAddOrDrop(objRepo, SILVERLIGHT)
        demonSlayer.silverlightGiven.set(player, true)
        demonSlayer.advance(access)
        objbox(SILVERLIGHT, "Sir Prysin hands you a gleaming sword.")
        chatNpc(neutral, "That sword belonged to my great-grandfather. Treat it with respect!")
        chatNpc(happy, "Now go and deal with that demon!")
    }

    private suspend fun Dialogue.beforeFight() {
        chatNpc(quiz, "Have you sorted that demon out yet?")
        if (with(demonSlayer) { access.hasSilverlight() }) {
            chatPlayer(neutral, "No, not yet.")
            chatNpc(neutral, "Then get on with it! He'll be a good deal stronger once he reaches his full power.")
            return
        }
        chatPlayer(sad, "Not yet. And I, um... lost Silverlight.")
        if (player.inv.freeSpace() < 1) {
            chatNpc(angry, "You did what? Someone handed it back in, luckily for you. Make some room in your pack and I'll give it to you again.")
            return
        }
        chatNpc(angry, "Yes, I know. Someone handed it back in. Take better care of it this time!")
        access.invAddOrDrop(objRepo, SILVERLIGHT)
        access.soundSynth("synth.pick2")
        objbox(SILVERLIGHT, "Sir Prysin hands Silverlight back to you.")
    }

    private suspend fun Dialogue.afterQuest() {
        chatNpc(happy, "So, the demon is dealt with? Well done. My great-grandfather would have been proud.")
        if (with(demonSlayer) { access.hasSilverlight() }) {
            chatPlayer(happy, "Silverlight served me well.")
            chatNpc(neutral, "Keep it safe. A blade like that always finds more work.")
            return
        }
        chatPlayer(sad, "I'm afraid I've lost Silverlight.")
        chatNpc(neutral, "Lost it? Hmph. I can have another forged from the old designs, but it will cost you ${DemonSlayerQuest.SILVERLIGHT_REPLACEMENT_COST} coins.")
        val buy =
            choice2(
                "Yes, I'll pay ${DemonSlayerQuest.SILVERLIGHT_REPLACEMENT_COST} coins.", true,
                "No thanks.", false,
                title = "Buy a replacement Silverlight?",
            )
        if (!buy) {
            chatPlayer(neutral, "No thanks, not right now.")
            return
        }
        if (player.inv.freeSpace() < 1) {
            chatNpc(neutral, "You'll need some room in your pack first.")
            return
        }
        if (!access.invTakeFee(DemonSlayerQuest.SILVERLIGHT_REPLACEMENT_COST)) {
            chatPlayer(sad, "I don't have enough coins on me.")
            chatNpc(neutral, "Then come back when you do.")
            return
        }
        access.invAddOrDrop(objRepo, SILVERLIGHT)
        access.soundSynth("synth.coins_jingle_1")
        objbox(SILVERLIGHT, "Sir Prysin hands you a freshly forged Silverlight.")
        chatNpc(neutral, "Do try to hold on to this one.")
    }

    private fun describeKeys(held: List<String>): String {
        val names =
            held.map {
                when (it) {
                    KEY_ROVIN -> "the key from Captain Rovin"
                    KEY_TRAIBORN -> "the key from Wizard Traiborn"
                    else -> "the key you dropped down the drain"
                }
            }
        return when (names.size) {
            1 -> names[0]
            else -> names.dropLast(1).joinToString(", ") + " and " + names.last()
        }
    }

    private companion object {
        const val PRESENTING_NPC = "npc.sir_prysin_silverlight"
        const val PRESENTING_TICKS = 8
    }
}
