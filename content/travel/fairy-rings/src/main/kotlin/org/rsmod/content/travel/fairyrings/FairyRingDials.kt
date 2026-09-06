package org.rsmod.content.travel.fairyrings

/**
 * The three dials of the fairy ring interface.
 *
 * Each dial is a value from 0 to 3 held in `varbit.fairyring_1`, `varbit.fairyring_2` and
 * `varbit.fairyring_3`; rotating clockwise adds one and anticlockwise subtracts one. The letter a
 * value stands for follows the cache's `dbtable.fairyring`, whose `id` column is the three dial
 * values written as a base-ten number (`AIP` = 0, `AIS` = 1, `DIP` = 100, `BJQ` = 333), which
 * pins the order down as A D C B, I L K J and P S R Q.
 */
object FairyRingDials {
    const val COUNT: Int = 3
    const val POSITIONS: Int = 4

    private val LETTERS: List<List<Char>> =
        listOf(listOf('A', 'D', 'C', 'B'), listOf('I', 'L', 'K', 'J'), listOf('P', 'S', 'R', 'Q'))

    /** Turns three dial values into the code they spell, e.g. `(0, 0, 3)` into `"AIQ"`. */
    fun code(dials: List<Int>): String {
        require(dials.size == COUNT) { "Expected $COUNT dial values, got $dials." }
        return dials
            .mapIndexed { dial, value ->
                LETTERS[dial].getOrNull(value) ?: error("Dial ${dial + 1} cannot show $value.")
            }
            .joinToString("")
    }

    /** The dial values that spell [code], or `null` when [code] is not a fairy ring code. */
    fun dials(code: String): List<Int>? {
        val letters = code.uppercase().filter { it != ' ' }
        if (letters.length != COUNT) {
            return null
        }
        val dials = letters.mapIndexed { dial, letter -> LETTERS[dial].indexOf(letter) }
        return dials.takeIf { values -> values.none { it == -1 } }
    }

    /** The `dbcol.fairyring:id` value for the code spelt by [dials]. */
    fun tableId(dials: List<Int>): Int = dials.fold(0) { acc, value -> acc * 10 + value }

    /** Inverse of [tableId]. */
    fun fromTableId(id: Int): List<Int> = listOf(id / 100 % 10, id / 10 % 10, id % 10)

    /** Wraps a rotated dial value back into `0 until 4`. */
    fun rotate(value: Int, steps: Int): Int = Math.floorMod(value + steps, POSITIONS)
}
