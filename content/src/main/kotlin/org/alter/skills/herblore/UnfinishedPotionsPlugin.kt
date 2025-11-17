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
import org.alter.rscm.RSCM.asRSCM
import org.alter.tables.herblore.HerbloreUnfinishedData

/**
 * Plugin for creating unfinished potions (clean herb + vial of water)
 */
class UnfinishedPotionsPlugin : PluginEvent() {

    companion object {
        private const val VIAL_OF_WATER = "items.vial_water"
        private val VIAL_OF_WATER_ID = VIAL_OF_WATER.asRSCM()
    }

    override fun init() {
        // Register herb + vial of water interactions
        onEvent<ItemOnItemEvent> {
            val item1 = fromItem.id
            val item2 = toItem.id

            // Check if this is a herb + vial interaction
            val isHerbVial = (item1 == VIAL_OF_WATER_ID && HerbloreDefinitions.herbItemIds.contains(item2)) ||
                             (item2 == VIAL_OF_WATER_ID && HerbloreDefinitions.herbItemIds.contains(item1))

            if (!isHerbVial) {
                return@onEvent
            }

            val herbloreLevel = player.getSkills().getCurrentLevel(Skills.HERBLORE)

            // Find all valid unfinished potion candidates (player has herb, has vial, meets level)
            val validCandidates = HerbloreDefinitions.unfinishedPotions.filter { potionData ->
                val hasHerb = player.inventory.contains(potionData.herbItem)
                val hasVial = player.inventory.contains(VIAL_OF_WATER_ID)
                val meetsLevel = herbloreLevel >= potionData.level
                hasHerb && hasVial && meetsLevel
            }

            if (validCandidates.isEmpty()) {
                return@onEvent
            }

            // Calculate max producible for each candidate
            val candidatesWithMax = validCandidates.map { potion ->
                val herbCount = player.inventory.getItemCount(potion.herbItem)
                val vialCount = player.inventory.getItemCount(VIAL_OF_WATER_ID)
                val maxProducible = minOf(herbCount, vialCount)
                potion to maxProducible
            }.filter { (_, max) -> max > 0 }

            if (candidatesWithMax.isEmpty()) {
                return@onEvent
            }

            // Show interface to select potion and quantity
            player.queue {
                val potionItems = candidatesWithMax.map { (potion, _) -> potion.unfinishedPotion }.toIntArray()
                val maxProducible = candidatesWithMax.maxOf { (_, max) -> max }

                produceItemBox(
                    player,
                    *potionItems,
                    title = "What would you like to make?",
                    maxProducable = maxProducible
                ) { selectedItemId: Int, quantity: Int ->
                    // Find the selected potion
                    val selectedPotion = candidatesWithMax.firstOrNull { (potion, _) ->
                        potion.unfinishedPotion == selectedItemId
                    }?.first ?: return@produceItemBox

                    // Create the specified quantity (up to what they can make)
                    createUnfinishedPotion(player, selectedPotion, quantity)
                }
            }
        }
    }

    /**
     * Creates an unfinished potion from a clean herb and vial of water.
     * Creates the specified quantity (up to what the player can make).
     */
    private fun createUnfinishedPotion(
        player: Player,
        potionData: HerbloreUnfinishedData,
        quantity: Int
    ) {
        val herbloreLevel = player.getSkills().getCurrentLevel(Skills.HERBLORE)

        // Check level requirement
        if (herbloreLevel < potionData.level) {
            player.filterableMessage("You need a Herblore level of ${potionData.level} to make this potion.")
            return
        }

        // Check if player has both items
        val hasHerb = player.inventory.contains(potionData.herbItem)
        val hasVial = player.inventory.contains(VIAL_OF_WATER_ID)

        if (!hasHerb || !hasVial) {
            player.filterableMessage("You don't have all the ingredients needed to make this potion.")
            return
        }

        player.queue {
            var cachedLevel = player.getSkills().getCurrentLevel(Skills.HERBLORE)
            var created = 0

            // Repeat until we've created the requested quantity or run out of ingredients
            repeatWhile(delay = 4, immediate = true, canRepeat = {
                created < quantity &&
                player.inventory.contains(potionData.herbItem) &&
                player.inventory.contains(VIAL_OF_WATER_ID) &&
                // Check inventory space (removing 2 items, adding 1)
                (player.inventory.freeSlotCount >= 1 || player.inventory.contains(potionData.unfinishedPotion))
            }) {
                // Play herblore animation
                player.animate("sequences.human_herbing_vial", interruptable = true)

                // Check level requirement again (in case it changed)
                if (cachedLevel < potionData.level) {
                    cachedLevel = player.getSkills().getCurrentLevel(Skills.HERBLORE)
                    if (cachedLevel < potionData.level) {
                        stop()
                        return@repeatWhile
                    }
                }

                // Remove ingredients
                val herbRemoved = player.inventory.remove(potionData.herbItem, 1)
                val vialRemoved = player.inventory.remove(VIAL_OF_WATER_ID, 1)

                if (!herbRemoved.hasSucceeded() || !vialRemoved.hasSucceeded()) {
                    // Restore items if removal failed
                    if (herbRemoved.hasSucceeded()) player.inventory.add(potionData.herbItem, 1)
                    if (vialRemoved.hasSucceeded()) player.inventory.add(VIAL_OF_WATER_ID, 1)
                    player.message(Entity.NOTHING_INTERESTING_HAPPENS)
                    stop()
                    return@repeatWhile
                }

                // Add unfinished potion
                val addResult = player.inventory.add(potionData.unfinishedPotion, 1)
                if (!addResult.hasSucceeded()) {
                    // Restore items if adding failed
                    player.inventory.add(potionData.herbItem, 1)
                    player.inventory.add(VIAL_OF_WATER_ID, 1)
                    player.filterableMessage("You don't have enough inventory space to make this potion.")
                    stop()
                    return@repeatWhile
                }

                // Award XP and message
                player.addXp(Skills.HERBLORE, potionData.xp.toDouble())
                val herbName = getItem(potionData.herbItem)?.name?.lowercase() ?: "herb"
                player.filterableMessage("You put the $herbName into the vial of water.")

                created++
            }

            // Stop animation when done
            player.animate(RSCM.NONE)
        }
    }
}

