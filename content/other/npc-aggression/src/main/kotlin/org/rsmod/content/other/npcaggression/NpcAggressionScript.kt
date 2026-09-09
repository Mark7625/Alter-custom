package org.rsmod.content.other.npcaggression

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcServerType
import org.rsmod.api.config.refs.params
import org.rsmod.api.script.onEvent
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Makes the monsters the OSRS wiki lists as aggressive hunt players.
 *
 * The cache carries no aggression flag, so without server data every monster stands still until
 * attacked. [NpcAggressionFile.GENERATED_RESOURCE] lists the npcs whose wiki infobox says
 * `aggressive = Yes`; this script gives each of them a player-hunting mode at boot, before the
 * map spawns its npcs, unless the npc's own config already declares one (bosses and God Wars
 * creatures do). Melee monsters get `stalk.constant_melee`, which walks up to the target;
 * ranged and magic monsters get `stalk.constant_ranged`, which attacks from range. Both modes
 * only hunt players of at most twice the monster's combat level outside the Wilderness and
 * respect the ten-minute tolerance, exactly as the real game does.
 */
class NpcAggressionScript : PluginScript() {
    override fun ScriptContext.startup() {
        val generated = NpcAggressionFile.loadResource(NpcAggressionFile.GENERATED_RESOURCE)
        val overrides = NpcAggressionFile.loadResource(NpcAggressionFile.OVERRIDES_RESOURCE)
        val applied = apply(generated.npc, force = false)
        val forced = apply(overrides.npc, force = true)
        logger.info { "Applied aggression to $applied npcs ($forced overrides)." }

        // Map npcs are constructed while the map loads, before any script runs, and copy their
        // hunt mode from the type at that point. Give them the mode the type has now when they
        // spawn; npcs created later already see the updated type.
        onEvent<NpcStateEvents.Create> {
            val typeMode = npc.type.huntMode
            if (npc.huntMode == null && typeMode != null) {
                ServerCacheManager.getHunt(typeMode)?.let(npc::setHuntMode)
            }
        }
    }

    private fun apply(entries: List<NpcAggressionEntry>, force: Boolean): Int {
        var applied = 0
        for (entry in entries) {
            val id = entry.id.asRSCM(RSCMType.NPC)
            val type = ServerCacheManager.getNpc(id)
            if (type == null) {
                logger.warn { "Npc not in cache, skipping: ${entry.id}" }
                continue
            }
            if (!entry.aggressive) {
                type.huntMode = null
                applied++
                continue
            }
            if (!force && type.huntMode != null) {
                continue
            }
            if (!type.hasOp(ATTACK_OP)) {
                continue
            }
            type.huntMode = if (type.attacksFromRange()) HUNT_CONSTANT_RANGED else HUNT_CONSTANT_MELEE
            applied++
        }
        return applied
    }

    /** Ranged and magic attackers stop at range rather than walking up to their target. */
    private fun NpcServerType.attacksFromRange(): Boolean {
        val attackType = paramOrNull(params.npc_attack_type)
        if (attackType != null) {
            return RANGED_ATTACK_TYPES.any { attackType.isType(it) }
        }
        return paramOrNull(params.proj_travel) != null
    }

    private companion object {
        private val logger = InlineLogger()

        /** The `Attack` op sits in slot 2 for every attackable npc. */
        private const val ATTACK_OP = 2

        /** Hunt modes from `.data/gamevals/stalk.rscm`; there is no RSCM prefix for them. */
        private const val HUNT_CONSTANT_MELEE = 1
        private const val HUNT_CONSTANT_RANGED = 2

        private val RANGED_ATTACK_TYPES =
            listOf(
                "category.attacktype_light",
                "category.attacktype_standard",
                "category.attacktype_heavy",
                "category.attacktype_magic",
            )
    }
}
