package org.rsmod.content.bosses.vorkath

import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.api.death.PlayerDeathHook
import org.rsmod.api.death.PlayerDeathStorageHook
import org.rsmod.api.player.hook.PlayerPostTickHook
import org.rsmod.plugin.module.PluginModule

public class VorkathModule : PluginModule() {
    override fun bind() {
        addSetBinding<NpcAttackValidateHook>(VorkathAttackHook::class.java)
        addSetBinding<PlayerDeathHook>(VorkathPlayerDeathHook::class.java)
        addSetBinding<PlayerDeathStorageHook>(VorkathDeathStorage::class.java)
        addSetBinding<PlayerDeathCleanupHook>(VorkathDeathCleanup::class.java)
        addSetBinding<PlayerPostTickHook>(VorkathLifecycle::class.java)
    }
}
