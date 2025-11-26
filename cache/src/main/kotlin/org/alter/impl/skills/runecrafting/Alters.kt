package org.alter.impl.skills.runecrafting

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType
import dev.openrune.util.Coords
import org.alter.impl.skills.runecrafting.Tiara.ITEM

enum class AltarData(
    val ruins: List<String>? = null,
    val altar: String,
    val exitPortal: String? = null,
    val talisman: String? = null,
    val tiara: String? = null,
    val varbit: String? = null,
    val rune: Rune,
    val entrance: Int? = null,
    val exit: Int? = null,
    val option: String = "craft-rune",
    val row : String,
    val combo : List<CombinationRuneData> = emptyList()
) {
    AIR(
        ruins = listOf("objects.airtemple_ruined_old", "objects.airtemple_ruined_new"),
        altar = "objects.air_altar",
        exitPortal = "objects.airtemple_exit_portal",
        talisman = "items.air_talisman",
        tiara = "dbrows.runecrafting_tiara_air",
        varbit = "varbits.rc_no_tally_required_air",
        rune = Rune.AIR,
        entrance = Coords(2841, 4830),
        exit = Coords(2983, 3288),
        row = "dbrows.runecrafting_altar_air",
        combo = listOf(CombinationRuneData.MIST_AIR, CombinationRuneData.SMOKE_AIR)
    ),
    MIND(
        ruins = listOf("objects.mindtemple_ruined_old", "objects.mindtemple_ruined_new"),
        altar = "objects.mind_altar",
        exitPortal = "objects.mindtemple_exit_portal",
        talisman = "items.mind_talisman",
        tiara = "dbrows.runecrafting_tiara_mind",
        varbit = "varbits.rc_no_tally_required_mind",
        rune = Rune.MIND,
        entrance = Coords(2793, 4829),
        exit = Coords(2980, 3511),
        row = "dbrows.runecrafting_altar_mind"
    ),
    WATER(
        ruins = listOf("objects.watertemple_ruined_old", "objects.watertemple_ruined_new"),
        altar = "objects.water_altar",
        exitPortal = "objects.watertemple_exit_portal",
        talisman = "items.water_talisman",
        tiara = "dbrows.runecrafting_tiara_water",
        varbit = "varbits.rc_no_tally_required_water",
        rune = Rune.WATER,
        entrance = Coords(2725, 4832),
        exit = Coords(3182, 3162),
        row = "dbrows.runecrafting_altar_water",
        combo = listOf(CombinationRuneData.MUD_WATER, CombinationRuneData.MIST_WATER,CombinationRuneData.STEAM_WATER)
    ),
    EARTH(
        ruins = listOf("objects.earthtemple_ruined_old", "objects.earthtemple_ruined_new"),
        altar = "objects.earth_altar",
        exitPortal = "objects.earthtemple_exit_portal",
        talisman = "items.earth_talisman",
        tiara = "dbrows.runecrafting_tiara_earth",
        varbit = "varbits.rc_no_tally_required_earth",
        rune = Rune.EARTH,
        entrance = Coords(2657, 4830),
        exit = Coords(3302, 3477),
        row = "dbrows.runecrafting_altar_earth",
        combo = listOf(CombinationRuneData.DUST_EARTH, CombinationRuneData.MUD_EARTH,CombinationRuneData.LAVA_EARTH)
    ),
    FIRE(
        ruins = listOf("objects.firetemple_ruined_old", "objects.firetemple_ruined_new"),
        altar = "objects.fire_altar",
        exitPortal = "objects.firetemple_exit_portal",
        talisman = "items.fire_talisman",
        tiara = "dbrows.runecrafting_tiara_fire",
        varbit = "varbits.rc_no_tally_required_fire",
        rune = Rune.FIRE,
        entrance = Coords(2576, 4848),
        exit = Coords(3310, 3252),
        row = "dbrows.runecrafting_altar_fire",
        combo = listOf(CombinationRuneData.LAVA_FIRE, CombinationRuneData.SMOKE_FIRE,CombinationRuneData.STEAM_FIRE)
    ),
    BODY(
        ruins = listOf("objects.bodytemple_ruined_old", "objects.bodytemple_ruined_new"),
        altar = "objects.body_altar",
        exitPortal = "objects.bodytemple_exit_portal",
        talisman = "items.body_talisman",
        tiara = "dbrows.runecrafting_tiara_body",
        varbit = "varbits.rc_no_tally_required_body",
        rune = Rune.BODY,
        entrance = Coords(2519, 4847),
        exit = Coords(3050, 3442),
        row = "dbrows.runecrafting_altar_body"
    ),
    COSMIC(
        ruins = listOf("objects.cosmictemple_ruined_old", "objects.cosmictemple_ruined_new"),
        altar = "objects.cosmic_altar",
        exitPortal = "objects.cosmictemple_exit_portal",
        talisman = "items.cosmic_talisman",
        tiara = "dbrows.runecrafting_tiara_cosmic",
        varbit = "varbits.rc_no_tally_required_cosmic",
        rune = Rune.COSMIC,
        entrance = Coords(2142, 4813),
        exit = Coords(2405, 4381),
        row = "dbrows.runecrafting_altar_cosmic"
    ),
    CHAOS(
        ruins = listOf("objects.chaostemple_ruined_old", "objects.chaostemple_ruined_new"),
        altar = "objects.chaos_altar",
        exitPortal = "objects.chaostemple_exit_portal",
        talisman = "items.chaos_talisman",
        tiara = "dbrows.runecrafting_tiara_chaos",
        varbit = "varbits.rc_no_tally_required_chaos",
        rune = Rune.CHAOS,
        entrance = Coords(2280, 4837),
        exit = Coords(3060, 3585),
        row = "dbrows.runecrafting_altar_chaos"
    ),
    ASTRAL(
        altar = "objects.astral_altar",
        rune = Rune.ASTRAL,
        row = "dbrows.runecrafting_altar_astral"
    ),
    NATURE(
        ruins = listOf("objects.naturetemple_ruined_old", "objects.naturetemple_ruined_new"),
        altar = "objects.nature_altar",
        exitPortal = "objects.naturetemple_exit_portal",
        talisman = "items.nature_talisman",
        tiara = "dbrows.runecrafting_tiara_nature",
        varbit = "varbits.rc_no_tally_required_nature",
        rune = Rune.NATURE,
        entrance = Coords(2400, 4835),
        exit = Coords(2865, 3022),
        row = "dbrows.runecrafting_altar_nature"
    ),
    LAW(
        ruins = listOf("objects.lawtemple_ruined_old", "objects.lawtemple_ruined_new"),
        altar = "objects.law_altar",
        exitPortal = "objects.lawtemple_exit_portal",
        talisman = "items.law_talisman",
        tiara = "dbrows.runecrafting_tiara_law",
        varbit = "varbits.rc_no_tally_required_law",
        rune = Rune.LAW,
        entrance = Coords(2464, 4819),
        exit = Coords(2858, 3378),
        row = "dbrows.runecrafting_altar_law"
    ),
    DEATH(
        ruins = listOf("objects.deathtemple_ruined_old", "objects.deathtemple_ruined_new"),
        altar = "objects.death_altar",
        exitPortal = "objects.deathtemple_exit_portal",
        talisman = "items.death_talisman",
        tiara = "dbrows.runecrafting_tiara_death",
        varbit = "varbits.rc_no_tally_required_death",
        rune = Rune.DEATH,
        entrance = Coords(2208, 4830),
        exit = Coords(1863, 4639),
        row = "dbrows.runecrafting_altar_death"
    ),
    BLOOD(
        altar = "objects.blood_altar",
        rune = Rune.BLOOD,
        option = "bind",
        row = "dbrows.runecrafting_altar_blood"
    ),

    SOUL(
        altar = "objects.archeus_altar_soul",
        rune = Rune.SOUL,
        option = "bind",
        row = "dbrows.runecrafting_altar_soul"
    ),
    WRATH(
        ruins = listOf("objects.wrathtemple_ruined_0op", "objects.wrathtemple_ruined_1op"),
        altar = "objects.wrath_altar",
        exitPortal = "objects.wrathtemple_exit_portal",
        talisman = "items.wrath_talisman",
        tiara = "dbrows.runecrafting_tiara_wrath",
        varbit = "varbits.rc_no_tally_required_wrath",
        rune = Rune.WRATH,
        entrance = Coords(2335, 4826),
        exit = Coords(2447, 2822),
        row = "dbrows.runecrafting_altar_wrath"
    );

    companion object {
        val values = enumValues<AltarData>()
    }
}

object Alters {

    const val ALTAR_OBJECT = 0
    const val EXIT_PORTAL = 1
    const val TALISMAN = 2
    const val TIARA_ITEM = 3
    const val VARBIT = 4
    const val RUNE = 5
    const val ENTRANCE = 6
    const val EXIT = 7
    const val RUINS = 8
    const val COMBO = 9

    fun altars() = dbTable("tables.runecrafting_altars") {

        column("altar_object", ALTAR_OBJECT, VarType.LOC)
        column("exit_portal", EXIT_PORTAL, VarType.LOC)
        column("talisman", TALISMAN, VarType.OBJ)
        column("tiara", TIARA_ITEM, VarType.DBROW)
        column("varbit", VARBIT, VarType.INT)
        column("rune", RUNE, VarType.DBROW)
        column("entrance", ENTRANCE, VarType.COORDGRID)
        column("exit", EXIT, VarType.COORDGRID)
        column("ruins", RUINS, VarType.LOC)
        column("combo", COMBO, VarType.DBROW)

        AltarData.values.forEach {
            row(it.row) {
                columnRSCM(ALTAR_OBJECT, it.altar)
                columnRSCM(RUNE, it.rune.dbId)

                if (it.ruins != null) {
                    columnRSCM(RUINS, *it.ruins.toTypedArray())
                }

                if (it.exit != null) {
                    column(EXIT, it.exit)
                }

                if (it.exitPortal != null) {
                    columnRSCM(EXIT_PORTAL, it.exitPortal)
                }

                if (it.talisman != null) {
                    columnRSCM(TALISMAN, it.talisman)
                }

                if (it.tiara != null) {
                    columnRSCM(TIARA_ITEM, it.tiara)
                }

                if (it.entrance != null) {
                    column(ENTRANCE, it.entrance)
                }

                if (it.varbit != null) {
                    columnRSCM(VARBIT, it.varbit)
                }

                if (it.combo.isNotEmpty()) {
                    columnRSCM(COMBO, *it.combo.map { combo -> combo.row }.toTypedArray())
                }

            }
        }

    }

}