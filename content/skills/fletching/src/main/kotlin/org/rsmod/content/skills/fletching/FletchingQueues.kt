package org.rsmod.content.skills.fletching

import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Owns the shared fletching production queue. Cutting, stringing, attaching, gem tips and assembly
 * all queue onto [registerFletchingQueue] - it lives in its own script so no single family's
 * removal or refactor can silently take the other four down with it.
 */
class FletchingQueues : PluginScript() {
    override fun ScriptContext.startup() {
        registerFletchingQueue()
    }
}
