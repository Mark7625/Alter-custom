package org.rsmod.content.skills.hunter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Pins every rung and both sides of every boundary. The literals are the wiki's, not
 * [TrapLadder]'s - nothing here reads the function back as its own expected value. Pure
 * arithmetic, no cache, so not serialised with the rest of the suite.
 */
class TrapLadderTest {
    @Test
    fun `one trap below level 20`() {
        assertEquals(1, TrapLadder.cap(1))
        assertEquals(1, TrapLadder.cap(19))
    }

    @Test
    fun `two traps from level 20`() {
        assertEquals(2, TrapLadder.cap(20))
        assertEquals(2, TrapLadder.cap(39))
    }

    @Test
    fun `three traps from level 40`() {
        assertEquals(3, TrapLadder.cap(40))
        assertEquals(3, TrapLadder.cap(59))
    }

    @Test
    fun `four traps from level 60`() {
        assertEquals(4, TrapLadder.cap(60))
        assertEquals(4, TrapLadder.cap(79))
    }

    @Test
    fun `five traps from level 80, including level 99`() {
        assertEquals(5, TrapLadder.cap(80))
        assertEquals(5, TrapLadder.cap(99))
    }
}
