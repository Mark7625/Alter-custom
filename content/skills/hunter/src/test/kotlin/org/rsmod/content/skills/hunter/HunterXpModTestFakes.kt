package org.rsmod.content.skills.hunter

import org.rsmod.api.stats.xpmod.StatXpMod
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.game.entity.Player

/**
 * The bonus a test wanting a modified award drives its world with: +100%, because doubling is
 * exact for every row where a realistic fractional bonus would round differently per creature.
 */
internal const val DOUBLE_HUNTER_XP: Double = 1.0

/**
 * An [XpModifiers] that adds [bonus] to `stat.hunter` and nothing to any other stat. An empty set
 * is a flat 1.0, so without one test per technique spending a real bonus, the multiply on every
 * award site could be deleted with the suite still green.
 */
internal fun hunterXpModifiers(bonus: Double, craftingBonus: Double = 0.0): XpModifiers {
    val mods = buildSet {
        if (bonus != 0.0) {
            add(HunterXpBonus(bonus))
        }
        if (craftingBonus != 0.0) {
            add(CraftingXpBonus(craftingBonus))
        }
    }
    return XpModifiers(mods)
}

/** The shape a Hunter skilling outfit would have, with a bonus a test picks instead of a cape. */
private class HunterXpBonus(private val bonus: Double) : StatXpMod("stat.hunter") {
    override fun Player.modifier(): Double = bonus
}

/** The same, for `stat.crafting` - bird house crafting's award needs a modifier on *that* stat. */
private class CraftingXpBonus(private val bonus: Double) : StatXpMod("stat.crafting") {
    override fun Player.modifier(): Double = bonus
}
