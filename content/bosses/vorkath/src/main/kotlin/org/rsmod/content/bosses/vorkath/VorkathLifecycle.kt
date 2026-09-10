package org.rsmod.content.bosses.vorkath

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.api.death.PlayerDeathContext
import org.rsmod.api.death.PlayerDeathDrops.Companion.DROP_DURATION_STANDARD
import org.rsmod.api.death.PlayerDeathDrops.Companion.standardKeepCount
import org.rsmod.api.death.PlayerDeathHandling
import org.rsmod.api.death.PlayerDeathHook
import org.rsmod.api.death.UntradeableHandling
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.instances.events.InstancePlayerLeaveEvent
import org.rsmod.api.instances.events.instanceEventId
import org.rsmod.api.player.hook.PlayerPostTickHook
import org.rsmod.api.player.output.mes
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

@Singleton
internal class VorkathAttackHook
@Inject
constructor(private val encounters: VorkathEncounterManager) : NpcAttackValidateHook {
    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult {
        if (!encounters.isEncounterNpc(npc)) return NpcAttackValidateResult.Pass
        if (!encounters.isOwnedBy(player, npc)) {
            return NpcAttackValidateResult.Deny(
                "That creature belongs to another adventurer's encounter."
            )
        }
        val denial = encounters.attackDenial(player, npc)
        return if (denial == null) NpcAttackValidateResult.BypassSingleWayPvnRestriction
        else NpcAttackValidateResult.Deny(denial)
    }
}

@Singleton
internal class VorkathPlayerDeathHook
@Inject
constructor(private val encounters: VorkathEncounterManager) : PlayerDeathHook {
    override fun handleDeath(context: PlayerDeathContext): PlayerDeathHandling? {
        if (!encounters.isActive(context.player)) return null
        return PlayerDeathHandling(
            keepCount = standardKeepCount(context.hasProtectItem),
            dropReceiver = context.player,
            dropDuration = DROP_DURATION_STANDARD,
            revealDelay = 0,
            supplyPile = false,
            untradeableHandling = UntradeableHandling.DROP,
        )
    }
}

@Singleton
internal class VorkathDeathCleanup
@Inject
constructor(private val encounters: VorkathEncounterManager) : PlayerDeathCleanupHook {
    override fun cleanup(player: Player) {
        encounters.abort(player, "player death", teleport = false)
    }
}

internal class VorkathLifecycle
@Inject
constructor(
    private val encounters: VorkathEncounterManager,
    private val storage: VorkathDeathStorage,
) : PluginScript(), PlayerPostTickHook {
    override fun ScriptContext.startup() {
        // Encounter visuals must be produced before zone/player update buffers are sent.
        onEvent<GameLifecycle.StartCycle> { encounters.tickAll() }
        onEvent<InstancePlayerLeaveEvent>(instanceEventId(VORKATH_INSTANCE_KEY)) {
            encounters.abort(player, "instance membership ended", teleport = false)
        }
        onPlayerLogout {
            encounters.abort(player, "logout or disconnect", teleport = false, logout = true)
        }
    }

    override fun onPostTick(player: Player) {
        if (storage.hasItems(player) && !player.attr.has(VORKATH_STORAGE_REMINDER_SHOWN)) {
            player.attr[VORKATH_STORAGE_REMINDER_SHOWN] = true
            player.mes(
                "Torfinn is holding ${storage.count(player)} item stacks from your Vorkath death."
            )
        }
    }
}
