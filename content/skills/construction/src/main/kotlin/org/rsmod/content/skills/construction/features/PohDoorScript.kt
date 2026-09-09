package org.rsmod.content.skills.construction.features

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.poh.PohManager
import org.rsmod.api.poh.PohStyleDoors
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Open/Close for the real house doors spawned on shared room passages outside building mode.
 *
 * The open variants keep their wall collision, so an opened leaf cannot stay on the doorway edge:
 * following Zenyte's `HouseDoor.java`, opening moves each leaf onto the tile across the wall and
 * turns it perpendicular (folded back against the doorway jamb), and closing moves it back. Both
 * leaves of the two-tile doorway swing from a single click. Door columns always sit on the middle
 * two positions (chunk-local 3/4) of a zone edge (chunk-local 0/7), which the swing geometry relies
 * on.
 */
class PohDoorScript
@Inject
constructor(private val locRepo: LocRepository, private val manager: PohManager) : PluginScript() {
    override fun ScriptContext.startup() {
        for (doors in PohStyleDoors.allDoorLocs()) {
            onOpLoc1(doors.closedLeft) { swing(it.loc, doors, open = true) }
            onOpLoc1(doors.closedRight) { swing(it.loc, doors, open = true) }
            onOpLoc1(doors.openLeft) { swing(it.loc, doors, open = false) }
            onOpLoc1(doors.openRight) { swing(it.loc, doors, open = false) }
        }
    }

    private suspend fun ProtectedAccess.swing(
        door: BoundLocInfo,
        doors: PohStyleDoors,
        open: Boolean,
    ) {
        arriveDelay()
        soundSynth(if (open) DOOR_OPEN_SYNTH else DOOR_CLOSE_SYNTH)
        val swaps = doors.stateSwaps(open)
        // Both leaves sit in a row along the wall run; visit the clicked tile and its neighbours.
        val (alongX, alongZ) = alongAxis(door.coords)
        val row =
            listOf(
                door.coords,
                door.coords.translate(alongX, alongZ),
                door.coords.translate(-alongX, -alongZ),
            )
        for (tile in row) {
            for (info in locRepo.findAll(tile).toList()) {
                val next = swaps[info.entity.id] ?: continue
                locRepo.del(info, Int.MAX_VALUE)
                // Despawning a spawned leaf resurrects the baked hotspot leaf underneath (the
                // registry drops the shadowing record); re-hide it before placing the new state.
                manager.hideDoorHotspot(player, tile)
                if (open) {
                    openLeaf(tile, info, next)
                } else {
                    closeLeaf(tile, next)
                }
            }
        }
    }

    /** Ids of the style's door locs in the current state, mapped to their target-state loc. */
    private fun PohStyleDoors.stateSwaps(open: Boolean): Map<Int, String> =
        if (open) {
            mapOf(
                closedLeft.asRSCM(RSCMType.LOC) to openLeft,
                closedRight.asRSCM(RSCMType.LOC) to openRight,
            )
        } else {
            mapOf(
                openLeft.asRSCM(RSCMType.LOC) to closedLeft,
                openRight.asRSCM(RSCMType.LOC) to closedRight,
            )
        }

    /** The axis the door row runs along: doorways sit on chunk-local 0/7 edges. */
    private fun alongAxis(coords: CoordGrid): Pair<Int, Int> {
        val cx = coords.x and 0x7
        return if (cx == 0 || cx == 7) 0 to 1 else 1 to 0
    }

    /** Zenyte `openDoor`: move the leaf across the wall, turned against the doorway jamb. */
    private fun ProtectedAccess.openLeaf(tile: CoordGrid, closed: LocInfo, openLoc: String) {
        val cx = tile.x and 0x7
        val cz = tile.z and 0x7
        val dest: CoordGrid
        val angle: LocAngle
        if (cz == 0 || cz == 7) {
            dest = tile.translateZ(if (cz == 7) 1 else -1)
            angle = if (cx == 3) LocAngle.West else LocAngle.East
        } else {
            dest = tile.translateX(if (cx == 7) 1 else -1)
            angle = if (cz == 3) LocAngle.South else LocAngle.North
        }
        locRepo.add(dest, openLoc, Int.MAX_VALUE, angle, closed.shape)
    }

    /** Zenyte `closeDoor`: move the leaf back onto the doorway edge, facing the wall run. */
    private fun ProtectedAccess.closeLeaf(tile: CoordGrid, closedLoc: String) {
        val cx = tile.x and 0x7
        val cz = tile.z and 0x7
        val dest: CoordGrid
        val angle: LocAngle
        if (cz == 0 || cz == 7) {
            dest = tile.translateZ(if (cz == 7) 1 else -1)
            angle = if (cz == 7) LocAngle.South else LocAngle.North
        } else {
            dest = tile.translateX(if (cx == 7) 1 else -1)
            angle = if (cx == 7) LocAngle.West else LocAngle.East
        }
        locRepo.add(dest, closedLoc, Int.MAX_VALUE, angle, LocShape.WallStraight)
    }

    private companion object {
        const val DOOR_OPEN_SYNTH = "synth.door_open"
        const val DOOR_CLOSE_SYNTH = "synth.door_close"
    }
}
