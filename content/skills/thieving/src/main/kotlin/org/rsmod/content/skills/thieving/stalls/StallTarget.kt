package org.rsmod.content.skills.thieving.stalls

import org.rsmod.content.skills.thieving.LootTable
import org.rsmod.content.skills.thieving.loot

/**
 * A market stall that can be stolen from. [locs] maps every stocked stall loc to the empty stall
 * it turns into for [respawnCycles]; a `null` empty loc means the stall simply disappears until
 * it restocks.
 */
enum class StallTarget(
    val label: String,
    val level: Int,
    val xp: Double,
    val respawnCycles: Int,
    val petBase: Int,
    val loot: LootTable,
    val locs: Map<String, String?>,
) {
    VEGETABLE(
        label = "vegetable stall",
        level = 2,
        xp = 10.0,
        respawnCycles = 2,
        petBase = 206_777,
        loot =
            loot {
                item(3, "obj.onion")
                item(3, "obj.cabbage")
                item(3, "obj.potato")
                item(3, "obj.tomato")
                item(1, "obj.garlic")
            },
        locs =
            mapOf(
                "loc.misc_veg_market" to "loc.viking_market",
                "loc.etc_veg_market" to "loc.viking_market",
                "loc.port_roberts_market_stall_veg" to "loc.port_roberts_market_stall",
            ),
    ),
    BAKERY(
        label = "baker's stall",
        level = 5,
        xp = 16.0,
        respawnCycles = 4,
        petBase = 124_066,
        loot =
            loot {
                item(6, "obj.cake")
                item(3, "obj.bread")
                item(1, "obj.chocolate_slice")
            },
        locs =
            mapOf(
                "loc.cakethiefstall" to "loc.market",
                "loc.hos_stall_bread" to "loc.hos_stall_empty",
                "loc.dwarf_market_bakery" to "loc.dwarf_market_empty_stall",
                "loc.fortis_market_stall_bakers" to "loc.fortis_market_stall",
            ),
    ),
    TEA(
        label = "tea stall",
        level = 5,
        xp = 16.0,
        respawnCycles = 4,
        petBase = 68_926,
        loot = loot { always("obj.cup_of_tea") },
        locs =
            mapOf(
                "loc.tea_stall" to "loc.market",
                "loc.icthalarins_tea_stall" to "loc.icthalarins_market",
                "loc.contact_tea_stall" to "loc.contact_market",
            ),
    ),
    MONKEY_FOOD(
        label = "food stall",
        level = 5,
        xp = 16.0,
        respawnCycles = 6,
        petBase = 47_718,
        loot = loot { always("obj.banana") },
        locs = mapOf("loc.mm_stall_food" to null),
    ),
    CRAFTING(
        label = "crafting stall",
        level = 5,
        xp = 20.0,
        respawnCycles = 8,
        petBase = 47_718,
        loot =
            loot {
                item(4, "obj.chisel")
                item(2, "obj.ring_mould")
                item(2, "obj.necklace_mould")
                item(1, "obj.amulet_mould")
                item(1, "obj.gold_bar")
            },
        locs =
            mapOf(
                "loc.mm_stall_crafting" to null,
                "loc.dwarf_market_crafting" to "loc.dwarf_market_empty_stall",
            ),
    ),
    MONKEY_GENERAL(
        label = "general stall",
        level = 5,
        xp = 25.0,
        respawnCycles = 8,
        petBase = 47_718,
        loot =
            loot {
                item(1, "obj.pot_empty")
                item(1, "obj.hammer")
                item(1, "obj.tinderbox")
            },
        locs = mapOf("loc.mm_stall_general" to null),
    ),
    ROCK_CAKE(
        label = "counter",
        level = 15,
        xp = 6.4,
        respawnCycles = 12,
        petBase = 47_718,
        loot = loot { always("obj.rockcake") },
        locs = mapOf("loc.rockcounter_withcakes" to "loc.empty_rock_cake_counter"),
    ),
    SILK(
        label = "silk stall",
        level = 20,
        xp = 24.0,
        respawnCycles = 8,
        petBase = 68_926,
        loot = loot { always("obj.silk") },
        locs =
            mapOf(
                "loc.silkthiefstall" to "loc.market",
                "loc.prif_marketstall_silk" to "loc.prif_marketstall_empty",
                "loc.fortis_market_stall_silk" to "loc.fortis_market_stall",
                "loc.port_roberts_market_stall_silk" to "loc.port_roberts_market_stall",
            ),
    ),
    FRUIT(
        label = "fruit stall",
        level = 25,
        xp = 28.5,
        respawnCycles = 4,
        petBase = 124_066,
        loot =
            loot {
                item(6, "obj.cooking_apple")
                item(6, "obj.banana")
                item(4, "obj.strawberry")
                item(2, "obj.jangerberries")
                item(2, "obj.lemon")
                item(2, "obj.redberries")
                item(2, "obj.lime")
                item(1, "obj.pineapple")
                item(1, "obj.papaya")
                item(1, "obj.golovanova_top")
            },
        locs = mapOf("loc.hos_fruit_stall_02" to null),
    ),
    SEED(
        label = "seed stall",
        level = 27,
        xp = 10.0,
        respawnCycles = 4,
        petBase = 36_490,
        loot =
            loot {
                item(40, "obj.potato_seed", 1..4)
                item(30, "obj.onion_seed", 1..3)
                item(25, "obj.cabbage_seed", 1..3)
                item(20, "obj.tomato_seed", 1..2)
                item(10, "obj.sweetcorn_seed")
                item(6, "obj.strawberry_seed")
                item(3, "obj.watermelon_seed")
                item(8, "obj.marigold_seed")
                item(6, "obj.rosemary_seed")
                item(6, "obj.nasturtium_seed")
                item(4, "obj.woad_seed")
                item(3, "obj.limpwurt_seed")
                item(5, "obj.barley_seed", 1..4)
                item(4, "obj.hammerstone_hop_seed", 1..4)
                item(3, "obj.asgarnian_hop_seed", 1..3)
                item(3, "obj.jute_seed", 1..3)
                item(2, "obj.yanillian_hop_seed", 1..2)
                item(1, "obj.krandorian_hop_seed")
                item(1, "obj.guam_seed")
                item(1, "obj.marrentill_seed")
            },
        locs = mapOf("loc.seed_stall" to "loc.market"),
    ),
    FUR(
        label = "fur stall",
        level = 35,
        xp = 45.0,
        respawnCycles = 12,
        petBase = 36_490,
        loot =
            loot {
                item(9, "obj.fur")
                item(1, "obj.grey_wolf_fur")
            },
        locs =
            mapOf(
                "loc.furthiefstall" to "loc.market",
                "loc.viking_fur_market" to "loc.viking_market",
                "loc.fortis_market_stall_fur" to "loc.fortis_market_stall",
                "loc.port_roberts_market_stall_fur" to "loc.port_roberts_market_stall",
            ),
    ),
    FISH(
        label = "fish stall",
        level = 42,
        xp = 42.0,
        respawnCycles = 12,
        petBase = 36_490,
        loot =
            loot {
                item(4, "obj.raw_salmon")
                item(3, "obj.raw_tuna")
                item(1, "obj.raw_lobster")
            },
        locs =
            mapOf(
                "loc.viking_fish_market" to "loc.viking_market",
                "loc.misc_fish_market" to "loc.viking_market",
                "loc.etc_fish_market" to "loc.viking_market",
                "loc.fish_stall_warrens" to null,
                "loc.port_roberts_market_stall_fish" to "loc.port_roberts_market_stall",
            ),
    ),
    CROSSBOW(
        label = "crossbow stall",
        level = 49,
        xp = 52.0,
        respawnCycles = 8,
        petBase = 36_490,
        loot =
            loot {
                item(4, "obj.bolt", 1..12)
                item(2, "obj.xbows_crossbow_bolts_mithril", 1..3)
                item(3, "obj.xbows_crossbow_limbs_bronze")
                item(3, "obj.xbows_crossbow_stock_wood")
            },
        locs = mapOf("loc.xbows_dwarf_market" to "loc.dwarf_market_empty_stall"),
    ),
    SILVER(
        label = "silver stall",
        level = 50,
        xp = 205.0,
        respawnCycles = 32,
        petBase = 36_490,
        loot =
            loot {
                item(5, "obj.silver_ore")
                item(2, "obj.silver_bar")
                item(1, "obj.tiara")
            },
        locs =
            mapOf(
                "loc.silverthiefstall" to "loc.market",
                "loc.dwarf_market_silver" to "loc.dwarf_market_empty_stall",
                "loc.prif_marketstall_silver" to "loc.prif_marketstall_empty",
                "loc.port_roberts_market_stall_silver" to "loc.port_roberts_market_stall",
            ),
    ),
    SPICE(
        label = "spice stall",
        level = 65,
        xp = 92.0,
        respawnCycles = 10,
        petBase = 36_490,
        loot = loot { always("obj.spicespot") },
        locs =
            mapOf(
                "loc.spicethiefstall" to "loc.market",
                "loc.prif_marketstall_spice" to "loc.prif_marketstall_empty",
                "loc.fortis_market_stall_spice" to "loc.fortis_market_stall",
                "loc.port_roberts_market_stall_spice" to "loc.port_roberts_market_stall",
            ),
    ),
    MAGIC(
        label = "magic stall",
        level = 65,
        xp = 90.0,
        respawnCycles = 12,
        petBase = 36_490,
        loot =
            loot {
                item(10, "obj.airrune", 1..10)
                item(10, "obj.earthrune", 1..10)
                item(10, "obj.firerune", 1..10)
                item(4, "obj.naturerune", 1..3)
                item(2, "obj.lawrune", 1..2)
            },
        locs = mapOf("loc.mm_stall_magic" to null),
    ),
    SCIMITAR(
        label = "scimitar stall",
        level = 65,
        xp = 210.0,
        respawnCycles = 32,
        petBase = 36_490,
        loot =
            loot {
                item(6, "obj.iron_scimitar")
                item(3, "obj.steel_scimitar")
                item(2, "obj.mithril_scimitar")
                item(1, "obj.adamant_scimitar")
            },
        locs = mapOf("loc.mm_stall_scimitar" to null),
    ),
    GEM(
        label = "gem stall",
        level = 75,
        xp = 408.0,
        respawnCycles = 100,
        petBase = 36_490,
        loot =
            loot {
                item(8, "obj.uncut_sapphire")
                item(4, "obj.uncut_emerald")
                item(2, "obj.uncut_ruby")
                item(1, "obj.uncut_diamond")
            },
        locs =
            mapOf(
                "loc.gemthiefstall" to "loc.market",
                "loc.dwarf_market_gems" to "loc.dwarf_market_empty_stall",
                "loc.prif_marketstall_gem" to "loc.prif_marketstall_empty",
                "loc.fortis_market_stall_gems" to "loc.fortis_market_stall",
                "loc.port_roberts_market_stall_gems" to "loc.port_roberts_market_stall",
            ),
    ),
    TZHAAR_GEM(
        label = "gem stall",
        level = 75,
        xp = 160.0,
        respawnCycles = 100,
        petBase = 36_490,
        loot =
            loot {
                item(8, "obj.uncut_sapphire")
                item(4, "obj.uncut_emerald")
                item(2, "obj.uncut_ruby")
                item(1, "obj.uncut_diamond")
            },
        locs = mapOf("loc.tzhaar_shopcounter_gem" to "loc.tzhaar_shopcounter_empty"),
    ),
    ORE(
        label = "ore stall",
        level = 82,
        xp = 350.0,
        respawnCycles = 50,
        petBase = 36_490,
        loot =
            loot {
                item(8, "obj.coal")
                item(8, "obj.iron_ore")
                item(4, "obj.silver_ore")
                item(4, "obj.gold_ore")
                item(2, "obj.mithril_ore")
                item(1, "obj.adamantite_ore")
                item(1, "obj.runite_ore")
            },
        locs =
            mapOf(
                "loc.tzhaar_shopcounter_ore" to "loc.tzhaar_shopcounter_empty",
                "loc.port_roberts_market_stall_ore" to "loc.port_roberts_market_stall",
            ),
    ),
    CANNONBALL(
        label = "cannonball stall",
        level = 87,
        xp = 223.0,
        respawnCycles = 4,
        petBase = 36_490,
        loot =
            loot {
                item(8, "obj.bronze_cannonball", 1..4)
                item(6, "obj.iron_cannonball", 1..4)
                item(4, "obj.mcannonball", 1..4)
                item(2, "obj.mithril_cannonball", 1..3)
                item(1, "obj.adamant_cannonball", 1..2)
                item(1, "obj.rune_cannonball", 1..2)
            },
        locs = mapOf("loc.port_roberts_market_stall_cball" to "loc.port_roberts_market_stall"),
    ),
}
