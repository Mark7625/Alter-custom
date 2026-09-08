package org.rsmod.content.quest.area.lumbridge.lostcity.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.lumbridge.lostcity.LostCityQuest
import org.rsmod.content.quest.area.lumbridge.lostcity.LostCityQuest.Companion.CRAFTING_REQ
import org.rsmod.content.quest.area.lumbridge.lostcity.LostCityQuest.Companion.RECOMMENDED_COMBAT
import org.rsmod.content.quest.area.lumbridge.lostcity.LostCityQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.lumbridge.lostcity.LostCityQuest.Companion.WOODCUTTING_REQ
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The four adventurers camped on the north-west edge of Lumbridge Swamp. The Warrior starts the
 * quest by letting far too much slip; the other three just want the player gone.
 */
class Adventurers @Inject constructor(private val lostCity: LostCityQuest) : PluginScript() {

    private val quest
        get() = lostCity.quest

    override fun ScriptContext.startup() {
        onOpNpc1(WARRIOR) { startDialogue(it.npc) { warrior() } }
        onOpNpc1(ARCHER) { startDialogue(it.npc) { archer() } }
        onOpNpc1(WIZARD) { startDialogue(it.npc) { wizard() } }
        onOpNpc1(MONK) { startDialogue(it.npc) { monk() } }
    }

    /* Warrior */

    private suspend fun Dialogue.warrior() {
        when {
            quest.isQuestCompleted(player) -> warriorAfterQuest()
            lostCity.stage(player) == STAGE_STARTED -> {
                chatPlayer(quiz, "So let me get this straight: I need to search the trees around here for a leprechaun; and then when I find him, he will tell me where this 'Zanaris' is?")
                chatNpc(worried, "What? How did you know that? Uh... I mean, no, no you're very wrong. Very wrong, and not right at all, and I definitely didn't tell you about that at all.")
            }
            quest.isQuestInProgress(player) -> {
                chatPlayer(quiz, "Have you found anything yet?")
                chatNpc(shifty, "We're still searching for Zanaris...GAH! I mean we're not doing anything here at all.")
                chatPlayer(neutral, "I haven't found it yet either.")
            }
            else -> warriorBeforeQuest()
        }
    }

    private suspend fun Dialogue.warriorBeforeQuest() {
        chatNpc(happy, "Hello there traveller.")
        when (
            choice2(
                "What are you camped out here for?", 1,
                "Do you know any good adventures I can go on?", 2,
            )
        ) {
            1 -> campedHere()
            2 -> goodAdventures()
        }
    }

    private suspend fun Dialogue.campedHere() {
        chatPlayer(quiz, "What are you camped here for?")
        chatNpc(shifty, "We're looking for Zanaris...GAH! I mean we're not here for any particular reason at all.")
        zanarisQuestions()
    }

    private suspend fun Dialogue.zanarisQuestions() {
        when (
            choice3(
                "Who's Zanaris?", 1,
                "What's Zanaris?", 2,
                "What makes you think it's out here?", 3,
            )
        ) {
            1 -> whosZanaris()
            2 -> whatsZanaris()
            3 -> whatMakesYouThink()
        }
    }

    private suspend fun Dialogue.whosZanaris() {
        chatPlayer(quiz, "Who's Zanaris?")
        chatNpc(laugh, "Ahahahaha! Zanaris isn't a person! It's a magical hidden city filled with treasures and rich.. uh, nothing. It's nothing.")
        when (
            choice2(
                "If it's hidden how are you planning to find it?", 1,
                "There's no such thing.", 2,
            )
        ) {
            1 -> howToFindIt()
            2 -> noSuchThing()
        }
    }

    private suspend fun Dialogue.howToFindIt() {
        chatPlayer(quiz, "If it's hidden how are you planning to find it?")
        chatNpc(shifty, "Well, we don't want to tell anyone else about that, because we don't want anyone else sharing in all that glory and treasure.")
        when (
            choice2(
                "Please tell me.", 1,
                "Looks like you don't know either.", 2,
            )
        ) {
            1 -> pleaseTellMe()
            2 -> {
                chatPlayer(neutral, "Well, it looks to me like YOU don't know EITHER seeing as you're all just sat around here.")
                chatNpc(angry, "Of course we know! We just haven't found which tree the stupid leprechaun's hiding in yet!")
                offerQuest()
            }
        }
    }

    private suspend fun Dialogue.offerQuest() {
        levelWarning()
        when (
            choice2(
                "Yes.", 1,
                "No.", 2,
                title = "Start the Lost City quest?",
            )
        ) {
            1 -> {
                chatPlayer(quiz, "Leprechaun?")
                chatNpc(worried, "GAH! I didn't mean to tell you that! Look, just forget I said anything okay?")
                chatPlayer(quiz, "So a leprechaun knows where Zanaris is eh?")
                chatNpc(worried, "Ye.. uh, no. No, not at all. And even if he did - which he doesn't - he DEFINITELY ISN'T hiding in some tree around here. Nope, definitely not. Honestly.")
                chatPlayer(happy, "Thanks for the help!")
                chatNpc(worried, "Help? What help? I didn't help! Please don't say I did, I'll get in trouble!")
                quest.advanceQuestStage(access)
            }
            2 -> chatPlayer(neutral, "Right... Well good luck with it.")
        }
    }

    /** The pre-quest warning about levels the player is still short of. */
    private suspend fun Dialogue.levelWarning() {
        val lowSkills =
            access.statBase("stat.crafting") < CRAFTING_REQ ||
                access.statBase("stat.woodcutting") < WOODCUTTING_REQ
        val lowCombat = player.combatLevel < RECOMMENDED_COMBAT
        val text =
            when {
                lowSkills && lowCombat ->
                    "Before starting this quest, be aware that one or more of your skill levels " +
                        "are lower than what is required to fully complete it. Your combat level " +
                        "is also lower than the recommended level of $RECOMMENDED_COMBAT."
                lowSkills ->
                    "Before starting this quest, be aware that one or more of your skill levels " +
                        "are lower than what is required to fully complete it."
                lowCombat ->
                    "Before starting this quest, be aware that your combat level is lower than " +
                        "the recommended level of $RECOMMENDED_COMBAT."
                else -> return
            }
        mesbox(text)
    }

    private suspend fun Dialogue.noSuchThing() {
        chatPlayer(neutral, "There's no such thing!")
        chatNpc(shifty, "When we've found Zanaris you'll... GAH! I mean, we're not here for any particular reason at all.")
        zanarisQuestions()
    }

    private suspend fun Dialogue.whatsZanaris() {
        chatPlayer(quiz, "What's Zanaris?")
        chatNpc(shifty, "I don't think we want other people competing with us to find it. Forget I said anything.")
        when (
            choice2(
                "Please tell me.", 1,
                "Oh well. Never mind.", 2,
            )
        ) {
            1 -> pleaseTellMe()
            2 -> chatPlayer(neutral, "Oh well. Never mind.")
        }
    }

    private suspend fun Dialogue.whatMakesYouThink() {
        chatPlayer(quiz, "What makes you think it's out here?")
        chatNpc(shifty, "Don't you know of the legends that tell of the magical city, hidden in the swam... Uh, no, you're right, we're wasting our time here.")
        when (
            choice2(
                "If it's hidden how are you planning to find it?", 1,
                "There's no such thing!", 2,
            )
        ) {
            1 -> howToFindIt()
            2 -> noSuchThing()
        }
    }

    private suspend fun Dialogue.goodAdventures() {
        chatPlayer(quiz, "Do you know any good adventures I can go on?")
        chatNpc(neutral, "Well we're on an adventure right now. Mind you, this is OUR adventure and we don't want to share it - find your own!")
        when (
            choice2(
                "Please tell me.", 1,
                "I don't think you've found a good adventure at all!", 2,
            )
        ) {
            1 -> pleaseTellMe()
            2 -> {
                chatPlayer(neutral, "I don't think you've found a good adventure at all!")
                chatNpc(angry, "Hah! Adventurers of our calibre don't just hang around in forests for fun, whelp!")
                chatPlayer(quiz, "Oh really?")
                campedHere()
            }
        }
    }

    private suspend fun Dialogue.pleaseTellMe() {
        chatPlayer(quiz, "Please tell me?")
        chatNpc(neutral, "No.")
        chatPlayer(sad, "Please?")
        chatNpc(angry, "No!")
        chatPlayer(sad, "PLEEEEEEEEEEEEEEEEEEEEEASE?")
        chatNpc(angry, "NO!")
    }

    private suspend fun Dialogue.warriorAfterQuest() {
        chatPlayer(happy, "Hey, thanks for all the information. It REALLY helped me out in finding the lost city of Zanaris and all.")
        chatNpc(worried, "Oh please don't say that anymore! If the rest of my party knew I'd helped you they'd probably throw me out and make me walk home by myself!")
        if (lostCity.thankedWarrior.get(player)) {
            chatNpc(quiz, "So anyway, what have you found out? Where is the fabled Zanaris? Is it all the legends say it is?")
        } else {
            lostCity.thankedWarrior.set(player, true)
        }
        chatPlayer(shifty, "You know.... I think I'll keep that to myself.")
    }

    /* Archer */

    private suspend fun Dialogue.archer() {
        when (lostCity.stage(player)) {
            0 -> {
                chatPlayer(quiz, "Why are you guys hanging around here?")
                chatNpc(angry, "(ahem)...'Guys'?")
                chatPlayer(worried, "Uh... yeah, sorry about that. Why are you all standing around out here?")
                chatNpc(neutral, "Well, that's really none of your business.")
            }
            STAGE_STARTED -> {
                chatPlayer(quiz, "So I hear theres a leprechaun around here who can show me the way to Zanaris?")
                chatNpc(shocked, "...W-what? How did you..?")
                chatNpc(angry, "No. You're wrong. Now go away.")
            }
            else -> {
                chatPlayer(quiz, "So you didn't find the entrance to Zanaris yet, huh?")
                chatNpc(angry, "Don't tell me a novice like YOU has found it!")
                chatPlayer(happy, "Yep. Found it REALLY easily too.")
                chatNpc(angry, "...I cannot believe that someone like YOU could find the portal where experienced adventurers such as ourselves could not.")
                chatPlayer(happy, "Believe what you want. Enjoy your little camp fire.")
            }
        }
    }

    /* Wizard */

    private suspend fun Dialogue.wizard() {
        when (lostCity.stage(player)) {
            0 -> {
                chatPlayer(quiz, "Why are all of you standing around here?")
                chatNpc(laugh, "Hahaha you dare talk to a mighty wizard such as myself? I bet you can't even cast windstrike yet amateur!")
                chatPlayer(bored, "...You're an idiot.")
            }
            STAGE_STARTED -> {
                chatPlayer(quiz, "Found that leprechaun yet?")
                chatNpc(laugh, "Hahaha go away amateur! You're not worthy of joining our great group!")
                chatPlayer(bored, "...right.")
            }
            else -> {
                chatNpc(laugh, "Hahaha you're such an amateur!")
                chatNpc(laugh, "Go away and play with some cabbage amateur!")
                chatPlayer(bored, "...right.")
            }
        }
    }

    /* Monk */

    private suspend fun Dialogue.monk() {
        when (lostCity.stage(player)) {
            0 -> {
                chatPlayer(quiz, "Why are all of you standing around here?")
                chatNpc(angry, "None of your business. Get lost.")
            }
            STAGE_STARTED -> {
                chatPlayer(quiz, "Have you found the tree with the leprechaun yet?")
                chatNpc(neutral, "No, we've looked for ages but haven't... Hey! Wait a minute! How did you know about that?")
                chatPlayer(happy, "Thanks for the information!")
                chatNpc(angry, "...You tricked me. I'm not talking to you anymore.")
            }
            else -> chatNpc(angry, "I already told you. I'm not talking to you anymore.")
        }
    }

    private companion object {
        const val WARRIOR = "npc.warrioradventurerpg"
        const val ARCHER = "npc.archeradventurerpg"
        const val WIZARD = "npc.wizardadventuterpg"
        const val MONK = "npc.monkadventurerpg"
    }
}
