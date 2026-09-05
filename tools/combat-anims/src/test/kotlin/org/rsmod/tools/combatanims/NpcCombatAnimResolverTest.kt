package org.rsmod.tools.combatanims

import dev.openrune.util.WeaponCategory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class NpcCombatAnimResolverTest {
    private val sequences =
        setOf(
            "human_ready",
            "human_caststrike",
            "human_caststrike_staff",
            "giant_update_basic_ready",
            "giant_update_basic_attack",
            "giant_update_basic_defend",
            "giant_update_basic_death",
        )

    private val sword =
        WeaponFacts(
            rscm = "obj.iron_sword",
            category = WeaponCategory.StabSword,
            attackAnim = "human_sword_stab",
            attackSound = 2549,
            defendAnim = "human_sword_def",
            projTravel = null,
            projType = null,
            attackRange = null,
        )

    private val battleaxe =
        sword.copy(
            rscm = "obj.iron_battleaxe",
            category = WeaponCategory.Axe,
            attackAnim = "human_axe_hack",
            attackSound = 2498,
            defendAnim = "human_axe_block",
        )

    private val mace =
        sword.copy(
            rscm = "obj.iron_mace",
            category = WeaponCategory.Spiked,
            attackAnim = "human_blunt_pound",
            attackSound = 2508,
            defendAnim = "human_blunt_block",
        )

    private val scimitar =
        sword.copy(
            rscm = "obj.iron_scimitar",
            category = WeaponCategory.SlashSword,
            attackAnim = "human_sword_slash",
            attackSound = 2500,
            defendAnim = null,
        )

    private val shortbow =
        sword.copy(
            rscm = "obj.shortbow",
            category = WeaponCategory.Bow,
            attackAnim = "human_bow",
            attackSound = 2693,
            defendAnim = "human_unarmedblock",
            projType = "arrow",
            attackRange = 7,
        )

    private val staff =
        sword.copy(
            rscm = "obj.staff_of_air",
            category = WeaponCategory.Staff,
            attackAnim = "human_stafforb_pummel",
            attackSound = 2555,
            defendAnim = "human_stafforb_block",
        )

    private val kiteshield = ShieldFacts("obj.iron_kiteshield", "Iron kiteshield")
    private val defender = ShieldFacts("obj.iron_defender", "Iron defender")

    @Test
    fun `a sword guard stabs with the sword and blocks with the shield`() {
        val guard = human("Guard", attack = 15)
        val result = NpcCombatAnimResolver.resolve(guard, sword, kiteshield, sequences)
        assertNotNull(result)
        assertEquals("human_sword_stab", result!!.attackAnim)
        assertEquals("category.attacktype_stab", result.attackType)
        assertEquals(2549, result.attackSound)
        assertEquals("human_shield_defence", result.defendAnim)
        assertEquals("weapon:obj.iron_sword", result.source)
        assertNull(result.projTravel)
    }

    @Test
    fun `battleaxe and mace use their own animations and attack types`() {
        val axe = NpcCombatAnimResolver.resolve(human("Barbarian"), battleaxe, null, sequences)!!
        assertEquals("human_axe_hack", axe.attackAnim)
        assertEquals("category.attacktype_slash", axe.attackType)
        assertEquals("human_axe_block", axe.defendAnim)

        val blunt = NpcCombatAnimResolver.resolve(human("Thug"), mace, null, sequences)!!
        assertEquals("human_blunt_pound", blunt.attackAnim)
        assertEquals("category.attacktype_crush", blunt.attackType)
        assertEquals("human_blunt_block", blunt.defendAnim)
    }

    @Test
    fun `a scimitar without a block animation falls back to the sword block`() {
        val result = NpcCombatAnimResolver.resolve(human("Pirate"), scimitar, null, sequences)!!
        assertEquals("human_sword_slash", result.attackAnim)
        assertEquals("human_sword_def", result.defendAnim)
    }

    @Test
    fun `a defender parries instead of the shield block`() {
        val result = NpcCombatAnimResolver.resolve(human("Knight"), sword, defender, sequences)!!
        assertEquals("warguild_parry_defend", result.defendAnim)
    }

    @Test
    fun `an archer fires arrows from range`() {
        val archer = human("Archer", attack = 20, ranged = 40)
        val result = NpcCombatAnimResolver.resolve(archer, shortbow, null, sequences)!!
        assertEquals("human_bow", result.attackAnim)
        assertEquals("category.attacktype_standard", result.attackType)
        assertEquals("iron_arrow_travel", result.projTravel)
        assertEquals("arrow", result.projType)
        assertEquals(7, result.attackRange)
        // The bow's bare-handed block is not worth writing; the default already is that.
        assertNull(result.defendAnim)
    }

    @Test
    fun `an unarmed ranger by name and stats still gets a bow`() {
        val ranger = human("Ranger", attack = 10, ranged = 40)
        val result = NpcCombatAnimResolver.resolve(ranger, null, null, sequences)!!
        assertEquals("human_bow", result.attackAnim)
        assertEquals("ranger", result.source)
    }

    @Test
    fun `a wizard casts a strike spell rather than punching`() {
        val wizard = human("Wizard", attack = 8, magic = 10)
        val result = NpcCombatAnimResolver.resolve(wizard, null, null, sequences)!!
        assertEquals("human_caststrike", result.attackAnim)
        assertEquals("category.attacktype_magic", result.attackType)
        assertEquals("windstrike_travel", result.projTravel)
        assertEquals("magic_spell", result.projType)
        assertEquals(7, result.attackRange)
    }

    @Test
    fun `a staff-holding caster uses the staff cast and the staff block`() {
        val druid = human("Chaos druid", attack = 8, magic = 10)
        val result = NpcCombatAnimResolver.resolve(druid, staff, null, sequences)!!
        assertEquals("human_caststrike_staff", result.attackAnim)
        assertEquals("human_stafforb_block", result.defendAnim)
        assertEquals("caster:obj.staff_of_air", result.source)
    }

    @Test
    fun `a caster by name keeps casting even when melee stats lead`() {
        val dark = human("Dark wizard", attack = 20, magic = 15)
        val result = NpcCombatAnimResolver.resolve(dark, null, null, sequences)!!
        assertEquals("human_caststrike", result.attackAnim)
    }

    @Test
    fun `a fire named caster throws fire strike`() {
        val result = NpcCombatAnimResolver.resolve(human("Fire wizard", magic = 20), null, null, sequences)!!
        assertEquals("firestrike_travel", result.projTravel)
    }

    @Test
    fun `a staff wielder with melee stats hits with the staff`() {
        val tribesman = human("Tribesman", attack = 23, magic = 1)
        val result = NpcCombatAnimResolver.resolve(tribesman, staff, null, sequences)!!
        assertEquals("human_stafforb_pummel", result.attackAnim)
        assertEquals("category.attacktype_crush", result.attackType)
    }

    @Test
    fun `an unarmed humanoid without a shield is left on the defaults`() {
        assertNull(NpcCombatAnimResolver.resolve(human("Man"), null, null, sequences))
        // Every stat equal is not a magic lead.
        val villager = human("Al-Kharid man", attack = 1, ranged = 1, magic = 1)
        assertNull(NpcCombatAnimResolver.resolve(villager, null, null, sequences))
    }

    @Test
    fun `a shield alone still fixes the block animation`() {
        val result = NpcCombatAnimResolver.resolve(human("Man"), null, kiteshield, sequences)!!
        assertNull(result.attackAnim)
        assertEquals("human_shield_defence", result.defendAnim)
    }

    @Test
    fun `monsters take their whole family from the ready animation`() {
        val giant = human("Hill Giant", attack = 18).copy(readyAnim = "giant_update_basic_ready")
        val result = NpcCombatAnimResolver.resolve(giant, null, null, sequences)!!
        assertEquals("giant_update_basic_attack", result.attackAnim)
        assertEquals("giant_update_basic_defend", result.defendAnim)
        assertEquals("giant_update_basic_death", result.deathAnim)
        assertEquals("family:giant_update_basic_ready", result.source)
        assertNull(result.attackType)
    }

    @Test
    fun `npcs with their own attack animation or no attack option are skipped`() {
        val done = human("Man").copy(hasAttackAnim = true)
        assertNull(NpcCombatAnimResolver.resolve(done, sword, null, sequences))
        val peaceful = human("Banker").copy(attackable = false)
        assertNull(NpcCombatAnimResolver.resolve(peaceful, sword, null, sequences))
    }

    private fun human(name: String, attack: Int = 5, ranged: Int = 1, magic: Int = 1): NpcFacts =
        NpcFacts(
            rscm = "npc.${name.lowercase().replace(' ', '_')}",
            name = name,
            readyAnim = "human_ready",
            attack = attack,
            ranged = ranged,
            magic = magic,
            attackable = true,
            hasAttackAnim = false,
        )
}
