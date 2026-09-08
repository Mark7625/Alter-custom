package org.rsmod.content.other.npcaggression

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NpcAggressionFileTest {
    @Test
    fun `parses entries with and without notes`() {
        val file =
            NpcAggressionFile.parse(
                """
                npc = [
                    { id = "npc.hill_giant", wiki = "Hill Giant" },
                    { id = "npc.vyrewatch", wiki = "Vyrewatch", note = "Yes, unless wearing vyre noble clothing" },
                    { id = "npc.guard", aggressive = false },
                ]
                """
                    .trimIndent()
            )
        assertEquals(3, file.npc.size)
        assertEquals("npc.hill_giant", file.npc[0].id)
        assertTrue(file.npc[0].aggressive)
        assertEquals("Vyrewatch", file.npc[1].wiki)
        assertFalse(file.npc[2].aggressive)
    }

    @Test
    fun `generated and override resources load and name npcs`() {
        val generated = NpcAggressionFile.loadResource(NpcAggressionFile.GENERATED_RESOURCE)
        assertTrue(generated.npc.size > 1000, "expected the wiki list, got ${generated.npc.size}")
        assertTrue(generated.npc.all { it.id.startsWith("npc.") })
        assertEquals(generated.npc.size, generated.npc.map { it.id }.toSet().size, "duplicate ids")
        NpcAggressionFile.loadResource(NpcAggressionFile.OVERRIDES_RESOURCE)
    }
}
