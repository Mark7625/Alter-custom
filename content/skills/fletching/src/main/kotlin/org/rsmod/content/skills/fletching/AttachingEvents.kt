package org.rsmod.content.skills.fletching

import org.rsmod.api.script.onOpHeldU
import org.rsmod.content.skills.SkillingActionType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class AttachingEvents : PluginScript() {

    override fun ScriptContext.startup() {
        for ((pair, recipes) in pairsFor(FletchingDefinitions.attaching)) {
            val usedFeather =
                listOf(pair.first, pair.second).firstOrNull { it in FletchingDefinitions.feathers }
            onOpHeldU(pair.first, pair.second) {
                openFletchMenu(recipes, SkillingActionType.MAKE_SETS, usedFeather)
            }
        }
    }
}
