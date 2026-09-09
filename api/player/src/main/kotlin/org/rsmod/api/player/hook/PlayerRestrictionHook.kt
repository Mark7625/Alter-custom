package org.rsmod.api.player.hook

import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.game.entity.Player

/**
 * An everyday action a piece of content may want to forbid while the player is in some special
 * state, such as a duel with "No Food" ticked or a minigame that disallows prayer.
 */
public sealed class RestrictedAction {
    /** A walk request from the client (map click or minimap click). */
    public data object Walk : RestrictedAction()

    /** Turning a prayer on, including quick-prayers. */
    public data object Prayer : RestrictedAction()

    /** Eating food. */
    public data object Food : RestrictedAction()

    /** Drinking a potion. */
    public data object Drink : RestrictedAction()

    /** Wielding or wearing [obj]. */
    public data class Equip(val obj: ItemServerType) : RestrictedAction()
}

/**
 * Registered as a set binding; every hook is asked before the action runs and the first non-null
 * message blocks the action and is shown to the player.
 */
public fun interface PlayerRestrictionHook {
    public fun restriction(player: Player, action: RestrictedAction): String?
}

@Singleton
public class PlayerRestrictions @Inject constructor(private val hooks: Set<PlayerRestrictionHook>) {
    /** Returns the denial message for [action], or `null` when every hook allows it. */
    public fun check(player: Player, action: RestrictedAction): String? {
        for (hook in hooks) {
            val message = hook.restriction(player, action)
            if (message != null) {
                return message
            }
        }
        return null
    }
}
