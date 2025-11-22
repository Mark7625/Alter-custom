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
    const val COL_DEPLETE_MECHANIC = 7
    const val COL_EMPTY_ROCK = 8
    const val CLUE_BASE_CHANCE = 9
    const val COL_TYPE = 10
    const val COL_DEPLETE_MIN_AMOUNT = 11
    const val COL_DEPLETE_MAX_AMOUNT = 12

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
                columnRSCM(WALL_ANIMATION, wallAnimation)
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
        column("deplete_mechanic", COL_DEPLETE_MECHANIC, VarType.INT)
        column("empty_rock_object", COL_EMPTY_ROCK, VarType.LOC)
        column("clue_base_chance", CLUE_BASE_CHANCE, VarType.INT)
        column("type", COL_TYPE, VarType.STRING)
        column("deplete_min_amount", COL_DEPLETE_MIN_AMOUNT, VarType.INT)
        column("deplete_max_amount", COL_DEPLETE_MAX_AMOUNT, VarType.INT)

        // Clayrocks (level 1)
        row("dbrows.mining_clayrock") {
            columnRSCM(COL_ROCK_OBJECT, "objects.clayrock1", "objects.clayrock2")
            column(COL_LEVEL, 1)
            column(COL_XP, 5)
            columnRSCM(COL_ORE_ITEM, "items.clay")
            column(COL_RESPAWN_CYCLES, 2)
            column(COL_SUCCESS_RATE_LOW, 64)
            column(COL_SUCCESS_RATE_HIGH, 256)
            column(COL_DEPLETE_MECHANIC, 1) // Normal
            columnRSCM(COL_EMPTY_ROCK, "objects.rocks2")
            column(CLUE_BASE_CHANCE, 741600)
            column(COL_TYPE, "rock")
        }
        // Copper (level 1)
        row("dbrows.mining_copperrock") {
            columnRSCM(COL_ROCK_OBJECT, "objects.copperrock1", "objects.copperrock2")
            column(COL_LEVEL, 1)
            column(COL_XP, 17.5)
            columnRSCM(COL_ORE_ITEM, "items.copper_ore")
            column(COL_RESPAWN_CYCLES, 4)
            column(COL_SUCCESS_RATE_LOW, 127)
            column(COL_SUCCESS_RATE_HIGH, 255)
            column(COL_DEPLETE_MECHANIC, 1) // Normal
            columnRSCM(COL_EMPTY_ROCK, "objects.rocks2")
            column(CLUE_BASE_CHANCE, 741600)
            column(COL_TYPE, "rock")
        }
        // Tin (level 1)
        row("dbrows.mining_tinrock") {
            columnRSCM(COL_ROCK_OBJECT, "objects.tinrock1", "objects.tinrock2")
            column(COL_LEVEL, 1)
            column(COL_XP, 17.5)
            columnRSCM(COL_ORE_ITEM, "items.tin_ore")
            column(COL_RESPAWN_CYCLES, 4)
            column(COL_SUCCESS_RATE_LOW, 99)
            column(COL_SUCCESS_RATE_HIGH, 255)
            column(COL_DEPLETE_MECHANIC, 1) // Normal
            columnRSCM(COL_EMPTY_ROCK, "objects.rocks2")
            column(CLUE_BASE_CHANCE, 741600)
            column(COL_TYPE, "rock")
        }
        // Blurite (level 10)
        row("dbrows.mining_bluriterock") {
            columnRSCM(COL_ROCK_OBJECT, "objects.blurite_rock_1", "objects.blurite_rock_2")
            column(COL_LEVEL, 10)
            column(COL_XP, 17.5)
            columnRSCM(COL_ORE_ITEM, "items.blurite_ore")
            column(COL_RESPAWN_CYCLES, 42)
            column(COL_SUCCESS_RATE_LOW, 99)
            column(COL_SUCCESS_RATE_HIGH, 255)
            column(COL_DEPLETE_MECHANIC, 1) // Normal
            columnRSCM(COL_EMPTY_ROCK, "objects.rocks2")
            column(CLUE_BASE_CHANCE, 741600)
            column(COL_TYPE, "rock")
        }
        // Iron (level 15)
        row("dbrows.mining_ironrock") {
            columnRSCM(COL_ROCK_OBJECT, "objects.ironrock1", "objects.ironrock2")
            column(COL_LEVEL, 15)
            column(COL_XP, 35)
            columnRSCM(COL_ORE_ITEM, "items.iron_ore")
            column(COL_RESPAWN_CYCLES, 9)
            column(COL_SUCCESS_RATE_LOW, 110)
            column(COL_SUCCESS_RATE_HIGH, 255)
            column(COL_DEPLETE_MECHANIC, 1) // Normal
            columnRSCM(COL_EMPTY_ROCK, "objects.rocks2")
            column(CLUE_BASE_CHANCE, 741600)
            column(COL_TYPE, "rock")
        }
        // Silver (level 20)
        row("dbrows.mining_silverrock") {
            columnRSCM(COL_ROCK_OBJECT, "objects.silverrock1", "objects.silverrock2")
            column(COL_LEVEL, 20)
            column(COL_XP, 40)
            columnRSCM(COL_ORE_ITEM, "items.silver_ore")
            column(COL_RESPAWN_CYCLES, 100)
            column(COL_SUCCESS_RATE_LOW, 24)
            column(COL_SUCCESS_RATE_HIGH, 200)
            column(COL_DEPLETE_MECHANIC, 1) // Normal
            columnRSCM(COL_EMPTY_ROCK, "objects.rocks2")
            column(CLUE_BASE_CHANCE, 741600)
            column(COL_TYPE, "rock")
        }
        // Lead (level 25)
        row("dbrows.mining_leadrock") {
            columnRSCM(COL_ROCK_OBJECT, "objects.leadrock1", "objects.poh_deadman_rugcorner") //Temp. added poh_deadman_rugcorner cause it cant read only one object.
            column(COL_LEVEL, 25)
            column(COL_XP, 40.5)
            columnRSCM(COL_ORE_ITEM, "items.lead_ore")
            column(COL_RESPAWN_CYCLES, 10)
            column(COL_SUCCESS_RATE_LOW, 110)
            column(COL_SUCCESS_RATE_HIGH, 255)
            column(COL_DEPLETE_MECHANIC, 1) // Normal
            columnRSCM(COL_EMPTY_ROCK, "objects.leadrock1_empty")
            column(CLUE_BASE_CHANCE, 290641)
            column(COL_TYPE, "rock")
        }
        // Coal (level 30)
        row("dbrows.mining_coalrock") {
            columnRSCM(COL_ROCK_OBJECT, "objects.coalrock1", "objects.coalrock2")
            column(COL_LEVEL, 30)
            column(COL_XP, 50)
            columnRSCM(COL_ORE_ITEM, "items.coal")
            column(COL_RESPAWN_CYCLES, 50)
            column(COL_SUCCESS_RATE_LOW, 15)
            column(COL_SUCCESS_RATE_HIGH, 100)
            column(COL_DEPLETE_MECHANIC, 1) // Normal
            columnRSCM(COL_EMPTY_ROCK, "objects.rocks2")
            column(CLUE_BASE_CHANCE, 290640)
            column(COL_TYPE, "rock")
        }
        // Gem (level 40)
        row("dbrows.mining_gemrock") {
            columnRSCM(COL_ROCK_OBJECT, "objects.gemrock1", "objects.gemrock")
            column(COL_LEVEL, 40)
            column(COL_XP, 65)
            column(COL_RESPAWN_CYCLES, 99)
            column(COL_SUCCESS_RATE_LOW, 27)
            column(COL_SUCCESS_RATE_HIGH, 70)
            column(COL_DEPLETE_MECHANIC, 2) // Normal
            columnRSCM(COL_EMPTY_ROCK, "objects.rocks1")
            column(CLUE_BASE_CHANCE, 211866)
            column(COL_TYPE, "gemrock")
            column(COL_DEPLETE_MIN_AMOUNT, 3)
            column(COL_DEPLETE_MAX_AMOUNT, 3)
        }
        //Gold (level 40)
        row("dbrows.mining_goldrock") {
            columnRSCM(COL_ROCK_OBJECT, "objects.goldrock1", "objects.goldrock2")
            column(COL_LEVEL, 40)
            column(COL_XP, 65)
            columnRSCM(COL_ORE_ITEM, "items.gold_ore")
            column(COL_RESPAWN_CYCLES, 100)
            column(COL_SUCCESS_RATE_LOW, 6)
            column(COL_SUCCESS_RATE_HIGH, 75)
            column(COL_DEPLETE_MECHANIC, 1) // Normal
            columnRSCM(COL_EMPTY_ROCK, "objects.rocks2")
            column(CLUE_BASE_CHANCE, 296640)
            column(COL_TYPE, "rock")
        }
        //Mith (level 55)
        row("dbrows.mining_mithrilrock") {
            columnRSCM(COL_ROCK_OBJECT, "objects.mithrilrock1", "objects.mithrilrock2")
            column(COL_LEVEL, 55)
            column(COL_XP, 80)
            columnRSCM(COL_ORE_ITEM, "items.mithril_ore")
            column(COL_RESPAWN_CYCLES, 200)
            column(COL_SUCCESS_RATE_LOW, 2)
            column(COL_SUCCESS_RATE_HIGH, 50)
            column(COL_DEPLETE_MECHANIC, 1) // Normal
            columnRSCM(COL_EMPTY_ROCK, "objects.rocks2")
            column(CLUE_BASE_CHANCE, 148320)
            column(COL_TYPE, "rock")
        }
        //Lovakite (level 65)
        row("dbrows.mining_lovakiterock") {
            columnRSCM(COL_ROCK_OBJECT, "objects.lovakite_rock1", "objects.lovakite_rock2")
            column(COL_LEVEL, 65)
            column(COL_XP, 60)
            columnRSCM(COL_ORE_ITEM, "items.lovakite_ore")
            column(COL_RESPAWN_CYCLES, 59)
            column(COL_SUCCESS_RATE_LOW, 2)
            column(COL_SUCCESS_RATE_HIGH, 50)
            column(COL_DEPLETE_MECHANIC, 1) // Normal
            columnRSCM(COL_EMPTY_ROCK, "objects.rocks2")
            column(CLUE_BASE_CHANCE, 245562)
            column(COL_TYPE, "rock")
        }
        //Addy (Level 70)
        row("dbrows.mining_adamantiterock") {
            columnRSCM(COL_ROCK_OBJECT, "objects.adamantiterock1", "objects.adamantiterock2")
            column(COL_LEVEL, 70)
            column(COL_XP, 95)
            columnRSCM(COL_ORE_ITEM, "items.adamantite_ore")
            column(COL_RESPAWN_CYCLES, 400)
            column(COL_SUCCESS_RATE_LOW, -1)
            column(COL_SUCCESS_RATE_HIGH, 25)
            column(COL_DEPLETE_MECHANIC, 1) // Normal
            columnRSCM(COL_EMPTY_ROCK, "objects.rocks2")
            column(CLUE_BASE_CHANCE, 59328)
            column(COL_TYPE, "rock")
        }
        //Nickel (Level 74)
        row("dbrows.mining_nickelrock") {
            columnRSCM(COL_ROCK_OBJECT, "objects.nickelrock1", "objects.deadman_final_wallsupport")//Temp. added deadman_final_wallsupport cause it cant read only one object.
            column(COL_LEVEL, 70)
            column(COL_XP, 95)
            columnRSCM(COL_ORE_ITEM, "items.nickel_ore")
            column(COL_RESPAWN_CYCLES, 200)
            column(COL_SUCCESS_RATE_LOW, -1)
            column(COL_SUCCESS_RATE_HIGH, 25)
            column(COL_DEPLETE_MECHANIC, 1) // Normal
            columnRSCM(COL_EMPTY_ROCK, "objects.nickelrock1_empty")
            column(CLUE_BASE_CHANCE, 59328)
            column(COL_TYPE, "rock")
        }
        row("dbrows.mining_runiterock") {
            columnRSCM(COL_ROCK_OBJECT, "objects.runiterock1", "objects.runiterock2")
            column(COL_LEVEL, 85)
            column(COL_XP, 125)
            columnRSCM(COL_ORE_ITEM, "items.runite_ore")
            column(COL_RESPAWN_CYCLES, 312)
            column(COL_SUCCESS_RATE_LOW, -1)
            column(COL_SUCCESS_RATE_HIGH, 18)
            column(COL_DEPLETE_MECHANIC, 1) // Normal
            columnRSCM(COL_EMPTY_ROCK, "objects.rocks2")
            column(CLUE_BASE_CHANCE, 42377)
            column(COL_TYPE, "rock")

        }
        // Amethystrock (level 92
        row("dbrows.mining_amethystrock") {
            columnRSCM(COL_ROCK_OBJECT, "objects.amethystrock1", "objects.amethystrock2")
            column(COL_LEVEL, 92)
            column(COL_XP, 240)
            columnRSCM(COL_ORE_ITEM, "items.amethyst")
            column(COL_RESPAWN_CYCLES, 125)
            column(COL_SUCCESS_RATE_LOW, -64)
            column(COL_SUCCESS_RATE_HIGH, 13)
            column(COL_DEPLETE_MECHANIC, 2)
            columnRSCM(COL_EMPTY_ROCK, "objects.amethystrock_empty")
            column(CLUE_BASE_CHANCE, 46350)
            column(COL_TYPE, "wall")
            column(COL_DEPLETE_MIN_AMOUNT, 2)
            column(COL_DEPLETE_MAX_AMOUNT, 3)
        }
        // Essence (level )
        row("dbrows.mining_essence") {
            columnRSCM(COL_ROCK_OBJECT, "objects.blankrunestone", "objects.deadman_flax")//Temp. added deadman flax cause it cant read only one object.
            column(COL_LEVEL, 1)
            column(COL_XP, 5)
            columnRSCM(COL_ORE_ITEM, "items.blankrune")
            column(COL_RESPAWN_CYCLES, 0)
            column(COL_SUCCESS_RATE_LOW, 256)
            column(COL_SUCCESS_RATE_HIGH, 256)
            column(COL_DEPLETE_MECHANIC, 3) // Always
            column(CLUE_BASE_CHANCE, 317647)
            column(COL_TYPE, "wall")
        }
    }
}

