package org.rsmod.api.combat.commons.player

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import kotlin.math.min
import org.rsmod.api.config.refs.params
import org.rsmod.api.player.hit.modifier.PlayerHitModifier
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.lefthand
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.righthand
import org.rsmod.api.player.vars.boolVarp
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcUid
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.hit.Hit
import org.rsmod.game.hit.HitType
import org.rsmod.game.type.getOrNull

private val ProtectedAccess.autoRetaliateDisabled by boolVarp("varp.option_nodef")

public fun Player.queueCombatRetaliate(source: Npc, delay: Int = 1) {
    strongQueue("queue.com_retaliate_npc", delay, source.uid)
}

public fun ProtectedAccess.combatRetaliate(uid: NpcUid, flinchDelay: Int) {
    if (autoRetaliateDisabled || isBusy2) {
        return
    }
    val source = findUid(uid) ?: return

    if (actionDelay < mapClock) {
        actionDelay = mapClock + flinchDelay
    }

    opNpc2(source)
}

public fun Player.queueCombatRetaliate(source: Player, delay: Int = 1) {
    strongQueue("queue.com_retaliate_player", delay, source.uid)
}

public fun ProtectedAccess.combatRetaliate(uid: PlayerUid, flinchDelay: Int) {
    preventLogout("You can't log out until 10 seconds after the end of combat.", 16)
    if (autoRetaliateDisabled || isBusy2) {
        return
    }
    val source = findUid(uid) ?: return

    if (actionDelay < mapClock) {
        actionDelay = mapClock + flinchDelay
    }

    opPlayer2(source)
}

/**
 * Queues an npc's hit on this player. Melee and ranged hits play the defend animation (ranged
 * after [defendClientDelay] client cycles, when the projectile lands); magic hits never do, as
 * in the real game, so a spell cannot interrupt the target's own attack animation.
 *
 * @param defendClientDelay Client cycles (20ms) to wait before the defend animation plays.
 */
public fun Player.finishNpcHit(
    source: Npc,
    delay: Int,
    type: HitType,
    damage: Int,
    modifier: PlayerHitModifier,
    defendClientDelay: Int = 0,
): Hit {
    // Queued before the hit and with the same delay: the hit's strong queue interrupts whatever
    // the player is doing when it lands, and the retaliation queued alongside it fights back.
    queueCombatRetaliate(source, delay.coerceAtLeast(1))
    val hit = queueHit(source, delay, type, damage, modifier)
    if (type != HitType.Magic) {
        combatPlayDefendAnim(defendClientDelay)
    }
    return hit
}

public fun Player.combatPlayDefendAnim(clientDelay: Int = 0) {
    val righthandType = getOrNull(righthand)
    val lefthandType = getOrNull(lefthand)
    val defendAnim = resolveDefendAnim(righthandType, lefthandType)
    anim(defendAnim, delay = clientDelay)
}

private fun resolveDefendAnim(righthand: ItemServerType?, lefthand: ItemServerType?): String {
    val righthandAnim = righthand?.param(params.defend_anim)
    val lefthandAnim = lefthand?.param(params.defend_anim)
    return when {
        lefthandAnim != null && !lefthandAnim.isType("seq.human_unarmedblock") ->
            RSCM.getReverseMapping(RSCMType.SEQ, lefthandAnim.id)
        righthandAnim != null -> RSCM.getReverseMapping(RSCMType.SEQ, righthandAnim.id)
        else -> "seq.human_unarmedblock"
    }
}

public fun Player.combatPlayDefendSpot(ammo: ItemServerType?, clientDelay: Int) {
    val type =
        ammo?.let { id -> ServerCacheManager.getItems().values.firstOrNull { it.id == id.id } }
            ?: return
    if (!type.isCategoryType("category.javelin")) {
        return
    }
    spotanim("spotanim.ballista_special", delay = clientDelay, height = 146)
}

public fun Player.resolveCombatXpMultiplier(): Double = min(1.125, 1 + (0.025 * (combatLevel / 20)))
