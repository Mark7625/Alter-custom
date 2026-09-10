package org.rsmod.content.skills.fletching

import dev.openrune.types.StatType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.api.table.Tuple2
import org.rsmod.content.skills.Material
import org.rsmod.content.skills.SkillMultiConfig
import org.rsmod.content.skills.SkillMultiEntry
import org.rsmod.content.skills.SkillingActionType
import org.rsmod.content.skills.openSkillMulti
import org.rsmod.plugin.scripts.ScriptContext

private const val FLETCH_QUEUE = "queue.fletching_produce"

data class FletchingTask(val recipe: FletchingRecipe, val amount: Int, val created: Int)

fun ScriptContext.registerFletchingQueue() {
    onPlayerQueueWithArgs<FletchingTask>(FLETCH_QUEUE) { processFletchTick(it.args) }
}

/**
 * Silent stat-requirement check used to build the candidate list for the make-X menu.
 * [meetsStatReqs] below shows a message box on failure, which is only appropriate for the single
 * recipe the player actually chose - running it over every candidate here would fire a blocking
 * dialog for each recipe the player doesn't yet qualify for.
 */
private fun ProtectedAccess.hasStatReqs(reqs: List<Tuple2<StatType, Int>>): Boolean =
    reqs.all { statBase(it.t0.internalName) >= it.t1 }

private suspend fun ProtectedAccess.meetsStatReqs(reqs: List<Tuple2<StatType, Int>>): Boolean {
    for (req in reqs) {
        val stat = req.t0
        val level = req.t1
        if (statBase(stat.internalName) < level) {
            val name = stat.displayName.ifEmpty { stat.internalName.removePrefix("stat.") }
            mesbox("You need a $name level of $level.")
            return false
        }
    }
    return true
}

/** "You need a hammer to make that." The tool is named by its cache display name. */
private fun missingToolMessage(tool: String): String =
    "You need a ${Material(tool).obj.name.lowercase()} to make that."

/** Some gem tip rows also carry a crafting requirement, so the fletching one is picked by name. */
private fun FletchingRecipe.fletchingLevel(): Int =
    statReq.firstOrNull { it.t0.internalName == "stat.fletching" }?.t1 ?: 0

suspend fun ProtectedAccess.startFletching(recipe: FletchingRecipe, amount: Int) {
    if (!meetsStatReqs(recipe.statReq)) return
    val tool = recipe.tool
    if (tool != null && !recipe.hasTool(inv)) {
        mes(missingToolMessage(tool))
        return
    }
    if (!recipe.hasMaterials(inv)) {
        mes("You don't have the materials needed to make that.")
        return
    }
    weakQueue(FLETCH_QUEUE, 1, FletchingTask(recipe, amount, 0))
}

/**
 * Opens the standard make-X menu for whichever recipes the player can currently make, and starts
 * the chosen one. Every fletching flow funnels through here; the only thing that varies between
 * them is the action type the client uses to label the menu.
 */
suspend fun ProtectedAccess.openFletchMenu(
    recipes: List<FletchingRecipe>,
    actionType: SkillingActionType,
    preferredFeather: String? = null,
) {
    // Sorted by level because a trigger can now gather recipes from more than one place in the
    // table - a knife on teak logs reaches both the teak stock and the hunter's spear - and the
    // make-X menu should still read low level to high.
    val affordable =
        recipes
            .map { it.withHeldFeather(inv, preferredFeather) }
            .filter { hasStatReqs(it.statReq) && it.maxProducible(inv) > 0 }
            .sortedBy { it.fletchingLevel() }
    val candidates = affordable.filter { it.hasTool(inv) }
    if (candidates.isEmpty()) {
        // Only complain when the tool is the sole thing standing in the way; an empty menu for any
        // other reason stays silent, as it did before.
        affordable.firstNotNullOfOrNull { it.tool }?.let { mes(missingToolMessage(it)) }
        return
    }

    // Two recipes can share an output name (e.g. multiple routes to obj.arrow_shaft), so the
    // entry itself - internal name plus materials - is the disambiguating key back to the recipe
    // that produced it, not the output name alone.
    val entries = candidates.map { SkillMultiEntry(it.output.internalName, it.inputs) }
    val recipeByEntry = entries.zip(candidates).toMap()

    openSkillMulti(
        SkillMultiConfig(
            actionType = actionType,
            verb = "make",
            entries = entries,
            maxCountProvider = { inventory, entry ->
                recipeByEntry[entry]?.maxProducible(inventory) ?: 0
            },
        )
    ) { selection ->
        val recipe = recipeByEntry[selection.entry] ?: return@openSkillMulti
        startFletching(recipe, selection.amount)
    }
}

private suspend fun ProtectedAccess.processFletchTick(task: FletchingTask) {
    val recipe = task.recipe

    if (!meetsStatReqs(recipe.statReq) || !recipe.hasTool(inv) || !recipe.hasMaterials(inv)) {
        resetAnim()
        return
    }

    // The shared animation loops, so it suits a run of actions and a lone one takes the one-shot
    // variant. Its tick gate covers the rows that animate nothing; a row with an animation of its
    // own is exempt, the dart rows being 0-tick yet animated.
    val specific = FletchingAnims.forOutput(recipe.output)
    val looping = specific == null && recipe.ticks > 0 && task.amount > 1
    if (specific != null) {
        anim(specific)
    } else if (recipe.ticks > 0) {
        anim(if (looping) FletchingAnims.GENERIC else FletchingAnims.GENERIC_SINGLE)
    }

    val consumed = mutableListOf<Pair<String, Int>>()
    for (material in recipe.inputs) {
        val name = material.obj.internalName
        if (invDel(inv, name, material.count).success) {
            consumed += name to material.count
        } else {
            consumed.forEach { (restore, count) -> invAdd(inv, restore, count) }
            resetAnim()
            return
        }
    }

    if (invAdd(inv, recipe.output.internalName, recipe.outputAmount).failure) {
        consumed.forEach { (restore, count) -> invAdd(inv, restore, count) }
        mes("You don't have enough inventory space to make that.")
        resetAnim()
        return
    }

    statAdvance("stat.fletching", recipe.xp)

    val created = task.created + 1
    if (created < task.amount) {
        // A queue submitted while the queue list is being processed is decremented on that same
        // cycle, so a re-queue arrives a cycle sooner than the opening one in startFletching.
        weakQueue(
            FLETCH_QUEUE,
            if (recipe.ticks > 0) recipe.ticks + 1 else 1,
            FletchingTask(recipe, task.amount, created),
        )
    } else if (looping) {
        // Only the looping animation needs stopping; a one-shot would be cut short instead.
        resetAnim()
    }
}
