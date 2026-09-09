package org.rsmod.content.generic.locs.passages

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The ditch along the southern edge of the Wilderness. `Cross` jumps the player from whichever
 * side they stand on to the tile just past the far edge.
 */
class WildernessDitchScript : PluginScript() {
    override fun ScriptContext.startup() {
        for (ditch in DITCHES) {
            onOpLoc1(ditch) { cross(it.loc) }
        }
    }

    private suspend fun ProtectedAccess.cross(loc: BoundLocInfo) {
        arriveDelay()
        val dest = Passages.farSide(loc, coords)
        if (dest == null) {
            mes("You cannot jump the ditch from here.")
            return
        }
        hopTo(dest, JUMP_ANIM, ticks = seqTicks(JUMP_ANIM, fallback = 2))
    }

    private companion object {
        private val DITCHES =
            listOf("loc.ditch_wilderness_cover", "loc.ditch_wilderness_cover_members")
        private const val JUMP_ANIM = "seq.wild_ditch_jump"
    }
}
