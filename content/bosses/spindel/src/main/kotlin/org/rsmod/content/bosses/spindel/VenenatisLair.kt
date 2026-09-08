package org.rsmod.content.bosses.spindel

import jakarta.inject.Inject
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class VenenatisLair
@Inject
constructor(private val playerList: PlayerList) : PluginScript() {

    private data class Entry(
        val enterLoc: String,
        val enter: CoordGrid,
        val exitLoc: CoordGrid,
        val exit: CoordGrid,
        val cfg: LairConfig,
    )

    private val entries =
        listOf(
            Entry(
                "loc.wild_venenatis_singles_entrance01",
                CoordGrid(1632, 11555, 2),
                CoordGrid(1630, 11527, 2),
                CoordGrid(3182, 3745, 0),
                SPINDEL_LAIR,
            ),
            Entry(
                "loc.wild_venanatis_entrance01",
                CoordGrid(3422, 10213, 2),
                CoordGrid(3422, 10183, 2),
                CoordGrid(3320, 3796, 0),
                VENENATIS_LAIR,
            ),
        )

    override fun ScriptContext.startup() {
        for (entry in entries) {
            onOpLoc1(entry.enterLoc) { enterLair(entry) }
            onOpLoc2(entry.enterLoc) { peekLair(entry) }
        }
        onOpLoc1(EXIT_LOC) { leaveLair(it.loc) }
    }

    private suspend fun ProtectedAccess.enterLair(entry: Entry) {
        arriveDelay()
        telejump(entry.enter, TeleportType.Exempt)
    }

    private suspend fun ProtectedAccess.leaveLair(exit: BoundLocInfo) {
        arriveDelay()
        val entry = entries.firstOrNull { it.exitLoc == exit.coords } ?: return
        telejump(entry.exit, TeleportType.Exempt)
    }

    private fun ProtectedAccess.peekLair(entry: Entry) {
        val count = playerList.count { entry.cfg.contains(it.coords) }
        if (count == 0) {
            player.mes("The lair is currently empty.")
        } else {
            val subject = if (count == 1) "is 1 player" else "are $count players"
            player.mes("There $subject currently in the lair.")
        }
    }

    private companion object {
        private const val EXIT_LOC = "loc.wild_venanatis_exit"
    }
}
