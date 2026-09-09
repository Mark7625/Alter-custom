package org.rsmod.api.npc.hit.processor

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.HealthBarServerType
import jakarta.inject.Inject
import org.rsmod.api.config.refs.done.hitmark_groups
import org.rsmod.api.config.refs.params
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.api.npc.headbar.InternalNpcHeadbars
import org.rsmod.api.npc.hit.NpcDamageContributor
import org.rsmod.api.player.ironman.shouldBlockNpcCombatXp
import org.rsmod.api.player.output.soundSynth
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.headbar.Headbar
import org.rsmod.game.hit.Hit

public class StandardNpcHitProcessor
@Inject
constructor(
    private val playerList: PlayerList,
    private val eventBus: EventBus,
    private val damageContributors: Set<NpcDamageContributor>,
) : NpcHitProcessor {
    override fun StandardNpcAccess.process(hit: Hit) {
        var changedDamage: Int? = null
        if (hit.damage > npc.hitpoints) {
            changedDamage = npc.hitpoints
        }

        if (changedDamage == 0) {
            val zeroDamageHitmark = hitmark_groups.zero_damage
            val modifiedHitmark =
                hit.hitmark.copy(
                    self = zeroDamageHitmark.lit.asRSCM(RSCMType.HITMARK),
                    source = zeroDamageHitmark.lit.asRSCM(RSCMType.HITMARK),
                    public =
                        if (hit.hitmark.isPrivate) null
                        else zeroDamageHitmark.tint?.asRSCM(RSCMType.HITMARK),
                    damage = changedDamage,
                )
            takeHit(hit.copy(hitmark = modifiedHitmark))
            return
        }

        val source = if (hit.isFromPlayer) hit.resolvePlayerSource(playerList) else null
        if (source != null && source.shouldBlockNpcCombatXp(npc)) {
            val blocked = hitmark_groups.ironman_blocked
            val mark = blocked.lit.asRSCM(RSCMType.HITMARK)
            val modifiedHitmark =
                hit.hitmark.copy(
                    self = mark,
                    source = mark,
                    public = if (hit.hitmark.isPrivate) null else mark,
                    damage = changedDamage ?: hit.damage,
                )
            takeHit(hit.copy(hitmark = modifiedHitmark))
            return
        }

        if (changedDamage != null) {
            val modifiedHitmark = hit.hitmark.copy(damage = changedDamage)
            takeHit(hit.copy(hitmark = modifiedHitmark))
            return
        }

        takeHit(hit)
    }

    private fun StandardNpcAccess.takeHit(hit: Hit) {
        check(hit.damage <= npc.hitpoints) {
            "Expected hit damage to be less than or equal to available hitpoints: " +
                "health=${npc.hitpoints}, hit=$hit"
        }
        // TODO(combat): Process recoils, retribution(?), etc.
        npc.hitpoints -= hit.damage

        if (hit.damage > 0 && hit.isFromPlayer) {
            hit.resolvePlayerSource(playerList)?.let { source ->
                npc.recordDamage(source, hit.damage)
                for (contributor in damageContributors) {
                    contributor.onPlayerDamageNpc(npc, source, hit.damage)
                }
            }
        }

        playDefendSound(hit)

        val queueDeath = npc.hitpoints == 0 && "queue.death" !in npc.queueList
        if (queueDeath) {
            queueDeath()
        }

        npc.showHitmark(hit.hitmark)

        val visHeadbar = npc.visHeadbar(params.headbar)
        val headbar = hit.createHeadbar(npc.hitpoints, npc.baseHitpointsLvl, visHeadbar)
        npc.showHeadbar(headbar)

        npc.publishHitEvent(hit)
    }

    private fun StandardNpcAccess.playDefendSound(hit: Hit) {
        val source = if (hit.isFromPlayer) hit.resolvePlayerSource(playerList) else null
        if (source == null) {
            return
        }
        val defendSound = npc.visType.paramOrNull(params.defend_sound) ?: return
        source.soundSynth(defendSound)
    }

    private fun Hit.createHeadbar(currHp: Int, maxHp: Int, headbar: HealthBarServerType): Headbar =
        InternalNpcHeadbars.createFromHitmark(hitmark, currHp, maxHp, headbar)

    private fun Npc.publishHitEvent(hit: Hit) {
        val event = NpcHitEvents.Impact(this, hit)
        eventBus.publish(event)
        eventBus.publish(NpcHitEvents.AnyImpact(this, hit))
    }
}
