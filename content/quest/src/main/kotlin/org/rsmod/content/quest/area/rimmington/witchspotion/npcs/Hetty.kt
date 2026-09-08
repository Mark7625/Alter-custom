package org.rsmod.content.quest.area.rimmington.witchspotion.npcs

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.rimmington.witchspotion.WitchsPotionQuest
import org.rsmod.content.quest.area.rimmington.witchspotion.WitchsPotionQuest.Companion.BURNT_MEAT
import org.rsmod.content.quest.area.rimmington.witchspotion.WitchsPotionQuest.Companion.EYE_OF_NEWT
import org.rsmod.content.quest.area.rimmington.witchspotion.WitchsPotionQuest.Companion.INGREDIENTS
import org.rsmod.content.quest.area.rimmington.witchspotion.WitchsPotionQuest.Companion.ONION
import org.rsmod.content.quest.area.rimmington.witchspotion.WitchsPotionQuest.Companion.RATS_TAIL
import org.rsmod.content.quest.area.rimmington.witchspotion.WitchsPotionQuest.Companion.STAGE_BREWED
import org.rsmod.content.quest.area.rimmington.witchspotion.WitchsPotionQuest.Companion.STAGE_STARTED
import org.rsmod.game.entity.Npc
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Hetty, the witch of Rimmington. Starts Witch's Potion and brews it. */
class Hetty
@Inject
constructor(
    private val witchsPotion: WitchsPotionQuest,
    private val locRepo: LocRepository,
    private val worldRepo: WorldRepository,
) : PluginScript() {

    private val quest
        get() = witchsPotion.quest

    private val cauldronType =
        ServerCacheManager.getObject(CAULDRON.asRSCM(RSCMType.LOC)) ?: error("Missing loc: $CAULDRON")

    override fun ScriptContext.startup() {
        onOpNpc1("npc.hetty") { startDialogue(it.npc) { hetty(it.npc) } }
    }

    private suspend fun Dialogue.hetty(npc: Npc) {
        when (quest.getQuestStage(player)) {
            0 -> beforeQuest()
            STAGE_STARTED -> gathering(npc)
            STAGE_BREWED -> {
                chatNpc(quiz, "Well, are you going to drink the potion or not?")
                chatPlayer(happy, "Yes, I will.")
            }
            else -> afterQuest()
        }
    }

    private suspend fun Dialogue.beforeQuest() {
        chatNpc(quiz, "What could you want with an old woman like me?")
        when (
            choice2(
                "I am in search of a quest.", 1,
                "I've heard that you are a witch.", 2,
            )
        ) {
            1 -> offerQuest()
            2 -> {
                chatPlayer(neutral, "I've heard that you are a witch.")
                chatNpc(neutral, "Yes, it does seem to be getting fairly common knowledge.")
                chatNpc(worried, "I fear I may be getting a visit from the witch hunters of Falador before long.")
                when (
                    choice2(
                        "I am in search of a quest.", 1,
                        "Goodbye.", 2,
                    )
                ) {
                    1 -> offerQuest()
                    2 -> chatPlayer(neutral, "Goodbye.")
                }
            }
        }
    }

    private suspend fun Dialogue.offerQuest() {
        chatPlayer(neutral, "I am in search of a quest.")
        chatNpc(neutral, "Hmmm... Maybe I can think of something for you.")
        chatNpc(quiz, "Would you like to become more proficient in the dark arts?")
        when (
            choice2(
                "Yes.", 1,
                "No.", 2,
                title = "Start the Witch's Potion quest?",
            )
        ) {
            1 -> {
                chatPlayer(shifty, "Yes, help me become one with my darker side.")
                chatNpc(happy, "Okay, I'm going to make a potion to help bring out your darker self.")
                chatNpc(neutral, "You will need certain ingredients.")
                chatPlayer(quiz, "What do I need?")
                chatNpc(neutral, "You need an eye of newt, a rat's tail, an onion... Oh, and a piece of burnt meat.")
                chatPlayer(happy, "Great, I'll go and get them.")
                quest.advanceQuestStage(access)
            }
            2 -> {
                chatPlayer(neutral, "No, I have my principles and honour.")
                chatNpc(bored, "Suit yourself, but you're missing out.")
            }
        }
    }

    private suspend fun Dialogue.gathering(npc: Npc) {
        chatPlayer(neutral, "I've been looking for those ingredients.")
        chatNpc(quiz, "So what have you found so far?")
        if (witchsPotion.hasAllIngredients(player)) {
            brew(npc)
            return
        }
        val held = INGREDIENTS.filter { player.inv.count(it) > 0 }
        if (held.isEmpty()) {
            chatPlayer(sad, "I'm afraid I don't have any of them yet.")
            chatNpc(angry, "Well, I can't make the potion without them! Remember... you need an eye of newt, a rat's tail, an onion, and a piece of burnt meat. Off you go, dear!")
            return
        }
        chatPlayer(neutral, ingredientReport(held))
        chatNpc(neutral, "Great, but I'll need the other ingredients as well.")
    }

    /** "I have the rat's tail (ewww), I don't have any burnt meat, ..." for whatever is carried. */
    private fun ingredientReport(held: List<String>): String {
        val tail = if (RATS_TAIL in held) "I have the rat's tail (ewww)" else "I don't have a rat's tail"
        val meat = if (BURNT_MEAT in held) "I have the burnt meat" else "I don't have any burnt meat"
        val onion = if (ONION in held) "I have an onion" else "I don't have an onion"
        val newt = if (EYE_OF_NEWT in held) "I have the eye of newt, yum!" else "I don't have an eye of newt."
        return "$tail, $meat, $onion, and $newt"
    }

    /** All four ingredients go into the cauldron and Hetty works her charm over it. */
    private suspend fun Dialogue.brew(npc: Npc) {
        chatPlayer(happy, "In fact, I have everything!")
        chatNpc(happy, "Excellent! Can I have them then?")
        for (ingredient in INGREDIENTS) {
            access.invDel(access.inv, ingredient)
        }
        val cauldron = locRepo.findExact(CAULDRON_TILE, cauldronType)
        if (cauldron != null) {
            npc.lockFacing(CAULDRON_TILE)
        }
        npc.anim(CHARM_SEQ)
        worldRepo.soundArea(CAULDRON_TILE, "synth.cauldron_bubbling", radius = SOUND_RADIUS)
        if (cauldron != null) {
            worldRepo.locAnim(cauldron, CAULDRON_SEQ)
        }
        mesbox("You pass the ingredients to Hetty and she puts them all into her cauldron. Hetty closes her eyes and begins to chant. The cauldron bubbles mysteriously.")
        npc.clearFacingLock()
        npc.facePlayer(player)
        quest.advanceQuestStage(access)
        chatPlayer(quiz, "Well, is it ready?")
        chatNpc(happy, "Okay, now drink from the cauldron.")
    }

    private suspend fun Dialogue.afterQuest() {
        chatNpc(quiz, "What could you want with an old woman like me?")
        when (
            choice2(
                "Could you brew me another potion?", 1,
                "I've heard that you are a witch.", 2,
            )
        ) {
            1 -> {
                chatPlayer(quiz, "Could you brew me another potion?")
                chatNpc(neutral, "One taste of your darker side is quite enough, dear. Any more and you'd never get the smell of newt out of your clothes.")
            }
            2 -> {
                chatPlayer(neutral, "I've heard that you are a witch.")
                chatNpc(neutral, "And you drank my potion all the same. I'd say we're both past pretending, wouldn't you?")
            }
        }
    }

    private companion object {
        const val CAULDRON = "loc.hettycauldron"

        /** Hetty's cauldron in her house in Rimmington. */
        val CAULDRON_TILE = CoordGrid(2967, 3205, 0)

        const val CHARM_SEQ = "seq.witchcharms"
        const val CAULDRON_SEQ = "seq.cauldron"
        const val SOUND_RADIUS = 8
    }
}
