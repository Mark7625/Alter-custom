package org.rsmod.content.quest.area.lumbridge.lostcity

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.config.refs.params
import org.rsmod.api.player.righthand
import org.rsmod.api.player.stat.woodcuttingLvl
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.game.map.collision.isZoneValid
import org.rsmod.game.type.getInvObj
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

/* Helpers shared by the Lost City scripts. */

internal const val NO_AXE_MESSAGE =
    "You do not have an axe which you have the woodcutting level to use."

private const val AXE_CONTENT = "content.woodcutting_axe"

/**
 * The best axe the player is wielding or carrying that they have the Woodcutting level to use.
 * The regular woodcutting script cannot be reused here because its axe ranking needs the
 * success-rate params that the quest trees do not carry.
 */
internal fun Player.findWoodcuttingAxe(): InvObj? {
    val candidates = buildList {
        righthand?.let(::add)
        addAll(inv.filterNotNull { true })
    }
    return candidates
        .filter { obj ->
            val type = getInvObj(obj)
            type.isContentType(AXE_CONTENT) && woodcuttingLvl >= type.axeLevelReq()
        }
        .maxByOrNull { getInvObj(it).axeLevelReq() }
}

private fun dev.openrune.types.ItemServerType.axeLevelReq(): Int =
    paramOrNull(params.levelrequire) ?: 1

/** The chopping animation the axe's cache params assign to it. */
internal fun InvObj.woodcuttingAnim(): String {
    val seq = getInvObj(this).param(params.skill_anim)
    return RSCM.getReverseMapping(RSCMType.SEQ, seq.id)
}

/** The walkable tile closest to [center] within [radius], or null if there is none. */
internal fun CollisionFlagMap.nearestFree(center: CoordGrid, radius: Int = 2): CoordGrid? {
    val tiles = mutableListOf<CoordGrid>()
    for (dz in -radius..radius) {
        for (dx in -radius..radius) {
            tiles += center.translate(dx, dz)
        }
    }
    return tiles
        .sortedWith(compareBy({ it.chebyshevDistance(center) }, { it.z }, { it.x }))
        .firstOrNull(::walkable)
}

/**
 * The walkable tile touching [loc]'s footprint that is closest to [near], or null if the loc is
 * completely boxed in.
 */
internal fun CollisionFlagMap.freeTileBeside(loc: BoundLocInfo, near: CoordGrid): CoordGrid? {
    val width = loc.adjustedWidth
    val length = loc.adjustedLength
    val tiles = mutableListOf<CoordGrid>()
    for (dx in -1..width) {
        for (dz in -1..length) {
            val inside = dx in 0 until width && dz in 0 until length
            if (!inside) {
                tiles += loc.coords.translate(dx, dz)
            }
        }
    }
    return tiles.filter(::walkable).minByOrNull { it.chebyshevDistance(near) }
}

private fun CollisionFlagMap.walkable(coords: CoordGrid): Boolean =
    isZoneValid(coords) && !isWalkBlocked(coords)
