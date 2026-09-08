package org.rsmod.content.skills.hunter

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.hunterLvl
import org.rsmod.api.repo.controller.ControllerRepository
import org.rsmod.api.script.onOpContentLoc1
import org.rsmod.api.script.onOpContentLoc2
import org.rsmod.api.script.onOpHeld1
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The box trap's player-facing ops. Every op routed here already exists on the cache type. The
 * sprung/failed states carry a real op2 of their own (`Reset`, out of scope - docs/hunter.md), and
 * [onOpContentLoc2] dispatches on group and slot, not label, so [investigate] guards on the armed
 * loc id to avoid answering a Reset click.
 */
class BoxTrapEvents
@Inject
constructor(private val traps: HunterTrap, private val conRepo: ControllerRepository) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onOpHeld1("obj.hunting_box_trap") { lay() }
        onOpContentLoc1("content.hunter_box_trap") { takeDown(it.loc) }
        onOpContentLoc2("content.hunter_box_trap") { investigate(it.loc) }
    }

    private fun ProtectedAccess.lay() {
        // The family gate; the per-creature gate in the tick only stops a catch, not the lay.
        if (player.hunterLvl < BOX_TRAP_LEVEL_REQ) {
            mes("You need a Hunter level of $BOX_TRAP_LEVEL_REQ to lay a box trap.")
            return
        }

        // Live also gates on Eagles' Peak, which this repo does not model; left unenforced
        // rather than fabricating a check (docs/hunter.md).

        with(traps) { layTrap(TrapFamily.BOX, player.coords) }
    }

    private suspend fun ProtectedAccess.takeDown(loc: BoundLocInfo) {
        arriveDelay()
        with(traps) { takeTrap(loc, TrapFamily.BOX) }
    }

    /** `Investigate` exists only on the armed state; other op2 clicks are `Reset` (see class doc). */
    private suspend fun ProtectedAccess.investigate(loc: BoundLocInfo) =
        investigateTrap(loc, noun = "box trap", armed = { it.id == SET_LOC }) {
            conRepo.findExact(it.coords, TRAP_CONTROLLER)
        }

    private companion object {
        private const val BOX_TRAP_LEVEL_REQ = 27
        private val SET_LOC =
            checkNotNull(HunterTrapStates.setLoc(TrapFamily.BOX)).asRSCM(RSCMType.LOC)
    }
}
