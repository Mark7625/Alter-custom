package org.rsmod.content.other.defendanims

import dev.openrune.util.WeaponCategory
import dev.openrune.util.Wearpos
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DefendAnimsTest {
    @Test
    fun `items that already declare a block animation are left alone`() {
        assertNull(offhand("Rune kiteshield", hasDefendAnim = true))
        assertNull(weapon("Dragon scimitar", WeaponCategory.SlashSword, hasDefendAnim = true))
        // `seq.human_unarmedblock` on an off-hand is a "use the weapon's block" marker.
        assertNull(offhand("Bruma torch", hasDefendAnim = true))
    }

    @Test
    fun `shields, books, tomes and wards block with the shield animation`() {
        val names =
            listOf(
                "Rune kiteshield",
                "Bronze sq shield",
                "Dragonfire shield",
                "Elysian spirit shield",
                "Toktz-ket-xil",
                "Book of law",
                "Tome of fire",
                "Mage's book",
                "Dragonfire ward",
                "Twisted buckler",
                "Dragonfire deflector",
            )
        for (name in names) {
            assertEquals(DefendAnims.SHIELD_BLOCK, offhand(name), name)
        }
    }

    @Test
    fun `defenders block with the defender parry`() {
        val names =
            listOf("Bronze defender", "Dragon defender", "Avernic defender", "Ghommal's avernic defender")
        for (name in names) {
            assertEquals(DefendAnims.DEFENDER_BLOCK, offhand(name), name)
        }
    }

    @Test
    fun `other off-hand items keep the weapon's block`() {
        for (name in listOf("Bullseye lantern", "Unlit bug lantern", "Cabbage")) {
            assertNull(offhand(name), name)
        }
    }

    @Test
    fun `weapons without a block animation fall back to their category`() {
        assertEquals("seq.human_sword_def", weapon("Dragon scimitar", WeaponCategory.SlashSword))
        assertEquals("seq.human_sword_def", weapon("Blade of saeldor", WeaponCategory.SlashSword))
        assertEquals("seq.human_sword_def", weapon("Bone dagger", WeaponCategory.StabSword))
        assertEquals("seq.human_blunt_block", weapon("Verac's flail", WeaponCategory.Spiked))
        assertEquals("seq.human_axe_block", weapon("Rune axe", WeaponCategory.Axe))
        assertEquals("seq.human_spear_block", weapon("Dragon halberd", WeaponCategory.Polearm))
        assertEquals("seq.slayer_abyssal_whip_defend", weapon("Abyssal whip", WeaponCategory.Whip))
        assertEquals("seq.dh_sword_update_defend", weapon("Armadyl godsword", WeaponCategory.GodSword))
    }

    @Test
    fun `ranged weapons and unarmed keep the bare-handed block`() {
        assertNull(weapon("Magic shortbow", WeaponCategory.Bow))
        assertNull(weapon("Rune crossbow", WeaponCategory.Crossbow))
        assertNull(weapon("Rune knife", WeaponCategory.Thrown))
        assertNull(weapon("Lyre", WeaponCategory.Unarmed))
    }

    @Test
    fun `only hand slots are considered`() {
        val unarmed = WeaponCategory.Unarmed
        assertNull(DefendAnims.resolve("Rune full helm", Wearpos.Hat.slot, unarmed, false))
        assertNull(DefendAnims.resolve("Rune platebody", Wearpos.Torso.slot, unarmed, false))
        assertNull(DefendAnims.resolve("Coins", -1, unarmed, false))
    }

    @Test
    fun `every animation the table hands out is a seq reference`() {
        assertTrue(DefendAnims.allAnimations.isNotEmpty())
        for (seq in DefendAnims.allAnimations) {
            assertTrue(seq.startsWith("seq."), seq)
        }
    }

    private fun offhand(name: String, hasDefendAnim: Boolean = false): String? =
        DefendAnims.resolve(name, Wearpos.LeftHand.slot, WeaponCategory.Unarmed, hasDefendAnim)

    private fun weapon(
        name: String,
        category: WeaponCategory,
        hasDefendAnim: Boolean = false,
    ): String? = DefendAnims.resolve(name, Wearpos.RightHand.slot, category, hasDefendAnim)
}
