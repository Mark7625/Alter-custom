package org.alter.plugins.content.commands.commands

import com.sun.management.HotSpotDiagnosticMXBean
import org.alter.api.ext.player
import org.alter.game.Server
import org.alter.game.model.Tile
import org.alter.game.model.World
import org.alter.game.model.entity.GroundItem
import org.alter.game.model.move.moveTo
import org.alter.game.plugin.KotlinPlugin
import org.alter.game.plugin.PluginRepository
import org.alter.rscm.RSCM.getRSCM
import java.lang.management.ManagementFactory
import java.nio.file.Paths

/**
 * @author CloudS3c 12/30/2024
 */
class MarksPlugin(
    r: PluginRepository,
    world: World,
    server: Server
) : KotlinPlugin(r, world, server) {


    private val MARK_SPAWN_TILES = listOf(
        Tile(2471, 3422, 1),
        Tile(2474, 3418, 2),
        Tile(2488, 3421, 2)
    )

    init {
        onCommand("m") {
            player.world.spawn(
                GroundItem(
                    item = 11849,
                    amount =1,
                    tile = Tile(2474,3418,2),
                    owner =player,
                ))
        }
        onCommand("m2") {
           player.moveTo(Tile(2474,3418,2))
        }
        onCommand("m3") {
            player.moveTo(Tile(2474,3418,0))
        }

    }
}
