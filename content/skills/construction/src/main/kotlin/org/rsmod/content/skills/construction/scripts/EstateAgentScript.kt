package org.rsmod.content.skills.construction.scripts

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.stat.baseConstructionLvl
import org.rsmod.api.poh.PohConstants
import org.rsmod.api.poh.PohLocation
import org.rsmod.api.poh.PohManager
import org.rsmod.api.poh.pohHouseLocation
import org.rsmod.api.poh.pohHouseStyle
import org.rsmod.api.script.onOpNpc1
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Estate agent: sells the starter house, relocates it between the house-portal cities,
 * redecorates it into the 13 cache styles, and stocks the Construction cape at 99.
 */
class EstateAgentScript @Inject constructor(private val manager: PohManager) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1("npc.poh_estate_agent") { startDialogue(it.npc) { agentDialogue() } }
    }

    private suspend fun Dialogue.agentDialogue() {
        chatNpc(happy, "Good day! How may I help you?")
        if (!manager.hasHouse(access.player)) {
            offerHouse()
            return
        }
        val choice =
            choice4(
                "Could you move my house please?",
                OPT_MOVE,
                "Could you redecorate my house please?",
                OPT_REDECORATE,
                "Can I buy a Construction cape?",
                OPT_CAPE,
                "Never mind.",
                OPT_NEVER_MIND,
            )
        when (choice) {
            OPT_MOVE -> relocateBranch()
            OPT_REDECORATE -> redecorateBranch()
            OPT_CAPE -> capeBranch()
            OPT_NEVER_MIND -> chatPlayer(neutral, "Never mind.")
        }
    }

    private suspend fun Dialogue.offerHouse() {
        chatNpc(
            happy,
            "I see you don't own a house yet. I can sell you a starter home in Rimmington " +
                "for just ${PohConstants.HOUSE_COST} coins. It comes with a parlour and a garden.",
        )
        val buy =
            choice2(
                "Yes please!",
                true,
                "No thanks.",
                false,
                title = "Buy a house for ${PohConstants.HOUSE_COST} coins?",
            )
        if (!buy) {
            chatPlayer(neutral, "No thanks.")
            return
        }
        if (access.invTotal(access.inv, COINS) < PohConstants.HOUSE_COST) {
            chatNpc(sad, "I'm afraid you don't have enough coins for that.")
            return
        }
        access.invDel(access.inv, COINS, PohConstants.HOUSE_COST)
        manager.createHouse(access.player)
        chatNpc(
            happy,
            "Congratulations on your new home! You'll find the house portal just south of " +
                "Rimmington. Enter in building mode to start furnishing it.",
        )
    }

    private suspend fun Dialogue.relocateBranch() {
        chatPlayer(quiz, "Could you move my house please?")
        val current = PohLocation.forVarValue(access.player.pohHouseLocation)
        val destinations = PohLocation.entries.filter { it != current }
        val labels = destinations.map { "${it.displayName} (${it.relocateCost} coins)" }
        val index = access.menu("Where would you like your house?", hotkeys = false, labels)
        val destination = destinations.getOrNull(index) ?: return
        if (access.invTotal(access.inv, COINS) < destination.relocateCost) {
            chatNpc(sad, "I'm afraid you don't have enough coins for that.")
            return
        }
        access.invDel(access.inv, COINS, destination.relocateCost)
        access.player.pohHouseLocation = destination.varValue
        chatNpc(happy, "Done! Your house is now in ${destination.displayName}.")
    }

    private suspend fun Dialogue.redecorateBranch() {
        chatPlayer(quiz, "Could you redecorate my house please?")
        val labels = STYLES.map { "${it.label} (${it.cost} coins) - level ${it.level}" }
        val index = access.menu("Which style would you like?", hotkeys = false, labels)
        val style = STYLES.getOrNull(index) ?: return
        if (access.player.baseConstructionLvl < style.level) {
            chatNpc(
                sad,
                "You need a Construction level of ${style.level} for the ${style.label} style.",
            )
            return
        }
        if (access.invTotal(access.inv, COINS) < style.cost) {
            chatNpc(sad, "I'm afraid you don't have enough coins for that.")
            return
        }
        access.invDel(access.inv, COINS, style.cost)
        access.player.pohHouseStyle = style.index
        chatNpc(happy, "Done! Your house has been redecorated in the ${style.label} style.")
    }

    private suspend fun Dialogue.capeBranch() {
        chatPlayer(quiz, "Can I buy a Construction cape?")
        if (access.player.baseConstructionLvl < 99) {
            chatNpc(
                neutral,
                "The Construction cape is only for those who have mastered the skill. Come " +
                    "back when you've reached level 99.",
            )
            return
        }
        val buy =
            choice2(
                "Yes please.",
                true,
                "No thanks.",
                false,
                title = "Buy a Construction cape for $CAPE_COST coins?",
            )
        if (!buy) {
            chatPlayer(neutral, "No thanks.")
            return
        }
        if (access.invTotal(access.inv, COINS) < CAPE_COST) {
            chatNpc(sad, "I'm afraid you don't have enough coins for that.")
            return
        }
        access.invDel(access.inv, COINS, CAPE_COST)
        access.invAdd(access.inv, CONSTRUCTION_CAPE, 1)
        access.invAdd(access.inv, CONSTRUCTION_HOOD, 1)
        chatNpc(happy, "Wear it with pride - you've earned it.")
    }

    private data class StyleOption(
        val index: Int,
        val label: String,
        val level: Int,
        val cost: Int,
    )

    private companion object {
        const val OPT_MOVE = 1
        const val OPT_REDECORATE = 2
        const val OPT_CAPE = 3
        const val OPT_NEVER_MIND = 4

        const val COINS = "obj.coins"
        const val CONSTRUCTION_CAPE = "obj.skillcape_construction"
        const val CONSTRUCTION_HOOD = "obj.skillcape_construction_hood"
        const val CAPE_COST = 99_000

        /** Style slots from the datagen table; classic styles use the wiki level/cost gates. */
        val STYLES =
            listOf(
                StyleOption(0, "Basic wood", level = 1, cost = 5_000),
                StyleOption(1, "Basic stone", level = 10, cost = 5_000),
                StyleOption(2, "Whitewashed stone", level = 20, cost = 7_500),
                StyleOption(3, "Fremennik-style wood", level = 30, cost = 10_000),
                StyleOption(4, "Tropical wood", level = 40, cost = 15_000),
                StyleOption(5, "Fancy stone", level = 50, cost = 25_000),
                StyleOption(6, "Deathly mansion", level = 1, cost = 25_000),
                StyleOption(7, "Twisted theme", level = 1, cost = 25_000),
                StyleOption(8, "Hosidius theme", level = 1, cost = 25_000),
                StyleOption(9, "Cosy cabin", level = 1, cost = 25_000),
                StyleOption(10, "Civitas illa fortis", level = 1, cost = 25_000),
                StyleOption(11, "Canifis theme", level = 1, cost = 25_000),
                StyleOption(12, "Wilderness theme", level = 1, cost = 25_000),
            )
    }
}
