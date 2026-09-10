package org.rsmod.content.bosses.vorkath

import dev.openrune.ServerCacheManager
import jakarta.inject.Inject
import org.rsmod.api.config.constants
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpc4
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class VorkathWorld
@Inject
constructor(
    private val encounters: VorkathEncounterManager,
    private val storage: VorkathDeathStorage,
    private val access: VorkathAccessPolicy,
) : PluginScript() {
    override fun ScriptContext.startup() {
        encounters.validateAssets()
        onEvent<NpcStateEvents.Create> {
            if (
                npc.type.internalName == VorkathAssets.SLEEPING ||
                    npc.type.internalName == VorkathAssets.SLEEPING_NOOP
            ) {
                npc.mode = null
                npc.movementLocked = true
            }
        }
        VORKATH_CRATER_ENTRANCE_LOC_IDS.forEach { id ->
            onOpLoc1(requireNotNull(ServerCacheManager.getObject(id))) {
                arriveDelay()
                val onPublicApproach =
                    VorkathRules.isPublicCraterApproach(
                        worldX = player.coords.x,
                        worldZ = player.coords.z,
                        level = player.coords.level,
                    )
                if (encounters.isActive(player) || onPublicApproach) {
                    val crossingX =
                        player.coords.lx.coerceIn(
                            VORKATH_WALL_MIN_LOCAL_X,
                            VORKATH_WALL_MAX_LOCAL_X,
                        )
                    crossIceWall(crossingX)
                } else {
                    player.mes("Nothing interesting happens.")
                }
            }
        }
        onOpNpc1(VorkathAssets.SLEEPING) {
            arriveDelay()
            encounters.poke(player, it.npc)
        }

        val ungaelTorfinn = listOf(VorkathAssets.TORFINN, VorkathAssets.TORFINN_COLLECT)
        val rellekkaTorfinn =
            listOf(VorkathAssets.TORFINN_RELLEKKA, VorkathAssets.TORFINN_COLLECT_RELLEKKA)
        ungaelTorfinn.forEach { torfinn ->
            onOpNpc1(torfinn) { talkToTorfinn(it.npc) }
            onOpNpc3(torfinn) {
                arriveDelay()
                encounters.teleportRellekka(player)
            }
            onOpNpc4(torfinn) { reclaimFromTorfinn(it.npc) }
        }
        rellekkaTorfinn.forEach { torfinn ->
            onOpNpc1(torfinn) { talkToTorfinn(it.npc) }
            onOpNpc3(torfinn) {
                arriveDelay()
                encounters.teleportOutside(player)
            }
            onOpNpc4(torfinn) { reclaimFromTorfinn(it.npc) }
        }
    }

    private suspend fun ProtectedAccess.crossIceWall(localX: Int) {
        if (encounters.isActive(player)) {
            val destination = encounters.wallTile(player, localX, inside = false)
            if (destination == null) {
                player.mes("The way over the ice chunks is unavailable.")
                return
            }
            anim(VorkathAssets.ICE_WALL_JUMP_ANIM)
            exactMove(
                player.coords,
                destination,
                delay1 = 0,
                delay2 = VORKATH_WALL_CROSS_CLIENT_CYCLES,
                dir = constants.em_face_south,
                teleportType = TeleportType.Exempt,
            )
            delay(2)
            encounters.escape(player, localX)
            return
        }
        if (!access.canAccess(player)) {
            player.mes("You must complete Dragon Slayer II before fighting Vorkath.")
            return
        }
        if (!encounters.enter(player, localX)) return
        val destination = encounters.wallTile(player, localX, inside = true)
        if (destination == null) {
            encounters.abort(player, "ice-wall crossing failed", teleport = true)
            return
        }
        anim(VorkathAssets.ICE_WALL_JUMP_ANIM)
        exactMove(
            player.coords,
            destination,
            delay1 = 0,
            delay2 = VORKATH_WALL_CROSS_CLIENT_CYCLES,
            dir = constants.em_face_north,
            teleportType = TeleportType.Exempt,
        )
        delay(2)
    }

    private suspend fun ProtectedAccess.talkToTorfinn(npc: org.rsmod.game.entity.Npc) =
        startDialogue(npc) {
            if (!storage.hasItems(player)) {
                chatNpc(neutral, "I am not holding any of your belongings.")
            } else {
                reclaimDialogue()
            }
        }

    private suspend fun ProtectedAccess.reclaimFromTorfinn(npc: org.rsmod.game.entity.Npc) =
        startDialogue(npc) { reclaimDialogue() }

    private suspend fun Dialogue.reclaimDialogue() {
        if (!storage.hasItems(player)) {
            chatNpc(neutral, "I am not holding any of your belongings.")
            return
        }
        chatNpc(neutral, "I recovered your belongings from Ungael. My fee is 100,000 coins.")
        val choice = choice2("Pay 100,000 coins.", 1, "Not now.", 2)
        if (choice == 1) storage.reclaim(player)
    }
}
