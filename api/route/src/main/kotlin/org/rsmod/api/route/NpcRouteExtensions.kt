package org.rsmod.api.route

import dev.openrune.types.MoveRestrict
import org.rsmod.game.entity.Npc
import org.rsmod.game.movement.MoveSpeed
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionStrategy

/**
 * Pathfinds a route from the npc's current position to [dest] (respecting the npc's size and
 * collision) and walks it, running [onArrival] once the route completes.
 *
 * Unlike [Npc.walk] with a raw [CoordGrid] destination - which queues the single tile and lets the
 * engine step naively straight toward it, wedging on any obstacle - this computes a full route via
 * [routeFactory] and feeds the waypoints in, so the npc navigates around walls, pillars and other
 * blocked tiles.
 *
 * The route is computed with `moveNear` enabled, so if [dest] itself is not standable (e.g. it is
 * occupied by a loc) the npc walks to the closest reachable tile and still fires [onArrival]. If no
 * route exists at all the npc does not move and [onArrival] fires on the next post-tick.
 *
 * When [passThroughEntities] is `true` (default), the npc's [Npc.moveRestrict] is set to
 * [MoveRestrict.PassThru] for the duration of the walk so it ignores players and npcs in its path
 * (walls/locs are still respected) and cannot be blocked/griefed by bodies standing in the way. The
 * original move restriction is restored automatically once the route completes.
 */
public fun Npc.walkTo(
    routeFactory: RouteFactory,
    dest: CoordGrid,
    collision: CollisionStrategy = CollisionStrategy.Normal,
    passThroughEntities: Boolean = true,
    speed: MoveSpeed = MoveSpeed.Walk,
    onArrival: (() -> Unit)? = null,
) {
    val route = routeFactory.create(avatar, dest, collision)
    val previousMoveRestrict = moveRestrict
    if (passThroughEntities) {
        moveRestrict = MoveRestrict.PassThru
    }
    walk(route.map { CoordGrid(it.x, it.z, it.level) }) {
        // Restore the original move restriction once the scripted walk finishes, before the caller's
        // arrival logic (which may re-engage combat) runs.
        moveRestrict = previousMoveRestrict
        onArrival?.invoke()
    }
    moveSpeed = speed
}
