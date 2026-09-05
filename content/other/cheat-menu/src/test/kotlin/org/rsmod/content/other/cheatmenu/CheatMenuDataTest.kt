package org.rsmod.content.other.cheatmenu

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.player.cheat.adminGodMode
import org.rsmod.api.player.cheat.adminInfiniteRunes
import org.rsmod.api.player.cheat.adminMaxHit
import org.rsmod.api.player.cheat.adminNoClip
import org.rsmod.api.player.cheat.adminOneHitKill
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.Appearance
import org.rsmod.map.CoordGrid

class CheatMenuDataTest {
    @Test
    fun `cheat toggles default to off and flip independently`() {
        val player = Player()

        assertFalse(player.adminGodMode)
        assertFalse(player.adminOneHitKill)
        assertFalse(player.adminMaxHit)
        assertFalse(player.adminInfiniteRunes)
        assertFalse(player.adminNoClip)

        player.adminOneHitKill = true
        assertTrue(player.adminOneHitKill)
        assertFalse(player.adminGodMode)
        assertFalse(player.adminInfiniteRunes)
        assertFalse(player.adminNoClip)

        player.adminOneHitKill = false
        assertFalse(player.adminOneHitKill)
    }

    /**
     * `ifChoice` encodes its options as a single pipe-delimited string, so a label containing a
     * pipe would silently split into extra entries and shift every selection index after it.
     */
    @Test
    fun `no menu label contains the choice delimiter`() {
        val labels =
            TeleportRegion.entries.flatMap { region ->
                listOf(region.label) + region.destinations.map(TeleportDestination::name)
            } + AppearancePart.entries.map(AppearancePart::label) +
                AppearanceColour.entries.map(AppearanceColour::label)

        for (label in labels) {
            assertFalse(label.contains('|'), "Label must not contain '|': $label")
            assertTrue(label.isNotBlank(), "Label must not be blank.")
        }
    }

    @Test
    fun `toggle states are colour tagged and safe to put in an option label`() {
        val on = CheatMenuScript.state(true)
        val off = CheatMenuScript.state(false)

        assertEquals("<col=${CheatMenuScript.GREEN}>ON</col>", on)
        assertEquals("<col=${CheatMenuScript.RED}>OFF</col>", off)

        // A stray pipe would split the option into two entries; an unclosed tag would bleed the
        // colour into every label after it.
        for (label in listOf(on, off)) {
            assertFalse(label.contains('|'), "State must not contain '|': $label")
            assertEquals(
                label.count { it == '<' },
                label.count { it == '>' },
                "State must have balanced colour markup: $label",
            )
            assertTrue(label.endsWith("</col>"), "State must close its colour tag: $label")
        }
    }

    /**
     * The chatbox dialogue only exists in two- to five-option flavours, so a page of choices plus
     * its navigation entry has to land inside that range.
     */
    @Test
    fun `a full page plus its navigation entry fits the chatbox dialogue`() {
        assertTrue(CheatMenuScript.PAGE_SIZE + 1 in 2..5)
    }

    @Test
    fun `destination search matches exactly, by prefix, and by substring`() {
        assertEquals(
            CoordGrid(3222, 3218),
            CheatMenuScript.findDestination("Lumbridge")?.coords,
            "Exact name should match.",
        )
        assertEquals(
            CoordGrid(3222, 3218),
            CheatMenuScript.findDestination("  lumbridge  ")?.coords,
            "Match should be case-insensitive and trimmed.",
        )
        assertEquals(
            "Grand Exchange",
            CheatMenuScript.findDestination("grand")?.name,
            "Prefix should match.",
        )
        assertEquals(
            "Grand Exchange",
            CheatMenuScript.findDestination("exchange")?.name,
            "Substring should match.",
        )
    }

    @Test
    fun `destination search rejects blank and unknown input`() {
        assertNull(CheatMenuScript.findDestination(""))
        assertNull(CheatMenuScript.findDestination("   "))
        assertNull(CheatMenuScript.findDestination("definitely not a real place"))
    }

    /**
     * An exact name must win over a destination that merely contains it, otherwise typing a short
     * name would land somewhere unexpected.
     */
    @Test
    fun `exact destination match wins over a longer containing name`() {
        for (destination in TeleportRegion.entries.flatMap(TeleportRegion::destinations)) {
            assertEquals(
                destination.name,
                CheatMenuScript.findDestination(destination.name)?.name,
                "Typing an exact destination name must select that destination.",
            )
        }
    }

    @Test
    fun `teleport destinations are unique and in bounds`() {
        val names = mutableSetOf<String>()
        for (region in TeleportRegion.entries) {
            assertTrue(region.destinations.isNotEmpty(), "Region ${region.label} is empty.")
            for (destination in region.destinations) {
                assertTrue(
                    names.add(destination.name),
                    "Duplicate destination name: ${destination.name}",
                )
                val coords = destination.coords
                assertTrue(
                    coords.x in 1..CoordGrid.X_BIT_MASK,
                    "${destination.name} has an out-of-bounds x: ${coords.x}",
                )
                assertTrue(
                    coords.z in 1..CoordGrid.Z_BIT_MASK,
                    "${destination.name} has an out-of-bounds z: ${coords.z}",
                )
                assertTrue(
                    coords.level in 0..CoordGrid.LEVEL_BIT_MASK,
                    "${destination.name} has an out-of-bounds level: ${coords.level}",
                )
            }
        }
    }

    @Test
    fun `appearance slots map one to one onto the default arrays`() {
        val partSlots = AppearancePart.entries.map(AppearancePart::slot)
        assertEquals(partSlots.distinct(), partSlots, "Ident-kit slots must be unique.")
        assertEquals(DefaultAppearance.identKit.size, AppearancePart.entries.size)
        assertEquals((0 until DefaultAppearance.identKit.size).toList(), partSlots.sorted())

        val colourIndices = AppearanceColour.entries.map(AppearanceColour::index)
        assertEquals(colourIndices.distinct(), colourIndices, "Colour indices must be unique.")
        assertEquals(DefaultAppearance.colours.size, AppearanceColour.entries.size)
        assertEquals((0 until DefaultAppearance.colours.size).toList(), colourIndices.sorted())
    }

    @Test
    fun `appearance styles are non-empty and within the api limits`() {
        for (part in AppearancePart.entries) {
            for (bodyType in listOf(Appearance.BODY_TYPE_A, Appearance.BODY_TYPE_B)) {
                val styles = part.stylesFor(bodyType)
                assertTrue(
                    styles.isNotEmpty(),
                    "${part.label} has no styles for body type $bodyType.",
                )
                assertTrue(styles.min() >= 0, "${part.label} has a negative style id.")
                assertTrue(
                    styles.max() <= MAX_IDENT_KIT,
                    "${part.label} exceeds the ident-kit limit.",
                )
                assertEquals(
                    styles.distinct(),
                    styles,
                    "${part.label} lists a style twice for body type $bodyType.",
                )
            }
        }
        for (colour in AppearanceColour.entries) {
            assertTrue(colour.paletteSize > 0, "${colour.label} palette is empty.")
            assertTrue(
                colour.paletteSize - 1 <= MAX_COLOUR,
                "${colour.label} palette exceeds the colour limit.",
            )
        }
    }

    /**
     * The style table is transcribed from the client cache, so it cannot be read back at runtime to
     * verify. The engine's own default appearance is a known-good body type A set, so every one of
     * its ident-kit values must appear in the matching slot - if the table drifts from the cache,
     * this is what catches it.
     */
    @Test
    fun `engine default ident-kit values are valid body type A styles`() {
        for (part in AppearancePart.entries) {
            val engineDefault = DefaultAppearance.identKit[part.slot]
            assertTrue(
                engineDefault in part.stylesFor(Appearance.BODY_TYPE_A),
                "Engine default ${part.label} style $engineDefault is not a known body type A " +
                    "style - the ident-kit table is out of step with the cache.",
            )
        }
    }

    /**
     * A style belonging to the other body type renders as the wrong model on the wrong part - a
     * female torso in the jaw slot, for instance, hangs off the character's chin.
     */
    @Test
    fun `no style is shared between the two body types`() {
        val a = AppearancePart.entries.flatMap { it.stylesFor(Appearance.BODY_TYPE_A) }
        val b = AppearancePart.entries.flatMap { it.stylesFor(Appearance.BODY_TYPE_B) }
        assertTrue(
            a.intersect(b.toSet()).isEmpty(),
            "Body types must not share ident-kit styles.",
        )
    }

    /** Two slots claiming the same style id would mean one of them is reading the wrong part. */
    @Test
    fun `no style is claimed by two different slots`() {
        for (bodyType in listOf(Appearance.BODY_TYPE_A, Appearance.BODY_TYPE_B)) {
            val seen = mutableMapOf<Int, AppearancePart>()
            for (part in AppearancePart.entries) {
                for (style in part.stylesFor(bodyType)) {
                    val clash = seen.put(style, part)
                    assertNull(
                        clash,
                        "Style $style is claimed by both ${clash?.label} and ${part.label}.",
                    )
                }
            }
        }
    }

    /**
     * Switching body type has to rewrite every ident-kit slot, so each body type needs a complete
     * style set and each style has to belong to that body type's range - otherwise the character
     * ends up wearing a mix of both bodies.
     */
    @Test
    fun `each body type has a complete style set it accepts`() {
        for (bodyType in listOf(Appearance.BODY_TYPE_A, Appearance.BODY_TYPE_B)) {
            val styles = DefaultAppearance.stylesFor(bodyType)
            assertEquals(
                AppearancePart.entries.size,
                styles.size,
                "Body type $bodyType must define a style for every part.",
            )
            for (part in AppearancePart.entries) {
                val style = styles[part.slot]
                assertTrue(
                    part.accepts(style, bodyType),
                    "${part.label} style $style is not valid for body type $bodyType.",
                )
            }
        }
    }

    /**
     * Every jaw id is an actual beard or moustache model - there is no blank one - so body type B
     * has to default to no model at all, or switching to it leaves a beard on the character.
     */
    @Test
    fun `body type B defaults to no facial hair`() {
        assertEquals(
            Appearance.NO_IDENT_KIT,
            AppearancePart.Jaw.defaultFor(Appearance.BODY_TYPE_B),
            "Body type B must default to no jaw model.",
        )
        assertTrue(
            AppearancePart.Jaw.accepts(Appearance.NO_IDENT_KIT, Appearance.BODY_TYPE_B),
            "The jaw slot must accept being left empty.",
        )
    }

    /** Only facial hair is optional; a body with no torso or legs would render as gaps. */
    @Test
    fun `only the jaw slot may be left empty`() {
        for (part in AppearancePart.entries) {
            assertEquals(
                part == AppearancePart.Jaw,
                part.optional,
                "${part.label} optionality is wrong.",
            )
            if (part.optional) {
                continue
            }
            for (bodyType in listOf(Appearance.BODY_TYPE_A, Appearance.BODY_TYPE_B)) {
                assertFalse(
                    part.accepts(Appearance.NO_IDENT_KIT, bodyType),
                    "${part.label} must not accept an empty slot.",
                )
                assertTrue(
                    part.defaultFor(bodyType) >= 0,
                    "${part.label} must default to a real model.",
                )
            }
        }
    }

    /**
     * The cycling menus show a position rather than a raw id, and step by index - so every style
     * must be findable in its own list, and stepping must wrap rather than run off either end.
     */
    @Test
    fun `every style has a position in its own list`() {
        for (part in AppearancePart.entries) {
            for (bodyType in listOf(Appearance.BODY_TYPE_A, Appearance.BODY_TYPE_B)) {
                val styles = part.stylesFor(bodyType)
                styles.forEachIndexed { index, style ->
                    assertEquals(
                        "${index + 1}/${styles.size}",
                        part.positionOf(style, bodyType),
                        "${part.label} style $style should report its position.",
                    )
                }
            }
        }
    }

    @Test
    fun `an empty or foreign style reports no position`() {
        val jaw = AppearancePart.Jaw
        assertEquals("none", jaw.positionOf(Appearance.NO_IDENT_KIT, Appearance.BODY_TYPE_B))

        // A body type A jaw held by a body type B character is exactly the mix-up that started
        // this: it must be reported, not silently shown as a valid position.
        val foreign = jaw.stylesFor(Appearance.BODY_TYPE_A).first()
        assertTrue(
            jaw.positionOf(foreign, Appearance.BODY_TYPE_B).contains("not body type"),
            "A style from the other body type must be flagged.",
        )
    }

    @Test
    fun `stepping wraps around both ends of a style list`() {
        for (part in AppearancePart.entries) {
            for (bodyType in listOf(Appearance.BODY_TYPE_A, Appearance.BODY_TYPE_B)) {
                val styles = part.stylesFor(bodyType)
                val last = styles.size - 1
                assertEquals(styles.first(), styles[(last + 1).mod(styles.size)], "Next must wrap.")
                assertEquals(styles.last(), styles[(0 - 1).mod(styles.size)], "Previous must wrap.")
            }
        }
    }

    /** `Appearance.setIdentKit` has to accept the sentinel the cheat menu relies on. */
    @Test
    fun `the appearance api accepts the no-model sentinel`() {
        val appearance = Appearance()
        appearance.setIdentKit(AppearancePart.Jaw.slot, Appearance.NO_IDENT_KIT)
        assertEquals(
            Appearance.NO_IDENT_KIT,
            appearance.identKitSnapshot()[AppearancePart.Jaw.slot].toInt(),
        )
    }

    @Test
    fun `default appearance values fall inside their own limits`() {
        for (identKit in DefaultAppearance.identKit) {
            assertTrue(identKit in 0..MAX_IDENT_KIT, "Default ident-kit out of range: $identKit")
        }
        for (colour in DefaultAppearance.colours) {
            assertTrue(colour in 0..MAX_COLOUR, "Default colour out of range: $colour")
        }
    }
}
