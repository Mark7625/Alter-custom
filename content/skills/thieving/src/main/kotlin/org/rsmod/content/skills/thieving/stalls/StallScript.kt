package org.rsmod.content.skills.thieving.stalls

import dev.openrune.types.ObjectServerType
import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.thievingLvl
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.thieving.Thieving
import org.rsmod.content.skills.thieving.Thieving.giveLoot
import org.rsmod.content.skills.thieving.Thieving.giveThievingXp
import org.rsmod.content.skills.thieving.pets.RockyPetManager
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * `Steal-from` on market stalls. A theft always succeeds once the level is met: the stall is
 * swapped for its empty form (or removed) until it restocks, one item from the stall's stock is
 * handed over and Rocky is rolled.
 */
class StallScript
@Inject
constructor(
    private val locRepo: LocRepository,
    private val objRepo: ObjRepository,
    private val xpMods: XpModifiers,
    private val rocky: RockyPetManager,
) : PluginScript() {

    override fun ScriptContext.startup() {
        for (stall in StallTarget.entries) {
            for ((loc, empty) in stall.locs) {
                onOpLoc2(loc) { steal(it.loc, it.type, stall, empty) }
            }
        }
    }

    private suspend fun ProtectedAccess.steal(
        loc: BoundLocInfo,
        type: ObjectServerType,
        stall: StallTarget,
        empty: String?,
    ) {
        if (player.thievingLvl < stall.level) {
            mes("You need to be at least level ${stall.level} Thieving to steal from this stall.")
            return
        }
        if (!inv.hasFreeSpace()) {
            mes("You don't have enough inventory space to steal from the ${stall.label}.")
            return
        }

        arriveDelay()
        faceLoc(loc)
        anim(Thieving.STEAL_ANIM)
        delay(1)

        // Somebody else may have cleared the stall while the animation played.
        if (locRepo.findExact(loc.coords, type) == null) {
            mes("Someone has already taken everything from the ${stall.label}.")
            return
        }
        if (empty != null) {
            locRepo.change(loc, empty, stall.respawnCycles)
        } else {
            locRepo.del(loc, stall.respawnCycles)
        }

        val drops = stall.loot.roll(random)
        giveLoot(objRepo, drops)
        giveThievingXp(xpMods, stall.xp)
        soundSynth(Thieving.STEAL_SYNTH)
        val stolen = drops.firstOrNull()
        if (stolen != null) {
            spam("You steal ${Thieving.withArticle(Thieving.objName(stolen.obj))}.")
        }
        rocky.roll(this, stall.petBase)
    }
}
