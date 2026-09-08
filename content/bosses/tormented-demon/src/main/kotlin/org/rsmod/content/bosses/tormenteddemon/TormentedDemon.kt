package org.rsmod.content.bosses.tormenteddemon

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossPluginScript
import org.rsmod.api.bosses.runtime.encounter
import org.rsmod.api.bosses.runtime.lob
import org.rsmod.api.bosses.runtime.suppressAttacks
import org.rsmod.api.bosses.spec.Condition
import org.rsmod.api.bosses.spec.Effect
import org.rsmod.api.bosses.spec.ProjectileConfig
import org.rsmod.api.combat.commons.CombatEffects
import org.rsmod.api.combat.commons.player.finishNpcHit
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.vars.setActiveMoveSpeed
import org.rsmod.api.player.vars.varMoveSpeed
import org.rsmod.api.route.RouteFactory
import org.rsmod.api.route.walkTo
import org.rsmod.api.script.onEvent
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.entity.npc.NpcUid
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.hit.HitType
import org.rsmod.game.movement.MoveSpeed
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.flag.CollisionFlag

class TormentedDemon
@Inject
constructor(deps: BossDeps, private val routeFactory: RouteFactory) : BossPluginScript(deps) {

    private val demonTypeIds: Set<Int> by lazy {
        setOf("npc.tormented_demon_1".asRSCM(RSCMType.NPC), "npc.tormented_demon_2".asRSCM(RSCMType.NPC))
    }

    private val defencelessModelByType: Map<Int, Int> by lazy {
        mapOf(
            "npc.tormented_demon_1".asRSCM(RSCMType.NPC) to DEFENCELESS_MODEL_1,
            "npc.tormented_demon_2".asRSCM(RSCMType.NPC) to DEFENCELESS_MODEL_2,
        )
    }

    private val fights = mutableMapOf<NpcUid, TdFight>()

    private fun fightFor(npc: Npc): TdFight = fights.getOrPut(npc.uid) { TdFight() }

    private fun markFightStarted(fight: TdFight) {
        if (fight.defencelessCycleStart == 0) fight.defencelessCycleStart = deps.mapClock.cycle
    }

    override fun ScriptContext.startup() {
        BossCombat.register(this, spec, deps, onModifyHit = { onDemonHit(npc, hit) })

        for (typeId in demonTypeIds) {
            onEvent<NpcStateEvents.Spawn>(typeId) {
                npc.vars["varn.td_shield_up"] = 1
                initializeOverheadPrayer(npc)
            }
        }
        onEvent<NpcStateEvents.Respawn> {
            if (npc.id in demonTypeIds) {
                fights.remove(npc.uid)
                npc.vars["varn.td_shield_up"] = 1
                npc.vars["varn.guaranteed_hit"] = 0
                if (DEFENCELESS_MODEL_SWAP_ENABLED) npc.resetBodyModel()
                initializeOverheadPrayer(npc)
            }
        }
        onEvent<NpcStateEvents.Delete> {
            if (npc.id in demonTypeIds) fights.remove(npc.uid)
        }

        deps.extensionRegistry.register("td.post_attack") { _, npc, _, _ -> postAttack(npc) }
        deps.extensionRegistry.register("td.fire_bomb") { _, npc, target, _ -> fireBomb(npc, target) }
    }

    override val spec =
        boss("npc.tormented_demon_1", "npc.tormented_demon_2") {
            stats(
                attackRate = TormentedDemonMechanics.SOLO_ATTACK_RATE,
                aggressionRadius = AGGRO_RANGE,
            )

            val melee =
                ability("melee") {
                    anim("seq.luc2_undead_demon_melee")
                    spotanim("spotanim.luc2_undead_demon_melee_spot")
                    hit {
                        damage(0..MELEE_MAX_HIT).roll()
                        type(Melee)
                    }
                    include(external("td.post_attack"))
                }

            val ranged =
                ability("ranged") {
                    anim("seq.luc2_undead_demon_spare_ribs")
                    projectile(
                        spotanim = "spotanim.luc2_rib_bone_shard_projectile",
                        config =
                            ProjectileConfig(
                                startHeight = 400,
                                endHeight = 120,
                                angle = 10,
                                progress = 200,
                            ),
                        hit = Effect.Hit(damage = Roll(0..RANGED_MAX_HIT), type = Ranged),
                    )
                    include(external("td.post_attack"))
                }

            val magic =
                ability("magic") {
                    anim("seq.luc2_undead_demon_firey_balls")
                    projectile(
                        spotanim = "spotanim.luc2_undead_demon_fireball_proj",
                        config =
                            ProjectileConfig(
                                startHeight = 344,
                                endHeight = 120,
                                angle = 0,
                                progress = 160,
                            ),
                        hit = Effect.Hit(damage = Roll(0..MAGIC_MAX_HIT), type = Magic),
                    )
                    include(external("td.post_attack"))
                }

            val fireBomb =
                ability("fire_bomb") {
                    anim("seq.luc2_undead_demon_explosion_fire")
                    include(external("td.fire_bomb"))
                }

            phase(PHASE_MELEE) {
                forceEvery(FIRE_BOMB_PERIOD, fireBomb)
                weightedSelectorRandom {
                    +random(melee, weight = 1, requires = WithinMeleeRange)
                    +random(ranged, weight = 1, requires = Condition.Not(WithinMeleeRange))
                    +random(magic, weight = 1, requires = Condition.Not(WithinMeleeRange))
                }
            }
            phase(PHASE_RANGED) {
                forceEvery(FIRE_BOMB_PERIOD, fireBomb)
                weightedSelectorRandom { +random(ranged, weight = 1) }
            }
            phase(PHASE_MAGIC) {
                forceEvery(FIRE_BOMB_PERIOD, fireBomb)
                weightedSelectorRandom { +random(magic, weight = 1) }
            }
        }

    private fun fireBomb(npc: Npc, target: Player) {
        if (!target.isValidTarget()) return
        val fight = fightFor(npc)
        markFightStarted(fight)

        val primaryTile = target.coords
        val secondaryTile = randomAdjacentWalkableTile(primaryTile) ?: primaryTile
        val targetUid = target.uid

        CombatEffects.freeze(target, FIRE_BOMB_BIND_TICKS)
        target.spotanim("spotanim.entangle_impact", height = 124)
        val restoreSpeed = target.varMoveSpeed
        target.setActiveMoveSpeed(MoveSpeed.Walk)
        deps.worldQueues.add(FIRE_BOMB_BIND_TICKS) {
            if (target.isValidTarget()) target.setActiveMoveSpeed(restoreSpeed)
        }

        for (landingTile in listOf(primaryTile, secondaryTile)) {
            deps.worldRepo.spotanimMap(
                SpotanimType(TELEGRAPH_SHADOW),
                landingTile,
                delay = FIRE_BOMB_TELEGRAPH_DELAY,
            )
            deps.lob(
                npc = npc,
                targetTile = landingTile,
                targetUid = targetUid,
                spotanim = PROJ_FIRE_BOMB,
                startHeight = 344,
                endHeight = 0,
                delay = FIRE_BOMB_PROJ_DELAY,
                travel = FIRE_BOMB_PROJ_TRAVEL,
                curve = FIRE_BOMB_PROJ_ANGLE,
                landTicks = FIRE_BOMB_LAND_TICKS,
                landGfx = SPOT_EXPLOSION,
                progress = FIRE_BOMB_PROJ_PROGRESS,
            ) { player ->
                if (player.coords == landingTile) {
                    val damage = FIRE_BOMB_MIN_DAMAGE + deps.random.of(FIRE_BOMB_DAMAGE_SPREAD)
                    player.finishNpcHit(npc, 1, HitType.Magic, damage, deps.playerHitModifier)
                }
            }
        }

        fight.defenceless = false
        fight.defencelessCycleStart = deps.mapClock.cycle
        if (DEFENCELESS_MODEL_SWAP_ENABLED) npc.resetBodyModel()
        dropShield(npc, fight)

        val encounter = deps.encounter(npc)
        val otherStyles = STYLE_PHASES.filter { it != encounter.currentPhaseName }
        val nextStyle = otherStyles[deps.random.of(otherStyles.size)]
        encounter.transitionTo(nextStyle, deps.mapClock.cycle)
        applyStyleRange(npc, nextStyle)
        if (nextStyle != PHASE_MELEE) {
            retreatFromMelee(npc, target)
        }
    }

    private fun applyStyleRange(npc: Npc, style: String) {
        npc.apRangeOverride = if (style == PHASE_MELEE) null else RANGED_MAGIC_AP_RANGE
    }

    private fun retreatFromMelee(npc: Npc, target: Player) {
        if (!npc.isWithinDistance(target, MELEE_RANGE_TILES)) return
        val dx = retreatOffset(npc.coords.x - target.coords.x)
        val dz = retreatOffset(npc.coords.z - target.coords.z)
        val dest = npc.coords.translate(dx * RETREAT_DISTANCE, dz * RETREAT_DISTANCE)
        npc.walkTo(routeFactory, dest)
    }

    private fun retreatOffset(delta: Int): Int =
        when {
            delta > 0 -> 1
            delta < 0 -> -1
            else -> if (deps.random.of(2) == 0) -1 else 1
        }

    private fun randomAdjacentWalkableTile(center: CoordGrid): CoordGrid? {
        val candidates =
            ADJACENT_OFFSETS.map { (dx, dz) -> center.translate(dx, dz) }.filter(::isWalkable)
        if (candidates.isEmpty()) return null
        return candidates[deps.random.of(candidates.size)]
    }

    private fun isWalkable(coord: CoordGrid): Boolean {
        val flags = deps.collision[coord.x, coord.z, coord.level]
        return flags and (CollisionFlag.BLOCK_WALK or CollisionFlag.LOC) == 0
    }

    private fun postAttack(npc: Npc) {
        val fight = fightFor(npc)
        markFightStarted(fight)
        updateDefenceless(npc, fight)
    }

    private fun dropShield(npc: Npc, fight: TdFight) {
        npc.vars["varn.td_shield_up"] = 0
        npc.spotanim("spotanim.luc2_undead_demon_explosion_fire_spot", slot = SHIELD_SPOT_SLOT)
        updateStyleImmunity(npc, null)
        updateGuaranteedHit(npc, fight)
    }

    private fun updateGuaranteedHit(npc: Npc, fight: TdFight) {
        val guaranteed = fight.defenceless || npc.vars["varn.td_shield_up"] == 0
        npc.vars["varn.guaranteed_hit"] = if (guaranteed) 1 else 0
    }

    private fun initializeOverheadPrayer(npc: Npc) {
        val style = HitType.Melee
        fightFor(npc).overheadStyle = style
        npc.vars["varn.td_overhead_style"] = overheadStyleCode(style)
        updateStyleImmunity(npc, style)
        val index = headIconIndex(style)
        if (index != null) {
            npc.setHeadIcon(HEADICON_SLOT, HEADICON_GRAPHIC, index)
        } else {
            npc.clearHeadIcon(HEADICON_SLOT)
        }
    }

    private fun updateStyleImmunity(npc: Npc, activeStyle: HitType?) {
        npc.vars["varn.immune_melee"] = if (activeStyle == HitType.Melee) 1 else 0
        npc.vars["varn.immune_ranged"] = if (activeStyle == HitType.Ranged) 1 else 0
        npc.vars["varn.immune_magic"] = if (activeStyle == HitType.Magic) 1 else 0
    }

    private fun armStyleImmunityNextTick(npc: Npc, activeStyle: HitType?) {
        val uid = npc.uid
        deps.worldQueues.add(1) {
            if (npc.uid == uid) {
                updateStyleImmunity(npc, activeStyle)
            }
        }
    }

    private fun updateDefenceless(npc: Npc, fight: TdFight) {
        if (fight.defenceless || fight.defencelessCycleStart == 0) return
        if (deps.mapClock.cycle - fight.defencelessCycleStart >= DEFENCELESS_DELAY_TICKS) {
            fight.defenceless = true
            updateGuaranteedHit(npc, fight)
            npc.spotanim("spotanim.luc2_undead_accuracy_debuff", slot = DEFENCELESS_SPOT_SLOT)
            if (DEFENCELESS_MODEL_SWAP_ENABLED) {
                defencelessModelByType[npc.id]?.let { npc.setBodyModel(it) }
            }
        }
    }

    private fun onDemonHit(npc: Npc, hit: HitBuilder) {
        if (!hit.isFromPlayer) return
        val fight = fightFor(npc)
        markFightStarted(fight)
        val style = hit.type
        val shieldWasUp = npc.vars["varn.td_shield_up"] == 1

        if (!fight.firstHitTaken) {
            fight.firstHitTaken = true
            dropShield(npc, fight)
        } else if (!shieldWasUp) {
            npc.vars["varn.td_shield_up"] = 1
            npc.spotanim("spotanim.luc2_undead_demon_shield_restore_spot", slot = SHIELD_SPOT_SLOT)
            armStyleImmunityNextTick(npc, fight.overheadStyle)
            updateGuaranteedHit(npc, fight)
        } else {
            npc.spotanim("spotanim.luc2_undead_demon_shield_spot", slot = SHIELD_SPOT_SLOT)
        }

        val blockedByOverhead = shieldWasUp && fight.overheadStyle == style
        fight.damageSinceSwap += if (blockedByOverhead) 0 else hit.damage
        fight.lastStyleHit = style

        if (TormentedDemonMechanics.shouldSwapPrayer(fight.damageSinceSwap)) {
            fight.overheadStyle = style
            fight.damageSinceSwap = 0
            npc.vars["varn.td_overhead_style"] = overheadStyleCode(style)
            val index = headIconIndex(style)
            if (index != null) {
                npc.setHeadIcon(HEADICON_SLOT, HEADICON_GRAPHIC, index)
            } else {
                npc.clearHeadIcon(HEADICON_SLOT)
            }
            armStyleImmunityNextTick(npc, style)
            deps.suppressAttacks(npc, PRAYER_STALL_TICKS)
        }
    }

    private fun headIconIndex(style: HitType): Int? =
        when (style) {
            HitType.Melee -> 0
            HitType.Ranged -> 1
            HitType.Magic -> 2
            else -> null
        }

    private fun overheadStyleCode(style: HitType): Int =
        when (style) {
            HitType.Melee -> 1
            HitType.Ranged -> 2
            HitType.Magic -> 3
            else -> 0
        }

    private class TdFight {
        var firstHitTaken: Boolean = false
        var overheadStyle: HitType? = null
        var lastStyleHit: HitType? = null
        var damageSinceSwap: Int = 0
        var defencelessCycleStart: Int = 0
        var defenceless: Boolean = false
    }

    private companion object {
        private const val PHASE_MELEE = "style_melee"
        private const val PHASE_RANGED = "style_ranged"
        private const val PHASE_MAGIC = "style_magic"
        private val STYLE_PHASES = listOf(PHASE_MELEE, PHASE_RANGED, PHASE_MAGIC)
        private const val RANGED_MAGIC_AP_RANGE = 7
        private const val MELEE_RANGE_TILES = 1
        private const val RETREAT_DISTANCE = 3

        private const val AGGRO_RANGE = 8

        private const val PRAYER_STALL_TICKS = 6
        private const val DEFENCELESS_DELAY_TICKS = 30
        private const val DEFENCELESS_MODEL_1 = 55475
        private const val DEFENCELESS_MODEL_2 = 55474

        /**
         * [Npc.setBodyModel]/[Npc.resetBodyModel] (RSProt's `setBodyCustomisation` extended info
         * block) crashes both the real client and rsprox's decoder on revision 240 with an
         * `IndexOutOfBoundsException` in `decodeBodyCustomisationV3`. Keep the call sites in place
         * but disabled until this is fixed upstream.
         */
        private const val DEFENCELESS_MODEL_SWAP_ENABLED = false

        private const val HEADICON_SLOT = 0
        private const val HEADICON_GRAPHIC = 440

        private const val SHIELD_SPOT_SLOT = 4
        private const val DEFENCELESS_SPOT_SLOT = 5

        private const val FIRE_BOMB_PERIOD = 60
        private const val FIRE_BOMB_BIND_TICKS = 2

        private const val FIRE_BOMB_LAND_TICKS = 4
        private const val FIRE_BOMB_MIN_DAMAGE = 20
        private const val FIRE_BOMB_DAMAGE_SPREAD = 6

        private const val FIRE_BOMB_PROJ_DELAY = 45
        private const val FIRE_BOMB_PROJ_TRAVEL = 70
        private const val FIRE_BOMB_PROJ_ANGLE = 30
        private const val FIRE_BOMB_PROJ_PROGRESS = 128

        private const val MELEE_MAX_HIT = 30
        private const val RANGED_MAX_HIT = 30
        private const val MAGIC_MAX_HIT = 30

        private const val PROJ_FIRE_BOMB = 2855
        private const val SPOT_EXPLOSION = 2856
        private const val TELEGRAPH_SHADOW = 1446
        private const val FIRE_BOMB_TELEGRAPH_DELAY = 30

        private val ADJACENT_OFFSETS =
            listOf(-1 to -1, 0 to -1, 1 to -1, -1 to 0, 1 to 0, -1 to 1, 0 to 1, 1 to 1)
    }
}

internal object TormentedDemonMechanics {
    const val SOLO_ATTACK_RATE: Int = 4
    const val PRAYER_SWAP_DAMAGE: Int = 150

    fun shouldSwapPrayer(damageSinceSwap: Int): Boolean = damageSinceSwap >= PRAYER_SWAP_DAMAGE
}
