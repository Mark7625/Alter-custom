package org.rsmod.content.bosses.spindel

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import kotlin.math.abs
import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossPluginScript
import org.rsmod.api.bosses.runtime.EffectInterpreter
import org.rsmod.api.bosses.runtime.bossProjectile
import org.rsmod.api.bosses.runtime.encounter
import org.rsmod.api.bosses.runtime.repeatTick
import org.rsmod.api.bosses.spec.Condition
import org.rsmod.api.bosses.spec.Effect
import org.rsmod.api.bosses.spec.ProjectileConfig
import org.rsmod.api.combat.commons.CombatEffects
import org.rsmod.api.combat.commons.player.finishNpcHit
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.route.RouteFactory
import org.rsmod.api.route.walkTo
import org.rsmod.api.script.onEvent
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.movement.MoveSpeed
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.module.PluginModule
import org.rsmod.plugin.scripts.ScriptContext

data class LairConfig(
    val key: String,
    val bossNpc: String,
    val spiderlingNpc: String,
    val level: Int,
    val minX: Int,
    val maxX: Int,
    val minZ: Int,
    val maxZ: Int,
) {
    fun contains(coord: CoordGrid, margin: Int = 0): Boolean =
        coord.level == level &&
            coord.x in (minX - margin)..(maxX + margin) &&
            coord.z in (minZ - margin)..(maxZ + margin)

    fun clampX(x: Int): Int = x.coerceIn(minX, maxX)

    fun clampZ(z: Int): Int = z.coerceIn(minZ, maxZ)
}

val SPINDEL_LAIR =
    LairConfig("spindel", "npc.venenatis_singles", "npc.spindel_spiderling", 2, 1610, 1650, 11525, 11570)

val VENENATIS_LAIR =
    LairConfig("venenatis", "npc.venenatis", "npc.venenatis_spiderling", 2, 3405, 3440, 10180, 10220)

class Venenatis
@Inject
constructor(
    deps: BossDeps,
    private val routeFactory: RouteFactory,
    private val aiPlayerInteractions: AiPlayerInteractions,
    private val locRepo: LocRepository,
) : BossPluginScript(deps) {

    private val lairsById: Map<Int, LairConfig> by lazy {
        listOf(SPINDEL_LAIR, VENENATIS_LAIR).associateBy { it.bossNpc.asRSCM(RSCMType.NPC) }
    }

    private fun lairFor(npc: Npc): LairConfig = lairsById.getValue(npc.id)

    override val spec =
        boss(SPINDEL_LAIR.bossNpc, VENENATIS_LAIR.bossNpc) {
            stats(attackRate = ATTACK_RATE, aggressionRadius = AGGRO_RANGE)

            val melee =
                ability("melee") {
                    anim("seq.npc_venenatis_melee_01")
                    hit {
                        damage(0..MELEE_MAX_HIT).roll()
                        type(Melee)
                    }
                    include(external("venenatis.post_attack"))
                }

            val rangedAttack =
                ability("ranged_attack") {
                    anim("seq.npc_venenatis_ranged_01")
                    include(
                        onEach(
                            AllInRadius(radius = ARENA_ATTACK_RADIUS),
                            Effect.Projectile(
                                spotanim = "spotanim.fx_venenatis_ranged_projectile",
                                config = RANGED_PROJECTILE_CONFIG,
                                hit =
                                    Effect.Hit(
                                        damage = Roll(0..RANGED_MAX_HIT),
                                        type = Ranged,
                                        spotanim = "spotanim.fx_venenatis_ranged_impact",
                                        spotanimHeight = RANGED_IMPACT_HEIGHT,
                                    ),
                            ),
                        )
                    )
                    include(external("venenatis.post_attack"))
                }

            val magicAttack =
                ability("magic_attack") {
                    anim("seq.npc_venenatis_magic_01")
                    include(
                        onEach(
                            AllInRadius(radius = ARENA_ATTACK_RADIUS),
                            Effect.Projectile(
                                spotanim = "spotanim.fx_venenatis_magic_projectile",
                                config = MAGIC_PROJECTILE_CONFIG,
                                hit =
                                    Effect.Hit(
                                        damage = Roll(0..MAGIC_MAX_HIT),
                                        type = Magic,
                                        spotanim = "spotanim.fx_venenatis_magic_impact",
                                        spotanimHeight = MAGIC_IMPACT_HEIGHT,
                                    ),
                            ),
                        )
                    )
                    include(external("venenatis.post_attack"))
                }

            phase(PHASE_RANGED) {
                weightedSelectorRandom {
                    +random(melee, weight = 1, requires = WithinMeleeRange)
                    +random(rangedAttack, weight = 1, requires = Condition.Not(WithinMeleeRange))
                }
            }
            phase(PHASE_MAGIC) {
                weightedSelectorRandom {
                    +random(melee, weight = 1, requires = WithinMeleeRange)
                    +random(magicAttack, weight = 1, requires = Condition.Not(WithinMeleeRange))
                }
            }
        }

    override fun ScriptContext.startup() {
        BossCombat.register(this, spec, deps)
        deps.extensionRegistry.register("venenatis.post_attack") { access, npc, target, _ ->
            onStyleAttackResolved(access, npc, target)
        }
        for (typeId in lairsById.keys) {
            onEvent<NpcStateEvents.Spawn>(typeId) { resetFightState(npc) }
        }
        onEvent<NpcStateEvents.Respawn> { if (npc.id in lairsById) resetFightState(npc) }
    }

    private fun resetFightState(npc: Npc) {
        npc.vars["varn.venenatis_attacks"] = 0
    }

    private suspend fun onStyleAttackResolved(access: StandardNpcAccess, npc: Npc, target: Player) {
        val encounter = deps.encounter(npc)
        val style = encounter.currentPhaseName
        val attacksBefore = npc.vars["varn.venenatis_attacks"]

        if (style == PHASE_RANGED && attacksBefore == 0) {
            summonSpiderlings(access, npc, target)
        }

        val attacks = attacksBefore + 1
        when (attacks) {
            BLOCK_SIZE -> {
                fleeFromTarget(npc)
                val nextStyle = if (style == PHASE_RANGED) PHASE_MAGIC else PHASE_RANGED
                encounter.transitionTo(nextStyle, deps.mapClock.cycle)
                npc.vars["varn.venenatis_attacks"] = 0
            }
            WEB_ATTACK -> {
                fleeFromTarget(npc)
                if (style == PHASE_MAGIC) deployStickyWeb(npc, target)
                npc.vars["varn.venenatis_attacks"] = attacks
            }
            else -> npc.vars["varn.venenatis_attacks"] = attacks
        }
    }

    private suspend fun summonSpiderlings(access: StandardNpcAccess, npc: Npc, target: Player) {
        val encounter = deps.encounter(npc)
        val interpreter = EffectInterpreter(npc, target, spec, encounter, deps)
        val effect =
            summon(
                npc = lairFor(npc).spiderlingNpc,
                count = SPIDERLING_COUNT,
                radius = SPIDERLING_SUMMON_RADIUS,
                centeredOn = Self,
            )
        interpreter.run(access, effect)
    }

    private fun fleeFromTarget(npc: Npc) {
        val lair = lairFor(npc)
        val dest = randomArenaTile(lair, npc.coords)
        npc.ignoreCombatInteractions = true
        npc.resetFaceEntity()
        npc.clearFacingLock()
        npc.walkTo(routeFactory, dest, speed = MoveSpeed.Run) {
            npc.ignoreCombatInteractions = false
            val next = pickArenaTarget(npc)
            if (next != null) {
                npc.apPlayer2(next, aiPlayerInteractions)
            }
        }
    }

    private fun pickArenaTarget(npc: Npc): Player? {
        val candidates =
            deps.playerList.filter {
                it.isValidTarget() &&
                    it.coords.level == npc.coords.level &&
                    it.coords.chebyshevDistance(npc.spawnCoords) <= npc.type.maxRange
            }
        if (candidates.isEmpty()) return null
        val inAttackRange =
            candidates.filter { it.coords.chebyshevDistance(npc.coords) <= npc.attackRange }
        val pool = inAttackRange.ifEmpty { candidates }
        return pool[deps.random.of(pool.size)]
    }

    private fun randomArenaTile(lair: LairConfig, from: CoordGrid): CoordGrid {
        repeat(FLEE_TILE_ATTEMPTS) {
            val dx = deps.random.of(FLEE_MAX_MOVE * 2 + 1) - FLEE_MAX_MOVE
            val dz = deps.random.of(FLEE_MAX_MOVE * 2 + 1) - FLEE_MAX_MOVE
            val x = lair.clampX(from.x + dx)
            val z = lair.clampZ(from.z + dz)
            val tile = CoordGrid(x, z, lair.level)
            if (from.chebyshevDistance(tile) in FLEE_MIN_MOVE..FLEE_MAX_MOVE) return tile
        }
        return from
    }

    private fun deployStickyWeb(npc: Npc, target: Player) {
        val centerTile = target.coords
        deps.bossProjectile(
            spotanim = "spotanim.fx_venenatis_web_projectile".asRSCM(RSCMType.SPOTANIM),
            src = npc.coords,
            target = centerTile,
            startHeight = WEB_PROJ_START_HEIGHT,
            endHeight = WEB_PROJ_END_HEIGHT,
            delay = WEB_PROJ_DELAY,
            travel = WEB_PROJ_TRAVEL,
            curve = WEB_PROJ_ANGLE,
        )
        deps.worldQueues.add(WEB_LAND_TICKS) { deployWebZone(npc, centerTile) }
    }

    private fun deployWebZone(npc: Npc, centerTile: CoordGrid) {
        val footprint = webFootprint(centerTile)
        val impactSpot = SpotanimType("spotanim.fx_venenatis_web_impact".asRSCM(RSCMType.SPOTANIM))
        for (tile in footprint) {
            deps.worldRepo.spotanimMap(impactSpot, tile.coord)
            locRepo.add(
                tile.coord,
                tile.locName,
                WEB_DURATION_TICKS,
                LocAngle[tile.rotation],
                LocShape.CentrepieceStraight,
            )
        }
        val tiles = footprint.mapTo(mutableSetOf()) { it.coord }
        deps.repeatTick(
            ticks = WEB_DURATION_TICKS,
            onTick = { _ ->
                if (!npc.isSlotAssigned) return@repeatTick false
                for (player in deps.playerList) {
                    if (player.hitpoints > 0 && player.coords in tiles) {
                        player.finishNpcHit(
                            npc,
                            1,
                            HitType.Typeless,
                            WEB_TICK_DAMAGE,
                            deps.playerHitModifier,
                        )
                        CombatEffects.statDrain(player, listOf("stat.prayer"), WEB_PRAYER_DRAIN)
                        player.runEnergy =
                            (player.runEnergy - WEB_RUN_ENERGY_DRAIN).coerceAtLeast(0)
                    }
                }
                true
            },
        )
    }

    private data class WebTile(val coord: CoordGrid, val locName: String, val rotation: Int)

    private fun webFootprint(center: CoordGrid): List<WebTile> {
        val tiles = mutableListOf<WebTile>()
        for (dx in -3..3) {
            for (dz in -3..3) {
                if (abs(dx) + abs(dz) > 4) continue
                val coord = center.translate(dx, dz)
                val (locName, rotation) =
                    when {
                        abs(dx) == 3 ->
                            "loc.wbr_venenatis_web_edge" to if (dx > 0) ROT_EAST else ROT_WEST
                        abs(dz) == 3 ->
                            "loc.wbr_venenatis_web_edge" to if (dz > 0) ROT_NORTH else ROT_SOUTH
                        abs(dx) == 2 && abs(dz) == 2 ->
                            "loc.wbr_venenatis_web_corner" to
                                when {
                                    dx > 0 && dz < 0 -> ROT_EAST
                                    dx < 0 && dz < 0 -> ROT_SOUTH
                                    dx < 0 && dz > 0 -> ROT_WEST
                                    else -> ROT_NORTH
                                }
                        else -> "loc.wbr_venenatis_web_centre" to ROT_EAST
                    }
                tiles += WebTile(coord, locName, rotation)
            }
        }
        return tiles
    }

    private companion object {

        private const val PHASE_RANGED = "ranged_style"
        private const val PHASE_MAGIC = "magic_style"

        private const val ATTACK_RATE = 4
        private const val AGGRO_RANGE = 8
        private const val BLOCK_SIZE = 8
        private const val WEB_ATTACK = 4

        private const val FLEE_MIN_MOVE = 4
        private const val FLEE_MAX_MOVE = 9
        private const val FLEE_TILE_ATTEMPTS = 8
        private const val ARENA_ATTACK_RADIUS = 15

        private const val SPIDERLING_COUNT = 2
        private const val SPIDERLING_SUMMON_RADIUS = 6

        private const val MELEE_MAX_HIT = 20
        private const val RANGED_MAX_HIT = 30
        private const val MAGIC_MAX_HIT = 30

        private const val RANGED_IMPACT_HEIGHT = 30
        private val RANGED_PROJECTILE_CONFIG =
            ProjectileConfig(
                startHeight = 150,
                endHeight = 90,
                startDelay = 25,
                travelTime = 0,
                angle = 14,
                progress = 48,
                stepMultiplier = 5,
            )

        private const val MAGIC_IMPACT_HEIGHT = 90
        private val MAGIC_PROJECTILE_CONFIG =
            ProjectileConfig(
                startHeight = 150,
                endHeight = 124,
                startDelay = 25,
                travelTime = 0,
                angle = 14,
                progress = 48,
                stepMultiplier = 5,
            )

        private const val ROT_EAST = 0
        private const val ROT_SOUTH = 1
        private const val ROT_WEST = 2
        private const val ROT_NORTH = 3
        private const val WEB_PROJ_START_HEIGHT = 150
        private const val WEB_PROJ_END_HEIGHT = 0
        private const val WEB_PROJ_DELAY = 0
        private const val WEB_PROJ_TRAVEL = 120
        private const val WEB_PROJ_ANGLE = 30
        private const val WEB_LAND_TICKS = 4
        private const val WEB_DURATION_TICKS = 39
        private const val WEB_TICK_DAMAGE = 3
        private const val WEB_PRAYER_DRAIN = 3
        private const val WEB_RUN_ENERGY_DRAIN = 100
    }
}

class VenenatisSpiderling
@Inject
constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec =
        boss(SPINDEL_LAIR.spiderlingNpc, VENENATIS_LAIR.spiderlingNpc) {
            stats(attackRate = 4, aggressionRadius = 1)
            val bite =
                ability("bite") {
                    anim("seq.small_spider_update_attack")
                    hit {
                        damage(0..1).roll()
                        type(Melee)
                    }
                    statDrain("stat.prayer", amount = 1)
                }
            phase("combat") { weightedSelectorRandom { +random(bite, weight = 1) } }
        }
}

class VenenatisModule : PluginModule() {
    override fun bind() {
        addSetBinding<NpcAttackValidateHook>(SpindelAttackValidateHook::class.java)
    }
}

internal class SpindelAttackValidateHook @Inject constructor() : NpcAttackValidateHook {
    private val spindelId by lazy { SPINDEL_LAIR.bossNpc.asRSCM(RSCMType.NPC) }

    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult =
        if (npc.id == spindelId) {
            NpcAttackValidateResult.BypassSingleWayPvnRestriction
        } else {
            NpcAttackValidateResult.Pass
        }
}
