package dev.openrune.tables

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType
import dev.openrune.pack.columnCoord
import org.rsmod.map.CoordGrid

object InstanceSettingsTable {

    const val KEY = 0
    const val EXIT_COORD = 1
    const val FEE = 2
    const val MAX_PLAYERS = 3
    const val TIME_LIMIT_MINUTES = 4
    const val GRACE_MINUTES = 5
    const val BOSS_NPC = 6
    const val BOSS_NAME = 7
    const val RECOMMENDED_COMBAT = 8
    const val TEAM_SIZE = 9
    const val LOOT_MULTIPLIER = 10
    const val DESCRIPTION = 11
    const val ENTER_COORD = 12
    const val ENTER_OBJECT = 13
    const val EXIT_OBJECT = 14

    fun instanceSettings() = dbTable("dbtable.instance_settings", serverOnly = true) {
        column("key", KEY, VarType.STRING)
        column("exit_coord", EXIT_COORD, VarType.COORDGRID)
        column("fee", FEE, VarType.INT)
        column("max_players", MAX_PLAYERS, VarType.INT)
        column("time_limit_minutes", TIME_LIMIT_MINUTES, VarType.INT)
        column("grace_minutes", GRACE_MINUTES, VarType.INT)
        column("boss_npc", BOSS_NPC, VarType.NPC)
        column("boss_name", BOSS_NAME, VarType.STRING)
        column("recommended_combat", RECOMMENDED_COMBAT, VarType.INT)
        column("team_size", TEAM_SIZE, VarType.INT)
        column("loot_multiplier", LOOT_MULTIPLIER, VarType.STRING)
        column("description", DESCRIPTION, VarType.STRING)
        column("enter_coord", ENTER_COORD, VarType.COORDGRID)
        column("enter_object", ENTER_OBJECT, VarType.LOC)
        column("exit_object", EXIT_OBJECT, VarType.LOC)

        row("dbrow.instance_scurrius") {
            column(KEY, "scurrius")
            columnCoord(EXIT_COORD, CoordGrid(3281, 9870))
            columnCoord(ENTER_COORD, CoordGrid(3290, 9868))
            column(FEE, 0)
            column(MAX_PLAYERS, 20)
            column(TIME_LIMIT_MINUTES, 0)
            column(GRACE_MINUTES, 10)
            columnRSCM(BOSS_NPC, "npc.rat_boss_normal","npc.rat_boss_instance")
            column(BOSS_NAME, "Scurrius")
            column(RECOMMENDED_COMBAT, 60,90)
            column(TEAM_SIZE, 20)
            column(LOOT_MULTIPLIER, "x1.0")
            column(DESCRIPTION, "King of the rats.")
            columnRSCM(ENTER_OBJECT, "loc.rat_boss_entrance")
            columnRSCM(EXIT_OBJECT, "loc.rat_boss_exit")
        }

        row("dbrow.instance_kbd") {
            column(KEY, "kbd")
            columnCoord(EXIT_COORD, CoordGrid(0, 47, 160, 59, 14))
            columnCoord(ENTER_COORD, CoordGrid(0, 35, 73, 31, 9))
            column(FEE, 50000)
            column(MAX_PLAYERS, 5)
            column(TIME_LIMIT_MINUTES, 0)
            column(GRACE_MINUTES, 10)
            columnRSCM(BOSS_NPC, "npc.king_dragon")
            column(BOSS_NAME, "King Black Dragon")
            column(RECOMMENDED_COMBAT, 80,90)
            column(TEAM_SIZE, 1)
            column(LOOT_MULTIPLIER, "x1.0")
            column(DESCRIPTION, "King of the dragons.")
            columnRSCM(ENTER_OBJECT, "loc.dragonkinginlever")
            columnRSCM(EXIT_OBJECT, "loc.dragonkingoutlever")
        }

        row("dbrow.instance_graardor") {
            column(KEY, "graardor")
            columnCoord(EXIT_COORD, CoordGrid(2862, 5354, 2))
            columnCoord(ENTER_COORD, CoordGrid(2864, 5354, 2))
            column(FEE, 0)
            column(MAX_PLAYERS, 20)
            column(TIME_LIMIT_MINUTES, 0)
            column(GRACE_MINUTES, 10)
            columnRSCM(BOSS_NPC, "npc.godwars_bandos_avatar")
            column(BOSS_NAME, "General Graardor")
            column(RECOMMENDED_COMBAT, 80, 90)
            column(TEAM_SIZE, 20)
            column(LOOT_MULTIPLIER, "x1.0")
            column(DESCRIPTION, "Leader of Bandos' forces in the God Wars Dungeon.")
            columnRSCM(ENTER_OBJECT, "loc.godwars_dungeon_door_bandos")
            columnRSCM(EXIT_OBJECT, "loc.godwars_dungeon_bandos_altar01")
        }

        row("dbrow.instance_kreearra") {
            column(KEY, "kreearra")
            columnCoord(EXIT_COORD, CoordGrid(2839, 5294, 2))
            columnCoord(ENTER_COORD, CoordGrid(2839, 5295, 2))
            column(FEE, 0)
            column(MAX_PLAYERS, 20)
            column(TIME_LIMIT_MINUTES, 0)
            column(GRACE_MINUTES, 10)
            columnRSCM(BOSS_NPC, "npc.godwars_armadyl_avatar")
            column(BOSS_NAME, "Kree'arra")
            column(RECOMMENDED_COMBAT, 80, 90)
            column(TEAM_SIZE, 20)
            column(LOOT_MULTIPLIER, "x1.0")
            column(DESCRIPTION, "Armadyl's aerial commander in the God Wars Dungeon.")
            columnRSCM(ENTER_OBJECT, "loc.godwars_dungeon_door_armadyl")
            columnRSCM(EXIT_OBJECT, "loc.godwars_dungeon_armadyl_altar01")
        }

        row("dbrow.instance_zilyana") {
            column(KEY, "zilyana")
            columnCoord(EXIT_COORD, CoordGrid(2909, 5265, 0))
            columnCoord(ENTER_COORD, CoordGrid(2908, 5265, 0))
            column(FEE, 0)
            column(MAX_PLAYERS, 20)
            column(TIME_LIMIT_MINUTES, 0)
            column(GRACE_MINUTES, 10)
            columnRSCM(BOSS_NPC, "npc.godwars_saradomin_avatar")
            column(BOSS_NAME, "Commander Zilyana")
            column(RECOMMENDED_COMBAT, 80, 90)
            column(TEAM_SIZE, 20)
            column(LOOT_MULTIPLIER, "x1.0")
            column(DESCRIPTION, "Leader of Saradomin's forces in the God Wars Dungeon.")
            columnRSCM(ENTER_OBJECT, "loc.godwars_dungeon_door_saradomin")
            columnRSCM(EXIT_OBJECT, "loc.godwars_dungeon_saradomin_altar01")
        }

        row("dbrow.instance_kril") {
            column(KEY, "kril")
            columnCoord(EXIT_COORD, CoordGrid(2925, 5333, 2))
            columnCoord(ENTER_COORD, CoordGrid(2925, 5332, 2))
            column(FEE, 0)
            column(MAX_PLAYERS, 20)
            column(TIME_LIMIT_MINUTES, 0)
            column(GRACE_MINUTES, 10)
            columnRSCM(BOSS_NPC, "npc.godwars_zamorak_avatar")
            column(BOSS_NAME, "K'ril Tsutsaroth")
            column(RECOMMENDED_COMBAT, 80, 90)
            column(TEAM_SIZE, 20)
            column(LOOT_MULTIPLIER, "x1.0")
            column(DESCRIPTION, "Leader of Zamorak's forces in the God Wars Dungeon.")
            columnRSCM(ENTER_OBJECT, "loc.godwars_dungeon_door_zamorak")
            columnRSCM(EXIT_OBJECT, "loc.godwars_dungeon_zamorak_altar01")
        }

        row("dbrow.instance_cowboss") {
            column(KEY, "cowboss")
            columnCoord(EXIT_COORD, CoordGrid(3258, 3289, 0))
            columnCoord(ENTER_COORD, CoordGrid(0, 50, 51, 58, 28))
            column(FEE, 0)
            column(MAX_PLAYERS, 1)
            column(TIME_LIMIT_MINUTES, 0)
            column(GRACE_MINUTES, 5)
            columnRSCM(BOSS_NPC, "npc.cowboss")
            column(BOSS_NAME, "Brutus")
            column(RECOMMENDED_COMBAT, 30,30)
            column(TEAM_SIZE, 1)
            column(LOOT_MULTIPLIER, "x1.0")
            column(
                DESCRIPTION,
                "The prize bull of the Lumbridge cow field. Dodge his charge and slam.",
            )
            columnRSCM(
                ENTER_OBJECT,
                "loc.fencegate_l_cowboss_start",
                "loc.fencegate_r_cowboss_start",
            )
            columnRSCM(
                EXIT_OBJECT,
                "loc.fencegate_l_cowboss_exit",
                "loc.fencegate_r_cowboss_exit",
            )
        }

        row("dbrow.instance_vardorvis") {
            column(KEY, "vardorvis")
            columnCoord(EXIT_COORD, CoordGrid(1117, 3431, 0))
            columnCoord(ENTER_COORD, CoordGrid(1119, 3428, 0))
            column(FEE, 0)
            column(MAX_PLAYERS, 1)
            column(TIME_LIMIT_MINUTES, 0)
            column(GRACE_MINUTES, 10)
            columnRSCM(BOSS_NPC, "npc.vardorvis")
            column(BOSS_NAME, "Vardorvis")
            column(RECOMMENDED_COMBAT, 100, 126)
            column(TEAM_SIZE, 1)
            column(LOOT_MULTIPLIER, "x1.0")
            column(
                DESCRIPTION,
                "The axe-bound horror at the heart of the Stranglewood ritual site.",
            )
            columnRSCM(
                ENTER_OBJECT,
                "loc.dt2_stranglewood_boss_entry",
                "loc.dt2_stranglewood_boss_entry_op",
            )
            columnRSCM(EXIT_OBJECT, "loc.vardorvis_exit")
        }

    }
}
