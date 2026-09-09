package org.rsmod.content.other.pouches.lootingbag

import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.interf.IfButtonOp
import jakarta.inject.Inject
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.invtx.invClear
import org.rsmod.api.market.MarketPrices
import org.rsmod.api.player.events.interact.HeldBanksideEvents
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.startInvTransmit
import org.rsmod.api.player.stopInvTransmit
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.player.ui.ifSetHide
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.advanced.onDestroyHeld
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onIfClose
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeld2
import org.rsmod.api.script.onOpHeld3
import org.rsmod.api.script.onOpHeld4
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.content.interfaces.bank.QuantityMode
import org.rsmod.content.interfaces.bank.lastQtyInput
import org.rsmod.content.interfaces.bank.leftClickQtyMode
import org.rsmod.content.interfaces.bank.scripts.BankInvScript
import org.rsmod.game.entity.Player
import org.rsmod.game.obj.Obj
import org.rsmod.game.type.getInvObj
import org.rsmod.objtx.TransactionResult
import org.rsmod.objtx.isOk
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The looting bag: up to 28 stacks of tradeable items that can only be added in the Wilderness and
 * only taken out again at a bank.
 *
 * The `wilderness_lootingbag` interface serves both the `Check` view (the bag inv) and the
 * `Deposit` view (the player inventory with `Store` ops); its `wilderness_lootingbag_setup` cs2
 * decides which from the mode argument. In the bank, `View` on the bag reveals the
 * `lootingbag_container` panel of the bankside interface, whose ops mirror the bankside deposit
 * ops.
 */
class LootingBagScript
@Inject
constructor(
    private val objRepo: ObjRepository,
    private val bankInv: BankInvScript,
    private val marketPrices: MarketPrices,
) : PluginScript() {
    private var Player.depositAll by boolVarBit("varbit.lootingbag_useallitems")
    private var Player.insideWilderness by boolVarBit("varbit.inside_wilderness")

    override fun ScriptContext.startup() {
        onPlayerLogin { player.restoreLootingBag() }

        onOpHeld1(LootingBags.CLOSED) { swapBag(it.slot, LootingBags.CLOSED, LootingBags.OPEN) }
        onOpHeld1(LootingBags.OPEN) { swapBag(it.slot, LootingBags.OPEN, LootingBags.CLOSED) }
        for (bag in LootingBags.ALL) {
            onOpHeld2(bag) { openCheck() }
            onOpHeld3(bag) { openDeposit() }
            onOpHeld4(bag) { settings() }
            onOpHeldU(bag) { useOnBag(it.secondSlot) }
            onEvent<HeldBanksideEvents.Type>(bag.asRSCM(RSCMType.OBJ)) { showBankPanel(player) }
        }

        onIfModalButton(COMPONENT_ITEMS) { bagInterfaceOp(it.comsub, it.op) }
        onIfClose(INTERFACE) { player.onBagInterfaceClose() }

        onIfModalButton(BANKSIDE_ITEMS) { bankPanelOp(it.comsub, it.op) }
        onIfModalButton(BANKSIDE_BANK_ALL) { bankAll() }
        onIfModalButton(BANKSIDE_DISMISS) { player.dismissBankPanel() }
        onIfClose("interface.bankside") { player.onBankClose() }

        onDestroyHeld {
            if (type in LootingBags.ALL) {
                player.destroyBag()
            }
        }
    }

    private fun ProtectedAccess.swapBag(slot: Int, from: String, to: String) {
        val removed = invDel(inv, from, slot = slot)
        if (removed.failure) {
            return
        }
        invAdd(inv, to, slot = slot)
    }

    private fun ProtectedAccess.openCheck() {
        val bag = player.lootingBag
        player.attr[VIEW_ATTR] = BagView.Check
        ifOpenMainModal(INTERFACE)
        runClientScript(SETUP_SCRIPT, MODE_BAG_CONTENTS, TITLE)
        invTransmit(bag)
        ifSetEvents(COMPONENT_ITEMS, 0 until LootingBags.CAPACITY, IfEvent.Op10)
        ifSetText(COMPONENT_TOTAL, "Total value: ${"%,d".format(bagValue())} coins")
    }

    private fun ProtectedAccess.openDeposit() {
        if (!player.insideWilderness) {
            mes(MSG_WILDERNESS_ONLY)
            return
        }
        player.attr[VIEW_ATTR] = BagView.Deposit
        ifOpenMainModal(INTERFACE)
        runClientScript(SETUP_SCRIPT, MODE_INVENTORY, TITLE)
        ifSetEvents(
            COMPONENT_ITEMS,
            inv.indices,
            IfEvent.Op1,
            IfEvent.Op2,
            IfEvent.Op3,
            IfEvent.Op4,
            IfEvent.Op9,
            IfEvent.Op10,
        )
    }

    /** Check view: op10 examines a bag slot. Deposit view: Store-1/5/All/X, then examine. */
    private suspend fun ProtectedAccess.bagInterfaceOp(slot: Int, op: IfButtonOp) {
        when (player.attr[VIEW_ATTR]) {
            BagView.Check -> {
                if (op == IfButtonOp.Op10) {
                    objExamine(player.lootingBag, slot)
                }
            }
            BagView.Deposit ->
                when (op) {
                    IfButtonOp.Op1 -> deposit(slot, 1)
                    IfButtonOp.Op2 -> deposit(slot, 5)
                    IfButtonOp.Op3 -> deposit(slot, Int.MAX_VALUE)
                    IfButtonOp.Op4 -> {
                        val input = countDialog()
                        if (input > 0) {
                            deposit(slot, input)
                        }
                    }
                    IfButtonOp.Op9,
                    IfButtonOp.Op10 -> objExamine(inv, slot)
                    else -> {}
                }
            null -> {}
        }
    }

    private fun Player.onBagInterfaceClose() {
        val view = attr[VIEW_ATTR] ?: return
        attr.remove(VIEW_ATTR)
        if (view == BagView.Check) {
            stopInvTransmit(lootingBag)
        }
    }

    /** Using an item on the bag asks for a count unless the `Settings` op said to take them all. */
    private suspend fun ProtectedAccess.useOnBag(itemSlot: Int) {
        val obj = inv[itemSlot] ?: return
        if (!player.insideWilderness) {
            mes(MSG_WILDERNESS_ONLY)
            return
        }
        val rejection = LootingBags.rejection(obj, getInvObj(obj))
        if (rejection != null) {
            mes(rejection)
            return
        }
        val carried = inv.objs.sumOf { if (it != null && it.id == obj.id) it.count else 0 }
        val amount =
            if (player.depositAll || carried == 1) {
                Int.MAX_VALUE
            } else {
                val input = countDialog()
                if (input <= 0) {
                    return
                }
                input
            }
        val current = inv[itemSlot]
        if (current == null || current.id != obj.id) {
            return
        }
        deposit(itemSlot, amount)
    }

    private suspend fun ProtectedAccess.settings() {
        val depositAll =
            choice2(
                "Ask how many to deposit.",
                false,
                "Deposit all of that item without asking.",
                true,
                title = "When using an item on the looting bag:",
            )
        player.depositAll = depositAll
        val message =
            if (depositAll) {
                "Using an item on the bag will now deposit all of that item."
            } else {
                "Using an item on the bag will now ask how many to deposit."
            }
        mes(message)
    }

    /**
     * Moves up to [amount] of the obj in inventory [slot] into the bag. Non-stackable objs are taken
     * one slot at a time, starting with the clicked slot.
     *
     * @return `true` if anything was moved.
     */
    private fun ProtectedAccess.deposit(slot: Int, amount: Int): Boolean {
        val obj = inv[slot] ?: return false
        if (!player.insideWilderness) {
            mes(MSG_WILDERNESS_ONLY)
            return false
        }
        val type = getInvObj(obj)
        val rejection = LootingBags.rejection(obj, type)
        if (rejection != null) {
            mes(rejection)
            return false
        }
        val bag = player.lootingBag
        val sources =
            if (type.stackable) {
                listOf(slot)
            } else {
                listOf(slot) + inv.indices.filter { it != slot && inv[it]?.id == obj.id }
            }
        var moved = 0
        var full = false
        for (source in sources) {
            if (moved >= amount) {
                break
            }
            val stack = inv[source] ?: continue
            val take = if (type.stackable) minOf(amount, stack.count) else 1
            val result = invMoveFromSlot(from = inv, into = bag, fromSlot = source, count = take, strict = false)
            val outcome = result[0]
            if (outcome == TransactionResult.NotEnoughSpace) {
                full = true
                break
            }
            if (!outcome.isOk()) {
                break
            }
            moved += take
        }
        if (moved > 0) {
            player.saveLootingBag()
        }
        if (full) {
            mes(if (moved == 0) "Your looting bag is full." else "Your looting bag is now full.")
        }
        return moved > 0
    }

    /* Bank */

    private fun showBankPanel(player: Player) {
        player.attr[BANK_PANEL_ATTR] = true
        player.ifSetHide(BANKSIDE_CONTAINER, false)
        player.startInvTransmit(player.lootingBag)
        player.ifSetEvents(
            BANKSIDE_ITEMS,
            0 until LootingBags.CAPACITY,
            IfEvent.Op1,
            IfEvent.Op2,
            IfEvent.Op3,
            IfEvent.Op4,
            IfEvent.Op5,
            IfEvent.Op6,
            IfEvent.Op7,
            IfEvent.Op10,
        )
    }

    private fun Player.dismissBankPanel() {
        if (!onBankClose()) {
            return
        }
        ifSetHide(BANKSIDE_CONTAINER, true)
    }

    /** @return `true` if the panel was showing. */
    private fun Player.onBankClose(): Boolean {
        if (attr[BANK_PANEL_ATTR] != true) {
            return false
        }
        attr.remove(BANK_PANEL_ATTR)
        stopInvTransmit(lootingBag)
        return true
    }

    /** Same op layout as the bankside inventory: left-click quantity, 1, 5, 10, last X, X, All. */
    private suspend fun ProtectedAccess.bankPanelOp(slot: Int, op: IfButtonOp) {
        val bag = player.lootingBag
        if (op == IfButtonOp.Op10) {
            objExamine(bag, slot)
            return
        }
        if (bag[slot] == null) {
            return
        }
        val count =
            when (op) {
                IfButtonOp.Op1 -> bankLeftClickQuantity()
                IfButtonOp.Op2 -> 1
                IfButtonOp.Op3 -> 5
                IfButtonOp.Op4 -> 10
                IfButtonOp.Op5 -> maxOf(1, lastQtyInput)
                IfButtonOp.Op6 -> {
                    val input = countDialog()
                    if (input <= 0) {
                        return
                    }
                    lastQtyInput = input
                    input
                }
                IfButtonOp.Op7 -> Int.MAX_VALUE
                else -> return
            }
        bankFromBag(slot, count)
    }

    private fun ProtectedAccess.bankLeftClickQuantity(): Int =
        when (leftClickQtyMode) {
            QuantityMode.One -> 1
            QuantityMode.Five -> 5
            QuantityMode.Ten -> 10
            QuantityMode.X -> maxOf(1, lastQtyInput)
            QuantityMode.All -> Int.MAX_VALUE
        }

    private fun ProtectedAccess.bankFromBag(slot: Int, count: Int): Boolean {
        val bag = player.lootingBag
        val deposited = with(bankInv) { invDeposit(slot, count, bag) }
        if (deposited) {
            invCompress(bag)
            player.saveLootingBag()
        }
        return deposited
    }

    private fun ProtectedAccess.bankAll() {
        val bag = player.lootingBag
        if (bag.isEmpty()) {
            mes("The bag is empty.")
            return
        }
        for (slot in bag.indices.reversed()) {
            if (bag[slot] == null) {
                continue
            }
            with(bankInv) { invDeposit(slot, Int.MAX_VALUE, bag) }
        }
        invCompress(bag)
        player.saveLootingBag()
    }

    /* Destroy */

    /** Destroying the bag in the Wilderness spills its contents; anywhere else they are lost. */
    private fun Player.destroyBag() {
        val bag = lootingBag
        if (bag.isEmpty()) {
            return
        }
        if (insideWilderness) {
            for (obj in bag.objs) {
                if (obj == null) {
                    continue
                }
                objRepo.add(Obj.fromOwner(this, coords, obj), SPILL_DURATION)
            }
        }
        invClear(bag)
        saveLootingBag()
    }

    private fun ProtectedAccess.bagValue(): Long =
        player.lootingBag.objs.sumOf { obj ->
            if (obj == null) {
                0L
            } else {
                val type = getInvObj(obj)
                (marketPrices[type] ?: type.cost).toLong() * obj.count
            }
        }

    private enum class BagView {
        Check,
        Deposit,
    }

    private companion object {
        private const val INTERFACE = "interface.wilderness_lootingbag"
        private const val COMPONENT_ITEMS = "component.wilderness_lootingbag:items"
        private const val COMPONENT_TOTAL = "component.wilderness_lootingbag:total"

        private const val BANKSIDE_CONTAINER = "component.bankside:lootingbag_container"
        private const val BANKSIDE_ITEMS = "component.bankside:lootingbag_items"
        private const val BANKSIDE_BANK_ALL = "component.bankside:lootingbag_bankall"
        private const val BANKSIDE_DISMISS = "component.bankside:lootingbag_dismiss"

        /** `[clientscript,wilderness_lootingbag_setup](int mode, string title)`. */
        private const val SETUP_SCRIPT = 495
        private const val MODE_BAG_CONTENTS = 1
        private const val MODE_INVENTORY = 0
        private const val TITLE = "Looting bag"

        private const val MSG_WILDERNESS_ONLY =
            "You can only put items in the looting bag while you're in the Wilderness."

        /** Ticks the spilled contents of a destroyed bag stay on the floor. */
        private const val SPILL_DURATION = 300

        private val VIEW_ATTR = AttributeKey<BagView>()
        private val BANK_PANEL_ATTR = AttributeKey<Boolean>()
    }
}
