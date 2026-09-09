package org.rsmod.content.other.emirsarena.duel

import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.interf.IfButtonOp
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.invtx.invAddOrDrop
import org.rsmod.api.invtx.invTransfer
import org.rsmod.api.player.output.UpdateInventory
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import dev.openrune.rscm.RSCM.asRSCM
import org.rsmod.api.player.ui.ifCloseSub
import org.rsmod.api.player.ui.ifOpenMainModal
import org.rsmod.api.player.ui.ifOpenMainSidePair
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.player.ui.ifSetText
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.content.other.emirsarena.EmirsArena
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.Inventory

/**
 * The stake step of a legacy duel, between the rules screen and the confirmation screen.
 *
 * Each player's stake lives in `inv.emirs_arena_stake`, a persisted inventory, from the moment
 * it is offered until the duel ends, so a crash or restart mid-duel cannot lose it: whatever is
 * still in there at login is handed back (see [recover]). The trade screens are hard-wired to
 * `inv.tradeoffer`, so the holding inventory is transmitted to the client under that id, and to
 * the opponent as a mirrored copy. On any outcome the manager either hands both stakes to the
 * winner or gives everything back.
 */
@Singleton
class DuelStakes
@Inject
constructor(private val eventBus: EventBus, private val objRepo: ObjRepository) {
    fun stakeInv(player: Player): Inventory = player.invMap.getOrPut(EmirsArena.STAKE_INV)

    fun hasStake(player: Player): Boolean = stakeInv(player).isNotEmpty()

    private val displayInvId: Int by lazy { EmirsArena.STAKE_DISPLAY_INV.asRSCM(RSCMType.INV) }

    private fun showOwn(player: Player) {
        UpdateInventory.updateInvFullAs(player, stakeInv(player), displayInvId)
    }

    private fun showOther(viewer: Player, owner: Player) {
        UpdateInventory.updateInvFullMirrorAs(viewer, stakeInv(owner), displayInvId)
    }

    /* Stake screen */

    fun open(duel: Duel) {
        for (player in duel.players) {
            val other = duel.other(player)
            val stake = stakeInv(player)
            player.ifOpenMainSidePair(
                EmirsArena.STAKE_INTERFACE,
                EmirsArena.STAKE_SIDE_INTERFACE,
                -1,
                -2,
                eventBus,
            )
            player.ifSetEvents(EmirsArena.STAKE_SIDE_ITEMS, 0 until player.inv.size, *ITEM_OPS)
            player.ifSetEvents(EmirsArena.STAKE_PREFIX + "your_offer", 0 until stake.size, *ITEM_OPS)
            showOwn(player)
            showOther(player, other)
            clearModifiedFlags(player)
            player.ifSetText(EmirsArena.STAKE_PREFIX + "title", "Stake for your duel with ${other.displayName}")
            player.ifSetText(EmirsArena.STAKE_PREFIX + "your_offer_header", "Your stake:")
            player.ifSetText(EmirsArena.STAKE_PREFIX + "other_offer_header", "${other.displayName}'s stake:")
            player.ifSetText(EmirsArena.STAKE_PREFIX + "status", "")
            showFreeSpace(player, other)
        }
    }

    /** The black box in the middle column: how much room the opponent has for what you stake. */
    private fun showFreeSpace(viewer: Player, other: Player) {
        viewer.ifSetText(
            EmirsArena.STAKE_PREFIX + "free_space_text",
            "${other.displayName} has ${other.inv.freeSpace()} free<br>inventory slots.",
        )
    }

    /** Handles an "Offer" op on the backpack panel. */
    suspend fun offer(access: ProtectedAccess, duel: Duel, slot: Int, op: IfButtonOp) {
        val player = access.player
        val obj = player.inv[slot] ?: return
        if (op == IfButtonOp.Op10) {
            access.mes(ServerCacheManager.getItem(obj.id)?.examine ?: "")
            return
        }
        val type = ServerCacheManager.getItem(obj.id)
        if (type == null || !type.tradeable) {
            access.mes("That item can't be staked.")
            return
        }
        val requested =
            when (op) {
                IfButtonOp.Op1 -> preferredQuantity(player)
                IfButtonOp.Op2 -> 1
                IfButtonOp.Op3 -> 5
                IfButtonOp.Op4 -> QUANTITY_X
                IfButtonOp.Op5 -> QUANTITY_ALL
                else -> return
            }
        val count =
            when (requested) {
                QUANTITY_X -> access.countDialog("Enter amount:")
                QUANTITY_ALL -> Int.MAX_VALUE
                else -> requested
            }
        if (count <= 0 || duel.stage != DuelStage.Stakes) {
            return
        }
        val moved = moveMatching(player, from = player.inv, into = stakeInv(player), objId = obj.id, firstSlot = slot, count = count)
        if (moved == 0) {
            access.mes("Your stake is full.")
            return
        }
        onChanged(duel, player, removed = false)
    }

    /** Handles a "Remove" op on the player's own offer. */
    suspend fun remove(access: ProtectedAccess, duel: Duel, slot: Int, op: IfButtonOp) {
        val player = access.player
        val stake = stakeInv(player)
        val obj = stake[slot] ?: return
        if (op == IfButtonOp.Op10) {
            access.mes(ServerCacheManager.getItem(obj.id)?.examine ?: "")
            return
        }
        val count =
            when (op) {
                IfButtonOp.Op1 -> 1
                IfButtonOp.Op2 -> 5
                IfButtonOp.Op3 -> 10
                IfButtonOp.Op4 -> Int.MAX_VALUE
                IfButtonOp.Op5 -> access.countDialog("Enter amount:")
                else -> return
            }
        if (count <= 0 || duel.stage != DuelStage.Stakes) {
            return
        }
        val moved = moveMatching(player, from = stake, into = player.inv, objId = obj.id, firstSlot = slot, count = count)
        if (moved == 0) {
            access.mes("You don't have enough inventory space to take that back.")
            return
        }
        onChanged(duel, player, removed = true)
    }

    private fun onChanged(duel: Duel, changedBy: Player, removed: Boolean) {
        duel.resetAcceptance()
        val other = duel.other(changedBy)
        showOwn(changedBy)
        showOther(other, changedBy)
        if (removed) {
            VarPlayerIntMapSetter.set(changedBy, EmirsArena.STAKE_MODIFIED_MINE_VARBIT, 1)
            VarPlayerIntMapSetter.set(other, EmirsArena.STAKE_MODIFIED_OTHER_VARBIT, 1)
        }
        changedBy.ifSetText(EmirsArena.STAKE_PREFIX + "status", "")
        other.ifSetText(EmirsArena.STAKE_PREFIX + "status", "${changedBy.displayName} has changed the stake.")
        showFreeSpace(other, changedBy)
    }

    /** Returns true once both players have accepted the stake screen. */
    fun accept(duel: Duel, player: Player): Boolean {
        if (duel.hasAccepted(player)) {
            return false
        }
        val both = duel.accept(player)
        player.ifSetText(EmirsArena.STAKE_PREFIX + "status", "Waiting for other player...")
        duel.other(player).ifSetText(EmirsArena.STAKE_PREFIX + "status", "Other player has accepted.")
        return both
    }

    /* Confirmation */

    fun openConfirm(duel: Duel) {
        duel.resetAcceptance()
        for (player in duel.players) {
            val other = duel.other(player)
            clearModifiedFlags(player)
            showOwn(player)
            showOther(player, other)
            player.ifCloseSub(EmirsArena.STAKE_SIDE_INTERFACE, eventBus)
            player.ifOpenMainModal(EmirsArena.STAKE_CONFIRM_INTERFACE, eventBus)
            player.ifSetText(EmirsArena.STAKE_CONFIRM_PREFIX + "tradeopponent", "Staking against: ${other.displayName}")
            player.ifSetText(EmirsArena.STAKE_CONFIRM_PREFIX + "you_will_give", "You are staking:")
            player.ifSetText(EmirsArena.STAKE_CONFIRM_PREFIX + "you_will_receive", "${other.displayName} is staking:")
        }
    }

    /* Leaving the stake screens */

    fun close(player: Player) {
        UpdateInventory.updateInvStopTransmit(player, displayInvId)
        clearModifiedFlags(player)
    }

    /**
     * Returns whatever an interrupted duel left in the holding inventory. Only the backpack is
     * used (nothing is dropped; the player may be nowhere near the arena); anything that does not
     * fit stays put and is retried at the next login or when the player walks into the arena.
     */
    fun recover(player: Player) {
        val stake = stakeInv(player)
        if (stake.isEmpty()) {
            return
        }
        var leftover = false
        for (slot in stake.indices) {
            val obj = stake[slot] ?: continue
            val result = player.invTransfer(from = stake, fromSlot = slot, count = obj.count, into = player.inv)
            if (result.failure) {
                leftover = true
            }
        }
        if (leftover) {
            player.mes(
                "Part of your stake from an interrupted duel is still being held for you. Free " +
                    "up some backpack space to collect the rest."
            )
        } else {
            player.mes("Your stake from an interrupted duel has been returned to you.")
        }
    }

    /** Gives [player] their own stake back, dropping anything the backpack cannot hold. */
    fun refund(player: Player) {
        moveAllOut(stakeInv(player), player)
    }

    /** Hands both stakes to [winner]. */
    fun award(winner: Player, loser: Player) {
        moveAllOut(stakeInv(loser), winner)
        moveAllOut(stakeInv(winner), winner)
    }

    private fun moveAllOut(stake: Inventory, receiver: Player) {
        for (slot in stake.indices) {
            val obj = stake[slot] ?: continue
            val name =
                try {
                    RSCM.getReverseMapping(RSCMType.OBJ, obj.id)
                } catch (e: Throwable) {
                    continue
                }
            stake.objs[slot] = null
            stake.modifiedSlots.set(slot)
            receiver.invAddOrDrop(objRepo, name, obj.count)
        }
    }

    /* Helpers */

    /**
     * Moves up to [count] of [objId] from [from] into [into], starting at [firstSlot]; stackables
     * move in one go, unstackables one slot at a time. Returns how many objs moved.
     */
    private fun moveMatching(
        player: Player,
        from: Inventory,
        into: Inventory,
        objId: Int,
        firstSlot: Int,
        count: Int,
    ): Int {
        var remaining = count
        var moved = 0
        val slots = listOf(firstSlot) + from.indices.filter { it != firstSlot }
        for (slot in slots) {
            if (remaining <= 0) break
            val obj = from[slot] ?: continue
            if (obj.id != objId) continue
            val take = minOf(remaining, obj.count)
            val result = player.invTransfer(from = from, fromSlot = slot, count = take, into = into)
            if (result.failure) {
                break
            }
            remaining -= take
            moved += take
        }
        return moved
    }

    private fun preferredQuantity(player: Player): Int =
        when (player.vars[EmirsArena.OFFER_QUANTITY_VARBIT]) {
            1 -> 5
            2 -> QUANTITY_X
            3 -> QUANTITY_ALL
            else -> 1
        }

    private fun clearModifiedFlags(player: Player) {
        VarPlayerIntMapSetter.set(player, EmirsArena.STAKE_MODIFIED_MINE_VARBIT, 0)
        VarPlayerIntMapSetter.set(player, EmirsArena.STAKE_MODIFIED_OTHER_VARBIT, 0)
    }

    private companion object {
        private const val QUANTITY_X = -1
        private const val QUANTITY_ALL = -2

        private val ITEM_OPS =
            arrayOf(IfEvent.Op1, IfEvent.Op2, IfEvent.Op3, IfEvent.Op4, IfEvent.Op5, IfEvent.Op10)
    }
}
