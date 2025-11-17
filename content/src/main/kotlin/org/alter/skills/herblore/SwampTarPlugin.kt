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
import org.alter.tables.herblore.HerbloreSwampTarRow

/**
 * Plugin for creating swamp tar (herb + 15x swamp tar = 15x finished tar)
 * Repeatable action that continues until ingredients run out or player cancels
 */
class SwampTarPlugin : PluginEvent() {

    companion object {
        private const val SWAMP_TAR = "items.swamp_tar"
        private val SWAMP_TAR_ID = SWAMP_TAR.asRSCM()
    }

    override fun init() {
        // Register specific item-on-item interactions for each swamp tar recipe
        // This ensures these handlers are checked before generic event handlers
        HerbloreDefinitions.swampTars.forEach { tarData ->
            onItemOnItem(SWAMP_TAR_ID, tarData.herb) {
                createSwampTar(player, tarData)
            }
        }
    }

    /**
     * Creates swamp tar from a herb and 15x swamp tar.
     * Repeatable action that continues until ingredients run out or player cancels.
     * Consumes 1 herb + 15 swamp tar = 15 finished tar per cycle.
     */
    private fun createSwampTar(
        player: Player,
        tarData: HerbloreSwampTarRow
    ) {
        val herbloreLevel = player.getSkills().getCurrentLevel(Skills.HERBLORE)

        // Check level requirement
        if (herbloreLevel < tarData.level) {
            player.filterableMessage("You need a Herblore level of ${tarData.level} to make this tar.")
            return
        }

        // Check if player has required ingredients (1 herb + 15 swamp tar)
        val hasHerb = player.inventory.contains(tarData.herb)
        val hasSwampTar = player.inventory.getItemCount(SWAMP_TAR_ID) >= 15

        if (!hasHerb || !hasSwampTar) {
            player.message(Entity.NOTHING_INTERESTING_HAPPENS)
            return
        }

        // Check inventory space (removing 16 items, adding 15)
        val freeSlots = player.inventory.freeSlotCount
        val hasFinishedTar = player.inventory.contains(tarData.finishedTar)
        val neededSlots = if (hasFinishedTar) 0 else 15
        if (freeSlots < neededSlots) {
            player.filterableMessage("You don't have enough inventory space to make this tar.")
            return
        }

        player.queue {
            // Cache level check to avoid repeated skill lookups
            var cachedLevel = herbloreLevel

            // Repeat while player has ingredients
            repeatWhile(delay = 3, immediate = true, canRepeat = {
                // Check if player still has required ingredients
                player.inventory.contains(tarData.herb) &&
                player.inventory.getItemCount(SWAMP_TAR_ID) >= 15 &&
                // Check inventory space (removing 16 items, adding 15)
                (player.inventory.freeSlotCount >= 15 || player.inventory.contains(tarData.finishedTar))
            }) {
                // Play herblore animation on each iteration
                player.animate("sequences.human_herbing_vial", interruptable = true)

                // Check level requirement again (in case it changed)
                if (cachedLevel < tarData.level) {
                    cachedLevel = player.getSkills().getCurrentLevel(Skills.HERBLORE)
                    if (cachedLevel < tarData.level) {
                        stop()
                        return@repeatWhile
                    }
                }


                // Remove ingredients (1 herb + 15 swamp tar)
                val herbRemoved = player.inventory.remove(tarData.herb, 1)
                val swampTarRemoved = player.inventory.remove(SWAMP_TAR_ID, 15)

                if (!herbRemoved.hasSucceeded() || !swampTarRemoved.hasSucceeded()) {
                    // Restore items if removal failed
                    if (herbRemoved.hasSucceeded()) player.inventory.add(tarData.herb, 1)
                    if (swampTarRemoved.hasSucceeded()) player.inventory.add(SWAMP_TAR_ID, swampTarRemoved.completed)
                    stop()
                    return@repeatWhile
                }

                // Add finished tar (15x)
                val addResult = player.inventory.add(tarData.finishedTar, 15)
                if (!addResult.hasSucceeded()) {
                    // Restore items if adding failed
                    player.inventory.add(tarData.herb, 1)
                    player.inventory.add(SWAMP_TAR_ID, 15)
                    player.filterableMessage("You don't have enough inventory space to make this tar.")
                    stop()
                    return@repeatWhile
                }

                // Award XP and message
                player.addXp(Skills.HERBLORE, tarData.xp.toDouble())
                val herbName = getItem(tarData.herb)?.name?.lowercase() ?: "herb"
                player.filterableMessage("You add the $herbName to the swamp tar.")
            }

            // Stop animation when done
            player.animate(RSCM.NONE)
        }
    }
}

