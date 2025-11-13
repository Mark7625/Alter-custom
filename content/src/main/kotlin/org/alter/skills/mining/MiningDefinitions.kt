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

/**
 * Definitions for mining rocks and pickaxes.
 * Contains all mappings and data structures used by the MiningPlugin.
 * Rock data is loaded from cache tables for easy modification.
 */
object MiningDefinitions {
    /**
     * Rock data loaded from cache table.
     * Similar to Logs.LogData in firemaking.
     */
    data class RockData(
        val levelReq: Int,
        val xp: Double,
        val ore: Int,
        val respawnCycles: Int,
        val successRateLow: Int,
        val successRateHigh: Int,
        val despawnTicks: Int,
        val depleteMechanic: Int,
        val depletedRock: Int?,
        val clueBaseChance: Int,
        val rockType: String,
    ) {
        /**
         * Returns true if this rock uses a countdown timer before depletion.
         */
        fun usesCountdown(): Boolean = depleteMechanic == 1 && despawnTicks > 0

        /**
         * Returns true if this rock never depletes (until the inventory is full).
         */
        fun isInfiniteResource(): Boolean = depleteMechanic == 3
    }

    /**
     * Loads rock data from cache table.
     * Maps representative rock object IDs to their data.
     */
    fun tableToRockData(rockTable: DbHelper): RockData {
        val level = rockTable.column("columns.mining_rocks:level", IntType)
        val xp = rockTable.column("columns.mining_rocks:xp", IntType).toDouble()
        val oreItem = rockTable.column("columns.mining_rocks:ore_item", ObjType)
        val respawnCycles = rockTable.column("columns.mining_rocks:respawn_cycles", IntType)
        val successRateLow = rockTable.column("columns.mining_rocks:success_rate_low", IntType)
        val successRateHigh = rockTable.column("columns.mining_rocks:success_rate_high", IntType)
        val despawnTicks = rockTable.column("columns.mining_rocks:despawn_ticks", IntType)
        val depleteMechanic = rockTable.column("columns.mining_rocks:deplete_mechanic", IntType)
        val depletedRock = rockTable.columnOptional("columns.mining_rocks:empty_rock_object", LocType)
        val clueBaseChance = rockTable.columnOptional("columns.mining_rocks:clue_base_chance", IntType) ?: -1
        val rockType = rockTable.columnOptional("columns.mining_rocks:type", StringType)?.lowercase() ?: "rock"

        return RockData(
            levelReq = level,
            xp = xp,
            ore = oreItem,
            respawnCycles = respawnCycles,
            successRateLow = successRateLow,
            successRateHigh = successRateHigh,
            despawnTicks = despawnTicks,
            depleteMechanic = depleteMechanic,
            depletedRock = depletedRock,
            clueBaseChance = clueBaseChance,
            rockType = rockType,
        )
    }

    data class PickaxeData(
        val levelReq: Int,
        val tickDelay: Int,
        val animationId: Int,
        val wallAnimationId: Int?,
    )

    val pickaxeData: Map<Int, PickaxeData> = table("tables.mining_pickaxes").associate { row ->
        val pickaxeId = row.column("columns.mining_pickaxes:item", ObjType)
        val levelReq = row.column("columns.mining_pickaxes:level", IntType)
        val tickDelay = row.column("columns.mining_pickaxes:delay", IntType)
        val animationId = row.column("columns.mining_pickaxes:animation", SeqType)
        val wallAnimationId = row.columnOptional("columns.mining_pickaxes:wall_animation", SeqType)

        pickaxeId to PickaxeData(levelReq, tickDelay, animationId, wallAnimationId)
    }
}

