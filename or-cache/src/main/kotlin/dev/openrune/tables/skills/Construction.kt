package dev.openrune.tables.skills

import dev.openrune.definition.dbtables.DBRowBuilder
import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType

/**
 * `dbtable.poh_furniture`: one row per Construction build option.
 *
 * - [COL_HOTSPOT_LOC] / [COL_BUILT_LOC] are parallel lists: building this option on the i-th
 *   hotspot loc yields the i-th built loc. Options offered on several independent hotspots (e.g.
 *   the three parlour chair spaces) list each hotspot with the same built loc; multi-piece
 *   furniture (rugs, stairs) lists each piece's hotspot with its matching piece loc.
 * - [COL_GROUP_BUILD] `true` means one build op replaces *every* placement of the listed hotspot
 *   locs in the room at once (rugs, curtains); `false` replaces only the clicked placement.
 * - [COL_XP] uses fine precision (x10), matching `dbtable.mining_rocks`.
 * - [COL_NAILS] is the number of nails required; any nail type is accepted at build time.
 * - Upgrade tiers (workbenches, tool stores, crafting tables, pools, jewellery boxes, costume-room
 *   storage, pet houses, portal nexus, occult altar, combat dummies) list the previous tier's
 *   BUILT loc as their hotspot; those locs carry an `Upgrade` op instead of `Build`.
 *
 * Data source: OSRS wiki room pages (level / materials / experience tables).
 */
object Construction {

    const val COL_HOTSPOT_LOC = 0
    const val COL_BUILT_LOC = 1
    const val COL_LEVEL = 2
    const val COL_XP = 3
    const val COL_MATERIAL = 4
    const val COL_NAILS = 5
    const val COL_MENU_NAME = 6
    const val COL_GROUP_BUILD = 7
    const val COL_FLATPACK = 8

    /** Adds a `material` obj+count pair; call repeatedly for multi-material options. */
    private class MaterialList {
        val objs = mutableListOf<String>()
        val counts = mutableListOf<Int>()

        fun material(obj: String, count: Int) {
            objs += obj
            counts += count
        }
    }

    private fun DBRowBuilder.option(
        name: String,
        level: Int,
        xp: Double,
        hotspots: List<String>,
        built: List<String>,
        groupBuild: Boolean = false,
        nails: Int = 0,
        flatpack: String? = null,
        materials: MaterialList.() -> Unit,
    ) {
        require(hotspots.size == built.size) {
            "hotspot/built lists must align for option '$name'"
        }
        columnRSCM(COL_HOTSPOT_LOC, *hotspots.toTypedArray())
        columnRSCM(COL_BUILT_LOC, *built.toTypedArray())
        column(COL_LEVEL, level)
        column(COL_XP, (xp * 10).toInt())
        val mats = MaterialList().apply(materials)
        if (mats.objs.isNotEmpty()) {
            materialColumn(COL_MATERIAL, mats)
        }
        column(COL_NAILS, nails)
        column(COL_MENU_NAME, name)
        column(COL_GROUP_BUILD, groupBuild)
        if (flatpack != null) {
            columnRSCM(COL_FLATPACK, flatpack)
        }
    }

    private fun DBRowBuilder.materialColumn(index: Int, mats: MaterialList) {
        val values = mutableListOf<Any>()
        for (i in mats.objs.indices) {
            values += dev.openrune.definition.constants.ConstantProvider.getMapping(mats.objs[i])
            values += mats.counts[i]
        }
        column(index, *values.toTypedArray())
    }

    private val PARLOUR_CHAIR_SPACES =
        listOf("loc.poh_parlour_1", "loc.poh_parlour_2", "loc.poh_parlour_3")

    private fun chairBuilt(chair: String) = List(PARLOUR_CHAIR_SPACES.size) { chair }

    fun furniture() =
        dbTable("dbtable.poh_furniture", serverOnly = true) {
            column("hotspot_loc", COL_HOTSPOT_LOC, VarType.LOC)
            column("built_loc", COL_BUILT_LOC, VarType.LOC)
            column("level", COL_LEVEL, VarType.INT)
            column("xp", COL_XP, VarType.INT)
            column("material", COL_MATERIAL, VarType.OBJ, VarType.INT)
            column("nails", COL_NAILS, VarType.INT)
            column("menu_name", COL_MENU_NAME, VarType.STRING)
            column("group_build", COL_GROUP_BUILD, VarType.BOOLEAN)
            column("flatpack", COL_FLATPACK, VarType.OBJ)

            /* Parlour - Chairs (three independent chair spaces). */
            row("dbrow.poh_crude_wooden_chair") {
                option(
                    "Crude wooden chair",
                    level = 1,
                    xp = 58.0,
                    hotspots = PARLOUR_CHAIR_SPACES,
                    built = chairBuilt("loc.poh_chair1"),
                    nails = 2,
                    flatpack = "obj.poh_flatpack_armchair1",
                ) {
                    material("obj.woodplank", 2)
                }
            }
            row("dbrow.poh_wooden_chair") {
                option(
                    "Wooden chair",
                    level = 8,
                    xp = 87.0,
                    hotspots = PARLOUR_CHAIR_SPACES,
                    built = chairBuilt("loc.poh_chair2"),
                    nails = 3,
                    flatpack = "obj.poh_flatpack_armchair2",
                ) {
                    material("obj.woodplank", 3)
                }
            }
            row("dbrow.poh_rocking_chair") {
                option(
                    "Rocking chair",
                    level = 14,
                    xp = 87.0,
                    hotspots = PARLOUR_CHAIR_SPACES,
                    built = chairBuilt("loc.poh_chair3"),
                    nails = 3,
                    flatpack = "obj.poh_flatpack_armchair3",
                ) {
                    material("obj.woodplank", 3)
                }
            }
            row("dbrow.poh_oak_chair") {
                option(
                    "Oak chair",
                    level = 19,
                    xp = 120.0,
                    hotspots = PARLOUR_CHAIR_SPACES,
                    built = chairBuilt("loc.poh_chair4"),
                    flatpack = "obj.poh_flatpack_armchair4",
                ) {
                    material("obj.plank_oak", 2)
                }
            }
            row("dbrow.poh_oak_armchair") {
                option(
                    "Oak armchair",
                    level = 26,
                    xp = 180.0,
                    hotspots = PARLOUR_CHAIR_SPACES,
                    built = chairBuilt("loc.poh_chair5"),
                    flatpack = "obj.poh_flatpack_armchair5",
                ) {
                    material("obj.plank_oak", 3)
                }
            }
            row("dbrow.poh_teak_armchair") {
                option(
                    "Teak armchair",
                    level = 35,
                    xp = 180.0,
                    hotspots = PARLOUR_CHAIR_SPACES,
                    built = chairBuilt("loc.poh_chair6"),
                    flatpack = "obj.poh_flatpack_armchair6",
                ) {
                    material("obj.plank_teak", 2)
                }
            }
            row("dbrow.poh_mahogany_armchair") {
                option(
                    "Mahogany armchair",
                    level = 50,
                    xp = 280.0,
                    hotspots = PARLOUR_CHAIR_SPACES,
                    built = chairBuilt("loc.poh_chair7"),
                    flatpack = "obj.poh_flatpack_armchair7",
                ) {
                    material("obj.plank_mahogany", 2)
                }
            }

            /* Bookcases - shared ladder for the parlour, study, and quest hall spaces. */
            row("dbrow.poh_wooden_bookcase") {
                option(
                    "Wooden bookcase",
                    level = 4,
                    xp = 115.0,
                    hotspots = listOf("loc.poh_parlour_5", "loc.poh_study_7", "loc.poh_hall2_7"),
                    built = listOf("loc.poh_bookcase1", "loc.poh_bookcase1", "loc.poh_bookcase1"),
                    nails = 4,
                ) {
                    material("obj.woodplank", 4)
                }
            }
            row("dbrow.poh_oak_bookcase") {
                option(
                    "Oak bookcase",
                    level = 29,
                    xp = 180.0,
                    hotspots = listOf("loc.poh_parlour_5", "loc.poh_study_7", "loc.poh_hall2_7"),
                    built = listOf("loc.poh_bookcase2", "loc.poh_bookcase2", "loc.poh_bookcase2"),
                ) {
                    material("obj.plank_oak", 3)
                }
            }
            row("dbrow.poh_mahogany_bookcase") {
                option(
                    "Mahogany bookcase",
                    level = 40,
                    xp = 420.0,
                    hotspots = listOf("loc.poh_parlour_5", "loc.poh_study_7", "loc.poh_hall2_7"),
                    built = listOf("loc.poh_bookcase3", "loc.poh_bookcase3", "loc.poh_bookcase3"),
                ) {
                    material("obj.plank_mahogany", 3)
                }
            }

            /* Fireplaces - shared ladder for the parlour, dining room, and bedroom. */
            row("dbrow.poh_clay_fireplace") {
                option(
                    "Clay fireplace",
                    level = 3,
                    xp = 30.0,
                    hotspots =
                        listOf(
                            "loc.poh_parlour_6",
                            "loc.poh_dining_room_4",
                            "loc.poh_bedroom_6",
                        ),
                    built =
                        listOf(
                            "loc.poh_fireplace_1",
                            "loc.poh_fireplace_1",
                            "loc.poh_fireplace_1",
                        ),
                ) {
                    material("obj.softclay", 3)
                }
            }
            row("dbrow.poh_stone_fireplace") {
                option(
                    "Stone fireplace",
                    level = 33,
                    xp = 40.0,
                    hotspots =
                        listOf(
                            "loc.poh_parlour_6",
                            "loc.poh_dining_room_4",
                            "loc.poh_bedroom_6",
                        ),
                    built =
                        listOf(
                            "loc.poh_fireplace_2",
                            "loc.poh_fireplace_2",
                            "loc.poh_fireplace_2",
                        ),
                ) {
                    material("obj.limestonebrick", 2)
                }
            }
            row("dbrow.poh_marble_fireplace") {
                option(
                    "Marble fireplace",
                    level = 63,
                    xp = 500.0,
                    hotspots =
                        listOf(
                            "loc.poh_parlour_6",
                            "loc.poh_dining_room_4",
                            "loc.poh_bedroom_6",
                        ),
                    built =
                        listOf(
                            "loc.poh_fireplace_3",
                            "loc.poh_fireplace_3",
                            "loc.poh_fireplace_3",
                        ),
                ) {
                    material("obj.marble_block", 1)
                }
            }

            /* Curtains - parlour, dining room, bedroom, and portal nexus; one build dresses the room. */
            row("dbrow.poh_torn_curtains") {
                option(
                    "Torn curtains",
                    level = 2,
                    xp = 132.0,
                    hotspots =
                        listOf(
                            "loc.poh_parlour_7",
                            "loc.poh_dining_room_5",
                            "loc.poh_bedroom_4",
                            "loc.poh_telenexus_3",
                        ),
                    built =
                        listOf(
                            "loc.poh_curtains_1",
                            "loc.poh_curtains_1",
                            "loc.poh_curtains_1",
                            "loc.poh_curtains_1",
                        ),
                    groupBuild = true,
                    nails = 3,
                ) {
                    material("obj.woodplank", 3)
                    material("obj.cloth", 3)
                }
            }
            row("dbrow.poh_curtains") {
                option(
                    "Curtains",
                    level = 18,
                    xp = 225.0,
                    hotspots =
                        listOf(
                            "loc.poh_parlour_7",
                            "loc.poh_dining_room_5",
                            "loc.poh_bedroom_4",
                            "loc.poh_telenexus_3",
                        ),
                    built =
                        listOf(
                            "loc.poh_curtains_2",
                            "loc.poh_curtains_2",
                            "loc.poh_curtains_2",
                            "loc.poh_curtains_2",
                        ),
                    groupBuild = true,
                ) {
                    material("obj.plank_oak", 3)
                    material("obj.cloth", 3)
                }
            }
            row("dbrow.poh_opulent_curtains") {
                option(
                    "Opulent curtains",
                    level = 40,
                    xp = 315.0,
                    hotspots =
                        listOf(
                            "loc.poh_parlour_7",
                            "loc.poh_dining_room_5",
                            "loc.poh_bedroom_4",
                            "loc.poh_telenexus_3",
                        ),
                    built =
                        listOf(
                            "loc.poh_curtains_3",
                            "loc.poh_curtains_3",
                            "loc.poh_curtains_3",
                            "loc.poh_curtains_3",
                        ),
                    groupBuild = true,
                ) {
                    material("obj.plank_teak", 3)
                    material("obj.cloth", 3)
                }
            }

            /* Rugs - parlour, bedroom, chapel, and portal nexus; corner/side/middle build as one unit. */
            row("dbrow.poh_brown_rug") {
                option(
                    "Brown rug",
                    level = 2,
                    xp = 30.0,
                    hotspots =
                        listOf(
                            "loc.poh_parlour_4_corner",
                            "loc.poh_parlour_4_side",
                            "loc.poh_parlour_4_middle",
                            "loc.poh_bedroom_5_corner",
                            "loc.poh_bedroom_5_side",
                            "loc.poh_bedroom_5_middle",
                            "loc.poh_chapel_5_corner",
                            "loc.poh_chapel_5_side",
                            "loc.poh_telenexus_2_corner",
                            "loc.poh_telenexus_2_side",
                            "loc.poh_telenexus_2_middle",
                        ),
                    built =
                        listOf(
                            "loc.poh_rugcorner1",
                            "loc.poh_rugside1",
                            "loc.poh_rugmiddle1",
                            "loc.poh_rugcorner1",
                            "loc.poh_rugside1",
                            "loc.poh_rugmiddle1",
                            "loc.poh_rugcorner1",
                            "loc.poh_rugside1",
                            "loc.poh_rugcorner1",
                            "loc.poh_rugside1",
                            "loc.poh_rugmiddle1",
                        ),
                    groupBuild = true,
                ) {
                    material("obj.cloth", 2)
                }
            }
            row("dbrow.poh_rug") {
                option(
                    "Rug",
                    level = 13,
                    xp = 60.0,
                    hotspots =
                        listOf(
                            "loc.poh_parlour_4_corner",
                            "loc.poh_parlour_4_side",
                            "loc.poh_parlour_4_middle",
                            "loc.poh_bedroom_5_corner",
                            "loc.poh_bedroom_5_side",
                            "loc.poh_bedroom_5_middle",
                            "loc.poh_chapel_5_corner",
                            "loc.poh_chapel_5_side",
                            "loc.poh_telenexus_2_corner",
                            "loc.poh_telenexus_2_side",
                            "loc.poh_telenexus_2_middle",
                        ),
                    built =
                        listOf(
                            "loc.poh_rugcorner2",
                            "loc.poh_rugside2",
                            "loc.poh_rugmiddle2",
                            "loc.poh_rugcorner2",
                            "loc.poh_rugside2",
                            "loc.poh_rugmiddle2",
                            "loc.poh_rugcorner2",
                            "loc.poh_rugside2",
                            "loc.poh_rugcorner2",
                            "loc.poh_rugside2",
                            "loc.poh_rugmiddle2",
                        ),
                    groupBuild = true,
                ) {
                    material("obj.cloth", 4)
                }
            }
            row("dbrow.poh_opulent_rug") {
                option(
                    "Opulent rug",
                    level = 65,
                    xp = 360.0,
                    hotspots =
                        listOf(
                            "loc.poh_parlour_4_corner",
                            "loc.poh_parlour_4_side",
                            "loc.poh_parlour_4_middle",
                            "loc.poh_bedroom_5_corner",
                            "loc.poh_bedroom_5_side",
                            "loc.poh_bedroom_5_middle",
                            "loc.poh_chapel_5_corner",
                            "loc.poh_chapel_5_side",
                            "loc.poh_telenexus_2_corner",
                            "loc.poh_telenexus_2_side",
                            "loc.poh_telenexus_2_middle",
                        ),
                    built =
                        listOf(
                            "loc.poh_rugcorner3",
                            "loc.poh_rugside3",
                            "loc.poh_rugmiddle3",
                            "loc.poh_rugcorner3",
                            "loc.poh_rugside3",
                            "loc.poh_rugmiddle3",
                            "loc.poh_rugcorner3",
                            "loc.poh_rugside3",
                            "loc.poh_rugcorner3",
                            "loc.poh_rugside3",
                            "loc.poh_rugmiddle3",
                        ),
                    groupBuild = true,
                ) {
                    material("obj.cloth", 4)
                    material("obj.gold_leaf", 1)
                }
            }

            // TODO(unmatched): Deadman rug / Raging echoes rug / Raging echoes curtains - leagues and
            // deadman cosmetic override objs are absent from rev 240 gamevals; the base rug/curtain rows
            // above and below cover every rug and curtain hotspot.

            /* Kitchen. */
            row("dbrow.poh_firepit") {
                option(
                    "Firepit",
                    level = 5,
                    xp = 40.0,
                    hotspots = listOf("loc.poh_kitchen_1"),
                    built = listOf("loc.poh_stove_1"),
                ) {
                    material("obj.steel_bar", 1)
                    material("obj.softclay", 2)
                }
            }
            row("dbrow.poh_firepit_with_hook") {
                option(
                    "Firepit with hook",
                    level = 11,
                    xp = 60.0,
                    hotspots = listOf("loc.poh_kitchen_1"),
                    built = listOf("loc.poh_stove_2"),
                ) {
                    material("obj.steel_bar", 2)
                    material("obj.softclay", 2)
                }
            }
            row("dbrow.poh_firepit_with_pot") {
                option(
                    "Firepit with pot",
                    level = 17,
                    xp = 80.0,
                    hotspots = listOf("loc.poh_kitchen_1"),
                    built = listOf("loc.poh_stove_3"),
                ) {
                    material("obj.steel_bar", 3)
                    material("obj.softclay", 2)
                }
            }
            row("dbrow.poh_small_oven") {
                option(
                    "Small oven",
                    level = 24,
                    xp = 80.0,
                    hotspots = listOf("loc.poh_kitchen_1"),
                    built = listOf("loc.poh_stove_4"),
                ) {
                    material("obj.steel_bar", 4)
                }
            }
            row("dbrow.poh_large_oven") {
                option(
                    "Large oven",
                    level = 29,
                    xp = 100.0,
                    hotspots = listOf("loc.poh_kitchen_1"),
                    built = listOf("loc.poh_stove_5"),
                ) {
                    material("obj.steel_bar", 5)
                }
            }
            row("dbrow.poh_steel_range") {
                option(
                    "Steel range",
                    level = 34,
                    xp = 120.0,
                    hotspots = listOf("loc.poh_kitchen_1"),
                    built = listOf("loc.poh_stove_6"),
                ) {
                    material("obj.steel_bar", 6)
                }
            }
            row("dbrow.poh_fancy_range") {
                option(
                    "Fancy range",
                    level = 42,
                    xp = 160.0,
                    hotspots = listOf("loc.poh_kitchen_1"),
                    built = listOf("loc.poh_stove_7"),
                ) {
                    material("obj.steel_bar", 8)
                }
            }
            row("dbrow.poh_wooden_shelves_1") {
                option(
                    "Wooden shelves 1",
                    level = 6,
                    xp = 87.0,
                    hotspots = listOf("loc.poh_kitchen_2", "loc.poh_kitchen_2_crockery"),
                    built = listOf("loc.poh_kitchen_shelves_1", "loc.poh_kitchen_crockery_1"),
                    groupBuild = true,
                    nails = 3,
                ) {
                    material("obj.woodplank", 3)
                }
            }
            row("dbrow.poh_wooden_shelves_2") {
                option(
                    "Wooden shelves 2",
                    level = 12,
                    xp = 147.0,
                    hotspots = listOf("loc.poh_kitchen_2", "loc.poh_kitchen_2_crockery"),
                    built = listOf("loc.poh_kitchen_shelves_2", "loc.poh_kitchen_crockery_2"),
                    groupBuild = true,
                    nails = 3,
                ) {
                    material("obj.woodplank", 3)
                    material("obj.softclay", 6)
                }
            }
            row("dbrow.poh_wooden_shelves_3") {
                option(
                    "Wooden shelves 3",
                    level = 23,
                    xp = 147.0,
                    hotspots = listOf("loc.poh_kitchen_2", "loc.poh_kitchen_2_crockery"),
                    built = listOf("loc.poh_kitchen_shelves_3", "loc.poh_kitchen_crockery_3"),
                    groupBuild = true,
                    nails = 3,
                ) {
                    material("obj.woodplank", 3)
                    material("obj.softclay", 6)
                }
            }
            row("dbrow.poh_oak_shelves_1") {
                option(
                    "Oak shelves 1",
                    level = 34,
                    xp = 240.0,
                    hotspots = listOf("loc.poh_kitchen_2", "loc.poh_kitchen_2_crockery"),
                    built = listOf("loc.poh_kitchen_shelves_4", "loc.poh_kitchen_crockery_4"),
                    groupBuild = true,
                ) {
                    material("obj.plank_oak", 3)
                    material("obj.softclay", 6)
                }
            }
            row("dbrow.poh_oak_shelves_2") {
                option(
                    "Oak shelves 2",
                    level = 45,
                    xp = 240.0,
                    hotspots = listOf("loc.poh_kitchen_2", "loc.poh_kitchen_2_crockery"),
                    built = listOf("loc.poh_kitchen_shelves_5", "loc.poh_kitchen_crockery_5"),
                    groupBuild = true,
                ) {
                    material("obj.plank_oak", 3)
                    material("obj.softclay", 6)
                }
            }
            row("dbrow.poh_teak_shelves_1") {
                option(
                    "Teak shelves 1",
                    level = 56,
                    xp = 330.0,
                    hotspots = listOf("loc.poh_kitchen_2", "loc.poh_kitchen_2_crockery"),
                    built = listOf("loc.poh_kitchen_shelves_6", "loc.poh_kitchen_crockery_6"),
                    groupBuild = true,
                ) {
                    material("obj.plank_teak", 3)
                    material("obj.softclay", 6)
                }
            }
            row("dbrow.poh_teak_shelves_2") {
                option(
                    "Teak shelves 2",
                    level = 67,
                    xp = 930.0,
                    hotspots = listOf("loc.poh_kitchen_2", "loc.poh_kitchen_2_crockery"),
                    built = listOf("loc.poh_kitchen_shelves_7", "loc.poh_kitchen_crockery_7"),
                    groupBuild = true,
                ) {
                    material("obj.plank_teak", 3)
                    material("obj.softclay", 6)
                    material("obj.gold_leaf", 2)
                }
            }
            row("dbrow.poh_pump_and_drain") {
                option(
                    "Pump and drain",
                    level = 7,
                    xp = 100.0,
                    hotspots = listOf("loc.poh_kitchen_6"),
                    built = listOf("loc.poh_sink_1"),
                ) {
                    material("obj.steel_bar", 5)
                }
            }
            row("dbrow.poh_pump_and_tub") {
                option(
                    "Pump and tub",
                    level = 27,
                    xp = 200.0,
                    hotspots = listOf("loc.poh_kitchen_6"),
                    built = listOf("loc.poh_sink_2"),
                ) {
                    material("obj.steel_bar", 10)
                }
            }
            row("dbrow.poh_kitchen_sink") {
                option(
                    "Sink",
                    level = 47,
                    xp = 300.0,
                    hotspots = listOf("loc.poh_kitchen_6"),
                    built = listOf("loc.poh_sink_3"),
                ) {
                    material("obj.steel_bar", 15)
                }
            }
            row("dbrow.poh_gold_sink") {
                option(
                    "Gold sink",
                    level = 47,
                    xp = 11144.0,
                    hotspots = listOf("loc.poh_kitchen_6"),
                    built = listOf("loc.poh_sink_4"),
                ) {
                    material("obj.poh_condensed_gold", 10)
                    material("obj.plank_mahogany", 5)
                    material("obj.gold_leaf", 5)
                }
            }
            row("dbrow.poh_wooden_larder") {
                option(
                    "Wooden larder",
                    level = 9,
                    xp = 228.0,
                    hotspots = listOf("loc.poh_kitchen_5"),
                    built = listOf("loc.poh_larder_1"),
                    nails = 8,
                ) {
                    material("obj.woodplank", 8)
                }
            }
            row("dbrow.poh_oak_larder") {
                option(
                    "Oak larder",
                    level = 33,
                    xp = 480.0,
                    hotspots = listOf("loc.poh_kitchen_5"),
                    built = listOf("loc.poh_larder_2"),
                ) {
                    material("obj.plank_oak", 8)
                }
            }
            row("dbrow.poh_teak_larder") {
                option(
                    "Teak larder",
                    level = 43,
                    xp = 750.0,
                    hotspots = listOf("loc.poh_kitchen_5"),
                    built = listOf("loc.poh_larder_3"),
                ) {
                    material("obj.plank_teak", 8)
                    material("obj.cloth", 2)
                }
            }
            row("dbrow.poh_beer_barrel") {
                option(
                    "Beer barrel",
                    level = 7,
                    xp = 87.0,
                    hotspots = listOf("loc.poh_kitchen_3"),
                    built = listOf("loc.poh_barrel_1"),
                    nails = 3,
                    flatpack = "obj.poh_flatpack_beerbarrel1",
                ) {
                    material("obj.woodplank", 3)
                }
            }
            row("dbrow.poh_cider_barrel") {
                option(
                    "Cider barrel",
                    level = 12,
                    xp = 91.0,
                    hotspots = listOf("loc.poh_kitchen_3"),
                    built = listOf("loc.poh_barrel_2"),
                    nails = 3,
                    flatpack = "obj.poh_flatpack_beerbarrel2",
                ) {
                    material("obj.woodplank", 3)
                    material("obj.cider", 8)
                }
            }
            row("dbrow.poh_asgarnian_ale_barrel") {
                option(
                    "Asgarnian ale",
                    level = 18,
                    xp = 184.0,
                    hotspots = listOf("loc.poh_kitchen_3"),
                    built = listOf("loc.poh_barrel_3"),
                    flatpack = "obj.poh_flatpack_beerbarrel3",
                ) {
                    material("obj.plank_oak", 3)
                    material("obj.asgarnian_ale", 8)
                }
            }
            row("dbrow.poh_greenmans_ale_barrel") {
                option(
                    "Greenman's ale",
                    level = 26,
                    xp = 184.0,
                    hotspots = listOf("loc.poh_kitchen_3"),
                    built = listOf("loc.poh_barrel_4"),
                    flatpack = "obj.poh_flatpack_beerbarrel4",
                ) {
                    material("obj.plank_oak", 3)
                    material("obj.greenmans_ale", 8)
                }
            }
            row("dbrow.poh_dragon_bitter_barrel") {
                option(
                    "Dragon bitter",
                    level = 36,
                    xp = 224.0,
                    hotspots = listOf("loc.poh_kitchen_3"),
                    built = listOf("loc.poh_barrel_5"),
                    flatpack = "obj.poh_flatpack_beerbarrel5",
                ) {
                    material("obj.plank_oak", 3)
                    material("obj.steel_bar", 2)
                    material("obj.dragon_bitter", 8)
                }
            }
            row("dbrow.poh_chefs_delight_barrel") {
                option(
                    "Chef's delight",
                    level = 48,
                    xp = 224.0,
                    hotspots = listOf("loc.poh_kitchen_3"),
                    built = listOf("loc.poh_barrel_6"),
                    flatpack = "obj.poh_flatpack_beerbarrel6",
                ) {
                    material("obj.plank_oak", 3)
                    material("obj.steel_bar", 2)
                    material("obj.chefs_delight", 8)
                }
            }
            row("dbrow.poh_cat_blanket") {
                option(
                    "Cat blanket",
                    level = 5,
                    xp = 15.0,
                    hotspots = listOf("loc.poh_kitchen_4"),
                    built = listOf("loc.poh_pet_1"),
                ) {
                    material("obj.cloth", 1)
                }
            }
            row("dbrow.poh_cat_basket") {
                option(
                    "Cat basket",
                    level = 19,
                    xp = 58.0,
                    hotspots = listOf("loc.poh_kitchen_4"),
                    built = listOf("loc.poh_pet_2"),
                    nails = 2,
                ) {
                    material("obj.woodplank", 2)
                }
            }
            row("dbrow.poh_cushioned_basket") {
                option(
                    "Cushioned basket",
                    level = 33,
                    xp = 58.0,
                    hotspots = listOf("loc.poh_kitchen_4"),
                    built = listOf("loc.poh_pet_3"),
                    nails = 2,
                ) {
                    material("obj.woodplank", 2)
                    material("obj.wool", 2)
                }
            }
            row("dbrow.poh_kitchen_table") {
                option(
                    "Kitchen table",
                    level = 12,
                    xp = 87.0,
                    hotspots = listOf("loc.poh_kitchen_7"),
                    built = listOf("loc.poh_kitchentable_1"),
                    nails = 3,
                    flatpack = "obj.poh_flatpack_kitchentable1",
                ) {
                    material("obj.woodplank", 3)
                }
            }
            row("dbrow.poh_oak_kitchen_table") {
                option(
                    "Oak kitchen table",
                    level = 32,
                    xp = 180.0,
                    hotspots = listOf("loc.poh_kitchen_7"),
                    built = listOf("loc.poh_kitchentable_2"),
                    flatpack = "obj.poh_flatpack_kitchentable2",
                ) {
                    material("obj.plank_oak", 3)
                }
            }
            row("dbrow.poh_teak_kitchen_table") {
                option(
                    "Teak kitchen table",
                    level = 52,
                    xp = 270.0,
                    hotspots = listOf("loc.poh_kitchen_7"),
                    built = listOf("loc.poh_kitchentable_3"),
                    flatpack = "obj.poh_flatpack_kitchentable3",
                ) {
                    material("obj.plank_teak", 3)
                }
            }
            row("dbrow.poh_spice_rack") {
                option(
                    "Spice rack",
                    level = 60,
                    xp = 374.0,
                    hotspots = listOf("loc.poh_kitchen_8"),
                    built = listOf("loc.poh_kitchen_rack_1"),
                ) {
                    material("obj.plank_teak", 3)
                    material("obj.softclay", 6)
                    material("obj.hundred_dave_spice_brown_4", 1)
                    material("obj.hundred_dave_spice_orange_4", 1)
                    material("obj.hundred_dave_spice_red_4", 1)
                    material("obj.hundred_dave_spice_yellow_4", 1)
                }
            }

            /* Dining room - wall decorations are shared with the combat and throne room spaces. */
            row("dbrow.poh_wood_dining_table") {
                option(
                    "Wood dining table",
                    level = 10,
                    xp = 115.0,
                    hotspots = listOf("loc.poh_dining_room_1"),
                    built = listOf("loc.poh_diningtable_1"),
                    nails = 4,
                    flatpack = "obj.poh_flatpack_diningtable1",
                ) {
                    material("obj.woodplank", 4)
                }
            }
            row("dbrow.poh_oak_dining_table") {
                option(
                    "Oak dining table",
                    level = 22,
                    xp = 240.0,
                    hotspots = listOf("loc.poh_dining_room_1"),
                    built = listOf("loc.poh_diningtable_2"),
                    flatpack = "obj.poh_flatpack_diningtable2",
                ) {
                    material("obj.plank_oak", 4)
                }
            }
            row("dbrow.poh_carved_oak_table") {
                option(
                    "Carved oak table",
                    level = 31,
                    xp = 360.0,
                    hotspots = listOf("loc.poh_dining_room_1"),
                    built = listOf("loc.poh_diningtable_3"),
                    flatpack = "obj.poh_flatpack_diningtable3",
                ) {
                    material("obj.plank_oak", 6)
                }
            }
            row("dbrow.poh_teak_table") {
                option(
                    "Teak table",
                    level = 38,
                    xp = 360.0,
                    hotspots = listOf("loc.poh_dining_room_1"),
                    built = listOf("loc.poh_diningtable_4"),
                    flatpack = "obj.poh_flatpack_diningtable4",
                ) {
                    material("obj.plank_teak", 4)
                }
            }
            row("dbrow.poh_carved_teak_table") {
                option(
                    "Carved teak table",
                    level = 45,
                    xp = 600.0,
                    hotspots = listOf("loc.poh_dining_room_1"),
                    built = listOf("loc.poh_diningtable_5"),
                    flatpack = "obj.poh_flatpack_diningtable5",
                ) {
                    material("obj.plank_teak", 6)
                    material("obj.cloth", 4)
                }
            }
            row("dbrow.poh_mahogany_table") {
                option(
                    "Mahogany table",
                    level = 52,
                    xp = 840.0,
                    hotspots = listOf("loc.poh_dining_room_1"),
                    built = listOf("loc.poh_diningtable_6"),
                    flatpack = "obj.poh_flatpack_diningtable6",
                ) {
                    material("obj.plank_mahogany", 6)
                }
            }
            row("dbrow.poh_opulent_table") {
                option(
                    "Opulent table",
                    level = 72,
                    xp = 3100.0,
                    hotspots = listOf("loc.poh_dining_room_1"),
                    built = listOf("loc.poh_diningtable_7"),
                    flatpack = "obj.poh_flatpack_diningtable7",
                ) {
                    material("obj.plank_mahogany", 6)
                    material("obj.cloth", 4)
                    material("obj.gold_leaf", 4)
                    material("obj.marble_block", 2)
                }
            }
            row("dbrow.poh_wooden_bench") {
                option(
                    "Wooden bench",
                    level = 10,
                    xp = 115.0,
                    hotspots = listOf("loc.poh_dining_room_2"),
                    built = listOf("loc.poh_diningchairs_1"),
                    groupBuild = true,
                    nails = 4,
                    flatpack = "obj.poh_flatpack_diningchair1",
                ) {
                    material("obj.woodplank", 4)
                }
            }
            row("dbrow.poh_wooden_bench_2") {
                option(
                    "Wooden bench",
                    level = 10,
                    xp = 115.0,
                    hotspots = listOf("loc.poh_dining_room_3"),
                    built = listOf("loc.poh_diningchairs_1"),
                    groupBuild = true,
                    nails = 4,
                    flatpack = "obj.poh_flatpack_diningchair1",
                ) {
                    material("obj.woodplank", 4)
                }
            }
            row("dbrow.poh_oak_bench") {
                option(
                    "Oak bench",
                    level = 22,
                    xp = 240.0,
                    hotspots = listOf("loc.poh_dining_room_2"),
                    built = listOf("loc.poh_diningchairs_2"),
                    groupBuild = true,
                    flatpack = "obj.poh_flatpack_diningchair2",
                ) {
                    material("obj.plank_oak", 4)
                }
            }
            row("dbrow.poh_oak_bench_2") {
                option(
                    "Oak bench",
                    level = 22,
                    xp = 240.0,
                    hotspots = listOf("loc.poh_dining_room_3"),
                    built = listOf("loc.poh_diningchairs_2"),
                    groupBuild = true,
                    flatpack = "obj.poh_flatpack_diningchair2",
                ) {
                    material("obj.plank_oak", 4)
                }
            }
            row("dbrow.poh_carved_oak_bench") {
                option(
                    "Carved oak bench",
                    level = 31,
                    xp = 240.0,
                    hotspots = listOf("loc.poh_dining_room_2"),
                    built = listOf("loc.poh_diningchairs_3"),
                    groupBuild = true,
                    flatpack = "obj.poh_flatpack_diningchair3",
                ) {
                    material("obj.plank_oak", 4)
                }
            }
            row("dbrow.poh_carved_oak_bench_2") {
                option(
                    "Carved oak bench",
                    level = 31,
                    xp = 240.0,
                    hotspots = listOf("loc.poh_dining_room_3"),
                    built = listOf("loc.poh_diningchairs_3"),
                    groupBuild = true,
                    flatpack = "obj.poh_flatpack_diningchair3",
                ) {
                    material("obj.plank_oak", 4)
                }
            }
            row("dbrow.poh_teak_dining_bench") {
                option(
                    "Teak dining bench",
                    level = 38,
                    xp = 360.0,
                    hotspots = listOf("loc.poh_dining_room_2"),
                    built = listOf("loc.poh_diningchairs_4"),
                    groupBuild = true,
                    flatpack = "obj.poh_flatpack_diningchair4",
                ) {
                    material("obj.plank_teak", 4)
                }
            }
            row("dbrow.poh_teak_dining_bench_2") {
                option(
                    "Teak dining bench",
                    level = 38,
                    xp = 360.0,
                    hotspots = listOf("loc.poh_dining_room_3"),
                    built = listOf("loc.poh_diningchairs_4"),
                    groupBuild = true,
                    flatpack = "obj.poh_flatpack_diningchair4",
                ) {
                    material("obj.plank_teak", 4)
                }
            }
            row("dbrow.poh_carved_teak_bench") {
                option(
                    "Carved teak bench",
                    level = 44,
                    xp = 360.0,
                    hotspots = listOf("loc.poh_dining_room_2"),
                    built = listOf("loc.poh_diningchairs_5"),
                    groupBuild = true,
                    flatpack = "obj.poh_flatpack_diningchair5",
                ) {
                    material("obj.plank_teak", 4)
                }
            }
            row("dbrow.poh_carved_teak_bench_2") {
                option(
                    "Carved teak bench",
                    level = 44,
                    xp = 360.0,
                    hotspots = listOf("loc.poh_dining_room_3"),
                    built = listOf("loc.poh_diningchairs_5"),
                    groupBuild = true,
                    flatpack = "obj.poh_flatpack_diningchair5",
                ) {
                    material("obj.plank_teak", 4)
                }
            }
            row("dbrow.poh_mahogany_bench") {
                option(
                    "Mahogany bench",
                    level = 52,
                    xp = 560.0,
                    hotspots = listOf("loc.poh_dining_room_2"),
                    built = listOf("loc.poh_diningchairs_6"),
                    groupBuild = true,
                    flatpack = "obj.poh_flatpack_diningchair6",
                ) {
                    material("obj.plank_mahogany", 4)
                }
            }
            row("dbrow.poh_mahogany_bench_2") {
                option(
                    "Mahogany bench",
                    level = 52,
                    xp = 560.0,
                    hotspots = listOf("loc.poh_dining_room_3"),
                    built = listOf("loc.poh_diningchairs_6"),
                    groupBuild = true,
                    flatpack = "obj.poh_flatpack_diningchair6",
                ) {
                    material("obj.plank_mahogany", 4)
                }
            }
            row("dbrow.poh_gilded_bench") {
                option(
                    "Gilded bench",
                    level = 61,
                    xp = 1760.0,
                    hotspots = listOf("loc.poh_dining_room_2"),
                    built = listOf("loc.poh_diningchairs_7"),
                    groupBuild = true,
                    flatpack = "obj.poh_flatpack_diningchair7",
                ) {
                    material("obj.plank_mahogany", 4)
                    material("obj.gold_leaf", 4)
                }
            }
            row("dbrow.poh_gilded_bench_2") {
                option(
                    "Gilded bench",
                    level = 61,
                    xp = 1760.0,
                    hotspots = listOf("loc.poh_dining_room_3"),
                    built = listOf("loc.poh_diningchairs_7"),
                    groupBuild = true,
                    flatpack = "obj.poh_flatpack_diningchair7",
                ) {
                    material("obj.plank_mahogany", 4)
                    material("obj.gold_leaf", 4)
                }
            }
            row("dbrow.poh_rope_bellpull") {
                option(
                    "Rope bell-pull",
                    level = 26,
                    xp = 64.0,
                    hotspots = listOf("loc.poh_dining_room_7"),
                    built = listOf("loc.poh_bellpull_1"),
                ) {
                    material("obj.plank_oak", 1)
                    material("obj.rope", 1)
                }
            }
            row("dbrow.poh_bellpull") {
                option(
                    "Bell-pull",
                    level = 37,
                    xp = 120.0,
                    hotspots = listOf("loc.poh_dining_room_7"),
                    built = listOf("loc.poh_bellpull_2"),
                ) {
                    material("obj.plank_teak", 1)
                    material("obj.cloth", 2)
                }
            }
            row("dbrow.poh_posh_bellpull") {
                option(
                    "Posh bell-pull",
                    level = 60,
                    xp = 420.0,
                    hotspots = listOf("loc.poh_dining_room_7"),
                    built = listOf("loc.poh_bellpull_3"),
                ) {
                    material("obj.plank_teak", 1)
                    material("obj.cloth", 2)
                    material("obj.gold_leaf", 1)
                }
            }
            row("dbrow.poh_greenman_carving") {
                option(
                    "Greenman carving",
                    level = 1,
                    xp = 0.0,
                    hotspots =
                        listOf(
                            "loc.poh_dining_room_6",
                            "loc.poh_combat_room_5",
                            "loc.poh_throne_room_3_q",
                        ),
                    built =
                        listOf(
                            "loc.poh_greenman_wall_decoration",
                            "loc.poh_greenman_wall_decoration",
                            "loc.poh_greenman_wall_decoration",
                        ),
                ) {
                    material("obj.greenman_wall_decoration", 1)
                }
            }
            row("dbrow.poh_oak_wall_decoration") {
                option(
                    "Oak wall decoration",
                    level = 16,
                    xp = 120.0,
                    hotspots =
                        listOf(
                            "loc.poh_dining_room_6",
                            "loc.poh_combat_room_5",
                            "loc.poh_throne_room_3_q",
                        ),
                    built =
                        listOf(
                            "loc.poh_wall_deco_1",
                            "loc.poh_wall_deco_1",
                            "loc.poh_wall_deco_1",
                        ),
                ) {
                    material("obj.plank_oak", 2)
                }
            }
            row("dbrow.poh_teak_wall_decoration") {
                option(
                    "Teak wall decoration",
                    level = 36,
                    xp = 180.0,
                    hotspots =
                        listOf(
                            "loc.poh_dining_room_6",
                            "loc.poh_combat_room_5",
                            "loc.poh_throne_room_3_q",
                        ),
                    built =
                        listOf(
                            "loc.poh_wall_deco_2",
                            "loc.poh_wall_deco_2",
                            "loc.poh_wall_deco_2",
                        ),
                ) {
                    material("obj.plank_teak", 2)
                }
            }
            row("dbrow.poh_gilded_decoration") {
                option(
                    "Gilded decoration",
                    level = 56,
                    xp = 1020.0,
                    hotspots =
                        listOf(
                            "loc.poh_dining_room_6",
                            "loc.poh_combat_room_5",
                            "loc.poh_throne_room_3_q",
                        ),
                    built =
                        listOf(
                            "loc.poh_wall_deco_3",
                            "loc.poh_wall_deco_3",
                            "loc.poh_wall_deco_3",
                        ),
                ) {
                    material("obj.plank_mahogany", 3)
                    material("obj.gold_leaf", 2)
                }
            }

            /* Bedroom. */
            row("dbrow.poh_wooden_bed") {
                option(
                    "Wooden bed",
                    level = 20,
                    xp = 117.0,
                    hotspots = listOf("loc.poh_bedroom_1_doublebed"),
                    built = listOf("loc.poh_bed_1"),
                    nails = 3,
                    flatpack = "obj.poh_flatpack_bed1",
                ) {
                    material("obj.woodplank", 3)
                    material("obj.cloth", 2)
                }
            }
            row("dbrow.poh_oak_bed") {
                option(
                    "Oak bed",
                    level = 30,
                    xp = 210.0,
                    hotspots = listOf("loc.poh_bedroom_1_doublebed"),
                    built = listOf("loc.poh_bed_2"),
                    flatpack = "obj.poh_flatpack_bed2",
                ) {
                    material("obj.plank_oak", 3)
                    material("obj.cloth", 2)
                }
            }
            row("dbrow.poh_large_oak_bed") {
                option(
                    "Large oak bed",
                    level = 34,
                    xp = 330.0,
                    hotspots = listOf("loc.poh_bedroom_1_doublebed"),
                    built = listOf("loc.poh_bed_3"),
                    flatpack = "obj.poh_flatpack_bed3",
                ) {
                    material("obj.plank_oak", 5)
                    material("obj.cloth", 2)
                }
            }
            row("dbrow.poh_teak_bed") {
                option(
                    "Teak bed",
                    level = 40,
                    xp = 300.0,
                    hotspots = listOf("loc.poh_bedroom_1_doublebed"),
                    built = listOf("loc.poh_bed_4"),
                    flatpack = "obj.poh_flatpack_bed4",
                ) {
                    material("obj.plank_teak", 3)
                    material("obj.cloth", 2)
                }
            }
            row("dbrow.poh_large_teak_bed") {
                option(
                    "Large teak bed",
                    level = 45,
                    xp = 480.0,
                    hotspots = listOf("loc.poh_bedroom_1_doublebed"),
                    built = listOf("loc.poh_bed_5"),
                    flatpack = "obj.poh_flatpack_bed5",
                ) {
                    material("obj.plank_teak", 5)
                    material("obj.cloth", 2)
                }
            }
            row("dbrow.poh_four_poster") {
                option(
                    "4-poster",
                    level = 53,
                    xp = 450.0,
                    hotspots = listOf("loc.poh_bedroom_1_doublebed"),
                    built = listOf("loc.poh_bed_6"),
                    flatpack = "obj.poh_flatpack_bed6",
                ) {
                    material("obj.plank_mahogany", 3)
                    material("obj.cloth", 2)
                }
            }
            row("dbrow.poh_gilded_four_poster") {
                option(
                    "Gilded 4-poster",
                    level = 60,
                    xp = 1330.0,
                    hotspots = listOf("loc.poh_bedroom_1_doublebed"),
                    built = listOf("loc.poh_bed_7"),
                    flatpack = "obj.poh_flatpack_bed7",
                ) {
                    material("obj.plank_mahogany", 5)
                    material("obj.cloth", 2)
                    material("obj.gold_leaf", 2)
                }
            }
            row("dbrow.poh_shoe_box") {
                option(
                    "Shoe box",
                    level = 20,
                    xp = 58.0,
                    hotspots = listOf("loc.poh_bedroom_2"),
                    built = listOf("loc.poh_wardrobe_1"),
                    nails = 2,
                    flatpack = "obj.poh_flatpack_wardrobe1",
                ) {
                    material("obj.woodplank", 2)
                }
            }
            row("dbrow.poh_oak_drawers") {
                option(
                    "Oak drawers",
                    level = 27,
                    xp = 120.0,
                    hotspots = listOf("loc.poh_bedroom_2"),
                    built = listOf("loc.poh_wardrobe_2"),
                    flatpack = "obj.poh_flatpack_wardrobe2",
                ) {
                    material("obj.plank_oak", 2)
                }
            }
            row("dbrow.poh_oak_wardrobe") {
                option(
                    "Oak wardrobe",
                    level = 39,
                    xp = 180.0,
                    hotspots = listOf("loc.poh_bedroom_2"),
                    built = listOf("loc.poh_wardrobe_3"),
                    flatpack = "obj.poh_flatpack_wardrobe3",
                ) {
                    material("obj.plank_oak", 3)
                }
            }
            row("dbrow.poh_teak_drawers") {
                option(
                    "Teak drawers",
                    level = 51,
                    xp = 180.0,
                    hotspots = listOf("loc.poh_bedroom_2"),
                    built = listOf("loc.poh_wardrobe_4"),
                    flatpack = "obj.poh_flatpack_wardrobe4",
                ) {
                    material("obj.plank_teak", 2)
                }
            }
            row("dbrow.poh_teak_wardrobe") {
                option(
                    "Teak wardrobe",
                    level = 63,
                    xp = 270.0,
                    hotspots = listOf("loc.poh_bedroom_2"),
                    built = listOf("loc.poh_wardrobe_5"),
                    flatpack = "obj.poh_flatpack_wardrobe5",
                ) {
                    material("obj.plank_teak", 3)
                }
            }
            row("dbrow.poh_mahogany_wardrobe") {
                option(
                    "Mahogany wardrobe",
                    level = 75,
                    xp = 420.0,
                    hotspots = listOf("loc.poh_bedroom_2"),
                    built = listOf("loc.poh_wardrobe_6"),
                    flatpack = "obj.poh_flatpack_wardrobe6",
                ) {
                    material("obj.plank_mahogany", 3)
                }
            }
            row("dbrow.poh_gilded_wardrobe") {
                option(
                    "Gilded wardrobe",
                    level = 87,
                    xp = 720.0,
                    hotspots = listOf("loc.poh_bedroom_2"),
                    built = listOf("loc.poh_wardrobe_7"),
                    flatpack = "obj.poh_flatpack_wardrobe7",
                ) {
                    material("obj.plank_mahogany", 3)
                    material("obj.gold_leaf", 1)
                }
            }
            row("dbrow.poh_shaving_stand") {
                option(
                    "Shaving stand",
                    level = 21,
                    xp = 30.0,
                    hotspots = listOf("loc.poh_bedroom_3"),
                    built = listOf("loc.poh_mirror_1"),
                    nails = 1,
                    flatpack = "obj.poh_flatpack_dresser1",
                ) {
                    material("obj.woodplank", 1)
                    material("obj.molten_glass", 1)
                }
            }
            row("dbrow.poh_oak_shaving_stand") {
                option(
                    "Oak shaving stand",
                    level = 29,
                    xp = 61.0,
                    hotspots = listOf("loc.poh_bedroom_3"),
                    built = listOf("loc.poh_mirror_2"),
                    flatpack = "obj.poh_flatpack_dresser2",
                ) {
                    material("obj.plank_oak", 1)
                    material("obj.molten_glass", 1)
                }
            }
            row("dbrow.poh_oak_dresser") {
                option(
                    "Oak dresser",
                    level = 37,
                    xp = 121.0,
                    hotspots = listOf("loc.poh_bedroom_3"),
                    built = listOf("loc.poh_mirror_3"),
                    flatpack = "obj.poh_flatpack_dresser3",
                ) {
                    material("obj.plank_oak", 2)
                    material("obj.molten_glass", 1)
                }
            }
            row("dbrow.poh_teak_dresser") {
                option(
                    "Teak dresser",
                    level = 46,
                    xp = 181.0,
                    hotspots = listOf("loc.poh_bedroom_3"),
                    built = listOf("loc.poh_mirror_4"),
                    flatpack = "obj.poh_flatpack_dresser4",
                ) {
                    material("obj.plank_teak", 2)
                    material("obj.molten_glass", 1)
                }
            }
            row("dbrow.poh_fancy_teak_dresser") {
                option(
                    "Fancy teak dresser",
                    level = 56,
                    xp = 182.0,
                    hotspots = listOf("loc.poh_bedroom_3"),
                    built = listOf("loc.poh_mirror_5"),
                    flatpack = "obj.poh_flatpack_dresser5",
                ) {
                    material("obj.plank_teak", 2)
                    material("obj.molten_glass", 2)
                }
            }
            row("dbrow.poh_mahogany_dresser") {
                option(
                    "Mahogany dresser",
                    level = 64,
                    xp = 281.0,
                    hotspots = listOf("loc.poh_bedroom_3"),
                    built = listOf("loc.poh_mirror_6"),
                    flatpack = "obj.poh_flatpack_dresser6",
                ) {
                    material("obj.plank_mahogany", 2)
                    material("obj.molten_glass", 1)
                }
            }
            row("dbrow.poh_gilded_dresser") {
                option(
                    "Gilded dresser",
                    level = 74,
                    xp = 582.0,
                    hotspots = listOf("loc.poh_bedroom_3"),
                    built = listOf("loc.poh_mirror_7"),
                    flatpack = "obj.poh_flatpack_dresser7",
                ) {
                    material("obj.plank_mahogany", 2)
                    material("obj.molten_glass", 2)
                    material("obj.gold_leaf", 1)
                }
            }
            row("dbrow.poh_oak_clock") {
                option(
                    "Oak clock",
                    level = 25,
                    xp = 142.0,
                    hotspots = listOf("loc.poh_bedroom_7"),
                    built = listOf("loc.poh_clock_1"),
                    flatpack = "obj.poh_flatpack_clock1",
                ) {
                    material("obj.plank_oak", 2)
                    material("obj.poh_clockwork_mechanism", 1)
                }
            }
            row("dbrow.poh_teak_clock") {
                option(
                    "Teak clock",
                    level = 55,
                    xp = 202.0,
                    hotspots = listOf("loc.poh_bedroom_7"),
                    built = listOf("loc.poh_clock_2"),
                    flatpack = "obj.poh_flatpack_clock2",
                ) {
                    material("obj.plank_teak", 2)
                    material("obj.poh_clockwork_mechanism", 1)
                }
            }
            row("dbrow.poh_servants_moneybag") {
                option(
                    "Servant's money bag",
                    level = 58,
                    xp = 595.0,
                    hotspots = listOf("loc.poh_bedroom_7"),
                    built = listOf("loc.poh_servant_moneybag"),
                ) {
                    material("obj.plank_mahogany", 2)
                    material("obj.cloth", 1)
                    material("obj.gold_leaf", 1)
                }
            }
            row("dbrow.poh_gilded_clock") {
                option(
                    "Gilded clock",
                    level = 85,
                    xp = 602.0,
                    hotspots = listOf("loc.poh_bedroom_7"),
                    built = listOf("loc.poh_clock_3"),
                    flatpack = "obj.poh_flatpack_clock3",
                ) {
                    material("obj.plank_mahogany", 2)
                    material("obj.poh_clockwork_mechanism", 1)
                    material("obj.gold_leaf", 1)
                }
            }

            /* Workshop. */
            row("dbrow.poh_wooden_workbench") {
                option(
                    "Wooden workbench",
                    level = 17,
                    xp = 143.0,
                    hotspots = listOf("loc.poh_workshop_1"),
                    built = listOf("loc.poh_workbench_1"),
                    nails = 5,
                ) {
                    material("obj.woodplank", 5)
                }
            }
            row("dbrow.poh_oak_workbench") {
                option(
                    "Oak workbench",
                    level = 32,
                    xp = 300.0,
                    hotspots = listOf("loc.poh_workshop_1"),
                    built = listOf("loc.poh_workbench_2"),
                ) {
                    material("obj.plank_oak", 5)
                }
            }
            row("dbrow.poh_steel_framed_workbench") {
                option(
                    "Steel framed workbench",
                    level = 46,
                    xp = 440.0,
                    hotspots = listOf("loc.poh_workshop_1"),
                    built = listOf("loc.poh_workbench_3"),
                ) {
                    material("obj.plank_oak", 6)
                    material("obj.steel_bar", 4)
                }
            }
            row("dbrow.poh_bench_with_vice") {
                option(
                    "Bench with vice",
                    level = 62,
                    xp = 140.0,
                    hotspots = listOf("loc.poh_workbench_3"),
                    built = listOf("loc.poh_workbench_4"),
                ) {
                    material("obj.plank_oak", 2)
                    material("obj.steel_bar", 1)
                }
            }
            row("dbrow.poh_bench_with_lathe") {
                option(
                    "Bench with lathe",
                    level = 77,
                    xp = 140.0,
                    hotspots = listOf("loc.poh_workbench_4"),
                    built = listOf("loc.poh_workbench_5"),
                ) {
                    material("obj.plank_oak", 2)
                    material("obj.steel_bar", 1)
                }
            }
            row("dbrow.poh_workshop_tool_store_1") {
                option(
                    "Tool store 1",
                    level = 15,
                    xp = 120.0,
                    hotspots = listOf("loc.poh_workshop_3a"),
                    built = listOf("loc.poh_tools1"),
                ) {
                    material("obj.plank_oak", 2)
                }
            }
            row("dbrow.poh_workshop_tool_store_2") {
                option(
                    "Tool store 2",
                    level = 25,
                    xp = 120.0,
                    hotspots = listOf("loc.poh_workshop_3b"),
                    built = listOf("loc.poh_tools2"),
                ) {
                    material("obj.plank_oak", 2)
                }
            }
            row("dbrow.poh_workshop_tool_store_3") {
                option(
                    "Tool store 3",
                    level = 35,
                    xp = 120.0,
                    hotspots = listOf("loc.poh_workshop_3c"),
                    built = listOf("loc.poh_tools3"),
                ) {
                    material("obj.plank_oak", 2)
                }
            }
            row("dbrow.poh_workshop_tool_store_4") {
                option(
                    "Tool store 4",
                    level = 44,
                    xp = 120.0,
                    hotspots = listOf("loc.poh_workshop_3d"),
                    built = listOf("loc.poh_tools4"),
                ) {
                    material("obj.plank_oak", 2)
                }
            }
            row("dbrow.poh_workshop_tool_store_5") {
                option(
                    "Tool store 5",
                    level = 55,
                    xp = 120.0,
                    hotspots = listOf("loc.poh_workshop_3e"),
                    built = listOf("loc.poh_tools5"),
                ) {
                    material("obj.plank_oak", 2)
                }
            }
            row("dbrow.poh_workshop_crafting_table_1") {
                option(
                    "Crafting table 1",
                    level = 16,
                    xp = 240.0,
                    hotspots = listOf("loc.poh_workshop_2"),
                    built = listOf("loc.poh_clockmaking_1"),
                ) {
                    material("obj.plank_oak", 4)
                }
            }
            row("dbrow.poh_workshop_crafting_table_2") {
                option(
                    "Crafting table 2",
                    level = 25,
                    xp = 1.0,
                    hotspots = listOf("loc.poh_clockmaking_1"),
                    built = listOf("loc.poh_clockmaking_2"),
                ) {
                    material("obj.molten_glass", 1)
                }
            }
            row("dbrow.poh_workshop_crafting_table_3") {
                option(
                    "Crafting table 3",
                    level = 34,
                    xp = 2.0,
                    hotspots = listOf("loc.poh_clockmaking_2"),
                    built = listOf("loc.poh_clockmaking_3"),
                ) {
                    material("obj.molten_glass", 2)
                }
            }
            row("dbrow.poh_workshop_crafting_table_4") {
                option(
                    "Crafting table 4",
                    level = 42,
                    xp = 120.0,
                    hotspots = listOf("loc.poh_clockmaking_3"),
                    built = listOf("loc.poh_clockmaking_4"),
                ) {
                    material("obj.plank_oak", 2)
                }
            }
            row("dbrow.poh_repair_bench") {
                option(
                    "Repair bench",
                    level = 15,
                    xp = 120.0,
                    hotspots = listOf("loc.poh_workshop_4"),
                    built = listOf("loc.poh_repair_1"),
                ) {
                    material("obj.plank_oak", 2)
                }
            }
            row("dbrow.poh_whetstone") {
                option(
                    "Whetstone",
                    level = 35,
                    xp = 260.0,
                    hotspots = listOf("loc.poh_workshop_4"),
                    built = listOf("loc.poh_repair_2"),
                ) {
                    material("obj.plank_oak", 4)
                    material("obj.limestonebrick", 1)
                }
            }
            row("dbrow.poh_armour_stand") {
                option(
                    "Armour stand",
                    level = 55,
                    xp = 500.0,
                    hotspots = listOf("loc.poh_workshop_4"),
                    built = listOf("loc.poh_repair_3"),
                ) {
                    material("obj.plank_oak", 8)
                    material("obj.limestonebrick", 1)
                }
            }
            row("dbrow.poh_pluming_stand") {
                option(
                    "Pluming stand",
                    level = 16,
                    xp = 120.0,
                    hotspots = listOf("loc.poh_workshop_5"),
                    built = listOf("loc.poh_repair_4"),
                ) {
                    material("obj.plank_oak", 2)
                }
            }
            row("dbrow.poh_shield_easel") {
                option(
                    "Shield easel",
                    level = 41,
                    xp = 240.0,
                    hotspots = listOf("loc.poh_workshop_5"),
                    built = listOf("loc.poh_repair_5"),
                ) {
                    material("obj.plank_oak", 4)
                }
            }
            row("dbrow.poh_banner_easel") {
                option(
                    "Banner easel",
                    level = 66,
                    xp = 510.0,
                    hotspots = listOf("loc.poh_workshop_5"),
                    built = listOf("loc.poh_repair_6"),
                ) {
                    material("obj.plank_oak", 8)
                    material("obj.cloth", 2)
                }
            }

            /* Study. */
            // TODO(unmatched): S.t.a.s.h chart - the S.t.a.s.h blueprint obj is not in rev 240 gamevals.
            row("dbrow.poh_oak_lectern") {
                option(
                    "Oak lectern",
                    level = 40,
                    xp = 60.0,
                    hotspots = listOf("loc.poh_study_1"),
                    built = listOf("loc.poh_lectern_1"),
                    flatpack = "obj.poh_flatpack_lecturn1",
                ) {
                    material("obj.plank_oak", 1)
                }
            }
            row("dbrow.poh_eagle_lectern") {
                option(
                    "Eagle lectern",
                    level = 47,
                    xp = 120.0,
                    hotspots = listOf("loc.poh_study_1"),
                    built = listOf("loc.poh_lectern_2"),
                    flatpack = "obj.poh_flatpack_lecturn2",
                ) {
                    material("obj.plank_oak", 2)
                }
            }
            row("dbrow.poh_demon_lectern") {
                option(
                    "Demon lectern",
                    level = 47,
                    xp = 120.0,
                    hotspots = listOf("loc.poh_study_1"),
                    built = listOf("loc.poh_lectern_3"),
                    flatpack = "obj.poh_flatpack_lecturn3",
                ) {
                    material("obj.plank_oak", 2)
                }
            }
            row("dbrow.poh_teak_eagle_lectern") {
                option(
                    "Teak eagle lectern",
                    level = 57,
                    xp = 180.0,
                    hotspots = listOf("loc.poh_study_1"),
                    built = listOf("loc.poh_lectern_4"),
                    flatpack = "obj.poh_flatpack_lecturn4",
                ) {
                    material("obj.plank_teak", 2)
                }
            }
            row("dbrow.poh_teak_demon_lectern") {
                option(
                    "Teak demon lectern",
                    level = 57,
                    xp = 180.0,
                    hotspots = listOf("loc.poh_study_1"),
                    built = listOf("loc.poh_lectern_5"),
                    flatpack = "obj.poh_flatpack_lecturn5",
                ) {
                    material("obj.plank_teak", 2)
                }
            }
            row("dbrow.poh_mahogany_eagle_lectern") {
                option(
                    "Mahogany eagle lectern",
                    level = 67,
                    xp = 580.0,
                    hotspots = listOf("loc.poh_study_1"),
                    built = listOf("loc.poh_lectern_6"),
                    flatpack = "obj.poh_flatpack_lecturn6",
                ) {
                    material("obj.plank_mahogany", 2)
                    material("obj.gold_leaf", 1)
                }
            }
            row("dbrow.poh_mahogany_demon_lectern") {
                option(
                    "Mahogany demon lectern",
                    level = 67,
                    xp = 580.0,
                    hotspots = listOf("loc.poh_study_1"),
                    built = listOf("loc.poh_lectern_7"),
                    flatpack = "obj.poh_flatpack_lecturn7",
                ) {
                    material("obj.plank_mahogany", 2)
                    material("obj.gold_leaf", 1)
                }
            }
            row("dbrow.poh_marble_lectern") {
                option(
                    "Marble lectern",
                    level = 77,
                    xp = 1800.0,
                    hotspots = listOf("loc.poh_study_1"),
                    built = listOf("loc.poh_lectern_8"),
                ) {
                    material("obj.marble_block", 1)
                    material("obj.poh_magic_crystal", 1)
                    material("obj.gold_leaf", 1)
                }
            }
            row("dbrow.poh_globe") {
                option(
                    "Globe",
                    level = 41,
                    xp = 180.0,
                    hotspots = listOf("loc.poh_study_2"),
                    built = listOf("loc.poh_globe_1"),
                    flatpack = "obj.poh_flatpack_globe1",
                ) {
                    material("obj.plank_oak", 3)
                }
            }
            row("dbrow.poh_ornamental_globe") {
                option(
                    "Ornamental globe",
                    level = 50,
                    xp = 270.0,
                    hotspots = listOf("loc.poh_study_2"),
                    built = listOf("loc.poh_globe_2"),
                    flatpack = "obj.poh_flatpack_globe2",
                ) {
                    material("obj.plank_teak", 3)
                }
            }
            row("dbrow.poh_lunar_globe") {
                option(
                    "Lunar globe",
                    level = 59,
                    xp = 570.0,
                    hotspots = listOf("loc.poh_study_2"),
                    built = listOf("loc.poh_globe_3"),
                    flatpack = "obj.poh_flatpack_globe3",
                ) {
                    material("obj.plank_teak", 3)
                    material("obj.gold_leaf", 1)
                }
            }
            row("dbrow.poh_celestial_globe") {
                option(
                    "Celestial globe",
                    level = 68,
                    xp = 570.0,
                    hotspots = listOf("loc.poh_study_2"),
                    built = listOf("loc.poh_globe_4"),
                    flatpack = "obj.poh_flatpack_globe4",
                ) {
                    material("obj.plank_teak", 3)
                    material("obj.gold_leaf", 1)
                }
            }
            row("dbrow.poh_armillary_sphere") {
                option(
                    "Armillary sphere",
                    level = 77,
                    xp = 960.0,
                    hotspots = listOf("loc.poh_study_2"),
                    built = listOf("loc.poh_globe_5"),
                    flatpack = "obj.poh_flatpack_globe5",
                ) {
                    material("obj.plank_mahogany", 2)
                    material("obj.gold_leaf", 2)
                    material("obj.steel_bar", 4)
                }
            }
            row("dbrow.poh_small_orrery") {
                option(
                    "Small orrery",
                    level = 86,
                    xp = 1320.0,
                    hotspots = listOf("loc.poh_study_2"),
                    built = listOf("loc.poh_globe_6"),
                    flatpack = "obj.poh_flatpack_globe6",
                ) {
                    material("obj.plank_mahogany", 3)
                    material("obj.gold_leaf", 3)
                }
            }
            row("dbrow.poh_large_orrery") {
                option(
                    "Large orrery",
                    level = 95,
                    xp = 1420.0,
                    hotspots = listOf("loc.poh_study_2"),
                    built = listOf("loc.poh_globe_7"),
                    flatpack = "obj.poh_flatpack_globe7",
                ) {
                    material("obj.plank_mahogany", 3)
                    material("obj.gold_leaf", 5)
                }
            }
            row("dbrow.poh_crystal_ball") {
                option(
                    "Crystal ball",
                    level = 42,
                    xp = 280.0,
                    hotspots = listOf("loc.poh_study_4"),
                    built = listOf("loc.poh_crystalball_1"),
                    flatpack = "obj.poh_flatpack_crystalball1",
                ) {
                    material("obj.plank_teak", 3)
                    material("obj.stafforb", 1)
                }
            }
            row("dbrow.poh_elemental_sphere") {
                option(
                    "Elemental sphere",
                    level = 54,
                    xp = 580.0,
                    hotspots = listOf("loc.poh_study_4"),
                    built = listOf("loc.poh_crystalball_2"),
                    flatpack = "obj.poh_flatpack_crystalball2",
                ) {
                    material("obj.plank_teak", 3)
                    material("obj.stafforb", 1)
                    material("obj.gold_leaf", 1)
                }
            }
            row("dbrow.poh_crystal_of_power") {
                option(
                    "Crystal of power",
                    level = 66,
                    xp = 890.0,
                    hotspots = listOf("loc.poh_study_4"),
                    built = listOf("loc.poh_crystalball_3"),
                    flatpack = "obj.poh_flatpack_crystalball3",
                ) {
                    material("obj.plank_mahogany", 2)
                    material("obj.stafforb", 1)
                    material("obj.gold_leaf", 2)
                }
            }
            row("dbrow.poh_oak_telescope") {
                option(
                    "Oak telescope",
                    level = 44,
                    xp = 121.0,
                    hotspots = listOf("loc.poh_study_6"),
                    built = listOf("loc.poh_telescope_1"),
                    flatpack = "obj.poh_flatpack_telescope1",
                ) {
                    material("obj.plank_oak", 2)
                    material("obj.molten_glass", 1)
                }
            }
            row("dbrow.poh_teak_telescope") {
                option(
                    "Teak telescope",
                    level = 64,
                    xp = 181.0,
                    hotspots = listOf("loc.poh_study_6"),
                    built = listOf("loc.poh_telescope_2"),
                    flatpack = "obj.poh_flatpack_telescope2",
                ) {
                    material("obj.plank_teak", 2)
                    material("obj.molten_glass", 1)
                }
            }
            row("dbrow.poh_mahogany_telescope") {
                option(
                    "Mahogany telescope",
                    level = 84,
                    xp = 580.0,
                    hotspots = listOf("loc.poh_study_6"),
                    built = listOf("loc.poh_telescope_3"),
                    flatpack = "obj.poh_flatpack_telescope3",
                ) {
                    material("obj.plank_mahogany", 2)
                    material("obj.molten_glass", 1)
                }
            }
            row("dbrow.poh_alchemical_chart") {
                option(
                    "Alchemical chart",
                    level = 43,
                    xp = 30.0,
                    hotspots = listOf("loc.poh_study_5"),
                    built = listOf("loc.poh_wallchart_1"),
                ) {
                    material("obj.cloth", 2)
                }
            }
            row("dbrow.poh_astronomical_chart") {
                option(
                    "Astronomical chart",
                    level = 63,
                    xp = 45.0,
                    hotspots = listOf("loc.poh_study_5"),
                    built = listOf("loc.poh_wallchart_2"),
                ) {
                    material("obj.cloth", 3)
                }
            }
            row("dbrow.poh_infernal_chart") {
                option(
                    "Infernal chart",
                    level = 83,
                    xp = 60.0,
                    hotspots = listOf("loc.poh_study_5"),
                    built = listOf("loc.poh_wallchart_3"),
                ) {
                    material("obj.cloth", 4)
                }
            }

            /* Chapel - altar/statue/window god variants follow the built icon at runtime. */
            // TODO(unmatched): Gnome child icon / Grid Master Icon - unlock objs absent from rev 240.
            row("dbrow.poh_oak_altar") {
                option(
                    "Oak altar",
                    level = 45,
                    xp = 240.0,
                    hotspots = listOf("loc.poh_chapel_2"),
                    built = listOf("loc.poh_altar_saradomin_1"),
                ) {
                    material("obj.plank_oak", 4)
                }
            }
            row("dbrow.poh_teak_altar") {
                option(
                    "Teak altar",
                    level = 50,
                    xp = 360.0,
                    hotspots = listOf("loc.poh_chapel_2"),
                    built = listOf("loc.poh_altar_saradomin_2"),
                ) {
                    material("obj.plank_teak", 4)
                }
            }
            row("dbrow.poh_cloth_altar") {
                option(
                    "Cloth altar",
                    level = 56,
                    xp = 390.0,
                    hotspots = listOf("loc.poh_chapel_2"),
                    built = listOf("loc.poh_altar_saradomin_3"),
                ) {
                    material("obj.plank_teak", 4)
                    material("obj.cloth", 2)
                }
            }
            row("dbrow.poh_mahogany_altar") {
                option(
                    "Mahogany altar",
                    level = 60,
                    xp = 590.0,
                    hotspots = listOf("loc.poh_chapel_2"),
                    built = listOf("loc.poh_altar_saradomin_4"),
                ) {
                    material("obj.plank_mahogany", 4)
                    material("obj.cloth", 2)
                }
            }
            row("dbrow.poh_limestone_altar") {
                option(
                    "Limestone altar",
                    level = 64,
                    xp = 910.0,
                    hotspots = listOf("loc.poh_chapel_2"),
                    built = listOf("loc.poh_altar_saradomin_5"),
                ) {
                    material("obj.plank_mahogany", 6)
                    material("obj.cloth", 2)
                    material("obj.limestonebrick", 2)
                }
            }
            row("dbrow.poh_marble_altar") {
                option(
                    "Marble altar",
                    level = 70,
                    xp = 1030.0,
                    hotspots = listOf("loc.poh_chapel_2"),
                    built = listOf("loc.poh_altar_saradomin_6"),
                ) {
                    material("obj.marble_block", 2)
                    material("obj.cloth", 2)
                }
            }
            row("dbrow.poh_gilded_altar") {
                option(
                    "Gilded altar",
                    level = 75,
                    xp = 2230.0,
                    hotspots = listOf("loc.poh_chapel_2"),
                    built = listOf("loc.poh_altar_saradomin_7"),
                ) {
                    material("obj.marble_block", 2)
                    material("obj.cloth", 2)
                    material("obj.gold_leaf", 4)
                }
            }
            row("dbrow.poh_steel_torches") {
                option(
                    "Steel torches",
                    level = 45,
                    xp = 40.0,
                    hotspots = listOf("loc.poh_chapel_3"),
                    built = listOf("loc.poh_torch_1"),
                ) {
                    material("obj.steel_bar", 2)
                }
            }
            row("dbrow.poh_wooden_torches") {
                option(
                    "Wooden torches",
                    level = 49,
                    xp = 58.0,
                    hotspots = listOf("loc.poh_chapel_3"),
                    built = listOf("loc.poh_torch_2"),
                    nails = 2,
                ) {
                    material("obj.woodplank", 2)
                }
            }
            row("dbrow.poh_steel_candlesticks") {
                option(
                    "Steel candlesticks",
                    level = 53,
                    xp = 124.0,
                    hotspots = listOf("loc.poh_chapel_3"),
                    built = listOf("loc.poh_torch_3"),
                ) {
                    material("obj.steel_bar", 6)
                    material("obj.unlit_candle", 6)
                }
            }
            row("dbrow.poh_gold_candlesticks") {
                option(
                    "Gold candlesticks",
                    level = 57,
                    xp = 46.0,
                    hotspots = listOf("loc.poh_chapel_3"),
                    built = listOf("loc.poh_torch_4"),
                ) {
                    material("obj.gold_bar", 6)
                    material("obj.unlit_candle", 6)
                }
            }
            row("dbrow.poh_oak_incense_burners") {
                option(
                    "Oak incense burners",
                    level = 61,
                    xp = 280.0,
                    hotspots = listOf("loc.poh_chapel_3"),
                    built = listOf("loc.poh_torch_5"),
                ) {
                    material("obj.plank_oak", 4)
                    material("obj.steel_bar", 2)
                }
            }
            row("dbrow.poh_mahogany_incense_burners") {
                option(
                    "Mahogany incense burners",
                    level = 65,
                    xp = 600.0,
                    hotspots = listOf("loc.poh_chapel_3"),
                    built = listOf("loc.poh_torch_6"),
                ) {
                    material("obj.plank_mahogany", 4)
                    material("obj.steel_bar", 2)
                }
            }
            row("dbrow.poh_marble_incense_burners") {
                option(
                    "Marble incense burners",
                    level = 69,
                    xp = 1600.0,
                    hotspots = listOf("loc.poh_chapel_3"),
                    built = listOf("loc.poh_torch_7"),
                ) {
                    material("obj.marble_block", 2)
                    material("obj.steel_bar", 2)
                }
            }
            row("dbrow.poh_saradomin_symbol") {
                option(
                    "Symbol of Saradomin",
                    level = 48,
                    xp = 120.0,
                    hotspots = listOf("loc.poh_chapel_1"),
                    built = listOf("loc.poh_icon_1"),
                ) {
                    material("obj.plank_oak", 2)
                }
            }
            row("dbrow.poh_zamorak_symbol") {
                option(
                    "Symbol of Zamorak",
                    level = 48,
                    xp = 120.0,
                    hotspots = listOf("loc.poh_chapel_1"),
                    built = listOf("loc.poh_icon_2"),
                ) {
                    material("obj.plank_oak", 2)
                }
            }
            row("dbrow.poh_guthix_symbol") {
                option(
                    "Symbol of Guthix",
                    level = 48,
                    xp = 120.0,
                    hotspots = listOf("loc.poh_chapel_1"),
                    built = listOf("loc.poh_icon_3"),
                ) {
                    material("obj.plank_oak", 2)
                }
            }
            row("dbrow.poh_saradomin_icon") {
                option(
                    "Icon of Saradomin",
                    level = 59,
                    xp = 960.0,
                    hotspots = listOf("loc.poh_chapel_1"),
                    built = listOf("loc.poh_icon_4"),
                ) {
                    material("obj.plank_teak", 4)
                    material("obj.gold_leaf", 2)
                }
            }
            row("dbrow.poh_zamorak_icon") {
                option(
                    "Icon of Zamorak",
                    level = 59,
                    xp = 960.0,
                    hotspots = listOf("loc.poh_chapel_1"),
                    built = listOf("loc.poh_icon_5"),
                ) {
                    material("obj.plank_teak", 4)
                    material("obj.gold_leaf", 2)
                }
            }
            row("dbrow.poh_guthix_icon") {
                option(
                    "Icon of Guthix",
                    level = 59,
                    xp = 960.0,
                    hotspots = listOf("loc.poh_chapel_1"),
                    built = listOf("loc.poh_icon_6"),
                ) {
                    material("obj.plank_teak", 4)
                    material("obj.gold_leaf", 2)
                }
            }
            row("dbrow.poh_icon_of_bob") {
                option(
                    "Icon of Bob",
                    level = 71,
                    xp = 1160.0,
                    hotspots = listOf("loc.poh_chapel_1"),
                    built = listOf("loc.poh_icon_7"),
                ) {
                    material("obj.plank_mahogany", 4)
                    material("obj.gold_leaf", 2)
                }
            }
            row("dbrow.poh_chapel_windchimes") {
                option(
                    "Windchimes",
                    level = 49,
                    xp = 323.0,
                    hotspots = listOf("loc.poh_chapel_7"),
                    built = listOf("loc.poh_musical_thing_1"),
                    nails = 4,
                ) {
                    material("obj.plank_oak", 4)
                    material("obj.steel_bar", 4)
                }
            }
            row("dbrow.poh_chapel_bells") {
                option(
                    "Bells",
                    level = 58,
                    xp = 480.0,
                    hotspots = listOf("loc.poh_chapel_7"),
                    built = listOf("loc.poh_musical_thing_2"),
                ) {
                    material("obj.plank_teak", 4)
                    material("obj.steel_bar", 6)
                }
            }
            row("dbrow.poh_chapel_organ") {
                option(
                    "Organ",
                    level = 69,
                    xp = 680.0,
                    hotspots = listOf("loc.poh_chapel_7"),
                    built = listOf("loc.poh_musical_thing_3"),
                ) {
                    material("obj.plank_mahogany", 4)
                    material("obj.steel_bar", 6)
                }
            }
            row("dbrow.poh_small_statue") {
                option(
                    "Small statue",
                    level = 49,
                    xp = 40.0,
                    hotspots = listOf("loc.poh_chapel_6"),
                    built = listOf("loc.poh_statue_monk"),
                ) {
                    material("obj.limestonebrick", 2)
                }
            }
            row("dbrow.poh_medium_statue") {
                option(
                    "Medium statue",
                    level = 69,
                    xp = 500.0,
                    hotspots = listOf("loc.poh_chapel_6"),
                    built = listOf("loc.poh_statue_angel"),
                ) {
                    material("obj.marble_block", 1)
                }
            }
            row("dbrow.poh_large_statue") {
                option(
                    "Large statue",
                    level = 89,
                    xp = 1500.0,
                    hotspots = listOf("loc.poh_chapel_6"),
                    built = listOf("loc.poh_statue_saradomin"),
                ) {
                    material("obj.marble_block", 3)
                }
            }
            row("dbrow.poh_shuttered_window") {
                option(
                    "Shuttered window",
                    level = 49,
                    xp = 228.0,
                    hotspots = listOf("loc.poh_chapelwindow_hotspot_rimmington"),
                    built = listOf("loc.poh_rimmington_window_shutters"),
                    groupBuild = true,
                    nails = 8,
                ) {
                    material("obj.woodplank", 8)
                }
            }
            row("dbrow.poh_decorative_window") {
                option(
                    "Decorative window",
                    level = 69,
                    xp = 4.0,
                    hotspots = listOf("loc.poh_chapelwindow_hotspot_rimmington"),
                    built = listOf("loc.poh_rimmington_window_saradomin"),
                    groupBuild = true,
                ) {
                    material("obj.molten_glass", 8)
                }
            }
            row("dbrow.poh_stained_glass") {
                option(
                    "Stained glass",
                    level = 89,
                    xp = 5.0,
                    hotspots = listOf("loc.poh_chapelwindow_hotspot_rimmington"),
                    built = listOf("loc.poh_rimmington_window_saradomin2"),
                    groupBuild = true,
                ) {
                    material("obj.molten_glass", 16)
                }
            }

            /* Throne room. */
            row("dbrow.poh_oak_throne") {
                option(
                    "Oak throne",
                    level = 60,
                    xp = 800.0,
                    hotspots = listOf("loc.poh_throne_room_1"),
                    built = listOf("loc.poh_throne_1"),
                ) {
                    material("obj.plank_oak", 5)
                    material("obj.marble_block", 1)
                }
            }
            row("dbrow.poh_teak_throne") {
                option(
                    "Teak throne",
                    level = 67,
                    xp = 1450.0,
                    hotspots = listOf("loc.poh_throne_room_1"),
                    built = listOf("loc.poh_throne_2"),
                ) {
                    material("obj.plank_teak", 5)
                    material("obj.marble_block", 2)
                }
            }
            row("dbrow.poh_mahogany_throne") {
                option(
                    "Mahogany throne",
                    level = 74,
                    xp = 2200.0,
                    hotspots = listOf("loc.poh_throne_room_1"),
                    built = listOf("loc.poh_throne_3"),
                ) {
                    material("obj.plank_mahogany", 5)
                    material("obj.marble_block", 3)
                }
            }
            row("dbrow.poh_gilded_throne") {
                option(
                    "Gilded throne",
                    level = 81,
                    xp = 2600.0,
                    hotspots = listOf("loc.poh_throne_room_1"),
                    built = listOf("loc.poh_throne_4"),
                ) {
                    material("obj.plank_mahogany", 5)
                    material("obj.marble_block", 2)
                    material("obj.gold_leaf", 3)
                }
            }
            row("dbrow.poh_skeleton_throne") {
                option(
                    "Skeleton throne",
                    level = 88,
                    xp = 7003.0,
                    hotspots = listOf("loc.poh_throne_room_1"),
                    built = listOf("loc.poh_throne_5"),
                ) {
                    material("obj.poh_magic_crystal", 5)
                    material("obj.marble_block", 4)
                    material("obj.bones", 5)
                    material("obj.skull", 2)
                }
            }
            row("dbrow.poh_crystal_throne") {
                option(
                    "Crystal throne",
                    level = 95,
                    xp = 15000.0,
                    hotspots = listOf("loc.poh_throne_room_1"),
                    built = listOf("loc.poh_throne_6"),
                ) {
                    material("obj.poh_magic_crystal", 15)
                }
            }
            row("dbrow.poh_demonic_throne") {
                option(
                    "Demonic throne",
                    level = 99,
                    xp = 25000.0,
                    hotspots = listOf("loc.poh_throne_room_1"),
                    built = listOf("loc.poh_throne_7"),
                ) {
                    material("obj.poh_magic_crystal", 25)
                }
            }
            row("dbrow.poh_oak_lever") {
                option(
                    "Oak lever",
                    level = 68,
                    xp = 300.0,
                    hotspots = listOf("loc.poh_throne_room_4"),
                    built = listOf("loc.poh_lever_oak_4"),
                ) {
                    material("obj.plank_oak", 5)
                }
            }
            row("dbrow.poh_teak_lever") {
                option(
                    "Teak lever",
                    level = 78,
                    xp = 450.0,
                    hotspots = listOf("loc.poh_throne_room_4"),
                    built = listOf("loc.poh_lever_teak_4"),
                ) {
                    material("obj.plank_teak", 5)
                }
            }
            row("dbrow.poh_mahogany_lever") {
                option(
                    "Mahogany lever",
                    level = 88,
                    xp = 700.0,
                    hotspots = listOf("loc.poh_throne_room_4"),
                    built = listOf("loc.poh_lever_mag_4"),
                ) {
                    material("obj.plank_mahogany", 5)
                }
            }
            row("dbrow.poh_oak_trapdoor") {
                option(
                    "Oak trapdoor",
                    level = 68,
                    xp = 300.0,
                    hotspots = listOf("loc.poh_throne_room_7"),
                    built = listOf("loc.poh_trapdoor_oak_7"),
                ) {
                    material("obj.plank_oak", 5)
                }
            }
            row("dbrow.poh_teak_trapdoor") {
                option(
                    "Teak trapdoor",
                    level = 78,
                    xp = 450.0,
                    hotspots = listOf("loc.poh_throne_room_7"),
                    built = listOf("loc.poh_trapdoor_teak_7"),
                ) {
                    material("obj.plank_teak", 5)
                }
            }
            row("dbrow.poh_mahogany_trapdoor") {
                option(
                    "Mahogany trapdoor",
                    level = 88,
                    xp = 700.0,
                    hotspots = listOf("loc.poh_throne_room_7"),
                    built = listOf("loc.poh_trapdoor_mag_7"),
                ) {
                    material("obj.plank_mahogany", 5)
                }
            }
            row("dbrow.poh_floor_decoration") {
                option(
                    "Floor decoration",
                    level = 61,
                    xp = 700.0,
                    hotspots = listOf("loc.poh_throne_room_3_rimmington"),
                    built = listOf("loc.poh_floordecor_rimmington"),
                    groupBuild = true,
                ) {
                    material("obj.plank_mahogany", 5)
                }
            }
            row("dbrow.poh_throne_steel_cage") {
                option(
                    "Steel cage",
                    level = 68,
                    xp = 1100.0,
                    hotspots = listOf("loc.poh_throne_room_3_rimmington"),
                    built = listOf("loc.poh_cage_throneroom"),
                    groupBuild = true,
                ) {
                    material("obj.plank_mahogany", 5)
                    material("obj.steel_bar", 20)
                }
            }
            row("dbrow.poh_trapdoor_floor") {
                option(
                    "Trapdoor",
                    level = 74,
                    xp = 770.0,
                    hotspots = listOf("loc.poh_throne_room_3_rimmington"),
                    built = listOf("loc.poh_floordecor_rimmington"),
                    groupBuild = true,
                ) {
                    material("obj.plank_mahogany", 5)
                    material("obj.poh_clockwork_mechanism", 10)
                }
            }
            row("dbrow.poh_lesser_magic_cage") {
                option(
                    "Lesser magic cage",
                    level = 82,
                    xp = 2700.0,
                    hotspots = listOf("loc.poh_throne_room_3_rimmington"),
                    built = listOf("loc.poh_magic_cage_lesser"),
                    groupBuild = true,
                ) {
                    material("obj.plank_mahogany", 5)
                    material("obj.poh_magic_crystal", 2)
                }
            }
            row("dbrow.poh_greater_magic_cage") {
                option(
                    "Greater magic cage",
                    level = 89,
                    xp = 4700.0,
                    hotspots = listOf("loc.poh_throne_room_3_rimmington"),
                    built = listOf("loc.poh_magic_cage_greater"),
                    groupBuild = true,
                ) {
                    material("obj.plank_mahogany", 5)
                    material("obj.poh_magic_crystal", 4)
                }
            }
            row("dbrow.poh_throne_carved_teak_bench") {
                option(
                    "Carved teak bench",
                    level = 44,
                    xp = 360.0,
                    hotspots = listOf("loc.poh_throne_room_5"),
                    built = listOf("loc.poh_throneroom_bench_1"),
                    groupBuild = true,
                ) {
                    material("obj.plank_teak", 4)
                }
            }
            row("dbrow.poh_throne_carved_teak_bench_2") {
                option(
                    "Carved teak bench",
                    level = 44,
                    xp = 360.0,
                    hotspots = listOf("loc.poh_throne_room_6"),
                    built = listOf("loc.poh_throneroom_bench_1"),
                    groupBuild = true,
                ) {
                    material("obj.plank_teak", 4)
                }
            }
            row("dbrow.poh_throne_mahogany_bench") {
                option(
                    "Mahogany bench",
                    level = 52,
                    xp = 560.0,
                    hotspots = listOf("loc.poh_throne_room_5"),
                    built = listOf("loc.poh_throneroom_bench_2"),
                    groupBuild = true,
                ) {
                    material("obj.plank_mahogany", 4)
                }
            }
            row("dbrow.poh_throne_mahogany_bench_2") {
                option(
                    "Mahogany bench",
                    level = 52,
                    xp = 560.0,
                    hotspots = listOf("loc.poh_throne_room_6"),
                    built = listOf("loc.poh_throneroom_bench_2"),
                    groupBuild = true,
                ) {
                    material("obj.plank_mahogany", 4)
                }
            }
            row("dbrow.poh_throne_gilded_bench") {
                option(
                    "Gilded bench",
                    level = 61,
                    xp = 1760.0,
                    hotspots = listOf("loc.poh_throne_room_5"),
                    built = listOf("loc.poh_throneroom_bench_3"),
                    groupBuild = true,
                ) {
                    material("obj.plank_mahogany", 4)
                    material("obj.gold_leaf", 4)
                }
            }
            row("dbrow.poh_throne_gilded_bench_2") {
                option(
                    "Gilded bench",
                    level = 61,
                    xp = 1760.0,
                    hotspots = listOf("loc.poh_throne_room_6"),
                    built = listOf("loc.poh_throneroom_bench_3"),
                    groupBuild = true,
                ) {
                    material("obj.plank_mahogany", 4)
                    material("obj.gold_leaf", 4)
                }
            }
            row("dbrow.poh_round_shield") {
                option(
                    "Round shield",
                    level = 66,
                    xp = 120.0,
                    hotspots = listOf("loc.poh_throne_room_3_q", "loc.poh_dungeon_treasure_5"),
                    built = listOf("loc.poh_round_shield_arrav", "loc.poh_round_shield_arrav"),
                ) {
                    material("obj.plank_oak", 2)
                }
            }
            row("dbrow.poh_square_shield") {
                option(
                    "Square shield",
                    level = 76,
                    xp = 360.0,
                    hotspots = listOf("loc.poh_throne_room_3_q", "loc.poh_dungeon_treasure_5"),
                    built = listOf("loc.poh_square_shield_arrav", "loc.poh_square_shield_arrav"),
                ) {
                    material("obj.plank_teak", 4)
                }
            }
            row("dbrow.poh_kite_shield") {
                option(
                    "Kite shield",
                    level = 86,
                    xp = 420.0,
                    hotspots = listOf("loc.poh_throne_room_3_q", "loc.poh_dungeon_treasure_5"),
                    built = listOf("loc.poh_kite_shield_arrav", "loc.poh_kite_shield_arrav"),
                ) {
                    material("obj.plank_mahogany", 3)
                }
            }

            /* Games room. */
            row("dbrow.poh_lesser_magical_balance") {
                option(
                    "Lesser magical balance",
                    level = 37,
                    xp = 176.0,
                    hotspots = listOf("loc.poh_games_room_6"),
                    built = listOf("loc.poh_elemental_orb_1"),
                ) {
                    material("obj.airrune", 500)
                    material("obj.earthrune", 500)
                    material("obj.firerune", 500)
                    material("obj.waterrune", 500)
                }
            }
            row("dbrow.poh_medium_magical_balance") {
                option(
                    "Medium balance",
                    level = 57,
                    xp = 252.0,
                    hotspots = listOf("loc.poh_games_room_6"),
                    built = listOf("loc.poh_elemental_orb_2"),
                ) {
                    material("obj.airrune", 1000)
                    material("obj.earthrune", 1000)
                    material("obj.firerune", 1000)
                    material("obj.waterrune", 1000)
                }
            }
            row("dbrow.poh_greater_magical_balance") {
                option(
                    "Greater magical balance",
                    level = 77,
                    xp = 356.0,
                    hotspots = listOf("loc.poh_games_room_6"),
                    built = listOf("loc.poh_elemental_orb_3"),
                ) {
                    material("obj.airrune", 2000)
                    material("obj.earthrune", 2000)
                    material("obj.firerune", 2000)
                    material("obj.waterrune", 2000)
                }
            }
            row("dbrow.poh_jester") {
                option(
                    "Jester",
                    level = 39,
                    xp = 360.0,
                    hotspots = listOf("loc.poh_games_room_2"),
                    built = listOf("loc.poh_mime_jester"),
                ) {
                    material("obj.plank_teak", 4)
                }
            }
            row("dbrow.poh_treasure_hunt") {
                option(
                    "Treasure hunt",
                    level = 49,
                    xp = 800.0,
                    hotspots = listOf("loc.poh_games_room_2"),
                    built = listOf("loc.poh_fairy_house"),
                ) {
                    material("obj.plank_teak", 8)
                    material("obj.steel_bar", 4)
                }
            }
            row("dbrow.poh_hangman") {
                option(
                    "Hangman",
                    level = 59,
                    xp = 1200.0,
                    hotspots = listOf("loc.poh_games_room_2"),
                    built = listOf("loc.poh_hangman_chest"),
                ) {
                    material("obj.plank_teak", 12)
                    material("obj.steel_bar", 6)
                }
            }
            row("dbrow.poh_oak_prize_chest") {
                option(
                    "Oak prize chest",
                    level = 34,
                    xp = 240.0,
                    hotspots = listOf("loc.poh_games_room_4"),
                    built = listOf("loc.poh_prize_chest_oak_closed"),
                ) {
                    material("obj.plank_oak", 4)
                }
            }
            row("dbrow.poh_teak_prize_chest") {
                option(
                    "Teak prize chest",
                    level = 44,
                    xp = 660.0,
                    hotspots = listOf("loc.poh_games_room_4"),
                    built = listOf("loc.poh_prize_chest_teak_closed"),
                ) {
                    material("obj.plank_teak", 4)
                    material("obj.gold_leaf", 1)
                }
            }
            row("dbrow.poh_mahogany_prize_chest") {
                option(
                    "Mahogany prize chest",
                    level = 54,
                    xp = 860.0,
                    hotspots = listOf("loc.poh_games_room_4"),
                    built = listOf("loc.poh_prize_chest_mag_closed"),
                ) {
                    material("obj.plank_mahogany", 4)
                    material("obj.gold_leaf", 1)
                }
            }
            row("dbrow.poh_hoop_and_stick") {
                option(
                    "Hoop and stick",
                    level = 30,
                    xp = 120.0,
                    hotspots = listOf("loc.poh_games_room_7"),
                    built = listOf("loc.poh_stick+hoop1"),
                ) {
                    material("obj.plank_oak", 2)
                }
            }
            row("dbrow.poh_dartboard") {
                option(
                    "Dartboard",
                    level = 54,
                    xp = 290.0,
                    hotspots = listOf("loc.poh_games_room_7"),
                    built = listOf("loc.poh_dartboard1"),
                ) {
                    material("obj.plank_teak", 3)
                    material("obj.steel_bar", 1)
                }
            }
            row("dbrow.poh_archery_target") {
                option(
                    "Archery target",
                    level = 81,
                    xp = 600.0,
                    hotspots = listOf("loc.poh_games_room_7"),
                    built = listOf("loc.poh_archery_target1"),
                ) {
                    material("obj.plank_teak", 6)
                    material("obj.steel_bar", 3)
                }
            }
            row("dbrow.poh_clay_attack_stone") {
                option(
                    "Clay attack stone",
                    level = 39,
                    xp = 100.0,
                    hotspots = listOf("loc.poh_games_room_5"),
                    built = listOf("loc.poh_tbt_clay_new"),
                ) {
                    material("obj.softclay", 10)
                }
            }
            row("dbrow.poh_limestone_attack_stone") {
                option(
                    "Limestone attack stone",
                    level = 59,
                    xp = 200.0,
                    hotspots = listOf("loc.poh_games_room_5"),
                    built = listOf("loc.poh_tbt_limestone_new"),
                ) {
                    material("obj.limestonebrick", 10)
                }
            }
            row("dbrow.poh_marble_attack_stone") {
                option(
                    "Marble attack stone",
                    level = 79,
                    xp = 2000.0,
                    hotspots = listOf("loc.poh_games_room_5"),
                    built = listOf("loc.poh_tbt_marble_new"),
                ) {
                    material("obj.marble_block", 4)
                }
            }

            /* Combat room - ring pieces map floor mats + combat-family walls per option. */
            // TODO(unmatched): Balance beam - poh_balancebeam_endl/middle/endr cannot be index-aligned to
            // the single poh_gr_1_wall_agility hotspot loc.
            row("dbrow.poh_boxing_ring") {
                option(
                    "Boxing ring",
                    level = 32,
                    xp = 420.0,
                    hotspots =
                        listOf(
                            "loc.poh_gr_1_floor_middle",
                            "loc.poh_gr_1_floor_side",
                            "loc.poh_gr_1_floor_n",
                            "loc.poh_gr_1_floor_s",
                            "loc.poh_gr_1_floor_ne",
                            "loc.poh_gr_1_floor_nw",
                            "loc.poh_gr_1_floor_se",
                            "loc.poh_gr_1_floor_sw",
                            "loc.poh_gr_1_wall_combat",
                            "loc.poh_gr_1_wall_combat_agility",
                            "loc.poh_gr_1_wall_ranging_combat",
                            "loc.poh_gr_1_wall_everything",
                            "loc.poh_gr_1_wall_cobat_corner",
                            "loc.poh_gr_1_wall_combat_agility_corner",
                            "loc.poh_gr_1_wall_redcorner",
                            "loc.poh_gr_1_wall_bluecorner",
                        ),
                    built =
                        listOf(
                            "loc.poh_boxing_ring_mat_middle",
                            "loc.poh_boxing_ring_mat_side",
                            "loc.poh_boxing_ring_mat_side",
                            "loc.poh_boxing_ring_mat_side",
                            "loc.poh_boxing_ring_mat_corner",
                            "loc.poh_boxing_ring_mat_corner",
                            "loc.poh_boxing_ring_mat_corner",
                            "loc.poh_boxing_ring_mat_corner",
                            "loc.poh_boxing_ringwall_white",
                            "loc.poh_boxing_ringwall_white",
                            "loc.poh_boxing_ringwall_white",
                            "loc.poh_boxing_ringwall_white",
                            "loc.poh_boxing_ringwall_corner",
                            "loc.poh_boxing_ringwall_corner",
                            "loc.poh_boxing_ringwall_red",
                            "loc.poh_boxing_ringwall_blue",
                        ),
                    groupBuild = true,
                ) {
                    material("obj.plank_oak", 6)
                    material("obj.cloth", 4)
                }
            }
            row("dbrow.poh_fencing_ring") {
                option(
                    "Fencing ring",
                    level = 41,
                    xp = 570.0,
                    hotspots =
                        listOf(
                            "loc.poh_gr_1_floor_middle",
                            "loc.poh_gr_1_floor_side",
                            "loc.poh_gr_1_floor_n",
                            "loc.poh_gr_1_floor_s",
                            "loc.poh_gr_1_floor_ne",
                            "loc.poh_gr_1_floor_nw",
                            "loc.poh_gr_1_floor_se",
                            "loc.poh_gr_1_floor_sw",
                            "loc.poh_gr_1_wall_combat",
                            "loc.poh_gr_1_wall_combat_agility",
                            "loc.poh_gr_1_wall_ranging_combat",
                            "loc.poh_gr_1_wall_everything",
                            "loc.poh_gr_1_wall_cobat_corner",
                            "loc.poh_gr_1_wall_combat_agility_corner",
                            "loc.poh_gr_1_wall_redcorner",
                            "loc.poh_gr_1_wall_bluecorner",
                        ),
                    built =
                        listOf(
                            "loc.poh_fencing_ring_mat_middle",
                            "loc.poh_fencing_ring_mat_side",
                            "loc.poh_fencing_ring_mat_side",
                            "loc.poh_fencing_ring_mat_side",
                            "loc.poh_fencing_ring_mat_corner",
                            "loc.poh_fencing_ring_mat_corner",
                            "loc.poh_fencing_ring_mat_corner",
                            "loc.poh_fencing_ring_mat_corner",
                            "loc.poh_fencing_ringwall",
                            "loc.poh_fencing_ringwall",
                            "loc.poh_fencing_ringwall",
                            "loc.poh_fencing_ringwall",
                            "loc.poh_fencing_ringwall",
                            "loc.poh_fencing_ringwall",
                            "loc.poh_fencing_ringwall",
                            "loc.poh_fencing_ringwall",
                        ),
                    groupBuild = true,
                ) {
                    material("obj.plank_oak", 8)
                    material("obj.cloth", 6)
                }
            }
            row("dbrow.poh_combat_ring") {
                option(
                    "Combat ring",
                    level = 51,
                    xp = 630.0,
                    hotspots =
                        listOf(
                            "loc.poh_gr_1_floor_middle",
                            "loc.poh_gr_1_floor_side",
                            "loc.poh_gr_1_floor_n",
                            "loc.poh_gr_1_floor_s",
                            "loc.poh_gr_1_floor_ne",
                            "loc.poh_gr_1_floor_nw",
                            "loc.poh_gr_1_floor_se",
                            "loc.poh_gr_1_floor_sw",
                            "loc.poh_gr_1_wall_combat",
                            "loc.poh_gr_1_wall_combat_agility",
                            "loc.poh_gr_1_wall_ranging_combat",
                            "loc.poh_gr_1_wall_everything",
                            "loc.poh_gr_1_wall_cobat_corner",
                            "loc.poh_gr_1_wall_combat_agility_corner",
                            "loc.poh_gr_1_wall_redcorner",
                            "loc.poh_gr_1_wall_bluecorner",
                        ),
                    built =
                        listOf(
                            "loc.poh_combat_mat_middle",
                            "loc.poh_combat_mat_side",
                            "loc.poh_combat_mat_side",
                            "loc.poh_combat_mat_side",
                            "loc.poh_combat_mat_corner",
                            "loc.poh_combat_mat_corner",
                            "loc.poh_combat_mat_corner",
                            "loc.poh_combat_mat_corner",
                            "loc.poh_combat_ringwall",
                            "loc.poh_combat_ringwall",
                            "loc.poh_combat_ringwall",
                            "loc.poh_combat_ringwall",
                            "loc.poh_combat_ringwall",
                            "loc.poh_combat_ringwall",
                            "loc.poh_combat_ringwall",
                            "loc.poh_combat_ringwall",
                        ),
                    groupBuild = true,
                ) {
                    material("obj.plank_teak", 6)
                    material("obj.cloth", 6)
                }
            }
            row("dbrow.poh_ranging_pedestals") {
                option(
                    "Ranging pedestals",
                    level = 71,
                    xp = 720.0,
                    hotspots =
                        listOf(
                            "loc.poh_gr_1_floor_middle",
                            "loc.poh_gr_1_floor_side",
                            "loc.poh_gr_1_floor_n",
                            "loc.poh_gr_1_floor_s",
                            "loc.poh_gr_1_floor_ne",
                            "loc.poh_gr_1_floor_nw",
                            "loc.poh_gr_1_floor_se",
                            "loc.poh_gr_1_floor_sw",
                            "loc.poh_gr_1_wall_ranging",
                            "loc.poh_gr_1_wall_ranging_agility",
                            "loc.poh_gr_1_wall_ranging_combat",
                            "loc.poh_gr_1_wall_everything",
                        ),
                    built =
                        listOf(
                            "loc.poh_magic_circle_mat",
                            "loc.poh_magic_circle_mat",
                            "loc.poh_magic_circle_mat",
                            "loc.poh_magic_circle_mat",
                            "loc.poh_magic_circle_mat",
                            "loc.poh_magic_circle_mat",
                            "loc.poh_magic_circle_mat",
                            "loc.poh_magic_circle_mat",
                            "loc.poh_magic_circle_wall",
                            "loc.poh_magic_circle_wall",
                            "loc.poh_magic_circle_wall",
                            "loc.poh_magic_circle_wall",
                        ),
                    groupBuild = true,
                ) {
                    material("obj.plank_teak", 8)
                }
            }
            row("dbrow.poh_boxing_glove_rack") {
                option(
                    "Boxing glove rack",
                    level = 34,
                    xp = 120.0,
                    hotspots = listOf("loc.poh_combat_room_4"),
                    built = listOf("loc.poh_weapons_rack_gloves"),
                ) {
                    material("obj.plank_oak", 2)
                }
            }
            row("dbrow.poh_weapons_rack") {
                option(
                    "Weapons rack",
                    level = 44,
                    xp = 180.0,
                    hotspots = listOf("loc.poh_combat_room_4"),
                    built = listOf("loc.poh_weapons_rack_gloves+woodenstuff"),
                ) {
                    material("obj.plank_teak", 2)
                }
            }
            row("dbrow.poh_extra_weapons_rack") {
                option(
                    "Extra weapons rack",
                    level = 54,
                    xp = 440.0,
                    hotspots = listOf("loc.poh_combat_room_4"),
                    built = listOf("loc.poh_weapons_rack_gloves+woodenstuff+pugels"),
                ) {
                    material("obj.plank_teak", 4)
                    material("obj.steel_bar", 4)
                }
            }
            row("dbrow.poh_combat_dummy_row") {
                option(
                    "Combat dummy",
                    level = 48,
                    xp = 660.0,
                    hotspots = listOf("loc.poh_combat_room_6"),
                    built = listOf("loc.poh_combat_dummy"),
                ) {
                    material("obj.plank_teak", 5)
                    material("obj.cloth", 4)
                    material("obj.bucket_sand", 5)
                }
            }
            row("dbrow.poh_undead_combat_dummy") {
                option(
                    "Undead combat dummy",
                    level = 53,
                    xp = 220.0,
                    hotspots = listOf("loc.poh_combat_dummy"),
                    built = listOf("loc.poh_combat_dummy_undeadslayer"),
                ) {
                    material("obj.harmless_black_mask", 1)
                    material("obj.bucket_ectoplasm", 4)
                }
            }
            row("dbrow.poh_ornate_undead_combat_dummy") {
                option(
                    "Ornate undead combat dummy",
                    level = 58,
                    xp = 300.0,
                    hotspots = listOf("loc.poh_combat_dummy_undeadslayer"),
                    built = listOf("loc.poh_combat_dummy_upgraded_1_undead"),
                ) {
                    material("obj.gold_leaf", 1)
                }
            }

            /* Portal chamber - directing a frame to a destination is runtime state. */
            // TODO(unmatched): Raging echoes portal - cosmetic unlock scroll obj absent from rev 240.
            row("dbrow.poh_teak_portal") {
                option(
                    "Teak portal",
                    level = 50,
                    xp = 270.0,
                    hotspots =
                        listOf(
                            "loc.poh_teleroom_1",
                            "loc.poh_teleroom_2",
                            "loc.poh_teleroom_3",
                        ),
                    built =
                        listOf(
                            "loc.poh_portal_teak_empty",
                            "loc.poh_portal_teak_empty",
                            "loc.poh_portal_teak_empty",
                        ),
                ) {
                    material("obj.plank_teak", 3)
                }
            }
            row("dbrow.poh_mahogany_portal") {
                option(
                    "Mahogany portal",
                    level = 65,
                    xp = 420.0,
                    hotspots =
                        listOf(
                            "loc.poh_teleroom_1",
                            "loc.poh_teleroom_2",
                            "loc.poh_teleroom_3",
                        ),
                    built =
                        listOf(
                            "loc.poh_portal_mag_empty",
                            "loc.poh_portal_mag_empty",
                            "loc.poh_portal_mag_empty",
                        ),
                ) {
                    material("obj.plank_mahogany", 3)
                }
            }
            row("dbrow.poh_marble_portal") {
                option(
                    "Marble portal",
                    level = 80,
                    xp = 1500.0,
                    hotspots =
                        listOf(
                            "loc.poh_teleroom_1",
                            "loc.poh_teleroom_2",
                            "loc.poh_teleroom_3",
                        ),
                    built =
                        listOf(
                            "loc.poh_portal_marble_empty",
                            "loc.poh_portal_marble_empty",
                            "loc.poh_portal_marble_empty",
                        ),
                ) {
                    material("obj.marble_block", 3)
                }
            }
            row("dbrow.poh_teleport_focus") {
                option(
                    "Teleport focus",
                    level = 50,
                    xp = 40.0,
                    hotspots = listOf("loc.poh_teleroom_7"),
                    built = listOf("loc.poh_teleport_centrepiece"),
                ) {
                    material("obj.limestonebrick", 2)
                }
            }
            row("dbrow.poh_greater_teleport_focus") {
                option(
                    "Greater teleport focus",
                    level = 65,
                    xp = 500.0,
                    hotspots = listOf("loc.poh_teleroom_7"),
                    built = listOf("loc.poh_teleport_centrepiece_grand"),
                ) {
                    material("obj.marble_block", 1)
                }
            }
            row("dbrow.poh_scrying_pool") {
                option(
                    "Scrying pool",
                    level = 80,
                    xp = 2000.0,
                    hotspots = listOf("loc.poh_teleroom_7"),
                    built = listOf("loc.poh_scrying_pool"),
                ) {
                    material("obj.marble_block", 4)
                }
            }

            /* Costume room - tiers above oak upgrade the previous built case/rack/box. */
            row("dbrow.poh_oak_armour_case") {
                option(
                    "Oak armour case",
                    level = 46,
                    xp = 180.0,
                    hotspots = listOf("loc.poh_cos_room_armour_case_hotspot"),
                    built = listOf("loc.poh_cos_room_armour_case_oak"),
                    flatpack = "obj.poh_flatpack_armour_case",
                ) {
                    material("obj.plank_oak", 3)
                }
            }
            row("dbrow.poh_teak_armour_case") {
                option(
                    "Teak armour case",
                    level = 64,
                    xp = 270.0,
                    hotspots = listOf("loc.poh_cos_room_armour_case_oak"),
                    built = listOf("loc.poh_cos_room_armour_case_teak"),
                    flatpack = "obj.poh_flatpack_armour_case2",
                ) {
                    material("obj.plank_teak", 3)
                }
            }
            row("dbrow.poh_mahogany_armour_case") {
                option(
                    "Mahogany armour case",
                    level = 82,
                    xp = 420.0,
                    hotspots = listOf("loc.poh_cos_room_armour_case_teak"),
                    built = listOf("loc.poh_cos_room_armour_case_mahogany"),
                    flatpack = "obj.poh_flatpack_armour_case3",
                ) {
                    material("obj.plank_mahogany", 3)
                }
            }
            row("dbrow.poh_oak_cape_rack") {
                option(
                    "Oak cape rack",
                    level = 54,
                    xp = 240.0,
                    hotspots = listOf("loc.poh_cos_room_cape_rack_hotspot"),
                    built = listOf("loc.poh_cos_room_cape_rack_oak"),
                    flatpack = "obj.poh_flatpack_cape_rack",
                ) {
                    material("obj.plank_oak", 4)
                }
            }
            row("dbrow.poh_teak_cape_rack") {
                option(
                    "Teak cape rack",
                    level = 63,
                    xp = 360.0,
                    hotspots = listOf("loc.poh_cos_room_cape_rack_oak"),
                    built = listOf("loc.poh_cos_room_cape_rack_teak"),
                    flatpack = "obj.poh_flatpack_cape_rack2",
                ) {
                    material("obj.plank_teak", 4)
                }
            }
            row("dbrow.poh_mahogany_cape_rack") {
                option(
                    "Mahogany cape rack",
                    level = 72,
                    xp = 560.0,
                    hotspots = listOf("loc.poh_cos_room_cape_rack_teak"),
                    built = listOf("loc.poh_cos_room_cape_rack_mahogany"),
                    flatpack = "obj.poh_flatpack_cape_rack3",
                ) {
                    material("obj.plank_mahogany", 4)
                }
            }
            row("dbrow.poh_gilded_cape_rack") {
                option(
                    "Gilded cape rack",
                    level = 81,
                    xp = 860.0,
                    hotspots = listOf("loc.poh_cos_room_cape_rack_mahogany"),
                    built = listOf("loc.poh_cos_room_cape_rack_mahogany_gilded"),
                    flatpack = "obj.poh_flatpack_cape_rack4",
                ) {
                    material("obj.plank_mahogany", 4)
                    material("obj.gold_leaf", 1)
                }
            }
            row("dbrow.poh_marble_cape_rack") {
                option(
                    "Marble cape rack",
                    level = 90,
                    xp = 500.0,
                    hotspots = listOf("loc.poh_cos_room_cape_rack_mahogany_gilded"),
                    built = listOf("loc.poh_cos_room_cape_rack_marble"),
                    flatpack = "obj.poh_flatpack_cape_rack5",
                ) {
                    material("obj.marble_block", 1)
                }
            }
            row("dbrow.poh_magical_cape_rack") {
                option(
                    "Magical cape rack",
                    level = 99,
                    xp = 1000.0,
                    hotspots = listOf("loc.poh_cos_room_cape_rack_marble"),
                    built = listOf("loc.poh_cos_room_cape_rack_magic_stone"),
                    flatpack = "obj.poh_flatpack_cape_rack6",
                ) {
                    material("obj.poh_magic_crystal", 1)
                }
            }
            row("dbrow.poh_oak_fancy_dress_box") {
                option(
                    "Oak fancy dress box",
                    level = 44,
                    xp = 120.0,
                    hotspots = listOf("loc.poh_cos_room_fancy_dress_box_hotspot"),
                    built = listOf("loc.poh_cos_room_fancy_dress_box_oak"),
                    flatpack = "obj.poh_flatpack_fancy_dress_box",
                ) {
                    material("obj.plank_oak", 2)
                }
            }
            row("dbrow.poh_teak_fancy_dress_box") {
                option(
                    "Teak fancy dress box",
                    level = 62,
                    xp = 180.0,
                    hotspots = listOf("loc.poh_cos_room_fancy_dress_box_oak"),
                    built = listOf("loc.poh_cos_room_fancy_dress_box_teak"),
                    flatpack = "obj.poh_flatpack_fancy_dress_box2",
                ) {
                    material("obj.plank_teak", 2)
                }
            }
            row("dbrow.poh_mahogany_fancy_dress_box") {
                option(
                    "Mahogany fancy dress box",
                    level = 80,
                    xp = 280.0,
                    hotspots = listOf("loc.poh_cos_room_fancy_dress_box_teak"),
                    built = listOf("loc.poh_cos_room_fancy_dress_box_mahogany"),
                    flatpack = "obj.poh_flatpack_fancy_dress_box3",
                ) {
                    material("obj.plank_mahogany", 2)
                }
            }
            row("dbrow.poh_oak_magic_wardrobe") {
                option(
                    "Oak magic wardrobe",
                    level = 42,
                    xp = 240.0,
                    hotspots = listOf("loc.poh_cos_room_magic_wardrobe_hotspot"),
                    built = listOf("loc.poh_cos_room_magic_wardrobe_oak"),
                    flatpack = "obj.poh_flatpack_magic_wardrobe",
                ) {
                    material("obj.plank_oak", 4)
                }
            }
            row("dbrow.poh_carved_oak_magic_wardrobe") {
                option(
                    "Carved oak magic wardrobe",
                    level = 51,
                    xp = 360.0,
                    hotspots = listOf("loc.poh_cos_room_magic_wardrobe_oak"),
                    built = listOf("loc.poh_cos_room_magic_wardrobe_carved_oak"),
                    flatpack = "obj.poh_flatpack_magic_wardrobe2",
                ) {
                    material("obj.plank_oak", 6)
                }
            }
            row("dbrow.poh_teak_magic_wardrobe") {
                option(
                    "Teak magic wardrobe",
                    level = 60,
                    xp = 360.0,
                    hotspots = listOf("loc.poh_cos_room_magic_wardrobe_carved_oak"),
                    built = listOf("loc.poh_cos_room_magic_wardrobe_teak"),
                    flatpack = "obj.poh_flatpack_magic_wardrobe3",
                ) {
                    material("obj.plank_teak", 4)
                }
            }
            row("dbrow.poh_carved_teak_magic_wardrobe") {
                option(
                    "Carved teak magic wardrobe",
                    level = 69,
                    xp = 540.0,
                    hotspots = listOf("loc.poh_cos_room_magic_wardrobe_teak"),
                    built = listOf("loc.poh_cos_room_magic_wardrobe_carved_teak"),
                    flatpack = "obj.poh_flatpack_magic_wardrobe4",
                ) {
                    material("obj.plank_teak", 6)
                }
            }
            row("dbrow.poh_mahogany_magic_wardrobe") {
                option(
                    "Mahogany magic wardrobe",
                    level = 78,
                    xp = 560.0,
                    hotspots = listOf("loc.poh_cos_room_magic_wardrobe_carved_teak"),
                    built = listOf("loc.poh_cos_room_magic_wardrobe_mahogany"),
                    flatpack = "obj.poh_flatpack_magic_wardrobe5",
                ) {
                    material("obj.plank_mahogany", 4)
                }
            }
            row("dbrow.poh_gilded_magic_wardrobe") {
                option(
                    "Gilded magic wardrobe",
                    level = 87,
                    xp = 860.0,
                    hotspots = listOf("loc.poh_cos_room_magic_wardrobe_mahogany"),
                    built = listOf("loc.poh_cos_room_magic_wardrobe_mahogany_gilded"),
                    flatpack = "obj.poh_flatpack_magic_wardrobe6",
                ) {
                    material("obj.plank_mahogany", 4)
                    material("obj.gold_leaf", 1)
                }
            }
            row("dbrow.poh_marble_magic_wardrobe") {
                option(
                    "Marble magic wardrobe",
                    level = 96,
                    xp = 500.0,
                    hotspots = listOf("loc.poh_cos_room_magic_wardrobe_mahogany_gilded"),
                    built = listOf("loc.poh_cos_room_magic_wardrobe_marble"),
                    flatpack = "obj.poh_flatpack_magic_wardrobe7",
                ) {
                    material("obj.marble_block", 1)
                }
            }
            row("dbrow.poh_oak_toy_box") {
                option(
                    "Oak toy box",
                    level = 50,
                    xp = 120.0,
                    hotspots = listOf("loc.poh_cos_room_toy_box_hotspot"),
                    built = listOf("loc.poh_cos_room_toy_box_oak"),
                    flatpack = "obj.poh_flatpack_toy_box",
                ) {
                    material("obj.plank_oak", 2)
                }
            }
            row("dbrow.poh_teak_toy_box") {
                option(
                    "Teak toy box",
                    level = 68,
                    xp = 180.0,
                    hotspots = listOf("loc.poh_cos_room_toy_box_hotspot"),
                    built = listOf("loc.poh_cos_room_toy_box_teak"),
                    flatpack = "obj.poh_flatpack_toy_box2",
                ) {
                    material("obj.plank_teak", 2)
                }
            }
            row("dbrow.poh_mahogany_toy_box") {
                option(
                    "Mahogany toy box",
                    level = 86,
                    xp = 280.0,
                    hotspots = listOf("loc.poh_cos_room_toy_box_hotspot"),
                    built = listOf("loc.poh_cos_room_toy_box_mahogany"),
                    flatpack = "obj.poh_flatpack_toy_box3",
                ) {
                    material("obj.plank_mahogany", 2)
                }
            }
            row("dbrow.poh_oak_treasure_chest") {
                option(
                    "Oak treasure chest",
                    level = 48,
                    xp = 120.0,
                    hotspots = listOf("loc.poh_cos_room_tresure_chest_hotspot"),
                    built = listOf("loc.poh_cos_room_tresure_chest_oak"),
                    flatpack = "obj.poh_flatpack_treasure_chest",
                ) {
                    material("obj.plank_oak", 2)
                }
            }
            row("dbrow.poh_teak_treasure_chest") {
                option(
                    "Teak treasure chest",
                    level = 66,
                    xp = 180.0,
                    hotspots = listOf("loc.poh_cos_room_tresure_chest_oak"),
                    built = listOf("loc.poh_cos_room_tresure_chest_teak"),
                    flatpack = "obj.poh_flatpack_treasure_chest2",
                ) {
                    material("obj.plank_teak", 2)
                }
            }
            row("dbrow.poh_mahogany_treasure_chest") {
                option(
                    "Mahogany treasure chest",
                    level = 84,
                    xp = 280.0,
                    hotspots = listOf("loc.poh_cos_room_tresure_chest_teak"),
                    built = listOf("loc.poh_cos_room_tresure_chest_mahogany"),
                    flatpack = "obj.poh_flatpack_treasure_chest3",
                ) {
                    material("obj.plank_mahogany", 2)
                }
            }

            /* Menagerie (indoor and outdoor share hotspot locs). */
            row("dbrow.poh_simple_arena") {
                option(
                    "Simple arena",
                    level = 63,
                    xp = 139.0,
                    hotspots =
                        listOf(
                            "loc.poh_menagerie_combatring_hotspot",
                            "loc.poh_menagerie_combatring_mat_hotspot",
                        ),
                    built =
                        listOf(
                            "loc.poh_menagerie_combatring_1",
                            "loc.poh_menagerie_combatring_mat",
                        ),
                    groupBuild = true,
                ) {
                    material("obj.plank_oak", 2)
                    material("obj.cloth", 1)
                    material("obj.rope", 1)
                }
            }
            row("dbrow.poh_advanced_arena") {
                option(
                    "Advanced arena",
                    level = 73,
                    xp = 199.0,
                    hotspots =
                        listOf(
                            "loc.poh_menagerie_combatring_hotspot",
                            "loc.poh_menagerie_combatring_mat_hotspot",
                        ),
                    built =
                        listOf(
                            "loc.poh_menagerie_combatring_2",
                            "loc.poh_menagerie_combatring_mat",
                        ),
                    groupBuild = true,
                ) {
                    material("obj.plank_teak", 2)
                    material("obj.cloth", 1)
                    material("obj.rope", 1)
                }
            }
            row("dbrow.poh_glorious_arena") {
                option(
                    "Glorious arena",
                    level = 83,
                    xp = 299.0,
                    hotspots =
                        listOf(
                            "loc.poh_menagerie_combatring_hotspot",
                            "loc.poh_menagerie_combatring_mat_hotspot",
                        ),
                    built =
                        listOf(
                            "loc.poh_menagerie_combatring_3",
                            "loc.poh_menagerie_combatring_mat",
                        ),
                    groupBuild = true,
                ) {
                    material("obj.plank_mahogany", 2)
                    material("obj.cloth", 1)
                    material("obj.rope", 1)
                }
            }
            row("dbrow.poh_grassland_habitat") {
                option(
                    "Grassland habitat",
                    level = 37,
                    xp = 37.0,
                    hotspots =
                        listOf(
                            "loc.poh_menagerie_habitat_ground_corner",
                            "loc.poh_menagerie_habitat_ground_side",
                            "loc.poh_menagerie_habitat_ground_middle",
                            "loc.poh_menagerie_habitat_feature",
                        ),
                    built =
                        listOf(
                            "loc.poh_menagerie_habitat_ground_corner_1",
                            "loc.poh_menagerie_habitat_ground_side_1",
                            "loc.poh_menagerie_habitat_ground_middle_1",
                            "loc.poh_menagerie_habitat_feature_1",
                        ),
                    groupBuild = true,
                ) {
                    material("obj.poh_sapling_tree_1", 1)
                    material("obj.bucket_compost", 2)
                }
            }
            row("dbrow.poh_forest_habitat") {
                option(
                    "Forest habitat",
                    level = 47,
                    xp = 51.0,
                    hotspots =
                        listOf(
                            "loc.poh_menagerie_habitat_ground_corner",
                            "loc.poh_menagerie_habitat_ground_side",
                            "loc.poh_menagerie_habitat_ground_middle",
                            "loc.poh_menagerie_habitat_feature",
                        ),
                    built =
                        listOf(
                            "loc.poh_menagerie_habitat_ground_corner_2",
                            "loc.poh_menagerie_habitat_ground_side_2",
                            "loc.poh_menagerie_habitat_ground_middle_2",
                            "loc.poh_menagerie_habitat_feature_2",
                        ),
                    groupBuild = true,
                ) {
                    material("obj.poh_sapling_tree_2", 1)
                    material("obj.bucket_compost", 3)
                }
            }
            row("dbrow.poh_desert_habitat") {
                option(
                    "Desert habitat",
                    level = 57,
                    xp = 181.0,
                    hotspots =
                        listOf(
                            "loc.poh_menagerie_habitat_ground_corner",
                            "loc.poh_menagerie_habitat_ground_side",
                            "loc.poh_menagerie_habitat_ground_middle",
                            "loc.poh_menagerie_habitat_feature",
                        ),
                    built =
                        listOf(
                            "loc.poh_menagerie_habitat_ground_corner_3",
                            "loc.poh_menagerie_habitat_ground_side_3",
                            "loc.poh_menagerie_habitat_ground_middle_3",
                            "loc.poh_menagerie_habitat_feature_3",
                        ),
                    groupBuild = true,
                ) {
                    material("obj.poh_sapling_plant_1", 1)
                    material("obj.bucket_sand", 5)
                }
            }
            row("dbrow.poh_polar_habitat") {
                option(
                    "Polar habitat",
                    level = 67,
                    xp = 271.0,
                    hotspots =
                        listOf(
                            "loc.poh_menagerie_habitat_ground_corner",
                            "loc.poh_menagerie_habitat_ground_side",
                            "loc.poh_menagerie_habitat_ground_middle",
                            "loc.poh_menagerie_habitat_feature",
                        ),
                    built =
                        listOf(
                            "loc.poh_menagerie_habitat_ground_corner_4",
                            "loc.poh_menagerie_habitat_ground_side_4",
                            "loc.poh_menagerie_habitat_ground_middle_4",
                            "loc.poh_menagerie_habitat_feature_4",
                        ),
                    groupBuild = true,
                ) {
                    material("obj.plank_oak", 3)
                    material("obj.waterrune", 2000)
                    material("obj.slayer_icy_water", 5)
                }
            }
            row("dbrow.poh_volcanic_habitat") {
                option(
                    "Volcanic habitat",
                    level = 77,
                    xp = 46.0,
                    hotspots =
                        listOf(
                            "loc.poh_menagerie_habitat_ground_corner",
                            "loc.poh_menagerie_habitat_ground_side",
                            "loc.poh_menagerie_habitat_ground_middle",
                            "loc.poh_menagerie_habitat_feature",
                        ),
                    built =
                        listOf(
                            "loc.poh_menagerie_habitat_ground_corner_5",
                            "loc.poh_menagerie_habitat_ground_side_5",
                            "loc.poh_menagerie_habitat_ground_middle_5",
                            "loc.poh_menagerie_habitat_feature_5",
                        ),
                    groupBuild = true,
                ) {
                    material("obj.enakh_granite_medium", 5)
                    material("obj.lavarune", 100)
                }
            }
            row("dbrow.poh_oak_feeder") {
                option(
                    "Oak feeder",
                    level = 37,
                    xp = 182.0,
                    hotspots = listOf("loc.poh_menagerie_petfeeder_hotspot"),
                    built = listOf("loc.poh_menagerie_petfeeder_1"),
                ) {
                    material("obj.plank_oak", 3)
                    material("obj.bucket_milk", 1)
                }
            }
            row("dbrow.poh_teak_feeder") {
                option(
                    "Teak feeder",
                    level = 48,
                    xp = 272.0,
                    hotspots = listOf("loc.poh_menagerie_petfeeder_hotspot"),
                    built = listOf("loc.poh_menagerie_petfeeder_2"),
                ) {
                    material("obj.plank_teak", 3)
                    material("obj.bucket_milk", 1)
                }
            }
            row("dbrow.poh_mahogany_feeder") {
                option(
                    "Mahogany feeder",
                    level = 59,
                    xp = 862.0,
                    hotspots = listOf("loc.poh_menagerie_petfeeder_hotspot"),
                    built = listOf("loc.poh_menagerie_petfeeder_3"),
                ) {
                    material("obj.plank_mahogany", 4)
                    material("obj.bucket_milk", 1)
                    material("obj.gold_leaf", 1)
                }
            }
            row("dbrow.poh_oak_pet_house") {
                option(
                    "Oak house",
                    level = 37,
                    xp = 240.0,
                    hotspots = listOf("loc.poh_menagerie_pethouse_hotspot"),
                    built = listOf("loc.poh_menagerie_pethouse_1"),
                ) {
                    material("obj.plank_oak", 4)
                }
            }
            row("dbrow.poh_teak_pet_house") {
                option(
                    "Teak house",
                    level = 48,
                    xp = 360.0,
                    hotspots = listOf("loc.poh_menagerie_pethouse_1"),
                    built = listOf("loc.poh_menagerie_pethouse_2"),
                ) {
                    material("obj.plank_teak", 4)
                }
            }
            row("dbrow.poh_mahogany_pet_house") {
                option(
                    "Mahogany house",
                    level = 59,
                    xp = 560.0,
                    hotspots = listOf("loc.poh_menagerie_pethouse_2"),
                    built = listOf("loc.poh_menagerie_pethouse_3"),
                ) {
                    material("obj.plank_mahogany", 4)
                }
            }
            row("dbrow.poh_consecrated_pet_house") {
                option(
                    "Consecrated house",
                    level = 70,
                    xp = 1560.0,
                    hotspots = listOf("loc.poh_menagerie_pethouse_3"),
                    built = listOf("loc.poh_menagerie_pethouse_4"),
                ) {
                    material("obj.plank_mahogany", 4)
                    material("obj.poh_magic_crystal", 1)
                }
            }
            row("dbrow.poh_desecrated_pet_house") {
                option(
                    "Desecrated house",
                    level = 81,
                    xp = 160.0,
                    hotspots = listOf("loc.poh_menagerie_pethouse_4"),
                    built = listOf("loc.poh_menagerie_pethouse_5"),
                ) {
                    material("obj.plank_mahogany", 1)
                    material("obj.limestonebrick", 1)
                }
            }
            row("dbrow.poh_nature_pet_house") {
                option(
                    "Nature house",
                    level = 92,
                    xp = 158.0,
                    hotspots = listOf("loc.poh_menagerie_pethouse_5"),
                    built = listOf("loc.poh_menagerie_pethouse_6"),
                ) {
                    material("obj.plank_mahogany", 1)
                    material("obj.bucket_water", 2)
                    material("obj.bucket_supercompost", 3)
                }
            }
            row("dbrow.poh_pet_list") {
                option(
                    "Pet list",
                    level = 38,
                    xp = 198.0,
                    hotspots = listOf("loc.poh_menagerie_petlist_hotspot"),
                    built = listOf("loc.poh_menagerie_petlist_1"),
                ) {
                    material("obj.plank_oak", 3)
                    material("obj.cloth", 1)
                    material("obj.papyrus", 1)
                }
            }
            row("dbrow.poh_oak_scratching_post") {
                option(
                    "Oak scratching post",
                    level = 39,
                    xp = 124.0,
                    hotspots = listOf("loc.poh_menagerie_scratchingpost_hotspot"),
                    built = listOf("loc.poh_menagerie_scratchingpost_1"),
                ) {
                    material("obj.plank_oak", 2)
                    material("obj.rope", 1)
                }
            }
            row("dbrow.poh_teak_scratching_post") {
                option(
                    "Teak scratching post",
                    level = 49,
                    xp = 204.0,
                    hotspots = listOf("loc.poh_menagerie_scratchingpost_hotspot"),
                    built = listOf("loc.poh_menagerie_scratchingpost_2"),
                ) {
                    material("obj.plank_teak", 2)
                    material("obj.rope", 1)
                    material("obj.limestonebrick", 1)
                }
            }
            row("dbrow.poh_mahogany_scratching_post") {
                option(
                    "Mahogany scratching post",
                    level = 59,
                    xp = 304.0,
                    hotspots = listOf("loc.poh_menagerie_scratchingpost_hotspot"),
                    built = listOf("loc.poh_menagerie_scratchingpost_3"),
                ) {
                    material("obj.plank_mahogany", 2)
                    material("obj.rope", 1)
                    material("obj.limestonebrick", 1)
                }
            }

            /* Dungeon - guards/traps/doors/lighting/decoration shared by corridor, junction, stairs,
               treasure room and oubliette. Guards, traps and monsters are paid for in coins. */
            row("dbrow.poh_dungeon_skeleton_guard") {
                option(
                    "Skeleton guard",
                    level = 70,
                    xp = 223.0,
                    hotspots =
                        listOf(
                            "loc.poh_dungeon_1",
                            "loc.poh_dungeon_stairs_2",
                            "loc.poh_dungeon_stairs_3",
                            "loc.poh_oubliette_3",
                        ),
                    built =
                        listOf(
                            "loc.poh_skeleton",
                            "loc.poh_skeleton",
                            "loc.poh_skeleton",
                            "loc.poh_skeleton",
                        ),
                ) {
                    material("obj.coins", 50000)
                }
            }
            row("dbrow.poh_dungeon_guard_dog") {
                option(
                    "Guard dog",
                    level = 74,
                    xp = 273.0,
                    hotspots =
                        listOf(
                            "loc.poh_dungeon_1",
                            "loc.poh_dungeon_stairs_2",
                            "loc.poh_dungeon_stairs_3",
                            "loc.poh_oubliette_3",
                        ),
                    built =
                        listOf(
                            "loc.poh_guarddog",
                            "loc.poh_guarddog",
                            "loc.poh_guarddog",
                            "loc.poh_guarddog",
                        ),
                ) {
                    material("obj.coins", 75000)
                }
            }
            row("dbrow.poh_hobgoblin_guard") {
                option(
                    "Hobgoblin",
                    level = 78,
                    xp = 316.0,
                    hotspots =
                        listOf(
                            "loc.poh_dungeon_1",
                            "loc.poh_dungeon_stairs_2",
                            "loc.poh_dungeon_stairs_3",
                            "loc.poh_oubliette_3",
                        ),
                    built =
                        listOf(
                            "loc.poh_hobgoblin",
                            "loc.poh_hobgoblin",
                            "loc.poh_hobgoblin",
                            "loc.poh_hobgoblin",
                        ),
                ) {
                    material("obj.coins", 100000)
                }
            }
            row("dbrow.poh_baby_red_dragon") {
                option(
                    "Baby red dragon",
                    level = 82,
                    xp = 387.0,
                    hotspots =
                        listOf(
                            "loc.poh_dungeon_1",
                            "loc.poh_dungeon_stairs_2",
                            "loc.poh_dungeon_stairs_3",
                            "loc.poh_oubliette_3",
                        ),
                    built =
                        listOf(
                            "loc.poh_babyreddragon",
                            "loc.poh_babyreddragon",
                            "loc.poh_babyreddragon",
                            "loc.poh_babyreddragon",
                        ),
                ) {
                    material("obj.coins", 150000)
                }
            }
            row("dbrow.poh_huge_spider") {
                option(
                    "Huge spider",
                    level = 86,
                    xp = 447.0,
                    hotspots =
                        listOf(
                            "loc.poh_dungeon_1",
                            "loc.poh_dungeon_stairs_2",
                            "loc.poh_dungeon_stairs_3",
                            "loc.poh_oubliette_3",
                        ),
                    built =
                        listOf(
                            "loc.poh_giantspider",
                            "loc.poh_giantspider",
                            "loc.poh_giantspider",
                            "loc.poh_giantspider",
                        ),
                ) {
                    material("obj.coins", 200000)
                }
            }
            row("dbrow.poh_troll_guard") {
                option(
                    "Troll guard",
                    level = 90,
                    xp = 1000.0,
                    hotspots =
                        listOf(
                            "loc.poh_dungeon_1",
                            "loc.poh_dungeon_stairs_2",
                            "loc.poh_dungeon_stairs_3",
                            "loc.poh_oubliette_3",
                        ),
                    built =
                        listOf(
                            "loc.poh_troll",
                            "loc.poh_troll",
                            "loc.poh_troll",
                            "loc.poh_troll",
                        ),
                ) {
                    material("obj.coins", 1000000)
                }
            }
            row("dbrow.poh_hellhound_guard") {
                option(
                    "Hellhound",
                    level = 94,
                    xp = 2236.0,
                    hotspots =
                        listOf(
                            "loc.poh_dungeon_1",
                            "loc.poh_dungeon_stairs_2",
                            "loc.poh_dungeon_stairs_3",
                            "loc.poh_oubliette_3",
                        ),
                    built =
                        listOf(
                            "loc.poh_hellhound",
                            "loc.poh_hellhound",
                            "loc.poh_hellhound",
                            "loc.poh_hellhound",
                        ),
                ) {
                    material("obj.coins", 5000000)
                }
            }
            row("dbrow.poh_dungeon_spike_trap") {
                option(
                    "Spike trap",
                    level = 72,
                    xp = 223.0,
                    hotspots = listOf("loc.poh_dungeon_2", "loc.poh_dungeon_3"),
                    built = listOf("loc.poh_trap_1", "loc.poh_trap_1"),
                ) {
                    material("obj.coins", 50000)
                }
            }
            row("dbrow.poh_dungeon_man_trap") {
                option(
                    "Man trap",
                    level = 76,
                    xp = 273.0,
                    hotspots = listOf("loc.poh_dungeon_2", "loc.poh_dungeon_3"),
                    built = listOf("loc.poh_trap_2", "loc.poh_trap_2"),
                ) {
                    material("obj.coins", 75000)
                }
            }
            row("dbrow.poh_tangle_vine") {
                option(
                    "Tangle vine",
                    level = 80,
                    xp = 316.0,
                    hotspots = listOf("loc.poh_dungeon_2", "loc.poh_dungeon_3"),
                    built = listOf("loc.poh_trap_3", "loc.poh_trap_3"),
                ) {
                    material("obj.coins", 100000)
                }
            }
            row("dbrow.poh_dungeon_marble_trap") {
                option(
                    "Marble trap",
                    level = 84,
                    xp = 387.0,
                    hotspots = listOf("loc.poh_dungeon_2", "loc.poh_dungeon_3"),
                    built = listOf("loc.poh_trap_4", "loc.poh_trap_4"),
                ) {
                    material("obj.coins", 150000)
                }
            }
            row("dbrow.poh_dungeon_teleport_trap") {
                option(
                    "Teleport trap",
                    level = 88,
                    xp = 447.0,
                    hotspots = listOf("loc.poh_dungeon_2", "loc.poh_dungeon_3"),
                    built = listOf("loc.poh_trap_5", "loc.poh_trap_5"),
                ) {
                    material("obj.coins", 200000)
                }
            }
            row("dbrow.poh_oak_door") {
                option(
                    "Oak door",
                    level = 74,
                    xp = 600.0,
                    hotspots = listOf("loc.poh_dungeon_4l", "loc.poh_dungeon_4r"),
                    built = listOf("loc.poh_dungeon_ldoor_oak", "loc.poh_dungeon_rdoor_oak"),
                    groupBuild = true,
                ) {
                    material("obj.plank_oak", 10)
                }
            }
            row("dbrow.poh_oak_door_2") {
                option(
                    "Oak door",
                    level = 74,
                    xp = 600.0,
                    hotspots = listOf("loc.poh_dungeon_5l", "loc.poh_dungeon_5r"),
                    built = listOf("loc.poh_dungeon_ldoor_oak", "loc.poh_dungeon_rdoor_oak"),
                    groupBuild = true,
                ) {
                    material("obj.plank_oak", 10)
                }
            }
            row("dbrow.poh_steel_plated_door") {
                option(
                    "Steel-plated door",
                    level = 84,
                    xp = 800.0,
                    hotspots = listOf("loc.poh_dungeon_4l", "loc.poh_dungeon_4r"),
                    built = listOf("loc.poh_dungeon_ldoor_steel", "loc.poh_dungeon_rdoor_steel"),
                    groupBuild = true,
                ) {
                    material("obj.plank_oak", 10)
                    material("obj.steel_bar", 10)
                }
            }
            row("dbrow.poh_steel_plated_door_2") {
                option(
                    "Steel-plated door",
                    level = 84,
                    xp = 800.0,
                    hotspots = listOf("loc.poh_dungeon_5l", "loc.poh_dungeon_5r"),
                    built = listOf("loc.poh_dungeon_ldoor_steel", "loc.poh_dungeon_rdoor_steel"),
                    groupBuild = true,
                ) {
                    material("obj.plank_oak", 10)
                    material("obj.steel_bar", 10)
                }
            }
            row("dbrow.poh_marble_door") {
                option(
                    "Marble door",
                    level = 94,
                    xp = 2000.0,
                    hotspots = listOf("loc.poh_dungeon_4l", "loc.poh_dungeon_4r"),
                    built = listOf("loc.poh_dungeon_ldoor_marble", "loc.poh_dungeon_rdoor_marble"),
                    groupBuild = true,
                ) {
                    material("obj.marble_block", 4)
                }
            }
            row("dbrow.poh_marble_door_2") {
                option(
                    "Marble door",
                    level = 94,
                    xp = 2000.0,
                    hotspots = listOf("loc.poh_dungeon_5l", "loc.poh_dungeon_5r"),
                    built = listOf("loc.poh_dungeon_ldoor_marble", "loc.poh_dungeon_rdoor_marble"),
                    groupBuild = true,
                ) {
                    material("obj.marble_block", 4)
                }
            }
            row("dbrow.poh_dungeon_candles") {
                option(
                    "Candle",
                    level = 72,
                    xp = 243.0,
                    hotspots = listOf("loc.poh_dungeon_6", "loc.poh_oubliette_4"),
                    built = listOf("loc.poh_dungeon_candle", "loc.poh_dungeon_candle"),
                    groupBuild = true,
                ) {
                    material("obj.plank_oak", 4)
                    material("obj.lit_candle", 4)
                }
            }
            row("dbrow.poh_dungeon_torches") {
                option(
                    "Torches",
                    level = 84,
                    xp = 244.0,
                    hotspots = listOf("loc.poh_dungeon_6", "loc.poh_oubliette_4"),
                    built = listOf("loc.poh_dungeon_torch", "loc.poh_dungeon_torch"),
                    groupBuild = true,
                ) {
                    material("obj.plank_oak", 4)
                    material("obj.torch_lit", 4)
                }
            }
            row("dbrow.poh_dungeon_skull_torches") {
                option(
                    "Skull torches",
                    level = 94,
                    xp = 246.0,
                    hotspots = listOf("loc.poh_dungeon_6", "loc.poh_oubliette_4"),
                    built = listOf("loc.poh_dungeon_skulltorch", "loc.poh_dungeon_skulltorch"),
                    groupBuild = true,
                ) {
                    material("obj.plank_oak", 4)
                    material("obj.torch_lit", 4)
                    material("obj.skull", 4)
                }
            }
            row("dbrow.poh_decorative_blood") {
                option(
                    "Decorative blood",
                    level = 72,
                    xp = 4.0,
                    hotspots = listOf("loc.poh_dungeon_7"),
                    built = listOf("loc.poh_dungeon_walldecor_blood"),
                    groupBuild = true,
                ) {
                    material("obj.reddye", 4)
                }
            }
            row("dbrow.poh_decorative_pipe") {
                option(
                    "Decorative pipe",
                    level = 83,
                    xp = 120.0,
                    hotspots = listOf("loc.poh_dungeon_7"),
                    built = listOf("loc.poh_dungeon_walldecor_pipe"),
                    groupBuild = true,
                ) {
                    material("obj.steel_bar", 6)
                }
            }
            row("dbrow.poh_hanging_skeleton") {
                option(
                    "Hanging skeleton",
                    level = 94,
                    xp = 3.0,
                    hotspots = listOf("loc.poh_dungeon_7"),
                    built = listOf("loc.poh_dungeon_walldecor_skeleton"),
                    groupBuild = true,
                ) {
                    material("obj.skull", 2)
                    material("obj.bones", 6)
                }
            }

            /* Oubliette. */
            row("dbrow.poh_oubliette_floor_spikes") {
                option(
                    "Spikes",
                    level = 65,
                    xp = 623.0,
                    hotspots =
                        listOf(
                            "loc.poh_oubliette_1",
                            "loc.poh_oubliette_1_side",
                            "loc.poh_oubliette_1_corner",
                        ),
                    built =
                        listOf(
                            "loc.poh_oubliette_spikes_mid",
                            "loc.poh_oubliette_spikes_side",
                            "loc.poh_oubliette_spikes_corner",
                        ),
                    groupBuild = true,
                ) {
                    material("obj.steel_bar", 20)
                    material("obj.coins", 50000)
                }
            }
            row("dbrow.poh_tentacle_pool") {
                option(
                    "Tentacle pool",
                    level = 71,
                    xp = 326.0,
                    hotspots =
                        listOf(
                            "loc.poh_oubliette_1",
                            "loc.poh_oubliette_1_side",
                            "loc.poh_oubliette_1_corner",
                        ),
                    built =
                        listOf(
                            "loc.poh_oubliette_pool_mid",
                            "loc.poh_oubliette_pool_side",
                            "loc.poh_oubliette_pool_corner",
                        ),
                    groupBuild = true,
                ) {
                    material("obj.bucket_water", 20)
                    material("obj.coins", 100000)
                }
            }
            row("dbrow.poh_flame_pit") {
                option(
                    "Flame pit",
                    level = 77,
                    xp = 357.0,
                    hotspots = listOf("loc.poh_oubliette_1_type8"),
                    built = listOf("loc.poh_oubliette_floor_fire"),
                    groupBuild = true,
                ) {
                    material("obj.tinderbox", 20)
                    material("obj.coins", 125000)
                }
            }
            row("dbrow.poh_rocnar") {
                option(
                    "Rocnar",
                    level = 83,
                    xp = 387.0,
                    hotspots = listOf("loc.poh_oubliette_1_type8_ogre"),
                    built = listOf("loc.poh_oub_monster1"),
                    groupBuild = true,
                ) {
                    material("obj.coins", 150000)
                }
            }
            row("dbrow.poh_oak_cage") {
                option(
                    "Oak cage",
                    level = 65,
                    xp = 640.0,
                    hotspots = listOf("loc.poh_oubliette_2_front", "loc.poh_oubliette_2_door"),
                    built = listOf("loc.poh_cage_dungeon_oak", "loc.poh_cage_dungeon_oak_door"),
                    groupBuild = true,
                ) {
                    material("obj.plank_oak", 10)
                    material("obj.steel_bar", 2)
                }
            }
            row("dbrow.poh_oak_and_steel_cage") {
                option(
                    "Oak and steel cage",
                    level = 70,
                    xp = 800.0,
                    hotspots = listOf("loc.poh_oubliette_2_front", "loc.poh_oubliette_2_door"),
                    built =
                        listOf(
                            "loc.poh_cage_dungeon_oak+steel",
                            "loc.poh_cage_dungeon_oak+steel_door",
                        ),
                    groupBuild = true,
                ) {
                    material("obj.plank_oak", 10)
                    material("obj.steel_bar", 10)
                }
            }
            row("dbrow.poh_oubliette_steel_cage") {
                option(
                    "Steel cage",
                    level = 75,
                    xp = 400.0,
                    hotspots = listOf("loc.poh_oubliette_2_front", "loc.poh_oubliette_2_door"),
                    built = listOf("loc.poh_cage_dungeon_steel", "loc.poh_cage_dungeon_steel_door"),
                    groupBuild = true,
                ) {
                    material("obj.steel_bar", 20)
                }
            }
            row("dbrow.poh_spiked_cage") {
                option(
                    "Spiked cage",
                    level = 80,
                    xp = 500.0,
                    hotspots = listOf("loc.poh_oubliette_2_front", "loc.poh_oubliette_2_door"),
                    built =
                        listOf(
                            "loc.poh_cage_dungeon_steel+spikes",
                            "loc.poh_cage_dungeon_steel+spikes_door",
                        ),
                    groupBuild = true,
                ) {
                    material("obj.steel_bar", 25)
                }
            }
            row("dbrow.poh_bone_cage") {
                option(
                    "Bone cage",
                    level = 85,
                    xp = 603.0,
                    hotspots = listOf("loc.poh_oubliette_2_front", "loc.poh_oubliette_2_door"),
                    built = listOf("loc.poh_cage_dungeon_bones", "loc.poh_cage_dungeon_bones_door"),
                    groupBuild = true,
                ) {
                    material("obj.plank_oak", 10)
                    material("obj.bones", 10)
                }
            }
            row("dbrow.poh_oak_ladder") {
                option(
                    "Oak ladder",
                    level = 68,
                    xp = 300.0,
                    hotspots = listOf("loc.poh_oubliette_5"),
                    built = listOf("loc.poh_dungeon_ladder_oak"),
                ) {
                    material("obj.plank_oak", 5)
                }
            }
            row("dbrow.poh_teak_ladder") {
                option(
                    "Teak ladder",
                    level = 78,
                    xp = 450.0,
                    hotspots = listOf("loc.poh_oubliette_5"),
                    built = listOf("loc.poh_dungeon_ladder_teak"),
                ) {
                    material("obj.plank_teak", 5)
                }
            }
            row("dbrow.poh_mahogany_ladder") {
                option(
                    "Mahogany ladder",
                    level = 88,
                    xp = 700.0,
                    hotspots = listOf("loc.poh_oubliette_5"),
                    built = listOf("loc.poh_dungeon_ladder_mag"),
                ) {
                    material("obj.plank_mahogany", 5)
                }
            }

            /* Treasure room. */
            row("dbrow.poh_wooden_crate") {
                option(
                    "Wooden crate",
                    level = 75,
                    xp = 143.0,
                    hotspots = listOf("loc.poh_dungeon_treasure_1"),
                    built = listOf("loc.poh_treasure_woodencrate"),
                    nails = 5,
                ) {
                    material("obj.woodplank", 5)
                }
            }
            row("dbrow.poh_oak_chest") {
                option(
                    "Oak chest",
                    level = 79,
                    xp = 340.0,
                    hotspots = listOf("loc.poh_dungeon_treasure_1"),
                    built = listOf("loc.poh_treasure_oak_chest"),
                ) {
                    material("obj.plank_oak", 5)
                    material("obj.steel_bar", 2)
                }
            }
            row("dbrow.poh_teak_chest") {
                option(
                    "Teak chest",
                    level = 83,
                    xp = 530.0,
                    hotspots = listOf("loc.poh_dungeon_treasure_1"),
                    built = listOf("loc.poh_treasure_teak_chest"),
                ) {
                    material("obj.plank_teak", 5)
                    material("obj.steel_bar", 4)
                }
            }
            row("dbrow.poh_mahogany_chest") {
                option(
                    "Mahogany chest",
                    level = 87,
                    xp = 1000.0,
                    hotspots = listOf("loc.poh_dungeon_treasure_1"),
                    built = listOf("loc.poh_treasure_mag_chest"),
                ) {
                    material("obj.plank_mahogany", 5)
                    material("obj.gold_leaf", 1)
                }
            }
            row("dbrow.poh_magic_chest") {
                option(
                    "Magic chest",
                    level = 91,
                    xp = 1500.0,
                    hotspots = listOf("loc.poh_dungeon_treasure_1"),
                    built = listOf("loc.poh_treasure_magic_chest"),
                ) {
                    material("obj.poh_magic_crystal", 1)
                }
            }
            row("dbrow.poh_treasure_demon") {
                option(
                    "Demon",
                    level = 75,
                    xp = 707.0,
                    hotspots = listOf("loc.poh_dungeon_treasure_2"),
                    built = listOf("loc.poh_demon"),
                ) {
                    material("obj.coins", 500000)
                }
            }
            row("dbrow.poh_treasure_kalphite_soldier") {
                option(
                    "Kalphite soldier",
                    level = 80,
                    xp = 866.0,
                    hotspots = listOf("loc.poh_dungeon_treasure_2"),
                    built = listOf("loc.poh_kalphite_soldier"),
                ) {
                    material("obj.coins", 750000)
                }
            }
            row("dbrow.poh_treasure_tok_xil") {
                option(
                    "Tok-Xil",
                    level = 85,
                    xp = 2236.0,
                    hotspots = listOf("loc.poh_dungeon_treasure_2"),
                    built = listOf("loc.poh_tok_xil"),
                ) {
                    material("obj.coins", 5000000)
                }
            }
            row("dbrow.poh_treasure_dagannoth") {
                option(
                    "Dagannoth",
                    level = 90,
                    xp = 2738.0,
                    hotspots = listOf("loc.poh_dungeon_treasure_2"),
                    built = listOf("loc.poh_dagganoth"),
                ) {
                    material("obj.coins", 7500000)
                }
            }
            row("dbrow.poh_treasure_steel_dragon") {
                option(
                    "Steel dragon",
                    level = 95,
                    xp = 3162.0,
                    hotspots = listOf("loc.poh_dungeon_treasure_2"),
                    built = listOf("loc.poh_steel_dragon"),
                ) {
                    material("obj.coins", 10000000)
                }
            }
            row("dbrow.poh_treasure_rune_dragon") {
                option(
                    "Rune dragon",
                    level = 99,
                    xp = 5000.0,
                    hotspots = listOf("loc.poh_dungeon_treasure_2"),
                    built = listOf("loc.poh_rune_dragon"),
                ) {
                    material("obj.coins", 25000000)
                }
            }

            /* Skill hall and quest hall - rugs/stairs shared (the dungeon stairs room reuses the
               poh_hall1_1 hotspots). Mounted head/fish trophies attach to displays at runtime. */
            row("dbrow.poh_hall_rug") {
                option(
                    "Rug",
                    level = 13,
                    xp = 60.0,
                    hotspots =
                        listOf(
                            "loc.poh_hall1_1_corner",
                            "loc.poh_hall1_1_side",
                            "loc.poh_hall1_1_middle",
                            "loc.poh_hall2_1_corner",
                            "loc.poh_hall2_1_side",
                            "loc.poh_hall2_1_middle",
                        ),
                    built =
                        listOf(
                            "loc.poh_rugcorner2",
                            "loc.poh_rugside2",
                            "loc.poh_rugmiddle2",
                            "loc.poh_rugcorner2",
                            "loc.poh_rugside2",
                            "loc.poh_rugmiddle2",
                        ),
                    groupBuild = true,
                ) {
                    material("obj.cloth", 4)
                }
            }
            row("dbrow.poh_hall_opulent_rug") {
                option(
                    "Opulent rug",
                    level = 65,
                    xp = 360.0,
                    hotspots =
                        listOf(
                            "loc.poh_hall1_1_corner",
                            "loc.poh_hall1_1_side",
                            "loc.poh_hall1_1_middle",
                            "loc.poh_hall2_1_corner",
                            "loc.poh_hall2_1_side",
                            "loc.poh_hall2_1_middle",
                        ),
                    built =
                        listOf(
                            "loc.poh_rugcorner3",
                            "loc.poh_rugside3",
                            "loc.poh_rugmiddle3",
                            "loc.poh_rugcorner3",
                            "loc.poh_rugside3",
                            "loc.poh_rugmiddle3",
                        ),
                    groupBuild = true,
                ) {
                    material("obj.cloth", 4)
                    material("obj.gold_leaf", 1)
                }
            }
            row("dbrow.poh_oak_staircase") {
                option(
                    "Oak staircase",
                    level = 27,
                    xp = 680.0,
                    hotspots =
                        listOf(
                            "loc.poh_hall1_1_stairs_up",
                            "loc.poh_hall1_1_stairs_top",
                            "loc.poh_hall2_1_stairs_up",
                            "loc.poh_hall2_1_stairs_top",
                        ),
                    built =
                        listOf(
                            "loc.poh_stairs_3",
                            "loc.poh_stairstop_3",
                            "loc.poh_stairs_3",
                            "loc.poh_stairstop_3",
                        ),
                ) {
                    material("obj.plank_oak", 10)
                    material("obj.steel_bar", 4)
                }
            }
            row("dbrow.poh_teak_staircase") {
                option(
                    "Teak staircase",
                    level = 48,
                    xp = 980.0,
                    hotspots =
                        listOf(
                            "loc.poh_hall1_1_stairs_up",
                            "loc.poh_hall1_1_stairs_top",
                            "loc.poh_hall2_1_stairs_up",
                            "loc.poh_hall2_1_stairs_top",
                        ),
                    built =
                        listOf(
                            "loc.poh_stairs_4",
                            "loc.poh_stairstop_4",
                            "loc.poh_stairs_4",
                            "loc.poh_stairstop_4",
                        ),
                ) {
                    material("obj.plank_teak", 10)
                    material("obj.steel_bar", 4)
                }
            }
            row("dbrow.poh_limestone_spiral_staircase") {
                option(
                    "Limestone spiral staircase",
                    level = 67,
                    xp = 1040.0,
                    hotspots =
                        listOf(
                            "loc.poh_hall1_1_stairs_up",
                            "loc.poh_hall1_1_stairs_top",
                            "loc.poh_hall2_1_stairs_up",
                            "loc.poh_hall2_1_stairs_top",
                        ),
                    built =
                        listOf(
                            "loc.poh_spiralstairs",
                            "loc.poh_spiralstairs",
                            "loc.poh_spiralstairs",
                            "loc.poh_spiralstairs",
                        ),
                ) {
                    material("obj.plank_teak", 10)
                    material("obj.limestonebrick", 7)
                }
            }
            row("dbrow.poh_marble_staircase") {
                option(
                    "Marble staircase",
                    level = 82,
                    xp = 3200.0,
                    hotspots =
                        listOf(
                            "loc.poh_hall1_1_stairs_up",
                            "loc.poh_hall1_1_stairs_top",
                            "loc.poh_hall2_1_stairs_up",
                            "loc.poh_hall2_1_stairs_top",
                        ),
                    built =
                        listOf(
                            "loc.poh_stairs_5",
                            "loc.poh_stairstop_5",
                            "loc.poh_stairs_5",
                            "loc.poh_stairstop_5",
                        ),
                ) {
                    material("obj.plank_mahogany", 5)
                    material("obj.marble_block", 5)
                }
            }
            row("dbrow.poh_marble_spiral") {
                option(
                    "Marble spiral",
                    level = 97,
                    xp = 4400.0,
                    hotspots =
                        listOf(
                            "loc.poh_hall1_1_stairs_up",
                            "loc.poh_hall1_1_stairs_top",
                            "loc.poh_hall2_1_stairs_up",
                            "loc.poh_hall2_1_stairs_top",
                        ),
                    built =
                        listOf(
                            "loc.poh_spiralstairs_2",
                            "loc.poh_spiralstairs_2",
                            "loc.poh_spiralstairs_2",
                            "loc.poh_spiralstairs_2",
                        ),
                ) {
                    material("obj.plank_teak", 10)
                    material("obj.marble_block", 7)
                }
            }
            row("dbrow.poh_teak_head_display") {
                option(
                    "Teak display",
                    level = 38,
                    xp = 180.0,
                    hotspots = listOf("loc.poh_hall1_2"),
                    built = listOf("loc.poh_trophy_head_blank_teak"),
                ) {
                    material("obj.plank_teak", 2)
                }
            }
            row("dbrow.poh_mahogany_head_display") {
                option(
                    "Mahogany display",
                    level = 58,
                    xp = 280.0,
                    hotspots = listOf("loc.poh_hall1_2"),
                    built = listOf("loc.poh_trophy_head_blank_mahogany"),
                ) {
                    material("obj.plank_mahogany", 2)
                }
            }
            row("dbrow.poh_gilded_head_display") {
                option(
                    "Gilded display",
                    level = 78,
                    xp = 600.0,
                    hotspots = listOf("loc.poh_hall1_2"),
                    built = listOf("loc.poh_trophy_head_blank_gilded"),
                ) {
                    material("obj.plank_mahogany", 2)
                    material("obj.gold_leaf", 2)
                }
            }
            row("dbrow.poh_oak_fish_display") {
                option(
                    "Oak display",
                    level = 36,
                    xp = 120.0,
                    hotspots = listOf("loc.poh_hall1_4"),
                    built = listOf("loc.poh_trophy_fish_blank_oak"),
                ) {
                    material("obj.plank_oak", 2)
                }
            }
            row("dbrow.poh_teak_fish_display") {
                option(
                    "Teak display",
                    level = 56,
                    xp = 180.0,
                    hotspots = listOf("loc.poh_hall1_4"),
                    built = listOf("loc.poh_trophy_fish_blank_teak"),
                ) {
                    material("obj.plank_teak", 2)
                }
            }
            row("dbrow.poh_mahogany_fish_display") {
                option(
                    "Mahogany display",
                    level = 76,
                    xp = 280.0,
                    hotspots = listOf("loc.poh_hall1_4"),
                    built = listOf("loc.poh_trophy_fish_blank_mahogany"),
                ) {
                    material("obj.plank_mahogany", 2)
                }
            }
            row("dbrow.poh_mithril_armour") {
                option(
                    "Mithril armour",
                    level = 28,
                    xp = 135.0,
                    hotspots = listOf("loc.poh_hall1_5"),
                    built = listOf("loc.poh_armour_mithril_5"),
                ) {
                    material("obj.plank_oak", 2)
                    material("obj.mithril_full_helm", 1)
                    material("obj.mithril_platebody", 1)
                    material("obj.mithril_plateskirt", 1)
                }
            }
            row("dbrow.poh_adamantite_armour") {
                option(
                    "Adamantite armour",
                    level = 28,
                    xp = 150.0,
                    hotspots = listOf("loc.poh_hall1_5"),
                    built = listOf("loc.poh_armour_adamant_5"),
                ) {
                    material("obj.plank_oak", 2)
                    material("obj.adamant_full_helm", 1)
                    material("obj.adamant_platebody", 1)
                    material("obj.adamant_plateskirt", 1)
                }
            }
            row("dbrow.poh_runite_armour") {
                option(
                    "Runite armour",
                    level = 28,
                    xp = 165.0,
                    hotspots = listOf("loc.poh_hall1_5"),
                    built = listOf("loc.poh_armour_rune_5"),
                ) {
                    material("obj.plank_oak", 2)
                    material("obj.rune_full_helm", 1)
                    material("obj.rune_platebody", 1)
                    material("obj.rune_plateskirt", 1)
                }
            }
            row("dbrow.poh_red_castlewars_armour") {
                option(
                    "Red Castlewars armour",
                    level = 28,
                    xp = 135.0,
                    hotspots = listOf("loc.poh_hall1_6"),
                    built = listOf("loc.poh_armour_castlewars_red_6"),
                ) {
                    material("obj.plank_oak", 2)
                    material("obj.castlewars_med_helm", 1)
                    material("obj.castlewars_armour_body", 1)
                    material("obj.castlewars_shield", 1)
                }
            }
            row("dbrow.poh_white_castlewars_armour") {
                option(
                    "White Castlewars armour",
                    level = 28,
                    xp = 150.0,
                    hotspots = listOf("loc.poh_hall1_6"),
                    built = listOf("loc.poh_armour_castlewars_white_6"),
                ) {
                    material("obj.plank_oak", 2)
                    material("obj.castlewars_med_helm_2", 1)
                    material("obj.castlewars_armour_body_2", 1)
                    material("obj.castlewars_shield_2", 1)
                }
            }
            row("dbrow.poh_gold_castlewars_armour") {
                option(
                    "Gold Castlewars armour",
                    level = 28,
                    xp = 165.0,
                    hotspots = listOf("loc.poh_hall1_6"),
                    built = listOf("loc.poh_armour_castlewars_gold_6"),
                ) {
                    material("obj.plank_oak", 2)
                    material("obj.castlewars_med_helm_3", 1)
                    material("obj.castlewars_armour_body_3", 1)
                    material("obj.castlewars_shield_3", 1)
                }
            }
            row("dbrow.poh_rune_case_1") {
                option(
                    "Rune case 1",
                    level = 41,
                    xp = 190.0,
                    hotspots = listOf("loc.poh_hall1_7"),
                    built = listOf("loc.poh_display_case_rune1_6"),
                ) {
                    material("obj.plank_teak", 2)
                    material("obj.molten_glass", 2)
                    material("obj.airrune", 1)
                    material("obj.earthrune", 1)
                    material("obj.firerune", 1)
                    material("obj.waterrune", 1)
                }
            }
            row("dbrow.poh_rune_case_2") {
                option(
                    "Rune case 2",
                    level = 41,
                    xp = 212.0,
                    hotspots = listOf("loc.poh_hall1_7"),
                    built = listOf("loc.poh_display_case_rune2_6"),
                ) {
                    material("obj.plank_teak", 2)
                    material("obj.molten_glass", 2)
                    material("obj.bodyrune", 1)
                    material("obj.chaosrune", 1)
                    material("obj.cosmicrune", 1)
                    material("obj.naturerune", 1)
                }
            }
            row("dbrow.poh_rune_case_3") {
                option(
                    "Rune case 3",
                    level = 41,
                    xp = 247.0,
                    hotspots = listOf("loc.poh_hall1_7"),
                    built = listOf("loc.poh_display_case_rune3_6"),
                ) {
                    material("obj.plank_teak", 2)
                    material("obj.molten_glass", 2)
                    material("obj.bloodrune", 1)
                    material("obj.deathrune", 1)
                    material("obj.lawrune", 1)
                    material("obj.soulrune", 1)
                }
            }
            row("dbrow.poh_king_arthur_portrait") {
                option(
                    "King Arthur",
                    level = 35,
                    xp = 211.0,
                    hotspots = listOf("loc.poh_hall2_2"),
                    built = listOf("loc.poh_portrait_kingarthur_1"),
                ) {
                    material("obj.plank_teak", 2)
                    material("obj.poh_unframed_painting_kingarthur", 1)
                }
            }
            row("dbrow.poh_elena_portrait") {
                option(
                    "Elena",
                    level = 35,
                    xp = 211.0,
                    hotspots = listOf("loc.poh_hall2_2"),
                    built = listOf("loc.poh_portrait_elena_1"),
                ) {
                    material("obj.plank_teak", 2)
                    material("obj.poh_unframed_painting_elena", 1)
                }
            }
            row("dbrow.poh_giant_dwarf_portrait") {
                option(
                    "Giant Dwarf",
                    level = 35,
                    xp = 211.0,
                    hotspots = listOf("loc.poh_hall2_2"),
                    built = listOf("loc.poh_portrait_giantdwarf_1"),
                ) {
                    material("obj.plank_teak", 2)
                    material("obj.poh_unframed_painting_giantdwarf", 1)
                }
            }
            row("dbrow.poh_miscellanians_portrait") {
                option(
                    "Miscellanians",
                    level = 55,
                    xp = 311.0,
                    hotspots = listOf("loc.poh_hall2_2"),
                    built = listOf("loc.poh_portrait_prince+princess_1"),
                ) {
                    material("obj.plank_mahogany", 2)
                    material("obj.poh_unframed_painting_prince+princess", 1)
                }
            }
            row("dbrow.poh_landscape_lumbridge") {
                option(
                    "Lumbridge",
                    level = 44,
                    xp = 314.0,
                    hotspots = listOf("loc.poh_hall2_3"),
                    built = listOf("loc.poh_landscape_lumbridge_1"),
                ) {
                    material("obj.plank_teak", 3)
                    material("obj.poh_unframed_painting_lumbridge", 1)
                }
            }
            row("dbrow.poh_landscape_desert") {
                option(
                    "The desert",
                    level = 44,
                    xp = 314.0,
                    hotspots = listOf("loc.poh_hall2_3"),
                    built = listOf("loc.poh_landscape_desert_1"),
                ) {
                    material("obj.plank_teak", 3)
                    material("obj.poh_unframed_painting_desert", 1)
                }
            }
            row("dbrow.poh_landscape_morytania") {
                option(
                    "Morytania",
                    level = 44,
                    xp = 314.0,
                    hotspots = listOf("loc.poh_hall2_3"),
                    built = listOf("loc.poh_landscape_morytania_1"),
                ) {
                    material("obj.plank_teak", 3)
                    material("obj.poh_unframed_painting_morytania", 1)
                }
            }
            row("dbrow.poh_landscape_karamja") {
                option(
                    "Karamja",
                    level = 65,
                    xp = 464.0,
                    hotspots = listOf("loc.poh_hall2_3"),
                    built = listOf("loc.poh_landscape_karamja_1"),
                ) {
                    material("obj.plank_mahogany", 3)
                    material("obj.poh_unframed_painting_karamja", 1)
                }
            }
            row("dbrow.poh_landscape_isafdar") {
                option(
                    "Isafdar",
                    level = 65,
                    xp = 464.0,
                    hotspots = listOf("loc.poh_hall2_3"),
                    built = listOf("loc.poh_landscape_istafar_1"),
                ) {
                    material("obj.plank_mahogany", 3)
                    material("obj.poh_unframed_painting_istafar", 1)
                }
            }
            row("dbrow.poh_mounted_anti_dragon_shield") {
                option(
                    "Anti-dragon shield",
                    level = 47,
                    xp = 280.0,
                    hotspots = listOf("loc.poh_hall2_4"),
                    built = listOf("loc.poh_trophy_antidragonbreath_4"),
                ) {
                    material("obj.plank_teak", 3)
                    material("obj.antidragonbreathshield", 1)
                }
            }
            row("dbrow.poh_mounted_amulet_of_glory") {
                option(
                    "Amulet of glory",
                    level = 47,
                    xp = 290.0,
                    hotspots = listOf("loc.poh_hall2_4"),
                    built = listOf("loc.poh_trophy_amuletofglory_4"),
                ) {
                    material("obj.plank_teak", 3)
                    material("obj.amulet_of_glory", 1)
                }
            }
            row("dbrow.poh_mounted_cape_of_legends") {
                option(
                    "Cape of legends",
                    level = 47,
                    xp = 300.0,
                    hotspots = listOf("loc.poh_hall2_4"),
                    built = listOf("loc.poh_trophy_legendscape_4"),
                ) {
                    material("obj.plank_teak", 3)
                    material("obj.cape_of_legends", 1)
                }
            }
            row("dbrow.poh_mounted_mythical_cape") {
                option(
                    "Mythical cape",
                    level = 47,
                    xp = 370.0,
                    hotspots = listOf("loc.poh_hall2_4"),
                    built = listOf("loc.poh_trophy_mythical_cape"),
                ) {
                    material("obj.plank_teak", 3)
                    material("obj.mythical_cape", 1)
                }
            }
            row("dbrow.poh_mounted_silverlight") {
                option(
                    "Silverlight",
                    level = 42,
                    xp = 187.0,
                    hotspots = listOf("loc.poh_hall2_5"),
                    built = listOf("loc.poh_trophy_silverlight_5"),
                ) {
                    material("obj.plank_teak", 2)
                    material("obj.silverlight", 1)
                }
            }
            row("dbrow.poh_mounted_excalibur") {
                option(
                    "Excalibur",
                    level = 42,
                    xp = 194.0,
                    hotspots = listOf("loc.poh_hall2_5"),
                    built = listOf("loc.poh_trophy_excalibur_5"),
                ) {
                    material("obj.plank_teak", 2)
                    material("obj.excalibur", 1)
                }
            }
            row("dbrow.poh_mounted_darklight") {
                option(
                    "Darklight",
                    level = 42,
                    xp = 202.0,
                    hotspots = listOf("loc.poh_hall2_5"),
                    built = listOf("loc.poh_trophy_darklight_5"),
                ) {
                    material("obj.plank_teak", 2)
                    material("obj.darklight", 1)
                }
            }
            row("dbrow.poh_small_map") {
                option(
                    "Small map",
                    level = 38,
                    xp = 211.0,
                    hotspots = listOf("loc.poh_hall2_6"),
                    built = listOf("loc.poh_wall_map_freearea"),
                ) {
                    material("obj.plank_teak", 2)
                    material("obj.poh_unframed_painting_small_map", 1)
                }
            }
            row("dbrow.poh_medium_map") {
                option(
                    "Medium map",
                    level = 58,
                    xp = 451.0,
                    hotspots = listOf("loc.poh_hall2_6"),
                    built = listOf("loc.poh_wall_map_world"),
                ) {
                    material("obj.plank_mahogany", 3)
                    material("obj.poh_unframed_painting_medium_map", 1)
                }
            }
            row("dbrow.poh_large_map") {
                option(
                    "Large map",
                    level = 78,
                    xp = 591.0,
                    hotspots = listOf("loc.poh_hall2_6"),
                    built = listOf("loc.poh_wall_map_world+underground"),
                ) {
                    material("obj.plank_mahogany", 4)
                    material("obj.poh_unframed_painting_large_map", 1)
                }
            }

            /* Garden. */
            // TODO(unmatched): Pumpkin / Beehive (style 1 and 2) - seasonal built locs absent from rev 240.
            // TODO(unmatched): Exit portal (Annihilation unlock) - blueprint obj absent from rev 240.
            row("dbrow.poh_exit_portal_build") {
                option(
                    "Exit portal",
                    level = 1,
                    xp = 100.0,
                    hotspots = listOf("loc.poh_crude_garden_1", "loc.poh_posh_garden_1"),
                    built = listOf("loc.poh_exit_portal", "loc.poh_exit_portal"),
                ) {
                    material("obj.iron_bar", 10)
                }
            }
            row("dbrow.poh_garden_greenman_statue") {
                option(
                    "Greenman statue",
                    level = 1,
                    xp = 0.0,
                    hotspots = listOf("loc.poh_crude_garden_1", "loc.poh_posh_garden_1"),
                    built = listOf("loc.poh_greenman_statue_rot0", "loc.poh_greenman_statue_rot0"),
                ) {
                    material("obj.greenman_statue", 1)
                }
            }
            row("dbrow.poh_decorative_rock") {
                option(
                    "Decorative rock",
                    level = 5,
                    xp = 100.0,
                    hotspots = listOf("loc.poh_crude_garden_1"),
                    built = listOf("loc.poh_crude_garden_centrepiece2"),
                ) {
                    material("obj.limestonebrick", 5)
                }
            }
            row("dbrow.poh_pond") {
                option(
                    "Pond",
                    level = 10,
                    xp = 100.0,
                    hotspots = listOf("loc.poh_crude_garden_1"),
                    built = listOf("loc.poh_crude_garden_centrepiece3"),
                ) {
                    material("obj.softclay", 10)
                }
            }
            row("dbrow.poh_imp_statue") {
                option(
                    "Imp statue",
                    level = 15,
                    xp = 150.0,
                    hotspots = listOf("loc.poh_crude_garden_1"),
                    built = listOf("loc.poh_crude_garden_centrepiece4"),
                ) {
                    material("obj.limestonebrick", 5)
                    material("obj.softclay", 5)
                }
            }
            row("dbrow.poh_dungeon_entrance") {
                option(
                    "Dungeon entrance",
                    level = 70,
                    xp = 500.0,
                    hotspots = listOf("loc.poh_crude_garden_1", "loc.poh_posh_garden_1"),
                    built =
                        listOf(
                            "loc.poh_crude_garden_centrepiece5",
                            "loc.poh_crude_garden_centrepiece5",
                        ),
                ) {
                    material("obj.marble_block", 1)
                }
            }
            row("dbrow.poh_tip_jar") {
                option(
                    "Tip jar",
                    level = 40,
                    xp = 651.0,
                    hotspots = listOf("loc.poh_garden_8"),
                    built = listOf("loc.poh_tipjar"),
                ) {
                    material("obj.plank_mahogany", 2)
                    material("obj.molten_glass", 1)
                    material("obj.gold_leaf", 1)
                    material("obj.platinum", 5)
                }
            }
            row("dbrow.poh_garden_dead_tree") {
                option(
                    "Tree",
                    level = 5,
                    xp = 31.0,
                    hotspots = listOf("loc.poh_crude_garden_2", "loc.poh_crude_garden_3"),
                    built = listOf("loc.poh_big_tree1_4", "loc.poh_small_tree1_5"),
                ) {
                    material("obj.poh_sapling_tree_1", 1)
                }
            }
            row("dbrow.poh_garden_nice_tree") {
                option(
                    "Nice tree",
                    level = 10,
                    xp = 44.0,
                    hotspots = listOf("loc.poh_crude_garden_2", "loc.poh_crude_garden_3"),
                    built = listOf("loc.poh_big_tree2_4", "loc.poh_small_tree2_5"),
                ) {
                    material("obj.poh_sapling_tree_2", 1)
                }
            }
            row("dbrow.poh_garden_oak_tree") {
                option(
                    "Oak tree",
                    level = 15,
                    xp = 70.0,
                    hotspots = listOf("loc.poh_crude_garden_2", "loc.poh_crude_garden_3"),
                    built = listOf("loc.poh_big_tree3_4", "loc.poh_small_tree3_5"),
                ) {
                    material("obj.poh_sapling_tree_3", 1)
                }
            }
            row("dbrow.poh_garden_willow_tree") {
                option(
                    "Willow tree",
                    level = 30,
                    xp = 100.0,
                    hotspots = listOf("loc.poh_crude_garden_2", "loc.poh_crude_garden_3"),
                    built = listOf("loc.poh_big_tree4_4", "loc.poh_small_tree4_5"),
                ) {
                    material("obj.poh_sapling_tree_4", 1)
                }
            }
            row("dbrow.poh_garden_maple_tree") {
                option(
                    "Maple tree",
                    level = 45,
                    xp = 122.0,
                    hotspots = listOf("loc.poh_crude_garden_2", "loc.poh_crude_garden_3"),
                    built = listOf("loc.poh_big_tree5_4", "loc.poh_small_tree5_5"),
                ) {
                    material("obj.poh_sapling_tree_5", 1)
                }
            }
            row("dbrow.poh_garden_yew_tree") {
                option(
                    "Yew tree",
                    level = 60,
                    xp = 141.0,
                    hotspots = listOf("loc.poh_crude_garden_2", "loc.poh_crude_garden_3"),
                    built = listOf("loc.poh_big_tree6_4", "loc.poh_small_tree6_5"),
                ) {
                    material("obj.poh_sapling_tree_6", 1)
                }
            }
            row("dbrow.poh_garden_magic_tree") {
                option(
                    "Magic tree",
                    level = 75,
                    xp = 223.0,
                    hotspots = listOf("loc.poh_crude_garden_2", "loc.poh_crude_garden_3"),
                    built = listOf("loc.poh_big_tree7_4", "loc.poh_small_tree7_5"),
                ) {
                    material("obj.poh_sapling_tree_7", 1)
                }
            }
            row("dbrow.poh_garden_fern") {
                option(
                    "Fern",
                    level = 1,
                    xp = 31.0,
                    hotspots = listOf("loc.poh_crude_garden_4"),
                    built = listOf("loc.poh_plantbig1a"),
                ) {
                    material("obj.poh_sapling_plant_1", 1)
                }
            }
            row("dbrow.poh_garden_bush") {
                option(
                    "Bush",
                    level = 6,
                    xp = 70.0,
                    hotspots = listOf("loc.poh_crude_garden_4"),
                    built = listOf("loc.poh_plantbig1b"),
                ) {
                    material("obj.poh_sapling_plant_2", 1)
                }
            }
            row("dbrow.poh_garden_tall_plant") {
                option(
                    "Tall plant",
                    level = 12,
                    xp = 100.0,
                    hotspots = listOf("loc.poh_crude_garden_4"),
                    built = listOf("loc.poh_plantbig1c"),
                ) {
                    material("obj.poh_sapling_plant_3", 1)
                }
            }
            row("dbrow.poh_garden_short_plant") {
                option(
                    "Short plant",
                    level = 1,
                    xp = 31.0,
                    hotspots = listOf("loc.poh_crude_garden_5"),
                    built = listOf("loc.poh_plantbig2a"),
                ) {
                    material("obj.poh_sapling_plant_1", 1)
                }
            }
            row("dbrow.poh_garden_large_leaf_bush") {
                option(
                    "Large leaf bush",
                    level = 6,
                    xp = 70.0,
                    hotspots = listOf("loc.poh_crude_garden_5"),
                    built = listOf("loc.poh_plantbig2b"),
                ) {
                    material("obj.poh_sapling_plant_2", 1)
                }
            }
            row("dbrow.poh_garden_huge_plant") {
                option(
                    "Huge plant",
                    level = 12,
                    xp = 100.0,
                    hotspots = listOf("loc.poh_crude_garden_5"),
                    built = listOf("loc.poh_plantbig2c"),
                ) {
                    material("obj.poh_sapling_plant_3", 1)
                }
            }
            row("dbrow.poh_garden_plant") {
                option(
                    "Plant",
                    level = 1,
                    xp = 31.0,
                    hotspots = listOf("loc.poh_crude_garden_6"),
                    built = listOf("loc.poh_plantbsmall1a"),
                ) {
                    material("obj.poh_sapling_plant_1", 1)
                }
            }
            row("dbrow.poh_garden_small_fern") {
                option(
                    "Small fern",
                    level = 6,
                    xp = 70.0,
                    hotspots = listOf("loc.poh_crude_garden_6"),
                    built = listOf("loc.poh_plantbsmall1b"),
                ) {
                    material("obj.poh_sapling_plant_2", 1)
                }
            }
            row("dbrow.poh_garden_fern_small") {
                option(
                    "Fern",
                    level = 12,
                    xp = 100.0,
                    hotspots = listOf("loc.poh_crude_garden_6"),
                    built = listOf("loc.poh_plantbsmall1c"),
                ) {
                    material("obj.poh_sapling_plant_3", 1)
                }
            }
            row("dbrow.poh_garden_dock_leaf") {
                option(
                    "Dock leaf",
                    level = 1,
                    xp = 31.0,
                    hotspots = listOf("loc.poh_crude_garden_7"),
                    built = listOf("loc.poh_plantbsmall2a"),
                ) {
                    material("obj.poh_sapling_plant_1", 1)
                }
            }
            row("dbrow.poh_garden_thistle") {
                option(
                    "Thistle",
                    level = 6,
                    xp = 70.0,
                    hotspots = listOf("loc.poh_crude_garden_7"),
                    built = listOf("loc.poh_plantbsmall2b"),
                ) {
                    material("obj.poh_sapling_plant_2", 1)
                }
            }
            row("dbrow.poh_garden_reeds") {
                option(
                    "Reeds",
                    level = 12,
                    xp = 100.0,
                    hotspots = listOf("loc.poh_crude_garden_7"),
                    built = listOf("loc.poh_plantbsmall2c"),
                ) {
                    material("obj.poh_sapling_plant_3", 1)
                }
            }

            /* Formal garden. */
            row("dbrow.poh_gazebo") {
                option(
                    "Gazebo",
                    level = 65,
                    xp = 1200.0,
                    hotspots = listOf("loc.poh_posh_garden_1"),
                    built = listOf("loc.poh_posh_garden_centrepiece2"),
                ) {
                    material("obj.plank_mahogany", 8)
                    material("obj.steel_bar", 4)
                }
            }
            row("dbrow.poh_small_fountain") {
                option(
                    "Small fountain",
                    level = 71,
                    xp = 500.0,
                    hotspots = listOf("loc.poh_posh_garden_1"),
                    built = listOf("loc.poh_posh_garden_centrepiece3"),
                ) {
                    material("obj.marble_block", 1)
                }
            }
            row("dbrow.poh_large_fountain") {
                option(
                    "Large fountain",
                    level = 75,
                    xp = 1000.0,
                    hotspots = listOf("loc.poh_posh_garden_1"),
                    built = listOf("loc.poh_posh_garden_centrepiece4"),
                ) {
                    material("obj.marble_block", 2)
                }
            }
            row("dbrow.poh_posh_fountain") {
                option(
                    "Posh fountain",
                    level = 81,
                    xp = 1500.0,
                    hotspots = listOf("loc.poh_posh_garden_1"),
                    built = listOf("loc.poh_posh_garden_centrepiece5"),
                ) {
                    material("obj.marble_block", 3)
                }
            }
            row("dbrow.poh_boundary_stones") {
                option(
                    "Boundary stones",
                    level = 55,
                    xp = 100.0,
                    hotspots = listOf("loc.poh_posh_garden_4"),
                    built = listOf("loc.poh_fencing1"),
                    groupBuild = true,
                ) {
                    material("obj.softclay", 10)
                }
            }
            row("dbrow.poh_wooden_fence") {
                option(
                    "Wooden fence",
                    level = 59,
                    xp = 280.0,
                    hotspots = listOf("loc.poh_posh_garden_4"),
                    built = listOf("loc.poh_fencing2"),
                    groupBuild = true,
                ) {
                    material("obj.woodplank", 10)
                }
            }
            row("dbrow.poh_stone_wall") {
                option(
                    "Stone wall",
                    level = 63,
                    xp = 200.0,
                    hotspots = listOf("loc.poh_posh_garden_4"),
                    built = listOf("loc.poh_fencing3"),
                    groupBuild = true,
                ) {
                    material("obj.limestonebrick", 10)
                }
            }
            row("dbrow.poh_iron_railings") {
                option(
                    "Iron railings",
                    level = 67,
                    xp = 220.0,
                    hotspots = listOf("loc.poh_posh_garden_4"),
                    built = listOf("loc.poh_fencing4"),
                    groupBuild = true,
                ) {
                    material("obj.iron_bar", 10)
                    material("obj.limestonebrick", 6)
                }
            }
            row("dbrow.poh_picket_fence") {
                option(
                    "Picket fence",
                    level = 71,
                    xp = 640.0,
                    hotspots = listOf("loc.poh_posh_garden_4"),
                    built = listOf("loc.poh_fencing5"),
                    groupBuild = true,
                ) {
                    material("obj.plank_oak", 10)
                    material("obj.steel_bar", 2)
                }
            }
            row("dbrow.poh_garden_fence") {
                option(
                    "Garden fence",
                    level = 75,
                    xp = 940.0,
                    hotspots = listOf("loc.poh_posh_garden_4"),
                    built = listOf("loc.poh_fencing6"),
                    groupBuild = true,
                ) {
                    material("obj.plank_teak", 10)
                    material("obj.steel_bar", 2)
                }
            }
            row("dbrow.poh_marble_wall") {
                option(
                    "Marble wall",
                    level = 79,
                    xp = 4000.0,
                    hotspots = listOf("loc.poh_posh_garden_4"),
                    built = listOf("loc.poh_fencing7"),
                    groupBuild = true,
                ) {
                    material("obj.marble_block", 8)
                }
            }
            row("dbrow.poh_thorny_hedge") {
                option(
                    "Thorny hedge",
                    level = 56,
                    xp = 70.0,
                    hotspots =
                        listOf(
                            "loc.poh_posh_garden_5cor",
                            "loc.poh_posh_garden_5end",
                            "loc.poh_posh_garden_5mid",
                        ),
                    built =
                        listOf(
                            "loc.poh_hedgecorner1",
                            "loc.poh_hedgeend1",
                            "loc.poh_hedgemiddle1",
                        ),
                    groupBuild = true,
                ) {
                    material("obj.poh_sapling_hedge1", 1)
                }
            }
            row("dbrow.poh_nice_hedge") {
                option(
                    "Nice hedge",
                    level = 60,
                    xp = 100.0,
                    hotspots =
                        listOf(
                            "loc.poh_posh_garden_5cor",
                            "loc.poh_posh_garden_5end",
                            "loc.poh_posh_garden_5mid",
                        ),
                    built =
                        listOf(
                            "loc.poh_hedgecorner2",
                            "loc.poh_hedgeend2",
                            "loc.poh_hedgemiddle2",
                        ),
                    groupBuild = true,
                ) {
                    material("obj.poh_sapling_hedge2", 1)
                }
            }
            row("dbrow.poh_small_box_hedge") {
                option(
                    "Small box hedge",
                    level = 64,
                    xp = 122.0,
                    hotspots =
                        listOf(
                            "loc.poh_posh_garden_5cor",
                            "loc.poh_posh_garden_5end",
                            "loc.poh_posh_garden_5mid",
                        ),
                    built =
                        listOf(
                            "loc.poh_hedgecorner3",
                            "loc.poh_hedgeend3",
                            "loc.poh_hedgemiddle3",
                        ),
                    groupBuild = true,
                ) {
                    material("obj.poh_sapling_hedge3", 1)
                }
            }
            row("dbrow.poh_topiary_hedge") {
                option(
                    "Topiary hedge",
                    level = 68,
                    xp = 141.0,
                    hotspots =
                        listOf(
                            "loc.poh_posh_garden_5cor",
                            "loc.poh_posh_garden_5end",
                            "loc.poh_posh_garden_5mid",
                        ),
                    built =
                        listOf(
                            "loc.poh_hedgecorner4",
                            "loc.poh_hedgeend4",
                            "loc.poh_hedgemiddle4",
                        ),
                    groupBuild = true,
                ) {
                    material("obj.poh_sapling_hedge4", 1)
                }
            }
            row("dbrow.poh_fancy_hedge") {
                option(
                    "Fancy hedge",
                    level = 72,
                    xp = 158.0,
                    hotspots =
                        listOf(
                            "loc.poh_posh_garden_5cor",
                            "loc.poh_posh_garden_5end",
                            "loc.poh_posh_garden_5mid",
                        ),
                    built =
                        listOf(
                            "loc.poh_hedgecorner5",
                            "loc.poh_hedgeend5",
                            "loc.poh_hedgemiddle5",
                        ),
                    groupBuild = true,
                ) {
                    material("obj.poh_sapling_hedge5", 1)
                }
            }
            row("dbrow.poh_tall_fancy_hedge") {
                option(
                    "Tall fancy hedge",
                    level = 76,
                    xp = 223.0,
                    hotspots =
                        listOf(
                            "loc.poh_posh_garden_5cor",
                            "loc.poh_posh_garden_5end",
                            "loc.poh_posh_garden_5mid",
                        ),
                    built =
                        listOf(
                            "loc.poh_hedgecorner6",
                            "loc.poh_hedgeend6",
                            "loc.poh_hedgemiddle6",
                        ),
                    groupBuild = true,
                ) {
                    material("obj.poh_sapling_hedge6", 1)
                }
            }
            row("dbrow.poh_tall_box_hedge") {
                option(
                    "Tall box hedge",
                    level = 80,
                    xp = 316.0,
                    hotspots =
                        listOf(
                            "loc.poh_posh_garden_5cor",
                            "loc.poh_posh_garden_5end",
                            "loc.poh_posh_garden_5mid",
                        ),
                    built =
                        listOf(
                            "loc.poh_hedgecorner7",
                            "loc.poh_hedgeend7",
                            "loc.poh_hedgemiddle7",
                        ),
                    groupBuild = true,
                ) {
                    material("obj.poh_sapling_hedge7", 1)
                }
            }
            row("dbrow.poh_sunflower") {
                option(
                    "Sunflower",
                    level = 66,
                    xp = 70.0,
                    hotspots = listOf("loc.poh_posh_garden_2", "loc.poh_posh_garden_6"),
                    built = listOf("loc.poh_flowera1_big", "loc.poh_flowera1_small"),
                ) {
                    material("obj.poh_sapling_flowera1", 1)
                }
            }
            row("dbrow.poh_marigolds") {
                option(
                    "Marigolds",
                    level = 71,
                    xp = 100.0,
                    hotspots = listOf("loc.poh_posh_garden_2", "loc.poh_posh_garden_6"),
                    built = listOf("loc.poh_flowera2_big", "loc.poh_flowera2_small"),
                ) {
                    material("obj.poh_sapling_flowera2", 1)
                }
            }
            row("dbrow.poh_roses") {
                option(
                    "Roses",
                    level = 76,
                    xp = 122.0,
                    hotspots = listOf("loc.poh_posh_garden_2", "loc.poh_posh_garden_6"),
                    built = listOf("loc.poh_flowera3_big", "loc.poh_flowera3_small"),
                ) {
                    material("obj.poh_sapling_flowera3", 1)
                }
            }
            row("dbrow.poh_rosemary") {
                option(
                    "Rosemary",
                    level = 66,
                    xp = 70.0,
                    hotspots = listOf("loc.poh_posh_garden_3", "loc.poh_posh_garden_7"),
                    built = listOf("loc.poh_flowerb1_big", "loc.poh_flowerb1_small"),
                ) {
                    material("obj.poh_sapling_flowerb1", 1)
                }
            }
            row("dbrow.poh_daffodils") {
                option(
                    "Daffodils",
                    level = 71,
                    xp = 100.0,
                    hotspots = listOf("loc.poh_posh_garden_3", "loc.poh_posh_garden_7"),
                    built = listOf("loc.poh_flowerb2_big", "loc.poh_flowerb2_small"),
                ) {
                    material("obj.poh_sapling_flowerb2", 1)
                }
            }
            row("dbrow.poh_bluebells") {
                option(
                    "Bluebells",
                    level = 76,
                    xp = 122.0,
                    hotspots = listOf("loc.poh_posh_garden_3", "loc.poh_posh_garden_7"),
                    built = listOf("loc.poh_flowerb3_big", "loc.poh_flowerb3_small"),
                ) {
                    material("obj.poh_sapling_flowerb3", 1)
                }
            }

            /* Superior garden - pools upgrade the previous tier. */
            row("dbrow.poh_restoration_pool") {
                option(
                    "Restoration pool",
                    level = 65,
                    xp = 706.0,
                    hotspots = listOf("loc.poh_superior_garden_hotspot_pool"),
                    built = listOf("loc.poh_pool_restoration"),
                ) {
                    material("obj.limestonebrick", 5)
                    material("obj.bucket_water", 5)
                    material("obj.soulrune", 1000)
                    material("obj.bodyrune", 1000)
                }
            }
            row("dbrow.poh_revitalisation_pool") {
                option(
                    "Revitalisation pool",
                    level = 70,
                    xp = 850.0,
                    hotspots = listOf("loc.poh_pool_restoration"),
                    built = listOf("loc.poh_pool_revitalisation"),
                ) {
                    material("obj.4dosestamina", 10)
                }
            }
            row("dbrow.poh_rejuvenation_pool") {
                option(
                    "Rejuvenation pool",
                    level = 80,
                    xp = 900.0,
                    hotspots = listOf("loc.poh_pool_revitalisation"),
                    built = listOf("loc.poh_pool_rejuvenation"),
                ) {
                    material("obj.4doseprayerrestore", 10)
                }
            }
            row("dbrow.poh_fancy_rejuvenation_pool") {
                option(
                    "Fancy rejuvenation pool",
                    level = 85,
                    xp = 1950.0,
                    hotspots = listOf("loc.poh_pool_rejuvenation"),
                    built = listOf("loc.poh_pool_recovery"),
                ) {
                    material("obj.4dose2restore", 10)
                    material("obj.marble_block", 2)
                }
            }
            row("dbrow.poh_ornate_rejuvenation_pool") {
                option(
                    "Ornate rejuvenation pool",
                    level = 90,
                    xp = 3107.0,
                    hotspots = listOf("loc.poh_pool_recovery"),
                    built = listOf("loc.poh_pool_regeneration"),
                ) {
                    material("obj.antivenom4", 10)
                    material("obj.gold_leaf", 5)
                    material("obj.bloodrune", 1000)
                }
            }
            row("dbrow.poh_spirit_tree_teleport") {
                option(
                    "Spirit tree",
                    level = 75,
                    xp = 350.0,
                    hotspots = listOf("loc.poh_superior_garden_hotspot_treering"),
                    built = listOf("loc.poh_spirit_tree"),
                ) {
                    material("obj.plantpot_spirit_tree_sapling", 1)
                }
            }
            row("dbrow.poh_obelisk") {
                option(
                    "Obelisk",
                    level = 80,
                    xp = 3000.0,
                    hotspots = listOf("loc.poh_superior_garden_hotspot_treering"),
                    built = listOf("loc.poh_wilderness_obelisk"),
                ) {
                    material("obj.wild_cave_obelisk_crystal", 4)
                    material("obj.marble_block", 4)
                }
            }
            row("dbrow.poh_fairy_ring_teleport") {
                option(
                    "Fairy ring",
                    level = 85,
                    xp = 535.0,
                    hotspots = listOf("loc.poh_superior_garden_hotspot_treering"),
                    built = listOf("loc.poh_fairy_ring"),
                ) {
                    material("obj.mortmyremushroom", 10)
                    material("obj.poh_fairy_enchantment", 1)
                }
            }
            row("dbrow.poh_spiritual_fairy_tree") {
                option(
                    "Spirit tree & fairy ring",
                    level = 95,
                    xp = 885.0,
                    hotspots = listOf("loc.poh_superior_garden_hotspot_treering"),
                    built = listOf("loc.poh_spirit_ring"),
                ) {
                    material("obj.plantpot_spirit_tree_sapling", 1)
                    material("obj.mortmyremushroom", 10)
                    material("obj.poh_fairy_enchantment", 1)
                }
            }
            row("dbrow.poh_zen_theme") {
                option(
                    "Zen theme",
                    level = 65,
                    xp = 474.0,
                    hotspots = listOf("loc.poh_superior_garden_hotspot_theme_feature"),
                    built = listOf("loc.poh_theme_zen_hero"),
                    groupBuild = true,
                ) {
                    material("obj.bucket_sand", 6)
                    material("obj.handsand_pink_dye", 1)
                    material("obj.poh_sapling_tree_2", 1)
                }
            }
            row("dbrow.poh_otherworldly_theme") {
                option(
                    "Otherworldly theme",
                    level = 75,
                    xp = 316.0,
                    hotspots = listOf("loc.poh_superior_garden_hotspot_theme_feature"),
                    built = listOf("loc.poh_theme_zanaris_hero"),
                    groupBuild = true,
                ) {
                    material("obj.bucket_supercompost", 8)
                    material("obj.bluedye", 1)
                    material("obj.mortmyremushroom", 4)
                    material("obj.fairy_enchanted_secateurs", 1)
                }
            }
            row("dbrow.poh_volcanic_theme") {
                option(
                    "Volcanic theme",
                    level = 85,
                    xp = 4464.0,
                    hotspots = listOf("loc.poh_superior_garden_hotspot_theme_feature"),
                    built = listOf("loc.poh_theme_tzhaar_hero"),
                    groupBuild = true,
                ) {
                    material("obj.enakh_granite_medium", 2)
                    material("obj.onyx", 6)
                    material("obj.firerune", 1000)
                    material("obj.lavarune", 2000)
                }
            }
            row("dbrow.poh_topiary_bush") {
                option(
                    "Topiary bush",
                    level = 65,
                    xp = 141.0,
                    hotspots = listOf("loc.poh_superior_garden_hotspot_topiary"),
                    built = listOf("loc.poh_topiary_null"),
                ) {
                    material("obj.poh_superior_garden_topiary", 1)
                }
            }
            row("dbrow.poh_redwood_fence") {
                option(
                    "Redwood fence",
                    level = 75,
                    xp = 240.0,
                    hotspots =
                        listOf(
                            "loc.poh_superior_garden_hotspot_fence_middle",
                            "loc.poh_superior_garden_hotspot_fence_post",
                            "loc.poh_superior_garden_hotspot_fence_post_m",
                        ),
                    built =
                        listOf(
                            "loc.poh_redwood_fence_middle",
                            "loc.poh_redwood_fence_post",
                            "loc.poh_redwood_fence_post_m",
                        ),
                    groupBuild = true,
                ) {
                    material("obj.redwood_logs", 10)
                    material("obj.steel_bar", 2)
                }
            }
            row("dbrow.poh_superior_marble_wall") {
                option(
                    "Marble wall",
                    level = 79,
                    xp = 4000.0,
                    hotspots =
                        listOf(
                            "loc.poh_superior_garden_hotspot_fence_middle",
                            "loc.poh_superior_garden_hotspot_fence_post",
                            "loc.poh_superior_garden_hotspot_fence_post_m",
                        ),
                    built = listOf("loc.poh_fencing7", "loc.poh_fencing7", "loc.poh_fencing7"),
                    groupBuild = true,
                ) {
                    material("obj.marble_block", 8)
                }
            }
            row("dbrow.poh_obsidian_fence") {
                option(
                    "Obsidian fence",
                    level = 83,
                    xp = 2741.0,
                    hotspots =
                        listOf(
                            "loc.poh_superior_garden_hotspot_fence_middle",
                            "loc.poh_superior_garden_hotspot_fence_post",
                            "loc.poh_superior_garden_hotspot_fence_post_m",
                        ),
                    built =
                        listOf(
                            "loc.poh_obsidian_fence_middle",
                            "loc.poh_obsidian_fence_post",
                            "loc.poh_obsidian_fence_post_m",
                        ),
                    groupBuild = true,
                ) {
                    material("obj.tzhaar_staff", 10)
                    material("obj.tzhaar_maul", 2)
                    material("obj.tzhaar_throwingring", 25)
                }
            }
            row("dbrow.poh_teak_garden_bench") {
                option(
                    "Teak garden bench",
                    level = 66,
                    xp = 540.0,
                    hotspots =
                        listOf(
                            "loc.poh_superior_garden_hotspot_seating_a_left",
                            "loc.poh_superior_garden_hotspot_seating_a_right",
                        ),
                    built =
                        listOf(
                            "loc.poh_garden_bench_teak_left",
                            "loc.poh_garden_bench_teak_right",
                        ),
                    groupBuild = true,
                ) {
                    material("obj.plank_teak", 6)
                }
            }
            row("dbrow.poh_teak_garden_bench_2") {
                option(
                    "Teak garden bench",
                    level = 66,
                    xp = 540.0,
                    hotspots =
                        listOf(
                            "loc.poh_superior_garden_hotspot_seating_b_left",
                            "loc.poh_superior_garden_hotspot_seating_b_right",
                        ),
                    built =
                        listOf(
                            "loc.poh_garden_bench_teak_left",
                            "loc.poh_garden_bench_teak_right",
                        ),
                    groupBuild = true,
                ) {
                    material("obj.plank_teak", 6)
                }
            }
            row("dbrow.poh_gnome_bench") {
                option(
                    "Gnome bench",
                    level = 77,
                    xp = 840.0,
                    hotspots =
                        listOf(
                            "loc.poh_superior_garden_hotspot_seating_a_left",
                            "loc.poh_superior_garden_hotspot_seating_a_right",
                        ),
                    built =
                        listOf(
                            "loc.poh_garden_bench_gnome_left",
                            "loc.poh_garden_bench_gnome_right",
                        ),
                    groupBuild = true,
                ) {
                    material("obj.plank_mahogany", 6)
                }
            }
            row("dbrow.poh_gnome_bench_2") {
                option(
                    "Gnome bench",
                    level = 77,
                    xp = 840.0,
                    hotspots =
                        listOf(
                            "loc.poh_superior_garden_hotspot_seating_b_left",
                            "loc.poh_superior_garden_hotspot_seating_b_right",
                        ),
                    built =
                        listOf(
                            "loc.poh_garden_bench_gnome_left",
                            "loc.poh_garden_bench_gnome_right",
                        ),
                    groupBuild = true,
                ) {
                    material("obj.plank_mahogany", 6)
                }
            }
            row("dbrow.poh_marble_bench") {
                option(
                    "Marble decorative bench",
                    level = 88,
                    xp = 3000.0,
                    hotspots =
                        listOf(
                            "loc.poh_superior_garden_hotspot_seating_a_left",
                            "loc.poh_superior_garden_hotspot_seating_a_right",
                        ),
                    built =
                        listOf(
                            "loc.poh_garden_bench_marble_left",
                            "loc.poh_garden_bench_marble_right",
                        ),
                    groupBuild = true,
                ) {
                    material("obj.marble_block", 6)
                }
            }
            row("dbrow.poh_marble_bench_2") {
                option(
                    "Marble decorative bench",
                    level = 88,
                    xp = 3000.0,
                    hotspots =
                        listOf(
                            "loc.poh_superior_garden_hotspot_seating_b_left",
                            "loc.poh_superior_garden_hotspot_seating_b_right",
                        ),
                    built =
                        listOf(
                            "loc.poh_garden_bench_marble_left",
                            "loc.poh_garden_bench_marble_right",
                        ),
                    groupBuild = true,
                ) {
                    material("obj.marble_block", 6)
                }
            }
            row("dbrow.poh_obsidian_bench") {
                option(
                    "Obsidian decorative bench",
                    level = 98,
                    xp = 2331.0,
                    hotspots =
                        listOf(
                            "loc.poh_superior_garden_hotspot_seating_a_left",
                            "loc.poh_superior_garden_hotspot_seating_a_right",
                        ),
                    built =
                        listOf(
                            "loc.poh_garden_bench_obsidian_left",
                            "loc.poh_garden_bench_obsidian_right",
                        ),
                    groupBuild = true,
                ) {
                    material("obj.marble_block", 3)
                    material("obj.onyx", 1)
                    material("obj.firerune", 250)
                    material("obj.lavarune", 500)
                }
            }
            row("dbrow.poh_obsidian_bench_2") {
                option(
                    "Obsidian decorative bench",
                    level = 98,
                    xp = 2331.0,
                    hotspots =
                        listOf(
                            "loc.poh_superior_garden_hotspot_seating_b_left",
                            "loc.poh_superior_garden_hotspot_seating_b_right",
                        ),
                    built =
                        listOf(
                            "loc.poh_garden_bench_obsidian_left",
                            "loc.poh_garden_bench_obsidian_right",
                        ),
                    groupBuild = true,
                ) {
                    material("obj.marble_block", 3)
                    material("obj.onyx", 1)
                    material("obj.firerune", 250)
                    material("obj.lavarune", 500)
                }
            }

            /* Achievement gallery - the occult altar upgrades whichever spellbook altar is built. */
            row("dbrow.poh_ancient_altar") {
                option(
                    "Ancient altar",
                    level = 80,
                    xp = 1490.0,
                    hotspots = listOf("loc.poh_achievement_hotspot_altar"),
                    built = listOf("loc.poh_altar_ancient"),
                ) {
                    material("obj.limestonebrick", 10)
                    material("obj.poh_magic_crystal", 1)
                    material("obj.poh_ancient_signet", 1)
                    material("obj.pharaohs_sceptre", 1)
                }
            }
            row("dbrow.poh_lunar_altar") {
                option(
                    "Lunar altar",
                    level = 80,
                    xp = 1957.0,
                    hotspots = listOf("loc.poh_achievement_hotspot_altar"),
                    built = listOf("loc.poh_altar_lunar"),
                ) {
                    material("obj.limestonebrick", 10)
                    material("obj.poh_magic_crystal", 1)
                    material("obj.poh_lunar_signet", 1)
                    material("obj.astralrune", 10000)
                }
            }
            row("dbrow.poh_dark_altar") {
                option(
                    "Dark altar",
                    level = 80,
                    xp = 3888.0,
                    hotspots = listOf("loc.poh_achievement_hotspot_altar"),
                    built = listOf("loc.poh_altar_dark"),
                ) {
                    material("obj.limestonebrick", 10)
                    material("obj.poh_magic_crystal", 1)
                    material("obj.poh_arceuus_signet", 1)
                    material("obj.bloodrune", 5000)
                    material("obj.soulrune", 5000)
                }
            }
            row("dbrow.poh_occult_altar") {
                option(
                    "Occult altar",
                    level = 90,
                    xp = 3445.0,
                    hotspots = listOf("loc.poh_altar_ancient"),
                    built = listOf("loc.poh_altar_occult_standard"),
                ) {
                    material("obj.poh_lunar_signet", 1)
                    material("obj.astralrune", 10000)
                    material("obj.poh_arceuus_signet", 1)
                    material("obj.bloodrune", 5000)
                    material("obj.soulrune", 5000)
                }
            }
            row("dbrow.poh_occult_altar_2") {
                option(
                    "Occult altar",
                    level = 90,
                    xp = 3445.0,
                    hotspots = listOf("loc.poh_altar_lunar"),
                    built = listOf("loc.poh_altar_occult_standard"),
                ) {
                    material("obj.poh_ancient_signet", 1)
                    material("obj.pharaohs_sceptre", 1)
                    material("obj.poh_arceuus_signet", 1)
                    material("obj.bloodrune", 5000)
                    material("obj.soulrune", 5000)
                }
            }
            row("dbrow.poh_occult_altar_3") {
                option(
                    "Occult altar",
                    level = 90,
                    xp = 3445.0,
                    hotspots = listOf("loc.poh_altar_dark"),
                    built = listOf("loc.poh_altar_occult_standard"),
                ) {
                    material("obj.poh_ancient_signet", 1)
                    material("obj.pharaohs_sceptre", 1)
                    material("obj.poh_lunar_signet", 1)
                    material("obj.astralrune", 10000)
                }
            }
            row("dbrow.poh_mahogany_adventure_log") {
                option(
                    "Mahogany adventure log",
                    level = 83,
                    xp = 504.0,
                    hotspots = listOf("loc.poh_achievement_hotspot_log"),
                    built = listOf("loc.poh_adventure_log_1"),
                ) {
                    material("obj.plank_mahogany", 3)
                    material("obj.papyrus", 2)
                    material("obj.slayer_gem", 1)
                }
            }
            row("dbrow.poh_gilded_adventure_log") {
                option(
                    "Gilded adventure log",
                    level = 88,
                    xp = 1100.0,
                    hotspots = listOf("loc.poh_achievement_hotspot_log"),
                    built = listOf("loc.poh_adventure_log_2"),
                ) {
                    material("obj.plank_mahogany", 3)
                    material("obj.gold_leaf", 2)
                    material("obj.slayer_gem", 1)
                }
            }
            row("dbrow.poh_marble_adventure_log") {
                option(
                    "Marble adventure log",
                    level = 93,
                    xp = 1160.0,
                    hotspots = listOf("loc.poh_achievement_hotspot_log"),
                    built = listOf("loc.poh_adventure_log_3"),
                ) {
                    material("obj.marble_block", 2)
                    material("obj.limestonebrick", 4)
                    material("obj.slayer_gem", 1)
                }
            }
            row("dbrow.poh_boss_lair_display") {
                option(
                    "Boss lair display",
                    level = 87,
                    xp = 1483.0,
                    hotspots = listOf("loc.poh_achievement_hotspot_lair"),
                    built = listOf("loc.poh_display_blank"),
                ) {
                    material("obj.steel_bar", 4)
                    material("obj.molten_glass", 5)
                    material("obj.plank_mahogany", 10)
                }
            }
            row("dbrow.poh_mounted_emblem") {
                option(
                    "Mounted emblem",
                    level = 80,
                    xp = 5300.0,
                    hotspots = listOf("loc.poh_achievement_hotspot_display"),
                    built = listOf("loc.poh_mounted_emblem"),
                ) {
                    material("obj.marble_block", 1)
                    material("obj.gold_leaf", 1)
                    material("obj.bh_decorative_emblem_10", 1)
                }
            }
            row("dbrow.poh_mounted_coins") {
                option(
                    "Mounted coins",
                    level = 80,
                    xp = 800.0,
                    hotspots = listOf("loc.poh_achievement_hotspot_display"),
                    built = listOf("loc.poh_mounted_coins"),
                ) {
                    material("obj.marble_block", 1)
                    material("obj.gold_leaf", 1)
                    material("obj.coins", 100000000)
                }
            }
            row("dbrow.poh_cape_hanger") {
                option(
                    "Cape hanger",
                    level = 80,
                    xp = 800.0,
                    hotspots = listOf("loc.poh_achievement_hotspot_display"),
                    built = listOf("loc.poh_mounted_capestand_blank"),
                ) {
                    material("obj.marble_block", 1)
                    material("obj.gold_leaf", 1)
                }
            }
            row("dbrow.poh_basic_jewellery_box") {
                option(
                    "Basic jewellery box",
                    level = 81,
                    xp = 605.0,
                    hotspots = listOf("loc.poh_achievement_hotspot_jewellerybox"),
                    built = listOf("loc.poh_jewellery_box_1_base"),
                ) {
                    material("obj.cloth", 1)
                    material("obj.steel_bar", 1)
                    material("obj.necklace_of_minigames_8", 3)
                    material("obj.ring_of_dueling_8", 3)
                }
            }
            row("dbrow.poh_fancy_jewellery_box") {
                option(
                    "Fancy jewellery box",
                    level = 86,
                    xp = 1350.0,
                    hotspots = listOf("loc.poh_jewellery_box_1_base"),
                    built = listOf("loc.poh_jewellery_box_2_base"),
                ) {
                    material("obj.gold_leaf", 1)
                    material("obj.jewl_necklace_of_skills_4", 5)
                    material("obj.jewl_bracelet_of_combat_4", 5)
                }
            }
            row("dbrow.poh_ornate_jewellery_box") {
                option(
                    "Ornate jewellery box",
                    level = 91,
                    xp = 2680.0,
                    hotspots = listOf("loc.poh_jewellery_box_2_base"),
                    built = listOf("loc.poh_jewellery_box_3_base"),
                ) {
                    material("obj.gold_leaf", 2)
                    material("obj.amulet_of_glory_4", 8)
                    material("obj.ring_of_wealth_5", 8)
                }
            }
            row("dbrow.poh_quest_list") {
                option(
                    "Quest list",
                    level = 80,
                    xp = 310.0,
                    hotspots = listOf("loc.poh_achievement_hotspot_questlist"),
                    built = listOf("loc.poh_quest_list"),
                ) {
                    material("obj.papyrus", 10)
                    material("obj.gold_leaf", 1)
                }
            }

            /* Portal nexus - gilded/crystalline tiers upgrade the built nexus. */
            row("dbrow.poh_marble_portal_nexus") {
                option(
                    "Marble portal nexus",
                    level = 72,
                    xp = 2000.0,
                    hotspots = listOf("loc.poh_telenexus_1"),
                    built = listOf("loc.poh_nexus_portal_1"),
                ) {
                    material("obj.marble_block", 4)
                }
            }
            row("dbrow.poh_gilded_portal_nexus") {
                option(
                    "Gilded portal nexus",
                    level = 82,
                    xp = 2600.0,
                    hotspots = listOf("loc.poh_nexus_portal_1"),
                    built = listOf("loc.poh_nexus_portal_2"),
                ) {
                    material("obj.marble_block", 4)
                    material("obj.gold_leaf", 2)
                }
            }
            row("dbrow.poh_crystalline_portal_nexus") {
                option(
                    "Crystalline portal nexus",
                    level = 92,
                    xp = 2600.0,
                    hotspots = listOf("loc.poh_nexus_portal_2"),
                    built = listOf("loc.poh_nexus_portal_3"),
                ) {
                    material("obj.poh_magic_crystal", 2)
                    material("obj.gold_leaf", 2)
                }
            }
            row("dbrow.poh_mounted_xerics_talisman") {
                option(
                    "Mounted xeric's talisman",
                    level = 72,
                    xp = 500.0,
                    hotspots = listOf("loc.poh_nexus_4_amulet", "loc.poh_nexus_5_amulet"),
                    built = listOf("loc.poh_amulet_xeric", "loc.poh_amulet_xeric"),
                ) {
                    material("obj.plank_mahogany", 1)
                    material("obj.gold_leaf", 1)
                    material("obj.xeric_talisman_empty", 1)
                    material("obj.lizardman_fang", 5000)
                }
            }
            row("dbrow.poh_mounted_digsite_pendant") {
                option(
                    "Mounted digsite pendant",
                    level = 82,
                    xp = 800.0,
                    hotspots = listOf("loc.poh_nexus_4_amulet", "loc.poh_nexus_5_amulet"),
                    built = listOf("loc.poh_amulet_digsite", "loc.poh_amulet_digsite"),
                ) {
                    material("obj.plank_mahogany", 1)
                    material("obj.gold_leaf", 1)
                    material("obj.poh_curator_medallion", 1)
                }
            }
        }
}
