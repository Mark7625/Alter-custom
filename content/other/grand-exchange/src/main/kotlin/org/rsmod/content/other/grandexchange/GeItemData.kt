package org.rsmod.content.other.grandexchange

import dev.openrune.ServerCacheManager
import kotlin.math.min

/**
 * Guide prices, buy limits and tax exemptions for every item the Grand Exchange trades.
 *
 * The data ships as `ge-items.tsv` (one `id  buy_limit  guide_price  name` line per tradeable
 * item), dumped from the OSRS wiki's prices API and its `Module:GELimits` page. Items missing from
 * the file fall back to their cache shop value and no buy limit.
 */
class GeItemData private constructor(private val entries: Map<Int, Entry>) {
    class Entry(val id: Int, val buyLimit: Int, val guidePrice: Int, val name: String)

    private val taxExempt: Set<Int> =
        entries.values.filter { isTaxExemptName(it.name) }.map { it.id }.toHashSet()

    val size: Int
        get() = entries.size

    fun entry(item: Int): Entry? = entries[item]

    /** The guide price the offer setup screen defaults to. Never below one coin. */
    fun guidePrice(item: Int): Int {
        val known = entries[item]?.guidePrice
        if (known != null && known > 0) {
            return known
        }
        val cost = ServerCacheManager.getItem(item)?.cost ?: 0
        return if (cost > 0) cost else 1
    }

    /** Items a player may buy every four hours, or [NO_LIMIT] when the wiki lists none. */
    fun buyLimit(item: Int): Int {
        val limit = entries[item]?.buyLimit ?: 0
        return if (limit > 0) limit else NO_LIMIT
    }

    fun isTaxExempt(item: Int): Boolean = item in taxExempt

    /** The fee taken from the seller on every item sold at [price]: 2% rounded down, capped. */
    fun taxPerItem(item: Int, price: Int): Int {
        if (isTaxExempt(item)) {
            return 0
        }
        val tax = price.toLong() * GeConfig.TAX_PER_MILLE / 1000
        return min(tax, GeConfig.TAX_CAP_PER_ITEM.toLong()).toInt()
    }

    companion object {
        const val NO_LIMIT: Int = Int.MAX_VALUE
        private const val RESOURCE = "/ge-items.tsv"

        /** Item names the wiki lists as exempt from the tax; potion names cover every dose. */
        private val EXACT_EXEMPT: Set<String> =
            setOf(
                "old school bond",
                "bronze arrow",
                "bronze dart",
                "iron arrow",
                "iron dart",
                "mind rune",
                "steel arrow",
                "steel dart",
                "bass",
                "bread",
                "cake",
                "cooked chicken",
                "cooked meat",
                "herring",
                "lobster",
                "mackerel",
                "meat pie",
                "pike",
                "salmon",
                "shrimps",
                "tuna",
                "ardougne teleport",
                "camelot teleport",
                "civitas illa fortis teleport",
                "falador teleport",
                "games necklace(8)",
                "kourend castle teleport",
                "lumbridge teleport",
                "ring of dueling(8)",
                "teleport to house",
                "varrock teleport",
                "chisel",
                "gardening trowel",
                "glassblowing pipe",
                "hammer",
                "needle",
                "pestle and mortar",
                "rake",
                "saw",
                "secateurs",
                "seed dibber",
                "shears",
                "spade",
                "watering can",
            )

        fun isTaxExemptName(name: String): Boolean {
            val lower = name.lowercase()
            return lower in EXACT_EXEMPT || lower.startsWith("energy potion(")
        }

        fun load(): GeItemData {
            val stream =
                GeItemData::class.java.getResourceAsStream(RESOURCE)
                    ?: error("Missing Grand Exchange item data resource: $RESOURCE")
            val lines = stream.bufferedReader().use { it.readLines() }
            return fromLines(lines)
        }

        fun fromLines(lines: Iterable<String>): GeItemData {
            val entries = HashMap<Int, Entry>()
            for (raw in lines) {
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) {
                    continue
                }
                val parts = line.split('\t')
                if (parts.size < 4) {
                    continue
                }
                val id = parts[0].toIntOrNull() ?: continue
                val limit = parts[1].toIntOrNull() ?: 0
                val price = parts[2].toIntOrNull() ?: 0
                entries[id] = Entry(id, limit, price, parts[3])
            }
            return GeItemData(entries)
        }
    }
}
