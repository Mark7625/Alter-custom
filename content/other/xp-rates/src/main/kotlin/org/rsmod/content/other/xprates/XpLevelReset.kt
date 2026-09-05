package org.rsmod.content.other.xprates

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.StatType
import jakarta.inject.Inject
import org.rsmod.api.player.output.UpdateStat
import org.rsmod.api.player.stat.PlayerSkillXP
import org.rsmod.api.player.ui.PlayerInterfaceUpdates
import org.rsmod.api.stats.levelmod.InvisibleLevels
import org.rsmod.game.entity.Player
import org.rsmod.game.stat.PlayerSkillXPTable

/**
 * Drops every skill back to the values a brand new character starts with: level 1 with no xp, plus
 * Hitpoints at its `minLevel` (10 in Old School RuneScape) with the xp to match.
 *
 * Those are exactly the values [org.rsmod.api.stats.plugin.InitialStatsScript] seeds a new account
 * with, read from the same cache field, so a reset character is indistinguishable from a fresh one.
 * Skills are all this touches - inventory, bank, quest progress, location and appearance are left
 * alone.
 *
 * There is deliberately no shared engine helper for lowering xp; both admin plugins in this repo
 * carry their own local copy, with a note that xp reduction is not a normal gameplay operation.
 * Unlike their general "set this skill to level N" helpers, this one only ever moves down to a
 * known-good starting point, so it can write the stat map directly rather than unwinding level by
 * level.
 */
class XpLevelReset @Inject constructor(private val invisibleLevels: InvisibleLevels) {
    /**
     * Resets every released skill on [player] and re-sends them to the client.
     *
     * @return the number of skills that were above their starting values, so callers can stay quiet
     *   when there was nothing to reset (a brand new character, or a second pass).
     */
    fun reset(player: Player): Int {
        var changed = 0
        for (type in releasedStats()) {
            if (player.resetStat(type)) {
                changed++
            }
        }
        player.appearance.combatLevel = PlayerSkillXP.calculateCombatLevel(player)
        PlayerInterfaceUpdates.updateCombatLevel(player)
        return changed
    }

    private fun Player.resetStat(type: StatType): Boolean {
        val internal = RSCM.getReverseMapping(RSCMType.STAT, type.id)

        // `minLevel` is 10 for Hitpoints and 1 for everything else; the table has no entry for 0.
        val level = type.minLevel.coerceAtLeast(1)
        val fineXp = PlayerSkillXPTable.getFineXPFromLevel(level)
        val changed = statMap.getFineXP(internal) != fineXp

        statMap.setFineXP(internal, fineXp)
        statMap.setBaseLevel(internal, level.toByte())
        statMap.setCurrentLevel(internal, level.toByte())

        val hiddenLevel = level + invisibleLevels.get(this, internal)
        UpdateStat.update(this, type, statMap.getXP(internal), level, hiddenLevel)
        return changed
    }

    private companion object {
        fun releasedStats(): List<StatType> =
            ServerCacheManager.getStats().values.filterNot(StatType::unreleased)
    }
}
