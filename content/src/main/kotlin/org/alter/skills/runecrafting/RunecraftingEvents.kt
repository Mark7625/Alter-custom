package org.alter.skills.runecrafting

import dev.openrune.ServerCacheManager.getItem
import org.alter.api.ext.message
import org.alter.api.ext.setVarbit
import org.alter.game.model.Tile
import org.alter.game.model.entity.Player
import org.alter.game.model.move.moveTo
import org.alter.game.pluginnew.PluginEvent
import org.alter.game.pluginnew.event.impl.ItemOnObject
import org.alter.game.pluginnew.event.impl.ObjectClickEvent
import org.alter.game.pluginnew.event.impl.onItemEquip
import org.alter.game.pluginnew.event.impl.onItemOption
import org.alter.game.pluginnew.event.impl.onItemUnequip
import org.alter.game.pluginnew.event.impl.onLogin
import org.alter.game.pluginnew.event.impl.onObjectOption
import org.generated.tables.runecrafting.RunecraftingAltarsRow
import org.generated.tables.runecrafting.RunecraftingRunesRow
import org.generated.tables.runecrafting.RunecraftingTiaraRow

inline fun <A, B> bothNotNull(a: A?, b: B?, block: (A, B) -> Unit) {
    if (a != null && b != null) block(a, b)
}

class RunecraftingEvents : PluginEvent() {

    override fun init() {
        RunecraftingAltarsRow.all().forEach { altar ->

            if (altar.entrance != null) {
                altar.ruins.forEach { ruin ->
                    onObjectOption(ruin!!,"enter") {
                        player.moveTo(altar.entrance)
                    }
                    on<ItemOnObject> {
                        where { id == ruin && item.id == altar.talisman }
                        then {  player.moveTo(altar.entrance) }
                    }
                }
            }

            on<ObjectClickEvent> {
                where { gameObject.internalID == altar.altarObject }
                then {
                    val rune = RunecraftingRunesRow.getRow(altar.rune)
                    RunecraftAction.craftRune(player,rune)
                }
            }

            // Exit portal handling
            if (altar.exitPortal != null && altar.exit != null) {
                onObjectOption(altar.exitPortal, "use") {
                    player.moveTo(altar.exit)
                }
            }

            // Tiara varbit handling
            if (altar.varbit != null) {
                altar.tiara?.let { tiaraId ->
                    val tiaraDef = RunecraftingTiaraRow.getRow(tiaraId)

                    onItemEquip(tiaraDef.item) {
                        player.setVarbit(altar.varbit, 1)
                    }

                    onItemUnequip(tiaraDef.item) {
                        player.setVarbit(altar.varbit, 0)
                    }

                    onLogin {
                        val equipped = player.equipment.contains(tiaraDef.item)
                        player.setVarbit(altar.varbit, if (equipped) 1 else 0)
                    }
                }
            }

            // Talisman locate option
            bothNotNull(altar.entrance, altar.talisman) { entrance, talisman ->
                onItemOption(talisman, "locate") {
                    locateAltar(player, entrance)
                }
            }
        }
    }

    private fun locateAltar(player: Player, altarTile: Tile) {
        val dx = player.tile.x - altarTile.x
        val dz = player.tile.z - altarTile.z

        val direction = when {
            dx > 0 && dz > 0 -> "north-east"
            dx < 0 && dz > 0 -> "north-west"
            dx > 0 && dz < 0 -> "south-east"
            dx < 0 && dz < 0 -> "south-west"
            dx == 0 && dz > 0 -> "north"
            dx == 0 && dz < 0 -> "south"
            dz == 0 && dx > 0 -> "east"
            dz == 0 && dx < 0 -> "west"
            else -> "unknown"
        }

        player.message("The talisman pulls towards the $direction.")
    }
}
