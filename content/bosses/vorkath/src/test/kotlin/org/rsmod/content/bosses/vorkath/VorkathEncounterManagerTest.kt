package org.rsmod.content.bosses.vorkath

import dev.openrune.ServerCacheManager
import kotlin.test.*
import org.junit.jupiter.api.BeforeAll
import org.mockito.Answers
import org.mockito.Mockito.*
import org.rsmod.annotations.InternalApi
import org.rsmod.api.bossbar.plugin.BossHpBarScript
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.config.refs.params
import org.rsmod.api.instances.*
import org.rsmod.api.instances.region.InstanceAreaResolver
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.owner.assignSpawnOwner
import org.rsmod.api.player.hit.modifier.StandardPlayerHitModifier
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.route.StepFactory
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.hit.Hit
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.proj.ProjAnim
import org.rsmod.game.queue.PlayerQueueList
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

/**
 * Real revision-240 entities, hit queues, and NPC registry; scene output and sessions are recorded.
 */
class VorkathEncounterManagerTest {
    @Test
    fun nativeSpawnRequiresCrumbleIdentityException() {
        val spawn = assertNotNull(ServerCacheManager.getNpc(8063))
        val undead = spawn.param(params.undead)
        println("VORKATH_CRUMBLE_CONTRACT id=${spawn.id} internalName=${spawn.internalName} undead=$undead")
        assertTrue(spawn.isType("npc.vorkath_spawn"))
        assertEquals("npc.vorkath_spawn", spawn.internalName)
        assertEquals(0, undead, "The native spawn needs its explicit Crumble Undead exception")
    }

    @Test
    fun spawnRejectsStuckLandingTilesAndClipsAlongTheCapturedBossEdge() {
        val f = VorkathFixture()
        f.player.coords = CoordGrid(2272, 4061)
        f.launchSpawn()
        f.run.pendingSpawnTile = CoordGrid(2271, 4069)
        f.advance(4)
        val spawn = assertNotNull(f.run.spawn)
        f.collision.add(2272, 4061, 0, CollisionFlag.BLOCK_NPCS)
        assertFalse(f.manager.spawnCanReach(f.run, spawn.coords))
        f.npcs.del(spawn, Int.MAX_VALUE)
        f.player.coords = CoordGrid(2264, 4068)
        var tile = CoordGrid(2272, 4069)
        for (x in 2271 downTo 2268) {
            tile = f.manager.nextSpawnStep(f.run, tile)
            assertEquals(CoordGrid(x, 4069), tile, "rsprox-3279 ticks13746..13749")
        }
        assertTrue(f.manager.spawnCanReach(f.run, CoordGrid(2272, 4069)))
    }

    @Test
    fun spawnFinalStepOverlapsFrozenPlayerButStillRespectsMapObjects() {
        val f = VorkathFixture()
        f.launchSpawn()
        f.advance(4)
        val spawn = assertNotNull(f.run.spawn)
        val target = f.player.coords
        spawn.coords = target.translate(1, 0)
        f.collision.add(target.x, target.z, target.level, CollisionFlag.BLOCK_NPCS)
        f.advance()
        val steps = StepFactory(f.collision)
        val strategy = assertNotNull(spawn.collisionStrategy)
        assertEquals(target, steps.validated(spawn, spawn.coords, target, strategy))
        f.collision.add(target.x, target.z, target.level, CollisionFlag.LOC)
        assertEquals(CoordGrid.NULL, f.manager.nextSpawnStep(f.run, spawn.coords))
        assertNotEquals(target, steps.validated(spawn, spawn.coords, target, strategy))
    }

    @Test
    fun wakeUsesMappedArenaBoundsInsteadOfTheAllocationId() {
        val f = VorkathFixture()
        assertEquals(setOf(0), f.session.regionIds)
        assertTrue(f.manager.forceReset(f.player))
        assertTrue(f.manager.poke(f.player, f.run.boss))
        assertEquals(VorkathState.AWAKENING, f.run.state)
    }

    @Test
    fun wakeRejectsCoordinatesOutsideMappedArenaAndWrongPlane() {
        val f = VorkathFixture()
        assertTrue(f.manager.forceReset(f.player))
        for (tile in listOf(CoordGrid(2239, 4057), CoordGrid(2304, 4057),
            CoordGrid(2272, 4031), CoordGrid(2272, 4096), CoordGrid(2272, 4057, 1))) {
            f.player.coords = tile
            assertFalse(f.manager.poke(f.player, f.run.boss), "$tile must not wake this instance")
            assertEquals(VorkathState.SLEEPING, f.run.state)
        }
    }

    @Test
    fun wakeRejectsAnotherSessionEvenWithMatchingMappedCoordinates() {
        val f = VorkathFixture()
        assertTrue(f.manager.forceReset(f.player))
        val other = InstanceSession(InstanceId(2), mutableSetOf(0), 2L,
            f.session.key, f.session.spec, f.session.placement, InstanceAccess.Private)
        `when`(f.instances.sessionForPlayer(f.player)).thenReturn(other)
        assertFalse(f.manager.poke(f.player, f.run.boss))
        assertEquals(VorkathState.SLEEPING, f.run.state)
    }

    @Test
    fun acidPoolArrivalAndBarrageUseTheSameEncounterClock() {
        val f = VorkathFixture()
        assertTrue(f.manager.forceSpecial(f.player, VorkathSpecial.ACID))
        val cast = f.clock.cycle
        val poolCount = f.run.pendingAcidPools.size
        assertTrue(poolCount in 56..57)
        assertTrue(f.run.pendingAcidPools.all { it.impactCycle == cast + 3 })
        assertTrue(f.player.coords in f.run.pendingAcidPools.map { it.tile })
        assertTrue(
            f.run.acidSafeLane.intersect(f.run.pendingAcidPools.map { it.tile }.toSet()).isEmpty()
        )
        f.player.coords = f.run.acidSafeLane.first()
        f.advance(2)
        assertTrue(f.run.acidTiles.isEmpty())
        assertEquals(0, f.run.shotsFired)
        f.advance()
        assertEquals(poolCount, f.run.acidTiles.size)
        assertEquals(poolCount, f.run.acidVisuals.size)
        assertEquals(0, f.run.shotsFired)
        f.advance()
        assertEquals(1, f.run.shotsFired)
        f.advance(24)
        assertEquals(25, f.run.shotsFired)
        assertEquals(25, f.projectiles().count { it.spotanim == 1482 })
        assertTrue(f.run.acidTiles.isEmpty())
        assertTrue(f.run.acidVisuals.isEmpty())
        f.advance(5)
        assertEquals(VorkathState.ACTIVE, f.run.state)
        assertEquals(25, f.projectiles().count { it.spotanim == 1482 })
        assertEquals(cast + 33, f.run.timeline.nextAttackCycle)
    }

    @Test
    fun spawnArrivesFourTicksAfterLaunchThenReleasesFreezeOnNativeDeathResolution() {
        val f = VorkathFixture()
        val launch = f.launchSpawn()
        assertTrue(f.player.frozen)
        assertEquals(VorkathState.ZOMBIFIED_SPAWN_SPECIAL, f.run.state)
        assertNotNull(f.manager.attackDenial(f.player, f.run.boss))
        val tile = assertNotNull(f.run.pendingSpawnTile)
        assertEquals(8, tile.chebyshevDistance(f.player.coords))
        f.advance(3)
        assertNull(f.run.spawn)
        f.advance()
        val spawn = assertNotNull(f.run.spawn)
        assertEquals(launch + 4, f.clock.cycle)
        assertEquals(38, spawn.hitpoints)
        assertEquals(tile, spawn.coords)
        spawn.hitpoints = 0
        f.manager.beginSpawnDeath(spawn)
        f.advance(2)
        assertTrue(f.player.frozen)
        f.advance()
        assertFalse(f.player.frozen)
        assertEquals(VorkathState.ACTIVE, f.run.state)
        assertNull(f.manager.attackDenial(f.player, f.run.boss))
        assertNull(f.run.spawn)
        assertTrue(spawn in f.run.retiringSpawns)
        assertTrue(f.playerHits().isEmpty())
        f.advance(2)
        assertFalse(spawn.isSlotAssigned)
        assertTrue(f.run.retiringSpawns.isEmpty())
    }

    @Test
    fun fullHealthSpawnExplosionDealsSixtyAndCombatResumesOnItsDamageTick() {
        val f = VorkathFixture()
        f.launchSpawn()
        f.advance(4)
        val spawn = assertNotNull(f.run.spawn)
        spawn.coords = f.player.coords
        f.advance()
        assertEquals(f.clock.cycle + 1, f.run.spawnExplosionCycle)
        assertFalse(f.player.frozen)
        f.advance()
        val hit = f.playerHits().single { it.damage == 60 }
        assertEquals(60, hit.damage)
        assertEquals(1, f.playerQueues().single { it.args === hit }.remainingCycles)
        assertEquals(VorkathState.ACTIVE, f.run.state)
        assertTrue(f.run.timeline.attackDue(f.clock.cycle))
        assertNull(f.run.spawn)
        assertFalse(spawn.isSlotAssigned)
    }

    @Test
    fun walkingOntoThePlayerStartsContactDuringMovementWithoutAnExtraManagerTick() {
        val f = VorkathFixture()
        f.launchSpawn()
        f.advance(5)
        val spawn = assertNotNull(f.run.spawn)
        assertTrue(f.player.frozen)
        val contactCycle = f.clock.cycle
        // The engine advances MapClock before the NPC post-tick arrival callback.
        f.clock.tick()
        f.completeMovement(spawn, f.player.coords)
        assertFalse(f.player.frozen)
        assertEquals(contactCycle + 1, f.run.spawnExplosionCycle)
        assertTrue(f.playerHits().isEmpty())
        f.manager.tick(f.player)
        assertEquals(60, f.playerHits().single().damage)
        assertEquals(VorkathState.ACTIVE, f.run.state)
        assertFalse(spawn.isSlotAssigned)
    }

    @Test
    fun aSpawnArrivalCallbackFromBeforeResetCannotStartAnExplosion() {
        val f = VorkathFixture()
        f.launchSpawn()
        f.advance(5)
        val spawn = assertNotNull(f.run.spawn)
        assertTrue(f.manager.forceReset(f.player))
        f.completeMovement(spawn, f.player.coords)
        assertFalse(f.player.frozen)
        assertEquals(Int.MAX_VALUE, f.run.spawnExplosionCycle)
        assertEquals(VorkathState.SLEEPING, f.run.state)
        assertTrue(f.playerHits().isEmpty())
    }

    @Test
    fun lethalSpawnHitAfterContactCancelsThePendingExplosion() {
        val f = VorkathFixture()
        f.launchSpawn()
        f.advance(4)
        val spawn = assertNotNull(f.run.spawn)
        spawn.coords = f.player.coords
        f.advance()
        spawn.hitpoints = 0
        f.manager.beginSpawnDeath(spawn)
        f.advance(3)
        assertTrue(f.playerHits().isEmpty())
        assertFalse(f.player.frozen)
        assertEquals(VorkathState.ACTIVE, f.run.state)
        assertNull(f.run.spawn)
    }

    @Test
    fun incomingDamageReductionAndImmunityFollowOnlyTheCurrentPhase() {
        val f = VorkathFixture()
        for (state in VorkathState.entries) {
            f.run.state = state
            val hit = f.incomingHit(41)
            f.manager.modifyNpcHit(f.player, f.run.boss, hit)
            val expected =
                when (state) {
                    VorkathState.ACTIVE -> 41
                    VorkathState.ACID_SPECIAL -> 20
                    else -> 0
                }
            assertEquals(expected, hit.damage, state.name)
        }
    }

    @Test
    fun fireballSnapshotsAtProjectileLaunchAndResetCancelsItsImpact() {
        val f = VorkathFixture()
        assertTrue(f.manager.forceAttack(f.player, VorkathStandardAttack.FIREBALL))
        assertTrue(f.projectiles().isEmpty())
        val launchTile = f.player.coords.translate(1, 0)
        f.player.coords = launchTile
        f.advance()
        val projectile = f.projectiles().single { it.spotanim == 1481 }
        assertEquals(launchTile, projectile.endCoord)
        assertEquals(0, projectile.targetIndex)
        assertEquals(1, f.run.pendingHits.size)
        assertEquals(launchTile, f.run.pendingHits.single().tile)
        assertTrue(f.manager.forceReset(f.player))
        assertTrue(f.run.pendingFireballs.isEmpty())
        assertTrue(f.run.pendingHits.isEmpty())
        assertEquals(VorkathState.SLEEPING, f.run.state)
        f.advance(8)
        assertTrue(f.playerHits().isEmpty())
        assertEquals(1, f.projectiles().count { it.spotanim == 1481 })
    }

    @Test
    fun fireballImpactQueuesForThisPlayerCycleAndResetClearsTheQueuedHit() {
        val f = VorkathFixture()
        assertTrue(f.manager.forceAttack(f.player, VorkathStandardAttack.FIREBALL))
        f.advance()
        val impactCycle = f.run.pendingHits.single().impactCycle
        f.advance(impactCycle - f.clock.cycle - 1)
        assertTrue(f.playerHits().isEmpty())
        f.advance()
        val hit = f.playerHits().single()
        assertEquals(1, f.playerQueues().single { it.args === hit }.remainingCycles)
        assertTrue(f.run.pendingHits.isEmpty())
        assertTrue(f.manager.forceReset(f.player))
        assertTrue(f.playerHits().isEmpty())
    }

    @Test
    fun abortRemovesEncounterEffectsAndClearsAlreadyQueuedHits() {
        val f = VorkathFixture()
        f.seedMechanics()
        val spawn = assertNotNull(f.run.spawn)
        val boss = f.run.boss
        f.manager.abort(f.player, "test leave", teleport = false)
        assertNull(f.manager.auditRun(f.player))
        f.assertClean()
        assertFalse(spawn.isSlotAssigned)
        assertFalse(boss.isSlotAssigned)
        verify(f.bars).onClose(f.player, boss, instant = true)
        verify(f.instances).leave(f.player, f.session, f.clock.cycle)
        verify(f.locs).del(f.poolVisual, Int.MAX_VALUE)
    }

    @Test
    fun leavingTheRegionAndLosingTheSessionRunTheSameCleanup() {
        for (loseSession in listOf(false, true)) {
            val f = VorkathFixture()
            f.seedMechanics()
            if (loseSession) {
                `when`(f.instances.sessionForPlayer(f.player)).thenReturn(null)
            } else {
                f.player.coords = CoordGrid(3200, 3200)
            }
            f.manager.tick(f.player)
            assertNull(f.manager.auditRun(f.player))
            f.assertClean()
        }
    }

    @Test
    fun logoutAndDisconnectClearEffectsAndRouteToTheCorrectInstanceCleanup() {
        for (loggingOut in listOf(false, true)) {
            val f = VorkathFixture()
            f.seedMechanics()
            if (loggingOut) f.player.loggingOut = true else f.player.slotId = -1
            f.manager.tick(f.player)
            assertNull(f.manager.auditRun(f.player))
            f.assertClean()
            if (loggingOut) verify(f.instances).handleLogout(f.player, f.clock.cycle)
            else verify(f.instances).leave(f.player, f.session, f.clock.cycle)
        }
    }

    @Test
    fun playerDeathClearsHazardsButRetainsMembershipForTheExistingDeathStorageHook() {
        val f = VorkathFixture()
        f.seedMechanics()
        f.player.statMap.setCurrentLevel("stat.hitpoints", 0)
        f.manager.tick(f.player)
        assertSame(f.run, f.manager.auditRun(f.player))
        assertEquals(VorkathState.ENDED, f.run.state)
        f.assertClean()
        verify(f.bars).onClose(f.player, f.run.boss, instant = true)
        f.manager.abort(f.player, "death hook", teleport = false)
        assertNull(f.manager.auditRun(f.player))
    }

    @Test
    fun bossDeathClearsSpecialEffectsAndRejectsDuplicateDeathAndStaleDamage() {
        val f = VorkathFixture()
        f.seedMechanics()
        f.run.rewardEligible = false
        assertSame(f.run, f.manager.beginBossDeath(f.run.boss))
        assertEquals(VorkathState.DYING, f.run.state)
        f.assertClean()
        assertNull(f.manager.beginBossDeath(f.run.boss))
        assertTrue(f.manager.canFinishDeath(f.run, f.run.boss))
        f.manager.abort(f.player, "leave during death", teleport = false)
        assertFalse(f.manager.canFinishDeath(f.run, f.run.boss))
    }

    @Test
    fun lethalBossDamageCancelsDueEffectsBeforeTheLaterDeathQueueRuns() {
        val f = VorkathFixture()
        f.seedMechanics()
        f.run.rewardEligible = false
        f.run.boss.hitpoints = 0
        f.advance()
        assertSame(f.run, f.manager.auditRun(f.player))
        f.assertClean()
        assertTrue(f.projectiles().isEmpty())
        assertTrue(f.playerHits().isEmpty())
        assertSame(f.run, f.manager.beginBossDeath(f.run.boss))
        assertEquals(VorkathState.DYING, f.run.state)
    }

    @Test
    fun removedBossAbortsInsteadOfLaunchingPendingEffects() {
        val f = VorkathFixture()
        f.seedMechanics()
        f.npcs.del(f.run.boss, Int.MAX_VALUE)
        f.advance()
        assertNull(f.manager.auditRun(f.player))
        f.assertClean()
        assertTrue(f.projectiles().isEmpty())
        assertTrue(f.playerHits().isEmpty())
    }

    @Test
    fun resetAndReawakeningStartAFreshBossWithoutOldSpecialState() {
        val f = VorkathFixture()
        f.seedMechanics()
        assertTrue(f.manager.forceReset(f.player))
        f.assertClean()
        assertEquals(8059, f.run.boss.id)
        assertEquals(VorkathState.SLEEPING, f.run.state)
        assertEquals(0, f.run.standardAttacks)
        assertTrue(f.manager.forceWake(f.player))
        f.advance(7)
        assertEquals(8061, f.run.boss.id)
        assertEquals(750, f.run.boss.hitpoints)
        assertEquals(VorkathState.ACTIVE, f.run.state)
        assertEquals(0, f.run.standardAttacks)
        assertEquals(0, f.run.shotsFired)
        assertTrue(f.run.pendingHits.isEmpty())
        assertTrue(f.run.pendingStandardEffects.isEmpty())
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun cache() {
            if (ServerCacheManager.objectSize() == 0) ServerCacheManager.init(240).close()
        }
    }
}

@OptIn(InternalApi::class)
private class VorkathFixture {
    val clock = MapClock(100)
    val collision = CollisionFlagMap()
    private val npcList = NpcList()
    val npcs = NpcRepository(clock, NpcRegistry(npcList, collision, EventBus()), npcList)
    val instances = mock(InstanceManager::class.java)
    val bars = mock(BossHpBarScript::class.java)
    val world = mock(WorldRepository::class.java)
    val playerHitModifier = StandardPlayerHitModifier(EventBus())
    val locs =
        mock(LocRepository::class.java) { call ->
            if (call.method.returnType == LocInfo::class.java) {
                LocInfo(2, CoordGrid(call.getArgument<Int>(0)), LocEntity(32000, 10, 0))
            } else Answers.RETURNS_DEFAULTS.answer(call)
        }
    val player =
        Player().apply {
            uuid = 1L
            observerUUID = 1L
            slotId = 1
            assignUid()
            coords = CoordGrid(2272, 4057)
            for (name in
                listOf("hitpoints", "prayer", "attack", "strength", "defence", "ranged", "magic")) {
                statMap.setBaseLevel("stat.$name", 99)
                statMap.setCurrentLevel("stat.$name", 99)
            }
            inv = invMap.getOrPut("inv.inv")
            worn = invMap.getOrPut("inv.worn")
        }
    private val placement =
        (InstanceAreaResolver().resolve(VORKATH_AREA) as InstanceAreaResolver.Result.Ready)
            .placement
    val session =
        InstanceSession(
            InstanceId(1),
            mutableSetOf(0), // RegionRegistry allocation ID, not the source map-square ID.
            1L,
            VORKATH_INSTANCE_KEY,
            InstanceSpec(0, 1, 100, 100, area = VORKATH_AREA, settingsRowId = -1),
            placement,
            InstanceAccess.Private,
        )
    private val boss = Npc(requireNotNull(ServerCacheManager.getNpc(8061)), CoordGrid(2269, 4062))
    val run = VorkathEncounterManager.Run(player, session, 1L, boss)
    val manager =
        VorkathEncounterManager(
            clock,
            instances,
            npcs,
            locs,
            mock(AiPlayerInteractions::class.java),
            collision,
            DefaultGameRandom(42L),
            mock(AccuracyFormulae::class.java),
            world,
            playerHitModifier,
            bars,
        )
    val poolVisual = LocInfo(2, player.coords, LocEntity(32000, 10, 0))

    init {
        for (x in 2240..2303 step 8) for (z in 4032..4095 step 8) {
            collision.allocateIfAbsent(x, z, 0)
        }
        npcs.add(boss, Int.MAX_VALUE)
        boss.assignSpawnOwner(player, clock.cycle)
        boss.hitpoints = 750
        run.timeline.wake(92)
        run.timeline.activate(99)
        `when`(instances.sessionForPlayer(player)).thenReturn(session)
        `when`(instances.leave(player, session, clock.cycle)).thenReturn(VORKATH_OUTSIDE)
        `when`(instances.localCoord(session, RegionLocal(0, 35, 63, 0, 0)))
            .thenReturn(CoordGrid(2240, 4032))
        for (x in 19..43) for (z in 20..44) {
            `when`(instances.localCoord(session, RegionLocal(0, 35, 63, x, z)))
                .thenReturn(CoordGrid(2240 + x, 4032 + z))
        }
        val field = VorkathEncounterManager::class.java.getDeclaredField("runs")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val runs = field.get(manager) as MutableMap<PlayerUid, VorkathEncounterManager.Run>
        runs[player.uid] = run
    }

    fun advance(ticks: Int = 1) =
        repeat(ticks) {
            clock.tick()
            player.currentMapClock = clock.cycle
            manager.tick(player)
        }

    fun completeMovement(npc: Npc, destination: CoordGrid) {
        npc.coords = destination
        npc.routeDestination.clear()
        npc.processArrivalAction()
    }
    fun launchSpawn(): Int {
        assertTrue(manager.forceSpecial(player, VorkathSpecial.ZOMBIFIED_SPAWN))
        val launch = run.spawnLaunchCycle
        advance(launch - clock.cycle)
        assertEquals(launch + 4, run.spawnArrivalCycle)
        return launch
    }

    fun projectiles(): List<ProjAnim> =
        mockingDetails(world)
            .invocations
            .filter { it.method.name == "projAnim" }
            .map { it.arguments[0] as ProjAnim }

    fun playerHits(): List<Hit> = playerQueues().mapNotNull { it.args as? Hit }

    fun playerQueues(): List<PlayerQueueList.Queue> {
        val queues = mutableListOf<PlayerQueueList.Queue>()
        val iterator = player.queueList.iterator()
        if (iterator != null)
            while (iterator.hasNext()) {
                queues += iterator.next()
            }
        return queues
    }

    fun incomingHit(damage: Int) =
        HitBuilder(
            type = HitType.Typeless,
            damage = damage,
            sourceUid = player.uid.packed,
            sourceSlot = player.slotId,
            isFromNpc = false,
            isFromPlayer = true,
            clientDelay = 0,
            righthandType = null,
            secondaryType = null,
            targetHitmark = 0,
            sourceHitmark = 0,
            publicHitmark = null,
            zeroDamageHitmarkLit = null,
            zeroDamageHitmarkTint = null,
            maxDamageHitmarkLit = null,
            targetMaxDamageThreshold = Int.MAX_VALUE,
            sourceMaxDamageThreshold = Int.MAX_VALUE,
        )

    fun seedMechanics() {
        val hit = incomingHit(40)
        manager.modifyNpcHit(player, boss, hit)
        assertEquals(40, hit.damage)
        run.state = VorkathState.ZOMBIFIED_SPAWN_SPECIAL
        run.acidTiles += player.coords
        run.acidVisuals += poolVisual
        run.acidSafeLane += player.coords.translate(1, 0)
        run.pendingAcidPools += PendingAcidPool(clock.cycle + 3, player.coords)
        run.pendingHits +=
            PendingTileHit(clock.cycle + 5, player.coords, 0, 121, VorkathTileAttack.FIREBALL)
        run.pendingFireballs += PendingFireball(clock.cycle + 1)
        run.pendingStandardEffects +=
            PendingStandardEffect(clock.cycle + 1, VorkathStandardAttack.PRAYER_DRAGONFIRE)
        run.pendingHeals += (clock.cycle + 1) to 10
        run.pendingSpawnTile = player.coords.translate(8, 0)
        val spawn =
            Npc(requireNotNull(ServerCacheManager.getNpc(8063)), player.coords.translate(2, 0))
        npcs.add(spawn, Int.MAX_VALUE)
        spawn.assignSpawnOwner(player, clock.cycle)
        run.spawn = spawn
        player.queueHit(
            source = boss,
            delay = 10,
            type = HitType.Typeless,
            damage = 1,
            modifier = playerHitModifier,
        )
        player.frozen = true
        run.ownsFreeze = true
    }

    fun assertClean() {
        assertTrue(run.pendingHits.isEmpty())
        assertTrue(run.pendingFireballs.isEmpty())
        assertTrue(run.pendingStandardEffects.isEmpty())
        assertTrue(run.pendingHeals.isEmpty())
        assertTrue(run.pendingAcidPools.isEmpty())
        assertTrue(run.acidTiles.isEmpty())
        assertTrue(run.acidVisuals.isEmpty())
        assertTrue(run.acidSafeLane.isEmpty())
        assertTrue(run.retiringSpawns.isEmpty())
        assertNull(run.spawn)
        assertNull(run.pendingSpawnTile)
        assertFalse(run.ownsFreeze)
        assertFalse(player.frozen)
        assertTrue(playerHits().isEmpty())
        assertEquals(Int.MAX_VALUE, run.spawnArrivalCycle)
        assertEquals(Int.MAX_VALUE, run.spawnLaunchCycle)
        assertEquals(Int.MAX_VALUE, run.spawnExplosionCycle)
        assertEquals(Int.MAX_VALUE, run.spawnDeathCycle)
        assertEquals(Int.MAX_VALUE, run.freezeCycle)
    }
}
