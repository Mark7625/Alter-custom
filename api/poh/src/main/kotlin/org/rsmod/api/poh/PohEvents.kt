package org.rsmod.api.poh

import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player

/**
 * Published after the owner is teleported into their (re)built house region. Feature scripts
 * subscribe to (re)spawn house npcs - servants, dungeon guards - idempotently.
 */
public data class PohHouseEnteredEvent(public val player: Player, public val buildMode: Boolean) :
    UnboundEvent

/** Published when the owner leaves their house (exit portal, options panel or logout). */
public data class PohHouseExitedEvent(public val player: Player) : UnboundEvent
