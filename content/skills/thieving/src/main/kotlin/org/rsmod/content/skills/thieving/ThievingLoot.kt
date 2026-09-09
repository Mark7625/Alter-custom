package org.rsmod.content.skills.thieving

import org.rsmod.api.random.GameRandom

/** One item that a thieving action can hand out. */
data class LootEntry(val obj: String, val count: IntRange, val weight: Int)

/** An item rolled with an independent `1 in [oneIn]` chance on every successful action. */
data class TertiaryEntry(val obj: String, val count: IntRange, val oneIn: Int)

/** One rolled reward. */
data class LootDrop(val obj: String, val count: Int)

/**
 * The reward set of a pickpocket target, stall or chest: everything in [guaranteed] is handed out
 * on every success, one entry of [weighted] is picked by weight, and each [tertiary] entry is
 * rolled on its own.
 */
class LootTable(
    val guaranteed: List<LootEntry>,
    val weighted: List<LootEntry>,
    val tertiary: List<TertiaryEntry>,
) {
    private val totalWeight: Int = weighted.sumOf { it.weight }

    fun roll(random: GameRandom): List<LootDrop> {
        val drops = ArrayList<LootDrop>(guaranteed.size + 2)
        for (entry in guaranteed) {
            drops += LootDrop(entry.obj, random.of(entry.count))
        }
        if (totalWeight > 0) {
            var pick = random.of(maxExclusive = totalWeight)
            for (entry in weighted) {
                pick -= entry.weight
                if (pick < 0) {
                    drops += LootDrop(entry.obj, random.of(entry.count))
                    break
                }
            }
        }
        for (entry in tertiary) {
            if (random.of(maxExclusive = entry.oneIn) == 0) {
                drops += LootDrop(entry.obj, random.of(entry.count))
            }
        }
        return drops
    }

    /** All objs this table can hand out, for inventory-space checks. */
    val objs: Set<String>
        get() = (guaranteed + weighted).mapTo(HashSet()) { it.obj } + tertiary.map { it.obj }

    companion object {
        val EMPTY: LootTable = LootTable(emptyList(), emptyList(), emptyList())
    }
}

class LootBuilder {
    private val guaranteed = mutableListOf<LootEntry>()
    private val weighted = mutableListOf<LootEntry>()
    private val tertiary = mutableListOf<TertiaryEntry>()

    /** Given on every success. */
    fun always(obj: String, count: IntRange = 1..1) {
        guaranteed += LootEntry(obj, count, weight = 0)
    }

    /** One weighted entry is chosen per success. */
    fun item(weight: Int, obj: String, count: IntRange = 1..1) {
        weighted += LootEntry(obj, count, weight)
    }

    /** Rolled independently at `1 in [oneIn]`. */
    fun rare(oneIn: Int, obj: String, count: IntRange = 1..1) {
        tertiary += TertiaryEntry(obj, count, oneIn)
    }

    fun build(): LootTable = LootTable(guaranteed.toList(), weighted.toList(), tertiary.toList())
}

fun loot(block: LootBuilder.() -> Unit): LootTable = LootBuilder().apply(block).build()
