package org.rsmod.api.game.process.npc.hunt

import dev.openrune.ServerCacheManager
import dev.openrune.types.HuntModeType
import dev.openrune.types.hunt.HuntCheckNotTooStrong
import dev.openrune.types.hunt.HuntType
import jakarta.inject.Inject
import org.rsmod.api.config.constants
import org.rsmod.api.hunt.Hunt
import org.rsmod.api.npc.isValidTarget
import org.rsmod.api.random.CoreRandom
import org.rsmod.api.random.GameRandom
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.type.getOrNull

public class NpcPlayerHuntProcessor
@Inject
constructor(
    @CoreRandom private val random: GameRandom,
    private val mapClock: MapClock,
    private val hunt: Hunt,
    private val tolerance: AggressionTolerance,
) {
    public fun process(npc: Npc) {
        if (!npc.isValidTarget() || npc.isDelayed) {
            return
        }

        val huntMode = npc.huntMode
        val huntDisabled = npc.huntRange == 0 || huntMode == null
        if (huntDisabled) {
            return
        }

        val skipHunt = !npc.isAnyoneNear()
        if (skipHunt) {
            return
        }

        val huntType = ServerCacheManager.getHunt(huntMode) ?: return
        val huntDelayed = npc.huntClock < huntType.rate - 1
        if (huntDelayed) {
            return
        }

        if (huntType.type == HuntType.Player && !npc.hasInteraction()) {
            npc.huntPlayer(huntType)
        }
    }

    private fun Npc.huntPlayer(mode: HuntModeType) {
        var target = PlayerUid.NULL
        var count = 0

        val players = hunt.findPlayers(coords, huntRange, mode.checkVis)
        for (player in players) {
            if (player.isInvisible) {
                continue
            }

            if (mode.checkNotBusy && player.isBusy) {
                continue
            }

            if (mode.checkAfk && player.isAfk()) {
                continue
            }

            if (mode.checkNotTooStrong == HuntCheckNotTooStrong.OutsideWilderness) {
                if (player.combatLevel > type.combatLevel * 2 && !player.isInWilderness()) {
                    continue
                }
                // Monsters that respect the level rule also tire of a player who has stayed in
                // their area for ten minutes; the always-aggressive modes never do.
                if (tolerance.isTolerant(player, mapClock.cycle)) {
                    continue
                }
            }

            if (!player.isInMulti()) {
                // Vars are stored by id, and the server-only combat varp the hunt modes point
                // at has no entry in the cache varp table, so read both by id.
                if (mode.checkNotCombat != -1) {
                    val lastCombat = player.vars.backing.getOrDefault(mode.checkNotCombat, 0)
                    val delay = lastCombat + constants.combat_activecombat_delay
                    if (delay > mapClock.cycle) {
                        continue
                    }
                }

                if (mode.checkNotCombatSelf != -1) {
                    val lastCombat = vars.backing.getOrDefault(mode.checkNotCombatSelf, 0)
                    val delay = lastCombat + constants.combat_activecombat_delay
                    if (delay > mapClock.cycle) {
                        continue
                    }
                }
            }

            val checkVar1 = mode.checkVar1
            if (checkVar1 != null) {
                val varp =
                    ServerCacheManager.getVarp(checkVar1.varp) ?: error("Error finding varp 1")
                val actual = player.vars[varp]
                if (!checkVar1.evaluate(actual)) {
                    continue
                }
            }

            val checkVar2 = mode.checkVar2
            if (checkVar2 != null) {
                val varp =
                    ServerCacheManager.getVarp(checkVar2.varp) ?: error("Error finding varp 2")
                val actual = player.vars[varp]
                if (!checkVar2.evaluate(actual)) {
                    continue
                }
            }

            val checkVar3 = mode.checkVar3
            if (checkVar3 != null) {
                val varp =
                    ServerCacheManager.getVarp(checkVar3.varp) ?: error("Error finding varp 3")
                val actual = player.vars[varp]
                if (!checkVar3.evaluate(actual)) {
                    continue
                }
            }

            val checkInvObj = mode.checkInvObj
            if (checkInvObj != null) {
                val inventory = player.invMap.backing[checkInvObj.inv]

                val obj = checkInvObj.type
                val count = inventory?.sumOf { if (it?.id == obj) it.count else 0 } ?: 0

                if (!checkInvObj.evaluate(count)) {
                    continue
                }
            }

            val checkInvParam = mode.checkInvParam
            if (checkInvParam != null) {
                val inventory = player.invMap.backing[checkInvParam.inv]
                val param = checkInvParam.type

                var count = 0
                if (inventory != null) {
                    for (invObj in inventory) {
                        val objType = getOrNull(invObj) ?: continue
                        val value = objType.paramMap?.primitiveMap?.get(param) ?: continue
                        if (value !is Int) {
                            val message = "Expected param value to be Int: $value (param=$param)"
                            throw IllegalStateException(message)
                        }
                        count += value
                    }
                }

                if (!checkInvParam.evaluate(count)) {
                    continue
                }
            }

            // Reservoir sampling: the n-th eligible player replaces the pick with probability
            // 1/n, so every eligible player is equally likely and one is always chosen.
            count++
            if (random.of(minInclusive = 0, maxInclusive = count - 1) == 0) {
                target = player.uid
            }
        }

        if (target != PlayerUid.NULL) {
            huntPlayer = target
        }
    }

    // TODO: Investigate what `checkAfk` actually checks.
    private fun Player.isAfk(): Boolean {
        return false
    }

    // The wilderness plugin keeps this varbit in step with the player's area.
    private fun Player.isInWilderness(): Boolean {
        return vars["varbit.inside_wilderness"] == 1
    }

    // Hunt can be quite expensive if not careful. We are assuming that using a possibly delayed
    // multiway indicator will not have any inaccuracies in emulation. If it does, we can change
    // this to a dynamic lookup on `AreaIndex` instead.
    private fun Player.isInMulti(): Boolean {
        return vars["varbit.multiway_indicator"] == 1
    }
}
