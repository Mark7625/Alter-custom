package org.rsmod.content.other.xprates

import com.github.michaelbull.logging.InlineLogger
import dev.or2.central.account.Rights
import jakarta.inject.Inject
import kotlin.math.roundToLong
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.realm.Realm
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.game.cheat.Cheat
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Puts experience gain back on Old School RuneScape's own curve.
 *
 * The per-action xp values in `content/skills` are already authentic - they are read from the cache
 * (`params.skill_xp`) or hard-coded from the wiki, and combat pays the usual 4 xp per damage with
 * 1.33 hitpoints xp alongside. What inflates levelling is the *multiplier* applied on top of them
 * in [org.rsmod.api.player.stat.statAdvance]:
 *
 * - [Player.xpRate] is the character's personal multiplier. It is seeded from the realm's
 *   `player_xp_rate_in_hundreds` when the character is created and then persisted on the character
 *   row, so it survives a realm config change. The bundled development realm seeds it at **150x**,
 *   which is where "levels go by way too fast" comes from.
 * - [Player.globalXpRate] is a server-wide multiplier compounded on top, intended for events like a
 *   double xp weekend. Nothing currently assigns it, so it sits at `1.0`.
 *
 * This script owns both: it applies the rates from `xp-rates.yml` (default `1.0`, i.e. authentic)
 * on login, which overwrites the inflated value already stored on existing characters. Because
 * [Player.xpRate] is written back on save, a character only needs to log in once to be corrected
 * permanently.
 *
 * #### Why the rate is not per-skill
 * [org.rsmod.api.player.stat.statAdvance] takes a single multiplier and has no hook that sees which
 * stat is being advanced, so a plugin cannot vary the rate by skill without an engine change.
 * [org.rsmod.api.stats.xpmod.XpMod] looks like it could, but it is additive and only consulted by
 * the gathering skills that opt into it - using it for rates would silently skip combat and every
 * artisan skill. One honest global multiplier beats a per-skill one that only works in five places.
 *
 * #### Clearing levels earned at the old rate
 * Changing the rate does not undo the levels it already handed out. [XpLevelReset] resets a
 * character's skills to fresh-account values, driven either by `reset-existing-levels` (once per
 * character, on login) or by `::resetxp` / `::resetxpall` on demand.
 */
class XpRatesScript
@Inject
constructor(
    private val realm: Realm,
    private val playerList: PlayerList,
    private val levelReset: XpLevelReset,
    private val protectedAccess: ProtectedAccessLauncher,
) : PluginScript() {
    private val logger = InlineLogger()
    private val loader = XpRateConfigLoader()

    private var config: XpRateConfig = XpRateConfig()

    /** Realms are only readable once services start, so the mismatch is reported once, on login. */
    private var warnedAboutRealmRate = false

    override fun ScriptContext.startup() {
        config = loader.loadOrCreate()
        logger.info {
            "Xp rates loaded: rate=${config.rate}x global=${config.globalRate}x " +
                "enforceOnLogin=${config.enforceOnLogin}"
        }

        onPlayerLogin { player.applyRates() }

        onCommand("xprate") {
            desc = "Show your current experience rate"
            cheat { player.reportRates() }
        }

        onCommand("setxprate") {
            desc = "Set a player's personal experience rate"
            requiredRights = Rights.ADMINISTRATOR
            invalidArgs = "Usage: ::setxprate <rate> [player]"
            cheat(::setXpRate)
        }

        onCommand("globalxprate") {
            desc = "Set the server-wide experience multiplier"
            requiredRights = Rights.ADMINISTRATOR
            invalidArgs = "Usage: ::globalxprate <rate>"
            cheat(::setGlobalXpRate)
        }

        onCommand("reloadxprates") {
            desc = "Reload xp-rates.yml and re-apply it to everyone online"
            requiredRights = Rights.ADMINISTRATOR
            cheat(::reloadRates)
        }

        onCommand("resetxp") {
            desc = "Reset a player's skills to fresh-account levels"
            requiredRights = Rights.ADMINISTRATOR
            invalidArgs = "Usage: ::resetxp [player]"
            cheat(::resetXp)
        }

        onCommand("resetxpall") {
            desc = "Reset every online player's skills to fresh-account levels"
            requiredRights = Rights.ADMINISTRATOR
            cheat(::resetXpAll)
        }
    }

    /* Login */

    private fun Player.applyRates() {
        warnAboutRealmRateOnce()

        // `globalXpRate` is not persisted on the character, so it is always re-applied.
        globalXpRate = config.globalRate

        if (config.enforceOnLogin && xpRate != config.rate) {
            val previous = xpRate
            xpRate = config.rate
            logger.info {
                "Corrected stored xp rate for '$username': " +
                    "${format(previous)}x -> ${format(config.rate)}x"
            }
        }

        val effective = effectiveRate()
        if (effective != XpRateConfig.AUTHENTIC_RATE) {
            mes("Experience rate: <col=800000>${format(effective)}x</col>.")
        }

        resetLevelsOnce()
    }

    /**
     * Clears levels earned at an old, inflated rate, once per character.
     *
     * The flag is stamped *before* the reset runs. If the reset were to throw halfway, resetting a
     * character's skills again on every single login is a far worse failure than leaving one
     * character half-reset for an admin to finish with `::resetxp`.
     */
    private fun Player.resetLevelsOnce() {
        if (!config.resetExistingLevels || attr.has(LEVELS_RESET)) {
            return
        }
        attr[LEVELS_RESET] = RESET_STAMP

        val changed = levelReset.reset(this)
        if (changed > 0) {
            mes("Your skills have been reset to suit the server's experience rates.")
            logger.info { "Reset $changed skills for '$username' (one-time xp rate migration)." }
        }
    }

    /**
     * The realm's `player_xp_rate_in_hundreds` is what stamps a rate onto newly created characters.
     * Overriding it on login fixes the symptom for every character, but leaving the realm row alone
     * means the mismatch is invisible - so say it out loud once per boot.
     */
    private fun warnAboutRealmRateOnce() {
        if (warnedAboutRealmRate || !config.enforceOnLogin) {
            return
        }
        warnedAboutRealmRate = true
        val realmRate = runCatching { realm.config.baseXpRate }.getOrNull() ?: return
        if (realmRate != config.rate) {
            logger.warn {
                "Realm '${realm.name}' assigns new characters ${format(realmRate)}x xp, but " +
                    "xp-rates.yml enforces ${format(config.rate)}x. Characters are corrected on " +
                    "login; set the realm's `player_xp_rate_in_hundreds` to " +
                    "${(config.rate * 100).roundToLong()} to fix it at the source."
            }
        }
    }

    /* Commands */

    private fun Player.reportRates() {
        mes("Your experience rate: <col=800000>${format(effectiveRate())}x</col>.")
        if (globalXpRate != XpRateConfig.AUTHENTIC_RATE) {
            mes(
                "(personal <col=800000>${format(xpRate)}x</col> " +
                    "x server-wide <col=800000>${format(globalXpRate)}x</col>)"
            )
        }
    }

    private fun setXpRate(cheat: Cheat) =
        with(cheat) {
            val rate = XpRateConfig.clampRate(args[0].toDouble())
            val targetName = args.getOrNull(1)
            val target =
                if (targetName == null) {
                    player
                } else {
                    findOnline(targetName)
                        ?: run {
                            player.mes("No online player found named '$targetName'.")
                            return
                        }
                }

            target.xpRate = rate
            target.mes("Your experience rate is now <col=800000>${format(rate)}x</col>.")
            if (target !== player) {
                player.mes("Set ${target.displayName}'s experience rate to ${format(rate)}x.")
            }
            if (config.enforceOnLogin) {
                player.mes(
                    "Note: `enforce-on-login` is on, so this resets to " +
                        "${format(config.rate)}x on their next login."
                )
            }
        }

    private fun setGlobalXpRate(cheat: Cheat) =
        with(cheat) {
            val rate = XpRateConfig.clampRate(args[0].toDouble())
            config = config.copy(globalRate = rate)
            for (online in playerList) {
                online.globalXpRate = rate
                online.mes("Server-wide experience rate is now <col=800000>${format(rate)}x</col>.")
            }
            player.mes("Edit xp-rates.yml to keep this across a reboot.")
            logger.info { "'${player.username}' set the server-wide xp rate to ${format(rate)}x." }
        }

    private fun reloadRates(cheat: Cheat) =
        with(cheat) {
            config = loader.loadOrCreate()
            for (online in playerList) {
                online.globalXpRate = config.globalRate
                if (config.enforceOnLogin) {
                    online.xpRate = config.rate
                }
            }
            player.mes(
                "Reloaded xp-rates.yml: rate <col=800000>${format(config.rate)}x</col>, " +
                    "server-wide <col=800000>${format(config.globalRate)}x</col>."
            )
        }

    private fun resetXp(cheat: Cheat) =
        with(cheat) {
            val targetName = args.getOrNull(0)
            val target =
                if (targetName == null) {
                    player
                } else {
                    findOnline(targetName)
                        ?: run {
                            player.mes("No online player found named '$targetName'.")
                            return
                        }
                }
            val label = if (target === player) "your own character" else target.displayName

            player.confirmThen("Reset every skill on $label?") {
                val changed = applyReset(target)
                if (target === player) {
                    player.mes("Reset $changed ${plural(changed, "skill")}.")
                } else {
                    player.mes("Reset $changed ${plural(changed, "skill")} on ${target.displayName}.")
                    target.mes("An administrator has reset your skills.")
                }
                logger.info {
                    "'${player.username}' reset $changed skills on '${target.username}'."
                }
            }
        }

    private fun resetXpAll(cheat: Cheat) =
        with(cheat) {
            val online = playerList.count()
            player.confirmThen("Reset every skill on all $online online ${plural(online, "player")}?") {
                // Re-read the list: players can log in or out while the dialogue is open.
                var reset = 0
                for (target in playerList.toList()) {
                    applyReset(target)
                    if (target !== player) {
                        target.mes("An administrator has reset your skills.")
                    }
                    reset++
                }
                player.mes("Reset the skills of $reset ${plural(reset, "player")}.")
                logger.warn { "'${player.username}' reset the skills of $reset online players." }
            }
        }

    /* Helpers */

    /**
     * Resets [target]'s skills and marks them as migrated, so the one-time `reset-existing-levels`
     * pass does not repeat the work on their next login.
     */
    private fun applyReset(target: Player): Int {
        val changed = levelReset.reset(target)
        target.attr[LEVELS_RESET] = RESET_STAMP
        return changed
    }

    /**
     * Puts a yes/no dialogue in front of an irreversible command. The cheat handler itself is not a
     * coroutine, so the dialogue needs its own [ProtectedAccess]; if the admin is mid-interaction
     * the launcher declines and says so, rather than silently doing nothing.
     */
    private fun Player.confirmThen(question: String, action: () -> Unit) {
        protectedAccess.launch(this, busyText = BUSY_TEXT) {
            if (choice2("Yes", true, "No", false, title = question)) {
                action()
            }
        }
    }

    private fun Player.effectiveRate(): Double = xpRate * globalXpRate

    private fun plural(count: Int, noun: String): String = if (count == 1) noun else "${noun}s"

    private fun findOnline(name: String): Player? =
        playerList.firstOrNull { it.displayName.equals(name.replace('_', ' '), ignoreCase = true) }

    private fun format(rate: Double): String {
        val rounded = (rate * 100).roundToLong() / 100.0
        return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
    }

    private companion object {
        private const val BUSY_TEXT = "Please finish what you're doing first."

        /**
         * Marks a character as having had the one-time level reset. Persisted attributes round-trip
         * through JSON, and the [AttributeKey] docs warn off floating point, so this is an `Int`
         * holding [RESET_STAMP] rather than a boolean flag.
         */
        private val LEVELS_RESET = AttributeKey<Int>(persistenceKey = "xp_rates_levels_reset")

        private const val RESET_STAMP = 1
    }
}
