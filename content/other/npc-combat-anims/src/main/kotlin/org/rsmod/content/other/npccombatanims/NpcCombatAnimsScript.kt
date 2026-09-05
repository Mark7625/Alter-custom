package org.rsmod.content.other.npccombatanims

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ParamMap
import dev.openrune.ServerCacheManager
import dev.openrune.TypedParamType
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcServerType
import org.rsmod.api.config.refs.params
import org.rsmod.api.script.onGameStartup
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Gives npcs the combat animations the cache leaves them without.
 *
 * Npc-versus-player combat plays one `param.attack_anim` per npc, defaulting to the bare-handed
 * punch, and only a few hundred npcs declare one. A guard with a sword punches, a hill giant
 * punches with a human animation, and a wizard punches too. The same goes for the block and
 * death animations, and for the attack type: with no `param.npc_attack_type`, an archer walks up
 * and melees.
 *
 * `tools/combat-anims` works out what each npc should do - from the weapon and shield it is drawn
 * holding, or from the animation family its ready animation belongs to - and writes the answer
 * to [NpcCombatAnimsFile.GENERATED_RESOURCE]. This script applies that file, then
 * [NpcCombatAnimsFile.OVERRIDES_RESOURCE] on top, to the cached npc types once at game startup.
 * The generated entries never replace a value the npc's own data declares; overrides always do.
 * Ranged and magic entries also raise the npc's attack range so it fights from a distance.
 */
class NpcCombatAnimsScript : PluginScript() {
    override fun ScriptContext.startup() {
        onGameStartup { applyAll() }
    }

    private fun applyAll() {
        val generated = NpcCombatAnimsFile.loadResource(NpcCombatAnimsFile.GENERATED_RESOURCE)
        val overrides = NpcCombatAnimsFile.loadResource(NpcCombatAnimsFile.OVERRIDES_RESOURCE)
        val applied = apply(generated.npc, force = false)
        val forced = apply(overrides.npc, force = true)
        logger.info { "Applied combat animations to $applied npcs ($forced overrides)." }
    }

    private fun apply(entries: List<NpcCombatAnimEntry>, force: Boolean): Int {
        var applied = 0
        for (entry in entries) {
            val id = entry.id.asRSCM(RSCMType.NPC)
            val type = ServerCacheManager.getNpc(id)
            if (type == null) {
                logger.warn { "Npc not in cache, skipping: ${entry.id}" }
                continue
            }
            if (type.apply(entry, force)) {
                applied++
            }
        }
        return applied
    }

    /** Returns `true` if anything changed. */
    private fun NpcServerType.apply(entry: NpcCombatAnimEntry, force: Boolean): Boolean {
        val values = mutableMapOf<Int, Any>()
        fun put(param: TypedParamType<*>, value: Int?) {
            if (value == null) {
                return
            }
            if (!force && paramMap?.contains(param) == true) {
                return
            }
            values[param.id] = value
        }
        put(params.attack_anim, entry.attackAnim?.asRSCM(RSCMType.SEQ))
        put(params.defend_anim, entry.defendAnim?.asRSCM(RSCMType.SEQ))
        put(params.death_anim, entry.deathAnim?.asRSCM(RSCMType.SEQ))
        put(params.npc_attack_type, entry.attackType?.asRSCM(RSCMType.CATEGORY))
        put(params.attack_sound, entry.attackSound)
        put(params.proj_travel, entry.projTravel?.asRSCM(RSCMType.SPOTANIM))
        put(params.proj_type, entry.projType?.asRSCM(RSCMType.PROJANIM))

        var changed = false
        if (values.isNotEmpty()) {
            paramMap = ParamMap(primitiveMap = paramMap?.primitiveMap.orEmpty() + values)
            changed = true
        }
        val range = entry.attackRange
        if (range != null && (force || attackRange < range)) {
            attackRange = range
            if (maxRange < range) {
                maxRange = range
            }
            changed = true
        }
        return changed
    }

    private companion object {
        private val logger = InlineLogger()
    }
}
