package org.rsmod.content.other.pouches.runepouch

import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.interf.IfButtonOp
import dev.openrune.types.util.UncheckedType
import jakarta.inject.Inject
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.player.events.interact.HeldBanksideEvents
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.advanced.onDestroyHeld
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeld3
import org.rsmod.api.script.onOpHeld4
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.obj.Obj
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The rune pouch and divine rune pouch.
 *
 * Runes live in the `rune_pouch_type_n` / `rune_pouch_quantity_n` varbits (three slots, four for
 * the divine pouch), which is what the spell system already reads when casting. The `rune_pouch`
 * interface draws itself from those varbits and the player inventory through its own cs2, so the
 * server only opens it, enables the slot ops and moves runes when a slot is clicked.
 */
@OptIn(UncheckedType::class)
class RunePouchScript
@Inject
constructor(private val objRepo: ObjRepository, private val protectedAccess: ProtectedAccessLauncher) :
    PluginScript() {
    private var Player.quantityMode by intVarBit("varbit.rune_pouch_selectedquantity")
    private var Player.customQuantity by intVarBit("varbit.rune_pouch_customquantity")

    override fun ScriptContext.startup() {
        for (pouch in RunePouches.ALL) {
            onOpHeld1(pouch) { openPouch() }
            onOpHeld4(pouch) { emptyPouch() }
            onEvent<HeldBanksideEvents.Type>(pouch.asRSCM(RSCMType.OBJ)) { banksideOp(player, op) }
        }
        onOpHeld3(RunePouches.DIVINE) { revertDivine(it.slot) }

        onIfModalButton(COMPONENT_POUCH) { pouchOp(it.comsub, it.op) }
        onIfModalButton(COMPONENT_INVENTORY) { inventoryOp(it.comsub, it.op) }
        onIfModalButton(COMPONENT_QUANTITY_1) { player.quantityMode = RunePouchQuantity.MODE_ONE }
        onIfModalButton(COMPONENT_QUANTITY_5) { player.quantityMode = RunePouchQuantity.MODE_FIVE }
        onIfModalButton(COMPONENT_QUANTITY_X) { selectCustomQuantity(it.op) }
        onIfModalButton(COMPONENT_QUANTITY_ALL) { player.quantityMode = RunePouchQuantity.MODE_ALL }

        onDestroyHeld {
            if (type in RunePouches.ALL) {
                spillRunes(player)
            }
        }
    }

    private fun ProtectedAccess.openPouch() {
        ifOpenMainModal(INTERFACE)
        ifSetEvents(COMPONENT_POUCH, 0 until RunePouches.DIVINE_SLOTS, *SLOT_EVENTS)
        ifSetEvents(COMPONENT_INVENTORY, inv.indices, *SLOT_EVENTS)
    }

    /** A rune in the inventory panel: store it. Non-runes only carry a `Store` op that refuses. */
    private suspend fun ProtectedAccess.inventoryOp(slot: Int, op: IfButtonOp) {
        if (op == IfButtonOp.Op10) {
            objExamine(inv, slot)
            return
        }
        val obj = inv[slot] ?: return
        val slots = player.runePouchSlots()
        if (slots == null) {
            ifClose()
            return
        }
        val rune = RunePouchRunes.byObj(obj.id)
        if (rune == null) {
            val message =
                if (obj.id in RunePouches.ids) "Don't be silly." else "You can only store runes in the pouch."
            mes(message)
            return
        }
        val amount = resolveAmount(op) ?: return
        val current = inv[slot]
        if (current == null || current.id != obj.id) {
            return
        }
        val contents = player.readRunePouch(slots)
        val carried = inv.objs.sumOf { if (it != null && it.id == obj.id) it.count else 0 }
        val stored = contents.add(rune.compactId, minOf(amount, carried))
        if (stored == 0) {
            val message =
                if (contents.slotOf(rune.compactId) != -1) {
                    "Your pouch can't hold any more of that rune."
                } else {
                    "Your rune pouch is full."
                }
            mes(message)
            return
        }
        val removed = invDel(inv, rune.name, count = stored)
        if (removed.success) {
            player.writeRunePouch(contents)
        }
    }

    /** A rune in the pouch panel: withdraw it. */
    private suspend fun ProtectedAccess.pouchOp(slot: Int, op: IfButtonOp) {
        val slots = player.runePouchSlots()
        if (slots == null) {
            ifClose()
            return
        }
        if (slot !in 0 until slots) {
            return
        }
        val contents = player.readRunePouch(slots)
        val rune = RunePouchRunes.byCompact(contents.types[slot]) ?: return
        if (contents.counts[slot] == 0) {
            return
        }
        if (op == IfButtonOp.Op10) {
            mes(rune.type.examine)
            return
        }
        val amount = resolveAmount(op) ?: return
        val latest = player.readRunePouch(slots)
        if (latest.types[slot] != rune.compactId || latest.counts[slot] == 0) {
            return
        }
        val take = minOf(amount, latest.counts[slot])
        val added = invAdd(inv, rune.name, count = take)
        if (added.failure) {
            mes("You don't have enough inventory space.")
            return
        }
        latest.remove(slot, take)
        player.writeRunePouch(latest)
    }

    private suspend fun ProtectedAccess.resolveAmount(op: IfButtonOp): Int? =
        when (val amount = RunePouchQuantity.resolve(op.ordinal + 1, player.quantityMode, player.customQuantity)) {
            is PouchAmount.Fixed -> amount.count
            PouchAmount.All -> Int.MAX_VALUE
            PouchAmount.Prompt -> promptQuantity()
            null -> null
        }

    /** Asks for a number, which also becomes the saved custom quantity shown on the `X` ops. */
    private suspend fun ProtectedAccess.promptQuantity(): Int? {
        val input = countDialog()
        if (input <= 0) {
            return null
        }
        player.customQuantity = input.coerceAtMost(RunePouchQuantity.MAX_CUSTOM)
        return input
    }

    /** Op1 selects the `X` quantity (asking for one if none is saved); op2 is "Set custom quantity". */
    private suspend fun ProtectedAccess.selectCustomQuantity(op: IfButtonOp) {
        if (op == IfButtonOp.Op2 || player.customQuantity == 0) {
            promptQuantity() ?: return
        }
        player.quantityMode = RunePouchQuantity.MODE_X
    }

    private fun ProtectedAccess.emptyPouch() {
        val slots = player.runePouchSlots() ?: return
        val contents = player.readRunePouch(slots)
        if (contents.isEmpty()) {
            mes("Your rune pouch is empty.")
            return
        }
        var partial = false
        for (slot in 0 until slots) {
            val count = contents.counts[slot]
            if (count == 0) {
                continue
            }
            val rune = RunePouchRunes.byCompact(contents.types[slot]) ?: continue
            val added = invAdd(inv, rune.name, count = count)
            if (added.success) {
                contents.remove(slot, count)
            } else {
                partial = true
            }
        }
        player.writeRunePouch(contents)
        if (partial) {
            mes("You don't have enough inventory space to empty the whole pouch.")
        }
    }

    /**
     * The bank draws `Empty` (op9) and `Configure` (op1) on a pouch in the bankside inventory.
     * Emptying puts the runes straight into the bank; configuring opens the pouch itself, which
     * replaces the bank modal.
     */
    private fun banksideOp(player: Player, op: IfButtonOp) {
        if (op == IfButtonOp.Op1) {
            protectedAccess.launch(player) { openPouch() }
            return
        }
        val slots = player.runePouchSlots() ?: return
        val contents = player.readRunePouch(slots)
        if (contents.isEmpty()) {
            player.mes("Your rune pouch is empty.")
            return
        }
        val bank = player.invMap.getOrPut("inv.bank")
        for (slot in 0 until slots) {
            val count = contents.counts[slot]
            if (count == 0) {
                continue
            }
            val rune = RunePouchRunes.byCompact(contents.types[slot]) ?: continue
            val added = player.invAdd(bank, rune.name, count)
            if (added.success) {
                contents.remove(slot, count)
            }
        }
        player.writeRunePouch(contents)
        player.mes("You empty the runes from your pouch into your bank.")
    }

    /** Reverting a divine pouch turns it back into a rune pouch; the fourth slot must be empty first. */
    private suspend fun ProtectedAccess.revertDivine(slot: Int) {
        val obj = inv[slot] ?: return
        val contents = player.readRunePouch(RunePouches.DIVINE_SLOTS)
        if (contents.counts[RunePouches.DIVINE_SLOTS - 1] > 0) {
            mes("You need to empty the fourth rune slot before reverting the pouch.")
            return
        }
        val confirm =
            choice2(
                "Yes, revert it to a rune pouch.",
                true,
                "No, keep it as it is.",
                false,
                title = "Revert the divine rune pouch?",
            )
        if (!confirm) {
            return
        }
        val current = inv[slot]
        if (current == null || current.id != obj.id) {
            return
        }
        val removed = invDel(inv, RunePouches.DIVINE, slot = slot)
        if (removed.failure) {
            return
        }
        invAdd(inv, RunePouches.REGULAR, slot = slot)
        mes("You revert the divine rune pouch into a regular rune pouch.")
    }

    /** "If you destroy the pouch, all the contents will fall out." */
    private fun spillRunes(player: Player) {
        val contents = player.readRunePouch(RunePouches.DIVINE_SLOTS)
        for (slot in 0 until contents.slots) {
            val count = contents.counts[slot]
            if (count == 0) {
                continue
            }
            val rune = RunePouchRunes.byCompact(contents.types[slot]) ?: continue
            val obj = Obj.fromOwner(player, player.coords, InvObj(rune.id, count))
            objRepo.add(obj, SPILL_DURATION)
        }
        contents.clear()
        player.writeRunePouch(contents)
    }

    private companion object {
        private const val INTERFACE = "interface.rune_pouch"
        private const val COMPONENT_POUCH = "component.rune_pouch:pouch"
        private const val COMPONENT_INVENTORY = "component.rune_pouch:inventory"
        private const val COMPONENT_QUANTITY_1 = "component.rune_pouch:1"
        private const val COMPONENT_QUANTITY_5 = "component.rune_pouch:5"
        private const val COMPONENT_QUANTITY_X = "component.rune_pouch:x"
        private const val COMPONENT_QUANTITY_ALL = "component.rune_pouch:all"

        /** Ticks the spilled runes of a destroyed pouch stay on the floor. */
        private const val SPILL_DURATION = 300

        private val SLOT_EVENTS =
            arrayOf(
                IfEvent.Op1,
                IfEvent.Op2,
                IfEvent.Op3,
                IfEvent.Op4,
                IfEvent.Op5,
                IfEvent.Op6,
                IfEvent.Op10,
            )
    }
}
