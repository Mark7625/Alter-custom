package org.rsmod.content.other.defendanims

import dev.openrune.util.WeaponCategory
import dev.openrune.util.Wearpos

/**
 * The block animation an equipped item should give the player when they are hit, for every item
 * the cache does not say so itself.
 *
 * The combat api resolves a defender's animation from the `param.defend_anim` of what they are
 * holding: the left hand wins unless it is the bare-handed block, then the right hand, then
 * `seq.human_unarmedblock`. The server item data leaves that param off two groups of items, and
 * both fall through to the bare-handed block:
 *
 * - **Every shield, defender, book, tome and ward.** No off-hand item carries a block animation,
 *   so a player with a kiteshield blocks with their weapon, and a player with a defender or a book
 *   blocks with their fists.
 * - **A handful of weapons.** All scimitars, the blade of Saeldor, the bone dagger, Verac's flail
 *   and a few others have attack animations but no block animation.
 *
 * This table is pure data so it can be tested without a cache. [resolve] answers with the
 * animation to assign, or `null` when the item should be left alone. Items that already declare a
 * `param.defend_anim` are never touched - `seq.human_unarmedblock` on an off-hand item is a
 * deliberate "use the weapon's block" marker (the bruma torch, for one) and must survive.
 */
object DefendAnims {
    const val UNARMED_BLOCK = "seq.human_unarmedblock"
    const val SHIELD_BLOCK = "seq.human_shield_defence"
    const val DEFENDER_BLOCK = "seq.warguild_parry_defend"

    /**
     * Off-hand items whose lowercase display name contains one of these play [SHIELD_BLOCK] (or
     * [DEFENDER_BLOCK] for defenders). Anything else in the shield slot - lanterns, torches,
     * satchels - keeps the weapon's block animation, as it does on Old School RuneScape.
     */
    private val shieldKeywords =
        listOf("shield", "defender", "ward", "book", "tome", "buckler", "deflector", "ket-xil")

    /**
     * Block animation per weapon category for weapons that declare none. Categories that block
     * bare-handed on Old School RuneScape (bows, crossbows, thrown, guns) are left out on purpose:
     * the resolver's fallback already gives them [UNARMED_BLOCK].
     */
    private val weaponCategoryBlocks: Map<WeaponCategory, String> =
        mapOf(
            WeaponCategory.Axe to "seq.human_axe_block",
            WeaponCategory.Pickaxe to "seq.human_axe_block",
            WeaponCategory.Claw to "seq.human_axe_block",
            WeaponCategory.Blunt to "seq.human_blunt_block",
            WeaponCategory.Spiked to "seq.human_blunt_block",
            WeaponCategory.Bludgeon to "seq.slayer_granite_maul_defend",
            WeaponCategory.SlashSword to "seq.human_sword_def",
            WeaponCategory.StabSword to "seq.human_sword_def",
            WeaponCategory.Salamander to "seq.human_sword_def",
            WeaponCategory.TwoHandedSword to "seq.human_dhsword_block",
            WeaponCategory.GodSword to "seq.dh_sword_update_defend",
            WeaponCategory.Polearm to "seq.human_spear_block",
            WeaponCategory.Spear to "seq.human_spear_block",
            WeaponCategory.Polestaff to "seq.human_staff_block",
            WeaponCategory.Staff to "seq.human_staff_block",
            WeaponCategory.BladedStaff to "seq.human_stafforb_block",
            WeaponCategory.PoweredStaff to "seq.human_stafforb_block",
            WeaponCategory.Scythe to "seq.human_scythe_block",
            WeaponCategory.Whip to "seq.slayer_abyssal_whip_defend",
            WeaponCategory.Banner to "seq.human_banner_block",
            WeaponCategory.Bulwark to "seq.human_dinhs_bulwark_block",
            WeaponCategory.Chinchompas to "seq.human_chinchompa_defend",
        )

    /**
     * Exact-name overrides, checked before the keyword and category rules. Keys are lowercase
     * display names. Empty today; this is where a single item that the rules get wrong goes.
     */
    private val itemOverrides: Map<String, String> = emptyMap()

    /** Every animation this table can hand out, for boot-time validation. */
    val allAnimations: Set<String> =
        buildSet {
            add(SHIELD_BLOCK)
            add(DEFENDER_BLOCK)
            addAll(weaponCategoryBlocks.values)
            addAll(itemOverrides.values)
        }

    /**
     * The `seq.*` name to assign as `param.defend_anim`, or `null` to leave the item as it is.
     *
     * @param name the item's display name.
     * @param wearpos the item's primary wear position slot ([Wearpos.slot]), `-1` if not worn.
     * @param category the item's weapon category.
     * @param hasDefendAnim whether the item already declares `param.defend_anim`.
     */
    fun resolve(
        name: String,
        wearpos: Int,
        category: WeaponCategory,
        hasDefendAnim: Boolean,
    ): String? {
        if (hasDefendAnim) {
            return null
        }
        val lowercase = name.lowercase()
        itemOverrides[lowercase]?.let {
            return it
        }
        return when (wearpos) {
            Wearpos.LeftHand.slot -> resolveOffhand(lowercase)
            Wearpos.RightHand.slot -> weaponCategoryBlocks[category]
            else -> null
        }
    }

    private fun resolveOffhand(lowercaseName: String): String? {
        if (shieldKeywords.none { it in lowercaseName }) {
            return null
        }
        return if ("defender" in lowercaseName) DEFENDER_BLOCK else SHIELD_BLOCK
    }
}
