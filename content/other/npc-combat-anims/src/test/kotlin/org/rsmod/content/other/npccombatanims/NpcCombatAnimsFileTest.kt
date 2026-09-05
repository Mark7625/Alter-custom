package org.rsmod.content.other.npccombatanims

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NpcCombatAnimsFileTest {
    @Test
    fun `parses an inline entry with every field`() {
        val file =
            NpcCombatAnimsFile.parse(
                """
                npc = [
                    { id = "npc.guard", source = "weapon:obj.iron_sword", attack_anim = "seq.human_sword_stab", attack_type = "category.attacktype_stab", attack_sound = 2549, defend_anim = "seq.human_shield_defence", death_anim = "seq.human_death", proj_travel = "spotanim.iron_arrow_travel", proj_type = "projanim.arrow", attack_range = 7 },
                ]
                """
                    .trimIndent()
            )
        val entry = file.npc.single()
        assertEquals("npc.guard", entry.id)
        assertEquals("seq.human_sword_stab", entry.attackAnim)
        assertEquals("category.attacktype_stab", entry.attackType)
        assertEquals(2549, entry.attackSound)
        assertEquals("seq.human_shield_defence", entry.defendAnim)
        assertEquals("seq.human_death", entry.deathAnim)
        assertEquals("spotanim.iron_arrow_travel", entry.projTravel)
        assertEquals("projanim.arrow", entry.projType)
        assertEquals(7, entry.attackRange)
    }

    @Test
    fun `missing fields are null and an empty array parses`() {
        val entry = NpcCombatAnimsFile.parse("""npc = [ { id = "npc.man" } ]""").npc.single()
        assertNull(entry.attackAnim)
        assertNull(entry.attackRange)
        assertTrue(NpcCombatAnimsFile.parse("npc = [\n]\n").npc.isEmpty())
    }

    @Test
    fun `shipped resources parse, reference the right namespaces and have no duplicates`() {
        for (name in
            listOf(NpcCombatAnimsFile.GENERATED_RESOURCE, NpcCombatAnimsFile.OVERRIDES_RESOURCE)) {
            val file = NpcCombatAnimsFile.loadResource(name)
            val ids = file.npc.map { it.id }
            assertEquals(ids.size, ids.toSet().size, "duplicate npc in $name")
            for (entry in file.npc) {
                assertTrue(entry.id.startsWith("npc."), "${entry.id} in $name")
                entry.attackAnim?.let { assertTrue(it.startsWith("seq."), "${entry.id}: $it") }
                entry.defendAnim?.let { assertTrue(it.startsWith("seq."), "${entry.id}: $it") }
                entry.deathAnim?.let { assertTrue(it.startsWith("seq."), "${entry.id}: $it") }
                entry.attackType?.let {
                    assertTrue(it.startsWith("category.attacktype_"), "${entry.id}: $it")
                }
                entry.projTravel?.let { assertTrue(it.startsWith("spotanim."), "${entry.id}: $it") }
                entry.projType?.let { assertTrue(it.startsWith("projanim."), "${entry.id}: $it") }
            }
        }
    }

    @Test
    fun `the generated file covers the npcs that were reported silent`() {
        val ids = NpcCombatAnimsFile.loadResource(NpcCombatAnimsFile.GENERATED_RESOURCE).npc.associateBy { it.id }
        // A sword, a battleaxe, a mace, a bow, a spell caster and a monster family.
        assertEquals("seq.human_sword_stab", ids.getValue("npc.highwayman").attackAnim)
        assertEquals("seq.human_sword_slash", ids.getValue("npc.black_knight").attackAnim)
        assertEquals("seq.human_axe_hack", ids.getValue("npc.barbarian").attackAnim)
        assertEquals("seq.human_blunt_pound", ids.getValue("npc.ardougne_guard").attackAnim)
        assertEquals("seq.human_bow", ids.getValue("npc.ardougne_archer").attackAnim)
        assertEquals("seq.human_caststrike", ids.getValue("npc.wizard").attackAnim)
        assertEquals("seq.giant_update_basic_attack", ids.getValue("npc.giant").attackAnim)
        // Villagers with every stat equal are not casters.
        assertNull(ids["npc.al_kharid_man"])
    }
}
