package org.rsmod.content.skills.construction.features

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.poh.PohManager
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Destination coordinates shared by the portal chamber, portal nexus and future teleport rooms.
 *
 * The classic six city destinations reuse the exact coordinates silo's standard spellbook teleports
 * resolve from `params.spell_telecoord` (plus their alternates), so a house portal and the
 * equivalent spell always land on the same tile. Destinations without a silo spell (Kharyrll and
 * the extended portal set) use the OSRS wiki teleport landing tiles.
 */
internal object PohTeleportDestinations {
    val VARROCK = CoordGrid(3213, 3424, 0)
    val GRAND_EXCHANGE = CoordGrid(3164, 3487, 0)
    val LUMBRIDGE = CoordGrid(3221, 3218, 0)
    val FALADOR = CoordGrid(2965, 3378, 0)
    val CAMELOT = CoordGrid(2757, 3478, 0)
    val SEERS_VILLAGE = CoordGrid(2725, 3485, 0)
    val ARDOUGNE = CoordGrid(2661, 3302, 0)
    val YANILLE = CoordGrid(2544, 3095, 0)
    val WATCHTOWER = CoordGrid(2933, 4712, 0)
    val KHARYRLL = CoordGrid(3492, 3471, 0)
    val LUNAR_ISLE = CoordGrid(2113, 3915, 0)
    val SENNTISTEN = CoordGrid(3322, 3336, 0)
    val ANNAKARL = CoordGrid(3288, 3886, 0)
    val WATERBIRTH = CoordGrid(2546, 3757, 0)
    val FISHING_GUILD = CoordGrid(2611, 3391, 0)
    val MARIM = CoordGrid(2797, 2798, 1)
    val KOUREND = CoordGrid(1641, 3673, 0)
}

private var Player.varrockPortalGeFirst by intVarBit("varbit.varrock_ge_teleport")
private var Player.camelotPortalSeersFirst by intVarBit("varbit.seers_camelot_teleport")
private var Player.yanillePortalYanilleFirst by intVarBit("varbit.yanille_teleport_location")

/**
 * Built portal-chamber portals: op1 `Enter` teleports to the destination encoded in the loc name.
 *
 * Varrock, Camelot and Yanille portals are multiloc parents whose varbit-selected children carry a
 * two-destination op pair plus a `Toggle` op that swaps which destination is offered first
 * (mirroring the spellbook's alternate teleports); the engine resolves op triggers against the
 * child loc, so the bindings live on the `_<dest>1st` children. All other destinations are plain
 * single-op portals.
 *
 * Not wired yet (no verified standard-teleport coordinate; the locs exist in the cache): the newer
 * expansion portals - ape_atoll, arceuus_library, barrows, battlefront, carrallangar, catherby,
 * cemetery, dareeyak, draynor_manor, fenkenstrain, fortis, ghorrock, harmony_island, iceplateau,
 * khazard, lassar, mind_altar, ourania, paddewwa, respawn, salve_graveyard, stronghold, trollheim,
 * weiss and west_ardougne.
 */
class PortalChamberScript @Inject constructor(private val manager: PohManager) : PluginScript() {
    override fun ScriptContext.startup() {
        for (material in MATERIALS) {
            for ((suffix, destination) in SIMPLE_DESTINATIONS) {
                onOpLoc1("loc.poh_portal_${material}_$suffix") { enterPortal(destination) }
            }
        }
        for (material in CHILD_MATERIALS) {
            registerTogglePortal(
                material = material,
                city = "varrock",
                firstSuffix = "varrock1st",
                secondSuffix = "ge1st",
                firstDestination = PohTeleportDestinations.VARROCK,
                secondDestination = PohTeleportDestinations.GRAND_EXCHANGE,
                toggle = { varrockPortalGeFirst = 1 - varrockPortalGeFirst },
            )
            registerTogglePortal(
                material = material,
                city = "camelot",
                firstSuffix = "camelot1st",
                secondSuffix = "seers1st",
                firstDestination = PohTeleportDestinations.CAMELOT,
                secondDestination = PohTeleportDestinations.SEERS_VILLAGE,
                toggle = { camelotPortalSeersFirst = 1 - camelotPortalSeersFirst },
            )
            registerTogglePortal(
                material = material,
                city = "yanille",
                firstSuffix = "watchtower1st",
                secondSuffix = "yanille1st",
                firstDestination = PohTeleportDestinations.WATCHTOWER,
                secondDestination = PohTeleportDestinations.YANILLE,
                toggle = { yanillePortalYanilleFirst = 1 - yanillePortalYanilleFirst },
            )
        }
    }

    /**
     * Registers both varbit children of a two-destination portal. Each child's op1 targets its own
     * primary destination, op2 the alternate, and op3 flips the varbit that decides which child the
     * multiloc parent resolves to.
     */
    private fun ScriptContext.registerTogglePortal(
        material: String,
        city: String,
        firstSuffix: String,
        secondSuffix: String,
        firstDestination: CoordGrid,
        secondDestination: CoordGrid,
        toggle: Player.() -> Unit,
    ) {
        val first = "loc.poh_portal_${material}_${city}_$firstSuffix"
        val second = "loc.poh_portal_${material}_${city}_$secondSuffix"

        onOpLoc1(first) { enterPortal(firstDestination) }
        onOpLoc2(first) { enterPortal(secondDestination) }
        onOpLoc3(first) { togglePortal(toggle) }

        onOpLoc1(second) { enterPortal(secondDestination) }
        onOpLoc2(second) { enterPortal(firstDestination) }
        onOpLoc3(second) { togglePortal(toggle) }
    }

    private suspend fun ProtectedAccess.enterPortal(destination: CoordGrid) {
        if (!manager.isInOwnHouse(player)) {
            mes("You can only use house portals in your own house.")
            return
        }
        arriveDelay()
        telejump(destination)
        mes("You step through the portal.")
    }

    private suspend fun ProtectedAccess.togglePortal(toggle: Player.() -> Unit) {
        if (!manager.isInOwnHouse(player)) {
            mes("You can only do that in your own house.")
            return
        }
        arriveDelay()
        player.toggle()
        mes("The portal's destination order has been switched.")
    }

    private companion object {
        /** Loc-name material prefixes of the single-destination portals. */
        val MATERIALS = listOf("teak", "mag", "marble")

        /** The varbit-child locs spell mahogany out in full, unlike their `mag` parents. */
        val CHILD_MATERIALS = listOf("teak", "mahogany", "marble")

        /** Loc-name suffix to destination for every single-op portal. */
        val SIMPLE_DESTINATIONS =
            listOf(
                "lumbridge" to PohTeleportDestinations.LUMBRIDGE,
                "falador" to PohTeleportDestinations.FALADOR,
                "ardougne" to PohTeleportDestinations.ARDOUGNE,
                "kharyrll" to PohTeleportDestinations.KHARYRLL,
                "lunarisle" to PohTeleportDestinations.LUNAR_ISLE,
                "senntisten" to PohTeleportDestinations.SENNTISTEN,
                "annakarl" to PohTeleportDestinations.ANNAKARL,
                "waterbirth" to PohTeleportDestinations.WATERBIRTH,
                "fishingguild" to PohTeleportDestinations.FISHING_GUILD,
                "marim" to PohTeleportDestinations.MARIM,
                "kourend" to PohTeleportDestinations.KOUREND,
            )
    }
}
