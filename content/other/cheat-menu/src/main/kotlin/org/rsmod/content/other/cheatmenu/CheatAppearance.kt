package org.rsmod.content.other.cheatmenu

import org.rsmod.game.entity.player.Appearance

/**
 * The ident-kit slots that make up a player's model, in the order the appearance block sends them.
 *
 * The valid style ids per slot were read out of the revision 240 client cache (config index 2,
 * `IDENTKIT` archive 3), grouping every definition by its `bodyPartId`: parts `0..6` are body type
 * A and parts `7..13` are body type B, in this same slot order. They are far from contiguous -
 * styles added over the years were appended well past the original blocks - which is why they are
 * listed as explicit ranges rather than a single span.
 *
 * The server cache does not decode ident-kit definitions, so there is nothing to read these from at
 * runtime. `CheatMenuDataTest` cross-checks the table against the engine's own default appearance
 * to catch it drifting from the cache.
 */
internal enum class AppearancePart(
    val label: String,
    val slot: Int,
    private val bodyTypeA: List<IntRange>,
    private val bodyTypeB: List<IntRange>,
) {
    Hair(
        "Hair",
        0,
        listOf(0..9, 129..134, 144..151, 201..247),
        listOf(45..55, 118..128, 141..143, 152..152, 154..200),
    ),
    Jaw("Jaw / beard", 1, listOf(10..17, 111..117, 153..153), listOf(292..306)),
    Torso("Torso", 2, listOf(18..25, 105..110, 254..259), listOf(56..60, 89..94, 265..273)),
    Arms("Arms", 3, listOf(26..32, 84..88, 260..264), listOf(61..66, 95..99, 248..253)),
    Hands("Hands", 4, listOf(33..35), listOf(67..69)),
    Legs("Legs", 5, listOf(36..41, 100..104, 281..291), listOf(70..78, 135..140, 274..280)),
    Feet("Feet", 6, listOf(42..44, 82..82), listOf(79..81, 83..83));

    /** Every style id this slot accepts for [bodyType]. */
    fun stylesFor(bodyType: Int): List<Int> = rangesFor(bodyType).flatten()

    /**
     * Whether this slot may be left empty. Facial hair is the only optional part - every id in the
     * jaw slot is an actual beard or moustache model, so "clean shaven" is the absence of a model
     * rather than a blank one.
     */
    val optional: Boolean
        get() = this == Jaw

    /**
     * The style this slot falls back to when switching to [bodyType].
     *
     * Body type B defaults to no facial hair; every jaw id would otherwise put a beard on it.
     */
    fun defaultFor(bodyType: Int): Int =
        if (optional && bodyType == Appearance.BODY_TYPE_B) {
            Appearance.NO_IDENT_KIT
        } else {
            rangesFor(bodyType).first().first
        }

    /**
     * Where [style] sits in this slot's list for [bodyType], as `3/71` - or a plain description
     * when it is not in the list at all. Style ids mean nothing to a reader, so the menus show a
     * position instead.
     */
    fun positionOf(style: Int, bodyType: Int): String {
        if (style == Appearance.NO_IDENT_KIT) {
            return "none"
        }
        val styles = stylesFor(bodyType)
        val index = styles.indexOf(style)
        return if (index < 0) "id $style (not body type ${bodyTypeLabel(bodyType)})" else "${index + 1}/${styles.size}"
    }

    /** Whether [style] renders correctly on [bodyType]. */
    fun accepts(style: Int, bodyType: Int): Boolean =
        (optional && style == Appearance.NO_IDENT_KIT) || style in stylesFor(bodyType)

    private fun bodyTypeLabel(bodyType: Int): String =
        if (bodyType == Appearance.BODY_TYPE_B) "B" else "A"

    private fun rangesFor(bodyType: Int): List<IntRange> =
        if (bodyType == Appearance.BODY_TYPE_B) bodyTypeB else bodyTypeA
}

/** The recolourable areas of a player's model, with the vanilla palette size for each. */
internal enum class AppearanceColour(val label: String, val index: Int, val paletteSize: Int) {
    Hair("Hair colour", 0, 25),
    Torso("Torso colour", 1, 29),
    Legs("Leg colour", 2, 29),
    Feet("Boot colour", 3, 6),
    Skin("Skin colour", 4, 8),
}

/** Ident-kit and colour values a freshly created [Appearance] starts with. */
internal object DefaultAppearance {
    val identKit: IntArray = intArrayOf(9, 14, 109, 26, 33, 36, 42)
    val colours: IntArray = intArrayOf(0, 3, 2, 0, 0)

    /**
     * A complete style set for [bodyType], indexed by [AppearancePart.slot].
     *
     * The appearance block sends explicit ident-kit model ids, so `Appearance.bodyType` on its own
     * only flips a flag - the character keeps whichever models it was already wearing. Switching
     * body type has to swap every slot, which is what this is for.
     */
    fun stylesFor(bodyType: Int): IntArray =
        IntArray(AppearancePart.entries.size) { slot ->
            AppearancePart.entries.first { it.slot == slot }.defaultFor(bodyType)
        }
}

internal const val MAX_IDENT_KIT: Int = 32767

internal const val MAX_COLOUR: Int = 255
