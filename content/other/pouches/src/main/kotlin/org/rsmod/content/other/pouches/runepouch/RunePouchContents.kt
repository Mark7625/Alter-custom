package org.rsmod.content.other.pouches.runepouch

/**
 * The runes a pouch holds: one compact rune id (see `enum.rune_compact_ids`, `0` = empty) and a
 * count per slot. A regular pouch has three slots, a divine pouch four.
 *
 * This is a plain value so the storage rules can be tested without a player; the script reads and
 * writes it through the `rune_pouch_type_n` / `rune_pouch_quantity_n` varbits.
 */
class RunePouchContents(val types: IntArray, val counts: IntArray) {
    init {
        require(types.size == counts.size) { "types and counts must be the same size" }
    }

    val slots: Int
        get() = types.size

    fun isEmpty(): Boolean = counts.all { it == 0 }

    /** The slot already holding [compactId], or `-1`. */
    fun slotOf(compactId: Int): Int =
        types.indices.firstOrNull { types[it] == compactId && counts[it] > 0 } ?: -1

    fun freeSlot(): Int = types.indices.firstOrNull { counts[it] == 0 } ?: -1

    /**
     * Stores up to [amount] of [compactId], topping up an existing stack or taking a free slot.
     *
     * @return the amount actually stored; `0` when the pouch has no room for that rune.
     */
    fun add(compactId: Int, amount: Int): Int {
        if (amount <= 0) {
            return 0
        }
        var slot = slotOf(compactId)
        if (slot == -1) {
            slot = freeSlot()
        }
        if (slot == -1) {
            return 0
        }
        val stored = minOf(MAX_STACK - counts[slot], amount)
        if (stored <= 0) {
            return 0
        }
        types[slot] = compactId
        counts[slot] += stored
        return stored
    }

    /** Takes up to [amount] out of [slot], clearing the slot type once it is empty. */
    fun remove(slot: Int, amount: Int): Int {
        val removed = minOf(counts[slot], amount).coerceAtLeast(0)
        counts[slot] -= removed
        if (counts[slot] == 0) {
            types[slot] = 0
        }
        return removed
    }

    fun clear() {
        types.fill(0)
        counts.fill(0)
    }

    companion object {
        /** Each rune type caps at 16,000 in both the regular and divine pouch. */
        const val MAX_STACK: Int = 16_000

        fun empty(slots: Int): RunePouchContents = RunePouchContents(IntArray(slots), IntArray(slots))
    }
}

/** What a store/withdraw op on a pouch or inventory slot resolves to. */
sealed class PouchAmount {
    data class Fixed(val count: Int) : PouchAmount()

    data object All : PouchAmount()

    /** Ask the player for a number. */
    data object Prompt : PouchAmount()
}

/**
 * The quantity buttons of the rune pouch interface and how the client labels the slot ops for
 * each selection (`rune_pouch_drawinventory_slot` / `rune_pouch_drawpouch_slot`).
 *
 * Op1 is always the selected quantity; ops 2-6 are fixed to 1, 5, the saved custom amount, an X
 * prompt and All. Op10 is Examine.
 */
object RunePouchQuantity {
    const val MODE_ONE: Int = 0
    const val MODE_FIVE: Int = 1
    const val MODE_X: Int = 2
    const val MODE_ALL: Int = 3

    /** `rune_pouch_customquantity` is a 14-bit varbit. */
    const val MAX_CUSTOM: Int = 16_383

    const val OP_EXAMINE: Int = 10

    /** @param op the 1-based op the client sent; [mode] and [custom] are the two quantity varbits. */
    fun resolve(op: Int, mode: Int, custom: Int): PouchAmount? =
        when (op) {
            1 ->
                when (mode) {
                    MODE_FIVE -> PouchAmount.Fixed(5)
                    MODE_X -> customOrPrompt(custom)
                    MODE_ALL -> PouchAmount.All
                    else -> PouchAmount.Fixed(1)
                }
            2 -> PouchAmount.Fixed(1)
            3 -> PouchAmount.Fixed(5)
            4 -> customOrPrompt(custom)
            5 -> PouchAmount.Prompt
            6 -> PouchAmount.All
            else -> null
        }

    private fun customOrPrompt(custom: Int): PouchAmount =
        if (custom > 0) PouchAmount.Fixed(custom) else PouchAmount.Prompt
}
