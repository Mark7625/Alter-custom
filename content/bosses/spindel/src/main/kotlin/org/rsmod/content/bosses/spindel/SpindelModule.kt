package org.rsmod.content.bosses.spindel

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.module.PluginModule

public class SpindelModule : PluginModule() {
    override fun bind() {
        addSetBinding<NpcAttackValidateHook>(SpindelAttackValidateHook::class.java)
    }
}

internal class SpindelAttackValidateHook @Inject constructor() : NpcAttackValidateHook {
    private val spindelId by lazy { "npc.venenatis_singles".asRSCM(RSCMType.NPC) }

    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult =
        if (npc.id == spindelId) {
            NpcAttackValidateResult.BypassSingleWayPvnRestriction
        } else {
            NpcAttackValidateResult.Pass
        }
}
