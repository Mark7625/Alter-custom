package org.alter.skills.herblore

import dev.openrune.ServerCacheManager.getItem
import org.alter.api.Skills
import org.alter.api.ext.filterableMessage
import org.alter.game.model.entity.Player
import org.alter.game.pluginnew.PluginEvent
import org.alter.game.pluginnew.event.impl.ItemClickEvent
import org.alter.tables.herblore.HerbloreCleaningRow

/**
 * Plugin for cleaning grimy herbs.
 * One-time action - player must click each grimy herb individually.
 * No repeatable delay, instant action.
 */
class CleaningHerbsPlugin : PluginEvent() {

    override fun init() {
        HerbloreDefinitions.cleaningHerbs.forEach { cleaningData ->
            on<ItemClickEvent> {
                where { item == cleaningData.grimyHerb && !player.isLocked() }
                then {
                    cleanHerb(player, cleaningData, item, slot)
                }
            }
        }
    }

    private fun cleanHerb(
        player: Player,
        cleaningData: HerbloreCleaningRow,
        grimyHerb: Int,
        slot: Int
    ) {
        val herbloreLevel = player.getSkills().getCurrentLevel(Skills.HERBLORE)

        // Check level requirement
        if (herbloreLevel < cleaningData.level) {
            player.filterableMessage("You need a Herblore level of ${cleaningData.level} to clean this herb.")
            return
        }

        // Check if player has the grimy herb
        if (!player.inventory.contains(grimyHerb)) {
            return
        }

        // Check inventory space (removing 1 item, adding 1)
        if (player.inventory.freeSlotCount < 1 && !player.inventory.contains(cleaningData.cleanHerb)) {
            player.filterableMessage("You don't have enough inventory space.")
            return
        }

        // Remove grimy herb
        val removeResult = player.inventory.remove(grimyHerb, 1, beginSlot = slot)
        if (!removeResult.hasSucceeded()) {
            return
        }

        // Add clean herb
        val addResult = player.inventory.add(cleaningData.cleanHerb, 1)
        if (!addResult.hasSucceeded()) {
            // Restore grimy herb if adding failed
            player.inventory.add(grimyHerb, 1)
            player.filterableMessage("You don't have enough inventory space.")
            return
        }

        // Award XP and message
        if (cleaningData.xp > 0) {
            player.addXp(Skills.HERBLORE, cleaningData.xp.toDouble())
        }
    }
}

