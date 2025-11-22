package org.alter.skills.mining

import org.alter.game.util.DbHelper
import org.alter.game.util.DbHelper.Companion.table
import org.alter.game.util.column
import org.alter.game.util.columnOptional
import org.alter.game.util.vars.IntType
import org.alter.game.util.vars.LocType
import org.alter.game.util.vars.ObjType
import org.alter.game.util.vars.SeqType
import org.alter.game.util.vars.StringType
import org.generated.tables.mining.MiningPickaxesRow
import org.generated.tables.mining.MiningRocksRow

/**
 * Definitions for mining rocks and pickaxes.
 * Contains all mappings and data structures used by the MiningPlugin.
 * Rock data is loaded from cache tables for easy modification.
 */
object MiningDefinitions {

    /**
     * Returns true if this rock never depletes (until the inventory is full).
     */
    fun MiningRocksRow.isInfiniteResource(): Boolean = depleteMechanic == 3

    /**
     * Computes the depletion range for rocks that use mechanic 2, defaulting to a single
     * ore for other mechanics so definitions don't need to populate the new columns.
     */
    fun MiningRocksRow.getDepletionRange(): IntRange {
        if (depleteMechanic != 2) {
            return 1..1
        }

        val minAmount = depleteMinAmount?.toInt() ?: 1
        val maxAmount = depleteMaxAmount?.toInt() ?: minAmount
        val (min, max) = if (maxAmount < minAmount) minAmount to minAmount else minAmount to maxAmount

        return min..max
    }

    val pickaxeData: List<MiningPickaxesRow> = MiningPickaxesRow.all()

    val miningRocks: List<MiningRocksRow> = MiningRocksRow.all()
}

