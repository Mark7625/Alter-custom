package org.rsmod.content.skills.construction

import dev.openrune.types.ObjectServerType
import org.rsmod.api.table.PohFurnitureRow

/** Lookup helpers over `dbtable.poh_furniture`. */
object PohFurniture {
    private val byHotspotLocId: Map<Int, List<PohFurnitureRow>> by lazy {
        val map = HashMap<Int, MutableList<PohFurnitureRow>>()
        for (row in PohFurnitureRow.all()) {
            for (hotspot in row.hotspotLoc) {
                map.getOrPut(hotspot.id) { mutableListOf() } += row
            }
        }
        map.mapValues { entry -> entry.value.sortedWith(compareBy({ it.level }, { it.xp })) }
    }

    private val byBuiltLocId: Map<Int, List<PohFurnitureRow>> by lazy {
        val map = HashMap<Int, MutableList<PohFurnitureRow>>()
        for (row in PohFurnitureRow.all()) {
            for (built in row.builtLoc) {
                map.getOrPut(built.id) { mutableListOf() } += row
            }
        }
        map
    }

    /** Build options for a hotspot loc, in menu order (level, then xp). */
    fun optionsFor(hotspotLocId: Int): List<PohFurnitureRow> =
        byHotspotLocId[hotspotLocId].orEmpty()

    /** Rows whose built furniture includes [builtLocId]. */
    fun rowsForBuilt(builtLocId: Int): List<PohFurnitureRow> = byBuiltLocId[builtLocId].orEmpty()

    /** The built loc this row yields on [hotspotLocId], via the aligned hotspot/built lists. */
    fun builtLocFor(row: PohFurnitureRow, hotspotLocId: Int): ObjectServerType? {
        val index = row.hotspotLoc.indexOfFirst { it.id == hotspotLocId }
        return if (index >= 0) row.builtLoc[index] else null
    }

    /** The hotspot loc this row's [builtLocId] reverts to on removal. */
    fun hotspotLocFor(row: PohFurnitureRow, builtLocId: Int): ObjectServerType? {
        val index = row.builtLoc.indexOfFirst { it.id == builtLocId }
        return if (index >= 0) row.hotspotLoc[index] else null
    }

    /** Every distinct built furniture loc across the table. */
    fun allBuiltLocs(): Set<String> =
        PohFurnitureRow.all().flatMap { row -> row.builtLoc.map { it.internalName } }.toSet()
}
