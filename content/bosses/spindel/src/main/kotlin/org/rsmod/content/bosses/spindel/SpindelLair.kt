package org.rsmod.content.bosses.spindel

import jakarta.inject.Inject
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.game.entity.PlayerList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class SpindelLair
@Inject
constructor(
    private val playerList: PlayerList,
    private val areaChecker: AreaChecker,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(ENTRANCE) { enterLair() }
        onOpLoc2(ENTRANCE) { peekLair() }
        onOpLoc1(EXIT) { leaveLair() }
    }

    private suspend fun ProtectedAccess.enterLair() {
        arriveDelay()
        telejump(LAIR_ENTER, TeleportType.Exempt)
    }

    private suspend fun ProtectedAccess.leaveLair() {
        arriveDelay()
        telejump(LAIR_EXIT, TeleportType.Exempt)
    }

    private fun ProtectedAccess.peekLair() {
        val count = playerList.count { areaChecker.inArea(LAIR_AREA, it.coords) }
        if (count == 0) {
            player.mes("The lair is currently empty.")
        } else {
            val subject = if (count == 1) "is 1 player" else "are $count players"
            player.mes("There $subject currently in the lair.")
        }
    }

    private companion object {
        private const val ENTRANCE = "loc.wild_venenatis_singles_entrance01"
        private const val EXIT = "loc.wild_venanatis_exit"
        private const val LAIR_AREA = "area.spindel_lair"
        private val LAIR_ENTER = CoordGrid(1632, 11555, 2)
        private val LAIR_EXIT = CoordGrid(3182, 3745, 0)
    }
}
