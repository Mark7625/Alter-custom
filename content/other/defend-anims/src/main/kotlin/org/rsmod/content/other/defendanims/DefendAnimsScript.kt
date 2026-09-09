package org.rsmod.content.other.defendanims

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ParamMap
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import org.rsmod.api.config.refs.params
import org.rsmod.api.script.onGameStartup
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Fills in `param.defend_anim` on every cached item that [DefendAnims] has an answer for, so the
 * combat api's existing resolver plays the right block animation when a player is hit.
 *
 * This runs once, on [onGameStartup], after the cache is loaded and before the first game cycle.
 * It edits the shared [ItemServerType] instances in place - the same objects that
 * `getOrNull(player.righthand)` hands to the resolver - so no combat code needs to change and no
 * cache rebuild is needed. A pack could ship the same params, but this way a new item added to the
 * cache is covered on the next boot without touching a TOML file.
 *
 * Only items with no `param.defend_anim` are changed. Certificates and placeholders are skipped;
 * they are never worn.
 */
class DefendAnimsScript : PluginScript() {
    override fun ScriptContext.startup() {
        onGameStartup { applyCorrections() }
    }

    private fun applyCorrections() {
        val sequenceIds = DefendAnims.allAnimations.associateWith { it.asRSCM(RSCMType.SEQ) }
        for ((seq, id) in sequenceIds) {
            checkNotNull(ServerCacheManager.getAnim(id)) { "Missing sequence in cache: $seq ($id)" }
        }

        var corrected = 0
        for (item in ServerCacheManager.getItems().values) {
            val seq = correctionFor(item) ?: continue
            item.paramMap = item.paramMap.withDefendAnim(sequenceIds.getValue(seq))
            corrected++
        }
        logger.info { "Corrected the block animation of $corrected items." }
    }

    private fun correctionFor(item: ItemServerType): String? {
        if (item.isCert || item.isPlaceholder) {
            return null
        }
        val hasDefendAnim = item.paramMap?.contains(params.defend_anim) == true
        return DefendAnims.resolve(item.name, item.wearpos1, item.weaponCategory, hasDefendAnim)
    }

    /**
     * A new map rather than [ParamMap.plus]: `plus` keeps this map's already-decoded typed values
     * and would not include the new key, so the resolver would still see nothing.
     */
    private fun ParamMap?.withDefendAnim(sequenceId: Int): ParamMap {
        val primitives = this?.primitiveMap.orEmpty() + (params.defend_anim.id to sequenceId)
        return ParamMap(primitiveMap = primitives)
    }

    private companion object {
        private val logger = InlineLogger()
    }
}
