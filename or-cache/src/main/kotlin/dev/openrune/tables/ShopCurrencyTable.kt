package dev.openrune.tables

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType

object ShopCurrencyTable {

    const val KEY = 0
    const val SINGULAR_NAME = 1
    const val PLURAL_NAME = 2
    const val OBJ = 3
    const val VARBIT = 4

    fun shopCurrencies() = dbTable("dbtable.shop_currency", serverOnly = true) {
        column("key", KEY, VarType.STRING)
        column("singular_name", SINGULAR_NAME, VarType.STRING)
        column("plural_name", PLURAL_NAME, VarType.STRING)
        column("obj", OBJ, VarType.OBJ)
        column("varbit", VARBIT, VarType.INT)

        row("dbrow.shop_currency_standard_gp") {
            column(KEY, "currency.standard_gp")
            column(SINGULAR_NAME, "coin")
            column(PLURAL_NAME, "coins")
            columnRSCM(OBJ, "obj.coins")
        }

        row("dbrow.shop_currency_stardust") {
            column(KEY, "currency.stardust")
            column(SINGULAR_NAME, "stardust")
            column(PLURAL_NAME, "stardust")
            columnRSCM(OBJ, "obj.star_dust")
        }

        // Emir's Arena reward points live in a varbit rather than an obj.
        row("dbrow.shop_currency_pvp_arena_points") {
            column(KEY, "currency.pvp_arena_points")
            column(SINGULAR_NAME, "PvP Arena reward point")
            column(PLURAL_NAME, "PvP Arena reward points")
            columnRSCM(VARBIT, "varbit.pvpa_points_currency")
        }
    }
}
