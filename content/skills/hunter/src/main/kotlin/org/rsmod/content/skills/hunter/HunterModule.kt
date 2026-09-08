package org.rsmod.content.skills.hunter

import org.rsmod.plugin.module.PluginModule

class HunterModule : PluginModule() {
    override fun bind() {
        bindInstance<HunterTrap>()
    }
}
