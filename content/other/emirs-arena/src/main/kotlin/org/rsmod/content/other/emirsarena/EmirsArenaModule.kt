package org.rsmod.content.other.emirsarena

import org.rsmod.api.combat.commons.hook.PvPAttackRestrictionHook
import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.api.death.PlayerDeathHook
import org.rsmod.api.death.PlayerRespawnHook
import org.rsmod.api.death.PvPAttackValidateHook
import org.rsmod.api.player.hook.PlayerRestrictionHook
import org.rsmod.api.player.hook.PlayerTeleportValidateHook
import org.rsmod.content.other.emirsarena.duel.DuelManager
import org.rsmod.plugin.module.PluginModule

public class EmirsArenaModule : PluginModule() {
    override fun bind() {
        bindInstance<DuelManager>()
        addSetBinding<PvPAttackValidateHook>(EmirsArenaHooks::class.java)
        addSetBinding<PvPAttackRestrictionHook>(EmirsArenaHooks::class.java)
        addSetBinding<PlayerRestrictionHook>(EmirsArenaHooks::class.java)
        addSetBinding<PlayerTeleportValidateHook>(EmirsArenaHooks::class.java)
        addSetBinding<PlayerDeathHook>(EmirsArenaHooks::class.java)
        addSetBinding<PlayerDeathCleanupHook>(EmirsArenaHooks::class.java)
        addSetBinding<PlayerRespawnHook>(EmirsArenaHooks::class.java)
    }
}
