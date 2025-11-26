package org.alter.impl.skills.runecrafting

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType
import org.alter.impl.skills.runecrafting.RunecraftRune.ESSENCE

enum class CombinationRuneData(val runeOutput: String, val level: Int, val xp: Double, val runeInput: String, val row : String, val talisman : String) {
    MIST_AIR(runeOutput = "items.mistrune", level = 6, xp = 8.0, runeInput = "items.waterrune","dbrows.mist_from_wateraltar","items.water_talisman"),
    MIST_WATER(runeOutput = "items.mistrune", level = 6, xp = 8.5, runeInput = "items.airrune","dbrows.mist_from_airaltar","items.air_talisman"),

    DUST_AIR(runeOutput = "items.dustrune", level = 10, xp = 8.3, runeInput = "items.earthrune","dbrows.dust_from_earthaltar","items.earth_talisman"),
    DUST_EARTH(runeOutput = "items.dustrune", level = 10, xp = 9.0, runeInput = "items.airrune","dbrows.dust_from_airaltar","items.air_talisman"),

    MUD_WATER(runeOutput = "items.mudrune", level = 13, xp = 9.3, runeInput = "items.earthrune","dbrows.mud_from_earthaltar","items.earth_talisman"),
    MUD_EARTH(runeOutput = "items.mudrune", level = 13, xp = 9.5, runeInput = "items.waterrune","dbrows.mud_from_wateraltar","items.water_talisman"),

    SMOKE_AIR(runeOutput = "items.smokerune", level = 15, xp = 8.5, runeInput = "items.firerune","dbrows.smoke_from_firealtar","items.fire_talisman"),
    SMOKE_FIRE(runeOutput = "items.smokerune", level = 15, xp = 9.5, runeInput = "items.airrune","dbrows.smoke_from_airaltar","items.air_talisman"),

    STEAM_WATER(runeOutput = "items.steamrune", level = 19, xp = 9.5, runeInput = "items.firerune","dbrows.steam_from_firealtar","items.fire_talisman"),
    STEAM_FIRE(runeOutput = "items.steamrune", level = 19, xp = 10.0, runeInput = "items.waterrune","dbrows.steam_from_wateraltar","items.water_talisman"),

    LAVA_EARTH(runeOutput = "items.lavarune", level = 23, xp = 10.0, runeInput = "items.firerune","dbrows.lava_from_firealtar","items.fire_talisman"),
    LAVA_FIRE(runeOutput = "items.lavarune", level = 23, xp = 10.5, runeInput = "items.earthrune","dbrows.lava_from_earthaltar","items.earth_talisman");

}

object CombinationRune {

    const val RUNE_OUTPUT = 0
    const val LEVEL = 1
    const val XP = 2
    const val RUNE_INPUT = 3
    const val TALISMAN = 4

    fun runecraftComboRune() = dbTable("tables.comborune_recipe") {

        column("rune_output", RUNE_OUTPUT, VarType.OBJ)
        column("level", LEVEL, VarType.INT)
        column("xp", XP, VarType.INT)
        column("rune_input", RUNE_INPUT, VarType.OBJ)
        column("talisman", TALISMAN, VarType.OBJ)

        CombinationRuneData.entries.forEach {
            row(it.row) {
                columnRSCM(RUNE_OUTPUT, it.runeOutput)
                column(LEVEL, it.level)
                column(XP, it.xp)
                columnRSCM(RUNE_INPUT, it.runeInput)
                columnRSCM(TALISMAN, it.talisman)
            }
        }

    }



}