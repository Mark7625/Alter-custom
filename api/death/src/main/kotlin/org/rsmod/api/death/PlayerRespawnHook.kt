package org.rsmod.api.death

import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

/**
 * Lets content choose where a player wakes up after dying. Hooks are asked in registration order
 * and the first non-null coordinate wins; when none answers, the player respawns at the default
 * respawn point.
 */
public fun interface PlayerRespawnHook {
    public fun respawn(player: Player): CoordGrid?
}
