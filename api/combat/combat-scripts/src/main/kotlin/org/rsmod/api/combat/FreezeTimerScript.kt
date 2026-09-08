package org.rsmod.api.combat

import org.rsmod.api.combat.commons.CombatEffects
import org.rsmod.api.script.onPlayerTimer
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

public class FreezeTimerScript : PluginScript() {
    override fun ScriptContext.startup() {
        onPlayerTimer("timer.combat_freeze") {
            CombatEffects.unfreeze(player)
        }
        onPlayerTimer("timer.combat_freeze_immunity") {
            CombatEffects.clearFreezeImmunity(player)
        }
        onPlayerTimer("timer.combat_stun") {
            CombatEffects.unstun(player)
        }
    }
}
