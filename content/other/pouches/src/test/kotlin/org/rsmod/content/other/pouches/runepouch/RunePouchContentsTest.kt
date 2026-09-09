package org.rsmod.content.other.pouches.runepouch

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RunePouchContentsTest {
    @Test
    fun `a new rune takes the first free slot`() {
        val pouch = RunePouchContents.empty(3)

        assertEquals(100, pouch.add(AIR, 100))
        assertEquals(50, pouch.add(FIRE, 50))

        assertArrayEquals(intArrayOf(AIR, FIRE, 0), pouch.types)
        assertArrayEquals(intArrayOf(100, 50, 0), pouch.counts)
    }

    @Test
    fun `an existing stack is topped up instead of using a new slot`() {
        val pouch = RunePouchContents.empty(3)
        pouch.add(AIR, 100)

        assertEquals(25, pouch.add(AIR, 25))

        assertEquals(0, pouch.slotOf(AIR))
        assertEquals(125, pouch.counts[0])
        assertEquals(1, pouch.freeSlot())
    }

    @Test
    fun `stacks cap at the maximum and report what fit`() {
        val pouch = RunePouchContents.empty(3)
        pouch.add(AIR, RunePouchContents.MAX_STACK - 10)

        assertEquals(10, pouch.add(AIR, 500))
        assertEquals(RunePouchContents.MAX_STACK, pouch.counts[0])
        assertEquals(0, pouch.add(AIR, 1))
    }

    @Test
    fun `a full pouch refuses a fourth rune type`() {
        val pouch = RunePouchContents.empty(3)
        pouch.add(AIR, 1)
        pouch.add(FIRE, 1)
        pouch.add(WATER, 1)

        assertEquals(0, pouch.add(EARTH, 1))
        assertEquals(-1, pouch.freeSlot())
    }

    @Test
    fun `the divine pouch has a fourth slot`() {
        val pouch = RunePouchContents.empty(4)
        pouch.add(AIR, 1)
        pouch.add(FIRE, 1)
        pouch.add(WATER, 1)

        assertEquals(1, pouch.add(EARTH, 1))
        assertEquals(3, pouch.slotOf(EARTH))
    }

    @Test
    fun `removing the last rune frees the slot type`() {
        val pouch = RunePouchContents.empty(3)
        pouch.add(AIR, 10)

        assertEquals(4, pouch.remove(0, 4))
        assertEquals(AIR, pouch.types[0])
        assertEquals(6, pouch.remove(0, 100))
        assertEquals(0, pouch.types[0])
        assertTrue(pouch.isEmpty())
    }

    @Test
    fun `op1 follows the selected quantity button`() {
        assertEquals(PouchAmount.Fixed(1), RunePouchQuantity.resolve(1, RunePouchQuantity.MODE_ONE, 0))
        assertEquals(PouchAmount.Fixed(5), RunePouchQuantity.resolve(1, RunePouchQuantity.MODE_FIVE, 0))
        assertEquals(PouchAmount.Fixed(40), RunePouchQuantity.resolve(1, RunePouchQuantity.MODE_X, 40))
        assertEquals(PouchAmount.Prompt, RunePouchQuantity.resolve(1, RunePouchQuantity.MODE_X, 0))
        assertEquals(PouchAmount.All, RunePouchQuantity.resolve(1, RunePouchQuantity.MODE_ALL, 0))
    }

    @Test
    fun `the remaining ops are fixed regardless of the selected button`() {
        for (mode in RunePouchQuantity.MODE_ONE..RunePouchQuantity.MODE_ALL) {
            assertEquals(PouchAmount.Fixed(1), RunePouchQuantity.resolve(2, mode, 7))
            assertEquals(PouchAmount.Fixed(5), RunePouchQuantity.resolve(3, mode, 7))
            assertEquals(PouchAmount.Fixed(7), RunePouchQuantity.resolve(4, mode, 7))
            assertEquals(PouchAmount.Prompt, RunePouchQuantity.resolve(4, mode, 0))
            assertEquals(PouchAmount.Prompt, RunePouchQuantity.resolve(5, mode, 7))
            assertEquals(PouchAmount.All, RunePouchQuantity.resolve(6, mode, 7))
        }
        assertNull(RunePouchQuantity.resolve(RunePouchQuantity.OP_EXAMINE, RunePouchQuantity.MODE_ONE, 0))
    }

    private companion object {
        /* Compact ids from `enum.rune_compact_ids`. */
        const val AIR = 1
        const val WATER = 2
        const val EARTH = 3
        const val FIRE = 4
    }
}
