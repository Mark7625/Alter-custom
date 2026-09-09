package org.rsmod.content.travel.jewellery

import org.rsmod.api.player.hook.TeleportType
import org.rsmod.map.CoordGrid

/**
 * One option on a piece of teleport jewellery. A `null` [coords] is a destination whose content
 * does not exist on this server yet; picking it tells the player so instead of teleporting.
 */
data class JewelleryDestination(
    val name: String,
    val coords: CoordGrid?,
    /** The landing tile is inside the Wilderness, so the player is asked to confirm first. */
    val wilderness: Boolean = false,
)

/**
 * A family of teleport jewellery: every charge variant of one item, in the order the charges run
 * down, plus where it can take the player.
 *
 * @param charged Obj names from the highest charge to the lowest. Using a charge turns the obj
 *   into the next entry; using the last entry turns it into [uncharged], or destroys it when that
 *   is `null`.
 * @param infinite Variants that never lose a charge (eternal glory, eternal slayer ring).
 * @param wornMenuOp When set, this worn op opens the destination menu rather than the worn ops
 *   mapping one-to-one onto [destinations] from op 2 upwards.
 */
data class Jewellery(
    val label: String,
    val charged: List<String>,
    val destinations: List<JewelleryDestination>,
    val teleportType: TeleportType = TeleportType.Standard,
    val infinite: List<String> = emptyList(),
    val uncharged: String? = null,
    val wornMenuOp: Int? = null,
) {
    val objs: List<String>
        get() = charged + infinite

    init {
        require(charged.isNotEmpty() || infinite.isNotEmpty()) { "$label needs at least one obj." }
        require(destinations.isNotEmpty()) { "$label needs at least one destination." }
        require(objs.all { it.startsWith("obj.") }) { "$label has a non-obj entry." }
        require(objs.toSet().size == objs.size) { "$label lists an obj twice." }
    }

    /** The obj [obj] turns into after one charge is spent, or `null` when it crumbles to dust. */
    fun afterCharge(obj: String): String? {
        val index = charged.indexOf(obj)
        if (index == -1) {
            return obj
        }
        return charged.getOrNull(index + 1) ?: uncharged
    }

    /** Charges left on [obj] after it has been used once, or `null` for infinite variants. */
    fun chargesAfter(obj: String): Int? {
        val index = charged.indexOf(obj)
        return if (index == -1) null else charged.size - index - 1
    }
}

object JewelleryTeleports {
    private fun tile(x: Int, z: Int, level: Int = 0) = CoordGrid(x, z, level)

    private fun dest(name: String, x: Int, z: Int, level: Int = 0, wilderness: Boolean = false) =
        JewelleryDestination(name, tile(x, z, level), wilderness)

    private fun missing(name: String) = JewelleryDestination(name, null)

    private fun charges(base: String, from: Int, to: Int = 1): List<String> =
        (from downTo to).map { "obj.${base}_$it" }

    private val GLORY_DESTINATIONS =
        listOf(
            dest("Edgeville", 3087, 3496),
            dest("Karamja", 2918, 3176),
            dest("Draynor Village", 3105, 3251),
            dest("Al Kharid", 3293, 3163),
        )

    private val WEALTH_DESTINATIONS =
        listOf(
            dest("Miscellania", 2535, 3861),
            dest("Grand Exchange", 3164, 3477),
            dest("Falador Park", 2995, 3375),
            dest("Dondakan's Rock", 2828, 10166),
        )

    val all: List<Jewellery> =
        listOf(
            Jewellery(
                label = "amulet",
                charged = charges("amulet_of_glory", 6),
                infinite = listOf("obj.amulet_of_glory_inf"),
                uncharged = "obj.amulet_of_glory",
                teleportType = TeleportType.MemberLevel30,
                destinations = GLORY_DESTINATIONS,
            ),
            Jewellery(
                label = "amulet",
                charged = charges("trail_amulet_of_glory", 6),
                uncharged = "obj.trail_amulet_of_glory",
                teleportType = TeleportType.MemberLevel30,
                destinations = GLORY_DESTINATIONS,
            ),
            Jewellery(
                label = "ring",
                charged = charges("ring_of_dueling", 8),
                destinations =
                    listOf(
                        dest("Emir's Arena", 3316, 3235),
                        dest("Castle Wars", 2441, 3090),
                        dest("Ferox Enclave", 3151, 3635),
                        missing("Fortis Colosseum"),
                    ),
            ),
            Jewellery(
                label = "necklace",
                charged = charges("necklace_of_minigames", 8),
                destinations =
                    listOf(
                        dest("Burthorpe", 2898, 3553),
                        dest("Barbarian Outpost", 2520, 3571),
                        dest("Corporeal Beast", 2967, 4384),
                        dest("Tears of Guthix", 3244, 9501),
                        dest("Wintertodt Camp", 1628, 3938),
                    ),
            ),
            Jewellery(
                label = "necklace",
                charged = charges("jewl_necklace_of_skills", 6),
                uncharged = "obj.jewl_necklace_of_skills",
                teleportType = TeleportType.MemberLevel30,
                destinations =
                    listOf(
                        dest("Fishing Guild", 2611, 3390),
                        dest("Mining Guild", 3051, 9763),
                        dest("Crafting Guild", 2933, 3295),
                        dest("Cooking Guild", 3143, 3442),
                        dest("Woodcutting Guild", 1662, 3505),
                        dest("Farming Guild", 1249, 3719),
                    ),
            ),
            Jewellery(
                label = "bracelet",
                charged = charges("jewl_bracelet_of_combat", 6),
                uncharged = "obj.jewl_bracelet_of_combat",
                teleportType = TeleportType.MemberLevel30,
                destinations =
                    listOf(
                        dest("Warriors' Guild", 2879, 3544),
                        dest("Champions' Guild", 3190, 3367),
                        dest("Monastery", 3051, 3490),
                        dest("Ranging Guild", 2656, 3441),
                    ),
            ),
            Jewellery(
                label = "ring",
                charged = charges("ring_of_wealth", 5),
                uncharged = "obj.ring_of_wealth",
                teleportType = TeleportType.MemberLevel30,
                destinations = WEALTH_DESTINATIONS,
            ),
            Jewellery(
                label = "ring",
                charged = (5 downTo 1).map { "obj.ring_of_wealth_i$it" },
                uncharged = "obj.ring_of_wealth_i",
                teleportType = TeleportType.MemberLevel30,
                destinations = WEALTH_DESTINATIONS,
            ),
            Jewellery(
                label = "necklace",
                charged = charges("necklace_of_passage", 5),
                destinations =
                    listOf(
                        dest("Wizards' Tower", 3114, 3181),
                        dest("The Outpost", 2431, 3348),
                        dest("Eagles' Eyrie", 2333, 3579),
                        missing("Wyrmscraig"),
                    ),
            ),
            Jewellery(
                label = "amulet",
                charged = charges("burning_amulet", 5),
                destinations =
                    listOf(
                        dest("Chaos Temple", 3236, 3635, wilderness = true),
                        dest("Bandit Camp", 3038, 3651, wilderness = true),
                        dest("Lava Maze", 3028, 3842, wilderness = true),
                    ),
            ),
            Jewellery(
                label = "pendant",
                charged = charges("necklace_of_digsite", 5),
                destinations =
                    listOf(
                        dest("Digsite", 3341, 3445),
                        dest("Fossil Island", 3764, 3869),
                        dest("Lithkren", 3549, 10456),
                    ),
            ),
            Jewellery(
                label = "ring",
                charged = charges("slayer_ring", 8),
                infinite = listOf("obj.slayer_ring_eternal"),
                teleportType = TeleportType.MemberLevel30,
                wornMenuOp = 3,
                destinations =
                    listOf(
                        dest("Stronghold Slayer Cave", 2433, 3423),
                        dest("Slayer Tower", 3422, 3537),
                        dest("Fremennik Slayer Dungeon", 2802, 10001),
                        dest("Tarn's Lair", 3185, 4601),
                        dest("Dark Beasts", 2028, 4636),
                        missing("Wyrmscraig Cavern"),
                    ),
            ),
        )
}
