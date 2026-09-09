package org.rsmod.content.skills.construction.scripts

import jakarta.inject.Inject
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.poh.PohManager
import org.rsmod.api.poh.pohBuildingMode
import org.rsmod.api.script.onDropTrigger
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onOpLoc4
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * World house portals (`Enter` / `Home` / `Build mode` / `Friend's house`), the in-house exit
 * portal, and the building-mode drop guardrail.
 *
 * Guests are out of scope: `Friend's house` always answers that the player is not at home.
 */
class HousePortalScript @Inject constructor(private val manager: PohManager) : PluginScript() {
    override fun ScriptContext.startup() {
        for (portal in WORLD_PORTALS) {
            onOpLoc1(portal) { enterHouse(buildMode = false) }
            onOpLoc2(portal) { enterHouse(buildMode = false) }
            onOpLoc3(portal) { enterHouse(buildMode = true) }
            onOpLoc4(portal) { mesbox("That player is not at home.") }
        }

        onOpLoc1("loc.poh_exit_portal") { exitHouse(manager) }

        onDropTrigger(BUILDING_MODE_DROP_TRIGGER) {
            player.dropTrigger(BUILDING_MODE_DROP_TRIGGER)
            player.mes("You cannot drop items while in building mode.")
        }
    }

    private suspend fun ProtectedAccess.enterHouse(buildMode: Boolean) {
        if (!manager.hasHouse(player)) {
            mesbox("You don't own a house.")
            return
        }
        when (manager.enter(player, buildMode)) {
            is PohManager.EnterResult.Success -> {
                if (buildMode) {
                    player.armBuildingModeDropTrigger()
                    mes("Building mode is now on.")
                } else {
                    player.disarmBuildingModeDropTrigger()
                }
            }
            PohManager.EnterResult.NoHouse -> mesbox("You don't own a house.")
            PohManager.EnterResult.NoSpace ->
                mes("Your house is currently unavailable. Please try again shortly.")
        }
    }

    companion object {
        const val BUILDING_MODE_DROP_TRIGGER = "droptrigger.poh_building_mode"

        /** The eight verified world portal locs. Yanille's portal loc is unresolved in rev 240. */
        val WORLD_PORTALS =
            listOf(
                "loc.poh_taverly_portal",
                "loc.poh_rimmington_portal",
                "loc.poh_pollnivneach_portal",
                "loc.poh_rellekka_portal",
                "loc.poh_brimhaven_portal",
                "loc.poh_kourend_portal",
                "loc.poh_prifddinas_portal",
                "loc.poh_aldarin_portal",
            )

        fun Player.armBuildingModeDropTrigger() {
            if (dropTrigger == null) {
                dropTrigger(BUILDING_MODE_DROP_TRIGGER)
            }
        }

        fun Player.disarmBuildingModeDropTrigger() {
            if (dropTrigger == BUILDING_MODE_DROP_TRIGGER) {
                clearDropTrigger(BUILDING_MODE_DROP_TRIGGER)
            }
        }

        /** Leaves the house through the exit portal, arriving at the world portal outside. */
        suspend fun ProtectedAccess.exitHouse(manager: PohManager) {
            if (!manager.isInOwnHouse(player)) {
                return
            }
            player.disarmBuildingModeDropTrigger()
            player.pohBuildingMode = 0
            val coords = manager.leave(player)
            telejump(coords)
        }
    }
}
