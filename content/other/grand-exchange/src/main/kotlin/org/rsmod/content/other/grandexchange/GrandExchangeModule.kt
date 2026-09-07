package org.rsmod.content.other.grandexchange

import org.rsmod.plugin.module.PluginModule

public class GrandExchangeModule : PluginModule() {
    override fun bind() {
        bindSingleton<GeItemData>(GeItemData.load())
        bindInstance<GeExchange>()
        bindInstance<GeService>()
    }
}
