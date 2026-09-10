package org.rsmod.content.bosses.vorkath

import org.rsmod.api.combat.commons.DragonfireProtection
import org.rsmod.game.entity.Player

/**
 * Wiki-derived Vorkath distribution: roll first, subtract potion protection, then variant
 * reduction. The shared table remains authoritative for shield recognition and protection
 * combinations.
 */
internal object VorkathDragonfire {
    const val ANTIFIRE_REDUCTION = 10
    const val SUPER_ANTIFIRE_REDUCTION = 20

    fun snapshot(player: Player): ProtectionSnapshot {
        val reduction =
            potionReduction(
                antifire = player.vars["varbit.antifire_potion"] > 0,
                superAntifire = player.vars["varbit.super_antifire_potion"] > 0,
            )
        return fromReducedCaps(
            DragonfireProtection.resolveMaxHit(
                player,
                DragonfireProtection.DragonfireType.Vorkath,
                80,
            ),
            DragonfireProtection.resolveVorkathResistedMaxHit(player),
            reduction,
        )
    }

    /**
     * Pure equivalent of the player snapshot for exhaustive protection/distribution verification.
     */
    fun snapshot(
        shield: Boolean,
        protect: Boolean,
        antifire: Boolean,
        superAntifire: Boolean,
    ): ProtectionSnapshot =
        fromReducedCaps(
            DragonfireProtection.resolveVorkathMaxHit(shield, protect, antifire, superAntifire),
            DragonfireProtection.resolveVorkathResistedMaxHit(
                shield,
                protect,
                antifire,
                superAntifire,
            ),
            potionReduction(antifire, superAntifire),
        )

    private fun fromReducedCaps(
        maximum: Int,
        resistedMaximum: Int,
        reduction: Int,
    ): ProtectionSnapshot =
        // Vorkath's lowest base cap is 20, equal to the strongest potion subtraction. Restoring
        // the subtraction therefore reconstructs the exact base even when the reduced cap is zero.
        ProtectionSnapshot(maximum + reduction, resistedMaximum + reduction, reduction)

    private fun potionReduction(antifire: Boolean, superAntifire: Boolean): Int =
        when {
            superAntifire -> SUPER_ANTIFIRE_REDUCTION
            antifire -> ANTIFIRE_REDUCTION
            else -> 0
        }

    data class ProtectionSnapshot(
        val maximum: Int,
        val resistedMaximum: Int,
        val potionReduction: Int,
    ) {
        fun damage(rawDamage: Int, attack: VorkathStandardAttack): Int =
            VorkathRules.dragonfireMaximum((rawDamage - potionReduction).coerceAtLeast(0), attack)
    }
}
