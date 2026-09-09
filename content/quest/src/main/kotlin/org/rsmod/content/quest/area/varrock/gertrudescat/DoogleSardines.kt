package org.rsmod.content.quest.area.varrock.gertrudescat

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.quest.area.varrock.gertrudescat.GertrudesCatQuest.Companion.DOOGLE_LEAVES
import org.rsmod.content.quest.area.varrock.gertrudescat.GertrudesCatQuest.Companion.RAW_SARDINE
import org.rsmod.content.quest.area.varrock.gertrudescat.GertrudesCatQuest.Companion.SEASONED_SARDINE
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Seasoning a raw sardine with doogle leaves, and the empty crates and barrels scattered
 * around the lumber yard. The kitten itself hides in the mewing crates handled by
 * [LumberYardCrates].
 */
class DoogleSardines : PluginScript() {
    override fun ScriptContext.startup() {
        onOpHeldU(RAW_SARDINE, DOOGLE_LEAVES) { seasonSardine() }
        onOpLoc1(EMPTY_CRATE) { searchEmpty("crate") }
        onOpLoc1(EMPTY_BARREL) { searchEmpty("barrel") }
    }

    private suspend fun ProtectedAccess.seasonSardine() {
        val removed = invDel(inv, RAW_SARDINE, 1, DOOGLE_LEAVES, 1)
        if (removed.failure) {
            return
        }
        invAdd(inv, SEASONED_SARDINE)
        anim("seq.human_pickuptable")
        mes("You rub the doogle leaves all over the sardine.")
    }

    private suspend fun ProtectedAccess.searchEmpty(what: String) {
        arriveDelay()
        anim("seq.human_pickuptable")
        mes("You search the $what but find nothing.")
    }

    private companion object {
        const val EMPTY_CRATE = "loc.gertrudeempty_crate"
        const val EMPTY_BARREL = "loc.gertrudeempty_barrel"
    }
}
