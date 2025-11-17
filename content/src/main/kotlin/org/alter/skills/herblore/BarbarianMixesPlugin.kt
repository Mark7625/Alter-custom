package org.alter.skills.herblore

import dev.openrune.ServerCacheManager.getItem
import org.alter.api.Skills
import org.alter.api.ext.filterableMessage
import org.alter.api.ext.message
import org.alter.api.ext.produceItemBox
import org.alter.game.model.entity.Entity
import org.alter.game.model.entity.Player
import org.alter.game.pluginnew.PluginEvent
import org.alter.game.pluginnew.event.impl.ItemOnItemEvent
import org.alter.rscm.RSCM
import org.alter.tables.herblore.HerbloreBarbarianMixesRow

/**
 * Plugin for creating barbarian mixes (two-dose potion + roe/caviar)
 */
class BarbarianMixesPlugin : PluginEvent() {

    override fun init() {
        // Register item-on-item interactions for barbarian mixes
        onEvent<ItemOnItemEvent> {
            val item1 = fromItem.id
            val item2 = toItem.id

            val herbloreLevel = player.getSkills().getCurrentLevel(Skills.HERBLORE)

            // Find all barbarian mix candidates that could match these items
            val candidates = HerbloreDefinitions.barbarianMixes.filter { mix ->
                (mix.twoDosePotion == item1 && mix.mixIngredient == item2) ||
                (mix.twoDosePotion == item2 && mix.mixIngredient == item1)
            }

            if (candidates.isEmpty()) {
                return@onEvent
            }

            // Filter candidates to only those where player has ALL required ingredients AND meets level requirement
            val validCandidates = candidates.filter { mix ->
                herbloreLevel >= mix.level &&
                player.inventory.contains(mix.twoDosePotion) &&
                player.inventory.contains(mix.mixIngredient)
            }

            if (validCandidates.isEmpty()) {
                return@onEvent
            }

            // Calculate max producible for each candidate
            val candidatesWithMax = validCandidates.map { mix ->
                val potionCount = player.inventory.getItemCount(mix.twoDosePotion)
                val ingredientCount = player.inventory.getItemCount(mix.mixIngredient)
                val maxProducible = minOf(potionCount, ingredientCount)
                mix to maxProducible
            }.filter { (_, max) -> max > 0 }

            if (candidatesWithMax.isEmpty()) {
                return@onEvent
            }

            // Show interface to select mix and quantity
            player.queue {
                val mixItems = candidatesWithMax.map { (mix, _) -> mix.barbarianMix }.toIntArray()
                val maxProducible = candidatesWithMax.maxOf { (_, max) -> max }

                produceItemBox(
                    player,
                    *mixItems,
                    title = "What would you like to make?",
                    maxProducable = maxProducible
                ) { selectedItemId: Int, quantity: Int ->
                    // Find the selected mix
                    val selectedMix = candidatesWithMax.firstOrNull { (mix, _) ->
                        mix.barbarianMix == selectedItemId
                    }?.first ?: return@produceItemBox

                    // Create the specified quantity (up to what they can make)
                    createBarbarianMix(player, selectedMix, quantity)
                }
            }
        }
    }

    /**
     * Creates a barbarian mix from a two-dose potion and roe/caviar.
     * Creates the specified quantity (up to what the player can make).
     */
    private fun createBarbarianMix(
        player: Player,
        mixData: HerbloreBarbarianMixesRow,
        quantity: Int
    ) {
        val herbloreLevel = player.getSkills().getCurrentLevel(Skills.HERBLORE)

        // Check level requirement
        if (herbloreLevel < mixData.level) {
            player.filterableMessage("You need a Herblore level of ${mixData.level} to make this mix.")
            return
        }

        // Check if player has both items
        val hasPotion = player.inventory.contains(mixData.twoDosePotion)
        val hasIngredient = player.inventory.contains(mixData.mixIngredient)

        if (!hasPotion || !hasIngredient) {
            player.message(Entity.NOTHING_INTERESTING_HAPPENS)
            return
        }

        player.queue {
            var cachedLevel = player.getSkills().getCurrentLevel(Skills.HERBLORE)
            var created = 0

            // Repeat until we've created the requested quantity or run out of ingredients
            repeatWhile(delay = 4, immediate = true, canRepeat = {
                created < quantity &&
                player.inventory.contains(mixData.twoDosePotion) &&
                player.inventory.contains(mixData.mixIngredient) &&
                // Check inventory space (removing 2 items, adding 1)
                (player.inventory.freeSlotCount >= 1 || player.inventory.contains(mixData.barbarianMix))
            }) {
                // Check level requirement again (in case it changed)
                if (cachedLevel < mixData.level) {
                    cachedLevel = player.getSkills().getCurrentLevel(Skills.HERBLORE)
                    if (cachedLevel < mixData.level) {
                        stop()
                        return@repeatWhile
                    }
                }

                // Remove ingredients
                val potionRemoved = player.inventory.remove(mixData.twoDosePotion, 1)
                val ingredientRemoved = player.inventory.remove(mixData.mixIngredient, 1)

                if (!potionRemoved.hasSucceeded() || !ingredientRemoved.hasSucceeded()) {
                    // Restore items if removal failed
                    if (potionRemoved.hasSucceeded()) player.inventory.add(mixData.twoDosePotion, 1)
                    if (ingredientRemoved.hasSucceeded()) player.inventory.add(mixData.mixIngredient, 1)
                    player.message(Entity.NOTHING_INTERESTING_HAPPENS)
                    stop()
                    return@repeatWhile
                }

                // Add barbarian mix
                val addResult = player.inventory.add(mixData.barbarianMix, 1)
                if (!addResult.hasSucceeded()) {
                    // Restore items if adding failed
                    player.inventory.add(mixData.twoDosePotion, 1)
                    player.inventory.add(mixData.mixIngredient, 1)
                    player.filterableMessage("You don't have enough inventory space to make this mix.")
                    stop()
                    return@repeatWhile
                }

                // Award XP and message (barbarian mixes typically give 0 XP, but we check anyway)
                if (mixData.xp > 0) {
                    player.addXp(Skills.HERBLORE, mixData.xp.toDouble())
                }

                val mixName = getItem(mixData.barbarianMix)?.name ?: "mix"
                player.filterableMessage("You add the ingredient to the potion.")
                player.filterableMessage("You make a $mixName.")

                created++
            }

            // Stop animation when done
            player.animate(RSCM.NONE)
        }
    }
}

