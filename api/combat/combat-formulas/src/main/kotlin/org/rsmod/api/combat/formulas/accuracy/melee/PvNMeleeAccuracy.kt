package org.rsmod.api.combat.formulas.accuracy.melee

import dev.openrune.types.NpcServerType
import jakarta.inject.Inject
import java.util.EnumSet
import org.rsmod.api.combat.accuracy.npc.NpcMeleeAccuracy
import org.rsmod.api.combat.accuracy.player.PlayerMeleeAccuracy
import org.rsmod.api.combat.commons.styles.MeleeAttackStyle
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.combat.formulas.accuracy.AccuracyOperations
import org.rsmod.api.combat.formulas.attributes.CombatMeleeAttributes
import org.rsmod.api.combat.formulas.attributes.CombatNpcAttributes
import org.rsmod.api.combat.formulas.attributes.collector.CombatMeleeAttributeCollector
import org.rsmod.api.combat.formulas.attributes.collector.CombatNpcAttributeCollector
import org.rsmod.api.combat.formulas.isSlayerTask
import org.rsmod.api.config.refs.BaseParams
import org.rsmod.api.player.bonus.WornBonuses
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

public class PvNMeleeAccuracy
@Inject
constructor(
    private val bonuses: WornBonuses,
    private val npcAttributes: CombatNpcAttributeCollector,
    private val meleeAttributes: CombatMeleeAttributeCollector,
) {
    public fun getHitChance(
        player: Player,
        target: Npc,
        attackType: MeleeAttackType?,
        attackStyle: MeleeAttackStyle?,
        blockType: MeleeAttackType?,
        specialMultiplier: Double,
    ): Int =
        computeHitChance(
            source = player,
            target = target.visType,
            npc = target,
            targetDefence = target.defenceLvl,
            targetCurrHp = target.hitpoints,
            targetMaxHp = target.baseHitpointsLvl,
            attackType = attackType,
            attackStyle = attackStyle,
            blockType = blockType,
            specialMultiplier = specialMultiplier,
        )

    public fun computeHitChance(
        source: Player,
        target: NpcServerType,
        targetDefence: Int,
        targetCurrHp: Int,
        targetMaxHp: Int,
        attackType: MeleeAttackType?,
        attackStyle: MeleeAttackStyle?,
        blockType: MeleeAttackType?,
        specialMultiplier: Double,
        npc: Npc? = null,
    ): Int {
        val meleeAttributes = meleeAttributes.collect(source, attackType)

        val slayerTask = target.isSlayerTask(source)
        val npcAttributes = npcAttributes.collect(target, npc, targetCurrHp, targetMaxHp, slayerTask)

        val baseAttackRoll =
            computeAttackRoll(source, attackType, attackStyle, meleeAttributes, npcAttributes)
        val attackRoll = (baseAttackRoll * specialMultiplier).toInt()

        val amascutInvocationLvl = source.vars["varbit.toa_client_raid_level"]
        val defenceRoll =
            computeDefenceRoll(
                target = target,
                targetDefence = targetDefence,
                amascutInvocationLvl = amascutInvocationLvl,
                blockType = blockType,
                npcAttributes = npcAttributes,
            )

        val hitChance = AccuracyOperations.calculateHitChance(attackRoll, defenceRoll, npcAttributes)
        return MeleeAccuracyOperations.modifyHitChance(
            hitChance = hitChance,
            attackRoll = attackRoll,
            defenceRoll = defenceRoll,
            meleeAttributes = meleeAttributes,
            npcAttributes = npcAttributes,
        )
    }

    public fun computeAttackRoll(
        source: Player,
        attackType: MeleeAttackType?,
        attackStyle: MeleeAttackStyle?,
        meleeAttributes: EnumSet<CombatMeleeAttributes>,
        npcAttributes: EnumSet<CombatNpcAttributes>,
    ): Int {
        val effectiveAttack = MeleeAccuracyOperations.calculateEffectiveAttack(source, attackStyle)
        val attackBonus = source.getAttackBonus(attackType)
        val attackRoll = PlayerMeleeAccuracy.calculateBaseAttackRoll(effectiveAttack, attackBonus)
        return MeleeAccuracyOperations.modifyAttackRoll(attackRoll, meleeAttributes, npcAttributes)
    }

    private fun Player.getAttackBonus(attackType: MeleeAttackType?): Int =
        when (attackType) {
            MeleeAttackType.Stab -> bonuses.offensiveStabBonus(this)
            MeleeAttackType.Slash -> bonuses.offensiveSlashBonus(this)
            MeleeAttackType.Crush -> bonuses.offensiveCrushBonus(this)
            null -> 0
        }

    public fun computeDefenceRoll(
        target: NpcServerType,
        targetDefence: Int,
        amascutInvocationLvl: Int,
        blockType: MeleeAttackType?,
        npcAttributes: EnumSet<CombatNpcAttributes>,
    ): Int {
        val effectiveDefence = NpcMeleeAccuracy.calculateEffectiveDefence(targetDefence)
        val defenceBonus = target.getDefenceBonus(blockType)
        val defenceRoll = NpcMeleeAccuracy.calculateBaseDefenceRoll(effectiveDefence, defenceBonus)
        return AccuracyOperations.modifyDefenceRoll(
            defenceRoll = defenceRoll,
            amascutInvocationLvl = amascutInvocationLvl,
            npcAttributes = npcAttributes,
        )
    }

    private fun NpcServerType.getDefenceBonus(attackType: MeleeAttackType?): Int =
        when (attackType) {
            MeleeAttackType.Stab -> param(BaseParams.defence_stab)
            MeleeAttackType.Slash -> param(BaseParams.defence_slash)
            MeleeAttackType.Crush -> param(BaseParams.defence_crush)
            null -> 0
        }
}
