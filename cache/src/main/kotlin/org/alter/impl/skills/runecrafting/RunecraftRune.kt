package org.alter.impl.skills.runecrafting

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType

enum class Rune(
    val id: String,
    val essence: List<String>,
    val level: Int,
    val xp: Int,
    val dbId: String,
    val extract: String
) {
    AIR(
        id = "items.mindrune",
        essence = listOf("items.blankrune", "items.blankrune_high"),
        level = 1,
        xp = 5,
        dbId = "dbrows.runecrafting_rune_air",
        extract = "items.scar_extract_warped"
    ),
    MIND(
        id = "items.mindrune",
        essence = listOf("items.blankrune", "items.blankrune_high"),
        level = 2,
        xp = 5,
        dbId = "dbrows.runecrafting_rune_mind",
        extract = "items.scar_extract_warped"
    ),
    WATER(
        id = "items.waterrune",
        essence = listOf("items.blankrune", "items.blankrune_high"),
        level = 5,
        xp = 6,
        dbId = "dbrows.runecrafting_rune_water",
        extract = "items.scar_extract_warped"
    ),
    EARTH(
        id = "items.earthrune",
        essence = listOf("items.blankrune", "items.blankrune_high"),
        level = 9,
        xp = 6,
        dbId = "dbrows.runecrafting_rune_earth",
        extract = "items.scar_extract_warped"
    ),
    FIRE(
        id = "items.firerune",
        essence = listOf("items.blankrune", "items.blankrune_high"),
        level = 14,
        xp = 7,
        dbId = "dbrows.runecrafting_rune_fire",
        extract = "items.scar_extract_warped"
    ),
    BODY(
        id = "items.bodyrune",
        essence = listOf("items.blankrune", "items.blankrune_high"),
        level = 20,
        xp = 7,
        dbId = "dbrows.runecrafting_rune_body",
        extract = "items.scar_extract_warped"
    ),
    COSMIC(
        id = "items.cosmicrune",
        essence = listOf("items.blankrune_high"),
        level = 27,
        xp = 8,
        dbId = "dbrows.runecrafting_rune_cosmic",
        extract = "items.scar_extract_twisted"
    ),
    CHAOS(
        id = "items.chaosrune",
        essence = listOf("items.blankrune_high"),
        level = 35,
        xp = 8,
        dbId = "dbrows.runecrafting_rune_chaos",
        extract = "items.scar_extract_twisted"
    ),
    ASTRAL(
        id = "items.astralrune",
        essence = listOf("items.blankrune_high"),
        level = 40,
        xp = 9,
        dbId = "dbrows.runecrafting_rune_astral",
        extract = "items.scar_extract_mangled"
    ),
    NATURE(
        id = "items.naturerune",
        essence = listOf("items.blankrune_high"),
        level = 44,
        xp = 9,
        dbId = "dbrows.runecrafting_rune_nature",
        extract = "items.scar_extract_mangled"
    ),
    LAW(
        id = "items.lawrune",
        essence = listOf("items.blankrune_high"),
        level = 54,
        xp = 9,
        dbId = "dbrows.runecrafting_rune_law",
        extract = "items.scar_extract_mangled"
    ),
    DEATH(
        id = "items.deathrune",
        essence = listOf("items.blankrune_high"),
        level = 65,
        xp = 10,
        dbId = "dbrows.runecrafting_rune_death",
        extract = "items.scar_extract_mangled"
    ),
    BLOOD(
        id = "items.bloodrune",
        essence = listOf("items.blankrune_high"),
        level = 77,
        xp = 24,
        dbId = "dbrows.runecrafting_rune_blood",
        extract = "items.scar_extract_scarred"
    ),
    SOUL(
        id = "items.soulrune",
        essence = listOf("items.bigblankrune"),
        level = 90,
        xp = 30,
        dbId = "dbrows.runecrafting_rune_soul",
        extract = "items.scar_extract_scarred"
    ),
    WRATH(
        id = "items.wrathrune",
        essence = listOf("items.blankrune_high"),
        level = 95,
        xp = 8,
        dbId = "dbrows.runecrafting_rune_wrath",
        extract = "items.scar_extract_scarred"
    );

    companion object {
        val values = enumValues<Rune>()
    }
}

object RunecraftRune {

    val ITEM = 0
    val ESSENCE = 1
    val LEVEL = 2
    val XP = 3
    val EXTRACT = 4

    fun runecraftRune() = dbTable("tables.runecrafting_runes") {
        column("rune_output", ITEM, VarType.OBJ)
        column("valid_essences", ESSENCE, VarType.OBJ)
        column("xp", XP, VarType.INT)
        column("level", LEVEL, VarType.INT)
        column("extract", EXTRACT, VarType.OBJ)
        Rune.entries.forEach {
            row(it.dbId) {
                columnRSCM(ITEM, it.id)
                columnRSCM(ESSENCE, *it.essence.toTypedArray())
                column(LEVEL, it.level)
                column(XP, it.xp)
                columnRSCM(EXTRACT, it.extract)
            }
        }


    }

}