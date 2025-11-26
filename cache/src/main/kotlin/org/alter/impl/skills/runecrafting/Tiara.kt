package org.alter.impl.skills.runecrafting

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType

object Tiara {

    const val ITEM = 0
    const val ALTER = 1
    const val XP = 2

    fun tiara() = dbTable("tables.runecrafting_tiara") {
        column("item", ITEM, VarType.OBJ)
        column("alter", ALTER, VarType.LOC)
        column("xp", XP, VarType.INT)

        row("dbrows.runecrafting_tiara_air") {
            columnRSCM(ITEM, "items.tiara_air")
            columnRSCM(ALTER, "objects.air_altar")
            column(XP, 25)
        }

        row("dbrows.runecrafting_tiara_mind") {
            columnRSCM(ITEM, "items.tiara_mind")
            columnRSCM(ALTER, "objects.mind_altar")
            column(XP, 27)
        }

        row("dbrows.runecrafting_tiara_water") {
            columnRSCM(ITEM, "items.tiara_water")
            columnRSCM(ALTER, "objects.water_altar")
            column(XP, 30)
        }

        row("dbrows.runecrafting_tiara_earth") {
            columnRSCM(ITEM, "items.tiara_earth")
            columnRSCM(ALTER, "objects.earth_altar")
            column(XP, 32)
        }

        row("dbrows.runecrafting_tiara_fire") {
            columnRSCM(ITEM, "items.tiara_fire")
            columnRSCM(ALTER, "objects.fire_altar")
            column(XP, 35)
        }

        row("dbrows.runecrafting_tiara_body") {
            columnRSCM(ITEM, "items.tiara_body")
            columnRSCM(ALTER, "objects.body_altar")
            column(XP, 37)
        }

        row("dbrows.runecrafting_tiara_cosmic") {
            columnRSCM(ITEM, "items.tiara_cosmic")
            columnRSCM(ALTER, "objects.cosmic_altar")
            column(XP, 40)
        }

        row("dbrows.runecrafting_tiara_chaos") {
            columnRSCM(ITEM, "items.tiara_chaos")
            columnRSCM(ALTER, "objects.chaos_altar")
            column(XP, 42)
        }

        row("dbrows.runecrafting_tiara_nature") {
            columnRSCM(ITEM, "items.tiara_nature")
            columnRSCM(ALTER, "objects.nature_altar")
            column(XP, 45)
        }

        row("dbrows.runecrafting_tiara_law") {
            columnRSCM(ITEM, "items.tiara_law")
            columnRSCM(ALTER, "objects.law_altar")
            column(XP, 47)
        }

        row("dbrows.runecrafting_tiara_death") {
            columnRSCM(ITEM, "items.tiara_death")
            columnRSCM(ALTER, "objects.death_altar")
            column(XP, 50)
        }

        row("dbrows.runecrafting_tiara_wrath") {
            columnRSCM(ITEM, "items.tiara_wrath")
            columnRSCM(ALTER, "objects.wrath_altar")
            column(XP, 52)
        }

    }

}