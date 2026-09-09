package org.rsmod.game.entity.player

import dev.openrune.types.BasType
import dev.openrune.types.NpcServerType

public class Appearance {
    /**
     * Tracks whether the player's appearance needs to be updated. Initially set to `true` so that
     * the player's appearance can be synchronized in the next update block.
     */
    public var rebuild: Boolean = true
        internal set

    public var bas: BasType? = null
        set(value) {
            field = value
            rebuild = true
        }

    public var transmog: NpcServerType? = null
        set(value) {
            field = value
            rebuild = true
        }

    public var skullIcon: Int? = null
        set(value) {
            field = value
            rebuild = true
        }

    public var overheadIcon: Int? = null
        set(value) {
            field = value
            rebuild = true
        }

    public var bodyType: Int = 0
        set(value) {
            field = value
            rebuild = true
        }

    public var pronoun: Int = 0
        set(value) {
            field = value
            rebuild = true
        }

    public var namePrefix: String? = null
        set(value) {
            field = value
            rebuild = true
        }

    public var nameSuffix: String? = null
        set(value) {
            field = value
            rebuild = true
        }

    public var combatLvlSuffix: String? = null
        set(value) {
            field = value
            rebuild = true
        }

    public var softHidden: Boolean = false
        set(value) {
            field = value
            rebuild = true
        }

    public var combatLevel: Int = 3
        set(value) {
            field = value
            rebuild = true
        }

    private val colours: ByteArray = ByteArray(5)
    private val identKit: ShortArray = ShortArray(7) { -1 }

    // TODO: Move default colours/identkit assignment to a relevant plugin and
    //  delete this init block.
    init {
        assignDefaultColours()
        assignDefaultIdentKit()
    }

    public fun setColour(index: Int, colour: Int) {
        require(colour in 0..255) { "colour must be in range [0..255]. ($colour)" }
        this.colours[index] = colour.toByte()
        this.rebuild = true
    }

    /**
     * @param identKit the ident-kit style for [index], or [NO_IDENT_KIT] to leave the slot empty.
     *   The backing array already starts as [NO_IDENT_KIT], and the appearance encoder passes the
     *   value straight through to a protocol that treats `-1` as "no model" - a body type B jaw,
     *   for instance, has no model rather than a blank one.
     */
    public fun setIdentKit(index: Int, identKit: Int) {
        require(identKit == NO_IDENT_KIT || identKit in 0..65535) {
            "identKit must be $NO_IDENT_KIT (none) or in range [0..65535]. ($identKit)"
        }
        this.identKit[index] = identKit.toShort()
        this.rebuild = true
    }

    public fun coloursSnapshot(): List<Byte> = colours.toList()

    public fun identKitSnapshot(): List<Short> = identKit.toList()

    public fun clearRebuildFlag() {
        rebuild = false
    }

    public fun subjectPronoun(): String =
        when (pronoun) {
            PRONOUN_HE -> "He"
            PRONOUN_SHE -> "She"
            PRONOUN_THEY -> "They"
            else -> if (bodyType == BODY_TYPE_A) "He" else "She"
        }

    private fun assignDefaultColours() {
        colours[0] = 0
        colours[1] = 3
        colours[2] = 2
        colours[3] = 0
        colours[4] = 0
    }

    private fun assignDefaultIdentKit() {
        identKit[0] = 9
        identKit[1] = 14
        identKit[2] = 109
        identKit[3] = 26
        identKit[4] = 33
        identKit[5] = 36
        identKit[6] = 42
    }

    public companion object {
        /** Leaves an ident-kit slot empty; the protocol encodes it as "no model". */
        public const val NO_IDENT_KIT: Int = -1

        public const val PRONOUN_HE: Int = 0
        public const val PRONOUN_SHE: Int = 1
        public const val PRONOUN_THEY: Int = 2

        public const val BODY_TYPE_A: Int = 0
        public const val BODY_TYPE_B: Int = 1
    }
}
