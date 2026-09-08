package org.rsmod.content.skills.hunter

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.table.hunter.HunterBoxCreaturesRow
import org.rsmod.api.table.hunter.HunterSnareCreaturesRow

/**
 * The creature tables read back from the packed dbtables; values and provenance live in
 * `HunterTables.kt` and docs/hunter.md. XP passes through unscaled (stored x10).
 */
object HunterCreatures {
    /**
     * **Append only.** A sprung trap persists its creature as an index into this list, so a new
     * row has to land after every row already in it. Sorted by dbrow id across **all tables at
     * once**, not per table and concatenated - global order is what makes "give a new row an id
     * above everything" the entire rule (docs/hunter.md).
     */
    val all: List<HunterCreature> by lazy {
        val rows =
            HunterSnareCreaturesRow.all().map { it.rowId to snare(it) } +
                HunterBoxCreaturesRow.all().map { it.rowId to box(it) }
        rows.sortedBy { it.first }.map { it.second }
    }

    private val byNpc: Map<String, HunterCreature> by lazy { all.associateBy { it.npc } }

    // `RSCM.getReverseMapping` is an unmemoised linear scan - far too slow for the trap tick.
    private val byNpcId: Map<Int, HunterCreature> by lazy {
        all.associateBy { it.npc.asRSCM(RSCMType.NPC) }
    }

    fun byNpc(npc: String): HunterCreature? = byNpc[npc]

    fun byNpcId(npc: Int): HunterCreature? = byNpcId[npc]

    private fun snare(row: HunterSnareCreaturesRow): HunterCreature =
        HunterCreature(
            family = TrapFamily.SNARE,
            npc = row.npc.internalName,
            level = row.level,
            xp = row.xp,
            caught =
                parallelCatches(
                    row.rowId,
                    row.caughtItems.map { it.internalName },
                    row.caughtMin,
                    row.caughtMax,
                ),
            successLow = row.successLow,
            successHigh = row.successHigh,
            locKey = row.locKey,
        )

    private fun box(row: HunterBoxCreaturesRow): HunterCreature =
        HunterCreature(
            family = TrapFamily.BOX,
            npc = row.npc.internalName,
            level = row.level,
            xp = row.xp,
            caught =
                listOf(HunterCatch(row.caughtItems.internalName, row.caughtMin..row.caughtMax)),
            successLow = row.successLow,
            successHigh = row.successHigh,
            locKey = row.locKey,
        )
}
