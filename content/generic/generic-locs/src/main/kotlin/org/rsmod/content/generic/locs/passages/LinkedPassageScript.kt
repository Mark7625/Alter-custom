package org.rsmod.content.generic.locs.passages

import jakarta.inject.Inject
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.game.map.collision.isZoneValid
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * One end of a [LinkedPassage]: the loc the player clicks and where a player arriving from the
 * other end is put. With no [landing] the arrival tile is the nearest free tile to the loc,
 * which is fine on the surface but not at a cave mouth, where the tile behind the opening is
 * unflagged black filler; those ends name their floor tile explicitly.
 */
data class PassageEnd(val loc: String, val coords: CoordGrid, val landing: CoordGrid? = null)

/**
 * A two-way passage whose ends do not sit 6400 tiles apart, so the generic surface/dungeon rule
 * cannot find the far side.
 */
data class LinkedPassage(val name: String, val a: PassageEnd, val b: PassageEnd)

/** Passages whose far side had to be read from the map rather than worked out. */
object LinkedPassages {
    val all: List<LinkedPassage> =
        listOf(
            LinkedPassage(
                name = "Wilderness Slayer Cave (south)",
                a = PassageEnd("loc.wild_slayer_cave_south_entrance", CoordGrid(3259, 3664)),
                b =
                    PassageEnd(
                        "loc.wild_slayer_cave_south_exit",
                        CoordGrid(3384, 10050),
                        landing = CoordGrid(3385, 10052),
                    ),
            ),
            LinkedPassage(
                name = "Wilderness Slayer Cave (north)",
                a = PassageEnd("loc.wild_slayer_cave_north_entrance", CoordGrid(3293, 3746)),
                b =
                    PassageEnd(
                        "loc.wild_slayer_cave_north_exit",
                        CoordGrid(3405, 10146),
                        landing = CoordGrid(3407, 10145),
                    ),
            ),
        )
}

class LinkedPassageScript @Inject constructor(private val collision: CollisionFlagMap) :
    PluginScript() {
    override fun ScriptContext.startup() {
        for (passage in LinkedPassages.all) {
            onOpLoc1(passage.a.loc) { travel(passage.b) }
            onOpLoc1(passage.b.loc) { travel(passage.a) }
        }
    }

    private suspend fun ProtectedAccess.travel(end: PassageEnd) {
        arriveDelay()
        val dest =
            end.landing
                ?: Passages.landingCandidates(end.coords).firstOrNull {
                    collision.isZoneValid(it) && !collision.isWalkBlocked(it)
                }
        if (dest == null) {
            mes("You cannot see a way through.")
            return
        }
        delay(1)
        telejump(dest, TeleportType.Exempt)
    }
}
