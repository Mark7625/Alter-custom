package org.rsmod.content.bosses.vorkath

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.game.entity.Player

/**
 * Single replacement point for a future Dragon Slayer II quest check. OpenRune does not currently
 * expose a complete Dragon Slayer II quest state, so the production default remains accessible.
 */
@Singleton
internal class VorkathAccessPolicy @Inject constructor() {
    fun canAccess(@Suppress("UNUSED_PARAMETER") player: Player): Boolean = true
}
