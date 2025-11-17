package org.alter.skills.herblore

import org.alter.game.util.DbHelper.Companion.table
import org.alter.game.util.column
import org.alter.game.util.columnOptional
import org.alter.game.util.multiColumnOptional
import org.alter.game.util.vars.IntType
import org.alter.game.util.vars.ObjType
import org.alter.tables.herblore.HerbloreBarbarianMixesRow
import org.alter.tables.herblore.HerbloreCleaningRow
import org.alter.tables.herblore.HerbloreCrushingRow
import org.alter.tables.herblore.HerbloreFinishedRow
import org.alter.tables.herblore.HerbloreSwampTarRow
import org.alter.tables.herblore.HerbloreUnfinishedRow

/**
 * Definitions for herblore potions.
 * Contains data structures for unfinished and finished potions loaded from cache tables.
 */
object HerbloreDefinitions {

    /**
     * Data for creating finished potions (unfinished potion + secondary ingredients)
     */
    data class FinishedPotionData(
        val unfinishedPotion: Int,
        val secondaries: List<Int>,
        val level: Int,
        val xp: Int,
        val finishedPotion: Int?
    ) {
        /**
         * Pre-computed set of all required items (unfinished potion + all secondaries)
         * Cached for performance to avoid creating new Set on every lookup
         */
        val requiredItems: Set<Int> = setOf(unfinishedPotion) + secondaries
    }

    val HerbloreFinishedRow.requiredItems: Set<Int>
        get() = setOf(potPrimary) + secondaries

    /**
     * Loads unfinished potion data from cache table.
     */
    val unfinishedPotions: List<HerbloreUnfinishedRow> = HerbloreUnfinishedRow.all()

    /**
     * Loads finished potion data from cache table.
     */

    val finishedPotions: List<FinishedPotionData> = table("tables.herblore_finished").map { row ->
        val unfinishedPotion = row.column("columns.herblore_finished:pot_primary", ObjType)
        // Always use multiColumnOptional to get ALL secondaries (works for both single and multiple values)
        val secondaries = row.multiColumnOptional("columns.herblore_finished:secondaries", ObjType).filterNotNull()
        val level = row.column("columns.herblore_finished:level_required", IntType)
        val xp = row.column("columns.herblore_finished:xp", IntType)
        val finishedPotion = row.columnOptional("columns.herblore_finished:finished_potion", ObjType)

        FinishedPotionData(
            unfinishedPotion,
            secondaries,
            level,
            xp,
            finishedPotion
        )
    }

    /**
     * Set of all herb item IDs for fast lookup (O(1) instead of O(n))
     */
    val herbItemIds: Set<Int> = unfinishedPotions.mapTo(mutableSetOf()) { it.herbItem }

    /**
     * Reverse lookup map: item ID -> list of potions that use this item
     * This allows O(1) lookup instead of O(n) filtering
     */
    val itemToPotions: Map<Int, List<FinishedPotionData>> = run {
        val map = mutableMapOf<Int, MutableList<FinishedPotionData>>()
        finishedPotions.forEach { potion ->
            // Add unfinished potion -> potion mapping
            map.getOrPut(potion.unfinishedPotion) { mutableListOf() }.add(potion)
            // Add each secondary -> potion mapping
            potion.secondaries.forEach { secondary ->
                map.getOrPut(secondary) { mutableListOf() }.add(potion)
            }
        }
        map
    }

    /**
     * Loads cleaning herb data from cache table.
     */
    val cleaningHerbs: List<HerbloreCleaningRow> = HerbloreCleaningRow.all()

    /**
     * Loads barbarian mix data from cache table.
     */
    val barbarianMixes: List<HerbloreBarbarianMixesRow> = HerbloreBarbarianMixesRow.all()
    /**
     * Map for quick lookup of barbarian mixes by two-dose potion and ingredient
     */
    val mixLookup: Map<Pair<Int, Int>, HerbloreBarbarianMixesRow> = barbarianMixes.associateBy { mix ->
        Pair(mix.twoDosePotion, mix.mixIngredient)
    }

    /**
     * Finds all potion recipes that could match the given two items.
     * Returns candidates that need to be checked for all required ingredients.
     * Uses reverse lookup map for O(1) lookup instead of O(n) filtering.
     *
     * Note: ANY of the required items (unfinished potion or any secondary)
     * can be used to trigger the interaction. For example, with Super Combat Potion, you can
     * use super_strength_3 (secondary) on torstol_potion_unf (unfinished) and it will still work
     * as long as you have all required ingredients.
     */
    fun findPotionCandidates(item1: Int, item2: Int): List<FinishedPotionData> {
        val potions1 = itemToPotions[item1] ?: emptyList()
        val potions2 = itemToPotions[item2] ?: emptyList()
        return (potions1 + potions2).distinct()
    }

    /**
     * Finds a barbarian mix recipe for the given two items.
     */
    fun findBarbarianMix(potion: Int, ingredient: Int): HerbloreBarbarianMixesRow? {
        return mixLookup[Pair(potion, ingredient)] ?: mixLookup[Pair(ingredient, potion)]
    }

    /**
     * Loads swamp tar data from cache table.
     */
    val swampTars: List<HerbloreSwampTarRow> = HerbloreSwampTarRow.all()

    /**
     * Map for quick lookup of swamp tar recipes by herb
     */
    val swampTarLookup: Map<Int, HerbloreSwampTarRow> = swampTars.associateBy { it.herb }

    /**
     * Finds a swamp tar recipe for the given herb.
     */
    fun findSwampTar(herb: Int): HerbloreSwampTarRow? {
        return swampTarLookup[herb]
    }

    /**
     * Loads crushing data from cache table.
     */
    val crushingRecipes: List<HerbloreCrushingRow> = HerbloreCrushingRow.all()

}

