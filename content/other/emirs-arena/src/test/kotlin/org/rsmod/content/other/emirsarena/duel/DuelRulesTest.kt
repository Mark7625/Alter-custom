package org.rsmod.content.other.emirsarena.duel

import dev.openrune.util.Wearpos
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DuelRulesTest {
    @Test
    fun `rule bits match the client's dueloptions layout`() {
        assertEquals(0, DuelRule.NoForfeit.bit)
        assertEquals(1, DuelRule.NoMovement.bit)
        assertEquals(2, DuelRule.NoWeaponSwitch.bit)
        assertEquals(3, DuelRule.ShowInventories.bit)
        assertEquals(4, DuelRule.NoRanged.bit)
        assertEquals(5, DuelRule.NoMelee.bit)
        assertEquals(6, DuelRule.NoMagic.bit)
        assertEquals(7, DuelRule.NoDrinks.bit)
        assertEquals(8, DuelRule.NoFood.bit)
        assertEquals(9, DuelRule.NoPrayer.bit)
        assertEquals(12, DuelRule.FunWeapons.bit)
        assertEquals(13, DuelRule.NoSpecialAttacks.bit)
    }

    @Test
    fun `toggling a rule flips only its bit`() {
        val rules = DuelRules.NONE.toggle(DuelRule.NoMagic)
        assertTrue(rules.has(DuelRule.NoMagic))
        assertEquals(1 shl 6, rules.packed)
        assertEquals(DuelRules.NONE, rules.toggle(DuelRule.NoMagic))
    }

    @Test
    fun `worn slot toggles live in bits 14 to 27`() {
        val rules = DuelRules.NONE.toggleSlot(Wearpos.LeftHand.slot)
        assertTrue(rules.isSlotDisabled(Wearpos.LeftHand))
        assertFalse(rules.isSlotDisabled(Wearpos.RightHand))
        assertEquals(1 shl (14 + 5), rules.packed)
        assertEquals(listOf(Wearpos.LeftHand), rules.disabledSlots)
    }

    @Test
    fun `whip preset keeps only the weapon slot and melee`() {
        val whip = DuelRules.WHIP
        assertFalse(whip.isSlotDisabled(Wearpos.RightHand))
        assertTrue(whip.isSlotDisabled(Wearpos.LeftHand))
        assertTrue(whip.isSlotDisabled(Wearpos.Torso))
        assertFalse(whip.isSlotDisabled(Wearpos.Arms), "client-only slots are never disabled")
        assertTrue(whip.has(DuelRule.NoRanged))
        assertTrue(whip.has(DuelRule.NoMagic))
        assertTrue(whip.has(DuelRule.NoWeaponSwitch))
        assertFalse(whip.has(DuelRule.NoMelee))
        assertFalse(whip.has(DuelRule.NoForfeit))
    }

    @Test
    fun `boxing preset disables every worn slot`() {
        val boxing = DuelRules.BOXING
        for (pos in Wearpos.entries.filter { !it.isClientOnly }) {
            assertTrue(boxing.isSlotDisabled(pos), pos.name)
        }
        assertFalse(boxing.has(DuelRule.NoWeaponSwitch))
        assertFalse(boxing.has(DuelRule.NoMelee))
    }

    @Test
    fun `packed values outside the known bits are rejected`() {
        assertTrue(DuelRules.WHIP.isValid)
        assertTrue(DuelRules.BOXING.isValid)
        assertFalse(DuelRules(1 shl 10).isValid)
        assertFalse(DuelRules(1 shl 28).isValid)
    }

    @Test
    fun `every option button maps to a distinct rule`() {
        assertEquals(DuelRule.entries.size, DuelRule.BY_COMPONENT.size)
        assertEquals(DuelRule.entries.toSet(), DuelRule.BY_COMPONENT.values.toSet())
    }
}
