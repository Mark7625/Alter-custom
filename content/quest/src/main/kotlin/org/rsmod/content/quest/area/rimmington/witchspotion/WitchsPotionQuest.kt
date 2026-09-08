package org.rsmod.content.quest.area.rimmington.witchspotion

import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Witch's Potion.
 *
 * Stages (stored in `varp.hetty`, endstate 3 from `dbrow.quest_witchspotion`):
 * - [STAGE_STARTED]: Hetty listed the four ingredients.
 * - [STAGE_BREWED]: the ingredients are in the cauldron; the potion waits to be drunk.
 * - [STAGE_COMPLETE]: the potion has been drunk.
 */
@Singleton
class WitchsPotionQuest : QuestScript(
    "quest_witchspotion",
    "varp.hetty",
    rewards { xp("stat.magic", 325.0) },
    ItemRewardDisplay(EYE_OF_NEWT),
) {
    override fun ScriptContext.init() {}

    override fun subTitle(): String =
        "talking to <col=800000>Hetty</col> in her house in <col=800000>Rimmington</col>, " +
            "south of Falador."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            objective(
                "<red>Hetty</red> the witch in Rimmington has offered to brew me a potion that " +
                    "will bring out my darker side. She needs four ingredients first.",
            ) {}

            objective("An <red>eye of newt</red>. Betty's Magic Emporium in Port Sarim sells them.") {
                visibleWhen { quest.getQuestStage(access.player) == STAGE_STARTED }
                hasItem("eye_of_newt", "I have an eye of newt.").strike()
            }

            objective("A <red>rat's tail</red>. The rats around Rimmington should provide one.") {
                visibleWhen { quest.getQuestStage(access.player) == STAGE_STARTED }
                hasItem("rats_tail", "I have a rat's tail. Ewww.").strike()
            }

            objective("An <red>onion</red>. There is an onion field just north of Rimmington.") {
                visibleWhen { quest.getQuestStage(access.player) == STAGE_STARTED }
                hasItem("onion", "I have an onion.").strike()
            }

            objective("A piece of <red>burnt meat</red>. Cooking some meat until it burns should do it.") {
                visibleWhen { quest.getQuestStage(access.player) == STAGE_STARTED }
                hasItem("burnt_meat", "I have some burnt meat.").strike()
            }

            objective(
                "Hetty has brewed the potion in her <red>cauldron</red>. All I have to do now is " +
                    "drink from it.",
            ) {
                visibleWhen { quest.getQuestStage(access.player) == STAGE_BREWED }
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "Hetty the witch in Rimmington offered to brew me a potion to bring out my " +
                    "darker side, in exchange for an eye of newt, a rat's tail, an onion and a " +
                    "piece of burnt meat.",
            )
            line(
                "I gathered them all, she brewed the potion in her cauldron, and I drank it. It " +
                    "tasted horrible, but I could feel the power in it.",
            )
        }

    fun stage(player: Player): Int = quest.getQuestStage(player)

    fun hasAllIngredients(player: Player): Boolean = INGREDIENTS.all { player.inv.count(it) > 0 }

    companion object {
        const val STAGE_STARTED = 1
        const val STAGE_BREWED = 2
        const val STAGE_COMPLETE = 3

        const val EYE_OF_NEWT = "obj.eye_of_newt"
        const val RATS_TAIL = "obj.rats_tail"
        const val ONION = "obj.onion"
        const val BURNT_MEAT = "obj.burnt_meat"

        val INGREDIENTS = listOf(RATS_TAIL, BURNT_MEAT, ONION, EYE_OF_NEWT)
    }
}
