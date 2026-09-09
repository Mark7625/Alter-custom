package org.rsmod.content.skills.thieving.chests

import dev.openrune.types.ObjectServerType
import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.thievingLvl
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.thieving.Thieving
import org.rsmod.content.skills.thieving.Thieving.giveLoot
import org.rsmod.content.skills.thieving.Thieving.giveThievingXp
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Trapped chests. `Search for traps` disarms the chest and loots it; `Open` without searching
 * springs the trap, which hurts in proportion to the player's current hitpoints.
 */
class ChestScript
@Inject
constructor(
    private val locRepo: LocRepository,
    private val objRepo: ObjRepository,
    private val xpMods: XpModifiers,
) : PluginScript() {

    override fun ScriptContext.startup() {
        for (chest in ChestTarget.entries) {
            onOpLoc1(chest.loc) { open(it.loc) }
            onOpLoc2(chest.loc) { searchForTraps(it.loc, it.type, chest) }
        }
        // Several chests share one looted form, so register each empty loc only once.
        for (empty in ChestTarget.entries.map { it.empty }.distinct()) {
            onOpLoc1(empty) { openEmpty() }
            onOpLoc2(empty) { openEmpty() }
        }
    }

    private suspend fun ProtectedAccess.open(loc: BoundLocInfo) {
        arriveDelay()
        faceLoc(loc)
        anim(Thieving.STEAL_ANIM)
        mes("You have activated a trap on the chest.")
        val damage = (player.hitpoints / TRAP_DAMAGE_DIVISOR).coerceAtLeast(1)
        queueHit(delay = 1, type = HitType.Typeless, damage = damage)
        delay(2)
    }

    private suspend fun ProtectedAccess.openEmpty() {
        arriveDelay()
        mes("It looks like this chest has already been looted.")
    }

    private suspend fun ProtectedAccess.searchForTraps(
        loc: BoundLocInfo,
        type: ObjectServerType,
        chest: ChestTarget,
    ) {
        if (player.thievingLvl < chest.level) {
            mes("You need to be at least level ${chest.level} Thieving to disarm this chest's trap.")
            return
        }
        if (!inv.hasFreeSpace()) {
            mes("You don't have enough inventory space to loot the chest.")
            return
        }

        arriveDelay()
        faceLoc(loc)
        anim(Thieving.STEAL_ANIM)
        mes("You search the chest for traps.")
        delay(2)
        mes("You find a trap on the chest... You disarm the trap and open the chest.")
        delay(1)

        if (locRepo.findExact(loc.coords, type) == null) {
            mes("Someone has already looted this chest.")
            return
        }
        locRepo.change(loc, chest.empty, chest.respawnCycles)

        giveLoot(objRepo, chest.loot.roll(random))
        giveThievingXp(xpMods, chest.xp)
        soundSynth(Thieving.STEAL_SYNTH)
        mes("You steal the treasure from the chest.")
    }

    private companion object {
        /** A sprung trap takes an eighth of the player's remaining hitpoints. */
        const val TRAP_DAMAGE_DIVISOR: Int = 8
    }
}
