package org.rsmod.content.skills.thieving.chests

import org.rsmod.content.skills.thieving.LootTable
import org.rsmod.content.skills.thieving.loot

/**
 * A trapped chest that can be searched for traps and looted. The stocked [loc] turns into
 * [empty] for [respawnCycles] once looted.
 */
enum class ChestTarget(
    val label: String,
    val level: Int,
    val xp: Double,
    val respawnCycles: Int,
    val loot: LootTable,
    val loc: String,
    val empty: String,
) {
    TEN_COINS(
        label = "10 coin chest",
        level = 13,
        xp = 7.8,
        respawnCycles = 6,
        loot = loot { always("obj.coins", 10..10) },
        loc = "loc.trapchest1",
        empty = "loc.emptytrapchest",
    ),
    NATURE_RUNE(
        label = "nature rune chest",
        level = 28,
        xp = 25.0,
        respawnCycles = 13,
        loot =
            loot {
                always("obj.coins", 3..3)
                always("obj.naturerune")
            },
        loc = "loc.trapchest2",
        empty = "loc.emptytrapchest",
    ),
    FIFTY_COINS(
        label = "50 coin chest",
        level = 43,
        xp = 125.0,
        respawnCycles = 75,
        loot = loot { always("obj.coins", 50..50) },
        loc = "loc.trapchest3",
        empty = "loc.emptytrapchest",
    ),
    STEEL_ARROWTIPS(
        label = "steel arrowtips chest",
        level = 47,
        xp = 150.0,
        respawnCycles = 125,
        loot =
            loot {
                always("obj.coins", 20..20)
                always("obj.steel_arrowheads", 5..5)
            },
        loc = "loc.trapchest4",
        empty = "loc.emptytrapchest",
    ),
    BLOOD_RUNES(
        label = "blood rune chest",
        level = 59,
        xp = 250.0,
        respawnCycles = 200,
        loot =
            loot {
                always("obj.coins", 500..500)
                always("obj.bloodrune", 2..2)
            },
        loc = "loc.trapchest5",
        empty = "loc.emptytrapchest",
    ),
    ARDOUGNE_CASTLE(
        label = "Ardougne castle chest",
        level = 72,
        xp = 500.0,
        respawnCycles = 400,
        loot =
            loot {
                always("obj.coins", 1000..1000)
                always("obj.raw_shark")
            },
        loc = "loc.pickchest3",
        empty = "loc.emptypickchest",
    ),
    ROGUES_CASTLE(
        label = "Rogues' Castle chest",
        level = 84,
        xp = 701.7,
        respawnCycles = 34,
        loot =
            loot {
                always("obj.coins", 1000..1000)
                always("obj.dragonstone")
            },
        loc = "loc.wilderness_rogue_chest",
        empty = "loc.wilderness_rogue_chest_open",
    ),
}
