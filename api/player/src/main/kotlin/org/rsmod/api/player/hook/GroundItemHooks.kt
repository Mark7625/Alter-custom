package org.rsmod.api.player.hook

import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import org.rsmod.game.entity.Player
import org.rsmod.game.obj.Obj
import org.rsmod.map.CoordGrid

public enum class GroundItemDropSource {
    Manual,
    Death,
    Overflow,
}

public data class GroundItemDropContext(
    val player: Player,
    val type: ItemServerType,
    val coords: CoordGrid,
    val source: GroundItemDropSource,
    val receiver: Player? = null,
)

public data class GroundItemDropParams(
    val duration: Int,
    val reveal: Int,
    val ownerOnly: Boolean = false,
)

public fun interface PlayerGroundItemDropHook {
    /**
     * @return Adjusted drop parameters when this hook applies, or `null` to use defaults.
     */
    public fun adjustDrop(
        context: GroundItemDropContext,
        duration: Int,
        reveal: Int,
    ): GroundItemDropParams?
}

public fun interface PlayerObjTakeValidateHook {
    /**
     * @return A denial message if the pickup should be blocked, or `null` if allowed.
     */
    public fun validateTake(player: Player, obj: Obj, objType: ItemServerType): String?
}

public class GroundItemDropResolver
@Inject
constructor(private val hooks: Set<@JvmSuppressWildcards PlayerGroundItemDropHook>) {
    public fun resolve(
        context: GroundItemDropContext,
        duration: Int,
        reveal: Int,
    ): GroundItemDropParams {
        for (hook in hooks) {
            val adjusted = hook.adjustDrop(context, duration, reveal)
            if (adjusted != null) {
                return adjusted
            }
        }
        return GroundItemDropParams(duration, reveal)
    }
}

public class PlayerObjTakeValidator
@Inject
constructor(private val hooks: Set<@JvmSuppressWildcards PlayerObjTakeValidateHook>) {
    public fun validate(player: Player, obj: Obj, objType: ItemServerType): String? {
        for (hook in hooks) {
            val denial = hook.validateTake(player, obj, objType)
            if (denial != null) {
                return denial
            }
        }
        return null
    }
}

/**
 * Lets content receive a picked-up ground obj into a container other than the player inventory,
 * such as an open looting bag in the Wilderness.
 *
 * [redirects] is asked before the pickup starts (so the inventory-space check is skipped) and
 * again once the obj has been removed from the ground. [take] should only fail if the container
 * changed in between, in which case the obj falls back to the regular inventory.
 */
public interface PlayerObjTakeRedirectHook {
    public fun redirects(player: Player, obj: Obj, objType: ItemServerType): Boolean

    /** @return `true` if [obj] was added to the container. */
    public fun take(player: Player, obj: Obj, objType: ItemServerType): Boolean
}

public class PlayerObjTakeRedirector
@Inject
constructor(private val hooks: Set<@JvmSuppressWildcards PlayerObjTakeRedirectHook>) {
    public fun find(player: Player, obj: Obj, objType: ItemServerType): PlayerObjTakeRedirectHook? =
        hooks.firstOrNull { it.redirects(player, obj, objType) }
}
