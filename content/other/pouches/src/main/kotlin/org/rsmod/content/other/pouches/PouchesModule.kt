package org.rsmod.content.other.pouches

import org.rsmod.api.death.PlayerDeathItemHook
import org.rsmod.api.player.hook.PlayerObjTakeRedirectHook
import org.rsmod.content.other.pouches.lootingbag.LootingBagTakeHook
import org.rsmod.plugin.module.PluginModule

class PouchesModule : PluginModule() {
    override fun bind() {
        addSetBinding<PlayerDeathItemHook>(PouchDeathHook::class.java)
        addSetBinding<PlayerObjTakeRedirectHook>(LootingBagTakeHook::class.java)
    }
}
