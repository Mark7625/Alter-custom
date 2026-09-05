package org.rsmod.api.account.character.appearance

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.rsmod.game.entity.player.Appearance

class CharacterAppearancePersistenceTest {
    @Test
    fun `restores an appearance saved through the attribute json`() {
        val saved =
            Appearance().apply {
                bodyType = Appearance.BODY_TYPE_B
                pronoun = Appearance.PRONOUN_SHE
                setIdentKit(0, 45)
                setIdentKit(1, Appearance.NO_IDENT_KIT)
                setIdentKit(2, 56)
                // Past `Byte.MAX_VALUE`: colours are stored signed and must come back unsigned.
                setColour(0, 200)
                setColour(4, 5)
            }

        val restored = Appearance()
        CharacterAppearancePersistence.restore(restored, roundTripThroughJson(saved))

        assertEquals(Appearance.BODY_TYPE_B, restored.bodyType)
        assertEquals(Appearance.PRONOUN_SHE, restored.pronoun)
        assertEquals(saved.identKitSnapshot(), restored.identKitSnapshot())
        assertEquals(saved.coloursSnapshot(), restored.coloursSnapshot())
    }

    @Test
    fun `keeps engine defaults when nothing was saved`() {
        val defaults = Appearance()

        val restored = Appearance()
        CharacterAppearancePersistence.restore(restored, emptyMap())

        assertEquals(defaults.bodyType, restored.bodyType)
        assertEquals(defaults.pronoun, restored.pronoun)
        assertEquals(defaults.identKitSnapshot(), restored.identKitSnapshot())
        assertEquals(defaults.coloursSnapshot(), restored.coloursSnapshot())
    }

    @Test
    fun `ignores out of range values instead of failing the login`() {
        val defaults = Appearance()
        val corrupt =
            mapOf<String, Any>(
                "appearance_body_type" to 7,
                "appearance_pronoun" to -1,
                "appearance_ident_kit" to listOf(-9, 999_999, "hair", 26, 33, 36, 42, 1),
                "appearance_colours" to listOf(300, 3, 2, 0, 0, 1),
            )

        val restored = Appearance()
        CharacterAppearancePersistence.restore(restored, corrupt)

        assertEquals(defaults.bodyType, restored.bodyType)
        assertEquals(defaults.pronoun, restored.pronoun)
        assertEquals(defaults.identKitSnapshot(), restored.identKitSnapshot())
        assertEquals(defaults.coloursSnapshot(), restored.coloursSnapshot())
    }

    /** Mirrors what `CharacterAccountRepository` writes to, and reads back from, `character_attrs`. */
    private fun roundTripThroughJson(appearance: Appearance): Map<String, Any> {
        val mapper = ObjectMapper()
        return CharacterAppearancePersistence.encode(appearance).mapValues { (_, value) ->
            mapper.readValue(mapper.writeValueAsString(value), object : TypeReference<Any>() {})
        }
    }
}
