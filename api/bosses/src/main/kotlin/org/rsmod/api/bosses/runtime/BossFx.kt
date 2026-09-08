package org.rsmod.api.bosses.runtime

import dev.openrune.types.aconverted.SpotanimType
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.proj.ProjAnim
import org.rsmod.map.CoordGrid

fun BossDeps.bossProjectile(
    spotanim: Int,
    src: CoordGrid,
    target: CoordGrid,
    startHeight: Int,
    endHeight: Int,
    delay: Int,
    travel: Int,
    curve: Int,
    progress: Int = 0,
): ProjAnim {
    val proj =
        ProjAnim(
            spotanim = spotanim,
            startHeight = startHeight,
            endHeight = endHeight,
            startTime = delay,
            endTime = delay + travel,
            angle = curve,
            progress = progress,
            sourceIndex = 0,
            targetIndex = 0,
            startCoord = src,
            endCoord = target,
        )
    worldRepo.projAnim(proj)
    return proj
}

fun BossDeps.suppressAttacks(npc: Npc, ticks: Int) {
    encounterRegistry.of(npc).lastAbilityTick = mapClock.cycle + ticks
}

fun BossDeps.encounter(npc: Npc): BossEncounter = encounterRegistry.of(npc)

fun BossDeps.repeatTick(
    ticks: Int,
    onTick: (remaining: Int) -> Boolean,
    onStop: () -> Unit = {},
) {
    fun step(remaining: Int) {
        worldQueues.add(1) {
            if (remaining <= 0 || !onTick(remaining)) {
                onStop()
            } else {
                step(remaining - 1)
            }
        }
    }
    step(ticks)
}

fun BossDeps.lob(
    npc: Npc,
    targetTile: CoordGrid,
    targetUid: PlayerUid,
    spotanim: Int,
    startHeight: Int,
    endHeight: Int,
    delay: Int,
    travel: Int,
    curve: Int,
    landTicks: Int,
    landGfx: Int,
    landGfxHeight: Int = 0,
    progress: Int = 0,
    onLand: (Player) -> Unit,
) {
    bossProjectile(
        spotanim,
        npc.coords.translate(2, 2),
        targetTile,
        startHeight,
        endHeight,
        delay,
        travel,
        curve,
        progress,
    )
    worldQueues.add(landTicks) {
        worldRepo.spotanimMap(SpotanimType(landGfx), targetTile, landGfxHeight)
        val player = targetUid.resolve(playerList) ?: return@add
        if (player.hitpoints > 0) {
            onLand(player)
        }
    }
}
