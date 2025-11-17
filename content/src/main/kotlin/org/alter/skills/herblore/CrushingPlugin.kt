package org.alter.skills.herblore

import dev.openrune.ServerCacheManager.getItem
import org.alter.api.Skills
import org.alter.api.ext.filterableMessage
import org.alter.api.ext.message
import org.alter.game.model.entity.Entity
import org.alter.game.model.entity.Player
import org.alter.game.pluginnew.PluginEvent
import org.alter.game.pluginnew.event.impl.onItemOnItem
import org.alter.rscm.RSCM
import org.alter.rscm.RSCM.asRSCM
import org.alter.tables.herblore.HerbloreCrushingData

/**
 * Plugin for crushing items with pestle and mortar.
 * Auto-crushes every 3 ticks when multiple items are available.
 */
class CrushingPlugin : PluginEvent() {

    override fun init() {
        // Register specific item-on-item interactions for each crushable item
        // This ensures these handlers are checked before generic event handlers
        HerbloreDefinitions.crushingRecipes.forEach { crushingData ->
            onItemOnItem("items.pestle_and_mortar", crushingData.item) {
                crushItem(player, crushingData)
            }
        }
    }

    /**
     * Crushes an item with pestle and mortar.
     * Repeatable action that continues every 3 ticks until items run out or player cancels.
     */
    private fun crushItem(
        player: Player,
        crushingData: HerbloreCrushingData
    ) {
        val herbloreLevel = player.getSkills().getCurrentLevel(Skills.HERBLORE)

        // Check level requirement
        if (herbloreLevel < crushingData.level) {
            player.filterableMessage("You need a Herblore level of ${crushingData.level} to crush this item.")
            return
        }

        // Check if player has pestle and mortar and crushable item
        val hasPestle = player.inventory.contains("items.pestle_and_mortar")
        val hasItem = player.inventory.contains(crushingData.item)

        if (!hasPestle || !hasItem) {
            player.message(Entity.NOTHING_INTERESTING_HAPPENS)
            return
        }

        // Check inventory space (removing 1 item, adding 1)
        if (player.inventory.freeSlotCount < 1 && !player.inventory.contains(crushingData.crushedItem)) {
            player.filterableMessage("You don't have enough inventory space.")
            return
        }

        player.queue {
            // Cache level check to avoid repeated skill lookups
            var cachedLevel = herbloreLevel

            // Repeat while player has items to crush (auto-crush every 3 ticks)
            repeatWhile(delay = 3, immediate = true, canRepeat = {
                // Check if player still has pestle and crushable item
                player.inventory.contains("items.pestle_and_mortar") &&
                player.inventory.contains(crushingData.item) &&
                // Check inventory space (removing 1 item, adding 1)
                (player.inventory.freeSlotCount >= 1 || player.inventory.contains(crushingData.crushedItem))
            }) {
                player.animate("sequences.human_herbing_grind", interruptable = false)

                // Check level requirement again (in case it changed)
                if (cachedLevel < crushingData.level) {
                    cachedLevel = player.getSkills().getCurrentLevel(Skills.HERBLORE)
                    if (cachedLevel < crushingData.level) {
                        stop()
                        return@repeatWhile
                    }
                }

                // Remove crushable item
                val removeResult = player.inventory.remove(crushingData.item, 1)
                if (!removeResult.hasSucceeded()) {
                    stop()
                    return@repeatWhile
                }

                // Add crushed item
                val addResult = player.inventory.add(crushingData.crushedItem, 1)
                if (!addResult.hasSucceeded()) {
                    // Restore item if adding failed
                    player.inventory.add(crushingData.item, 1)
                    player.filterableMessage("You don't have enough inventory space.")
                    stop()
                    return@repeatWhile
                }

                // Award XP (most crushing gives 0 XP, but we check anyway)
                if (crushingData.xp > 0) {
                    player.addXp(Skills.HERBLORE, crushingData.xp.toDouble())
                }

                val itemName = getItem(crushingData.item)?.name?.lowercase() ?: "item"
                player.filterableMessage("You crush the $itemName.")
            }

            // Stop any animation when done
            player.animate(RSCM.NONE)
        }
    }
}

