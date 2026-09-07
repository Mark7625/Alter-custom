package org.rsmod.content.other.emirsarena.duel

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.map.CoordGrid

class DuelArenaTest {
    @Test
    fun `the four arenas do not overlap and sit inside the arena complex`() {
        val arenas = DuelArena.ALL
        assertEquals(4, arenas.size)
        for (a in arenas) {
            for (b in arenas) {
                if (a === b) continue
                val overlapX = a.minX <= b.maxX && b.minX <= a.maxX
                val overlapZ = a.minZ <= b.maxZ && b.minZ <= a.maxZ
                assertTrue(!(overlapX && overlapZ), "${a.name} overlaps ${b.name}")
            }
            assertTrue(a.minX >= 3308 && a.maxX <= 3392, a.name)
            assertTrue(a.minZ >= 3200 && a.maxZ <= 3292, a.name)
        }
    }

    @Test
    fun `start positions face each other inside the arena`() {
        for (arena in DuelArena.ALL) {
            val (west, east) = arena.startPositions(noMovement = false)
            assertTrue(arena.contains(west) && arena.contains(east), arena.name)
            assertEquals(west.z, east.z)
            assertEquals(6, east.x - west.x)

            val (adjacentWest, adjacentEast) = arena.startPositions(noMovement = true)
            assertEquals(1, adjacentEast.x - adjacentWest.x)
            assertTrue(arena.contains(adjacentWest) && arena.contains(adjacentEast), arena.name)
        }
    }

    @Test
    fun `trapdoors hug the west and east walls`() {
        for (arena in DuelArena.ALL) {
            val (west, east) = arena.trapdoors
            assertEquals(arena.minX, west.x)
            assertEquals(arena.maxX, east.x)
            // A 1x3 loc spans three tiles north of its origin; all must be inside the arena.
            for (trapdoor in arena.trapdoors) {
                for (dz in 0 until 3) {
                    assertTrue(arena.contains(CoordGrid(trapdoor.x, trapdoor.z + dz, arena.level)), arena.name)
                }
            }
        }
    }

    @Test
    fun `lookup by coordinate`() {
        assertEquals("north-west", DuelArena.at(CoordGrid(3340, 3250, 0))?.name)
        assertEquals("south-east", DuelArena.at(CoordGrid(3380, 3210, 0))?.name)
        assertNull(DuelArena.at(CoordGrid(3360, 3230, 0)))
        assertNull(DuelArena.at(CoordGrid(3340, 3250, 1)))
    }
}
