package org.rsmod.content.bosses.spindel

import jakarta.inject.Inject
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.game.entity.PlayerList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class VenenatisLair
@Inject
constructor(private val playerList: PlayerList) : PluginScript() {

    private data class Entry(
        val enterLoc: String,
        val enter: CoordGrid,
        val exit: CoordGrid,
        val cfg: LairConfig,
    )

    private val entries =
        listOf(
            Entry(
                "loc.wild_venenatis_singles_entrance01",
                CoordGrid(1632, 11555, 2),
                CoordGrid(3182, 3745, 0),
                SPINDEL_LAIR,
            ),
            Entry(
                "loc.wild_venanatis_entrance01",
                CoordGrid(3422, 10213, 2),
                CoordGrid(3320, 3796, 0),
                VENENATIS_LAIR,
            ),
        )

    override fun ScriptContext.startup() {
        for (entry in entries) {
            onOpLoc1(entry.enterLoc) { enterLair(entry) }
            onOpLoc2(entry.enterLoc) { peekLair(entry) }
        }
        onOpLoc1(EXIT_LOC) { leaveLair() }
    }

    private suspend fun ProtectedAccess.enterLair(entry: Entry) {
        arriveDelay()
        telejump(entry.enter, TeleportType.Exempt)
    }

    private suspend fun ProtectedAccess.leaveLair() {
        arriveDelay()
        val dest = entries.firstOrNull { it.cfg.contains(player.coords, PEEK_MARGIN) }?.exit ?: return
        telejump(dest, TeleportType.Exempt)
    }

    private fun ProtectedAccess.peekLair(entry: Entry) {
        val count = playerList.count { entry.cfg.contains(it.coords, PEEK_MARGIN) }
        if (count == 0) {
            player.mes("The lair is currently empty.")
        } else {
            val subject = if (count == 1) "is 1 player" else "are $count players"
            player.mes("There $subject currently in the lair.")
        }
    }

    private companion object {
        private const val EXIT_LOC = "loc.wild_venanatis_exit"
        private const val PEEK_MARGIN = 12
    }
}
