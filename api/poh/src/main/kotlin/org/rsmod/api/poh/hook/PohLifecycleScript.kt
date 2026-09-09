package org.rsmod.api.poh.hook

import jakarta.inject.Inject
import org.rsmod.api.poh.PohDataStore
import org.rsmod.api.poh.PohManager
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Releases house regions on logout and forces [PohDataStore] to load (and verify its manifest) at
 * server startup rather than on first house entry.
 */
internal class PohLifecycleScript
@Inject
constructor(
    private val manager: PohManager,
    @Suppress("unused") private val dataStore: PohDataStore,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onPlayerLogin { manager.handleLogin(player) }
        onPlayerLogout { manager.handleLogout(player) }
    }
}
