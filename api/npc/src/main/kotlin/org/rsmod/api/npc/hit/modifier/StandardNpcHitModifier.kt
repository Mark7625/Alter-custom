package org.rsmod.api.npc.hit.modifier

import jakarta.inject.Inject
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.min
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.api.npc.hit.isStyleImmuneTo
import org.rsmod.api.player.cheat.adminOneHitKill
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.hit.HitBuilder

public class StandardNpcHitModifier
@Inject
constructor(private val eventBus: EventBus, private val playerList: PlayerList) : NpcHitModifier {
    override fun HitBuilder.modify(target: Npc) {
        target.publishEvent(this)
        target.applyStyleImmunity(this)
        target.applyFlatArmour(this)
        // Applied last so style immunity and flat armour cannot undo the cheat.
        target.applyOneHitKill(this)
    }

    private fun Npc.publishEvent(hit: HitBuilder) {
        val event = NpcHitEvents.Modify(this, hit)
        eventBus.publish(event)
    }

    private fun Npc.applyStyleImmunity(hit: HitBuilder) {
        if (isStyleImmuneTo(hit.type)) {
            hit.damage = 0
        }
    }

    private fun Npc.applyFlatArmour(hit: HitBuilder) {
        val armour = vars["varn.flat_armour"]

        if (armour > 0) {
            val capped = min(armour.absoluteValue, hit.damage)
            vars["varn.flat_armour"] -= capped
            hit.damage -= capped
        }

        if (armour < 0) {
            vars["varn.flat_armour"] = 0
            hit.damage += armour.absoluteValue
        }
    }

    private fun Npc.applyOneHitKill(hit: HitBuilder) {
        if (!hit.isFromPlayer) {
            return
        }
        val sourceUid = hit.sourceUid ?: return
        val source = PlayerUid(sourceUid).resolve(playerList) ?: return
        if (!source.adminOneHitKill) {
            return
        }
        hit.damage = max(hit.damage, hitpoints)
    }
}
