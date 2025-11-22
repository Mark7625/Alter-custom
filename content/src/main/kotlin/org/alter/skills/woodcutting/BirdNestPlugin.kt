package org.alter.skills.woodcutting

import org.alter.api.ChatMessageType
import org.alter.api.Skills
import org.alter.api.ext.filterableMessage
import org.alter.api.ext.findClosestWalkableTile
import org.alter.api.ext.inWilderness
import org.alter.api.ext.message
import org.alter.api.ext.replaceItem
import org.alter.api.ext.toItem
import org.alter.game.model.entity.GroundItem
import org.alter.game.model.entity.Player
import org.alter.game.model.weight.impl.WeightItem
import org.alter.game.pluginnew.MenuOption
import org.alter.game.pluginnew.PluginEvent
import org.alter.game.pluginnew.event.impl.ItemClickEvent
import org.alter.rscm.RSCM.asRSCM
import kotlin.random.Random

sealed class NestResult {
    data class Regular(val type: NestType) : NestResult()
    data class Clue(val clueType: ClueType?) : NestResult()
}

enum class ClueType(val clueNest: String, val clueBox: String) {
    BEGINNER("items.wc_clue_nest_beginner", "items.league_clue_box_beginner"),
    EASY("items.wc_clue_nest_easy", "items.league_clue_box_easy"),
    MEDIUM("items.wc_clue_nest_medium", "items.league_clue_box_medium"),
    HARD("items.wc_clue_nest_hard", "items.league_clue_box_hard"),
    ELITE("items.wc_clue_nest_elite", "items.league_clue_box_elite");

    val clueNestID = clueNest.asRSCM()
    val clueBoxID = clueBox.asRSCM()
}

private const val ALWAYS = 0
private const val COMMON = 256
private const val UNCOMMON = 32
private const val RARE = 8
private const val VERY_RARE = 1

enum class NestType(val item: String, val rewards: List<WeightItem> = emptyList()) {
    SEED(
        "items.bird_nest_seeds", rewards = listOf(
            WeightItem("items.acorn", 1, COMMON),
            WeightItem("items.apple_tree_seed", 1, COMMON),
            WeightItem("items.banana_tree_seed", 1, COMMON),
            WeightItem("items.orange_tree_seed", 1, UNCOMMON),
            WeightItem("items.willow_seed", 1, UNCOMMON),
            WeightItem("items.curry_tree_seed", 1, UNCOMMON),
            WeightItem("items.maple_seed", 1, UNCOMMON),
            WeightItem("items.pineapple_tree_seed", 1, RARE),
            WeightItem("items.papaya_tree_seed", 1, RARE),
            WeightItem("items.palm_tree_seed", 1, RARE),
            WeightItem("items.calquat_tree_seed", 1, RARE),
            WeightItem("items.yew_seed", 1, RARE),
            WeightItem("items.magic_tree_seed", 1, VERY_RARE),
            WeightItem("items.spirit_tree_seed", 1, VERY_RARE)
        )
    ),
    RING(
        "items.bird_nest_ring", rewards = listOf(
            WeightItem("items.gold_ring", 1, COMMON),
            WeightItem("items.sapphire_ring", 1, COMMON),
            WeightItem("items.emerald_ring", 1, COMMON),
            WeightItem("items.ruby_ring", 1, COMMON),
            WeightItem("items.diamond_ring", 1, COMMON)
        )
    ),
    EGG_BLUE("items.bird_nest_egg_blue", listOf(WeightItem("items.bird_egg_blue", 1, ALWAYS))),
    EGG_RED("items.bird_nest_egg_red", listOf(WeightItem("items.bird_egg_red", 1, ALWAYS))),
    EGG_GREEN("items.bird_nest_egg_green", listOf(WeightItem("items.bird_egg_green", 1, ALWAYS))),
    CLUE("items.bird_nest_egg_green"); // No rewards for clue nests

    val nestID = item.asRSCM()

    companion object {
        val nestIDs = entries.map { it.item }
        val nestIDss = entries.map { it.nestID }
    }
}

class BirdNestPlugin : PluginEvent() {

    override fun init() {
        on<TreeLogObtainedEvent> {
            where { treeData.logItem != "items.blisterwood_logs".asRSCM() }
            then { rollBirdNest(player, clueBaseChance) }
        }

        NestType.entries.forEach { nestType ->
            on<ItemClickEvent> {
                where { nestType.nestID == item && op == MenuOption.OP2 }
                then { handleNestSearch(player) }
            }
        }
    }

    private fun handleNestSearch(player: Player) {
        player.queue {
            repeatWhile(delay = 4, immediate = true, canRepeat = { hasNest(player) }) {
                val nestItem = player.inventory.firstOrNull { nest -> nest != null && NestType.nestIDss.contains(nest.id) }
                if (nestItem == null) {
                    stop()
                    return@repeatWhile
                }

                val nestType = NestType.entries.find { it.nestID == nestItem.id }
                if (nestType == null) {
                    player.filterableMessage("This nest cannot be searched.")
                    stop()
                    return@repeatWhile
                }

                val reward = nestType.rewards.random()
                if (player.inventory.add(reward.item).hasFailed()) {
                    player.filterableMessage("<col=B50A11>You need more room to search this.")
                    stop()
                }

                player.replaceItem(nestType.nestID, "items.bird_nest_empty".asRSCM())
                player.message(
                    "You take a ${reward.item.toItem().getName()} out of the bird's nest.",
                    ChatMessageType.GAME_MESSAGE
                )
            }
        }
    }
    private fun hasNest(player: Player) = player.inventory.any { it != null && NestType.nestIDss.contains(it.id) }

    private fun rollBirdNest(player: Player, clueBaseChance: Int) {
        val chance = 1.0 / 256.0 * if (isWearingWoodcuttingCape(player)) 1.1 else 1.0

        if (Random.nextDouble() < chance) {
            player.message("<col=B50A11>A bird's nest falls out of the tree.",ChatMessageType.GAME_MESSAGE)

            val nestID = when (val nest = determineNestType(player, isRedwood = false, clueBaseChance)) {
                is NestResult.Regular -> nest.type.nestID
                is NestResult.Clue -> if (xMarksSpotFinished()) nest.clueType?.clueBoxID else nest.clueType?.clueNestID
            }

            nestID?.let {
                val spawnTile = player.findClosestWalkableTile() ?: player.tile
                player.world.spawn(GroundItem(it, 1, spawnTile, player).apply { timeUntilDespawn = 200 })
            }
        }
    }

    private fun determineNestType(player: Player, isRedwood: Boolean, clueBaseChance: Int): NestResult {
        if (isRedwood) return NestResult.Clue(determineClueType(player, clueBaseChance))

        var seedSlots = 65.0
        val ringSlots = 32.0
        val eggSlots = 3.0

        if (isWearingStrungRabbitFoot(player)) seedSlots -= 5

        val nestProbs = mutableMapOf(
            NestType.SEED to seedSlots,
            NestType.RING to ringSlots,
            NestType.EGG_BLUE to eggSlots / 3,
            NestType.EGG_RED to eggSlots / 3,
            NestType.EGG_GREEN to eggSlots / 3,
            NestType.CLUE to 0.0
        )

        val boostTypes = when (getTwitchersGlovesBoost(player)) {
            NestType.SEED -> listOf(NestType.SEED)
            NestType.RING -> listOf(NestType.RING)
            NestType.CLUE -> listOf(NestType.CLUE)
            else -> listOf(NestType.EGG_BLUE, NestType.EGG_RED, NestType.EGG_GREEN)
        }

        boostTypes.forEach { type -> nestProbs[type] = nestProbs.getValue(type) * 1.2 }

        val total = nestProbs.values.sum()
        nestProbs.replaceAll { _, v -> v / total }

        val roll = Random.nextDouble()
        var cumulative = 0.0

        for ((type, prob) in nestProbs) {
            cumulative += prob
            if (roll < cumulative) {
                return if (type == NestType.CLUE) {
                    NestResult.Clue(determineClueType(player, clueBaseChance))
                } else {
                    NestResult.Regular(type)
                }
            }
        }

        return NestResult.Regular(NestType.SEED)
    }

    private fun determineClueType(player: Player, baseChance: Int): ClueType? {
        val woodcutting = player.getSkills().getBaseLevel(Skills.WOODCUTTING)
        val inWilderness = player.inWilderness()
        val hasWealthRing = player.equipment.containsAny(
            "items.ring_of_wealth_i",
            "items.ring_of_wealth_i1",
            "items.ring_of_wealth_i2",
            "items.ring_of_wealth_i3",
            "items.ring_of_wealth_i4"
        )

        val eligibleTiers = ClueType.entries.filterNot { player.hasClueOfType(it) }

        for (clue in eligibleTiers) {
            val ca = if (hasCombatAchievementTier(player, clue)) 0.95 else 1.0
            val clueTierModifier = when (clue) {
                ClueType.BEGINNER -> 0.2
                ClueType.EASY -> 1.7
                ClueType.MEDIUM -> 2.0
                ClueType.HARD -> 3.3
                ClueType.ELITE -> 10.0
            }

            var denominator = ((baseChance * ca) / 100.0 + woodcutting * clueTierModifier).toInt()
            if (inWilderness && hasWealthRing) denominator /= 2

            if (Random.nextDouble() < 1.0 / denominator) return clue
        }

        return null
    }

    private fun Player.hasClueOfType(type: ClueType) = inventory.contains(type.clueNestID) || bank.contains(type.clueNestID)

    private fun isWearingWoodcuttingCape(player: Player) = player.equipment.containsAny(
        "items.skillcape_woodcutting",
        "items.skillcape_woodcutting_trimmed"
    )

    private fun isWearingStrungRabbitFoot(player: Player) = player.equipment.contains("items.hunting_strung_rabbit_foot")

    private fun getTwitchersGlovesBoost(player: Player) = if (player.equipment.contains("items.forestry_gloves")) NestType.SEED else null

    // Placeholder implementations
    private fun xMarksSpotFinished() = false
    private fun hasCombatAchievementTier(player: Player, clue: ClueType) = false
}