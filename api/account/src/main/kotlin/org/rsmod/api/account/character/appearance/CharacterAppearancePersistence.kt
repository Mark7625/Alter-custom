package org.rsmod.api.account.character.appearance

import org.rsmod.game.entity.player.Appearance

/**
 * Saves and restores a character's [Appearance].
 *
 * The `account_characters` table has no appearance columns and its schema lives in an external
 * module, so appearance is stored in `character_attrs` - the generic key/json store the character
 * save already writes - rather than in dedicated columns.
 *
 * Values are read straight off [Appearance] when saving and written back before the player is
 * registered on login, so nothing that edits appearance has to remember to persist it.
 */
public object CharacterAppearancePersistence {
    private const val BODY_TYPE_KEY: String = "appearance_body_type"
    private const val PRONOUN_KEY: String = "appearance_pronoun"
    private const val IDENT_KIT_KEY: String = "appearance_ident_kit"
    private const val COLOURS_KEY: String = "appearance_colours"

    /**
     * The attribute keys this object owns.
     *
     * They are written by [encode] instead of by an [org.rsmod.api.attr.AttributeKey], so the
     * generic attribute restore skips them and [Appearance] stays the single source of truth.
     */
    public val persistenceKeys: Set<String> =
        setOf(BODY_TYPE_KEY, PRONOUN_KEY, IDENT_KIT_KEY, COLOURS_KEY)

    public fun encode(appearance: Appearance): Map<String, Any> =
        mapOf(
            BODY_TYPE_KEY to appearance.bodyType,
            PRONOUN_KEY to appearance.pronoun,
            IDENT_KIT_KEY to appearance.identKitSnapshot().map(Short::toInt),
            // Colours are kept in a `ByteArray`, so anything past 127 comes back signed.
            COLOURS_KEY to appearance.coloursSnapshot().map { it.toInt() and 0xFF },
        )

    /**
     * Applies the appearance stored in [attrs] to [appearance], leaving the engine defaults in
     * place for anything missing (a character created before appearance was persisted) or out of
     * range (a hand-edited or corrupt row). Out-of-range values would otherwise throw out of
     * [Appearance.setIdentKit] / [Appearance.setColour] and fail the login.
     */
    public fun restore(appearance: Appearance, attrs: Map<String, Any>) {
        val bodyType = (attrs[BODY_TYPE_KEY] as? Number)?.toInt()
        if (bodyType == Appearance.BODY_TYPE_A || bodyType == Appearance.BODY_TYPE_B) {
            appearance.bodyType = bodyType
        }

        val pronoun = (attrs[PRONOUN_KEY] as? Number)?.toInt()
        if (pronoun != null && pronoun in Appearance.PRONOUN_HE..Appearance.PRONOUN_THEY) {
            appearance.pronoun = pronoun
        }

        val identKit = attrs[IDENT_KIT_KEY] as? List<*>
        if (identKit != null) {
            val slotCount = appearance.identKitSnapshot().size
            for ((slot, value) in identKit.withIndex()) {
                if (slot >= slotCount) {
                    break
                }
                val style = (value as? Number)?.toInt() ?: continue
                if (style == Appearance.NO_IDENT_KIT || style in 0..65535) {
                    appearance.setIdentKit(slot, style)
                }
            }
        }

        val colours = attrs[COLOURS_KEY] as? List<*>
        if (colours != null) {
            val indexCount = appearance.coloursSnapshot().size
            for ((index, value) in colours.withIndex()) {
                if (index >= indexCount) {
                    break
                }
                val colour = (value as? Number)?.toInt() ?: continue
                if (colour in 0..255) {
                    appearance.setColour(index, colour)
                }
            }
        }
    }
}
