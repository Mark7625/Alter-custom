package org.rsmod.content.bosses.vorkath

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.MoveRestrict
import dev.openrune.types.ObjectServerType
import dev.openrune.types.aconverted.SpotanimType
import dev.openrune.types.aconverted.SynthType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.bossbar.plugin.BossHpBarScript
import org.rsmod.api.combat.commons.CombatEffects
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.instances.InstanceAccess
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.instances.RegionLocal
import org.rsmod.api.mechanics.toxins.impl.PlayerVenom
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.heal
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.owner.assignSpawnOwner
import org.rsmod.api.npc.owner.isSpawnOwnedBy
import org.rsmod.api.player.combatClearQueue
import org.rsmod.api.player.disablePrayers
import org.rsmod.api.player.hit.modifier.StandardPlayerHitModifier
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.route.StepFactory
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcUid
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.game.map.collision.get
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

@Singleton
internal class VorkathEncounterManager
@Inject
constructor(
    private val clock: MapClock,
    private val instances: InstanceManager,
    private val npcs: NpcRepository,
    private val locs: LocRepository,
    private val interactions: AiPlayerInteractions,
    private val collision: CollisionFlagMap,
    private val random: GameRandom,
    private val accuracy: AccuracyFormulae,
    private val world: WorldRepository,
    private val playerHitModifier: StandardPlayerHitModifier,
    private val bossBars: BossHpBarScript,
) {
    private val log = InlineLogger()
    private val spawnSteps = StepFactory(collision)
    private val COMBAT_STATES = setOf(
        VorkathState.ACTIVE, VorkathState.ACID_SPECIAL, VorkathState.ZOMBIFIED_SPAWN_SPECIAL
    )
    private val runs = mutableMapOf<PlayerUid, Run>()
    private var generation = 1L

    fun validateAssets() {
        listOf(
                VorkathAssets.SLEEPING,
                VorkathAssets.SLEEPING_NOOP,
                VorkathAssets.ACTIVE,
                VorkathAssets.SPAWN,
                VorkathAssets.TORFINN,
                VorkathAssets.TORFINN_COLLECT,
                VorkathAssets.TORFINN_RELLEKKA,
                VorkathAssets.TORFINN_COLLECT_RELLEKKA,
            )
            .forEach(::requireNpc)
        listOf(
                VorkathAssets.WAKE_ANIM,
                VorkathAssets.ICE_WALL_JUMP_ANIM,
                VorkathAssets.MELEE_ANIM,
                VorkathAssets.RANGED_ANIM,
                VorkathAssets.RANGED_UP_ANIM,
                VorkathAssets.ACID_ANIM,
                VorkathAssets.SPAWN_DEATH_ANIM,
                VorkathAssets.SPAWN_ATTACK_ANIM,
            )
            .forEach { internal ->
                val id = internal.asRSCM(RSCMType.SEQ)
                requireNotNull(ServerCacheManager.getAnim(id)) {
                    "Missing Vorkath sequence: $internal"
                }
            }
        listOf(
                VorkathAssets.RANGED_TRAVEL,
                VorkathAssets.MAGIC_TRAVEL,
                VorkathAssets.DRAGONFIRE_TRAVEL,
                VorkathAssets.VENOM_DRAGONFIRE_TRAVEL,
                VorkathAssets.PRAYER_DRAGONFIRE_TRAVEL,
                VorkathAssets.RANGED_IMPACT,
                VorkathAssets.MAGIC_IMPACT,
                VorkathAssets.FIREBALL_TRAVEL,
                VorkathAssets.RAPID_FIRE_TRAVEL,
                VorkathAssets.ACID_TRAVEL,
                VorkathAssets.SPAWN_TRAVEL,
            )
            .forEach { it.asRSCM(RSCMType.SPOTANIM) }
        VORKATH_CRATER_ENTRANCE_LOC_IDS.forEach {
            requireNotNull(ServerCacheManager.getObject(it)) { "Missing Vorkath entrance loc: $it" }
        }
        acidPoolType()
    }

    fun teleportOutside(player: Player): Boolean {
        val current = instances.sessionForPlayer(player)
        if (current != null && current.key != VORKATH_INSTANCE_KEY) {
            player.mes("Leave your current instance before travelling to Ungael.")
            return false
        }
        runs[player.uid]?.let { abort(player, "staff travel command", teleport = false) }
        if (current != null) instances.leave(player, current, clock.cycle)
        prepare(player)
        PathingEntityCommon.telejump(player, collision, VORKATH_OUTSIDE)
        player.mes("You arrive outside Vorkath's arena. Climb over the ice chunks to enter.")
        return true
    }

    fun teleportRellekka(player: Player): Boolean {
        runs[player.uid]?.let { abort(player, "Torfinn travel", teleport = false) }
        val current = instances.sessionForPlayer(player)
        if (current != null && current.key == VORKATH_INSTANCE_KEY) {
            instances.leave(player, current, clock.cycle)
        }
        prepare(player)
        PathingEntityCommon.telejump(player, collision, VORKATH_RELLEKKA)
        player.mes("Torfinn sails you back to Rellekka.")
        return true
    }

    fun enter(player: Player, wallLocalX: Int): Boolean {
        if (player.hitpoints <= 0 || player.isAccessProtected) {
            player.mes("Finish what you are doing before entering Vorkath's arena.")
            return false
        }
        if (runs.containsKey(player.uid) || instances.sessionForPlayer(player) != null) {
            player.mes("You already have an active instance.")
            return false
        }
        val activeType = requireNpc(VorkathAssets.ACTIVE)
        requireNpc(VorkathAssets.SLEEPING)
        requireNpc(VorkathAssets.SPAWN)
        val result =
            instances.create(
                owner = player,
                key = VORKATH_INSTANCE_KEY,
                spec = vorkathSpec(activeType),
                access = InstanceAccess.Private,
                currentTick = clock.cycle,
            )
        if (result !is InstanceManager.Result.Created) {
            player.mes(
                (result as? InstanceManager.Result.Failed)?.reason ?: "The arena is unavailable."
            )
            return false
        }
        prepare(player)
        val entry = wallTile(result.session, wallLocalX, inside = false) ?: result.enter
        PathingEntityCommon.telejump(player, collision, entry)
        instances.finalizeEntry(player, result.session, clock.cycle)
        player.lootDropDuration?.let { player.attr[VORKATH_PREVIOUS_DROP_DURATION] = it }
        player.lootDropDuration = VORKATH_DROP_DURATION
        val sleeping =
            spawnNpc(player, result.session, VorkathAssets.SLEEPING, bossCoord(result.session))
        sleeping.movementLocked = true
        val run =
            Run(
                player = player,
                session = result.session,
                generation = generation++,
                firstSpecial = VorkathRules.firstSpecial(random.of(2)),
                boss = sleeping,
                initialState = VorkathState.ENTERING,
            )
        runs[player.uid] = run
        run.state = VorkathState.SLEEPING
        player.mes("Vorkath is sleeping. Poke him when you are ready.")
        log.info {
            "[Vorkath] player=${player.displayName} generation=${run.generation} instance=${result.session.id}"
        }
        return true
    }

    fun poke(player: Player, npc: Npc): Boolean {
        val run = runs[player.uid] ?: return false
        if (!inArena(run) || !player.isSlotAssigned || player.hitpoints <= 0) return false
        if (run.state != VorkathState.SLEEPING || run.boss !== npc || !npc.isSpawnOwnedBy(player))
            return false
        npc.mode = null
        npc.movementLocked = true
        run.timeline.wake(clock.cycle)
        npc.transmog(requireNpc(VorkathAssets.SLEEPING_NOOP), Int.MAX_VALUE)
        npc.anim(VorkathAssets.WAKE_ANIM)
        player.mes("Vorkath awakens.")
        return true
    }

    fun tickAll() {
        runs.values.toList().forEach { tick(it.player) }
    }

    internal fun auditRun(player: Player): Run? = runs[player.uid]

    fun tick(player: Player) {
        val run = runs[player.uid] ?: return
        if (
            !player.isSlotAssigned ||
                player.pendingLogout ||
                player.loggingOut
        ) {
            abort(player, "player unavailable", teleport = false, logout = player.loggingOut || player.pendingLogout)
            return
        }
        if (player.hitpoints <= 0) {
            // Keep membership until the death hook chooses the existing Vorkath reclaim behavior.
            if (run.state != VorkathState.ENDED) {
                run.state = VorkathState.ENDED
                clearMechanics(run)
                bossBars.onClose(player, run.boss, instant = true)
            }
            return
        }
        if (!inArena(run)) {
            abort(player, "left arena", teleport = false)
            return
        }
        if (run.state in COMBAT_STATES && !run.boss.isSlotAssigned) {
            abort(player, "boss removed", teleport = false)
            return
        }
        if (run.state in COMBAT_STATES && run.boss.hitpoints <= 0) {
            // The death queue runs later in the tick. Do not launch pending effects before it.
            clearMechanics(run)
            return
        }
        if (run.state in COMBAT_STATES) {
            bossBars.onUpdate(player, run.boss)
            val heals = run.pendingHeals.filter { it.first <= clock.cycle }
            run.pendingHeals.removeAll(heals.toSet())
            heals.forEach { run.boss.heal(it.second, showHitsplat = true) }
            processFireballLaunches(run)
            processPendingStandardEffects(run)
            processPending(run)
        }
        if (clock.cycle >= run.spawnRetireCycle) {
            run.retiringSpawns.forEach { if (it.isSlotAssigned) npcs.del(it, Int.MAX_VALUE) }
            run.retiringSpawns.clear()
            run.spawnRetireCycle = Int.MAX_VALUE
        }
        when (run.state) {
            VorkathState.ENTERING,
            VorkathState.SLEEPING,
            VorkathState.RESETTING,
            VorkathState.DYING,
            VorkathState.ENDED -> Unit
            VorkathState.AWAKENING -> if (clock.cycle >= run.wakeCycle) activate(run)
            VorkathState.ACTIVE -> {
                if (run.timeline.attackDue(clock.cycle) &&
                    run.standardAttacks == VORKATH_STANDARD_ATTACKS) beginSpecial(run)
                else keepAggressive(run)
            }
            VorkathState.ACID_SPECIAL -> tickAcid(run)
            VorkathState.ZOMBIFIED_SPAWN_SPECIAL -> tickSpawn(run)
            VorkathState.LOOTABLE ->
                if (clock.cycle >= run.respawnCycle) {
                    run.state = VorkathState.RESETTING
                    respawn(run)
                }
        }
    }

    fun attack(access: StandardNpcAccess, target: Player) {
        val run = runs[target.uid] ?: return
        if (run.boss !== access.npc || !validTarget(run) || !run.timeline.attackDue(clock.cycle)) return
        if (run.standardAttacks == VORKATH_STANDARD_ATTACKS) {
            beginSpecial(run)
            return
        }
        val attack = chooseStandard(run, target)
        if (attack == VorkathStandardAttack.FIREBALL) {
            launchFireball(run)
            return
        }
        launchStandard(run, target, attack)
    }

    fun isEncounterNpc(npc: Npc): Boolean = runs.values.any { it.owns(npc) }

    fun isOwnedBy(player: Player, npc: Npc): Boolean =
        runs[player.uid]?.let { it.owns(npc) && npc.isSpawnOwnedBy(player) } == true

    fun attackDenial(player: Player, npc: Npc): String? {
        val run = runs[player.uid] ?: return "You do not have an active Vorkath encounter."
        if (!run.owns(npc)) return "That creature belongs to another encounter."
        if (
            npc === run.boss &&
                run.state != VorkathState.ACTIVE &&
                run.state != VorkathState.ACID_SPECIAL
        ) {
            return if (run.state == VorkathState.SLEEPING || run.state == VorkathState.AWAKENING) {
                "Poke Vorkath to wake him first."
            } else {
                "Vorkath is immune during this phase."
            }
        }
        return null
    }

    fun modifyNpcHit(player: Player, npc: Npc, hit: HitBuilder) {
        val run = runs[player.uid]
        if (run == null || !run.owns(npc) || !npc.isSpawnOwnedBy(player)) {
            hit.damage = 0
            return
        }
        if (npc === run.boss) {
            hit.damage =
                when (run.state) {
                    VorkathState.ACTIVE -> hit.damage
                    VorkathState.ACID_SPECIAL -> VorkathRules.acidDamage(hit.damage)
                    else -> 0
                }
        }
    }

    fun beginBossDeath(npc: Npc): Run? {
        val run = runs.values.firstOrNull { it.boss === npc } ?: return null
        if (
            run.state == VorkathState.DYING ||
                run.state == VorkathState.LOOTABLE ||
                run.state == VorkathState.RESETTING ||
                run.state == VorkathState.ENDED
        )
            return null
        run.state = VorkathState.DYING
        run.player.combatClearQueue()
        clearMechanics(run)
        if (run.rewardEligible) {
            val newKillcount = run.player.vars["varp.kc_vorkath"] + 1
            VarPlayerIntMapSetter.set(run.player, "varp.kc_vorkath", newKillcount)
            run.killcount = newKillcount
            run.killTicks = (clock.cycle - run.startCycle).coerceAtLeast(0)
        }
        return run
    }

    fun finishBossDeath(npc: Npc) {
        val run = runs.values.firstOrNull { it.boss === npc } ?: return
        if (runs[run.player.uid] !== run) return
        if (run.rewardEligible) {
            val oldPb = run.player.attr[VORKATH_PERSONAL_BEST_TICKS]
            val personalBest = oldPb == null || run.killTicks < oldPb
            if (personalBest) run.player.attr[VORKATH_PERSONAL_BEST_TICKS] = run.killTicks
            val suffix = if (personalBest) " <col=ff0000>New personal best!</col>" else ""
            run.player.mes(
                "<col=ff9900>Vorkath kill ${run.killcount}: ${VorkathRules.formatTicks(run.killTicks)}.$suffix</col>"
            )
        } else {
            run.player.mes(
                "Developer Vorkath kill complete; no loot, killcount, or record awarded."
            )
        }
        run.state = VorkathState.LOOTABLE
        run.respawnCycle = clock.cycle
        run.boss = npc
        respawn(run)
    }

    fun beginSpawnDeath(npc: Npc) {
        val run = runs.values.firstOrNull { it.spawn === npc } ?: return
        if (run.state != VorkathState.ZOMBIFIED_SPAWN_SPECIAL ||
            run.spawnDeathCycle != Int.MAX_VALUE) return
        npc.movementLocked = true
        npc.mode = null
        npc.hideAllOps()
        // Anchor to the lethal hit, independently of death-queue processing order.
        run.spawnDeathCycle = clock.cycle + 3
    }

    fun canFinishDeath(run: Run, npc: Npc): Boolean =
        runs[run.player.uid] === run && run.boss === npc && run.state == VorkathState.DYING &&
            inArena(run) && run.player.hitpoints > 0

    fun isActive(player: Player): Boolean = runs[player.uid]?.state != null

    fun escape(player: Player, localX: Int): Boolean {
        if (!runs.containsKey(player.uid)) return false
        val publicWallTile =
            CoordGrid(
                x = (VORKATH_SOURCE_REGION_X * 64) + localX,
                z = (VORKATH_SOURCE_REGION_Z * 64) + VORKATH_WALL_OUTSIDE_LOCAL_Z,
                level = 0,
            )
        abort(player, "left through the ice chunks", teleport = false)
        if (player.isSlotAssigned) {
            PathingEntityCommon.telejump(player, collision, publicWallTile)
        }
        player.mes("You leave Vorkath's arena.")
        return true
    }

    fun wallTile(player: Player, localX: Int, inside: Boolean): CoordGrid? {
        val session = instances.sessionForPlayer(player) ?: return null
        if (session.key != VORKATH_INSTANCE_KEY) return null
        return wallTile(session, localX, inside)
    }

    private fun wallTile(session: InstanceSession, localX: Int, inside: Boolean): CoordGrid? {
        if (localX !in VORKATH_WALL_MIN_LOCAL_X..VORKATH_WALL_MAX_LOCAL_X) return null
        val localZ = if (inside) VORKATH_WALL_INSIDE_LOCAL_Z else VORKATH_WALL_OUTSIDE_LOCAL_Z
        return instances.localCoord(session, RegionLocal(0, 35, 63, localX, localZ))
    }

    fun abort(player: Player, reason: String, teleport: Boolean, logout: Boolean = false) {
        val run = runs.remove(player.uid) ?: return
        run.state = VorkathState.ENDED
        clearMechanics(run)
        bossBars.onClose(player, run.boss, instant = true)
        if (run.boss.isSlotAssigned) npcs.del(run.boss, Int.MAX_VALUE)
        player.lootDropDuration = player.attr[VORKATH_PREVIOUS_DROP_DURATION]
        player.attr.remove(VORKATH_PREVIOUS_DROP_DURATION)
        if (logout) {
            instances.handleLogout(player, clock.cycle)
        } else {
            val exit = instances.leave(player, run.session, clock.cycle)
            if (teleport && player.isSlotAssigned)
                PathingEntityCommon.telejump(player, collision, exit)
        }
        prepare(player)
        log.info {
            "[Vorkath] player=${player.displayName} generation=${run.generation} abort=$reason"
        }
    }

    internal fun forceWake(player: Player): Boolean {
        val run = runs[player.uid] ?: return false
        if (run.state != VorkathState.SLEEPING) return false
        run.rewardEligible = false
        return poke(player, run.boss)
    }

    internal fun forceSpecial(player: Player, special: VorkathSpecial): Boolean {
        val run = runs[player.uid] ?: return false
        if (run.state != VorkathState.ACTIVE) return false
        run.rewardEligible = false
        clearMechanics(run)
        run.state = VorkathState.ACTIVE
        run.timeline.forceSpecial(clock.cycle, special)
        run.boss.actionDelay = clock.cycle
        beginSpecial(run)
        return true
    }

    internal fun forceAttack(player: Player, attack: VorkathStandardAttack): Boolean {
        val run = runs[player.uid] ?: return false
        if (run.state != VorkathState.ACTIVE) return false
        run.rewardEligible = false
        run.timeline.forceAttack(clock.cycle)
        run.boss.actionDelay = clock.cycle
        if (attack == VorkathStandardAttack.FIREBALL) {
            launchFireball(run)
        } else {
            launchStandard(run, player, attack)
        }
        return true
    }

    internal fun forceReset(player: Player): Boolean {
        val run = runs[player.uid] ?: return false
        run.rewardEligible = false
        run.state = VorkathState.RESETTING
        clearMechanics(run)
        if (run.boss.isSlotAssigned) npcs.del(run.boss, Int.MAX_VALUE)
        respawn(run)
        return true
    }

    private fun activate(run: Run) {
        val sleeping = run.boss
        val active = spawnNpc(run.player, run.session, VorkathAssets.ACTIVE, bossCoord(run.session))
        active.baseHitpointsLvl = VORKATH_MAX_HITPOINTS
        active.hitpoints = VORKATH_MAX_HITPOINTS
        active.movementLocked = true
        active.apRangeOverride = 32
        active.apRequiresLineOfSight = false
        run.boss = active
        run.timeline.activate(clock.cycle)
        active.actionDelay = run.timeline.nextAttackCycle
        run.startCycle = clock.cycle
        if (sleeping.isSlotAssigned) npcs.del(sleeping, Int.MAX_VALUE)
        active.apPlayer2(run.player, interactions)
        bossBars.onOpen(run.player, active)
    }

    private fun respawn(run: Run) {
        clearMechanics(run)
        bossBars.onClose(run.player, run.boss, instant = true)
        if (run.boss.isSlotAssigned) npcs.del(run.boss, Int.MAX_VALUE)
        val sleeping =
            spawnNpc(run.player, run.session, VorkathAssets.SLEEPING, bossCoord(run.session))
        sleeping.movementLocked = true
        run.boss = sleeping
        run.timeline.reset(VorkathRules.firstSpecial(random.of(2)))
        run.player.mes("Vorkath settles back into a deep sleep.")
    }

    private fun keepAggressive(run: Run) {
        if (run.boss.isSlotAssigned && run.boss.mode == null) {
            run.boss.apPlayer2(run.player, interactions)
        }
    }

    private fun launchStandard(
        run: Run,
        target: Player,
        attack: VorkathStandardAttack,
    ) {
        if (!validTarget(run) || !run.timeline.attackDue(clock.cycle)) return
        val spec = VorkathProjectiles.standard(attack)
        val projectile = spec?.build(run.boss, target.coords, target)
        val dragonfire =
            attack == VorkathStandardAttack.DRAGONFIRE ||
                attack == VorkathStandardAttack.VENOM_DRAGONFIRE ||
                attack == VorkathStandardAttack.PRAYER_DRAGONFIRE
        val accurate =
            when (attack) {
                VorkathStandardAttack.MELEE ->
                    accuracy.rollMeleeAccuracy(run.boss, target, MeleeAttackType.Crush, random)
                VorkathStandardAttack.RANGED ->
                    accuracy.rollRangedAccuracy(run.boss, target, random)
                else -> accuracy.rollMagicAccuracy(run.boss, target, random)
            }
        val protection = if (dragonfire) VorkathDragonfire.snapshot(target) else null
        val maximum =
            when (attack) {
                VorkathStandardAttack.MELEE, VorkathStandardAttack.RANGED -> 32
                VorkathStandardAttack.MAGIC -> 30
                VorkathStandardAttack.FIREBALL -> 0
                else ->
                    if (accurate) {
                        requireNotNull(protection).maximum
                    } else {
                        requireNotNull(protection).resistedMaximum
                    }
            }
        val rolled = if (accurate || dragonfire) random.of(0..maximum) else 0
        val damage = if (dragonfire) requireNotNull(protection).damage(rolled, attack) else rolled
        val hitType =
            when (attack) {
                VorkathStandardAttack.MELEE -> HitType.Melee
                VorkathStandardAttack.RANGED -> HitType.Ranged
                VorkathStandardAttack.MAGIC -> HitType.Magic
                else -> HitType.Typeless
            }

        run.boss.facePlayer(target)
        run.boss.anim(
            if (attack == VorkathStandardAttack.MELEE) {
                VorkathAssets.MELEE_ANIM
            } else {
                VorkathAssets.RANGED_ANIM
            }
        )
        projectile?.let(world::projAnim)
        run.timeline.standardLaunched(clock.cycle)
        run.boss.actionDelay = run.timeline.nextAttackCycle

        // Player hit queues count their first processing tick, including this NPC launch tick.
        val impactDelay = (projectile?.endTime?.div(30) ?: 0) + 1
        VorkathProjectiles.impact(attack)?.let {
            PathingEntityCommon.spotanim(
                target,
                it,
                delay = projectile?.endTime ?: 0,
                height = 124,
                slot = 0,
            )
        }
        target.queueHit(
            source = run.boss,
            delay = impactDelay,
            type = hitType,
            damage = damage,
            modifier = playerHitModifier,
        )
        if (
            attack == VorkathStandardAttack.VENOM_DRAGONFIRE ||
                attack == VorkathStandardAttack.PRAYER_DRAGONFIRE
        ) {
            run.pendingStandardEffects += PendingStandardEffect(clock.cycle + impactDelay, attack)
        }
    }

    private fun launchFireball(run: Run) {
        if (!validTarget(run) || !run.timeline.attackDue(clock.cycle)) return
        run.timeline.standardLaunched(clock.cycle)
        run.boss.actionDelay = run.timeline.nextAttackCycle
        run.boss.anim(VorkathAssets.RANGED_UP_ANIM, delay = 2)
        run.pendingFireballs += PendingFireball(clock.cycle + 1)
    }

    private fun processFireballLaunches(run: Run) {
        val due = run.pendingFireballs.filter { it.launchCycle <= clock.cycle }
        run.pendingFireballs.removeAll(due.toSet())
        for (launch in due) {
            val tile = run.player.coords
            launchTileProjectile(run, tile, VorkathProjectiles.FIREBALL)
            run.pendingHits += PendingTileHit(
                clock.cycle + VorkathProjectiles.FIREBALL.impactTicks, tile, 0, 121,
                VorkathTileAttack.FIREBALL
            )
        }
    }

    private fun processPendingStandardEffects(run: Run) {
        val due = run.pendingStandardEffects.filter { it.impactCycle <= clock.cycle }
        run.pendingStandardEffects.removeAll(due.toSet())
        if (!validTarget(run)) return
        for (effect in due) {
            when (effect.attack) {
                VorkathStandardAttack.VENOM_DRAGONFIRE -> PlayerVenom.tryVenom(run.player)
                VorkathStandardAttack.PRAYER_DRAGONFIRE -> run.player.disablePrayers()
                else -> Unit
            }
        }
    }

    private fun beginSpecial(run: Run) {
        if (!validTarget(run)) return
        when (run.timeline.beginSpecial(clock.cycle)) {
            VorkathSpecial.ACID -> beginAcid(run)
            VorkathSpecial.ZOMBIFIED_SPAWN -> beginSpawn(run)
        }
        run.boss.mode = null
    }

    private fun beginAcid(run: Run) {
        run.boss.anim(VorkathAssets.ACID_ANIM)
        clearAcid(run)
        val selected = VorkathAcidLayout.select(
            boss = run.boss.coords,
            player = run.player.coords,
            isWalkable = { collision[it] and CollisionFlag.BLOCK_WALK == 0 },
            choose = { it[random.of(it.size)] },
        )
        run.acidSafeLane += VorkathAcidLayout.exitLane(run.boss.coords)
            .filter { it !in selected && collision[it] and CollisionFlag.BLOCK_WALK == 0 }
        selected.forEach { tile ->
            launchTileProjectile(run, tile, VorkathProjectiles.ACID)
            run.pendingAcidPools += PendingAcidPool(clock.cycle + 3, tile)
        }
    }

    private fun tickAcid(run: Run) {
        processPendingAcid(run)
        if (run.player.coords in run.acidTiles) {
            val damage = random.of(1..10)
            val hit =
                run.player.queueHit(
                    source = run.boss,
                    delay = 1,
                    type = HitType.Typeless,
                    damage = damage,
                    modifier = playerHitModifier,
                )
            run.pendingHeals += (clock.cycle + 1) to hit.damage
        }
        if (run.timeline.rapidShotDue(clock.cycle)) {
            val tile = run.player.coords
            run.boss.facePlayer(run.player)
            val delay = launchTileProjectile(run, tile, VorkathProjectiles.RAPID_FIRE)
            run.pendingHits +=
                PendingTileHit(
                    impactCycle = clock.cycle + delay,
                    tile = tile,
                    minimum = 25,
                    maximum = 41,
                    kind = VorkathTileAttack.RAPID_FIRE,
                )
            run.timeline.rapidLaunched(clock.cycle)
            if (run.shotsFired == VORKATH_ACID_SHOTS) clearAcid(run)
        }
        if (
            clock.cycle >= run.timeline.specialStartCycle + 33
        )
            finishSpecial(run, recoveryTicks = 0)
    }

    private fun processPending(run: Run) {
        val due = run.pendingHits.filter { it.impactCycle <= clock.cycle }
        run.pendingHits.removeAll(due.toSet())
        for (hit in due) {
            val graphic = if (hit.kind == VorkathTileAttack.FIREBALL)
                VorkathAssets.FIREBALL_IMPACT else VorkathAssets.RAPID_FIRE_IMPACT
            val height = if (hit.kind == VorkathTileAttack.FIREBALL) 38 else 30
            world.spotanimMap(SpotanimType(graphic), hit.tile, height)
            if (hit.kind == VorkathTileAttack.FIREBALL)
                world.soundArea(
                    hit.tile,
                    SynthType(VorkathAssets.FIREBALL_IMPACT_SOUND),
                    radius = 12,
                )
            else
                world.soundArea(
                    hit.tile,
                    SynthType(VorkathAssets.RAPID_FIRE_IMPACT_SOUND),
                    radius = 12,
                )
            val distance = run.player.coords.chebyshevDistance(hit.tile)
            val maximum =
                when (hit.kind) {
                    VorkathTileAttack.FIREBALL -> VorkathRules.fireballMaximum(distance)
                    VorkathTileAttack.RAPID_FIRE -> if (distance == 0) hit.maximum else 0
                }
            if (maximum <= 0) continue
            run.player.queueHit(
                source = run.boss,
                delay = 1,
                type = HitType.Typeless,
                damage =
                    if (hit.kind == VorkathTileAttack.FIREBALL) {
                        VorkathRules.fireballDamage(random.of(0..hit.maximum), distance)
                    } else {
                        random.of(hit.minimum..maximum)
                    },
                modifier = playerHitModifier,
            )
        }
    }

    private fun processPendingAcid(run: Run) {
        val due = run.pendingAcidPools.filter { it.impactCycle <= clock.cycle }
        run.pendingAcidPools.removeAll(due.toSet())
        for (pool in due) {
            if (pool.tile in run.acidTiles) continue
            val visual =
                locs.add(
                    pool.tile,
                    acidPoolType(),
                    Int.MAX_VALUE,
                    LocAngle[random.of(4)],
                    LocShape.CentrepieceStraight,
                )
            run.acidTiles += pool.tile
            run.acidVisuals += visual
        }
    }

    private fun beginSpawn(run: Run) {
        run.boss.anim(VorkathAssets.RANGED_ANIM)
        val ice = VorkathProjectiles.ICE.build(run.boss, run.player.coords, run.player)
        world.projAnim(ice)
        PathingEntityCommon.spotanim(
            run.player,
            369,
            delay = ice.endTime,
            height = 0,
            slot = 0,
        )
        run.freezeCycle = clock.cycle + ice.endTime / 30
        run.spawnLaunchCycle = run.freezeCycle
    }

    private fun tickSpawn(run: Run) {
        if (clock.cycle >= run.freezeCycle) {
            run.freezeCycle = Int.MAX_VALUE
            // This freeze belongs to the special; existing PvP immunity must not prevent it.
            run.player.clearQueue("queue.com_retaliate_npc")
            run.player.clearInteraction()
            CombatEffects.clearFreezeImmunity(run.player)
            CombatEffects.freeze(run.player, VORKATH_FREEZE_TICKS)
            run.ownsFreeze = run.player.frozen
        }
        if (clock.cycle >= run.spawnLaunchCycle) {
            run.spawnLaunchCycle = Int.MAX_VALUE
            val candidates = arenaTiles(run.session).filter {
                it.chebyshevDistance(run.player.coords) == VORKATH_SPAWN_DISTANCE &&
                    !bossOccupies(run, it) && collision[it] and CollisionFlag.BLOCK_WALK == 0 &&
                    spawnCanReach(run, it)
            }
            if (candidates.isEmpty()) {
                log.error { "[Vorkath] no valid spawn location at distance $VORKATH_SPAWN_DISTANCE for ${run.session.id}" }
                finishSpecial(run)
                return
            }
            val tile = candidates[random.of(candidates.size)]
            run.boss.anim(VorkathAssets.RANGED_UP_ANIM)
            launchTileProjectile(run, tile, VorkathProjectiles.SPAWN)
            run.pendingSpawnTile = tile
            run.spawnArrivalCycle = clock.cycle + 4
        }
        val pendingTile = run.pendingSpawnTile
        if (pendingTile != null && clock.cycle >= run.spawnArrivalCycle) {
            val spawn = spawnNpc(run.player, run.session, VorkathAssets.SPAWN, pendingTile)
            spawn.baseHitpointsLvl = 38
            spawn.hitpoints = 38
            spawn.mode = null
            run.spawn = spawn
            run.pendingSpawnTile = null
            return // First walk is the tick after arrival.
        }
        val spawn = run.spawn ?: return
        if (!spawn.isSlotAssigned) {
            finishSpecial(run)
            return
        }
        if (spawn.hitpoints <= 0) {
            if (clock.cycle >= run.spawnDeathCycle) {
                spawn.anim(VorkathAssets.SPAWN_DEATH_ANIM)
                run.retiringSpawns += spawn
                run.spawn = null
                run.spawnRetireCycle = clock.cycle + 2
                finishSpecial(run)
            }
            return
        }
        if (clock.cycle >= run.spawnExplosionCycle) {
            run.spawnExplosionCycle = Int.MAX_VALUE
            val damage = VorkathRules.zombifiedSpawnDamage(spawn.hitpoints)
            run.player.queueHit(
                source = spawn,
                delay = 1,
                type = HitType.Typeless,
                damage = damage,
                modifier = playerHitModifier,
            )
            // Captures remove the NPC on the explosion's damage tick (contact +1).
            if (spawn.isSlotAssigned) npcs.del(spawn, Int.MAX_VALUE)
            run.spawn = null
            finishSpecial(run, recoveryTicks = 0)
        } else if (run.spawnExplosionCycle != Int.MAX_VALUE) {
            return
        } else if (spawn.coords.chebyshevDistance(run.player.coords) == 0) {
            beginSpawnExplosion(run, spawn, contactCycle = clock.cycle)
        } else {
            spawn.facePlayer(run.player)
            // Arrival is processed after movement and player hits, before NPC/world updates.
            // The map clock has advanced by then, so retain this movement tick's cycle.
            val movementCycle = clock.cycle
            val next = nextSpawnStep(run, spawn.coords)
            if (next == CoordGrid.NULL) return
            // Only the final step may overlap the target. Captures retain boss-body clipping.
            spawn.moveRestrict =
                if (next == run.player.coords) MoveRestrict.PassThru else MoveRestrict.Normal
            spawn.walk(next) {
                beginSpawnExplosion(run, spawn, contactCycle = movementCycle)
            }
        }
    }

    internal fun nextSpawnStep(run: Run, source: CoordGrid): CoordGrid {
        val target = run.player.coords
        if (source == target) return target
        if (source.chebyshevDistance(target) == 1 && !bossOccupies(run, target)) {
            val diagonal = source.x != target.x && source.z != target.z
            val crossesBossCorner = diagonal &&
                (bossOccupies(run, CoordGrid(source.x, target.z, source.level)) ||
                    bossOccupies(run, CoordGrid(target.x, source.z, source.level)))
            if (!crossesBossCorner && spawnSteps.validated(source, target) == target) return target
        }
        return spawnSteps.validated(source, target, extraFlag = CollisionFlag.BLOCK_NPCS)
    }

    internal fun spawnCanReach(run: Run, source: CoordGrid): Boolean {
        // The ring and edge-clipping are captured; rejecting stuck direct routes is inferred.
        var tile = source
        repeat(VORKATH_SPAWN_DISTANCE * 2) {
            if (tile == run.player.coords) return true
            tile = nextSpawnStep(run, tile)
            if (tile == CoordGrid.NULL) return false
        }
        return tile == run.player.coords
    }

    private fun beginSpawnExplosion(run: Run, spawn: Npc, contactCycle: Int) {
        if (
            !validTarget(run) ||
                run.state != VorkathState.ZOMBIFIED_SPAWN_SPECIAL ||
                run.spawn !== spawn ||
                !spawn.isSlotAssigned ||
                spawn.hitpoints <= 0 ||
                run.spawnExplosionCycle != Int.MAX_VALUE ||
                spawn.coords != run.player.coords
        ) return
        spawn.anim(VorkathAssets.SPAWN_ATTACK_ANIM)
        world.spotanimMap(
            SpotanimType(VorkathAssets.SPAWN_EXPLOSION), spawn.coords, height = 30, delay = 22
        )
        releaseFreeze(run)
        spawn.movementLocked = true
        spawn.abortRoute()
        run.spawnExplosionCycle = contactCycle + 1
    }

    private fun finishSpecial(run: Run, recoveryTicks: Int = 1) {
        clearAcid(run)
        clearSpawn(run)
        releaseFreeze(run)
        run.timeline.finishSpecial(clock.cycle, recoveryTicks)
        run.boss.actionDelay = run.timeline.nextAttackCycle
        run.boss.apPlayer2(run.player, interactions)
    }

    private fun clearMechanics(run: Run) {
        clearAcid(run)
        run.pendingHits.clear()
        run.pendingFireballs.clear()
        run.pendingStandardEffects.clear()
        run.pendingHeals.clear()
        clearSpawn(run)
        run.retiringSpawns.forEach { if (it.isSlotAssigned) npcs.del(it, Int.MAX_VALUE) }
        run.retiringSpawns.clear()
        run.spawnRetireCycle = Int.MAX_VALUE
        releaseFreeze(run)
        run.boss.clearInteraction()
        run.boss.clearFacingLock()
        run.boss.mode = null
        run.boss.vars["varn.attacking_player"] = PlayerUid.NULL.packed
        run.boss.vars["varn.aggressive_player"] = PlayerUid.NULL.packed
        if (run.player.vars["varp.aggressive_npc"] == run.boss.uid.packed)
            VarPlayerIntMapSetter.set(run.player, "varp.aggressive_npc", NpcUid.NULL.packed)
        run.player.combatClearQueue()
        run.player.clearInteraction()
        run.player.resetSpotanim()
    }

    private fun releaseFreeze(run: Run) {
        run.freezeCycle = Int.MAX_VALUE
        if (!run.ownsFreeze) return
        CombatEffects.unfreeze(run.player)
        run.player.resetSpotanim()
        run.ownsFreeze = false
    }

    private fun clearAcid(run: Run) {
        run.acidVisuals.forEach { locs.del(it, Int.MAX_VALUE) }
        run.acidVisuals.clear()
        run.acidTiles.clear()
        run.acidSafeLane.clear()
        run.pendingAcidPools.clear()
    }

    private fun clearSpawn(run: Run) {
        run.pendingSpawnTile = null
        run.spawnLaunchCycle = Int.MAX_VALUE
        run.spawnArrivalCycle = Int.MAX_VALUE
        run.spawnExplosionCycle = Int.MAX_VALUE
        run.spawnDeathCycle = Int.MAX_VALUE
        val spawn = run.spawn ?: return
        run.spawn = null
        if (spawn.isSlotAssigned) npcs.del(spawn, Int.MAX_VALUE)
    }

    private fun inArena(run: Run): Boolean {
        if (instances.sessionForPlayer(run.player) !== run.session) return false
        // Session regionIds are allocation IDs, not packed OSRS map-square coordinates.
        val origin =
            instances.localCoord(run.session, RegionLocal(0, 35, 63, 0, 0)) ?: return false
        val tile = run.player.coords
        return tile.level == origin.level &&
            tile.x in origin.x until (origin.x + 64) &&
            tile.z in origin.z until (origin.z + 64)
    }

    private fun validTarget(run: Run): Boolean =
        runs[run.player.uid] === run && inArena(run) &&
            run.player.isSlotAssigned && !run.player.pendingLogout && !run.player.loggingOut &&
            run.player.hitpoints > 0 && run.boss.isSlotAssigned && run.boss.hitpoints > 0 &&
            run.state in COMBAT_STATES

    private fun chooseStandard(run: Run, target: Player): VorkathStandardAttack {
        val adjacent = run.boss.isWithinDistance(target, 1) && !bossOccupies(run, target.coords)
        val weights = VorkathRules.standardWeights(adjacent)
        var roll = random.of(weights.values.sum())
        for ((attack, weight) in weights) {
            if (roll < weight) return attack
            roll -= weight
        }
        return VorkathStandardAttack.MAGIC
    }

    private fun arenaTiles(session: InstanceSession): List<CoordGrid> {
        val tiles = ArrayList<CoordGrid>(550)
        // All four bounds are witnessed by the supplied spawn launches.
        for (x in 21..43) {
            for (z in 22..44) {
                instances.localCoord(session, RegionLocal(0, 35, 63, x, z))?.let(tiles::add)
            }
        }
        return tiles
    }

    private fun bossOccupies(run: Run, tile: CoordGrid): Boolean =
        tile.x in run.boss.coords.x until (run.boss.coords.x + run.boss.size) &&
            tile.z in run.boss.coords.z until (run.boss.coords.z + run.boss.size)

    private fun bossCoord(session: InstanceSession): CoordGrid =
        requireNotNull(instances.localCoord(session, RegionLocal(0, 35, 63, 29, 30)))

    private fun spawnNpc(
        player: Player,
        session: InstanceSession,
        internal: String,
        coords: CoordGrid,
    ): Npc {
        val type = requireNpc(internal)
        val npc = Npc(type, coords)
        npc.mode = null
        npc.assignSpawnOwner(player, clock.cycle)
        npcs.add(npc, VORKATH_NPC_LIFETIME)
        instances.attachNpc(session.id, npc)
        return npc
    }

    private fun launchTileProjectile(run: Run, tile: CoordGrid, spec: VorkathProjectile): Int {
        world.projAnim(spec.build(run.boss, tile, facingTile = run.player.coords))
        return spec.impactTicks
    }

    private fun acidPoolType(): ObjectServerType =
        requireNotNull(ServerCacheManager.getObject(VORKATH_ACID_POOL_LOC_ID)) {
            "Missing Vorkath acid-pool loc: $VORKATH_ACID_POOL_LOC_ID"
        }

    private fun requireNpc(internal: String) =
        requireNotNull(ServerCacheManager.getNpc(internal.asRSCM(RSCMType.NPC))) {
            "Missing Vorkath npc definition: $internal"
        }

    private fun prepare(player: Player) {
        player.combatClearQueue()
        player.clearInteraction()
        player.resetAnim()
    }

    internal data class Run(
        val player: Player,
        val session: InstanceSession,
        val generation: Long,
        var boss: Npc,
        val initialState: VorkathState = VorkathState.SLEEPING,
        val firstSpecial: VorkathSpecial = VorkathSpecial.ACID,
        var startCycle: Int = 0,
        var respawnCycle: Int = Int.MAX_VALUE,
        var spawnArrivalCycle: Int = Int.MAX_VALUE,
        var freezeCycle: Int = Int.MAX_VALUE,
        var spawnLaunchCycle: Int = Int.MAX_VALUE,
        var spawnExplosionCycle: Int = Int.MAX_VALUE,
        var spawnDeathCycle: Int = Int.MAX_VALUE,
        var spawnRetireCycle: Int = Int.MAX_VALUE,
        var ownsFreeze: Boolean = false,
        val retiringSpawns: MutableList<Npc> = mutableListOf(),
        val pendingFireballs: MutableList<PendingFireball> = mutableListOf(),
        val pendingStandardEffects: MutableList<PendingStandardEffect> = mutableListOf(),
        val pendingHeals: MutableList<Pair<Int, Int>> = mutableListOf(),
        var spawn: Npc? = null,
        var pendingSpawnTile: CoordGrid? = null,
        var killcount: Int = 0,
        var killTicks: Int = 0,
        var rewardEligible: Boolean = true,
        val acidTiles: MutableSet<CoordGrid> = linkedSetOf(),
        val acidSafeLane: MutableSet<CoordGrid> = linkedSetOf(),
        val acidVisuals: MutableList<LocInfo> = mutableListOf(),
        val pendingAcidPools: MutableList<PendingAcidPool> = mutableListOf(),
        val pendingHits: MutableList<PendingTileHit> = mutableListOf(),
    ) {
        val timeline = VorkathTimeline(firstSpecial).also { it.state = initialState }
        var state: VorkathState
            get() = timeline.state
            set(value) { timeline.state = value }
        val standardAttacks: Int get() = timeline.standardAttacks
        val nextSpecial: VorkathSpecial get() = timeline.nextSpecial
        val wakeCycle: Int get() = timeline.wakeCycle
        val shotsFired: Int get() = timeline.shotsFired
        fun owns(npc: Npc): Boolean = npc === boss || npc === spawn || npc in retiringSpawns
    }
}
