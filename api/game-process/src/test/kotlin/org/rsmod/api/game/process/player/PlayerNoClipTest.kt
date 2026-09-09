package org.rsmod.api.game.process.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.player.cheat.adminNoClip
import org.rsmod.api.route.RouteFactory
import org.rsmod.api.route.StepFactory
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.movement.MoveSpeed
import org.rsmod.game.movement.RouteRequestCoord
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

/**
 * Covers the admin "no clip" cheat in [PlayerMovementProcessor].
 *
 * The map is a block of allocated zones with a solid column of blocked tiles down the middle.
 * Everything outside the allocated zones reads as [CollisionFlagMap.DEFAULT_COLLISION_FLAG] - every
 * bit set - so there is no way around the wall and the destination is genuinely unreachable by
 * pathfinding.
 */
class PlayerNoClipTest {
    @Test
    fun `player is stopped by a wall without no clip`() {
        val collision = walledMap()
        val movement = processor(collision)
        val player = playerAt(START)

        player.walkTo(movement, DESTINATION)

        assertNotEquals(DESTINATION, player.coords, "Player should not have crossed the wall.")
        assertTrue(
            player.coords.x < WALL_X,
            "Player should have stopped west of the wall, but is at ${player.coords}.",
        )
    }

    @Test
    fun `player walks through a wall with no clip`() {
        val collision = walledMap()
        val movement = processor(collision)
        val player = playerAt(START)
        player.adminNoClip = true

        player.walkTo(movement, DESTINATION)

        assertEquals(DESTINATION, player.coords, "No clip should have walked through the wall.")
    }

    /**
     * No clip only takes over when pathfinding cannot reach the destination. An ordinary walk on an
     * open map must still work, since that is the path loc and npc interactions rely on.
     */
    @Test
    fun `no clip does not disturb a reachable destination`() {
        val collision = openMap()
        val movement = processor(collision)
        val player = playerAt(START)
        player.adminNoClip = true

        player.walkTo(movement, DESTINATION)

        assertEquals(DESTINATION, player.coords)
    }

    @Test
    fun `no clip can be switched back off`() {
        val collision = walledMap()
        val movement = processor(collision)
        val player = playerAt(START)

        player.adminNoClip = true
        player.walkTo(movement, DESTINATION)
        assertEquals(DESTINATION, player.coords)

        player.adminNoClip = false
        player.walkTo(movement, START)
        assertNotEquals(START, player.coords, "The wall should block the way back again.")
    }

    private fun processor(collision: CollisionFlagMap) =
        PlayerMovementProcessor(
            collision,
            RouteFactory(collision),
            StepFactory(collision),
            EventBus(),
        )

    private fun playerAt(coords: CoordGrid): Player =
        Player().apply {
            this.coords = coords
            // Pinned so the processor never reads `varMoveSpeed`, which would need a loaded cache.
            tempMoveSpeed = MoveSpeed.Walk
        }

    /** Issues a route request and runs enough cycles for the walk to finish. */
    private fun Player.walkTo(movement: PlayerMovementProcessor, destination: CoordGrid) {
        tempMoveSpeed = MoveSpeed.Walk
        routeRequest = RouteRequestCoord(destination)
        repeat(CYCLES) {
            previousCoords = coords
            currentMapClock++
            processedMapClock++
            movement.process(this)
        }
    }

    private fun openMap(): CollisionFlagMap {
        val collision = CollisionFlagMap()
        for (x in 0 until SPAN step ZONE_SIZE) {
            for (z in 0 until SPAN step ZONE_SIZE) {
                collision.allocateIfAbsent(x, z, 0)
            }
        }
        return collision
    }

    private fun walledMap(): CollisionFlagMap {
        val collision = openMap()
        for (z in 0 until SPAN) {
            collision.add(WALL_X, z, 0, CollisionFlag.BLOCK_WALK)
        }
        return collision
    }

    private companion object {
        const val ZONE_SIZE = 8
        const val SPAN = 32
        const val WALL_X = 12
        const val CYCLES = 40

        val START = CoordGrid(8, 16, 0)
        val DESTINATION = CoordGrid(16, 16, 0)
    }
}
