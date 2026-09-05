package org.rsmod.content.other.levelup

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.config.refs.params

/**
 * Decides which levels count as "unlocking new content" for the seventeen skills whose second
 * level-up jingle is reserved for exactly that.
 *
 * Old School RuneScape's own unlock lists are not something the server can read back: they are
 * spread across quests, spellbooks, shop stock and every skilling action in the game, and any list
 * copied out of the wiki would be wrong the moment this server's content differs from theirs.
 *
 * What the cache *does* hold, on every wieldable obj, is the level you need to equip it
 * ([params.statreq1_level] / [params.statreq2_level]). Scanning those gives an unlock set that is
 * exactly right for whatever gear this server actually ships - the Attack tiers, the armour tiers,
 * the bows and staves, and the second requirement that gates things like a dragon axe on
 * Woodcutting. Skills with no equipment gate simply never take their unlock jingle, which is the
 * conservative outcome: they keep playing their normal one.
 *
 * [EXTRA_UNLOCK_LEVELS] is the hook for filling in the rest. Add the levels your own content
 * unlocks at and they are merged into the scan.
 */
@Singleton
class LevelUpUnlocks @Inject constructor() {
    private val levelsByStat: Map<Int, Set<Int>> by lazy(::scan)

    /** Returns `true` if reaching [level] in [statId] makes new content available. */
    fun unlocksContent(statId: Int, level: Int): Boolean = level in levelsByStat[statId].orEmpty()

    private fun scan(): Map<Int, Set<Int>> {
        val levels = HashMap<Int, MutableSet<Int>>()

        for (obj in ServerCacheManager.getItemTypes()) {
            obj.recordRequirement(levels, first = true)
            obj.recordRequirement(levels, first = false)
        }

        for ((stat, extra) in EXTRA_UNLOCK_LEVELS) {
            levels.getOrPut(stat.asRSCM(RSCMType.STAT), ::mutableSetOf) += extra
        }

        logger.debug {
            "Level-up unlock levels resolved for ${levels.size} stats " +
                "(${levels.values.sumOf(Set<Int>::size)} levels in total)."
        }
        return levels
    }

    private fun ItemServerType.recordRequirement(
        levels: MutableMap<Int, MutableSet<Int>>,
        first: Boolean,
    ) {
        val stat = paramOrNull(if (first) params.statreq1_skill else params.statreq2_skill) ?: return
        val level = paramOrNull(if (first) params.statreq1_level else params.statreq2_level) ?: return
        if (level <= 1) {
            return
        }
        levels.getOrPut(stat.id, ::mutableSetOf) += level
    }

    private companion object {
        private val logger = InlineLogger()

        /**
         * Hand-maintained unlock levels, merged on top of the equipment scan. Keys are stat RSCM
         * names; an unknown name fails loudly at first use rather than silently doing nothing.
         *
         * Nothing is listed by default - the equipment scan already covers the combat skills, and
         * anything added here is a claim about *this* server's content, so it is left for whoever
         * knows what that content is. For example:
         *
         * ```
         * "stat.cooking" to setOf(15, 25, 30, 40, 50, 62, 80),
         * ```
         */
        private val EXTRA_UNLOCK_LEVELS: Map<String, Set<Int>> = emptyMap()
    }
}
