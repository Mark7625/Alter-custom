package org.rsmod.content.bosses.vardorvis

import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.IdentityHashMap
import org.rsmod.api.player.hit.modifier.PlayerHitModifier
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.ui.ifCloseSub
import org.rsmod.api.player.ui.ifOpenFullOverlay
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

@Singleton
class VardorvisStrangle
@Inject
constructor(
    private val worldQueues: WorldQueueList,
    private val hitModifier: PlayerHitModifier,
) : PluginScript() {

    private val sessions = IdentityHashMap<Player, Int>()

    private val onResolve = IdentityHashMap<Player, () -> Unit>()

    private lateinit var events: EventBus

    override fun ScriptContext.startup() {
        events = eventBus

        for (n in 1..MAX_SPORES) {
            onIfOverlayButton("component.vardorvis_qte:qte_model_$n") { clearSpore(player, n) }
        }
    }

    fun beginStrangle(player: Player, spores: Int = DEFAULT_SPORES, resolved: () -> Unit = {}) {
        if (player in sessions) {
            player.mes("A Strangle QTE is already running.")
            return
        }
        sessions[player] = spores
        onResolve[player] = resolved

        player.abortRoute()
        player.clearInteraction()
        player.delay(ROOT_TICKS)

        player.mes(ENTANGLE_MESSAGE)
        for (n in 1..spores) VarPlayerIntMapSetter.set(player, qteVarbit(n), 1)
        player.spotanim(SPORE_SPAWN_SPOTANIM, slot = 2)

        player.ifOpenFullOverlay(QTE_INTERFACE, events)
        for (n in 1..spores) {
            player.ifSetEvents("component.vardorvis_qte:qte_model_$n", -1..-1, IfEvent.Op1)
        }
        player.runClientScript(qteSetupClientScript(), spores)

        worldQueues.add(WINDOW_TICKS) { finishStrangle(player) }
    }

    private fun clearSpore(player: Player, n: Int) {
        val spores = sessions[player] ?: return
        if (n > spores) return
        if (player.vars[qteVarbit(n)] == 0) return
        VarPlayerIntMapSetter.set(player, qteVarbit(n), 0)
        player.spotanim(SPORE_IDLE_SPOTANIM, slot = 2)

        if ((1..spores).all { player.vars[qteVarbit(it)] == 0 }) {
            finishStrangle(player)
        }
    }

    private fun finishStrangle(player: Player) {
        val spores = sessions.remove(player) ?: return
        onResolve.remove(player)?.invoke()
        player.delay = player.currentMapClock // lift the QTE movement/interaction root
        val survivors = (1..spores).count { player.vars[qteVarbit(it)] != 0 }
        for (n in 1..spores) VarPlayerIntMapSetter.set(player, qteVarbit(n), 0)

        player.spotanim(SPORE_DESPAWN_SPOTANIM, slot = 2)
        player.runClientScript(qteFadeClientScript())
        worldQueues.add(1) { player.ifCloseSub(QTE_INTERFACE, events) }

        if (survivors == 0) {
            player.mes(ESCAPE_MESSAGE)
        } else {
            player.mes(TIGHTEN_MESSAGE)
            val damage =
                (SPORE_MISS_BASE_DAMAGE + (survivors - 1) * SPORE_MISS_EXTRA_DAMAGE)
                    .coerceAtMost(SPORE_MISS_MAX_DAMAGE)
            player.queueHit(
                delay = 1,
                type = HitType.Typeless,
                damage = damage,
                modifier = hitModifier,
            )
        }
    }

    private fun qteVarbit(n: Int): String = "varbit.vardorvis_qte_$n"

    private fun qteSetupClientScript(): Int =
        "clientscript.[clientscript,vardorvis_qte_setup]".asRSCM(RSCMType.CLIENTSCRIPT)

    private fun qteFadeClientScript(): Int =
        "clientscript.[clientscript,script1823]".asRSCM(RSCMType.CLIENTSCRIPT)

    companion object {
        const val DEFAULT_SPORES = 4

        const val WINDOW_TICKS = 5

        const val RESOLVE_TICKS = 1

        const val TOTAL_TICKS = WINDOW_TICKS + RESOLVE_TICKS

        private const val ROOT_TICKS = TOTAL_TICKS

        private const val MAX_SPORES = 6
        private const val SPORE_MISS_BASE_DAMAGE = 25
        private const val SPORE_MISS_EXTRA_DAMAGE = 8
        private const val SPORE_MISS_MAX_DAMAGE = 40

        private const val QTE_INTERFACE = "interface.vardorvis_qte"

        private const val SPORE_SPAWN_SPOTANIM = "spotanim.vfx_player_vardorvis_spores_spawn"
        private const val SPORE_IDLE_SPOTANIM = "spotanim.vfx_player_vardorvis_spores_idle"
        private const val SPORE_DESPAWN_SPOTANIM = "spotanim.vfx_player_vardorvis_spores_despawn"

        private const val ENTANGLE_MESSAGE =
            "<col=ff3045>Vardorvis entangles you in some tendrils!</col>"
        private const val ESCAPE_MESSAGE =
            "<col=229628>You manage to escape from the tendrils.</col>"
        private const val TIGHTEN_MESSAGE =
            "<col=ff3045>The tendrils tighten around you, damaging you in the process!</col>"
    }
}
