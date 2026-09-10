package org.rsmod.content.skills.fletching

import org.rsmod.api.script.onOpHeldU
import org.rsmod.content.skills.SkillingActionType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class AssemblyEvents : PluginScript() {

    override fun ScriptContext.startup() {
        for ((pair, recipes) in pairsFor(FletchingDefinitions.assembly)) {
            onOpHeldU(pair.first, pair.second) {
                openFletchMenu(recipes, SkillingActionType.MAKE_SETS)
            }
        }
    }
}
