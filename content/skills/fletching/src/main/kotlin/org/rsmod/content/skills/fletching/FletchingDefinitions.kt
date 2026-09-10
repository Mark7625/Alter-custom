package org.rsmod.content.skills.fletching

import dev.openrune.types.ItemServerType
import dev.openrune.types.StatType
import org.rsmod.api.table.Tuple2
import org.rsmod.api.table.fletching.FletchingAssemblyRow
import org.rsmod.api.table.fletching.FletchingAttachingRow
import org.rsmod.api.table.fletching.FletchingCuttingRow
import org.rsmod.api.table.fletching.FletchingGemTipsRow
import org.rsmod.api.table.fletching.FletchingStringingRow
import org.rsmod.content.skills.Material
import org.rsmod.game.inv.Inventory

/**
 * The five generated row types have no common supertype, so each gets a one-line adapter into this
 * shared builder rather than repeating the body five times.
 *
 * The tables store XP in tenths — see the header comment on Fletching.kt. This is the only place
 * that divides; no script should ever read a row's xp property directly.
 */
private fun recipe(
    input: List<ItemServerType>,
    inputAmount: List<Int>,
    output: ItemServerType,
    outputAmount: Int,
    statReq: List<Tuple2<StatType, Int>>,
    xpTenths: Int,
    ticks: Int,
    category: String,
    tool: ItemServerType?,
) =
    FletchingRecipe(
        inputs =
            input.mapIndexed { i, item ->
                Material(item.internalName, inputAmount.getOrElse(i) { 1 })
            },
        output = output,
        outputAmount = outputAmount,
        statReq = statReq,
        xp = xpTenths / 10.0,
        ticks = ticks,
        category = category,
        tool = tool?.internalName,
    )

private fun FletchingCuttingRow.toRecipe() =
    recipe(input, inputAmount, output, outputAmount, statReq, xp, ticks, category, tool)

private fun FletchingGemTipsRow.toRecipe() =
    recipe(input, inputAmount, output, outputAmount, statReq, xp, ticks, category, tool)

private fun FletchingStringingRow.toRecipe() =
    recipe(input, inputAmount, output, outputAmount, statReq, xp, ticks, category, tool = null)

private fun FletchingAttachingRow.toRecipe() =
    recipe(input, inputAmount, output, outputAmount, statReq, xp, ticks, category, tool = null)

private fun FletchingAssemblyRow.toRecipe() =
    recipe(input, inputAmount, output, outputAmount, statReq, xp, ticks, category, tool)

object FletchingDefinitions {
    val cutting: List<FletchingRecipe> = FletchingCuttingRow.all().map { it.toRecipe() }
    val stringing: List<FletchingRecipe> = FletchingStringingRow.all().map { it.toRecipe() }
    val attaching: List<FletchingRecipe> = FletchingAttachingRow.all().map { it.toRecipe() }
    val gemTips: List<FletchingRecipe> = FletchingGemTipsRow.all().map { it.toRecipe() }
    val assembly: List<FletchingRecipe> = FletchingAssemblyRow.all().map { it.toRecipe() }

    /**
     * Any feather fletches bolts, darts and headless arrows; the table only carries obj.feather.
     * The other seven were resolved by display-name lookup against the packed cache: the
     * `hunting_*_feather` keys are named by biome, not by colour, so the mapping below is not
     * derivable from spelling.
     * - Feather -> id=314 -> obj.feather
     * - Yellow feather -> id=10090 -> obj.hunting_desert_feather
     * - Orange feather -> id=10091 -> obj.hunting_woodland_feather
     * - Red feather -> id=10088 -> obj.hunting_jungle_feather
     * - Blue feather -> id=10089 -> obj.hunting_polar_feather
     * - Stripy feather -> id=10087 -> obj.hunting_stripy_bird_feather
     * - Gryphon feather -> id=31235 -> obj.gryphon_feather
     * - Stymphike feather -> id=33651 -> obj.stymphike_feather
     */
    val feathers: Set<String> =
        setOf(
            "obj.feather",
            "obj.hunting_desert_feather",
            "obj.hunting_woodland_feather",
            "obj.hunting_jungle_feather",
            "obj.hunting_polar_feather",
            "obj.hunting_stripy_bird_feather",
            "obj.gryphon_feather",
            "obj.stymphike_feather",
        )

    /**
     * Items that stand in for the tool a row names. Only the hammer has any, matching
     * SmithingUtils.hasHammer - without them, requiring a hammer for crossbow assembly would lock
     * out players carrying an Imcando one.
     *
     * The Forestry fletching knife is deliberately absent. It speeds fletching up but does not
     * replace the knife: "the wielder will still need a knife in their inventory to use on logs to
     * initiate the cutting". https://oldschool.runescape.wiki/w/Fletching_knife
     */
    private val TOOL_VARIANTS: Map<String, Set<String>> =
        mapOf(
            "obj.hammer" to setOf("obj.hammer", "obj.imcando_hammer", "obj.imcando_hammer_offhand")
        )

    fun toolVariants(tool: String): Set<String> = TOOL_VARIANTS[tool] ?: setOf(tool)
}

/**
 * Groups two-input recipes by the ordered item pair that triggers them.
 *
 * onOpHeldU is keyed per ordered pair and throws at boot on a duplicate or reversed registration,
 * so callers must register one handler per key of this map and never per recipe.
 *
 * A feather input expands to all eight feather types, since any feather fletches bolts, darts and
 * headless arrows and the table only carries obj.feather.
 */
fun pairsFor(recipes: List<FletchingRecipe>): Map<Pair<String, String>, List<FletchingRecipe>> {
    val byPair = mutableMapOf<Pair<String, String>, MutableList<FletchingRecipe>>()
    for (recipe in recipes) {
        val names = recipe.inputs.map { it.obj.internalName }
        if (names.size != 2) continue
        for (first in variantsOf(names[0])) {
            for (second in variantsOf(names[1])) {
                byPair.getOrPut(first to second) { mutableListOf() }.add(recipe)
            }
        }
    }
    return byPair
}

private fun variantsOf(item: String): Set<String> =
    if (item in FletchingDefinitions.feathers) FletchingDefinitions.feathers else setOf(item)

/**
 * Groups tool-driven recipes by the ordered pair that triggers them: the tool used on the recipe's
 * first material. Any further materials are ordinary inventory requirements, which is what lets a
 * blowpipe (two logs plus a squid beak) or a hunter's spear (three materials) share the same
 * trigger shape as a plain log.
 *
 * Note this deliberately keys on the tool rather than on a material pair. A knife on teak logs
 * therefore offers the teak stock and the hunter's spear from one menu, and a chisel on a sunlight
 * antelope antler offers both its bolt tips and the crossbow upgrade, which is how those recipes
 * are reached in game.
 */
fun byToolAndPrimary(
    recipes: List<FletchingRecipe>
): Map<Pair<String, String>, List<FletchingRecipe>> =
    recipes
        .mapNotNull { recipe ->
            val tool = recipe.tool ?: return@mapNotNull null
            (recipe.inputs.first().obj.internalName to tool) to recipe
        }
        .groupBy({ it.first }, { it.second })

/**
 * Rewrites a canonical obj.feather input to whichever feather type the player actually holds.
 * Recipes without a feather input are returned unchanged, so this is safe to apply to every recipe
 * regardless of family.
 *
 * [preferred], when given, is the feather the player actually clicked to trigger this recipe (see
 * `pairsFor`); if the player holds enough of it, it is used regardless of the fallback order below.
 * This keeps the menu's candidate/max-count pass and the consume step substituting the same
 * feather.
 */
fun FletchingRecipe.withHeldFeather(inv: Inventory, preferred: String? = null): FletchingRecipe {
    val feather = inputs.firstOrNull { it.obj.internalName == "obj.feather" } ?: return this
    if (preferred != null && inv.count(preferred) >= feather.count) {
        return copy(
            inputs =
                inputs.map {
                    if (it.obj.internalName == "obj.feather") Material(preferred, it.count) else it
                }
        )
    }
    // Plain feathers take priority whenever the player holds enough of them; this is checked
    // explicitly rather than relying on "obj.feather" being iterated first in the set below.
    if (inv.count("obj.feather") >= feather.count) return this
    val held =
        FletchingDefinitions.feathers.firstOrNull { inv.count(it) >= feather.count } ?: return this
    return copy(
        inputs =
            inputs.map {
                if (it.obj.internalName == "obj.feather") Material(held, it.count) else it
            }
    )
}
