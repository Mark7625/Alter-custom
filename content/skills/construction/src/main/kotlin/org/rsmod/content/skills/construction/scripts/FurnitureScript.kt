package org.rsmod.content.skills.construction.scripts

import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.types.aconverted.interf.IfSubType
import jakarta.inject.Inject
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.righthand
import org.rsmod.api.player.stat.baseConstructionLvl
import org.rsmod.api.poh.PohDataStore
import org.rsmod.api.poh.PohFloor
import org.rsmod.api.poh.PohHotspotSlot
import org.rsmod.api.poh.PohHouse
import org.rsmod.api.poh.PohManager
import org.rsmod.api.poh.PohRoomSlot
import org.rsmod.api.poh.pohBuildingMode
import org.rsmod.api.script.onOpLoc5
import org.rsmod.api.table.PohFurnitureRow
import org.rsmod.content.skills.construction.PohFurniture
import org.rsmod.content.skills.construction.PohFurnitureIcons
import org.rsmod.content.skills.construction.PohStairs
import org.rsmod.game.entity.Player
import org.rsmod.game.stat.PlayerStatMap
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Building and removing furniture on hotspots.
 *
 * The build menu follows the captured `interface.poh_furniture_creation` wire format exactly:
 * `runclientscript 1404` per option, `runclientscript 1406 [count, false]`, pause-button events on
 * `component.poh_furniture_creation:contents` (458:2), then a `resume_pausebutton` reply carrying
 * the chosen slot.
 */
class FurnitureScript
@Inject
constructor(private val manager: PohManager, private val dataStore: PohDataStore) : PluginScript() {
    override fun ScriptContext.startup() {
        val hotspotLocs = dataStore.hotspots.map { it.loc }.distinct()
        for (hotspotLoc in hotspotLocs) {
            onOpLoc5(hotspotLoc) { onHotspotBuild(it.loc.coords, it.type.id) }
        }
        for (builtLoc in PohFurniture.allBuiltLocs()) {
            onOpLoc5(builtLoc) { onFurnitureRemove(it.loc.coords, it.type.id) }
        }
    }

    private suspend fun ProtectedAccess.onHotspotBuild(coords: CoordGrid, locId: Int) {
        if (player.pohBuildingMode != 1) {
            mes("You can only do that in building mode.")
            return
        }
        val (hotspotSlot, hotspot) = manager.findHotspot(player, coords, locId) ?: return
        val options = PohFurniture.optionsFor(locId)
        if (options.isEmpty()) {
            mes("You can't build anything there yet.")
            return
        }

        val chosen = openBuildMenu(options) ?: return

        // Stair connectors exist in pairs: mirroring into an empty linked stair space needs the
        // matching materials up front (wiki: "resources to build a matching staircase").
        val builtLoc = PohFurniture.builtLocFor(chosen, locId)?.internalName
        val mirrorTargets =
            if (builtLoc != null) linkedEmptyStairSpaces(hotspotSlot, builtLoc) else emptyList()
        val mirrorMaterials =
            if (mirrorTargets.isEmpty()) emptyList() else mirrorMaterials(builtLoc!!)

        if (player.baseConstructionLvl < chosen.level) {
            mes("You need a Construction level of ${chosen.level} to build this.")
            return
        }
        if (!player.hasHammerAndSaw()) {
            mes("You need a hammer and a saw to build furniture.")
            return
        }
        val nailType = selectNails(chosen.nails)
        if (!hasMaterials(chosen) || (chosen.nails > 0 && nailType == null)) {
            mes("You don't have enough materials to build that.")
            return
        }
        for ((material, count) in mirrorMaterials) {
            if (invTotal(inv, material) < count) {
                mes("You don't have the materials to build the matching staircase.")
                return
            }
        }

        playBuildAnim(hotspot.shape)
        delay(2)
        if (!driveNails(chosen.nails)) {
            mes("You've run out of nails.")
            return
        }
        consumeMaterials(chosen)
        mirrorMaterials.forEach { (material, count) -> invDel(inv, material, count) }

        statAdvance("stat.construction", chosen.xp / PlayerStatMap.XP_FINE_PRECISION.toDouble())
        applyBuild(chosen, hotspotSlot, locId)
        if (mirrorTargets.isNotEmpty()) {
            val mirror = PohStairs.MIRRORS.getValue(builtLoc!!).builtLoc
            for (target in mirrorTargets) {
                manager.applyFurniture(player, target, mirror)
            }
        }
    }

    private suspend fun ProtectedAccess.onFurnitureRemove(coords: CoordGrid, locId: Int) {
        if (player.pohBuildingMode != 1) {
            mes("You can only do that in building mode.")
            return
        }
        val (hotspotSlot, _) = manager.findBuiltFurniture(player, coords, locId) ?: return
        val house = manager.houseOf(player) ?: return
        val removedLoc = house.furniture[hotspotSlot]
        val row = PohFurniture.rowsForBuilt(locId).firstOrNull() ?: return

        val confirm = choice2("Yes.", true, "No.", false, title = "Really remove it?")
        if (!confirm) {
            return
        }

        anim(REMOVE_ANIM)
        delay(2)

        if (row.groupBuild) {
            val roomSlot = hotspotSlot.roomSlot
            val builtIds = row.builtLoc.map { it.id }.toSet()
            val grouped =
                house.furnitureAt(roomSlot).filterValues { locName ->
                    dev.openrune.rscm.RSCM.getRSCM(locName) in builtIds
                }
            for (slot in grouped.keys) {
                manager.removeFurniture(player, slot)
            }
        } else {
            manager.removeFurniture(player, hotspotSlot)
        }
        if (removedLoc != null) {
            removeLinkedStairs(house, hotspotSlot, removedLoc)
        }
    }

    /** Empty linked stair spaces a pair-building connector at [slot] would mirror into. */
    private fun ProtectedAccess.linkedEmptyStairSpaces(
        slot: PohHotspotSlot,
        builtLoc: String,
    ): List<PohHotspotSlot> {
        val house = manager.houseOf(player) ?: return emptyList()
        return buildList {
            for (direction in PohStairs.linkedDirections(builtLoc)) {
                val linked =
                    stairSpaceSlot(house, slot, slot.roomSlot.level + direction) ?: continue
                if (house.furniture[linked] == null) {
                    add(linked)
                }
            }
        }
    }

    private fun mirrorMaterials(builtLoc: String): List<Pair<String, Int>> =
        PohStairs.MIRRORS[builtLoc]?.materials.orEmpty()

    /**
     * Removes the other half of a stair connector, walking spiral chains across levels so no
     * orphaned staircase is left clipping through the floor of the room above or below.
     */
    private fun ProtectedAccess.removeLinkedStairs(
        house: PohHouse,
        slot: PohHotspotSlot,
        removedLoc: String,
    ) {
        for (direction in PohStairs.linkedDirections(removedLoc)) {
            var fromLoc = removedLoc
            var level = slot.roomSlot.level + direction
            while (true) {
                val expected = PohStairs.MIRRORS[fromLoc]?.builtLoc ?: break
                val linked = stairSpaceSlot(house, slot, level) ?: break
                val linkedLoc = house.furniture[linked] ?: break
                if (linkedLoc != expected) {
                    break
                }
                manager.removeFurniture(player, linked)
                if (direction !in PohStairs.linkedDirections(linkedLoc)) {
                    break
                }
                fromLoc = linkedLoc
                level += direction
            }
        }
    }

    /** The stair-space hotspot slot of the room at [level] in [slot]'s grid column, if any. */
    private fun stairSpaceSlot(house: PohHouse, slot: PohHotspotSlot, level: Int): PohHotspotSlot? {
        if (level !in PohFloor.DUNGEON.destLevel..PohFloor.UPPER.destLevel) {
            return null
        }
        val roomSlot = PohRoomSlot(level, slot.roomSlot.gridX, slot.roomSlot.gridZ)
        val room = house.rooms[roomSlot] ?: return null
        val hotspot =
            dataStore.hotspots(room.type).firstOrNull { it.name == PohStairs.STAIR_SPACE_NAME }
                ?: return null
        return PohHotspotSlot(level, roomSlot.gridX, roomSlot.gridZ, hotspot.index)
    }

    /** Opens the furniture creation menu and returns the chosen row, or `null` on dismissal. */
    private suspend fun ProtectedAccess.openBuildMenu(
        options: List<PohFurnitureRow>
    ): PohFurnitureRow? {
        ifOpenSub(
            "interface.poh_furniture_creation",
            "component.toplevel_osrs_stretch:mainmodal",
            IfSubType.Modal,
        )
        for ((index, row) in options.withIndex()) {
            val icon = PohFurnitureIcons.iconFor(row)
            runClientScript(
                ENTRY_CLIENTSCRIPT,
                index + 1,
                icon,
                row.level,
                menuText(row),
                if (canAfford(row)) 1 else 0,
            )
        }
        runClientScript(FINALIZE_CLIENTSCRIPT, options.size, 0)
        ifSetEvents(
            "component.poh_furniture_creation:contents",
            1..options.size,
            IfEvent.PauseButton,
        )
        val input = coroutine.pause(ResumePauseButtonInput::class)
        ifClose()
        val slot = input.subcomponent
        if (slot !in 1..options.size) {
            return null
        }
        return options[slot - 1]
    }

    private fun menuText(row: PohFurnitureRow): String {
        val materials = buildList {
            for (material in row.material) {
                add("${material.t1} x ${material.t0.name}")
            }
            if (row.nails > 0) {
                add("${row.nails} x Nails")
            }
        }
        return "${row.menuName}|Materials: ${materials.joinToString("<br>")}"
    }

    private fun ProtectedAccess.canAfford(row: PohFurnitureRow): Boolean =
        hasMaterials(row) && (row.nails == 0 || selectNails(row.nails) != null)

    private fun ProtectedAccess.hasMaterials(row: PohFurnitureRow): Boolean =
        row.material.all { invTotal(inv, it.t0.internalName) >= it.t1 }

    /** Picks the cheapest nail type the player has enough of, or `null`. */
    private fun ProtectedAccess.selectNails(count: Int): String? {
        if (count == 0) {
            return null
        }
        return NAIL_TYPES.firstOrNull { invTotal(inv, it) >= count }
    }

    /**
     * Drives [required] nails, consuming one per attempt with a per-metal chance of bending it
     * (wasting the nail without progress). Falls through to the next nail type in the player's
     * inventory when one stock runs dry; returns false when every nail is spent before the
     * furniture is secured.
     */
    private fun ProtectedAccess.driveNails(required: Int): Boolean {
        var driven = 0
        while (driven < required) {
            val type = NAIL_TYPES.firstOrNull { invTotal(inv, it) > 0 } ?: return false
            invDel(inv, type, 1)
            if (random.of(100) < NAIL_BEND.getValue(type)) {
                mes("You accidentally bend a nail!")
            } else {
                driven++
            }
        }
        return true
    }

    private fun ProtectedAccess.consumeMaterials(row: PohFurnitureRow) {
        for (material in row.material) {
            invDel(inv, material.t0.internalName, material.t1)
        }
    }

    private fun ProtectedAccess.playBuildAnim(hotspotShape: Int) {
        val imcando = player.hasImcandoHammer()
        val anim =
            when {
                hotspotShape in WALL_SHAPES ->
                    if (imcando) "seq.human_poh_build_wall_imcando_hammer"
                    else "seq.human_poh_build_wall"
                hotspotShape == GROUND_DECOR_SHAPE ->
                    if (imcando) "seq.human_poh_build_floor_imcando_hammer"
                    else "seq.human_poh_build_floor"
                else -> if (imcando) "seq.human_poh_build_imcando_hammer" else "seq.human_poh_build"
            }
        anim(anim)
    }

    private fun ProtectedAccess.applyBuild(
        row: PohFurnitureRow,
        hotspotSlot: PohHotspotSlot,
        clickedLocId: Int,
    ) {
        if (row.groupBuild) {
            val house = manager.houseOf(player) ?: return
            val roomSlot = hotspotSlot.roomSlot
            val room = house.rooms[roomSlot] ?: return
            val hotspotLocIds = row.hotspotLoc.map { it.id }
            for (hotspot in dataStore.hotspots(room.type)) {
                val locId = dev.openrune.rscm.RSCM.getRSCM(hotspot.loc)
                if (locId !in hotspotLocIds) {
                    continue
                }
                val built = PohFurniture.builtLocFor(row, locId) ?: continue
                val slot =
                    PohHotspotSlot(roomSlot.level, roomSlot.gridX, roomSlot.gridZ, hotspot.index)
                manager.applyFurniture(player, slot, built.internalName)
            }
        } else {
            val built = PohFurniture.builtLocFor(row, clickedLocId) ?: return
            manager.applyFurniture(player, hotspotSlot, built.internalName)
        }
    }

    private fun Player.hasHammerAndSaw(): Boolean {
        val hammer = invContainsAny(HAMMERS) || hasImcandoHammer()
        val saw = invContainsAny(SAWS)
        return hammer && saw
    }

    private fun Player.hasImcandoHammer(): Boolean = invContainsAny(listOf(IMCANDO_HAMMER))

    private fun Player.invContainsAny(types: List<String>): Boolean {
        val ids = types.map { dev.openrune.rscm.RSCM.getRSCM(it) }
        val inInv = inv.any { it != null && it.id in ids }
        val worn = righthand?.id in ids
        return inInv || worn
    }

    private companion object {
        const val ENTRY_CLIENTSCRIPT = 1404
        const val FINALIZE_CLIENTSCRIPT = 1406

        const val IMCANDO_HAMMER = "obj.imcando_hammer"
        val HAMMERS = listOf("obj.hammer")
        val SAWS = listOf("obj.poh_saw", "obj.eyeglo_crystal_saw")

        /** Nail types in cost order; the cheapest available stock is driven first. */
        val NAIL_TYPES =
            listOf(
                "obj.nails_bronze",
                "obj.nails_iron",
                "obj.nails_steel",
                "obj.nails_black",
                "obj.nails_mithril",
                "obj.nails_adamant",
                "obj.nails_rune",
            )

        /**
         * Chance (percent) of bending a nail per drive attempt, by metal. Zenyte's approximation
         * (best metals bend least); wiki-exact per-level rates are unpublished.
         */
        val NAIL_BEND =
            mapOf(
                "obj.nails_bronze" to 60,
                "obj.nails_iron" to 50,
                "obj.nails_steel" to 40,
                "obj.nails_black" to 30,
                "obj.nails_mithril" to 20,
                "obj.nails_adamant" to 10,
                "obj.nails_rune" to 5,
            )

        const val REMOVE_ANIM = "seq.human_throw_away"

        val WALL_SHAPES = 0..9
        const val GROUND_DECOR_SHAPE = 22
    }
}
