package org.rsmod.api.bosses.runtime

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ProjAnimType
import dev.openrune.types.aconverted.SpotanimType
import org.rsmod.api.bosses.spec.*
import org.rsmod.api.bosses.spec.HitType as BossHitType
import org.rsmod.api.combat.commons.CombatEffects
import org.rsmod.api.combat.commons.DragonfireProtection
import org.rsmod.api.combat.commons.player.finishNpcHit
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.game.proj.ProjAnim
import org.rsmod.map.CoordGrid

class EffectInterpreter(
    private val npc: Npc,
    private val target: Player,
    private val spec: BossSpec,
    private val encounter: BossEncounter,
    private val deps: BossDeps,
) {

    suspend fun run(access: StandardNpcAccess, effect: Effect) {
        when (effect) {
            is Effect.Anim -> access.anim(effect.seq)
            is Effect.Say -> access.say(effect.text)
            is Effect.Sound -> {
                deps.worldRepo.soundArea(npc.coords, effect.synth, radius = effect.radius)
            }
            is Effect.Spotanim -> access.spotanim(effect.spot, effect.delay, effect.height)
            is Effect.MapSpotanim -> {
                val coord = resolveTile(effect.at)
                val spot = SpotanimType(effect.spot.asRSCM(RSCMType.SPOTANIM))
                deps.worldRepo.spotanimMap(spot, coord, effect.height, effect.delay)
            }
            is Effect.Broadcast -> {
                val radius = effect.radius
                for (player in deps.playerList) {
                    if (player.coords.chebyshevDistance(npc.coords) <= radius) {
                        player.mes(effect.text)
                    }
                }
            }
            is Effect.Delay -> access.delay(effect.ticks)
            is Effect.NoOp -> {}

            is Effect.Hit -> applyHit(effect)
            is Effect.Projectile -> fireProjectile(access, effect)
            is Effect.TileAoE -> applyTileAoE(effect)
            is Effect.Debris -> applyDebris(effect)
            is Effect.Summon -> summon(effect)
            is Effect.Poison -> applyPoison(effect)
            is Effect.Freeze -> applyFreeze(effect)
            is Effect.StatDrain -> applyStatDrain(effect)
            is Effect.Transmog -> {
                val npcType = ServerCacheManager.getNpc(effect.to.asRSCM(RSCMType.NPC))
                if (npcType != null) {
                    access.changeType(npcType, effect.durationTicks)
                }
            }

            is Effect.Run -> {
                val ability = spec.abilities[effect.ability] ?: return
                run(access, ability)
            }
            is Effect.TransitionTo -> {
                encounter.transitionTo(effect.phase, deps.mapClock.cycle)
            }
            is Effect.External -> {
                deps.extensionRegistry.invoke(effect.handler, access, npc, target, effect.params)
            }

            is Effect.Sequence -> {
                for (e in effect.effects) run(access, e)
            }
            is Effect.Parallel -> {
                for (e in effect.effects) run(access, e)
            }
            is Effect.Choose -> {
                val abilityName = encounter.selectAbility(effect.selector, deps.mapClock.cycle) ?: return
                val branch = effect.branches[abilityName] ?: return
                run(access, branch)
            }
            is Effect.Repeat -> {
                repeat(effect.times) { run(access, effect.effect) }
            }
            is Effect.Whenever -> {
                if (encounter.evaluate(effect.condition, target)) {
                    run(access, effect.then)
                } else {
                    run(access, effect.otherwise)
                }
            }
            is Effect.OnEach -> {
                val targets = resolveMulti(effect.targets)
                for (t in targets) {
                    val subInterpreter = EffectInterpreter(npc, t, spec, encounter, deps)
                    subInterpreter.run(access, effect.effect)
                }
            }
        }
    }

    private fun applyHit(hit: Effect.Hit) {
        val targets = when (val t = hit.target) {
            is TargetExpr.Single -> listOfNotNull(resolveSingle(t))
            is TargetExpr.Multi -> resolveMulti(t)
            else -> listOf(target)
        }
        val delay = hit.delay.coerceAtLeast(1)
        for (t in targets) {
            var damage = evaluateDamage(hit.damage, hit.type, t)
            dragonfireType(hit.type)?.let { dfType ->
                val cap = DragonfireProtection.resolveMaxHit(t, dfType, damageMax(hit.damage, hit.type, t))
                damage = if (cap <= 0) 0 else deps.random.of(cap + 1)
            }
            if (damage > 0) {
                hit.spotanim?.let { t.spotanim(it, height = hit.spotanimHeight) }
            }
            t.finishNpcHit(npc, delay, hit.type.toEngine(), damage, deps.playerHitModifier)
        }
    }

    private fun fireProjectile(access: StandardNpcAccess, proj: Effect.Projectile) {
        val t = resolveSingle(proj.target as? TargetExpr.Single ?: TargetExpr.CurrentTarget) ?: return
        val spotId = proj.spotanim.asRSCM(RSCMType.SPOTANIM)

        val type = if (proj.travel != null) {
            ServerCacheManager.getProjectile(proj.travel.asRSCM(RSCMType.PROJANIM))
                ?: error("Projectile not found: ${proj.travel}")
        } else {
            val cfg = proj.config ?: ProjectileConfig()
            ProjAnimType(
                startHeight = cfg.startHeight,
                endHeight = cfg.endHeight,
                delay = cfg.startDelay,
                angle = cfg.angle,
                lengthAdjustment = cfg.travelTime,
                progress = cfg.progress,
                stepMultiplier = cfg.stepMultiplier,
            )
        }

        val projAnim = ProjAnim.fromNpcToPlayer(npc, t, spotId, type)
        deps.worldRepo.projAnim(projAnim)

        proj.hit?.let { hit ->
            var damage = evaluateDamage(hit.damage, hit.type, t)
            dragonfireType(hit.type)?.let { dfType ->
                val cap = DragonfireProtection.resolveMaxHit(t, dfType, damageMax(hit.damage, hit.type, t))
                damage = if (cap <= 0) 0 else deps.random.of(cap + 1)
            }
            if (damage > 0) {
                hit.spotanim?.let {
                    t.spotanim(it, delay = projAnim.clientCycles, height = hit.spotanimHeight)
                }
            }
            t.finishNpcHit(npc, projAnim.serverCycles, hit.type.toEngine(), damage, deps.playerHitModifier)
        }
    }

    private fun applyTileAoE(aoe: Effect.TileAoE) {
        val center = resolveTile(aoe.center)
        val radius = aoe.radius
        val targets = deps.playerList.filter {
            it.coords.chebyshevDistance(center) <= radius
        }
        for (t in targets) {
            val damage = evaluateDamage(aoe.damage, aoe.type, t)
            t.finishNpcHit(npc, 0, aoe.type.toEngine(), damage, deps.playerHitModifier)
        }
    }

    private fun applyDebris(effect: Effect.Debris) {
        val center = resolveTile(effect.center)
        val telegraphSpot = SpotanimType(effect.telegraph.asRSCM(RSCMType.SPOTANIM))

        val playersInRange =
            deps.playerList.filter { it.coords.chebyshevDistance(center) <= effect.targetRadius }
        val total = effect.count.first + deps.random.of(effect.count.last - effect.count.first + 1)
        val scatterCount = (total - playersInRange.size).coerceAtLeast(0)
        val scatterDiameter = effect.scatterRadius * 2 + 1
        val tiles =
            playersInRange.map { it.coords } +
                List(scatterCount) {
                    center.translate(
                        deps.random.of(scatterDiameter) - effect.scatterRadius,
                        deps.random.of(scatterDiameter) - effect.scatterRadius,
                    )
                }
        val tileSet = tiles.toSet()

        tiles.forEach { deps.worldRepo.spotanimMap(telegraphSpot, it) }

        deps.worldQueues.add(effect.windup) {
            effect.impact?.let { impact ->
                val impactSpot = SpotanimType(impact.asRSCM(RSCMType.SPOTANIM))
                tileSet.forEach { deps.worldRepo.spotanimMap(impactSpot, it) }
            }
            val landing =
                deps.playerList.filter { it.coords.chebyshevDistance(center) <= effect.targetRadius }
            for (player in landing) {
                if (player.hitpoints > 0 && player.coords in tileSet) {
                    val damage = evaluateDamage(effect.damage, effect.type, player)
                    player.finishNpcHit(npc, 1, effect.type.toEngine(), damage, deps.playerHitModifier)
                }
            }
        }
    }

    private fun summon(summon: Effect.Summon) {
        val npcTypeId = summon.npc.asRSCM(RSCMType.NPC)
        val npcType = ServerCacheManager.getNpc(npcTypeId) ?: return
        val center = resolveTile(summon.centeredOn)
        val radius = summon.radius
        val mode = summon.mode ?: npcType.defaultMode

        // Only consider tiles that are walkable
        val spawnTiles =
            buildList {
                    for (dx in -radius..radius) {
                        for (dz in -radius..radius) {
                            val origin = center.translate(dx, dz)
                            if (canStand(origin, npcType.size)) {
                                add(origin)
                            }
                        }
                    }
                }
                .toMutableList()

        repeat(summon.count) {
            val spawnCoord =
                if (spawnTiles.isNotEmpty()) {
                    spawnTiles.removeAt(deps.random.of(spawnTiles.size))
                } else {
                    center
                }
            val spawned = Npc(npcType, spawnCoord)
            spawned.mode = mode
            deps.npcRepo.add(spawned, 100)
        }
    }

    /** Whether an npc of [size] can stand at [origin]. */
    private fun canStand(origin: CoordGrid, size: Int): Boolean {
        for (dx in 0 until size) {
            for (dz in 0 until size) {
                if (deps.collision.isWalkBlocked(origin.translate(dx, dz))) {
                    return false
                }
            }
        }
        return true
    }

    private fun applyPoison(effect: Effect.Poison) {
        if (deps.random.of(effect.outOf) < effect.chance) {
            CombatEffects.poison(target, effect.damage)
        }
    }

    private fun applyFreeze(effect: Effect.Freeze) {
        if (deps.random.of(effect.outOf) < effect.chance) {
            CombatEffects.freeze(target, effect.ticks)
        }
    }

    private fun applyStatDrain(effect: Effect.StatDrain) {
        for (entry in effect.entries) {
            if (deps.random.of(entry.outOf) < entry.chance) {
                CombatEffects.statDrain(target, listOf(entry.stat), entry.amount)
            }
        }
    }

    private fun resolveSingle(expr: TargetExpr.Single): Player? {
        return when (expr) {
            is TargetExpr.CurrentTarget -> target
            is TargetExpr.Self -> null
            is TargetExpr.CurrentTargetTile -> null
            is TargetExpr.HighestDamageDealer -> target
            is TargetExpr.LowestPrayer -> target
            is TargetExpr.RandomNearby -> target
        }
    }

    private fun resolveTile(expr: TargetExpr): org.rsmod.map.CoordGrid {
        return when (expr) {
            is TargetExpr.CurrentTarget -> target.coords
            is TargetExpr.CurrentTargetTile -> target.coords
            is TargetExpr.Self -> npc.coords
            else -> npc.coords
        }
    }

    private fun resolveMulti(expr: TargetExpr): List<Player> {
        return when (expr) {
            is TargetExpr.AllInRadius -> {
                if (expr.of is TargetExpr.Self) {
                    deps.playerList.filter {
                        it.coords.level == npc.coords.level && npc.isWithinDistance(it, expr.radius)
                    }
                } else {
                    val center = resolveTile(expr.of)
                    deps.playerList.filter {
                        it.coords.level == center.level &&
                            it.coords.chebyshevDistance(center) <= expr.radius
                    }
                }
            }
            is TargetExpr.TopN -> listOf(target)
            is TargetExpr.Single -> listOfNotNull(resolveSingle(expr))
            else -> listOf(target)
        }
    }

    private fun damageMax(expr: DamageExpr, hitType: BossHitType, t: Player): Int =
        when (expr) {
            is DamageExpr.Roll -> if (expr.range.isEmpty()) 0 else expr.range.last
            is DamageExpr.Fixed -> expr.value
            is DamageExpr.NpcMaxHit -> npcFormulaMaxHit(expr, hitType, t)
            else -> evaluateDamage(expr, hitType, t)
        }

    private fun npcFormulaMaxHit(expr: DamageExpr.NpcMaxHit, hitType: BossHitType, t: Player): Int {
        val raw =
            when (hitType) {
                BossHitType.Ranged -> deps.maxHit.getRangedMaxHit(npc, t)
                BossHitType.Magic,
                BossHitType.Dragonfire,
                BossHitType.DragonfireMetal,
                BossHitType.WyvernIce -> deps.maxHit.getMagicMaxHit(npc, t)
                BossHitType.Melee,
                BossHitType.Typeless -> deps.maxHit.getMeleeMaxHit(npc, t, expr.meleeAttackType)
            }
        val scaled = if (expr.scale == 1.0) raw else (raw * expr.scale).toInt()
        return scaled.coerceAtLeast(0)
    }

    private fun dragonfireType(t: BossHitType): DragonfireProtection.DragonfireType? =
        when (t) {
            BossHitType.Dragonfire -> DragonfireProtection.DragonfireType.Chromatic
            BossHitType.DragonfireMetal -> DragonfireProtection.DragonfireType.Metal
            BossHitType.WyvernIce -> DragonfireProtection.DragonfireType.WyvernIce
            else -> null
        }

    private fun evaluateDamage(expr: DamageExpr, hitType: BossHitType, t: Player): Int {
        return when (expr) {
            is DamageExpr.Fixed -> expr.value
            is DamageExpr.Roll ->
                if (expr.range.isEmpty()) 0
                else {
                    expr.range.first + deps.random.of(expr.range.last - expr.range.first + 1)
                }
            is DamageExpr.Accuracy -> {
                val landed = rollAccuracy(hitType, t, expr.meleeAttackType)
                evaluateDamage(if (landed) expr.on else expr.miss, hitType, t)
            }
            is DamageExpr.NpcMaxHit -> {
                val max = npcFormulaMaxHit(expr, hitType, t)
                val lo = expr.minHit.coerceIn(0, max)
                if (max <= 0) 0 else lo + deps.random.of(max - lo + 1)
            }
            is DamageExpr.PercentOfTargetHp -> (t.hitpoints * expr.fraction).toInt()
            is DamageExpr.Min -> minOf(evaluateDamage(expr.a, hitType, t), evaluateDamage(expr.b, hitType, t))
            is DamageExpr.Max -> maxOf(evaluateDamage(expr.a, hitType, t), evaluateDamage(expr.b, hitType, t))
        }
    }

    private fun rollAccuracy(
        hitType: BossHitType,
        t: Player,
        meleeAttackType: MeleeAttackType? = null,
    ): Boolean =
        when (hitType) {
            BossHitType.Melee -> deps.accuracy.rollMeleeAccuracy(npc, t, meleeAttackType, deps.random)
            BossHitType.Ranged -> deps.accuracy.rollRangedAccuracy(npc, t, deps.random)
            BossHitType.Magic,
            BossHitType.Dragonfire,
            BossHitType.DragonfireMetal,
            BossHitType.WyvernIce -> deps.accuracy.rollMagicAccuracy(npc, t, deps.random)
            BossHitType.Typeless -> true
        }

    private fun BossHitType.toEngine(): HitType = when (this) {
        BossHitType.Melee -> HitType.Melee
        BossHitType.Ranged -> HitType.Ranged
        BossHitType.Magic -> HitType.Magic
        BossHitType.Dragonfire -> HitType.Magic
        BossHitType.DragonfireMetal -> HitType.Magic
        BossHitType.WyvernIce -> HitType.Magic
        BossHitType.Typeless -> HitType.Typeless
    }
}
