package org.rsmod.api.combat

import org.rsmod.api.combat.commons.hook.PvPAttackRestrictionHook
import org.rsmod.plugin.module.PluginModule

internal class CombatModule : PluginModule() {
    override fun bind() {
        bindInstance<NvPCombat>()
        bindInstance<PvNCombat>()
        bindInstance<PvPCombat>()
        newSetBinding<PvPAttackRestrictionHook>()
    }
}
