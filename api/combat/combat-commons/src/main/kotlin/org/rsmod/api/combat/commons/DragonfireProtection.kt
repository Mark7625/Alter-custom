package org.rsmod.api.combat.commons

import org.rsmod.game.entity.Player

public object DragonfireProtection {

    public enum class DragonfireType { Chromatic, Metal, WyvernIce, Vorkath }

    private val antifireShields = setOf(
        "obj.antidragonbreathshield",
        "obj.dragonfire_shield",
        "obj.dragonfire_shield_uncharged",
        "obj.dragonfire_ward",
        "obj.dragonfire_ward_uncharged",
        "obj.wyvern_shield",
        "obj.wyvern_shield_uncharged",
    )

    private val wyvernIceShields = setOf(
        "obj.elemental_shield",
        "obj.elemental_mind_shield",
        "obj.dragonfire_shield",
        "obj.dragonfire_shield_uncharged",
        "obj.dragonfire_ward",
        "obj.dragonfire_ward_uncharged",
        "obj.wyvern_shield",
        "obj.wyvern_shield_uncharged",
    )

    public fun resolveMaxHit(player: Player, type: DragonfireType, baseMax: Int): Int {
        if (type == DragonfireType.Vorkath) {
            return resolveVorkathMaxHit(player)
        }
        if (type == DragonfireType.WyvernIce) {
            return when {
                wyvernIceShields.any { it in player.worn } -> 10
                isProtectingFromMagic(player) -> 20
                else -> baseMax
            }
        }

        if (hasSuperAntifire(player)) return 0

        val shield = hasAntifireShield(player)
        val protect = type == DragonfireType.Chromatic && isProtectingFromMagic(player)
        val antifire = hasAntifire(player)

        if (antifire && (shield || protect)) return 0

        var cap = when {
            shield -> 5
            protect -> 10
            else -> baseMax
        }
        if (antifire) cap = (cap - 15).coerceAtLeast(0)
        return cap
    }

    private fun hasAntifireShield(player: Player): Boolean =
        antifireShields.any { it in player.worn }

    private fun resolveVorkathMaxHit(player: Player): Int =
        resolveVorkathMaxHit(
            shield = hasAntifireShield(player),
            protect = isProtectingFromMagic(player),
            antifire = hasAntifire(player),
            superAntifire = hasSuperAntifire(player),
        )

    public fun resolveVorkathResistedMaxHit(player: Player): Int =
        resolveVorkathResistedMaxHit(
            shield = hasAntifireShield(player),
            protect = isProtectingFromMagic(player),
            antifire = hasAntifire(player),
            superAntifire = hasSuperAntifire(player),
        )

    /** Pure post-quest Vorkath dragonfire protection table. */
    public fun resolveVorkathMaxHit(
        shield: Boolean,
        protect: Boolean,
        antifire: Boolean,
        superAntifire: Boolean,
    ): Int =
        when {
            superAntifire && shield -> 0
            superAntifire && protect -> 10
            superAntifire -> 60
            antifire && shield -> 10
            antifire && protect -> 20
            antifire -> 70
            shield -> 20
            protect -> 30
            else -> 80
        }

    /** Vorkath still deals reduced dragonfire damage when its accuracy roll fails. */
    public fun resolveVorkathResistedMaxHit(
        shield: Boolean,
        protect: Boolean,
        antifire: Boolean,
        superAntifire: Boolean,
    ): Int =
        when {
            superAntifire && shield -> 0
            superAntifire && protect -> 10
            superAntifire -> 30
            antifire && shield -> 10
            antifire && protect -> 20
            antifire -> 40
            shield -> 20
            protect -> 30
            else -> 50
        }

    private fun hasSuperAntifire(player: Player): Boolean =
        player.vars["varbit.super_antifire_potion"] > 0

    private fun hasAntifire(player: Player): Boolean =
        player.vars["varbit.antifire_potion"] > 0

    private fun isProtectingFromMagic(player: Player): Boolean =
        player.vars["varbit.prayer_protectfrommagic"] > 0
}
