package org.rsmod.content.skills.fletching

import org.rsmod.api.script.onOpHeldU
import org.rsmod.content.skills.SkillingActionType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class StringingEvents : PluginScript() {

    override fun ScriptContext.startup() {
        for ((pair, recipes) in pairsFor(FletchingDefinitions.stringing)) {
            onOpHeldU(pair.first, pair.second) {
                openFletchMenu(recipes, SkillingActionType.STRING)
            }
        }
    }
}
