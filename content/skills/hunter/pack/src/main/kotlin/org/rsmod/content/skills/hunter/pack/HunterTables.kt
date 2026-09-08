package org.rsmod.content.skills.hunter.pack

import dev.openrune.definition.dbtables.DBTable
import dev.openrune.definition.dbtables.DBTableBuilder
import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType

/**
 * The hunter creature dbtables. Every `npc` and `obj` is a cache symbol confirmed against the
 * cache, never a wiki name transcribed directly. XP is stored x10 so fractional wiki values
 * survive an int column. See docs/hunter.md.
 *
 * Row order is load-bearing: a sprung trap persists its creature as an index into the combined
 * creature list, read back sorted by dbrow id - a new technique's rows must sort after every row
 * already shipped, never between them.
 */
object HunterTables {
    // Column ids must form a dense 0..n-1 set per table: the encoder writes columns sorted by id
    // without the id itself, so a gap silently shifts every later column and drops the last, with
    // no pack-time diagnostic (docs/hunter.md). Ids 0-7 are shared; per-technique columns start
    // at 8, nested per table so one table's column cannot be typed into another's builder.
    const val COL_NPC = 0
    const val COL_LEVEL = 1
    const val COL_XP = 2
    const val COL_SUCCESS_LOW = 3
    const val COL_SUCCESS_HIGH = 4
    const val COL_CAUGHT_ITEMS = 5
    const val COL_CAUGHT_MIN = 6
    const val COL_CAUGHT_MAX = 7

    /**
     * The loc-state name suffix is authored data, never derived from the npc symbol - not every
     * creature's npc and loc names share a derivable stem. See docs/hunter.md.
     */
    private object LocKeyed {
        const val COL_LOC_KEY = 8
    }

    /** Columns 0-7, shared verbatim by every creature table. */
    private fun DBTableBuilder.creatureColumns() {
        column("npc", COL_NPC, VarType.NPC)
        column("level", COL_LEVEL, VarType.INT)
        // Stored x10.
        column("xp", COL_XP, VarType.INT)
        column("success_low", COL_SUCCESS_LOW, VarType.INT)
        column("success_high", COL_SUCCESS_HIGH, VarType.INT)
        column("caught_items", COL_CAUGHT_ITEMS, VarType.OBJ)
        column("caught_min", COL_CAUGHT_MIN, VarType.INT)
        column("caught_max", COL_CAUGHT_MAX, VarType.INT)
    }

    /**
     * Each pair was fit against the creature's charted per-level success curve and verified to
     * reproduce every non-capped point exactly - see docs/hunter.md. A catch awards bones, meat
     * and feathers in one go; only the feather count is rolled.
     */
    fun snareCreatures(): DBTable =
        dbTable("dbtable.hunter_snare_creatures", serverOnly = true) {
            creatureColumns()
            column("loc_key", LocKeyed.COL_LOC_KEY, VarType.STRING)

            row("dbrow.hunter_jungle_bird") {
                columnRSCM(COL_NPC, "npc.hunting_bird_jungle")
                column(COL_LEVEL, 1)
                column(COL_XP, 340)
                column(COL_SUCCESS_LOW, 100)
                column(COL_SUCCESS_HIGH, 420)
                columnRSCM(
                    COL_CAUGHT_ITEMS,
                    "obj.bones",
                    "obj.spit_raw_bird_meat",
                    "obj.hunting_jungle_feather",
                )
                column(COL_CAUGHT_MIN, 1, 1, 5)
                column(COL_CAUGHT_MAX, 1, 1, 10)
                column(LocKeyed.COL_LOC_KEY, "jungle")
            }

            row("dbrow.hunter_desert_bird") {
                columnRSCM(COL_NPC, "npc.hunting_bird_desert")
                column(COL_LEVEL, 5)
                column(COL_XP, 470)
                column(COL_SUCCESS_LOW, 92)
                column(COL_SUCCESS_HIGH, 400)
                columnRSCM(
                    COL_CAUGHT_ITEMS,
                    "obj.bones",
                    "obj.spit_raw_bird_meat",
                    "obj.hunting_desert_feather",
                )
                column(COL_CAUGHT_MIN, 1, 1, 5)
                column(COL_CAUGHT_MAX, 1, 1, 10)
                column(LocKeyed.COL_LOC_KEY, "desert")
            }

            row("dbrow.hunter_woodland_bird") {
                columnRSCM(COL_NPC, "npc.hunting_bird_woodland")
                column(COL_LEVEL, 9)
                column(COL_XP, 612)
                column(COL_SUCCESS_LOW, 85)
                column(COL_SUCCESS_HIGH, 390)
                columnRSCM(
                    COL_CAUGHT_ITEMS,
                    "obj.bones",
                    "obj.spit_raw_bird_meat",
                    "obj.hunting_woodland_feather",
                )
                column(COL_CAUGHT_MIN, 1, 1, 5)
                column(COL_CAUGHT_MAX, 1, 1, 10)
                column(LocKeyed.COL_LOC_KEY, "woodland")
            }

            row("dbrow.hunter_polar_bird") {
                columnRSCM(COL_NPC, "npc.hunting_bird_polar")
                column(COL_LEVEL, 11)
                // The infobox states 64.5 xp, the parent summary table 64.6; the infobox ships.
                column(COL_XP, 645)
                column(COL_SUCCESS_LOW, 82)
                column(COL_SUCCESS_HIGH, 380)
                columnRSCM(
                    COL_CAUGHT_ITEMS,
                    "obj.bones",
                    "obj.spit_raw_bird_meat",
                    "obj.hunting_polar_feather",
                )
                column(COL_CAUGHT_MIN, 1, 1, 5)
                column(COL_CAUGHT_MAX, 1, 1, 10)
                column(LocKeyed.COL_LOC_KEY, "polar")
            }
        }

    /**
     * All three chinchompas state their success formula directly on the wiki, so those pairs are
     * read off rather than fit. No `bait` column: nothing would read it (docs/hunter.md).
     */
    fun boxCreatures(): DBTable =
        dbTable("dbtable.hunter_box_creatures", serverOnly = true) {
            creatureColumns()
            column("loc_key", LocKeyed.COL_LOC_KEY, VarType.STRING)

            row("dbrow.hunter_chinchompa") {
                columnRSCM(COL_NPC, "npc.hunting_chinchompa")
                column(COL_LEVEL, 53)
                column(COL_XP, 1984)
                column(COL_SUCCESS_LOW, 6)
                column(COL_SUCCESS_HIGH, 268)
                columnRSCM(COL_CAUGHT_ITEMS, "obj.chinchompa_captured")
                column(COL_CAUGHT_MIN, 1)
                column(COL_CAUGHT_MAX, 1)
                column(LocKeyed.COL_LOC_KEY, "chinchompa")
            }

            row("dbrow.hunter_carnivorous_chinchompa") {
                columnRSCM(COL_NPC, "npc.hunting_chinchompa_big")
                column(COL_LEVEL, 63)
                column(COL_XP, 2650)
                // "Carnivorous and Black Chinchompas have the same catch rate" - both wiki pages.
                column(COL_SUCCESS_LOW, -78)
                column(COL_SUCCESS_HIGH, 228)
                columnRSCM(COL_CAUGHT_ITEMS, "obj.chinchompa_big_captured")
                column(COL_CAUGHT_MIN, 1)
                column(COL_CAUGHT_MAX, 1)
                column(LocKeyed.COL_LOC_KEY, "chinchompa_big")
            }

            row("dbrow.hunter_black_chinchompa") {
                columnRSCM(COL_NPC, "npc.hunting_chinchompa_black")
                column(COL_LEVEL, 73)
                column(COL_XP, 3150)
                column(COL_SUCCESS_LOW, -78)
                column(COL_SUCCESS_HIGH, 228)
                columnRSCM(COL_CAUGHT_ITEMS, "obj.chinchompa_black")
                column(COL_CAUGHT_MIN, 1)
                column(COL_CAUGHT_MAX, 1)
                column(LocKeyed.COL_LOC_KEY, "chinchompa_black")
            }
        }
}
