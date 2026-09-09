package org.rsmod.content.other.playtime

import jakarta.inject.Inject
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Surfaces the `varp.playtime` counter that [org.rsmod.content.other.mapclock] increments once per
 * cycle: a greeting on login and a `::playtime [name]` lookup for any online player.
 */
class PlaytimeScript @Inject constructor(private val playerList: PlayerList) : PluginScript() {
    private var Player.playtime by intVarp("varp.playtime")

    override fun ScriptContext.startup() {
        onPlayerLogin { player.greet() }

        onCommand("playtime") {
            desc = "Show how long you (or another online player) have played for"
            cheat {
                val target = args.firstOrNull()?.let(::findOnline) ?: player
                if (args.isNotEmpty() && target === player && !player.matches(args[0])) {
                    player.mes("No online player found named '${args[0]}'.")
                    return@cheat
                }
                if (target === player) {
                    player.mes("You have played for ${format(player.playtime)}.")
                } else {
                    player.mes("${target.displayName} has played for ${format(target.playtime)}.")
                }
            }
        }
    }

    private fun Player.greet() {
        val cycles = playtime
        if (cycles < CYCLES_PER_HOUR) {
            mes("Welcome. Type <col=800000>::playtime</col> to see how long you have played for.")
            return
        }
        mes("Welcome back. You have played for ${format(cycles)}.")
    }

    private fun findOnline(name: String): Player? = playerList.firstOrNull { it.matches(name) }

    private fun Player.matches(name: String): Boolean =
        displayName.equals(name.replace('_', ' '), ignoreCase = true)

    private fun format(cycles: Int): String {
        val totalMinutes = (cycles.toLong() * MILLIS_PER_CYCLE) / 60_000
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours > 0 -> "$hours hour${plural(hours)} and $minutes minute${plural(minutes)}"
            minutes > 0 -> "$minutes minute${plural(minutes)}"
            else -> "less than a minute"
        }
    }

    private fun plural(value: Long): String = if (value == 1L) "" else "s"

    private companion object {
        const val MILLIS_PER_CYCLE = 600L
        const val CYCLES_PER_HOUR = 6_000
    }
}
