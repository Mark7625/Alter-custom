@file:OptIn(dev.openrune.types.util.UncheckedType::class)

package org.rsmod.content.bosses.vorkath

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.death.PlayerDeathContext
import org.rsmod.api.death.PlayerDeathDrops
import org.rsmod.api.death.PlayerDeathStorageHook
import org.rsmod.api.invtx.add
import org.rsmod.api.invtx.delete
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.output.mes
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj

internal val VORKATH_DEATH_STORAGE: AttributeKey<MutableList<Int>> =
    AttributeKey(persistenceKey = "vorkath_death_storage_v1")

@Singleton
internal class VorkathDeathStorage
@Inject
constructor(private val encounters: VorkathEncounterManager) : PlayerDeathStorageHook {
    override fun store(
        context: PlayerDeathContext,
        result: PlayerDeathDrops.DeathDropResult,
    ): Boolean {
        val player = context.player
        val hadStoredItems = player.attr.has(VORKATH_DEATH_STORAGE)
        if (!encounters.isActive(player)) {
            if (hadStoredItems) {
                player.attr.remove(VORKATH_DEATH_STORAGE)
                player.attr.remove(VORKATH_STORAGE_REMINDER_SHOWN)
                player.mes("Your items held by Torfinn were lost because you died an unsafe death.")
            }
            return false
        }
        val lost = result.supplyPile + result.lostTradeable + result.lostUntradeable
        if (lost.isEmpty()) {
            if (hadStoredItems) {
                player.attr.remove(VORKATH_DEATH_STORAGE)
                player.attr.remove(VORKATH_STORAGE_REMINDER_SHOWN)
                player.mes(
                    "Your items held by Torfinn were lost because you died again before reclaiming them."
                )
            }
            return false
        }
        if (hadStoredItems) {
            player.mes("Your previously stored items were lost.")
        }
        player.attr[VORKATH_DEATH_STORAGE] = VorkathStorageCodec.encode(lost)
        player.attr.remove(VORKATH_STORAGE_REMINDER_SHOWN)
        player.mes("Torfinn has collected your items. He will return them for 100,000 coins.")
        return true
    }

    fun hasItems(player: Player): Boolean =
        VorkathStorageCodec.decode(player.attr[VORKATH_DEATH_STORAGE]).isNotEmpty()

    fun count(player: Player): Int =
        VorkathStorageCodec.decode(player.attr[VORKATH_DEATH_STORAGE]).size

    fun reclaim(player: Player): Boolean {
        val items = VorkathStorageCodec.decode(player.attr[VORKATH_DEATH_STORAGE])
        if (items.isEmpty()) {
            player.mes("Torfinn is not holding any items for you.")
            return false
        }
        val query =
            player.invTransaction(player.inv) {
                val inv = select(player.inv)
                delete(inv, "obj.coins".asRSCM(RSCMType.OBJ), VORKATH_DEATH_FEE)
                for (item in items) add(inv, item.id, item.count, item.vars)
            }
        if (!query.success) {
            player.mes("You need 100,000 coins and enough inventory space for every stored item.")
            return false
        }
        player.attr.remove(VORKATH_DEATH_STORAGE)
        player.attr.remove(VORKATH_STORAGE_REMINDER_SHOWN)
        player.mes("Torfinn returns all of your stored items.")
        return true
    }
}

internal object VorkathStorageCodec {
    internal fun encode(items: List<InvObj>): MutableList<Int> =
        items.flatMapTo(mutableListOf()) { listOf(it.id, it.count, it.vars) }

    internal fun decode(values: List<Int>?): List<InvObj> =
        values.orEmpty().chunked(3).mapNotNull { triple ->
            if (triple.size != 3 || triple[1] <= 0) null
            else InvObj(triple[0], triple[1], triple[2])
        }
}
