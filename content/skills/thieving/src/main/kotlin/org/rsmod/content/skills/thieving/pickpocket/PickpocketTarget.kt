package org.rsmod.content.skills.thieving.pickpocket

import org.rsmod.content.skills.thieving.LootTable
import org.rsmod.content.skills.thieving.loot

/**
 * A pickpocket target as listed on the OSRS wiki Thieving page: the level and experience, the
 * stun damage dealt on failure, the success-rate curve, the Rocky base chance, and the loot.
 *
 * Success is rolled out of 256. At [level] the chance is [lowChance]; it climbs linearly and is
 * guaranteed (255) at [perfectLevel]. Targets that never become fail-proof use a [perfectLevel]
 * above 99 so the curve tops out around 240 at level 99.
 *
 * Coins come as the target's own coin pouch ([pouch]) worth [coins] when opened; a target with no
 * pouch in the cache hands the coins out directly.
 */
enum class PickpocketTarget(
    val level: Int,
    val xp: Double,
    val stunDamage: IntRange,
    val shout: String,
    val lowChance: Int,
    val perfectLevel: Int,
    val petBase: Int,
    val pouch: String?,
    val coins: IntRange,
    val loot: LootTable,
    val npcs: List<PickpocketNpc>,
) {
    MAN_WOMAN(
        level = 1,
        xp = 8.0,
        stunDamage = 1..1,
        shout = "What do you think you're doing?",
        lowChance = 180,
        perfectLevel = 85,
        petBase = 257_211,
        pouch = "obj.pickpocket_coin_pouch_citizen",
        coins = 3..3,
        loot = loot { always("obj.pickpocket_coin_pouch_citizen") },
        npcs = PickpocketNpcs.MAN_WOMAN,
    ),
    FARMER(
        level = 10,
        xp = 14.5,
        stunDamage = 1..1,
        shout = "Cor blimey mate, what are ye doing in me pockets?",
        lowChance = 170,
        perfectLevel = 90,
        petBase = 257_211,
        pouch = "obj.pickpocket_coin_pouch_farmer",
        coins = 9..9,
        loot =
            loot {
                item(8, "obj.pickpocket_coin_pouch_farmer")
                item(1, "obj.potato_seed")
            },
        npcs = PickpocketNpcs.FARMER,
    ),
    HAM_MEMBER(
        level = 15,
        xp = 22.2,
        stunDamage = 1..3,
        shout = "Hey! Get your hands off me!",
        lowChance = 160,
        perfectLevel = 91,
        petBase = 257_211,
        pouch = "obj.pickpocket_coin_pouch_ham",
        coins = 1..21,
        loot =
            loot {
                item(17, "obj.pickpocket_coin_pouch_ham")
                item(4, "obj.digsitebuttons")
                item(4, "obj.digsitearmour1")
                item(4, "obj.digsitesword")
                item(3, "obj.bronze_arrow", 1..13)
                item(3, "obj.bronze_axe")
                item(3, "obj.bronze_dagger")
                item(3, "obj.bronze_pickaxe")
                item(3, "obj.iron_axe")
                item(3, "obj.iron_dagger")
                item(3, "obj.iron_pickaxe")
                item(3, "obj.leather_armour")
                item(2, "obj.steel_arrow", 1..13)
                item(2, "obj.steel_axe")
                item(2, "obj.steel_dagger")
                item(2, "obj.steel_pickaxe")
                item(3, "obj.feather", 1..7)
                item(3, "obj.thread", 1..10)
                item(3, "obj.logs")
                item(3, "obj.cow_hide")
                item(2, "obj.coal")
                item(2, "obj.iron_ore")
                item(2, "obj.uncut_jade")
                item(2, "obj.uncut_opal")
                item(2, "obj.knife")
                item(2, "obj.needle")
                item(2, "obj.raw_anchovies")
                item(2, "obj.raw_chicken")
                item(2, "obj.tinderbox")
                item(1, "obj.ham_boots")
                item(1, "obj.ham_cloak")
                item(1, "obj.ham_gloves")
                item(1, "obj.ham_hood")
                item(1, "obj.ham_badge")
                item(1, "obj.ham_robe")
                item(1, "obj.ham_shirt")
                item(1, "obj.unidentified_guam")
                item(1, "obj.unidentified_marentill")
            },
        npcs = PickpocketNpcs.HAM_MEMBER,
    ),
    WARRIOR(
        level = 25,
        xp = 26.0,
        stunDamage = 2..2,
        shout = "What do you think you're doing?",
        lowChance = 150,
        perfectLevel = 93,
        petBase = 257_211,
        pouch = "obj.pickpocket_coin_pouch_warrior",
        coins = 18..18,
        loot = loot { always("obj.pickpocket_coin_pouch_warrior") },
        npcs = PickpocketNpcs.WARRIOR,
    ),
    VILLAGER(
        level = 30,
        xp = 8.0,
        stunDamage = 2..2,
        shout = "Get your hands out of my pockets!",
        lowChance = 150,
        perfectLevel = 120,
        petBase = 257_211,
        pouch = null,
        coins = 1..8,
        loot = loot { always("obj.coins", 1..8) },
        npcs = PickpocketNpcs.VILLAGER,
    ),
    ROGUE(
        level = 32,
        xp = 36.5,
        stunDamage = 2..2,
        shout = "Hey! Nobody steals from a rogue!",
        lowChance = 150,
        perfectLevel = 94,
        petBase = 257_211,
        pouch = "obj.pickpocket_coin_pouch_rogue",
        coins = 25..40,
        loot =
            loot {
                item(60, "obj.pickpocket_coin_pouch_rogue")
                item(10, "obj.lockpick")
                item(10, "obj.airrune", 8..8)
                item(10, "obj.jug_wine")
                item(5, "obj.iron_dagger_p")
            },
        npcs = PickpocketNpcs.ROGUE,
    ),
    CAVE_GOBLIN(
        level = 36,
        xp = 40.0,
        stunDamage = 1..1,
        shout = "Oi! Get away from me!",
        lowChance = 150,
        perfectLevel = 120,
        petBase = 257_211,
        pouch = "obj.pickpocket_coin_pouch_cavegoblin",
        coins = 10..50,
        loot =
            loot {
                item(7, "obj.pickpocket_coin_pouch_cavegoblin")
                item(1, "obj.dorgesh_bat_shish")
                item(1, "obj.dorgesh_crispy_froglegs")
                item(1, "obj.dorgesh_wall_beast_fingers")
                item(1, "obj.dorgesh_frog_burger")
                item(1, "obj.dorgesh_frog_spawn_gumbo")
                item(1, "obj.dorgesh_green_gloop_soup")
                item(1, "obj.bullseye_lantern_unlit")
                item(1, "obj.dorgesh_wire", 1..2)
                item(1, "obj.iron_ore", 1..4)
                item(1, "obj.oil_lantern_unlit")
                item(1, "obj.swamp_tar")
                item(1, "obj.tinderbox")
                item(1, "obj.torch_unlit")
            },
        npcs = PickpocketNpcs.CAVE_GOBLIN,
    ),
    MASTER_FARMER(
        level = 38,
        xp = 43.0,
        stunDamage = 3..3,
        shout = "Cor blimey mate, what are ye doing in me pockets?",
        lowChance = 128,
        perfectLevel = 94,
        petBase = 257_211,
        pouch = null,
        coins = 0..0,
        loot =
            loot {
                item(1770, "obj.potato_seed", 1..4)
                item(1328, "obj.onion_seed", 1..3)
                item(694, "obj.cabbage_seed", 1..3)
                item(637, "obj.tomato_seed", 1..2)
                item(221, "obj.sweetcorn_seed", 1..2)
                item(111, "obj.strawberry_seed")
                item(53, "obj.watermelon_seed")
                item(38, "obj.snape_grass_seed")
                item(556, "obj.barley_seed", 1..12)
                item(556, "obj.hammerstone_hop_seed", 1..9)
                item(418, "obj.asgarnian_hop_seed", 1..6)
                item(415, "obj.jute_seed", 1..9)
                item(277, "obj.yanillian_hop_seed", 1..6)
                item(139, "obj.krandorian_hop_seed", 1..6)
                item(70, "obj.wildblood_hop_seed", 1..3)
                item(459, "obj.marigold_seed")
                item(304, "obj.nasturtium_seed")
                item(196, "obj.rosemary_seed")
                item(145, "obj.woad_seed")
                item(116, "obj.limpwurt_seed")
                item(388, "obj.redberry_bush_seed")
                item(272, "obj.cadavaberry_bush_seed")
                item(194, "obj.dwellberry_bush_seed")
                item(78, "obj.jangerberry_bush_seed")
                item(28, "obj.whiteberry_bush_seed")
                item(11, "obj.poisonivy_bush_seed")
                item(20, "obj.mushroom_seed")
                item(12, "obj.belladonna_seed")
                item(8, "obj.cactus_seed")
                item(5, "obj.seaweed_seed")
                item(4, "obj.potato_cactus_seed")
                item(160, "obj.guam_seed")
                item(105, "obj.marrentill_seed")
                item(71, "obj.tarromin_seed")
                item(49, "obj.harralander_seed")
                item(30, "obj.ranarr_seed")
                item(23, "obj.toadflax_seed")
                item(15, "obj.irit_seed")
                item(11, "obj.avantoe_seed")
                item(7, "obj.kwuarm_seed")
                item(4, "obj.snapdragon_seed")
                item(3, "obj.cadantine_seed")
                item(2, "obj.lantadyme_seed")
                item(1, "obj.dwarf_weed_seed")
                item(1, "obj.torstol_seed")
            },
        npcs = PickpocketNpcs.MASTER_FARMER,
    ),
    GUARD(
        level = 40,
        xp = 46.8,
        stunDamage = 2..2,
        shout = "Hey, what do you think you're doing?",
        lowChance = 128,
        perfectLevel = 95,
        petBase = 257_211,
        pouch = "obj.pickpocket_coin_pouch_guard",
        coins = 30..30,
        loot = loot { always("obj.pickpocket_coin_pouch_guard") },
        npcs = PickpocketNpcs.GUARD,
    ),
    FREMENNIK_CITIZEN(
        level = 45,
        xp = 65.0,
        stunDamage = 2..2,
        shout = "Outerlander thief! Get away from me!",
        lowChance = 128,
        perfectLevel = 120,
        petBase = 257_211,
        pouch = "obj.pickpocket_coin_pouch_fremennik",
        coins = 40..40,
        loot = loot { always("obj.pickpocket_coin_pouch_fremennik") },
        npcs = PickpocketNpcs.FREMENNIK_CITIZEN,
    ),
    BEARDED_BANDIT(
        level = 45,
        xp = 65.0,
        stunDamage = 5..5,
        shout = "I'll kill you for that!",
        lowChance = 128,
        perfectLevel = 120,
        petBase = 257_211,
        pouch = "obj.pickpocket_coin_pouch_bandit",
        coins = 40..40,
        loot = loot { always("obj.pickpocket_coin_pouch_bandit") },
        npcs = PickpocketNpcs.BEARDED_BANDIT,
    ),
    WEALTHY_CITIZEN(
        level = 50,
        xp = 96.0,
        stunDamage = 3..3,
        shout = "How dare you! Guards!",
        lowChance = 128,
        perfectLevel = 120,
        petBase = 257_211,
        pouch = "obj.pickpocket_coin_pouch_varlamore_wealthy",
        coins = 60..120,
        loot = loot { always("obj.pickpocket_coin_pouch_varlamore_wealthy") },
        npcs = PickpocketNpcs.WEALTHY_CITIZEN,
    ),
    DESERT_BANDIT(
        level = 53,
        xp = 79.4,
        stunDamage = 3..3,
        shout = "What do you think you're doing?",
        lowChance = 128,
        perfectLevel = 95,
        petBase = 257_211,
        pouch = "obj.pickpocket_coin_pouch_desertbandit",
        coins = 30..30,
        loot =
            loot {
                item(10, "obj.pickpocket_coin_pouch_desertbandit")
                item(1, "obj.3doseantipoison")
                item(1, "obj.lockpick")
            },
        npcs = PickpocketNpcs.DESERT_BANDIT,
    ),
    KNIGHT(
        level = 55,
        xp = 84.3,
        stunDamage = 3..3,
        shout = "What do you think you're doing?",
        lowChance = 128,
        perfectLevel = 95,
        petBase = 257_211,
        pouch = "obj.pickpocket_coin_pouch_knight",
        coins = 50..50,
        loot = loot { always("obj.pickpocket_coin_pouch_knight") },
        npcs = PickpocketNpcs.KNIGHT,
    ),
    POLLNIVNIAN_BANDIT(
        level = 55,
        xp = 84.3,
        stunDamage = 5..5,
        shout = "I'll kill you for that!",
        lowChance = 128,
        perfectLevel = 120,
        petBase = 257_211,
        pouch = "obj.pickpocket_coin_pouch_bandit2",
        coins = 50..50,
        loot = loot { always("obj.pickpocket_coin_pouch_bandit2") },
        npcs = PickpocketNpcs.POLLNIVNIAN_BANDIT,
    ),
    PIRATE(
        level = 60,
        xp = 72.0,
        stunDamage = 3..3,
        shout = "Arr! Keep yer hands off me booty!",
        lowChance = 128,
        perfectLevel = 120,
        petBase = 257_211,
        pouch = "obj.pickpocket_coin_pouch_pirate",
        coins = 4..55,
        loot =
            loot {
                item(61, "obj.pickpocket_coin_pouch_pirate")
                item(6, "obj.iron_dagger")
                item(4, "obj.bronze_scimitar")
                item(1, "obj.iron_platebody")
                item(10, "obj.xbows_crossbow_bolts_iron", 2..12)
                item(6, "obj.chaosrune", 2..2)
                item(5, "obj.naturerune", 2..2)
                item(5, "obj.bronze_arrow", 9..12)
                item(2, "obj.airrune", 10..10)
                item(2, "obj.earthrune", 9..9)
                item(2, "obj.firerune", 5..5)
                item(1, "obj.lawrune", 2..2)
                item(12, "obj.eye_patch")
                item(1, "obj.chefs_hat")
                item(1, "obj.iron_bar")
                item(1, "obj.uncut_sapphire")
            },
        npcs = PickpocketNpcs.PIRATE,
    ),
    WATCHMAN(
        level = 65,
        xp = 137.5,
        stunDamage = 3..3,
        shout = "Hey! Get your hands off me!",
        lowChance = 128,
        perfectLevel = 120,
        petBase = 134_625,
        pouch = "obj.pickpocket_coin_pouch_watchman",
        coins = 60..60,
        loot =
            loot {
                always("obj.pickpocket_coin_pouch_watchman")
                always("obj.bread")
            },
        npcs = PickpocketNpcs.WATCHMAN,
    ),
    MENAPHITE_THUG(
        level = 65,
        xp = 137.5,
        stunDamage = 5..5,
        shout = "I'll kill you for that!",
        lowChance = 128,
        perfectLevel = 120,
        petBase = 257_211,
        pouch = "obj.pickpocket_coin_pouch_menaphite",
        coins = 60..60,
        loot = loot { always("obj.pickpocket_coin_pouch_menaphite") },
        npcs = PickpocketNpcs.MENAPHITE_THUG,
    ),
    PALADIN(
        level = 70,
        xp = 131.8,
        stunDamage = 3..3,
        shout = "What do you think you're doing?",
        lowChance = 128,
        perfectLevel = 120,
        petBase = 127_056,
        pouch = "obj.pickpocket_coin_pouch_paladin",
        coins = 80..80,
        loot =
            loot {
                always("obj.pickpocket_coin_pouch_paladin")
                always("obj.chaosrune", 2..2)
            },
        npcs = PickpocketNpcs.PALADIN,
    ),
    GNOME(
        level = 75,
        xp = 133.3,
        stunDamage = 1..1,
        shout = "What do you think you're doing?",
        lowChance = 128,
        perfectLevel = 120,
        petBase = 108_718,
        pouch = "obj.pickpocket_coin_pouch_gnome",
        coins = 300..300,
        loot =
            loot {
                item(8, "obj.pickpocket_coin_pouch_gnome")
                item(1, "obj.earthrune")
                item(1, "obj.gold_ore")
                item(1, "obj.fire_orb")
                item(1, "obj.swamp_toad")
                item(1, "obj.king_worm")
            },
        npcs = PickpocketNpcs.GNOME,
    ),
    HERO(
        level = 80,
        xp = 163.3,
        stunDamage = 3..3,
        shout = "What do you think you're doing?",
        lowChance = 128,
        perfectLevel = 120,
        petBase = 99_175,
        pouch = "obj.pickpocket_coin_pouch_hero",
        coins = 200..300,
        loot =
            loot {
                item(8, "obj.pickpocket_coin_pouch_hero")
                item(1, "obj.deathrune", 2..2)
                item(1, "obj.bloodrune")
                item(1, "obj.gold_ore")
                item(1, "obj.jug_wine")
                item(1, "obj.diamond")
                item(1, "obj.fire_orb")
            },
        npcs = PickpocketNpcs.HERO,
    ),
    VYRE(
        level = 82,
        xp = 306.9,
        stunDamage = 5..5,
        shout = "How dare you touch me, you filthy creature!",
        lowChance = 128,
        perfectLevel = 120,
        petBase = 99_175,
        pouch = "obj.pickpocket_coin_pouch_vyre",
        coins = 230..315,
        loot =
            loot {
                item(109, "obj.pickpocket_coin_pouch_vyre")
                item(8, "obj.deathrune", 2..2)
                item(2, "obj.bloodrune", 4..4)
                item(6, "obj.blood_pint")
                item(5, "obj.uncut_ruby")
                item(1, "obj.diamond")
                item(1, "obj.cooked_mystery_meat")
                rare(5000, "obj.blood_shard")
            },
        npcs = PickpocketNpcs.VYRE,
    ),
    ELF(
        level = 85,
        xp = 353.3,
        stunDamage = 5..5,
        shout = "Keep your filthy hands to yourself!",
        lowChance = 128,
        perfectLevel = 120,
        petBase = 99_175,
        pouch = "obj.pickpocket_coin_pouch_elf",
        coins = 280..350,
        loot =
            loot {
                item(100, "obj.pickpocket_coin_pouch_elf")
                item(8, "obj.deathrune", 2..2)
                item(6, "obj.naturerune", 3..3)
                item(3, "obj.jug_wine")
                item(2, "obj.diamond")
                item(2, "obj.gold_ore")
                item(2, "obj.fire_orb")
                rare(1000, "obj.prif_crystal_shard")
                rare(1024, "obj.prif_teleport_seed")
            },
        npcs = PickpocketNpcs.ELF,
    ),
    TZHAAR_HUR(
        level = 90,
        xp = 103.4,
        stunDamage = 4..4,
        shout = "JalYt thief! Get away from Hur!",
        lowChance = 128,
        perfectLevel = 120,
        petBase = 176_743,
        pouch = null,
        coins = 1..25,
        loot =
            loot {
                item(20, "obj.tzhaar_token", 1..25)
                item(3, "obj.uncut_sapphire")
                item(2, "obj.uncut_emerald")
                item(1, "obj.uncut_ruby")
                item(1, "obj.uncut_diamond")
            },
        npcs = PickpocketNpcs.TZHAAR_HUR,
    );

    /** Success chance out of 256 for a player at [thievingLevel]. */
    fun successChance(thievingLevel: Int): Int {
        if (thievingLevel >= perfectLevel) {
            return 255
        }
        val span = perfectLevel - level
        val progress = (thievingLevel - level).coerceAtLeast(0)
        val chance = lowChance + (255 - lowChance) * progress / span
        return chance.coerceIn(lowChance, 255)
    }

    companion object {
        /** Coin pouch obj -> the target whose coins it holds. */
        val byPouch: Map<String, PickpocketTarget> =
            entries.filter { it.pouch != null }.associateBy { it.pouch!! }
    }
}
