package org.rsmod.api.death

import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.player.ironman.PlayerGamemode
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

public val DEATH_KILLER_ATTR: AttributeKey<Player> = AttributeKey()

public val LAST_PVP_HIT_TICK_ATTR: AttributeKey<Int> = AttributeKey()

public val DEATH_DROPS_BYPASS_ADMIN_ATTR: AttributeKey<Boolean> = AttributeKey()

public data class PlayerDeathContext(
    val player: Player,
    val coords: CoordGrid,
    val inWilderness: Boolean,
    val wildernessLevel: Int,
    val inRevenantCaves: Boolean,
    val inInstance: Boolean,
    val isSkulled: Boolean,
    val hasProtectItem: Boolean,
    val recentPvpDamage: Boolean,
    val gamemode: Int,
    val killer: Player?,
) {
    val isUIM: Boolean get() = gamemode == PlayerGamemode.ULTIMATE_IRONMAN
    val isHardcoreIronman: Boolean get() = gamemode == PlayerGamemode.HARDCORE_IRONMAN
    val isIronman: Boolean get() = gamemode != PlayerGamemode.NORMAL
    val isPvpDeath: Boolean get() = killer != null || recentPvpDamage
}

public data class PlayerDeathHandling(
    val keepCount: Int,
    val dropReceiver: Player?,
    val dropDuration: Int,
    val revealDelay: Int,
    val supplyPile: Boolean,
    val untradeableHandling: UntradeableHandling,
)

public enum class UntradeableHandling {
    DROP,
    COINS,
    DESTROY,
    KEEP,
}

public const val RECENT_PVP_HIT_TICKS: Int = 600

public interface PlayerDeathHook {
    public fun handleDeath(context: PlayerDeathContext): PlayerDeathHandling?
}

/**
 * Stores the items a player would otherwise lose on death.
 *
 * Returning true claims the lost portion of [result], preventing it from being placed on the
 * ground. Kept items are still returned to the player's inventory by the standard death flow.
 */
public interface PlayerDeathStorageHook {
    public fun store(
        context: PlayerDeathContext,
        result: PlayerDeathDrops.DeathDropResult,
    ): Boolean
}
