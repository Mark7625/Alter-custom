package org.rsmod.content.generic.locs.passages

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid

class PassagesTest {
    @Test
    fun `doors ladders caves and stiles are told apart by name and op`() {
        val ground = LocShape.CentrepieceStraight
        assertEquals(PassageAction.OpenDoor, Passages.classify("Door", "Open", LocShape.WallStraight))
        assertEquals(PassageAction.CloseDoor, Passages.classify("Gate", "Close", LocShape.WallStraight))
        assertEquals(PassageAction.OpenTrapdoor, Passages.classify("Trapdoor", "Open", ground))
        assertEquals(PassageAction.ClimbUp, Passages.classify("Ladder", "Climb-up", ground))
        assertEquals(PassageAction.ClimbDown, Passages.classify("Stairs", "Climb-down", ground))
        assertEquals(PassageAction.ClimbEither, Passages.classify("Staircase", "Climb", ground))
        assertEquals(PassageAction.Enter, Passages.classify("Cave entrance", "Enter", ground))
        assertEquals(PassageAction.ClimbOver, Passages.classify("Stile", "Climb-over", ground))
        assertNull(Passages.classify("Crate", "Search", ground))
        assertNull(Passages.classify("Tree", "Climb-up", ground))
    }

    @Test
    fun `climbing moves one level or between the surface and its dungeon`() {
        val ground = CoordGrid(3222, 3218, 0)
        assertEquals(CoordGrid(3222, 3218, 1), Passages.climbDestination(ground, up = true))
        assertEquals(CoordGrid(3222, 9618, 0), Passages.climbDestination(ground, up = false))
        val upstairs = CoordGrid(3222, 3218, 2)
        assertEquals(CoordGrid(3222, 3218, 1), Passages.climbDestination(upstairs, up = false))
        val dungeon = CoordGrid(3222, 9618, 0)
        assertEquals(ground, Passages.climbDestination(dungeon, up = true))
        assertNull(Passages.climbDestination(dungeon, up = false))
    }

    @Test
    fun `stiles and ditches are crossed to the tile past the far edge`() {
        // A 1x2 loc lying across z = 3521..3522, like the Wilderness ditch.
        val ditch =
            BoundLocInfo(
                coords = CoordGrid(3087, 3521),
                entity = LocEntity(23271, LocShape.CentrepieceStraight.id, LocAngle.West.id),
                layer = 2,
                width = 1,
                length = 2,
                forceApproachFlags = 0,
            )
        assertEquals(CoordGrid(3087, 3523), Passages.farSide(ditch, CoordGrid(3087, 3520)))
        assertEquals(CoordGrid(3087, 3520), Passages.farSide(ditch, CoordGrid(3087, 3523)))
        assertNull(Passages.farSide(ditch, CoordGrid(3087, 3521)))

        // The same loc turned a quarter, so it is 2 wide and 1 long.
        val turned =
            ditch.copy(entity = LocEntity(23271, LocShape.CentrepieceStraight.id, LocAngle.North.id))
        assertEquals(CoordGrid(3089, 3521), Passages.farSide(turned, CoordGrid(3086, 3521)))
    }

    @Test
    fun `landing candidates start at the destination and move outwards`() {
        val dest = CoordGrid(3200, 3200)
        val candidates = Passages.landingCandidates(dest, radius = 1)
        assertEquals(dest, candidates.first())
        assertEquals(9, candidates.size)
    }
}
