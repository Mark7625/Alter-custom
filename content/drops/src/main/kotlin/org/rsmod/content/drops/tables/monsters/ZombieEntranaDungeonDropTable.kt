package org.rsmod.content.drops.tables.monsters

import dtx.rs.RSDropTable
import dtx.rs.npcs
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.RegisterDropTable
import org.rsmod.api.droptable.ringNothing
import org.rsmod.api.droptable.rsPlayerGuaranteedTable
import org.rsmod.api.droptable.rsPlayerTertiaryTable
import org.rsmod.api.droptable.rsPlayerWeightedTable
import org.rsmod.content.drops.isOnQuest
import org.rsmod.content.drops.tables.shared.SharedDropTables
import org.rsmod.game.entity.Player

/**
 * The level 25 zombies in the Entrana Dungeon. Their bronze axe is how Lost City players, who
 * cannot bring one to the island, get to chop the Dramen tree.
 */
@field:RegisterDropTable
@JvmField
public val zombieEntranaDungeonDropTable: RSDropTable<Player, DropRollItem> = RSDropTable(
    tableIdentifier = "Zombie (Entrana Dungeon) Drops",
    npcs = npcs("npc.zombie_entranan", "npc.zombie_entranan2", "npc.zombie_entranan3", "npc.zombie_entranan4", "npc.zombie_entranan5"),
    guaranteed = rsPlayerGuaranteedTable {
        "obj.bones" count 1
    },
    mainTable = rsPlayerWeightedTable(total = 128) {
        name("Zombie (Entrana Dungeon) Drops")
        50 weight "obj.bronze_axe" count 1
        4 weight "obj.bronze_med_helm" count 1
        1 weight "obj.bronze_longsword" count 1
        1 weight "obj.iron_axe" count 1
        4 weight "obj.airrune" count 13
        4 weight "obj.bronze_arrow" count 8
        1 weight "obj.naturerune" count 6
        46 weight "obj.fishing_bait" count 5
        8 weight ringNothing()
        3 weight "obj.coins" count 18
        2 weight "obj.coins" count 28

        4 weight SharedDropTables.herb
    },
    tertiaries = rsPlayerTertiaryTable {
        1 outOf 4 weight "obj.rag_zombie_bone" count 1 condition {
            player -> player.isOnQuest("quest_ragandboneman2")
        }
        1 outOf 5000 weight "obj.champions_challenge_zombie" count 1
    },
)
