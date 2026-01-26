package org.alter.impl.skills

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType

object Cooking {

    const val COL_RAW = 0
    const val COL_COOKED = 1
    const val COL_BURNT = 2
    const val COL_LEVEL = 3
    const val COL_XP = 4
    const val COL_STOP_BURN_FIRE = 5
    const val COL_STOP_BURN_RANGE = 6

    fun recipes() = dbTable("tables.cooking_recipes") {

        column("raw", COL_RAW, VarType.OBJ)
        column("cooked", COL_COOKED, VarType.OBJ)
        column("burnt", COL_BURNT, VarType.OBJ)
        column("level", COL_LEVEL, VarType.INT)
        column("xp", COL_XP, VarType.INT)
        column("stop_burn_fire", COL_STOP_BURN_FIRE, VarType.INT)
        column("stop_burn_range", COL_STOP_BURN_RANGE, VarType.INT)

        // Basic fish & meat (initial skill implementation)
        row("dbrows.cooking_shrimps") {
            columnRSCM(COL_RAW, "items.raw_shrimp")
            columnRSCM(COL_COOKED, "items.shrimp")
            columnRSCM(COL_BURNT, "items.burnt_shrimp")
            column(COL_LEVEL, 1)
            column(COL_XP, 30)
            column(COL_STOP_BURN_FIRE, 34)
            column(COL_STOP_BURN_RANGE, 33)
        }

        row("dbrows.cooking_anchovies") {
            columnRSCM(COL_RAW, "items.raw_anchovies")
            columnRSCM(COL_COOKED, "items.anchovies")
            columnRSCM(COL_BURNT, "items.burntfish1")
            column(COL_LEVEL, 1)
            column(COL_XP, 30)
            column(COL_STOP_BURN_FIRE, 34)
            column(COL_STOP_BURN_RANGE, 33)
        }

        row("dbrows.cooking_sardine") {
            columnRSCM(COL_RAW, "items.raw_sardine")
            columnRSCM(COL_COOKED, "items.sardine")
            columnRSCM(COL_BURNT, "items.burntfish5")
            column(COL_LEVEL, 1)
            column(COL_XP, 40)
            column(COL_STOP_BURN_FIRE, 38)
            column(COL_STOP_BURN_RANGE, 37)
        }

        row("dbrows.cooking_herring") {
            columnRSCM(COL_RAW, "items.raw_herring")
            columnRSCM(COL_COOKED, "items.herring")
            columnRSCM(COL_BURNT, "items.burntfish3")
            column(COL_LEVEL, 5)
            column(COL_XP, 50)
            column(COL_STOP_BURN_FIRE, 41)
            column(COL_STOP_BURN_RANGE, 40)
        }

        row("dbrows.cooking_mackerel") {
            columnRSCM(COL_RAW, "items.raw_mackerel")
            columnRSCM(COL_COOKED, "items.mackerel")
            columnRSCM(COL_BURNT, "items.burntfish3")
            column(COL_LEVEL, 10)
            column(COL_XP, 60)
            column(COL_STOP_BURN_FIRE, 44)
            column(COL_STOP_BURN_RANGE, 43)
        }

        row("dbrows.cooking_trout") {
            columnRSCM(COL_RAW, "items.raw_trout")
            columnRSCM(COL_COOKED, "items.trout")
            columnRSCM(COL_BURNT, "items.burntfish2")
            column(COL_LEVEL, 15)
            column(COL_XP, 70)
            column(COL_STOP_BURN_FIRE, 50)
            column(COL_STOP_BURN_RANGE, 49)
        }

        row("dbrows.cooking_salmon") {
            columnRSCM(COL_RAW, "items.raw_salmon")
            columnRSCM(COL_COOKED, "items.salmon")
            columnRSCM(COL_BURNT, "items.burntfish2")
            column(COL_LEVEL, 25)
            column(COL_XP, 90)
            column(COL_STOP_BURN_FIRE, 58)
            column(COL_STOP_BURN_RANGE, 57)
        }

        row("dbrows.cooking_lobster") {
            columnRSCM(COL_RAW, "items.raw_lobster")
            columnRSCM(COL_COOKED, "items.lobster")
            columnRSCM(COL_BURNT, "items.burnt_lobster")
            column(COL_LEVEL, 40)
            column(COL_XP, 120)
            column(COL_STOP_BURN_FIRE, 74)
            column(COL_STOP_BURN_RANGE, 64)
        }

        row("dbrows.cooking_swordfish") {
            columnRSCM(COL_RAW, "items.raw_swordfish")
            columnRSCM(COL_COOKED, "items.swordfish")
            columnRSCM(COL_BURNT, "items.burnt_swordfish")
            column(COL_LEVEL, 45)
            column(COL_XP, 140)
            column(COL_STOP_BURN_FIRE, 86)
            column(COL_STOP_BURN_RANGE, 80)
        }

        row("dbrows.cooking_shark") {
            columnRSCM(COL_RAW, "items.raw_shark")
            columnRSCM(COL_COOKED, "items.shark")
            columnRSCM(COL_BURNT, "items.burnt_shark")
            column(COL_LEVEL, 80)
            column(COL_XP, 210)
            column(COL_STOP_BURN_FIRE, 99)
            column(COL_STOP_BURN_RANGE, 94)
        }

        row("dbrows.cooking_beef") {
            columnRSCM(COL_RAW, "items.raw_beef")
            columnRSCM(COL_COOKED, "items.cooked_meat")
            columnRSCM(COL_BURNT, "items.burnt_meat")
            column(COL_LEVEL, 1)
            column(COL_XP, 30)
            column(COL_STOP_BURN_FIRE, 33)
            column(COL_STOP_BURN_RANGE, 32)
        }

        row("dbrows.cooking_chicken") {
            columnRSCM(COL_RAW, "items.raw_chicken")
            columnRSCM(COL_COOKED, "items.cooked_chicken")
            columnRSCM(COL_BURNT, "items.burnt_chicken")
            column(COL_LEVEL, 1)
            column(COL_XP, 30)
            column(COL_STOP_BURN_FIRE, 33)
            column(COL_STOP_BURN_RANGE, 32)
        }
    }
}
