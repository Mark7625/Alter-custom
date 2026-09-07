package org.rsmod.content.other.emirsarena.duel

import dev.openrune.util.Wearpos

/**
 * The rule set of a legacy duel, packed exactly the way the client's `varp.dueloptions` (varp 286)
 * stores it so the same value can be written to both players and read back from the interface.
 *
 * Bit numbers come from clientscript 6169 (the option buttons of
 * `interface.pvp_arena_legacyduel_options`), and bits 14..27 are `varbit.duelwornoptions0..13`:
 * one per [Wearpos] slot, set when items may not be worn in that slot.
 */
@JvmInline
value class DuelRules(val packed: Int) {
    fun has(rule: DuelRule): Boolean = (packed shr rule.bit) and 1 == 1

    fun toggle(rule: DuelRule): DuelRules = DuelRules(packed xor (1 shl rule.bit))

    fun with(rule: DuelRule): DuelRules = DuelRules(packed or (1 shl rule.bit))

    fun isSlotDisabled(wearpos: Wearpos): Boolean = isSlotDisabled(wearpos.slot)

    fun isSlotDisabled(slot: Int): Boolean = (packed shr (WORN_BIT_OFFSET + slot)) and 1 == 1

    fun toggleSlot(slot: Int): DuelRules = DuelRules(packed xor (1 shl (WORN_BIT_OFFSET + slot)))

    fun withSlotDisabled(wearpos: Wearpos): DuelRules =
        DuelRules(packed or (1 shl (WORN_BIT_OFFSET + wearpos.slot)))

    val enabledRules: List<DuelRule>
        get() = DuelRule.entries.filter { has(it) }

    val disabledSlots: List<Wearpos>
        get() = Wearpos.entries.filter { !it.isClientOnly && isSlotDisabled(it) }

    /** True when the packed value only uses bits this interface understands. */
    val isValid: Boolean
        get() = packed and VALID_MASK.inv() == 0

    companion object {
        const val WORN_BIT_OFFSET: Int = 14

        val NONE: DuelRules = DuelRules(0)

        private val VALID_MASK: Int =
            DuelRule.entries.fold(0) { acc, rule -> acc or (1 shl rule.bit) } or
                Wearpos.entries.fold(0) { acc, pos -> acc or (1 shl (WORN_BIT_OFFSET + pos.slot)) }

        /** Bits the "Whip" preset button ticks: melee only, no helping items, weapon slot only. */
        val WHIP: DuelRules =
            listOf(
                    DuelRule.NoRanged,
                    DuelRule.NoMagic,
                    DuelRule.NoSpecialAttacks,
                    DuelRule.NoPrayer,
                    DuelRule.NoDrinks,
                    DuelRule.NoFood,
                    DuelRule.NoWeaponSwitch,
                )
                .fold(NONE) { acc, rule -> acc.with(rule) }
                .let { rules ->
                    Wearpos.entries
                        .filter { !it.isClientOnly && it != Wearpos.RightHand }
                        .fold(rules) { acc, pos -> acc.withSlotDisabled(pos) }
                }

        /** Bits the "Boxing" preset button ticks: bare fists, no helping items, nothing worn. */
        val BOXING: DuelRules =
            listOf(
                    DuelRule.NoRanged,
                    DuelRule.NoMagic,
                    DuelRule.NoSpecialAttacks,
                    DuelRule.NoPrayer,
                    DuelRule.NoDrinks,
                    DuelRule.NoFood,
                )
                .fold(NONE) { acc, rule -> acc.with(rule) }
                .let { rules ->
                    Wearpos.entries
                        .filter { !it.isClientOnly }
                        .fold(rules) { acc, pos -> acc.withSlotDisabled(pos) }
                }
    }
}

enum class DuelRule(val bit: Int, val label: String, val description: String) {
    NoForfeit(0, "No Forfeit", "Neither player is allowed to forfeit the duel."),
    NoMovement(
        1,
        "No Movement",
        "Players stand next to each other and aren't allowed to move or use hold spells.",
    ),
    NoWeaponSwitch(
        2,
        "No Weapon Switch",
        "Neither player is allowed to swap their weapons during the duel.",
    ),
    ShowInventories(
        3,
        "Show Inventories",
        "Show your opponent your worn and back pack inventory, and view theirs.",
    ),
    NoRanged(4, "No Ranged", "Neither player is allowed to use ranged attacks."),
    NoMelee(5, "No Melee", "Neither player is allowed to use melee attacks."),
    NoMagic(6, "No Magic", "Neither player is allowed to use magic attacks."),
    NoDrinks(7, "No Drinks", "Neither player is allowed to use drinks."),
    NoFood(8, "No Food", "Neither player is allowed to use food."),
    NoPrayer(9, "No Prayer", "Neither player is allowed to use prayer."),
    FunWeapons(
        12,
        "Fun Weapons",
        "Both players must use a 'fun weapon', such as flowers or a rubber chicken.",
    ),
    NoSpecialAttacks(13, "No Special Attacks", "Neither player is allowed to use special attacks.");

    companion object {
        /** The option buttons of the duel options interface, keyed by component suffix. */
        val BY_COMPONENT: Map<String, DuelRule> =
            mapOf(
                "option_ranged" to NoRanged,
                "option_melee" to NoMelee,
                "option_magic" to NoMagic,
                "option_specialattacks" to NoSpecialAttacks,
                "option_funweapons" to FunWeapons,
                "option_forfeit" to NoForfeit,
                "option_prayer" to NoPrayer,
                "option_potions" to NoDrinks,
                "option_food" to NoFood,
                "option_movement" to NoMovement,
                "option_noswitch" to NoWeaponSwitch,
                "option_showinv" to ShowInventories,
            )
    }
}
