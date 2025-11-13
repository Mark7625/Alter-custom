package org.alter.skills.mining

import org.alter.game.model.World
import org.alter.game.model.entity.GameObject
import org.alter.game.model.entity.Player

/**
 * Handler interface for rock deplete events.
 * Each rock type can have its own handler to customise depletion behaviour.
 */
interface RockDepleteHandler {
    /**
     * The rock type identifier (e.g., "copper", "tin").
     */
    val rockTypeId: String

    /**
     * Handles the rock depletion event.
     *
     * @param player The player who mined the rock.
     * @param rockObject The GameObject representing the rock that was depleted.
     * @param world The game world.
     * @return true if the handler processed the depletion (preventing default behaviour), false otherwise.
     */
    suspend fun handleDeplete(
        player: Player,
        rockObject: GameObject,
        world: World,
    ): Boolean
}

