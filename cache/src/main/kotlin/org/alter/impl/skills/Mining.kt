package org.alter.impl.skills

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType

object Mining {

    const val COL_ROCK_OBJECT = 0
    const val COL_LEVEL = 1
    const val COL_XP = 2
    const val COL_ORE_ITEM = 3
    const val COL_RESPAWN_CYCLES = 4
    const val COL_SUCCESS_RATE_LOW = 5
    const val COL_SUCCESS_RATE_HIGH = 6
    const val COL_DESPAWN_TICKS = 7
    const val COL_DEPLETE_MECHANIC = 8
    const val COL_EMPTY_ROCK = 9
    const val CLUE_BASE_CHANCE = 10
    const val COL_TYPE = 11

    data class AnimData(
        val animation: String,
        val wallAnimation: String?,
        val dbrow: String
    )

    val PICKAXE_DATA = mapOf(
        "items.bronze_pickaxe" to Triple(1, 8, Triple("sequences.human_mining_bronze_pickaxe", "sequences.human_mining_bronze_pickaxe_wall", "dbrows.mining_bronze_pickaxe")),
        "items.iron_pickaxe" to Triple(1, 7, Triple("sequences.human_mining_iron_pickaxe", "sequences.human_mining_iron_pickaxe_wall","dbrows.mining_iron_pickaxe")),
        "items.steel_pickaxe" to Triple(6, 6, Triple("sequences.human_mining_steel_pickaxe", "sequences.human_mining_steel_pickaxe_wall", "dbrows.mining_steel_pickaxe")),
        "items.black_pickaxe" to Triple(11, 5, Triple("sequences.human_mining_black_pickaxe", "sequences.human_mining_black_pickaxe_wall", "dbrows.mining_black_pickaxe")),
        "items.mithril_pickaxe" to Triple(21, 5, Triple("sequences.human_mining_mithril_pickaxe","sequences.human_mining_mithril_pickaxe_wall",  "dbrows.mining_mithril_pickaxe")),
        "items.adamant_pickaxe" to Triple(31, 4, Triple("sequences.human_mining_adamant_pickaxe","sequences.human_mining_adamant_pickaxe_wall", "dbrows.mining_adamant_pickaxe")),
        "items.rune_pickaxe" to Triple(41, 3, Triple("sequences.human_mining_rune_pickaxe", "sequences.human_mining_rune_pickaxe_wall", "dbrows.mining_rune_pickaxe")),
        "items.dragon_pickaxe" to Triple(61, 2, Triple("sequences.human_mining_dragon_pickaxe", "sequences.human_mining_dragon_pickaxe_wall", "dbrows.mining_dragon_pickaxe")),
        "items.3a_pickaxe" to Triple(61, 2, Triple("sequences.human_mining_3a_pickaxe", "sequences.human_mining_3a_pickaxe_wall", "dbrows.mining_3a_pickaxe")),
        "items.infernal_pickaxe" to Triple(61, 2, Triple("sequences.human_mining_infernal_pickaxe", "sequences.human_mining_infernal_pickaxe_wall", "dbrows.mining_infernal_pickaxe")),
        "items.crystal_pickaxe" to Triple(71, 2, Triple("sequences.human_mining_crystal_pickaxe", "sequences.human_mining_crystal_pickaxe_wall", "dbrows.mining_crystal_pickaxe"))
    )

    const val ITEM = 0
    const val LEVEL = 1
    const val DELAY = 2
    const val ANIMATION = 3
    const val WALL_ANIMATION = 4


    fun pickaxes() = dbTable("tables.mining_pickaxes") {
        column("item", ITEM, VarType.OBJ)
        column("level", LEVEL, VarType.INT)
        column("delay", DELAY, VarType.INT)
        column("animation", ANIMATION, VarType.SEQ)
        column("wall_animation", WALL_ANIMATION, VarType.SEQ)

        PICKAXE_DATA.forEach { (item, data) ->
            // data = Triple(level, delay, Triple(animation, wallAnimation, dbrow))
            val (level, delay, animTriple) = data
            val (animation, wallAnimation, dbrow) = animTriple

            row(dbrow) {
                columnRSCM(ITEM, item)
                column(LEVEL, level)
                column(DELAY, delay)
                columnRSCM(ANIMATION, animation)

                if (wallAnimation != null) {
                    columnRSCM(WALL_ANIMATION, wallAnimation)
                }
            }
        }
    }


    fun rocks() = dbTable("tables.mining_rocks") {

        column("rock_object", COL_ROCK_OBJECT, VarType.LOC)
        column("level", COL_LEVEL, VarType.INT)
        column("xp", COL_XP, VarType.INT)
        column("ore_item", COL_ORE_ITEM, VarType.OBJ)
        column("respawn_cycles", COL_RESPAWN_CYCLES, VarType.INT)
        column("success_rate_low", COL_SUCCESS_RATE_LOW, VarType.INT)
        column("success_rate_high", COL_SUCCESS_RATE_HIGH, VarType.INT)
        column("despawn_ticks", COL_DESPAWN_TICKS, VarType.INT)
        column("deplete_mechanic", COL_DEPLETE_MECHANIC, VarType.INT)
        column("empty_rock_object", COL_EMPTY_ROCK, VarType.LOC)
        column("clue_base_chance", CLUE_BASE_CHANCE, VarType.INT)
        column("type", COL_TYPE, VarType.STRING)

        // Clayrocks (level 1)
        row("dbrows.mining_clayrock") {
            columnRSCM(COL_ROCK_OBJECT, "objects.clayrock1", "objects.clayrock2")
            column(COL_LEVEL, 1)
            column(COL_XP, 5)
            columnRSCM(COL_ORE_ITEM, "items.clay")
            column(COL_RESPAWN_CYCLES, 2)
            column(COL_SUCCESS_RATE_LOW, 64)
            column(COL_SUCCESS_RATE_HIGH, 256)
            column(COL_DESPAWN_TICKS, 0)
            column(COL_DEPLETE_MECHANIC, 3) // Always
            columnRSCM(COL_EMPTY_ROCK, "objects.rocks2")
            column(CLUE_BASE_CHANCE, 317647)
            column(COL_TYPE, "rock")
        }
        // Copper (level 1)
        row("dbrows.mining_copperrock") {
            columnRSCM(COL_ROCK_OBJECT, "objects.copperrock1", "objects.copperrock2")
            column(COL_LEVEL, 1)
            column(COL_XP, 17.5)
            columnRSCM(COL_ORE_ITEM, "items.copper_ore")
            column(COL_RESPAWN_CYCLES, 4)
            column(COL_SUCCESS_RATE_LOW, 64)
            column(COL_SUCCESS_RATE_HIGH, 256)
            column(COL_DESPAWN_TICKS, 0)
            column(COL_DEPLETE_MECHANIC, 0) // Always
            columnRSCM(COL_EMPTY_ROCK, "objects.rocks2")
            column(CLUE_BASE_CHANCE, 317647)
            column(COL_TYPE, "rock")
        }
        // Tin (level 1)
        row("dbrows.mining_tinrock") {
            columnRSCM(COL_ROCK_OBJECT, "objects.tinrock1", "objects.tinrock2")
            column(COL_LEVEL, 1)
            column(COL_XP, 17.5)
            columnRSCM(COL_ORE_ITEM, "items.tin_ore")
            column(COL_RESPAWN_CYCLES, 4)
            column(COL_SUCCESS_RATE_LOW, 64)
            column(COL_SUCCESS_RATE_HIGH, 256)
            column(COL_DESPAWN_TICKS, 45)
            column(COL_DEPLETE_MECHANIC, 1) // Always
            columnRSCM(COL_EMPTY_ROCK, "objects.rocks2")
            column(CLUE_BASE_CHANCE, 317647)
            column(COL_TYPE, "wall")
        }

//        // Oak trees
//        row("dbrows.woodcutting_oak_tree") {
//            columnRSCM(COL_TREE_OBJECT,
//                "objects.oaktree",
//                "objects.oaktree", "objects.oak_tree_1",
//                "objects.oak_tree_2", "objects.oak_tree_3",
//                "objects.oak_tree_3_top", "objects.oak_tree_fullygrown_1",
//                "objects.oak_tree_fullygrown_2"
//            )
//            column(COL_LEVEL, 15)
//            column(COL_XP, 37)
//            columnRSCM(COL_LOG_ITEM, "items.oak_logs")
//            column(COL_RESPAWN_CYCLES, 60)
//            column(COL_SUCCESS_RATE_LOW, 64)
//            column(COL_SUCCESS_RATE_HIGH, 256)
//            column(COL_DESPAWN_TICKS, 45)
//            column(COL_DEPLETE_MECHANIC, 1) // Countdown
//            columnRSCM(COL_STUMP, "objects.oaktree_stump")
//            column(CLUE_BASE_CHANCE, 361146)
//        }
//
//        // Willow trees
//        row("dbrows.woodcutting_willow_tree") {
//            columnRSCM(COL_TREE_OBJECT,
//                "objects.willowtree", "objects.willow_tree_1",
//                "objects.willow_tree_2", "objects.willow_tree_3",
//                "objects.willow_tree_4", "objects.willow_tree_5",
//                "objects.willow_tree_fullygrown_1", "objects.willow_tree_fullygrown_2",
//                "objects.willow_tree2", "objects.willow_tree3",
//                "objects.willow_tree4"
//            )
//            column(COL_LEVEL, 30)
//            column(COL_XP, 67)
//            columnRSCM(COL_LOG_ITEM, "items.willow_logs")
//            column(COL_RESPAWN_CYCLES, 100)
//            column(COL_SUCCESS_RATE_LOW, 32)
//            column(COL_SUCCESS_RATE_HIGH, 256)
//            column(COL_DESPAWN_TICKS, 50)
//            column(COL_DEPLETE_MECHANIC, 1) // Countdown
//            columnRSCM(COL_STUMP, "objects.willow_tree_stump_new")
//            column(CLUE_BASE_CHANCE, 289286)
//        }
//
//        // Teak trees
//        row("dbrows.woodcutting_teak_tree") {
//            columnRSCM(COL_TREE_OBJECT,
//                "objects.teaktree", "objects.teak_tree_1",
//                "objects.teak_tree_2", "objects.teak_tree_3",
//                "objects.teak_tree_4", "objects.teak_tree_5",
//                "objects.teak_tree_6", "objects.teak_tree_5_top",
//                "objects.teak_tree_6_top", "objects.teak_tree_fullygrown",
//                "objects.teak_tree_fullygrown_top"
//            )
//            column(COL_LEVEL, 35)
//            column(COL_XP, 85)
//            columnRSCM(COL_LOG_ITEM, "items.teak_logs")
//            column(COL_RESPAWN_CYCLES, 100)
//            column(COL_SUCCESS_RATE_LOW, 20)
//            column(COL_SUCCESS_RATE_HIGH, 256)
//            column(COL_DESPAWN_TICKS, 50)
//            column(COL_DEPLETE_MECHANIC, 1) // Countdown
//            columnRSCM(COL_STUMP, "objects.teak_tree_stump")
//            column(CLUE_BASE_CHANCE, 264336)
//        }
//
//        // Juniper trees
//        row("dbrows.woodcutting_juniper_tree") {
//            columnRSCM(COL_TREE_OBJECT, "objects.mature_juniper_tree")
//            column(COL_LEVEL, 42)
//            column(COL_XP, 35)
//            columnRSCM(COL_LOG_ITEM, "items.juniper_logs")
//            column(COL_RESPAWN_CYCLES, 100)
//            column(COL_SUCCESS_RATE_LOW, 18)
//            column(COL_SUCCESS_RATE_HIGH, 256)
//            column(COL_DESPAWN_TICKS, 50)
//            column(COL_DEPLETE_MECHANIC, 1) // Countdown
//            columnRSCM(COL_STUMP, "objects.mature_juniper_tree_stump")
//            column(CLUE_BASE_CHANCE, 360000)
//        }
//
//        // Maple trees
//        row("dbrows.woodcutting_maple_tree") {
//            columnRSCM(COL_TREE_OBJECT,
//                "objects.mapletree", "objects.maple_tree_1",
//                "objects.maple_tree_2", "objects.maple_tree_3",
//                "objects.maple_tree_4", "objects.maple_tree_5",
//                "objects.maple_tree_6", "objects.maple_tree_7",
//                "objects.maple_tree_fullygrown_1", "objects.maple_tree_fullygrown_2"
//            )
//            column(COL_LEVEL, 45)
//            column(COL_XP, 100)
//            columnRSCM(COL_LOG_ITEM, "items.maple_logs")
//            column(COL_RESPAWN_CYCLES, 100)
//            column(COL_SUCCESS_RATE_LOW, 16)
//            column(COL_SUCCESS_RATE_HIGH, 256)
//            column(COL_DESPAWN_TICKS, 100)
//            column(COL_DEPLETE_MECHANIC, 1) // Countdown
//            columnRSCM(COL_STUMP, "objects.maple_tree_stump_new")
//            column(CLUE_BASE_CHANCE, 221918)
//        }
//
//        // Mahogany trees
//        row("dbrows.woodcutting_mahogany_tree") {
//            columnRSCM(COL_TREE_OBJECT,
//                "objects.mahoganytree", "objects.mahogany_tree_1",
//                "objects.mahogany_tree_2", "objects.mahogany_tree_3",
//                "objects.mahogany_tree_4", "objects.mahogany_tree_5",
//                "objects.mahogany_tree_6", "objects.mahogany_tree_7",
//                "objects.mahogany_tree_8", "objects.mahogany_tree_9",
//                "objects.mahogany_tree_fullygrown"
//            )
//            column(COL_LEVEL, 50)
//            column(COL_XP, 125)
//            columnRSCM(COL_LOG_ITEM, "items.mahogany_logs")
//            column(COL_RESPAWN_CYCLES, 120)
//            column(COL_SUCCESS_RATE_LOW, 12)
//            column(COL_SUCCESS_RATE_HIGH, 256)
//            column(COL_DESPAWN_TICKS, 100)
//            column(COL_DEPLETE_MECHANIC, 1) // Countdown
//            columnRSCM(COL_STUMP, "objects.mahogany_tree_stump")
//            column(CLUE_BASE_CHANCE, 220623)
//        }
//
//        // Yew trees
        row("dbrows.mining_amethystrock") {
            columnRSCM(COL_ROCK_OBJECT, "objects.amethystrock1", "objects.amethystrock2")
            column(COL_LEVEL, 92)
            column(COL_XP, 240)
            columnRSCM(COL_ORE_ITEM, "items.amethyst")
            column(COL_RESPAWN_CYCLES, 125)
            column(COL_SUCCESS_RATE_LOW, -18)
            column(COL_SUCCESS_RATE_HIGH, 10)
            column(COL_DESPAWN_TICKS, 45)
            column(COL_DEPLETE_MECHANIC, 1) // Always
            columnRSCM(COL_EMPTY_ROCK, "objects.amethystrock_empty")
            column(CLUE_BASE_CHANCE, 317647)
            column(COL_TYPE, "wall")
        }
//
//        // Amethyst trees
//        row("dbrows.woodcutting_magic_tree") {
//            columnRSCM(COL_TREE_OBJECT,
//                "objects.magictree", "objects.magic_tree_1",
//                "objects.magic_tree_2", "objects.magic_tree_3",
//                "objects.magic_tree_4", "objects.magic_tree_5",
//                "objects.magic_tree_6", "objects.magic_tree_7",
//                "objects.magic_tree_8", "objects.magic_tree_9",
//                "objects.magic_tree_10", "objects.magic_tree_11",
//                "objects.magic_tree_fullygrown_1", "objects.magic_tree_fullygrown_2"
//            )
//            column(COL_LEVEL, 75)
//            column(COL_XP, 250)
//            columnRSCM(COL_LOG_ITEM, "items.magic_logs")
//            column(COL_RESPAWN_CYCLES, 120)
//            column(COL_SUCCESS_RATE_LOW, 4)
//            column(COL_SUCCESS_RATE_HIGH, 256)
//            column(COL_DESPAWN_TICKS, 390)
//            column(COL_DEPLETE_MECHANIC, 1) // Countdown
//            columnRSCM(COL_STUMP, "objects.magic_tree_stump_new")
//            column(CLUE_BASE_CHANCE, 72321)
//        }
//
//        // Blisterwood trees
//        row("dbrows.woodcutting_blisterwood_tree") {
//            columnRSCM(COL_TREE_OBJECT, "objects.blisterwood_tree")
//            column(COL_LEVEL, 62)
//            column(COL_XP, 76)
//            columnRSCM(COL_LOG_ITEM, "items.blisterwood_logs")
//            column(COL_RESPAWN_CYCLES, 0)
//            column(COL_SUCCESS_RATE_LOW, 10)
//            column(COL_SUCCESS_RATE_HIGH, 256)
//            column(COL_DESPAWN_TICKS, 50)
//            column(COL_DEPLETE_MECHANIC, 1) // Countdown
//            column(CLUE_BASE_CHANCE, 0)
//        }
    }
}

