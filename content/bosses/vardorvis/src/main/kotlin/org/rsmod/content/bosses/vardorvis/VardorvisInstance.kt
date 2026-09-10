package org.rsmod.content.bosses.vardorvis

import jakarta.inject.Inject
import org.rsmod.api.instances.BossInstanceRegistry
import org.rsmod.api.instances.InstanceAccess
import org.rsmod.api.instances.InstanceArea
import org.rsmod.api.instances.InstanceNpc
import org.rsmod.api.instances.InstanceScript
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.script.onPlayerInit
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

class VardorvisInstance @Inject constructor(registry: BossInstanceRegistry) :
    InstanceScript(registry) {

    private var Player.stranglewoodProgress by intVarBit(STRANGLEWOOD_VARBIT)

    override fun settingsRow(): String = "dbrow.instance_vardorvis"

    override fun area(): InstanceArea = INSTANCE

    override fun ScriptContext.configure() {
        onPlayerInit {
            if (player.stranglewoodProgress < STRANGLEWOOD_UNLOCKED) {
                player.stranglewoodProgress = STRANGLEWOOD_UNLOCKED
            }
        }

        onEnterObject {
            if (manager.sessionForPlayer(player) != null) {
                defaultLeaveFlow()
            } else {
                privateInstanceEntry()
            }
        }
        onExitObject { defaultLeaveFlow() }
    }

    private suspend fun ProtectedAccess.privateInstanceEntry() {
        if (manager.sessionForPlayer(player) != null) {
            mes("You are already inside an instance.")
            return
        }
        val owned = player.uuid?.let { manager.sessionOwnedBy(key, it) }
        val result =
            if (owned != null) {
                manager.join(player, owned, worldClock.cycle, code = null, forceAccess = true)
            } else {
                manager.create(player, key, buildSpec(), InstanceAccess.Private, worldClock.cycle)
            }
        completeInstanceEntry(result)
    }

    private companion object {
        private const val STRANGLEWOOD_VARBIT = "varbit.dt2_stranglewood"

        private const val STRANGLEWOOD_UNLOCKED = 37

        private const val ARENA_REGION = 4405

        private val INSTANCE =
            InstanceArea.copyRegions(
                regionIds = listOf(ARENA_REGION),
                npcSpawns = listOf(InstanceNpc("npc.vardorvis", CoordGrid(1128, 3417, 0))),
            )
    }
}
