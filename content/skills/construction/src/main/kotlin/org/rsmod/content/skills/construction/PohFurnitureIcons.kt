package org.rsmod.content.skills.construction

import dev.openrune.ServerCacheManager
import org.rsmod.api.table.PohFurnitureRow

/**
 * Resolves the icon obj shown for a build option in `interface.poh_furniture_creation`.
 *
 * Jagex ships a dedicated, untradeable icon obj for every buildable piece whose name matches the
 * menu name exactly and whose category is the POH furniture category 206 (e.g. "Firepit" = obj
 * 8216, which also carries the level requirement in its params). Those are authoritative; flatpack
 * objs cover flatpackables the icon set misses, and the first build material is the last-resort
 * fallback so the menu never renders an empty icon. Name matches outside category 206 are
 * deliberately not used - random same-named objs are exactly how icons end up wrong.
 */
object PohFurnitureIcons {
    private const val POH_FURNITURE_CATEGORY = 206

    private val iconsByName: Map<String, Int> by lazy {
        val icons = HashMap<String, Int>()
        val byExamine = HashMap<String, Int>()
        for (item in ServerCacheManager.getItemTypes()) {
            if (item.category != POH_FURNITURE_CATEGORY) {
                continue
            }
            val name = item.name.lowercase()
            if (name.isNotEmpty()) {
                icons.putIfAbsent(name, item.id)
            }
            // Some icon objs carry the menu name only in their examine (e.g. obj 8258 is named
            // "Spiral staircase" but examined "Limestone spiral staircase").
            val examine = item.examine?.lowercase().orEmpty()
            if (examine.isNotEmpty()) {
                byExamine.putIfAbsent(examine, item.id)
            }
        }
        for ((examine, id) in byExamine) {
            icons.putIfAbsent(examine, id)
        }
        icons
    }

    fun iconFor(row: PohFurnitureRow): Int =
        iconsByName[row.menuName.lowercase()]
            ?: row.flatpack?.id
            ?: row.material.firstOrNull()?.t0?.id
            ?: -1
}
