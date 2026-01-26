package org.alter.skills.cooking

import dev.openrune.ServerCacheManager.getItem
import org.alter.api.Skills
import org.alter.api.computeSkillingSuccess
import org.alter.api.ext.filterableMessage
import org.alter.api.ext.message
import org.alter.api.ext.produceItemBox
import org.alter.game.model.entity.GameObject
import org.alter.game.model.entity.Player
import org.alter.game.pluginnew.MenuOption
import org.alter.game.pluginnew.PluginEvent
import org.alter.game.pluginnew.event.impl.ItemOnObject
import org.alter.game.pluginnew.event.impl.ObjectClickEvent
import org.alter.rscm.RSCM
import org.alter.rscm.RSCM.asRSCM
import org.alter.rscm.RSCMType
import org.alter.skills.firemaking.ColoredLogs
import org.generated.tables.cooking.CookingRecipesRow
import kotlin.random.Random

class CookingEvents : PluginEvent() {

    private enum class CookStation {
        FIRE,
        RANGE
    }

    override fun init() {

        // Click range/fire -> open cooking menu
        on<ObjectClickEvent> {
            where { optionName.equals("cook", ignoreCase = true) }
            then {
                val station = stationFor(gameObject)
                openCookingMenu(player, station, gameObject)
            }
        }

        // Use raw food on range/fire -> open cooking menu for that item
        on<ItemOnObject> {
            where {
                option.equals("cook", ignoreCase = true) &&
                    CookingRecipes.byRaw.containsKey(item.id)
            }
            then {
                val recipe = CookingRecipes.byRaw[item.id] ?: return@then
                val station = stationFor(gameObject)
                openCookingMenu(player, station, gameObject, listOf(recipe))
            }
        }
    }

    private fun stationFor(gameObject: GameObject): CookStation {
        val fireIds = buildFireObjectIds()
        return if (fireIds.contains(gameObject.internalID)) CookStation.FIRE else CookStation.RANGE
    }

    private fun buildFireObjectIds(): Set<Int> {
        val fireKeys = ColoredLogs.COLOURED_LOGS.values.map { it.second } +
            "objects.fire" +
            "objects.forestry_fire"

        val fireInts = fireKeys.mapNotNull { key ->
            runCatching { key.asRSCM() }.getOrNull()
        }

        return fireInts.toSet()
    }

    private fun openCookingMenu(
        player: Player,
        station: CookStation,
        gameObject: GameObject,
        only: List<CookingRecipesRow>? = null
    ) {
        val cookingLevel = player.getSkills().getCurrentLevel(Skills.COOKING)

        val candidates = (only ?: CookingRecipes.all)
            .filter { recipe ->
                player.inventory.contains(recipe.raw) && cookingLevel >= recipe.level
            }

        if (candidates.isEmpty()) {
            player.message("You have nothing you can cook.")
            return
        }

        // The SKILL_MULTI chatbox UI supports up to 18 item options.
        val maxOptions = 18
        val menuCandidates = candidates
            .sortedWith(
                compareBy<CookingRecipesRow> { it.level }
                    .thenBy { getItem(it.cooked)?.name ?: "" }
            )
            .take(maxOptions)

        val maxProducible = menuCandidates.maxOf { recipe -> player.inventory.getItemCount(recipe.raw) }

        player.queue {
            val cookedItems = menuCandidates.map { it.cooked }.toIntArray()

            produceItemBox(
                player,
                *cookedItems,
                title = "What would you like to cook?",
                maxProducable = maxProducible
            ) { cookedItemId, qty ->
                val recipe = menuCandidates.firstOrNull { it.cooked == cookedItemId } ?: return@produceItemBox

                cook(player, station, gameObject, recipe, qty)
            }
        }
    }

    private fun cook(
        player: Player,
        station: CookStation,
        gameObject: GameObject,
        recipe: CookingRecipesRow,
        quantity: Int
    ) {
        val cookingLevel = player.getSkills().getCurrentLevel(Skills.COOKING)
        if (cookingLevel < recipe.level) {
            player.filterableMessage("You need a Cooking level of ${recipe.level} to cook this.")
            return
        }

        val objectTile = gameObject.tile
        val objectId = gameObject.internalID

        player.queue {
            var cooked = 0

            player.filterableMessage("You begin cooking...")

            repeatWhile(delay = 4, immediate = true, canRepeat = {
                cooked < quantity &&
                    player.inventory.contains(recipe.raw) &&
                    isStillAtStation(player, objectTile, objectId)
            }) {

                // Optional animation (safe fallback if missing)
                runCatching { player.animate("sequences.human_cooking", interruptable = true) }
                    .onFailure { player.animate(RSCM.NONE) }

                val removed = player.inventory.remove(recipe.raw, 1)
                if (!removed.hasSucceeded()) {
                    stop()
                    return@repeatWhile
                }

                val success = rollCookSuccess(player, station, recipe)
                val outputItem = if (success) recipe.cooked else recipe.burnt

                val addResult = player.inventory.add(outputItem, 1)
                if (!addResult.hasSucceeded()) {
                    // restore raw
                    player.inventory.add(recipe.raw, 1)
                    player.filterableMessage("You don't have enough inventory space.")
                    stop()
                    return@repeatWhile
                }

                if (success) {
                    player.addXp(Skills.COOKING, recipe.xp.toDouble())
                    val cookedName = getItem(recipe.cooked)?.name?.lowercase() ?: "food"
                    player.filterableMessage("You cook the $cookedName.")
                } else {
                    val burntName = getItem(recipe.burnt)?.name?.lowercase() ?: "food"
                    player.filterableMessage("You accidentally burn the $burntName.")
                }

                cooked++
                wait(1)
            }

            player.animate(RSCM.NONE)
        }
    }

    private fun rollCookSuccess(
        player: Player,
        station: CookStation,
        recipe: CookingRecipesRow
    ): Boolean {
        val cookingLevel = player.getSkills().getCurrentLevel(Skills.COOKING)

        val stopBurn = when (station) {
            CookStation.FIRE -> recipe.stopBurnFire
            CookStation.RANGE -> recipe.stopBurnRange
        }.coerceAtLeast(recipe.level)

        if (cookingLevel >= stopBurn) {
            return true
        }

        val baseSuccessAtReq = when (station) {
            CookStation.FIRE -> 0.30
            CookStation.RANGE -> 0.45
        }

        val progress = ((cookingLevel - recipe.level).toDouble() / (stopBurn - recipe.level).toDouble())
            .coerceIn(0.0, 1.0)

        val chance = (baseSuccessAtReq + (1.0 - baseSuccessAtReq) * progress).coerceIn(0.0, 1.0)

        // Blend with the shared OSRS skilling success curve for nicer scaling.
        // This keeps early levels from feeling too punishing while still rewarding levels.
        val curve = computeSkillingSuccess(low = 64, high = 256, level = (cookingLevel - recipe.level + 1).coerceIn(1, 99))
        val finalChance = (chance * 0.70 + curve * 0.30).coerceIn(0.0, 1.0)

        return Random.nextDouble() < finalChance
    }

    private fun isStillAtStation(player: Player, objectTile: org.alter.game.model.Tile, objectId: Int): Boolean {
        if (player.tile.getDistance(objectTile) > 1) return false

        val world = player.world
        val obj = world.getObject(objectTile, type = 10) ?: world.getObject(objectTile, type = 11)
        return obj != null && obj.internalID == objectId
    }
}
