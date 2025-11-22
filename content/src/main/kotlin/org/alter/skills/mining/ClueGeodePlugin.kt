package org.alter.skills.mining

import org.alter.api.ext.findClosestWalkableTile
import org.alter.api.ext.message
import org.alter.rscm.RSCM.asRSCM
import org.alter.game.model.entity.GroundItem
import org.alter.game.model.entity.Player
import org.alter.game.pluginnew.PluginEvent
import kotlin.random.Random


class ClueGeodePlugin : PluginEvent() {

    companion object {
        private val CLUE_GEODES = listOf(
            "items.mining_clue_geode_beginner",
            "items.mining_clue_geode_easy",
            "items.mining_clue_geode_medium",
            "items.mining_clue_geode_hard",
            "items.mining_clue_geode_elite",
        ).map { it.asRSCM() }
    }

    override fun init() {
        on<RockOreObtainedEvent> {
            then { rollClueGeode(player, clueBaseChance) }
        }
    }

    private fun rollClueGeode(player: Player, baseChance: Int) {
        if (baseChance <= 0) return

        val shouldDropGeode = Random.nextInt(baseChance) == 0
        if (!shouldDropGeode) return

        giveGeode(player, CLUE_GEODES.random())
    }

    private fun giveGeode(player: Player, geodeId: Int) {
        if (player.inventory.add(geodeId, 1).hasSucceeded()) {
            player.message("You find a clue geode!")
            return
        }

        val spawnTile = player.findClosestWalkableTile() ?: player.tile
        player.world.spawn(GroundItem(geodeId, 1, spawnTile, player))
        player.message("A clue geode falls to the ground as your inventory is too full.")
    }
}
