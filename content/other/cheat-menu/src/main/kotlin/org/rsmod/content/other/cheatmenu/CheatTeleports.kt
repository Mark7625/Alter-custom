package org.rsmod.content.other.cheatmenu

import org.rsmod.map.CoordGrid

/** A single named teleport target listed under a [TeleportRegion]. */
internal data class TeleportDestination(val name: String, val coords: CoordGrid)

/**
 * Curated town/city destinations grouped by region so that no single menu grows past a comfortable
 * length. The cheat menu also offers a "custom coordinates" entry for anywhere not listed here.
 */
internal enum class TeleportRegion(val label: String, val destinations: List<TeleportDestination>) {
    Misthalin(
        "Misthalin",
        listOf(
            TeleportDestination("Lumbridge", CoordGrid(3222, 3218)),
            TeleportDestination("Varrock Square", CoordGrid(3213, 3428)),
            TeleportDestination("Grand Exchange", CoordGrid(3164, 3486)),
            TeleportDestination("Draynor Village", CoordGrid(3093, 3244)),
            TeleportDestination("Edgeville", CoordGrid(3087, 3496)),
            TeleportDestination("Barbarian Village", CoordGrid(3082, 3420)),
            TeleportDestination("Champions' Guild", CoordGrid(3191, 3363)),
            TeleportDestination("Wizards' Tower", CoordGrid(3113, 3177)),
            TeleportDestination("Lumbridge Swamp", CoordGrid(3200, 3170)),
        ),
    ),
    Asgarnia(
        "Asgarnia",
        listOf(
            TeleportDestination("Falador", CoordGrid(2965, 3380)),
            TeleportDestination("Port Sarim", CoordGrid(3022, 3217)),
            TeleportDestination("Rimmington", CoordGrid(2957, 3214)),
            TeleportDestination("Taverley", CoordGrid(2894, 3443)),
            TeleportDestination("Burthorpe", CoordGrid(2899, 3544)),
            TeleportDestination("Goblin Village", CoordGrid(2957, 3506)),
            TeleportDestination("Warriors' Guild", CoordGrid(2845, 3540)),
            TeleportDestination("Crafting Guild", CoordGrid(2933, 3288)),
            TeleportDestination("Dwarven Mine", CoordGrid(3018, 9740)),
        ),
    ),
    Kandarin(
        "Kandarin",
        listOf(
            TeleportDestination("Seers' Village / Camelot", CoordGrid(2757, 3477)),
            TeleportDestination("Catherby", CoordGrid(2809, 3435)),
            TeleportDestination("East Ardougne", CoordGrid(2662, 3305)),
            TeleportDestination("Yanille", CoordGrid(2605, 3093)),
            TeleportDestination("Port Khazard", CoordGrid(2661, 3161)),
            TeleportDestination("Fishing Guild", CoordGrid(2611, 3393)),
            TeleportDestination("Ranging Guild", CoordGrid(2658, 3439)),
            TeleportDestination("Barbarian Outpost", CoordGrid(2519, 3571)),
            TeleportDestination("Tree Gnome Stronghold", CoordGrid(2461, 3444)),
        ),
    ),
    KharidianDesert(
        "Kharidian Desert",
        listOf(
            TeleportDestination("Al Kharid", CoordGrid(3293, 3174)),
            TeleportDestination("Shantay Pass", CoordGrid(3304, 3124)),
            TeleportDestination("Bedabin Camp", CoordGrid(3181, 3046)),
            TeleportDestination("Bandit Camp", CoordGrid(3174, 2987)),
            TeleportDestination("Pollnivneach", CoordGrid(3358, 2965)),
            TeleportDestination("Nardah", CoordGrid(3427, 2891)),
            TeleportDestination("Sophanem", CoordGrid(3288, 2785)),
            TeleportDestination("Uzer", CoordGrid(3489, 3089)),
        ),
    ),
    Morytania(
        "Morytania",
        listOf(
            TeleportDestination("Canifis", CoordGrid(3494, 3489)),
            TeleportDestination("Slayer Tower", CoordGrid(3428, 3535)),
            TeleportDestination("Port Phasmatys", CoordGrid(3676, 3479)),
            TeleportDestination("Mort'ton", CoordGrid(3488, 3288)),
            TeleportDestination("Burgh de Rott", CoordGrid(3496, 3235)),
            TeleportDestination("Barrows", CoordGrid(3565, 3306)),
            TeleportDestination("Darkmeyer", CoordGrid(3626, 3363)),
            TeleportDestination("Mos Le'Harmless", CoordGrid(3682, 2963)),
        ),
    ),
    Karamja(
        "Karamja",
        listOf(
            TeleportDestination("Musa Point", CoordGrid(2956, 3143)),
            TeleportDestination("Brimhaven", CoordGrid(2802, 3178)),
            TeleportDestination("Tai Bwo Wannai", CoordGrid(2795, 3065)),
            TeleportDestination("Shilo Village", CoordGrid(2852, 2954)),
            TeleportDestination("TzHaar City", CoordGrid(2480, 5175)),
        ),
    ),
    Fremennik(
        "Fremennik & Isles",
        listOf(
            TeleportDestination("Rellekka", CoordGrid(2660, 3657)),
            TeleportDestination("Piscatoris", CoordGrid(2340, 3650)),
            TeleportDestination("Waterbirth Island", CoordGrid(2544, 3741)),
            TeleportDestination("Neitiznot", CoordGrid(2337, 3807)),
            TeleportDestination("Jatizso", CoordGrid(2416, 3801)),
            TeleportDestination("Miscellania", CoordGrid(2515, 3862)),
            TeleportDestination("Lunar Isle", CoordGrid(2113, 3915)),
            TeleportDestination("Keldagrim", CoordGrid(2845, 10210)),
        ),
    ),
    Kourend(
        "Great Kourend",
        listOf(
            TeleportDestination("Kourend Castle", CoordGrid(1608, 3670)),
            TeleportDestination("Arceuus", CoordGrid(1698, 3743)),
            TeleportDestination("Port Piscarilius", CoordGrid(1800, 3749)),
            TeleportDestination("Hosidius", CoordGrid(1743, 3517)),
            TeleportDestination("Shayzien", CoordGrid(1504, 3623)),
            TeleportDestination("Lovakengj", CoordGrid(1508, 3789)),
            TeleportDestination("Woodcutting Guild", CoordGrid(1591, 3475)),
            TeleportDestination("Mount Karuulm", CoordGrid(1311, 3808)),
            TeleportDestination("Farming Guild", CoordGrid(1249, 3718)),
        ),
    ),
    Tirannwn(
        "Tirannwn",
        listOf(
            TeleportDestination("Lletya", CoordGrid(2330, 3170)),
            TeleportDestination("Port Tyras", CoordGrid(2145, 3122)),
            TeleportDestination("Prifddinas", CoordGrid(3243, 6053)),
        ),
    ),
    Wilderness(
        "Wilderness",
        listOf(
            TeleportDestination("Edgeville ditch", CoordGrid(3087, 3520)),
            TeleportDestination("Ferox Enclave", CoordGrid(3151, 3635)),
            TeleportDestination("Revenant Caves entrance", CoordGrid(3130, 3832)),
            TeleportDestination("Lava Dragon Isle", CoordGrid(3200, 3807)),
            TeleportDestination("Chaos Temple (level 38)", CoordGrid(2947, 3821)),
            TeleportDestination("Wilderness Agility Course", CoordGrid(2998, 3916)),
            TeleportDestination("Mage Arena bank", CoordGrid(3094, 3956)),
        ),
    ),
}
