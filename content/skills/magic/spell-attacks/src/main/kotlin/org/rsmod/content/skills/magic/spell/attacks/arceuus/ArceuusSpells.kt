package org.rsmod.content.skills.magic.spell.attacks.arceuus

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.config.refs.params
import org.rsmod.api.enums.SpellbookEnums
import org.rsmod.api.spells.attack.SpellAttackManager
import org.rsmod.api.spells.attack.SpellAttackMap
import org.rsmod.api.spells.attack.SpellAttackRepository
import org.rsmod.content.skills.magic.spell.attacks.EffectSpellAttack
import org.rsmod.content.skills.magic.spell.attacks.SpellEffects
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity

/**
 * The Arceuus spellbook's attack spells: the three grasps, which damage and hold the target, and
 * the three demonbanes, which only affect demons.
 *
 * The Arceuus spell objs carry stale gameval names in this cache (`obj.dmm_ref_hat` is Ghostly
 * Grasp), so the spells are found by the `spell_name` param on the entries of the Arceuus
 * spellbook enum rather than by name.
 */
class ArceuusSpells : SpellAttackMap {
    override fun SpellAttackRepository.register(manager: SpellAttackManager) {
        val byName =
            SpellbookEnums.arceuus_spellbook
                .filterValuesNotNull()
                .values
                .associateBy { it.paramOrNull(params.spell_name).orEmpty() }

        fun registerNamed(spellName: String, attack: EffectSpellAttack) {
            val obj = byName[spellName]
            if (obj == null) {
                logger.warn { "Arceuus spell not found in spellbook enum: $spellName" }
                return
            }
            register(RSCM.getReverseMapping(RSCMType.OBJ, obj.id), attack)
        }

        registerNamed("Ghostly Grasp", grasp(manager, "ghostly", GHOSTLY_GRASP_MAX_HIT, GHOSTLY_GRASP_TICKS))
        registerNamed("Skeletal Grasp", grasp(manager, "skeletal", SKELETAL_GRASP_MAX_HIT, SKELETAL_GRASP_TICKS))
        registerNamed("Undead Grasp", grasp(manager, "undead", UNDEAD_GRASP_MAX_HIT, UNDEAD_GRASP_TICKS))

        registerNamed("Inferior Demonbane", demonbane(manager, "inferior", INFERIOR_DEMONBANE_MAX_HIT))
        registerNamed("Superior Demonbane", demonbane(manager, "superior", SUPERIOR_DEMONBANE_MAX_HIT))
        registerNamed("Dark Demonbane", demonbane(manager, "dark", DARK_DEMONBANE_MAX_HIT))
    }

    private fun grasp(
        manager: SpellAttackManager,
        name: String,
        maxHit: Int,
        freezeTicks: Int,
    ): EffectSpellAttack =
        EffectSpellAttack(
            manager = manager,
            staffAnim = "seq.human_spellcast_grasp",
            launch = "spotanim.${name}_grasp_cast_spotanim",
            travel = null,
            impact = "spotanim.${name}_grasp_hit_spotanim",
            impactHeight = 0,
            castSound = null,
            hitSound = null,
            baseMaxHit = { maxHit },
            onLand = { target, damage -> if (damage > 0) SpellEffects.freeze(target, freezeTicks) },
        )

    private fun demonbane(manager: SpellAttackManager, name: String, maxHit: Int): EffectSpellAttack =
        EffectSpellAttack(
            manager = manager,
            staffAnim = "seq.human_spellcast_demonbane",
            launch = "spotanim.${name}_demonbane_cast_spotanim",
            travel = null,
            impact = "spotanim.${name}_demonbane_hit_spotanim",
            impactHeight = 0,
            castSound = null,
            hitSound = null,
            baseMaxHit = { maxHit },
            targetCheck = ::demonsOnly,
        )

    private fun demonsOnly(target: PathingEntity): String? =
        if (target is Npc && SpellEffects.isDemon(target)) null else "This spell only affects demons."

    private companion object {
        private val logger = InlineLogger()

        private const val GHOSTLY_GRASP_MAX_HIT = 12
        private const val SKELETAL_GRASP_MAX_HIT = 17
        private const val UNDEAD_GRASP_MAX_HIT = 24
        private const val GHOSTLY_GRASP_TICKS = 4
        private const val SKELETAL_GRASP_TICKS = 8
        private const val UNDEAD_GRASP_TICKS = 12
        private const val INFERIOR_DEMONBANE_MAX_HIT = 16
        private const val SUPERIOR_DEMONBANE_MAX_HIT = 23
        private const val DARK_DEMONBANE_MAX_HIT = 30
    }
}
