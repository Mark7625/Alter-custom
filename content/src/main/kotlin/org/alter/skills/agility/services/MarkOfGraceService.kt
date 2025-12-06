package org.alter.skills.agility

import org.alter.api.ext.filterableMessage
import org.alter.game.model.Tile
import org.alter.game.model.entity.GroundItem
import org.alter.game.model.entity.Player
import org.alter.rscm.RSCM.asRSCM
import org.alter.game.model.EntityType

class MarkOfGraceService(
    private val spawnTiles: List<Tile>,
    private val dropChance: Double,
    private val itemName: String = "items.grace",
    private val despawnSeconds: Int = 600
) {

    private val itemId = itemName.asRSCM()

    fun hasMarksSpawned(player: Player, tile: Tile = player.tile): Boolean {
        val chunk = player.world.chunks.getOrCreate(tile)

        return chunk.getEntities<GroundItem>(tile, EntityType.GROUND_ITEM).any {
            it.item == itemId && it.tile == tile && it.isOwnedBy(player)
        }
    }

    fun spawnMarkofGrace(player: Player) {
        if (Math.random() > dropChance) return

        val tile = spawnTiles.random()

        val chunk = player.world.chunks.getOrCreate(tile)

        val existing = if (hasMarksSpawned(player, tile)) {
            chunk.getEntities<GroundItem>(tile, EntityType.GROUND_ITEM).firstOrNull {
                it.item == itemId && it.tile == tile && it.isOwnedBy(player)
            }
        } else {
            null
        }

        if (existing != null) {
            existing.amount += 1
            existing.timeUntilDespawn = despawnSeconds
        } else {
            val mark = GroundItem(
                item = itemId,
                amount = 1,
                tile = tile,
                owner = player
            )
            mark.timeUntilDespawn = despawnSeconds
            player.world.spawn(mark)
        }

        player.filterableMessage("A Mark of Grace appears.")
    }
}

