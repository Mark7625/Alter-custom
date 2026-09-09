package org.rsmod.content.quest.area.varrock.demonslayer

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.player.righthand
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

/** Delrith can only be fought with Silverlight in hand. */
class SilverlightAttackHook @Inject constructor() : NpcAttackValidateHook {
    private val delrithId = "npc.delrith".asRSCM(RSCMType.NPC)

    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult {
        if (npc.id != delrithId) {
            return NpcAttackValidateResult.Pass
        }
        if (player.righthand?.id != DemonSlayerQuest.SILVERLIGHT_ID) {
            return NpcAttackValidateResult.Deny("You'll need to wield Silverlight to harm Delrith.")
        }
        return NpcAttackValidateResult.Pass
    }
}
