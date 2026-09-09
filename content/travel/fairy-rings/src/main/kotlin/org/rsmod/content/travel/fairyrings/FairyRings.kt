package org.rsmod.content.travel.fairyrings

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.definition.constants.ConstantProvider
import org.rsmod.api.table.FairyringRow
import org.rsmod.map.CoordGrid

/**
 * One three-letter code and where it leads.
 *
 * @param code The letters without spaces, e.g. `"AIQ"`.
 * @param dials The dial values that spell [code].
 * @param logIndex The code's `multiloc_state` in the cache table, which is also what
 *   `varbit.fairyring_lastloc` stores for the ring's `Last-destination` op.
 * @param destination Where the code lands the player, or `null` for codes that go nowhere (the
 *   cache points those at a placeholder tile so the player "hardly moves at all").
 * @param description The cache's location text without its leading line break, e.g. `"Asgarnia:
 *   Mudskipper Point"`, or `null` for codes that go nowhere.
 * @param visitedVarBit The varbit set once the player has travelled to this ring, which is what
 *   lists the code in the travel log; `null` when the cache has no such varbit for the code.
 */
data class FairyRing(
    val code: String,
    val dials: List<Int>,
    val logIndex: Int,
    val destination: CoordGrid?,
    val description: String?,
    val visitedVarBit: String?,
) {
    /** The code the way the cache and the interface write it: `"A I Q"`. */
    val spacedCode: String = code.toCharArray().joinToString(" ")

    /** The travel log entry that lists this code (`Use code` / `Add Favourite`). */
    val logComponent: String = "component.fairyrings_log:${code.lowercase()}"

    /** The star next to the travel log entry (`Add Favourite`). */
    val favouriteIconComponent: String = "component.fairyrings_log:${code.lowercase()}_fave"

    /** The text shown for this code in the travel log and the favourites list. */
    val logText: String =
        buildString {
            append("<col=ff981f>").append(spacedCode).append("</col>")
            if (description != null) {
                append("<br>").append(description)
            }
        }
}

/** Every fairy ring code in the cache's `dbtable.fairyring`, keyed the ways the scripts need. */
class FairyRings private constructor(val all: List<FairyRing>) {
    private val byTableId: Map<Int, FairyRing> = all.associateBy { FairyRingDials.tableId(it.dials) }
    private val byCode: Map<String, FairyRing> = all.associateBy { it.code }
    private val byLogIndex: Map<Int, FairyRing> = all.associateBy { it.logIndex }

    fun byDials(dials: List<Int>): FairyRing? = byTableId[FairyRingDials.tableId(dials)]

    fun byCode(code: String): FairyRing? = byCode[code.uppercase().filter { it != ' ' }]

    fun byLogIndex(index: Int): FairyRing? = byLogIndex[index]

    companion object {
        private val logger = InlineLogger()

        /**
         * Codes that go nowhere point at this tile in the cache; the interface treats them as
         * "hardly moved at all".
         */
        private val NOWHERE = CoordGrid(65, 65, 1)

        fun load(): FairyRings {
            val varbits = ConstantProvider.mappings["varbit"].orEmpty()
            val rings =
                FairyringRow.all().map { row ->
                    val code = row.code.filter { it != ' ' }.uppercase()
                    val dials = FairyRingDials.fromTableId(row.id)
                    check(FairyRingDials.code(dials) == code) {
                        "Fairy ring row ${row.rowId} spells ${FairyRingDials.code(dials)} but is $code."
                    }
                    val destination =
                        row.destCoord.takeUnless { it == NOWHERE || it.packed == 0 }
                    val visitedVarBit =
                        "varbit.fairyrings_log_${code.lowercase()}".takeIf { it in varbits }
                    FairyRing(
                        code = code,
                        dials = dials,
                        logIndex = row.multilocState,
                        destination = destination,
                        description = row.desc?.removePrefix("<br>")?.trim()?.ifEmpty { null },
                        visitedVarBit = visitedVarBit,
                    )
                }
            logger.info {
                val reachable = rings.count { it.destination != null }
                "Loaded ${rings.size} fairy ring codes, $reachable with a destination: " +
                    rings
                        .filter { it.destination != null }
                        .joinToString { "${it.code}=${it.destination}" }
            }
            return FairyRings(rings)
        }
    }
}
