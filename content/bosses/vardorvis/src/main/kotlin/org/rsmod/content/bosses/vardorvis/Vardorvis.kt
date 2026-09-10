package org.rsmod.content.bosses.vardorvis

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcMode
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import java.util.IdentityHashMap
import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossPluginScript
import org.rsmod.api.bosses.runtime.bossProjectile
import org.rsmod.api.bosses.runtime.suppressAttacks
import org.rsmod.api.combat.commons.CombatEffects
import org.rsmod.api.config.refs.done.hitmark_groups
import org.rsmod.api.npc.heal
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.events.PlayerHitEvents
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.lockOverheads
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onNpcHit
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.entity.util.EntityExactMove
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.game.map.Direction
import org.rsmod.game.map.collision.get
import org.rsmod.game.movement.MoveSpeed
import org.rsmod.map.CoordGrid
import org.rsmod.map.util.Translation
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.flag.CollisionFlag

class Vardorvis
@Inject
constructor(
    deps: BossDeps,
    private val npcList: NpcList,
    private val locRepo: LocRepository,
    private val aiPlayerInteractions: AiPlayerInteractions,
    private val strangle: VardorvisStrangle,
) : BossPluginScript(deps) {

    private val nextHeadGazeTick = IdentityHashMap<Npc, Int>()
    private val arenaBarrier = IdentityHashMap<Npc, List<LocInfo>>()
    private val lastStrangleTick = IdentityHashMap<Npc, Int>()

    override fun ScriptContext.startup() {
        BossCombat.register(this, spec, deps, onModifyHit = { scaleStatsToHp(npc) })
        registerHeadGaze()
        registerDash()

        val bossIds = spec.npcTypes.mapNotNullTo(mutableSetOf()) { it.npcId() }
        val vardorvisType = ServerCacheManager.getNpc("npc.vardorvis".asRSCM(RSCMType.NPC))

        onEvent<NpcStateEvents.Create> {
            if (npc.type.id in bossIds) {
                resetStats(npc)
                npc.defaultMoveSpeed = MoveSpeed.Run
                npc.anim(SPAWN_SEQ)
            }
        }
        onEvent<NpcStateEvents.Respawn> {
            if (npc.type.id in bossIds) {
                resetStats(npc)
                npc.defaultMoveSpeed = MoveSpeed.Run
                npc.anim(SPAWN_SEQ)
                dropArenaBarrier(npc)
            }
        }
        onEvent<NpcStateEvents.Delete> {
            if (npc.type.id in bossIds) {
                nextHeadGazeTick.remove(npc)
                lastStrangleTick.remove(npc)
                dropArenaBarrier(npc)
            }
        }

        if (vardorvisType != null) {
            onNpcHit(vardorvisType) {
                if (npc.hitpoints > 0) {
                    val firstHit = npc !in arenaBarrier
                    raiseArenaBarrier(npc)
                    if (firstHit) {
                        deps.worldQueues.add(AXE_FIRST_DELAY) { runAxeSet(npc, allowStrangle = false) }
                    }
                } else {
                    deps.worldQueues.add(BARRIER_DEATH_DELAY) { dropArenaBarrier(npc, animated = true) }
                }
            }
        }

        onEvent<PlayerHitEvents.Impact> { lifestealOnImpact(bossIds, this) }
    }

    private fun raiseArenaBarrier(npc: Npc) {
        if (npc in arenaBarrier) return
        val centre = npc.spawnCoords
        sealArenaExit(centre)
        val locs = mutableListOf<LocInfo>()
        for (dx in ARENA_WEST_DX..ARENA_EAST_DX) {
            for (dz in ARENA_SOUTH_DZ..ARENA_NORTH_DZ) {
                val onEdge =
                    dx == ARENA_WEST_DX ||
                        dx == ARENA_EAST_DX ||
                        dz == ARENA_SOUTH_DZ ||
                        dz == ARENA_NORTH_DZ
                if (!onEdge) continue
                val tile = centre.translate(dx, dz)
                val variant = barrierVariant(dx, dz)
                val tendril = ARENA_BARRIER_LOCS[variant]
                val riseCc = riseDelay(dx, dz)

                locs += addBarrierLoc(tile, tendril)
                addBarrierLoc(tile, ARENA_BARRIER_MASK_BLOCKING)
                val fx = ARENA_BARRIER_RISE_FX[variant].asRSCM(RSCMType.SPOTANIM)
                deps.worldRepo.spotanimMap(SpotanimType(fx), tile, height = 0, delay = riseCc)
                deps.worldQueues.add(1 + riseCc / CLIENT_CYCLES_PER_TICK) {
                    if (npc in arenaBarrier) addBarrierLoc(tile, tendril)
                }
            }
        }
        arenaBarrier[npc] = locs
    }

    private fun addBarrierLoc(tile: CoordGrid, internal: String): LocInfo =
        locRepo.add(tile, internal, Int.MAX_VALUE, LocAngle.West, LocShape.CentrepieceStraight)

    private fun sealArenaExit(centre: CoordGrid) {
        addBarrierLoc(centre.translate(ARENA_EXIT_DX, ARENA_EXIT_DZ), ARENA_EXIT_SEALED)
    }

    private fun restoreArenaExit(centre: CoordGrid) {
        addBarrierLoc(centre.translate(ARENA_EXIT_DX, ARENA_EXIT_DZ), ARENA_EXIT_OPEN)
    }

    private fun barrierVariant(dx: Int, dz: Int): Int {
        val s = dx + dz
        return when {
            s.mod(2) == 0 -> 0
            s.mod(4) == 1 -> 1
            else -> 2
        }
    }

    private fun riseDelay(dx: Int, dz: Int): Int {
        val w = ARENA_EAST_DX - ARENA_WEST_DX
        val h = ARENA_NORTH_DZ - ARENA_SOUTH_DZ
        val perimeter = 2 * (w + h)
        val cw =
            when {
                dx == ARENA_EAST_DX -> dz - ARENA_SOUTH_DZ
                dz == ARENA_NORTH_DZ -> h + (ARENA_EAST_DX - dx)
                dx == ARENA_WEST_DX -> h + w + (ARENA_NORTH_DZ - dz)
                else -> h + w + h + (dx - ARENA_WEST_DX)
            }
        return BARRIER_RISE_CYCLES_PER_TILE * minOf(cw, perimeter - cw)
    }

    private fun dropArenaBarrier(npc: Npc, animated: Boolean = false) {
        val locs = arenaBarrier.remove(npc) ?: return
        val centre = npc.spawnCoords
        restoreArenaExit(centre)
        locs.forEach { loc ->
            if (animated) {
                addBarrierLoc(loc.coords, ARENA_BARRIER_MASK_CLEARED)
                val fx = ARENA_BARRIER_DESPAWN_FX[barrierVariant(loc.coords.x - centre.x, loc.coords.z - centre.z)]
                deps.worldRepo.spotanimMap(SpotanimType(fx.asRSCM(RSCMType.SPOTANIM)), loc.coords)
            } else {
                locRepo.del(loc, Int.MAX_VALUE)
            }
        }
    }

    private fun lifestealOnImpact(bossIds: Set<Int>, event: PlayerHitEvents.Impact) {
        val hit = event.hit
        if (!hit.isFromNpc || hit.damage <= 0) return
        val source = hit.resolveNpcSource(npcList) ?: return
        if (source.type.id !in bossIds) return
        source.heal(hit.damage / 2, showHitsplat = true)
    }

    private fun registerHeadGaze() {
        deps.extensionRegistry.register(MAYBE_HEAD_GAZE) { _, npc, target, _ ->
            val now = deps.mapClock.cycle
            val ready = now >= (nextHeadGazeTick[npc] ?: 0)
            val inRange =
                target.isValidTarget() &&
                    target.coords.chebyshevDistance(npc.coords) <= HEAD_GAZE_RANGE
            val belowHp = npc.hitpoints in 1 until HEAD_GAZE_HP_THRESHOLD
            if (ready && inRange && belowHp) {
                nextHeadGazeTick[npc] =
                    now + HEAD_GAZE_MIN_INTERVAL + deps.random.of(HEAD_GAZE_INTERVAL_SPREAD)
                deps.worldQueues.add(HEAD_GAZE_LAUNCH_DELAY) {
                    if (npc.isSlotAssigned && npc.hitpoints > 0 && target.isValidTarget()) {
                        fireHeadGaze(npc, target)
                    }
                }
            }
        }
    }

    private fun fireHeadGaze(npc: Npc, target: Player) {
        val headTile = pickTentacleTile(npc)

        target.mes(HEAD_GAZE_MESSAGE)

        val tentacleType = ServerCacheManager.getNpc(HEAD_TENTACLE_NPC.asRSCM(RSCMType.NPC))!!
        val tentacle = Npc(tentacleType, headTile)
        tentacle.mode = NpcMode.None
        deps.npcRepo.add(tentacle, HEAD_TENTACLE_TICKS)
        tentacle.spotanim(HEAD_SCREAM_SPOTANIM)
        tentacle.facePlayer(target)

        val proj =
            deps.bossProjectile(
                spotanim = HEAD_PROJECTILE_SPOTANIM.asRSCM(RSCMType.SPOTANIM),
                src = headTile,
                target = target.coords,
                startHeight = HEAD_PROJ_START_HEIGHT,
                endHeight = HEAD_PROJ_END_HEIGHT,
                delay = HEAD_PROJ_START_DELAY,
                travel = HEAD_PROJ_TRAVEL,
                curve = HEAD_PROJ_ANGLE,
                homing = target, // OSRS head-gaze projectile tracks the player
            )

        target.spotanim(
            HEAD_IMPACT_SPOTANIM,
            delay = proj.clientCycles,
            height = HEAD_IMPACT_HEIGHT,
            slot = 2,
        )

        val damage = deps.random.of(HEAD_GAZE_MAX_HIT + 1)
        target.queueHit(npc, HEAD_GAZE_FLIGHT_TICKS, HitType.Ranged, damage, deps.playerHitModifier)

        deps.worldQueues.add(HEAD_GAZE_FLIGHT_TICKS) {
            val prayed = target.vars[PROTECT_FROM_MISSILES] != 0
            if (target.hitpoints > 0 && !prayed) {
                target.mes(HEAD_GAZE_LOCKOUT_MESSAGE)
                target.lockOverheads(HEAD_GAZE_LOCKOUT_TICKS)
                CombatEffects.statDrain(target, listOf("stat.prayer"), HEAD_GAZE_PRAYER_DRAIN)
            }
        }
    }

    private fun runAxeSet(npc: Npc, allowStrangle: Boolean = true) {
        if (npc !in arenaBarrier || npc.hitpoints <= 0 || !npc.isSlotAssigned) return

        if (allowStrangle && maybeStrangleInsteadOfAxes(npc)) return

        val awakened = isAwakened(npc)
        val count =
            VardorvisAxes.count(npc.hitpoints, awakened) { deps.random.randomBoolean(AXE_EXTRA_CHANCE) }
        val centre = npc.spawnCoords
        val anchors = pickAnchors(count)

        val tendrils = anchors.mapNotNull { anchor -> spawnTendril(centre, anchor)?.let { anchor to it } }
        deps.worldQueues.add(AXE_WINDUP_ANIM_DELAY) {
            tendrils.forEach { (_, tendril) -> if (tendril.isSlotAssigned) tendril.anim(AXE_WINDUP_SEQ) }
        }
        deps.worldQueues.add(AXE_WINDUP_TICKS) {
            tendrils.forEach { (_, tendril) ->
                if (tendril.isSlotAssigned) deps.npcRepo.del(tendril, Int.MAX_VALUE)
            }
            if (npc in arenaBarrier && npc.hitpoints > 0) {
                anchors.forEach { handoffAxe(npc, centre, it, awakened) }
            }
        }

        val enrage = awakened || npc.hpFraction() <= ENRAGE_HP_FRACTION
        val interval = if (enrage) AXE_INTERVAL_ENRAGE else AXE_INTERVAL_NORMAL
        deps.worldQueues.add(interval) { runAxeSet(npc) }
    }

    private fun maybeStrangleInsteadOfAxes(npc: Npc): Boolean {
        val now = deps.mapClock.cycle
        val last = lastStrangleTick[npc]
        if (last != null && now - last < STRANGLE_MIN_GAP) return false
        if (!deps.random.randomBoolean(STRANGLE_SLOT_CHANCE)) return false

        val target = arenaTarget(npc) ?: return false
        lastStrangleTick[npc] = now
        deps.suppressAttacks(npc, STRANGLE_SUPPRESS_TICKS)

        npc.anim(ENTANGLE_START_SEQ)
        deps.worldQueues.add(STRANGLE_TELEGRAPH) {
            if (npc.isSlotAssigned && npc.hitpoints > 0 && target.isValidTarget()) {
                npc.anim(ENTANGLE_LOOP_SEQ)
                strangle.beginStrangle(target) {
                    if (npc.isSlotAssigned) npc.anim(ENTANGLE_END_SEQ)
                }
            }
        }
        deps.worldQueues.add(
            STRANGLE_TELEGRAPH + VardorvisStrangle.TOTAL_TICKS + STRANGLE_AXE_RESUME_GAP
        ) {
            runAxeSet(npc)
        }
        return true
    }

    private fun arenaTarget(npc: Npc): Player? =
        deps.playerList
            .filter {
                it.isValidTarget() &&
                    it.coords.level == npc.coords.level &&
                    it.coords.chebyshevDistance(npc.spawnCoords) <= ARENA_EAST_DX
            }
            .minByOrNull { it.coords.chebyshevDistance(npc.coords) }

    private fun pickAnchors(count: Int): List<VardorvisAxes.Anchor> {
        val pool = VardorvisAxes.ANCHORS.toMutableList()
        return buildList {
            repeat(count.coerceAtMost(pool.size)) { add(pool.removeAt(deps.random.of(pool.size))) }
        }
    }

    private fun spawnTendril(centre: CoordGrid, anchor: VardorvisAxes.Anchor): Npc? {
        val type = ServerCacheManager.getNpc(AXE_TENDRIL_NPC.asRSCM(RSCMType.NPC))!!
        val tendril = Npc(type, centre.translate(anchor.dx, anchor.dz))
        tendril.mode = NpcMode.None
        tendril.headingTo(anchor) // spawnangle
        deps.npcRepo.add(tendril, AXE_TENDRIL_TICKS)
        tendril.faceInstant(anchor.bearing)
        tendril.anim(AXE_SPAWN_SEQ)
        return tendril
    }

    private fun Npc.headingTo(anchor: VardorvisAxes.Anchor) {
        Direction.entries.firstOrNull { it.xOff == anchor.hx && it.zOff == anchor.hz }?.let { respawnDir = it }
    }

    private fun Npc.faceInstant(angle: Int) {
        infoProtocol.setFaceAngle(angle, instant = true)
    }

    private fun handoffAxe(npc: Npc, centre: CoordGrid, anchor: VardorvisAxes.Anchor, awakened: Boolean) {
        spawnWallStub(centre, anchor)

        val type = ServerCacheManager.getNpc(AXE_FLYING_NPC.asRSCM(RSCMType.NPC))!!
        val spawn = centre.translate(anchor.spawnDx, anchor.spawnDz) // = the dump's [add] coord

        val axe = Npc(type, spawn)
        axe.mode = NpcMode.None
        axe.headingTo(anchor)
        deps.npcRepo.add(axe, AXE_FLYING_TICKS)
        axe.faceInstant(anchor.bearing)
        axe.anim(AXE_TRAVEL_SEQ)
        axe.pendingExactMove =
            EntityExactMove(
                deltaX1 = -anchor.hx,
                deltaZ1 = -anchor.hz,
                deltaX2 = 0,
                deltaZ2 = 0,
                clientDelay1 = 0,
                clientDelay2 = CLIENT_CYCLES_PER_TICK,
                direction = anchor.bearing,
            )

        val struck = HashSet<Player>()
        // Awakened: the axe also hits anyone it spawns on top of — no bleed on that one (wiki).
        if (awakened) strikePlayers(npc, footprintBox(spawn), struck, bleed = false, awakened = true)

        var from = spawn
        VardorvisAxes.TRAVEL_STEPS.forEachIndexed { i, advance ->
            val legFrom = from
            val legTo = clampToArena(centre, from.translate(anchor.hx * advance, anchor.hz * advance))
            deps.worldQueues.add(AXE_STEP_BASE_DELAY + i) { // 2, 3, 4, 5
                if (axe.isSlotAssigned && npc.hitpoints > 0) {
                    glideAxe(axe, legFrom, legTo, anchor.bearing)
                    axe.anim(AXE_TRAVEL_SEQ) // dump re-issues the spin every tick
                    strikePlayers(npc, sweptBox(legFrom, legTo), struck, bleed = true, awakened = awakened)
                }
            }
            from = legTo
        }
    }

    private fun glideAxe(axe: Npc, from: CoordGrid, to: CoordGrid, angle: Int) {
        val lead = Translation.between(from, to) // from - to
        PathingEntityCommon.teleport(axe, deps.collision, to) // non-jump -> rsprox `[teleport]`
        axe.pendingExactMove =
            EntityExactMove(
                deltaX1 = lead.x,
                deltaZ1 = lead.z,
                deltaX2 = 0,
                deltaZ2 = 0,
                clientDelay1 = 0,
                clientDelay2 = CLIENT_CYCLES_PER_TICK,
                direction = angle,
            )
    }

    private fun spawnWallStub(centre: CoordGrid, anchor: VardorvisAxes.Anchor) {
        val type = ServerCacheManager.getNpc(AXE_WALL_NPC.asRSCM(RSCMType.NPC))!!
        val stub = Npc(type, centre.translate(anchor.dx, anchor.dz))
        stub.mode = NpcMode.None
        deps.npcRepo.add(stub, AXE_WALL_STUB_TICKS)
        stub.anim(AXE_WALL_END_SEQ)
    }

    private fun strikePlayers(
        npc: Npc,
        box: Box,
        struck: MutableSet<Player>,
        bleed: Boolean,
        awakened: Boolean,
    ) {
        for (player in deps.playerList) {
            if (player.hitpoints <= 0 || player.coords.level != npc.coords.level) continue
            if (!box.contains(player.coords.x, player.coords.z)) continue
            if (!struck.add(player)) continue

            val max = if (awakened) AXE_MAX_HIT_AWAKENED else AXE_MAX_HIT
            var damage = deps.random.of(max + 1)
            if (player.vars[PROTECT_FROM_MELEE] != 0) damage /= 2
            player.queueHit(npc, HIT_DELAY, HitType.Melee, damage, deps.playerHitModifier)
            if (bleed) applyAxeBleed(npc, player, awakened)
        }
    }

    private fun applyAxeBleed(npc: Npc, player: Player, awakened: Boolean) {
        val amount = if (awakened) AXE_BLEED_DAMAGE_AWAKENED else AXE_BLEED_DAMAGE
        for (hit in 1..AXE_BLEED_HITS) {
            deps.worldQueues.add(hit * AXE_BLEED_INTERVAL) {
                if (player.hitpoints > 0) {
                    player.queueHit(
                        npc,
                        HIT_DELAY,
                        HitType.Typeless,
                        amount,
                        deps.playerHitModifier,
                        hitmark = hitmark_groups.bleed,
                    )
                }
            }
        }
    }

    private fun registerDash() {
        deps.extensionRegistry.register(DASH) { _, npc, target, _ ->
            if (npc.hitpoints > 0 && target.isValidTarget()) {
                runDash(npc, target)
            }
        }
    }

    private fun runDash(npc: Npc, target: Player) {
        val awakened = isAwakened(npc)
        val darts = VardorvisDash.count(npc.hitpoints)

        val plan = VardorvisDash.plan(darts, deps.random::of)

        deps.suppressAttacks(npc, darts)
        npc.ignoreCombatInteractions = true
        npc.clearFacingLock()

        for (i in 0 until darts) {
            val dartMarks = plan.getOrElse(i) { emptyList() }
            deps.worldQueues.add(DASH_STEP_BASE_DELAY + i) { // 2, 3, 4
                if (npc.isSlotAssigned && npc.hitpoints > 0) {
                    doDart(npc, target, marks = dartMarks, awakened = awakened)
                }
            }
        }

        deps.worldQueues.add(DASH_STEP_BASE_DELAY + darts) {
            npc.ignoreCombatInteractions = false
            if (npc.isSlotAssigned && npc.hitpoints > 0 && target.isValidTarget()) {
                npc.opPlayer2(target, aiPlayerInteractions)
            }
        }
    }

    private fun doDart(
        npc: Npc,
        target: Player,
        marks: List<VardorvisDash.Mark>,
        awakened: Boolean,
    ) {
        if (!npc.isSlotAssigned || npc.hitpoints <= 0) return

        val from = npc.coords
        val dest = pickDartTile(npc, target)
        if (dest != from) {
            val lead = Translation.between(from, dest)
            PathingEntityCommon.teleport(npc, deps.collision, dest)
            npc.pendingExactMove =
                EntityExactMove(
                    deltaX1 = lead.x,
                    deltaZ1 = lead.z,
                    deltaX2 = 0,
                    deltaZ2 = 0,
                    clientDelay1 = 0,
                    clientDelay2 = CLIENT_CYCLES_PER_TICK,
                    direction = VardorvisDash.bearing(dest.x - from.x, dest.z - from.z),
                )
        }
        npc.anim(DASH_SEQ)
        npc.facePlayer(target)

        val inPlace = dest == from
        val centre = npc.spawnCoords
        val playerTile = target.coords
        val warningSpot = SpotanimType(DASH_WARNING_SPOT.asRSCM(RSCMType.SPOTANIM))
        val combinedSpot = SpotanimType(DASH_COMBINED_SPOT.asRSCM(RSCMType.SPOTANIM))
        val planned = if (inPlace) marks.filterNot { it.dx == 0 && it.dz == 0 }.take(1) else marks
        val tiles =
            planned
                .map { m -> playerTile.translate(m.dx, m.dz) to m.delayCc }
                .filter { (tile, _) -> tile.inArenaInterior(centre) }
                .distinctBy { it.first }
        tiles.forEach { (tile, cc) -> deps.worldRepo.spotanimMap(warningSpot, tile, 0, cc) }

        val tileSet = tiles.mapTo(HashSet()) { it.first }
        deps.worldQueues.add(SPIKE_STRIKE_DELAY + 1) {
            if (npc.hitpoints <= 0) return@add
            tiles.forEach { (tile, cc) -> deps.worldRepo.spotanimMap(combinedSpot, tile, 0, cc) }
            strikeCracks(npc, tileSet, awakened)
        }
    }

    private fun pickDartTile(npc: Npc, target: Player): CoordGrid {
        val centre = npc.spawnCoords
        val p = target.coords
        val minX = centre.x + ARENA_WEST_DX + 1
        val maxX = centre.x + ARENA_EAST_DX - BOSS_SIZE
        val minZ = centre.z + ARENA_SOUTH_DZ + 1
        val maxZ = centre.z + ARENA_NORTH_DZ - BOSS_SIZE

        val offsets = DART_SW_OFFSETS.toMutableList()
        for (i in offsets.size - 1 downTo 1) {
            val j = deps.random.of(i + 1)
            offsets[i] = offsets[j].also { offsets[j] = offsets[i] }
        }
        for ((ox, oz) in offsets) {
            val tx = p.x + ox
            val tz = p.z + oz
            if (tx < minX || tx > maxX || tz < minZ || tz > maxZ) continue
            val tile = p.translate(ox, oz)
            if (tile != npc.coords && dartFootprintClear(npc, tile)) return tile
        }
        return npc.coords
    }

    private fun dartFootprintClear(npc: Npc, sw: CoordGrid): Boolean {
        val bx = npc.coords.x
        val bz = npc.coords.z
        for (fx in 0 until BOSS_SIZE) {
            for (fz in 0 until BOSS_SIZE) {
                val t = sw.translate(fx, fz)
                val underBossNow = t.x in bx until bx + BOSS_SIZE && t.z in bz until bz + BOSS_SIZE
                if (!underBossNow && deps.collision[t] and DART_BLOCKED_MASK != 0) return false
            }
        }
        return true
    }

    private fun strikeCracks(npc: Npc, tiles: Set<CoordGrid>, awakened: Boolean) {
        val max = if (awakened) DASH_MAX_HIT_AWAKENED else DASH_MAX_HIT
        for (player in deps.playerList) {
            if (player.hitpoints <= 0 || player.coords.level != npc.coords.level) continue
            if (player.coords !in tiles) continue
            var damage = deps.random.of(max + 1)
            if (player.vars[PROTECT_FROM_MELEE] != 0) damage /= 2
            player.queueHit(npc, HIT_DELAY, HitType.Melee, damage, deps.playerHitModifier)
        }
    }

    // Placeholder
    private fun isAwakened(npc: Npc): Boolean = false

    private fun Npc.hpFraction(): Double =
        hitpoints.toDouble() / baseHitpointsLvl.coerceAtLeast(1)

    private fun CoordGrid.inArenaInterior(centre: CoordGrid): Boolean {
        val dx = x - centre.x
        val dz = z - centre.z
        return dx in (ARENA_WEST_DX + 1)..(ARENA_EAST_DX - 1) &&
            dz in (ARENA_SOUTH_DZ + 1)..(ARENA_NORTH_DZ - 1)
    }

    private fun clampToArena(centre: CoordGrid, tile: CoordGrid): CoordGrid {
        val x = tile.x.coerceIn(centre.x + ARENA_WEST_DX, centre.x + ARENA_EAST_DX)
        val z = tile.z.coerceIn(centre.z + ARENA_SOUTH_DZ, centre.z + ARENA_NORTH_DZ)
        return tile.translate(x - tile.x, z - tile.z)
    }

    private fun footprintBox(sw: CoordGrid): Box =
        Box(sw.x, sw.z, sw.x + AXE_SIZE - 1, sw.z + AXE_SIZE - 1)

    private fun sweptBox(from: CoordGrid, to: CoordGrid): Box =
        Box(
            minOf(from.x, to.x),
            minOf(from.z, to.z),
            maxOf(from.x, to.x) + AXE_SIZE - 1,
            maxOf(from.z, to.z) + AXE_SIZE - 1,
        )

    private class Box(val minX: Int, val minZ: Int, val maxX: Int, val maxZ: Int) {
        fun contains(x: Int, z: Int): Boolean = x in minX..maxX && z in minZ..maxZ
    }

    private fun pickTentacleTile(npc: Npc): CoordGrid {
        val centre = npc.spawnCoords
        val bx = npc.coords.x
        val bz = npc.coords.z

        fun tooCloseToBoss(tile: CoordGrid) =
            tile.x in (bx - 1)..(bx + BOSS_SIZE) && tile.z in (bz - 1)..(bz + BOSS_SIZE)

        val candidates =
            buildList {
                for (dx in -HEAD_GAZE_SPAWN_SPREAD..HEAD_GAZE_SPAWN_SPREAD) {
                    for (dz in -HEAD_GAZE_SPAWN_SPREAD..HEAD_GAZE_SPAWN_SPREAD) {
                        val tile = centre.translate(dx, dz)
                        if (!tooCloseToBoss(tile)) add(tile)
                    }
                }
            }
        return if (candidates.isEmpty()) {
            centre.translate(HEAD_GAZE_SPAWN_SPREAD, HEAD_GAZE_SPAWN_SPREAD)
        } else {
            candidates[deps.random.of(candidates.size)]
        }
    }

    private fun resetStats(npc: Npc) {
        npc.strengthLvl = npc.type.strength
        npc.defenceLvl = npc.type.defence
    }

    private fun scaleStatsToHp(npc: Npc) {
        val base = npc.baseHitpointsLvl.coerceAtLeast(1)
        val hpFraction = npc.hitpoints.toDouble() / base
        npc.defenceLvl = VardorvisScaling.defence(hpFraction, npc.type.defence)
        npc.strengthLvl = VardorvisScaling.strength(hpFraction, npc.type.strength)
    }

    override val spec =
        boss("npc.vardorvis") {
            stats(attackRate = ATTACK_RATE, aggressionRadius = AGGRESSION_RADIUS)

            val melee =
                ability("melee") {
                    anim("seq.npc_vardorvis_01_melee_01")
                    hit {
                        damage(Accuracy(npcMaxHit()))
                        type(Melee)
                    }
                    include(external(MAYBE_HEAD_GAZE))
                }

            val dash = ability("dash") { include(external(DASH)) }

            phase("main") {
                weightedSelectorRandom {
                    +random(melee, weight = 6, requires = WithinMeleeRange)
                    +random(dash, weight = 1, requires = WithinMeleeRange, cooldown = DASH_COOLDOWN)
                }
            }

            phase("enrage", entryHp = ENRAGE_HP_FRACTION) {
                weightedSelectorRandom {
                    +random(melee, weight = 5, requires = WithinMeleeRange)
                    +random(dash, weight = 2, requires = WithinMeleeRange, cooldown = DASH_COOLDOWN_ENRAGE)
                }
            }
        }

    private companion object {
        private const val MAYBE_HEAD_GAZE = "vardorvis.maybe_head_gaze"

        private const val DASH = "vardorvis.dash"


        private const val AXE_TENDRIL_NPC = "npc.vardorvis_big_tentacle"
        private const val AXE_FLYING_NPC = "npc.vardorvis_axe"
        private const val AXE_WALL_NPC = "npc.vardorvis_axe_static"

        private const val AXE_SPAWN_SEQ = "seq.npc_vardorvis_axe_01_spawn_01"
        private const val AXE_WINDUP_SEQ = "seq.npc_vardorvis_axe_01_attack_start"
        private const val AXE_TRAVEL_SEQ = "seq.proj_vardorvis_axe_01"
        private const val AXE_WALL_END_SEQ = "seq.npc_vardorvis_axe_02_attack_end"

        private const val AXE_SIZE = 3

        private const val AXE_FIRST_DELAY = 15
        private const val AXE_WINDUP_ANIM_DELAY = 2
        private const val AXE_WINDUP_TICKS = 4
        private const val AXE_TENDRIL_TICKS = 6
        private const val AXE_STEP_BASE_DELAY = 2
        private const val AXE_FLYING_TICKS = 6
        private const val AXE_WALL_STUB_TICKS = 2
        private const val AXE_INTERVAL_NORMAL = 13
        private const val AXE_INTERVAL_ENRAGE = 8
        private const val AXE_EXTRA_CHANCE = 4

        private const val HIT_DELAY = 1

        private const val AXE_MAX_HIT = 35
        private const val AXE_MAX_HIT_AWAKENED = 96
        private const val AXE_BLEED_HITS = 5
        private const val AXE_BLEED_INTERVAL = 3
        private const val AXE_BLEED_DAMAGE = 3
        private const val AXE_BLEED_DAMAGE_AWAKENED = 5

        private const val DASH_SEQ = "seq.npc_vardorvis_01_dash_01"
        private const val DASH_WARNING_SPOT = "spotanim.vfx_vardorvis_spike_warning_01"
        private const val DASH_COMBINED_SPOT = "spotanim.vfx_vardorvis_spike_combined"
        private const val SPIKE_STRIKE_DELAY = 3
        private const val DASH_STEP_BASE_DELAY = 2
        private const val DASH_MAX_HIT = 25
        private const val DASH_MAX_HIT_AWAKENED = 40

        private val DART_SW_OFFSETS =
            listOf(
                -1 to 1, 0 to 1,
                -1 to -2, 0 to -2,
                1 to -1, 1 to 0,
                -2 to -1, -2 to 0,
            )

        private const val DART_BLOCKED_MASK: Int =
            CollisionFlag.LOC or
                CollisionFlag.BLOCK_WALK or
                CollisionFlag.GROUND_DECOR or
                CollisionFlag.BLOCK_NPCS or
                CollisionFlag.BLOCK_PLAYERS

        private const val DASH_COOLDOWN = 15
        private const val DASH_COOLDOWN_ENRAGE = 10

        private const val PROTECT_FROM_MELEE = "varbit.prayer_protectfrommelee"

        private const val ATTACK_RATE = 5
        private const val AGGRESSION_RADIUS = 10

        private const val STRANGLE_SLOT_CHANCE = 3
        private const val STRANGLE_MIN_GAP = 25
        private const val STRANGLE_TELEGRAPH = 2

        private const val ENTANGLE_START_SEQ = "seq.npc_vardorvis_01_entangle_start"
        private const val ENTANGLE_LOOP_SEQ = "seq.npc_vardorvis_01_entangle_loop"
        private const val ENTANGLE_END_SEQ = "seq.npc_vardorvis_01_entangle_end"

        private const val STRANGLE_GRACE = 1
        private const val STRANGLE_SUPPRESS_TICKS =
            STRANGLE_TELEGRAPH + VardorvisStrangle.TOTAL_TICKS + STRANGLE_GRACE - ATTACK_RATE

        private const val STRANGLE_AXE_RESUME_GAP = 13

        private const val SPAWN_SEQ = "seq.npc_vardorvis_01_spawn_01"
        private const val BOSS_SIZE = 2

        private const val ARENA_WEST_DX = -5
        private const val ARENA_EAST_DX = 7
        private const val ARENA_SOUTH_DZ = -5
        private const val ARENA_NORTH_DZ = 7

        private const val ARENA_BARRIER_MASK_BLOCKING = "loc.invisible_type8_blocking"
        private const val ARENA_BARRIER_MASK_CLEARED = "loc.invisible_type8_nonblocking"

        private const val ARENA_EXIT_OPEN = "loc.vardorvis_exit"
        private const val ARENA_EXIT_SEALED = "loc.vardorvis_exit_noop"
        private const val ARENA_EXIT_DX = -10
        private const val ARENA_EXIT_DZ = 11

        private const val CLIENT_CYCLES_PER_TICK = 30

        private val ARENA_BARRIER_LOCS =
            arrayOf(
                "loc.vardorvis_escape_1",
                "loc.vardorvis_escape_2",
                "loc.vardorvis_escape_3",
            )
        private val ARENA_BARRIER_RISE_FX =
            arrayOf(
                "spotanim.vardorvis_spike_spawn_short",
                "spotanim.vardorvis_spike_spawn_med",
                "spotanim.vardorvis_spike_spawn_tall",
            )
        private val ARENA_BARRIER_DESPAWN_FX =
            arrayOf(
                "spotanim.vardorvis_spike_despawn_short",
                "spotanim.vardorvis_spike_despawn_med",
                "spotanim.vardorvis_spike_despawn_tall",
            )


        private const val BARRIER_RISE_CYCLES_PER_TILE = 2
        private const val BARRIER_DEATH_DELAY = 5


        private const val HEAD_GAZE_HP_THRESHOLD = 570
        private const val HEAD_GAZE_MIN_INTERVAL = 7
        private const val HEAD_GAZE_INTERVAL_SPREAD = 6
        private const val HEAD_GAZE_LAUNCH_DELAY = 1
        private const val HEAD_GAZE_SPAWN_SPREAD = 3
        private const val HEAD_GAZE_RANGE = 12
        private const val HEAD_GAZE_MAX_HIT = 24
        private const val HEAD_GAZE_PRAYER_DRAIN = 10
        private const val HEAD_GAZE_LOCKOUT_TICKS = 3
        private const val HEAD_TENTACLE_NPC = "npc.vardorvis_head_tentacle"
        private const val HEAD_TENTACLE_TICKS = 6
        private const val HEAD_SCREAM_SPOTANIM = "spotanim.vfx_vardorvis_01_head_01_scream_ranged_01"
        private const val HEAD_PROJECTILE_SPOTANIM = "spotanim.vfx_vardorvis_head_projectile_ranged_01"
        private const val HEAD_IMPACT_SPOTANIM =
            "spotanim.vfx_vardorvis_head_projectile_impact_ranged_01"
        private const val HEAD_PROJ_START_DELAY = 15
        private const val HEAD_PROJ_TRAVEL = 75
        private const val HEAD_PROJ_ANGLE = 8
        private const val HEAD_PROJ_START_HEIGHT = 124
        private const val HEAD_PROJ_END_HEIGHT = 96
        private const val HEAD_IMPACT_HEIGHT = 124
        private const val HEAD_GAZE_FLIGHT_TICKS = 3
        private const val PROTECT_FROM_MISSILES = "varbit.prayer_protectfrommissiles"
        private const val HEAD_GAZE_MESSAGE =
            "<col=ff289d>Vardorvis' head gazes upon you...</col>"
        private const val HEAD_GAZE_LOCKOUT_MESSAGE =
            "You've been injured and can't use protection prayers!"

        private const val ENRAGE_HP_FRACTION = 0.33

        private fun String.npcId(): Int? =
            ServerCacheManager.getNpc(this.asRSCM(RSCMType.NPC))?.id
    }
}
