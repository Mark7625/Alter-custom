package org.rsmod.content.skills.construction.features

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.poh.PohManager
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Built portal nexus (`loc.poh_nexus_portal_1..3` - marble/gilded/crystalline): op1 `Teleport` and
 * op2 `Teleport Menu` both open a destination picker; the chosen destination is telejumped to with
 * the same coordinate table the portal chamber uses.
 *
 * Simplification for this pass: on OSRS a nexus only offers destinations the owner has attuned to
 * it (paying the rune cost once per destination, stored in `varbit.poh_nexus_tele_1..35`).
 * Attunement is out of scope here, so the eight standard city destinations are offered directly and
 * the attunement varbits are left untouched. When attunement lands, this menu should filter on
 * those varbits instead, and op3 `Configuration` (currently unbound) should manage them.
 */
class PortalNexusScript @Inject constructor(private val manager: PohManager) : PluginScript() {
    override fun ScriptContext.startup() {
        for (nexus in NEXUS_PORTALS) {
            onOpLoc1(nexus) { openTeleportMenu() }
            onOpLoc2(nexus) { openTeleportMenu() }
        }
    }

    private suspend fun ProtectedAccess.openTeleportMenu() {
        if (!manager.isInOwnHouse(player)) {
            mes("You can only use the portal nexus in your own house.")
            return
        }
        val index = menu(MENU_TITLE, hotkeys = true, choices = DESTINATIONS.map { it.first })
        val destination = DESTINATIONS.getOrNull(index)?.second ?: return
        arriveDelay()
        telejump(destination)
        mes("You step through the portal.")
    }

    private companion object {
        const val MENU_TITLE = "Portal Nexus"

        val NEXUS_PORTALS =
            listOf("loc.poh_nexus_portal_1", "loc.poh_nexus_portal_2", "loc.poh_nexus_portal_3")

        /** The eight standard city destinations, sharing the portal chamber's coordinates. */
        val DESTINATIONS: List<Pair<String, CoordGrid>> =
            listOf(
                "Varrock" to PohTeleportDestinations.VARROCK,
                "Lumbridge" to PohTeleportDestinations.LUMBRIDGE,
                "Falador" to PohTeleportDestinations.FALADOR,
                "Camelot" to PohTeleportDestinations.CAMELOT,
                "Ardougne" to PohTeleportDestinations.ARDOUGNE,
                "Yanille" to PohTeleportDestinations.YANILLE,
                "Kharyrll" to PohTeleportDestinations.KHARYRLL,
                "Kourend Castle" to PohTeleportDestinations.KOUREND,
            )
    }
}
