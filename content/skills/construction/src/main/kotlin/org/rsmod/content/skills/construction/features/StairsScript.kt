package org.rsmod.content.skills.construction.features

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.baseConstructionLvl
import org.rsmod.api.poh.PohConstants
import org.rsmod.api.poh.PohDataStore
import org.rsmod.api.poh.PohHotspotSlot
import org.rsmod.api.poh.PohManager
import org.rsmod.api.poh.PohRoom
import org.rsmod.api.poh.PohRoomSlot
import org.rsmod.api.poh.pohBuildingMode
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.content.skills.construction.PohStairs
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.map.collision.isZoneValid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * Built staircases, spiral staircases, dungeon ladders and throne-room trapdoors.
 *
 * Movement is a straight level translation at the player's tile: hall staircases
 * (`loc.poh_stairs_3..5` op1 `Climb-up`, `loc.poh_stairstop_3..5` op1 `Climb-down`) link the ground
 * and upper floors; spiral staircases (`loc.poh_spiralstairs`, `loc.poh_spiralstairs_2`, ops
 * `Climb`/`Climb-up`/`Climb-down`) and oubliette ladders (`loc.poh_dungeon_ladder_*`, op1 `Climb`)
 * resolve the combined `Climb` op to whichever direction has a valid destination zone, asking when
 * both are valid. Throne-room trapdoors (`loc.poh_trapdoor_{oak,teak,mag}_7`) open into their
 * `_open_7` variants, whose `Go-down` op descends into the dungeon (house level 0).
 *
 * Every move is guarded by `collision.isZoneValid` on the destination - the region builder only
 * allocates zones for floors that hold rooms, so climbing towards a missing floor reports "There is
 * nothing above/below." instead of teleporting into the void. Climb-up is additionally capped at
 * the upper floor so stairs can never lead onto the roof level.
 */
class StairsScript
@Inject
constructor(
    private val collision: CollisionFlagMap,
    private val locRepo: LocRepository,
    private val manager: PohManager,
    private val dataStore: PohDataStore,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for (stairs in STAIRCASES) {
            onOpLoc1(stairs) { climb(UP, connector = stairs) }
        }
        for (stairtop in STAIRCASE_TOPS) {
            onOpLoc1(stairtop) { climb(DOWN, connector = stairtop) }
        }
        for (spiral in SPIRAL_STAIRCASES) {
            onOpLoc1(spiral) { climbEither(connector = spiral) }
            onOpLoc2(spiral) { climb(UP, connector = spiral) }
            onOpLoc3(spiral) { climb(DOWN, connector = spiral) }
        }
        onOpLoc1(DUNGEON_ENTRANCE) { climb(DOWN, connector = DUNGEON_ENTRANCE) }
        for (ladder in DUNGEON_LADDERS) {
            onOpLoc1(ladder) { climbEither(ladder = true) }
        }
        for ((closed, open) in TRAPDOORS) {
            onOpLoc1(closed) { openTrapdoor(it.loc, open) }
            onOpLoc1(open) { descendTrapdoor() }
            onOpLoc2(open) { closeTrapdoor(it.loc, closed) }
        }
    }

    private suspend fun ProtectedAccess.climb(
        direction: Int,
        ladder: Boolean = false,
        connector: String? = null,
    ) {
        if (!canClimbTo(direction)) {
            if (!offerStairRoom(direction, ladder, connector)) {
                mes(if (direction > 0) "There is nothing above." else "There is nothing below.")
            }
            return
        }
        arriveDelay()
        if (ladder) {
            anim(if (direction > 0) LADDER_UP_ANIM else LADDER_DOWN_ANIM)
            delay(1)
        }
        telejump(player.coords.translateLevel(direction))
    }

    /**
     * Resolves a combined `Climb` op: single valid direction climbs immediately; both valid asks
     * which way; neither reports there is nowhere to go.
     */
    private suspend fun ProtectedAccess.climbEither(
        ladder: Boolean = false,
        connector: String? = null,
    ) {
        val canUp = canClimbTo(UP)
        val canDown = canClimbTo(DOWN)
        when {
            canUp && canDown -> {
                val up =
                    choice2(
                        "Climb up.",
                        true,
                        "Climb down.",
                        false,
                        title = "Climb up or down the ${if (ladder) "ladder" else "stairs"}?",
                    )
                climb(if (up) UP else DOWN, ladder, connector)
            }
            canUp -> climb(UP, ladder, connector)
            canDown -> climb(DOWN, ladder, connector)
            else -> mes("These ${if (ladder) "ladders" else "stairs"} don't lead anywhere.")
        }
    }

    /**
     * Zenyte `ClimbEmptyStaircaseD` (live-captured dialogue): climbing toward a missing room in
     * building mode offers to build the stair-linked room instead - halls above, halls or the
     * dungeon stairs room below. The new room copies the current room's rotation so the stairwells
     * line up, and per the wiki the matching connector is mirrored into it: spiral staircases
     * consume a second set of their materials, hall staircases continue for free as their stair-top
     * piece. Returns false when the offer doesn't apply and the plain "nothing above/below" message
     * should be shown.
     */
    private suspend fun ProtectedAccess.offerStairRoom(
        direction: Int,
        ladder: Boolean,
        connector: String?,
    ): Boolean {
        if (player.pohBuildingMode != 1) {
            return false
        }
        val destLevel = player.coords.level + direction
        if (destLevel < DUNGEON_LEVEL || destLevel > UPPER_LEVEL) {
            return false
        }
        val active = manager.activeHouse(player) ?: return false
        val slot = manager.roomSlotAt(active.region, player.coords) ?: return false
        val current = active.house.rooms[slot] ?: return false
        val target = PohRoomSlot(destLevel, slot.gridX, slot.gridZ)
        if (active.house.rooms.containsKey(target)) {
            return false
        }
        if (active.house.rooms.size >= PohConstants.maxRooms(player.baseConstructionLvl)) {
            mes("You can't build any more rooms at your Construction level.")
            return true
        }
        val options =
            if (direction == DOWN && destLevel == DUNGEON_LEVEL) STAIR_DOWN_ROOMS
            else STAIR_UP_ROOMS
        val end = if (direction == UP) "top" else "bottom"
        mesbox("These stairs do not lead anywhere. Do you want to build a room at the $end?")
        val labels = options.map { "${it.label} (${it.cost} coins) - level ${it.level}" } + "Cancel"
        val index = menu("Select an option", hotkeys = false, labels)
        val option = options.getOrNull(index) ?: return true
        if (player.baseConstructionLvl < option.level) {
            mes("You need a Construction level of ${option.level} to build that room.")
            return true
        }
        if (invTotal(inv, COINS) < option.cost) {
            mes("You can't afford to build that room.")
            return true
        }
        val mirror = PohStairs.MIRRORS[connector]
        if (mirror != null) {
            for ((material, count) in mirror.materials) {
                if (invTotal(inv, material) < count) {
                    mes("You don't have the materials to build the matching staircase.")
                    return true
                }
            }
        }
        invDel(inv, COINS, option.cost)
        mirror?.materials?.forEach { (material, count) -> invDel(inv, material, count) }
        manager.addRoom(player, target, PohRoom(option.roomKey, current.rotation))
        mes("You build a ${option.label.lowercase()}.")
        if (mirror != null) {
            val stairSpace =
                dataStore.hotspots(option.roomKey).firstOrNull {
                    it.name == PohStairs.STAIR_SPACE_NAME
                }
            if (stairSpace != null) {
                manager.applyFurniture(
                    player,
                    PohHotspotSlot(target.level, target.gridX, target.gridZ, stairSpace.index),
                    mirror.builtLoc,
                )
            }
        }
        climb(direction, ladder, connector)
        return true
    }

    private fun ProtectedAccess.canClimbTo(direction: Int): Boolean {
        // Check the level arithmetic before building the coordinate: CoordGrid rejects levels
        // outside [0..3], and climbing down at the dungeon level would otherwise throw (and
        // disconnect the player) instead of reporting there is nothing below.
        val destLevel = player.coords.level + direction
        if (destLevel < DUNGEON_LEVEL || destLevel > UPPER_LEVEL) {
            return false
        }
        val dest = player.coords.translateLevel(direction)
        return collision.isZoneValid(dest)
    }

    private suspend fun ProtectedAccess.openTrapdoor(trapdoor: BoundLocInfo, openLoc: String) {
        arriveDelay()
        soundSynth(DOOR_OPEN_SYNTH)
        locRepo.change(trapdoor, openLoc, TRAPDOOR_REVERT_TICKS)
    }

    private suspend fun ProtectedAccess.closeTrapdoor(trapdoor: BoundLocInfo, closedLoc: String) {
        arriveDelay()
        soundSynth(DOOR_CLOSE_SYNTH)
        locRepo.change(trapdoor, closedLoc, TRAPDOOR_REVERT_TICKS)
    }

    private suspend fun ProtectedAccess.descendTrapdoor() {
        if (!canClimbTo(DOWN)) {
            mes("There is nothing below.")
            return
        }
        arriveDelay()
        anim(TRAPDOOR_DOWN_ANIM)
        delay(1)
        telejump(player.coords.translateLevel(DOWN))
    }

    /** A stair-linked room offer: wiki level and cost gates, template key for the target floor. */
    private data class StairRoomOption(
        val label: String,
        val roomKey: String,
        val level: Int,
        val cost: Int,
    )

    private companion object {
        const val UP = 1

        const val COINS = "obj.coins"
        const val DOWN = -1

        /** House destination levels: 0 = dungeon, 1 = ground, 2 = upper floor; 3 is roof-only. */
        const val DUNGEON_LEVEL = 0
        const val UPPER_LEVEL = 2

        const val LADDER_UP_ANIM = "seq.human_reachforladder"
        const val LADDER_DOWN_ANIM = "seq.human_pickupfloor"
        const val TRAPDOOR_DOWN_ANIM = "seq.human_pickupfloor"
        const val DOOR_OPEN_SYNTH = "synth.door_open"
        const val DOOR_CLOSE_SYNTH = "synth.door_close"

        /** Ticks an opened/closed trapdoor overlay persists before reverting to the built loc. */
        const val TRAPDOOR_REVERT_TICKS = 100

        /** Hall staircases: oak / teak / marble bottom halves, op1 `Climb-up`. */
        val STAIRCASES = listOf("loc.poh_stairs_3", "loc.poh_stairs_4", "loc.poh_stairs_5")

        /** Matching upper-floor staircase tops, op1 `Climb-down`. */
        val STAIRCASE_TOPS =
            listOf("loc.poh_stairstop_3", "loc.poh_stairstop_4", "loc.poh_stairstop_5")

        /** Limestone and marble spiral staircases, ops `Climb`/`Climb-up`/`Climb-down`. */
        val SPIRAL_STAIRCASES = listOf("loc.poh_spiralstairs", "loc.poh_spiralstairs_2")

        /** Garden/formal-garden centrepiece "Dungeon entrance", op1 `Enter` (descends). */
        const val DUNGEON_ENTRANCE = "loc.poh_crude_garden_centrepiece5"

        /** Oubliette ladder-space builds, op1 `Climb`. */
        val DUNGEON_LADDERS =
            listOf(
                "loc.poh_dungeon_ladder_oak",
                "loc.poh_dungeon_ladder_teak",
                "loc.poh_dungeon_ladder_mag",
            )

        /** Throne-room trapdoors: closed built loc to its open runtime variant. */
        val TRAPDOORS =
            listOf(
                "loc.poh_trapdoor_oak_7" to "loc.poh_trapdoor_oak_open_7",
                "loc.poh_trapdoor_teak_7" to "loc.poh_trapdoor_teak_open_7",
                "loc.poh_trapdoor_mag_7" to "loc.poh_trapdoor_mag_open_7",
            )

        /** Rooms offered when ascending toward an empty slot (capture: "Skill hall|Quest hall"). */
        val STAIR_UP_ROOMS =
            listOf(
                StairRoomOption("Skill hall", "skill_hall_stairs_top", 25, 15_000),
                StairRoomOption("Quest hall", "quest_hall_stairs_top", 35, 25_000),
            )

        /** Rooms offered descending into the dungeon level (capture adds the stairs room). */
        val STAIR_DOWN_ROOMS =
            listOf(
                StairRoomOption("Skill hall", "skill_hall_stairs_up", 25, 15_000),
                StairRoomOption("Quest hall", "quest_hall_stairs_up", 35, 25_000),
                StairRoomOption("Dungeon stairs room", "dungeon_stairs", 70, 7_500),
            )
    }
}
