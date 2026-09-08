package org.rsmod.content.skills.hunter

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.controller.ControllerRepository
import org.rsmod.api.script.onAiConTimer
import org.rsmod.api.script.onOpContentLoc1
import org.rsmod.api.script.onOpContentLoc2
import org.rsmod.api.script.onOpHeld1
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The bird snare's player-facing ops, and the trap tick every family shares. Every op routed here
 * already exists on the cache type; the `Dismantle`/`Check` ops are all op1, so one registration
 * catches them and [HunterTrap.takeTrap] decides what the tile owes the player.
 */
class BirdSnareEvents
@Inject
constructor(private val traps: HunterTrap, private val conRepo: ControllerRepository) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onOpHeld1("obj.hunting_ojibway_bird_snare") { lay() }
        onOpContentLoc1("content.hunter_bird_snare") { takeDown(it.loc) }
        onOpContentLoc2("content.hunter_bird_snare") { investigate(it.loc) }

        // Registered exactly once in the codebase: the controller type is shared, and a second
        // registration would run every laid trap's tick twice per cycle.
        onAiConTimer(TRAP_CONTROLLER) { with(traps) { controller.hunterTrapTick() } }
    }

    private fun ProtectedAccess.lay() {
        with(traps) { layTrap(TrapFamily.SNARE, player.coords) }
    }

    private suspend fun ProtectedAccess.takeDown(loc: BoundLocInfo) {
        arriveDelay()
        with(traps) { takeTrap(loc, TrapFamily.SNARE) }
    }

    /** Live's server-sent `Investigate` wording is not recoverable offline; the strings are ours. */
    private suspend fun ProtectedAccess.investigate(loc: BoundLocInfo) =
        investigateTrap(loc, noun = "snare") {
            conRepo.findExact(it.coords, TRAP_CONTROLLER)
        }
}
