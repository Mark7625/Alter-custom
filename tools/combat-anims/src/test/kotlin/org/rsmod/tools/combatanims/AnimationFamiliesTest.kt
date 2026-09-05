package org.rsmod.tools.combatanims

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AnimationFamiliesTest {
    private val sequences =
        setOf(
            "giant_update_basic_ready",
            "giant_update_basic_attack",
            "giant_update_basic_defend",
            "giant_update_basic_death",
            "cow_just_ready_update",
            "cow_attack",
            "cow_block",
            "cow_death",
            "zombie_update_ready_weapon",
            "zombie_update_attack_weapon",
            "zombie_update_defend_weapon",
            "zombie_update_death_weapon",
            "zombie_update_ready_normal",
            "zombie_update_attack_normal",
            "slice_surface_goblin_squat_ready",
            "slice_surface_goblin_squat_unarmed_attack",
            "slice_surface_goblin_squat_ready_spear",
            "slice_surface_goblin_squat_attack_spear",
            "scorpion_update_ready",
            "scorpion_update_attack_tail",
            "scorpion_update_defend",
            "scorpion_update_death",
            "skeleton_update_ready",
            "skeleton_update_attack_weapon",
            "skeleton_update_attack_weapon_transparent",
            "skeleton_update_attack_sword",
            "skeleton_update_defend",
            "skeleton_update_death",
            "godwars_armadyl_ready",
            "godwars_armadyl_cannon_attack",
            "godwars_armadyl_spear_attack",
            "godwars_armadyl_sword_attack",
            "godwars_armadyl_defend",
            "godwars_armadyl_death",
            "troll_ready_sword",
            "troll_attack",
            "troll_block",
            "troll_death",
            "thzaar_parry",
            "thzaar_death",
            "thzaar_ready",
            "thzaar_unarmed_attack",
            "thzaar_armed_attack",
            "thzaar_magic_attack",
            "maiden_idle",
            "maiden_attack",
        )

    @Test
    fun `plain family resolves every member`() {
        val family = AnimationFamilies.resolve("giant_update_basic_ready", sequences)
        assertEquals("giant_update_basic_attack", family.attack)
        assertEquals("giant_update_basic_defend", family.defend)
        assertEquals("giant_update_basic_death", family.death)
    }

    @Test
    fun `long ready markers are stripped whole`() {
        val family = AnimationFamilies.resolve("cow_just_ready_update", sequences)
        assertEquals("cow_attack", family.attack)
        assertEquals("cow_block", family.defend)
        assertEquals("cow_death", family.death)
    }

    @Test
    fun `a ready variant carries over to the other members`() {
        val family = AnimationFamilies.resolve("zombie_update_ready_weapon", sequences)
        assertEquals("zombie_update_attack_weapon", family.attack)
        assertEquals("zombie_update_defend_weapon", family.defend)
        assertEquals("zombie_update_death_weapon", family.death)
    }

    @Test
    fun `the variant can sit before the member name`() {
        val unarmed = AnimationFamilies.resolve("slice_surface_goblin_squat_ready", sequences)
        assertEquals("slice_surface_goblin_squat_unarmed_attack", unarmed.attack)
        val spear = AnimationFamilies.resolve("slice_surface_goblin_squat_ready_spear", sequences)
        assertEquals("slice_surface_goblin_squat_attack_spear", spear.attack)
    }

    @Test
    fun `a family with a single attack uses it whatever it is called`() {
        assertEquals(
            "scorpion_update_attack_tail",
            AnimationFamilies.resolve("scorpion_update_ready", sequences).attack,
        )
        assertEquals("maiden_attack", AnimationFamilies.resolve("maiden_idle", sequences).attack)
    }

    @Test
    fun `several attacks prefer the generic weapon one and otherwise stay unresolved`() {
        assertEquals(
            "skeleton_update_attack_weapon",
            AnimationFamilies.resolve("skeleton_update_ready", sequences).attack,
        )
        val armadyl = AnimationFamilies.resolve("godwars_armadyl_ready", sequences)
        assertNull(armadyl.attack)
        assertEquals("godwars_armadyl_defend", armadyl.defend)
    }

    @Test
    fun `a variant with no member of its own falls back to the base family`() {
        val family = AnimationFamilies.resolve("troll_ready_sword", sequences)
        assertEquals("troll_attack", family.attack)
        assertEquals("troll_block", family.defend)
    }

    @Test
    fun `unarmed attack beats an armed sibling`() {
        assertEquals("thzaar_unarmed_attack", AnimationFamilies.resolve("thzaar_ready", sequences).attack)
        assertEquals("thzaar_parry", AnimationFamilies.resolve("thzaar_ready", sequences).defend)
    }

    @Test
    fun `an unknown family is empty`() {
        val family = AnimationFamilies.resolve("human_ready", sequences)
        assertTrue(family.isEmpty)
        assertTrue(AnimationFamilies.resolve("no_marker_here", sequences).isEmpty)
    }
}
