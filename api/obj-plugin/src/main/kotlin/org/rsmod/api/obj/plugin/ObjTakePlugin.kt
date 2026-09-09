package org.rsmod.api.obj.plugin

import dev.openrune.ServerCacheManager
import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import org.rsmod.api.config.Constants
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.player.hook.PlayerObjTakeRedirector
import org.rsmod.api.player.hook.PlayerObjTakeValidator
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.advanced.onDefaultOpObj3
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.obj.Obj
import org.rsmod.objtx.TransactionResultList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

public class ObjTakePlugin
@Inject
constructor(
    private val repo: ObjRepository,
    private val takeValidator: PlayerObjTakeValidator,
    private val takeRedirector: PlayerObjTakeRedirector,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onDefaultOpObj3 { triggerTake(it.obj) }
    }

    private suspend fun ProtectedAccess.triggerTake(obj: Obj) {
        val type = ServerCacheManager.getItem(obj.type) ?: return
        val denial = takeValidator.validate(player, obj, type)
        if (denial != null) {
            mes(denial)
            return
        }
        val redirected = takeRedirector.find(player, obj, type) != null
        if (!redirected && !player.hasInvSpace(obj)) {
            player.mes(Constants.dm_take_invspace)
            return
        }
        player.resetAnim()
        if (player.coords != obj.coords) {
            takeFar(obj, type)
        } else {
            soundSynth("synth.pick2")
            player.takeClose(obj, type)
        }
    }

    private suspend fun ProtectedAccess.takeFar(obj: Obj, type: ItemServerType) {
        delay(1)
        anim("seq.human_pickuptable")
        soundSynth("synth.pick2")
        player.takeClose(obj, type)
    }

    private fun Player.takeClose(obj: Obj, type: ItemServerType) {
        val redirect = takeRedirector.find(this, obj, type)
        val take = transaction(obj)
        if (redirect == null && take.failure) {
            mes(Constants.dm_take_invspace)
            return
        }
        val removed = repo.del(obj)
        if (!removed) {
            mes(Constants.dm_take_taken)
            return
        }
        if (redirect != null && redirect.take(this, obj, type)) {
            return
        }
        // The redirect container refused after all; fall back to the inventory transaction that
        // was prepared before the obj left the ground.
        if (take.failure) {
            mes(Constants.dm_take_invspace)
        } else {
            take.commitAll()
        }
    }

    private fun Player.hasInvSpace(obj: Obj): Boolean = transaction(obj).success

    private fun Player.transaction(obj: Obj): TransactionResultList<InvObj> =
        invAdd(inv, obj.type, obj.count, autoCommit = false)
}
