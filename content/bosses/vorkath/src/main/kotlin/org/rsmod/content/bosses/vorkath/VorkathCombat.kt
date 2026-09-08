package org.rsmod.content.bosses.vorkath

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.script.onAiApPlayer2
import org.rsmod.api.script.onModifyNpcHit
import org.rsmod.api.script.onNpcHit
import org.rsmod.api.script.onNpcQueue
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class VorkathCombat
@Inject
constructor(
    private val encounters: VorkathEncounterManager,
    private val death: NpcDeath,
    private val players: PlayerList,
) : PluginScript() {
    override fun ScriptContext.startup() {
        val active = requireNpc(VorkathAssets.ACTIVE)
        val sleeping = requireNpc(VorkathAssets.SLEEPING)
        val spawn = requireNpc(VorkathAssets.SPAWN)

        onAiApPlayer2(active) { encounters.attack(this, it.target) }
        onModifyNpcHit(active) {
            val player =
                if (hit.isFromPlayer) hit.sourceUid?.let(::PlayerUid)?.resolve(players) else null
            if (player == null) hit.damage = 0 else encounters.modifyNpcHit(player, npc, hit)
        }
        onModifyNpcHit(sleeping) { hit.damage = 0 }
        onModifyNpcHit(spawn) {
            val player =
                if (hit.isFromPlayer) hit.sourceUid?.let(::PlayerUid)?.resolve(players) else null
            if (player == null) hit.damage = 0 else encounters.modifyNpcHit(player, npc, hit)
        }
        onNpcHit(spawn) {
            if (npc.hitpoints == 0) encounters.beginSpawnDeath(npc)
        }
        onNpcQueue(active, "queue.death") {
            val run = encounters.beginBossDeath(npc) ?: return@onNpcQueue
            noneMode()
            hideAllOps()
            npc.anim(VorkathAssets.DEATH_ANIM)
            // Captures remove the corpse and produce loot at animation +6. The native
            // sequence's rounded seven-tick duration is not the encounter's death deadline.
            delay(VORKATH_DEATH_TICKS)
            if (encounters.canFinishDeath(run, npc)) {
                if (run.rewardEligible) death.spawnDrops(this, npc.coords)
                encounters.finishBossDeath(npc)
            }
        }
        onNpcQueue(spawn, "queue.death") {
            encounters.beginSpawnDeath(npc)
        }
    }

    private fun requireNpc(internal: String) =
        requireNotNull(ServerCacheManager.getNpc(internal.asRSCM(RSCMType.NPC))) {
            "Missing Vorkath npc definition: $internal"
        }
}
