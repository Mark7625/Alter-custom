package org.rsmod.content.other.pouches

import dev.openrune.types.ItemServerType
import dev.openrune.types.util.UncheckedType
import jakarta.inject.Inject
import org.rsmod.api.death.PlayerDeathContext
import org.rsmod.api.death.PlayerDeathHandling
import org.rsmod.api.death.PlayerDeathItemHook
import org.rsmod.api.invtx.invClear
import org.rsmod.api.player.SupplyItems
import org.rsmod.api.player.hook.GroundItemDropContext
import org.rsmod.api.player.hook.GroundItemDropResolver
import org.rsmod.api.player.hook.GroundItemDropSource
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.content.other.pouches.lootingbag.LootingBags
import org.rsmod.content.other.pouches.lootingbag.hasLootingBag
import org.rsmod.content.other.pouches.lootingbag.lootingBag
import org.rsmod.content.other.pouches.lootingbag.saveLootingBag
import org.rsmod.content.other.pouches.runepouch.RunePouchRunes
import org.rsmod.content.other.pouches.runepouch.readRunePouch
import org.rsmod.content.other.pouches.runepouch.runePouchSlots
import org.rsmod.content.other.pouches.runepouch.writeRunePouch
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.obj.Obj
import org.rsmod.game.type.getInvObj

/**
 * What happens to a looting bag and a rune pouch when the player dies.
 *
 * The looting bag's contents always go to the floor (to the killer on a PvP death, minus food and
 * potions, otherwise to the player) and never count towards the items kept; the bag itself is
 * destroyed on any PvP or Wilderness death. A rune pouch drops its runes to the killer on a PvP
 * death; outside PvP the runes stay in the pouch.
 */
@OptIn(UncheckedType::class)
class PouchDeathHook
@Inject
constructor(
    private val objRepo: ObjRepository,
    private val mapClock: MapClock,
    private val dropResolver: GroundItemDropResolver,
) : PlayerDeathItemHook {
    override fun beforeDrops(context: PlayerDeathContext, handling: PlayerDeathHandling) {
        val player = context.player
        releaseLootingBag(player, context, handling)
        if (context.isPvpDeath) {
            spillRunePouch(player, context, handling)
        }
    }

    private fun releaseLootingBag(
        player: Player,
        context: PlayerDeathContext,
        handling: PlayerDeathHandling,
    ) {
        if (!player.hasLootingBag()) {
            return
        }
        val bag = player.lootingBag
        for (obj in bag.objs) {
            if (obj == null) {
                continue
            }
            val type = getInvObj(obj)
            if (context.isPvpDeath && SupplyItems.isFoodOrPotion(type)) {
                continue
            }
            drop(player, obj, type, context, handling)
        }
        player.invClear(bag)
        player.saveLootingBag()

        if (context.isPvpDeath || context.inWilderness) {
            for (slot in player.inv.indices) {
                val obj = player.inv[slot] ?: continue
                if (obj.id in LootingBags.ids) {
                    player.inv[slot] = null
                }
            }
        }
    }

    private fun spillRunePouch(
        player: Player,
        context: PlayerDeathContext,
        handling: PlayerDeathHandling,
    ) {
        val slots = player.runePouchSlots() ?: return
        val contents = player.readRunePouch(slots)
        for (slot in 0 until slots) {
            val count = contents.counts[slot]
            if (count == 0) {
                continue
            }
            val rune = RunePouchRunes.byCompact(contents.types[slot]) ?: continue
            drop(player, InvObj(rune.id, count), rune.type, context, handling)
        }
        contents.clear()
        player.writeRunePouch(contents)
    }

    /** Mirrors how the regular death drops place an obj, including ironman ownership rules. */
    private fun drop(
        player: Player,
        item: InvObj,
        type: ItemServerType,
        context: PlayerDeathContext,
        handling: PlayerDeathHandling,
    ) {
        val receiver = handling.dropReceiver
        val params =
            dropResolver.resolve(
                GroundItemDropContext(
                    player = player,
                    type = type,
                    coords = context.coords,
                    source = GroundItemDropSource.Death,
                    receiver = receiver,
                ),
                duration = handling.dropDuration,
                reveal = handling.revealDelay,
            )
        val effectiveReceiver = if (params.ownerOnly) player else receiver
        val obj =
            when {
                effectiveReceiver != null && effectiveReceiver !== player ->
                    Obj.fromPvp(effectiveReceiver, player, item)
                effectiveReceiver != null -> Obj.fromOwner(effectiveReceiver, context.coords, item)
                else -> Obj.fromServer(mapClock, context.coords, item)
            }
        objRepo.add(obj, params.duration, params.reveal)
    }
}
