package org.rsmod.content.other.levelup

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.StatType
import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.script.advanced.onAdvanceStat
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.game.cheat.Cheat
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The level-up celebration: the jingle, the fireworks over the player's head, and the box in the
 * chatbox that says what went up.
 *
 * [org.rsmod.api.player.stat.PlayerSkillXP] already does the bookkeeping - it raises the base level,
 * re-sends the stat and recalculates the combat level - and then pushes an `AdvanceStat` engine
 * queue. Nothing was listening to it, so levelling up was silent. This script is that listener.
 *
 * Three fireworks play, matching Old School RuneScape:
 * - `spotanim.levelup_anim` for an ordinary level.
 * - `spotanim.levelup_99_anim` on reaching a skill's last level, in place of the ordinary set.
 * - `spotanim.levelup_max` once every released skill is at its ceiling.
 *
 * Which jingle goes with which skill, and how the two-jingle skills choose, lives in
 * [LevelUpJingles]. The chatbox box - `interface.levelup_display` - is in [LevelUpDisplay].
 *
 * #### Combat levels
 *
 * A combat level up gets its chat lines, but neither the jingle nor the box. Both are single-slot:
 * the client plays one jingle at a time and the chatbox holds one modal, and a combat level only
 * ever moves on the back of a combat skill advancing. `jingle.combat_level_up` and the interface's
 * own `combat` layer would therefore always land on top of - and replace - the skill that just
 * earned them. The skill keeps the presentation.
 *
 * #### Only one script may bind `onAdvanceStat`
 *
 * `onAdvanceStat` is an engine-queue script, and the engine allows a single default handler per
 * queue type. This plugin claims it. Anything else that wants to react to a level up should hook
 * into here rather than binding its own.
 */
class LevelUpScript
@Inject
constructor(private val unlocks: LevelUpUnlocks, private val display: LevelUpDisplay) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onPlayerLogin { player.attr[LAST_COMBAT_LEVEL] = player.combatLevel }

        onAdvanceStat { player.celebrate(it.args) }

        onCommand("testlevelup") {
            desc = "Replay the level-up jingle and fireworks for a skill, without granting xp"
            requiredRights = Rights.ADMINISTRATOR
            invalidArgs = "Usage: ::testlevelup <skill> [level]"
            cheat(::testLevelUp)
        }

        onCommand("jingle") {
            desc = "Play a jingle by archive id; no id plays the next one along"
            requiredRights = Rights.ADMINISTRATOR
            invalidArgs = "Usage: ::jingle [id]"
            cheat(::playJingle)
        }
    }

    /**
     * Plays everything a player should see and hear for having just reached a new [stat] level.
     *
     * The chat line goes out as an ordinary game message. The client also has a chat type of its
     * own for level ups (`ChatType.LevelUpMessage`, 118) which would let players filter them
     * separately; swap [mes]'s type over if you want that and your client renders it.
     */
    private fun Player.celebrate(stat: StatType, level: Int = statBase(stat.rscmName)) {
        val name = stat.name()
        val advanced = "Congratulations, you just advanced ${article(name)} $name level."
        val nowAt = "Your $name level is now $level."

        // Old School RuneScape only puts the first line in the chat log; the second one lives in
        // the level-up box. If this skill has no layer on that interface, both go to chat.
        mes(advanced)
        val layer = display.layerFor(stat.rscmName)
        if (layer != null) {
            display.show(this, layer, advanced, nowAt)
        } else {
            mes(nowAt)
        }

        val jingle = LevelUpJingles[stat.rscmName]
        if (jingle != null) {
            val unlocksContent = unlocks.unlocksContent(stat.id, level)
            Jingles.play(this, jingle.pick(LevelUp(level, stat.maxLevel, unlocksContent)))
        }

        spotanim(fireworks(stat, level), height = FIREWORKS_HEIGHT)

        announceCombatLevel()
    }

    /**
     * Old School RuneScape swaps the usual fireworks for a bigger set on a skill's last level, and
     * for a longer red and gold set once there is nothing left to level.
     */
    private fun Player.fireworks(stat: StatType, level: Int): String =
        when {
            level < stat.maxLevel -> ORDINARY_FIREWORKS
            hasMaxedEveryStat() -> MAXED_FIREWORKS
            else -> MASTERY_FIREWORKS
        }

    private fun Player.hasMaxedEveryStat(): Boolean =
        releasedStats().all { statBase(it.rscmName) >= it.maxLevel }

    /**
     * Sends the combat level lines if this level up pushed the combat level along with it.
     *
     * The combat level is recalculated inside `PlayerSkillXP` before the engine queue runs, so
     * [Player.combatLevel] is already the new value by the time this is called; the previous one is
     * tracked per session instead. A character that somehow reaches here without a stored value -
     * only possible if the login handler did not run - records the current level and stays quiet
     * rather than reporting a level up it cannot vouch for.
     */
    private fun Player.announceCombatLevel() {
        val previous = attr[LAST_COMBAT_LEVEL]
        val current = combatLevel
        attr[LAST_COMBAT_LEVEL] = current
        if (previous == null || current <= previous) {
            return
        }
        mes("Congratulations, you just advanced a Combat level.")
        mes("Your Combat level is now $current.")
    }

    /* Commands */

    private fun testLevelUp(cheat: Cheat) {
        val player = cheat.player
        val name = cheat.args.getOrNull(0)
        if (name == null) {
            player.mes("Usage: ::testlevelup <skill> [level]")
            return
        }

        val stat = findStat(name)
        if (stat == null) {
            player.mes("No skill found named '$name'.")
            return
        }

        val level = cheat.args.getOrNull(1)?.toInt() ?: player.statBase(stat.rscmName)
        player.celebrate(stat, level.coerceIn(1, stat.maxLevel))
    }

    /**
     * Plays one jingle straight from archive 11, bypassing the name lookup, so the ids can be
     * identified by ear - the cache has no jingle names left to derive them from. With no
     * argument it plays the one after whatever was played last, so a run of ids can be walked
     * through by repeating the command. The level-up family is most likely somewhere in 28-70.
     */
    private fun playJingle(cheat: Cheat) {
        val player = cheat.player
        val id = cheat.args.getOrNull(0)?.toInt() ?: ((player.attr[LAST_JINGLE_ID] ?: -1) + 1)
        player.attr[LAST_JINGLE_ID] = id
        Jingles.playRaw(player, id)
        player.mes("Playing jingle $id.")
    }

    private fun findStat(name: String): StatType? =
        releasedStats().firstOrNull {
            it.displayName.equals(name, ignoreCase = true) ||
                it.rscmName.substringAfter('.').equals(name, ignoreCase = true)
        }

    private companion object {
        /** Matches the height Old School RuneScape draws over-the-head effects at. */
        private const val FIREWORKS_HEIGHT = 100

        private const val ORDINARY_FIREWORKS = "spotanim.levelup_anim"
        private const val MASTERY_FIREWORKS = "spotanim.levelup_99_anim"
        private const val MAXED_FIREWORKS = "spotanim.levelup_max"

        /**
         * The combat level as of the last time it was reported, so a level up can be spotted after
         * the engine has already applied it. Per session; a fresh login re-seeds it.
         */
        private val LAST_COMBAT_LEVEL = AttributeKey<Int>()

        /** The last archive id played with `::jingle`, so the command can step to the next. */
        private val LAST_JINGLE_ID = AttributeKey<Int>()

        private val StatType.rscmName: String
            get() = RSCM.getReverseMapping(RSCMType.STAT, id)

        private fun releasedStats(): Collection<StatType> =
            ServerCacheManager.getStats().values.filterNot(StatType::unreleased)

        private fun StatType.name(): String =
            displayName.ifBlank { rscmName.substringAfter('.').replaceFirstChar(Char::titlecase) }

        private fun article(name: String): String =
            if ((name.firstOrNull() ?: ' ').lowercaseChar() in VOWELS) "an" else "a"

        private val VOWELS = setOf('a', 'e', 'i', 'o', 'u')
    }
}
