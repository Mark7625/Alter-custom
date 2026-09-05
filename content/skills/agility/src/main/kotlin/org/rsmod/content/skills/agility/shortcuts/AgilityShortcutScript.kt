package org.rsmod.content.skills.agility.shortcuts

import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.agilityLvl
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.skills.agility.AgilityAnims
import org.rsmod.content.skills.agility.BalanceStyle
import org.rsmod.content.skills.agility.balanceAlong
import org.rsmod.content.skills.agility.climbTo
import org.rsmod.content.skills.agility.leapTo
import org.rsmod.content.skills.agility.stepOnto
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Moves players across the two-way agility shortcuts listed in [AgilityShortcuts]. */
class AgilityShortcutScript : PluginScript() {
    override fun ScriptContext.startup() {
        val byLoc = mutableMapOf<String, MutableList<AgilityShortcut>>()
        for (shortcut in AgilityShortcuts.all) {
            for (loc in shortcut.locs) {
                byLoc.getOrPut(loc) { mutableListOf() } += shortcut
            }
        }
        for ((loc, shortcuts) in byLoc) {
            onOpLoc1(loc) { use(shortcuts) }
        }
    }

    private suspend fun ProtectedAccess.use(candidates: List<AgilityShortcut>) {
        arriveDelay()
        val shortcut = candidates.minBy { it.distanceTo(coords) }
        if (player.agilityLvl < shortcut.level) {
            mes("You need an Agility level of ${shortcut.level} to use this shortcut.")
            return
        }
        val fromA = shortcut.startsFromA(coords)
        val from = if (fromA) shortcut.sideA else shortcut.sideB
        val dest = if (fromA) shortcut.sideB else shortcut.sideA
        stepOnto(from)
        cross(shortcut.move, dest, fromA)
        if (shortcut.xp > 0.0) {
            statAdvance("stat.agility", shortcut.xp)
        }
    }

    private suspend fun ProtectedAccess.cross(move: ShortcutMove, dest: CoordGrid, fromA: Boolean) {
        when (move) {
            is ShortcutMove.Climb -> climbTo(dest, move.seq, move.ticks)
            is ShortcutMove.Jump -> leapTo(dest, move.seq, move.ticks)
            is ShortcutMove.Squeeze -> {
                faceSquare(dest)
                anim(move.enter)
                delay(move.ticks)
                telejump(dest, TeleportType.Exempt)
                anim(move.leave)
            }
            is ShortcutMove.Balance -> {
                val path = (if (fromA) move.tiles else move.tiles.reversed()) + dest
                balanceAlong(path, BalanceStyle.Tightrope)
            }
            is ShortcutMove.Hop -> {
                val stones = (if (fromA) move.stones else move.stones.reversed()) + dest
                for (stone in stones) {
                    leapTo(stone, AgilityAnims.STEPPING_STONE, ticks = 1)
                }
            }
        }
    }
}
