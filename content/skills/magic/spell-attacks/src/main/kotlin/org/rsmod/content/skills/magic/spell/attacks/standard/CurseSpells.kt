package org.rsmod.content.skills.magic.spell.attacks.standard

import org.rsmod.api.spells.attack.SpellAttackManager
import org.rsmod.api.spells.attack.SpellAttackMap
import org.rsmod.api.spells.attack.SpellAttackRepository
import org.rsmod.content.skills.magic.spell.attacks.EffectSpellAttack
import org.rsmod.content.skills.magic.spell.attacks.SpellEffects

/**
 * The standard spellbook's curses and binds. Curses lower one of the target's combat stats by a
 * share of its base level and do not stack; binds hold the target in place for 5, 10 or 15
 * seconds. None of them deal damage: a landed cast shows its impact graphic and the effect, a
 * failed one splashes.
 */
class CurseSpells : SpellAttackMap {
    override fun SpellAttackRepository.register(manager: SpellAttackManager) {
        register(
            spell = "obj.03_confuse",
            attack =
                curse(
                    manager,
                    anim = "seq.human_castconfuse",
                    staffAnim = "seq.human_castconfuse_staff",
                    name = "confuse",
                    castSound = "synth.confuse_cast_and_fire",
                    hitSound = "synth.confuse_hit",
                    stat = SpellEffects.ATTACK,
                    percent = 5,
                ),
        )
        register(
            spell = "obj.11_weaken",
            attack =
                curse(
                    manager,
                    anim = "seq.human_castweaken",
                    staffAnim = "seq.human_castweaken_staff",
                    name = "weaken",
                    castSound = "synth.weaken_all",
                    hitSound = null,
                    stat = SpellEffects.STRENGTH,
                    percent = 5,
                ),
        )
        register(
            spell = "obj.19_curse",
            attack =
                curse(
                    manager,
                    anim = "seq.human_castcurse",
                    staffAnim = "seq.human_castcurse_staff",
                    name = "curse",
                    castSound = "synth.curse_cast_and_fire",
                    hitSound = "synth.curse_hit",
                    stat = SpellEffects.DEFENCE,
                    percent = 5,
                ),
        )
        register(
            spell = "obj.66_vulnerability",
            attack =
                curse(
                    manager,
                    anim = "seq.human_castcurse",
                    staffAnim = "seq.human_castcurse_staff",
                    name = "vulnerability",
                    castSound = "synth.vulnerability_all",
                    hitSound = null,
                    stat = SpellEffects.DEFENCE,
                    percent = 10,
                    projanim = "projanim.vulnerability",
                ),
        )
        register(
            spell = "obj.73_enfeeble",
            attack =
                curse(
                    manager,
                    anim = "seq.human_castenfeeble",
                    staffAnim = "seq.human_castenfeeble_staff",
                    name = "enfeeble",
                    castSound = "synth.enfeeble_cast_and_fire",
                    hitSound = "synth.enfeeble_hit",
                    stat = SpellEffects.STRENGTH,
                    percent = 10,
                    projanim = "projanim.enfeeble",
                ),
        )
        register(
            spell = "obj.80_stun",
            attack =
                curse(
                    manager,
                    anim = "seq.human_caststun",
                    staffAnim = "seq.human_caststun_staff",
                    name = "stun",
                    castSound = "synth.stun_all",
                    hitSound = null,
                    stat = SpellEffects.ATTACK,
                    percent = 10,
                    projanim = "projanim.stun",
                ),
        )

        register(
            spell = "obj.20_bind",
            attack =
                bind(
                    manager,
                    impact = "spotanim.bind_impact",
                    castSound = "synth.bind_cast",
                    hitSound = "synth.bind_impact",
                    ticks = BIND_TICKS,
                ),
        )
        register(
            spell = "obj.50_snare",
            attack =
                bind(
                    manager,
                    impact = "spotanim.snare_impact",
                    castSound = "synth.snare_all",
                    hitSound = null,
                    ticks = SNARE_TICKS,
                ),
        )
        register(
            spell = "obj.79_entangle",
            attack =
                bind(
                    manager,
                    impact = "spotanim.entangle_impact",
                    castSound = "synth.entangle_cast_and_fire",
                    hitSound = "synth.entangle_hit",
                    ticks = ENTANGLE_TICKS,
                ),
        )
    }

    private fun curse(
        manager: SpellAttackManager,
        anim: String,
        staffAnim: String,
        name: String,
        castSound: String?,
        hitSound: String?,
        stat: String,
        percent: Int,
        projanim: String = "projanim.confuse",
    ): EffectSpellAttack =
        EffectSpellAttack(
            manager = manager,
            staffAnim = staffAnim,
            unarmedAnim = anim,
            launch = "spotanim.${name}_casting",
            travel = "spotanim.${name}_travel",
            projanim = projanim,
            impact = "spotanim.${name}_impact",
            castSound = castSound,
            hitSound = hitSound,
            onLand = { target, _ -> SpellEffects.drainPercent(target, stat, percent) },
        )

    private fun bind(
        manager: SpellAttackManager,
        impact: String,
        castSound: String?,
        hitSound: String?,
        ticks: Int,
    ): EffectSpellAttack =
        EffectSpellAttack(
            manager = manager,
            staffAnim = "seq.human_castentangle_staff",
            unarmedAnim = "seq.human_castentangle",
            launch = "spotanim.entangle_casting",
            travel = "spotanim.entangle_travel",
            projanim = "projanim.bind",
            impact = impact,
            impactHeight = 0,
            castSound = castSound,
            hitSound = hitSound,
            onLand = { target, _ -> SpellEffects.freeze(target, ticks) },
        )

    private companion object {
        private const val BIND_TICKS = 8
        private const val SNARE_TICKS = 16
        private const val ENTANGLE_TICKS = 24
    }
}
