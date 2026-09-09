package org.rsmod.content.other.pets

import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.content.other.pets.cats.CatPetDeathHook
import org.rsmod.plugin.module.PluginModule

class PetsModule : PluginModule() {
    override fun bind() {
        addSetBinding<PlayerDeathCleanupHook>(CatPetDeathHook::class.java)
    }
}
