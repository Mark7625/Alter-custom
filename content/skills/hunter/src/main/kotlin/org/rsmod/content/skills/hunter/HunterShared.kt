package org.rsmod.content.skills.hunter

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.random.GameRandom
import org.rsmod.game.entity.Controller
import org.rsmod.game.inv.Inventory
import org.rsmod.game.loc.BoundLocInfo

// Rules shared by every hunter technique. Design notes: docs/hunter.md.

/** The `maxLevel` SkillingSuccessRate interpolates against; published charts run to level 99. */
internal const val MAX_HUNTER_LEVEL: Int = 99

/** A fixed quantity must consume no random draw - tests script the RNG as a draw sequence. */
internal fun rollQuantity(random: GameRandom, quantity: IntRange): Int =
    if (quantity.first == quantity.last) quantity.first else random.of(quantity)

/** A stackable already held needs no free slot, whatever the count. */
internal fun hunterInvSlotsNeeded(inv: Inventory, internal: String, count: Int): Int {
    val stackable = ServerCacheManager.getItem(internal.asRSCM(RSCMType.OBJ))?.isStackable == true
    return when {
        !stackable -> count
        inv.contains(internal) -> 0
        else -> 1
    }
}

/**
 * `Investigate` on an armed trap: walk to it, then say who owns it and whether it has caught.
 * [controller] is deferred rather than resolved by the caller because [arriveDelay] suspends: the
 * trap must be looked up *after* the walk, or one that collapsed en route still reports as set.
 */
internal suspend fun ProtectedAccess.investigateTrap(
    loc: BoundLocInfo,
    noun: String,
    armed: (BoundLocInfo) -> Boolean = { true },
    controller: (BoundLocInfo) -> Controller?,
) {
    arriveDelay()

    if (!armed(loc)) {
        mes("Nothing interesting happens.")
        return
    }

    val found = controller(loc)
    when {
        found == null -> mes("This trap has collapsed.")
        found.trapOwner != player.uid.packed -> mes("This isn't your trap.")
        else -> mes("The $noun is set. Nothing has sprung it yet.")
    }
}
