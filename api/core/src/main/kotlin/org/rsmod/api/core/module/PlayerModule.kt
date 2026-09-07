package org.rsmod.api.core.module

import com.google.inject.Provider
import jakarta.inject.Inject
import org.rsmod.api.player.music.MusicPlayer
import org.rsmod.api.player.hook.GroundItemDropResolver
import org.rsmod.api.player.hook.PlayerGroundItemDropHook
import org.rsmod.api.player.hook.PlayerObjTakeValidateHook
import org.rsmod.api.player.hook.PlayerObjTakeValidator
import org.rsmod.api.player.hook.PlayerRestrictionHook
import org.rsmod.api.player.hook.PlayerRestrictions
import org.rsmod.api.player.hook.PlayerTeleportValidateHook
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.util.ShuffledPlayerList
import org.rsmod.module.ExtendedModule

public object PlayerModule : ExtendedModule() {
    override fun bind() {
        newSetBinding<PlayerTeleportValidateHook>()
        newSetBinding<PlayerGroundItemDropHook>()
        newSetBinding<PlayerObjTakeValidateHook>()
        newSetBinding<PlayerRestrictionHook>()
        bindInstance<PlayerRestrictions>()
        bindInstance<MusicPlayer>()
        bindInstance<ProtectedAccessContextFactory>()
        bindInstance<ProtectedAccessLauncher>()
        bindInstance<PlayerTeleportValidator>()
        bindInstance<GroundItemDropResolver>()
        bindInstance<PlayerObjTakeValidator>()
        bindProvider(ShuffledPlayerListProvider::class.java)
    }

    private class ShuffledPlayerListProvider
    @Inject
    constructor(private val playerList: PlayerList) : Provider<ShuffledPlayerList> {
        override fun get(): ShuffledPlayerList = ShuffledPlayerList(playerList)
    }
}
