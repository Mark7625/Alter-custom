package org.rsmod.api.instances

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcServerType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.concurrent.atomic.AtomicLong
import org.rsmod.api.instances.events.InstanceEndedEvent
import org.rsmod.api.instances.events.InstancePlayerJoinEvent
import org.rsmod.api.instances.events.InstancePlayerJoinUnboundEvent
import org.rsmod.api.instances.events.InstancePlayerLeaveEvent
import org.rsmod.api.instances.events.InstancePlayerLeaveUnboundEvent
import org.rsmod.api.instances.events.InstanceStartedEvent
import org.rsmod.api.instances.events.InstanceTimeTickEvent
import org.rsmod.api.instances.region.InstanceAreaResolver
import org.rsmod.api.instances.region.OsrsInstancing
import org.rsmod.api.instances.region.enterCoord
import org.rsmod.api.instances.region.localCoord
import org.rsmod.api.instances.timer.InstanceKillTimer
import org.rsmod.api.instances.timer.InstanceTiming
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.invtx.invTakeFee
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.output.mes
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.region.RegionRepository
import org.rsmod.events.EventBus
import org.rsmod.events.KeyedEvent
import org.rsmod.game.MapClock
import org.rsmod.game.damage.DamageContributions
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.npc.NpcUid
import org.rsmod.game.region.Region
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

@Singleton
public class InstanceManager
@Inject
constructor(
    private val regionRepo: RegionRepository,
    private val npcRepo: NpcRepository,
    private val playerList: PlayerList,
    private val eventBus: EventBus,
    private val areaResolver: InstanceAreaResolver,
    private val worldClock: MapClock,
    private val collision: CollisionFlagMap
) {
    private val nextId = AtomicLong(1)

    private val sessions = HashMap<InstanceId, InstanceSession>()
    private val regions = HashMap<InstanceId, Region>()
    private val regionToInstance = HashMap<Int, InstanceId>()
    private val ownerIndex = HashMap<Long, InstanceId>()
    private val playerIndex = HashMap<Long, InstanceId>()
    private val spawnedNpcs = HashMap<InstanceId, MutableList<Npc>>()
    private val npcInstanceIndex = HashMap<NpcUid, InstanceId>()

    public sealed interface Result {
        public data class Created(val session: InstanceSession, val enter: CoordGrid) : Result

        public data class Joined(val session: InstanceSession, val enter: CoordGrid) : Result

        public data class Failed(val reason: String) : Result
    }

    public fun create(
        owner: Player,
        key: String,
        spec: InstanceSpec,
        access: InstanceAccess,
        currentTick: Int,
    ): Result {
        val ownerId = owner.playerId()

        if (ownerIndex.containsKey(ownerId)) {
            return Result.Failed("You already have an active instance.")
        }

        if (spec.fee > 0 && !owner.invTakeFee(spec.fee)) {
            return Result.Failed("You need ${spec.fee} coins to create this instance.")
        }

        val placement =
            when (val resolved = areaResolver.resolve(spec.area)) {
                is InstanceAreaResolver.Result.Ready -> resolved.placement
            }

        val region = regionRepo.add(placement.regionTemplate)
        if (region == null) {
            areaResolver.release(spec.area, placement)
            if (spec.fee > 0) owner.invAdd(owner.inv, "obj.coins", spec.fee)
            return Result.Failed("No instance space available, try again shortly.")
        }

        val instanceId = allocateId()
        val session =
            InstanceSession(
                id = instanceId,
                regionIds = mutableSetOf(region.uid),
                owner = ownerId,
                key = key,
                spec = spec,
                placement = placement,
                access = access,
            )
        sessions[instanceId] = session
        regions[instanceId] = region
        registerRegion(instanceId, region.uid)
        ownerIndex[ownerId] = instanceId

        startSession(session, currentTick)

        val spawned = mutableListOf<Npc>()
        if (!spec.spawnOnFirstJoin) {
            spawnSessionNpcs(session, region, spawned, currentTick)
        }
        spawnedNpcs[instanceId] = spawned

        return Result.Created(session, session.enterCoord(region))
    }

    public fun createServerOwned(
        key: String,
        spec: InstanceSpec,
        access: InstanceAccess,
        currentTick: Int,
    ): InstanceSession? {
        val placement =
            when (val resolved = areaResolver.resolve(spec.area)) {
                is InstanceAreaResolver.Result.Ready -> resolved.placement
            }

        val region = regionRepo.add(placement.regionTemplate) ?: run {
            areaResolver.release(spec.area, placement)
            return null
        }

        val instanceId = allocateId()
        val session =
            InstanceSession(
                id = instanceId,
                regionIds = mutableSetOf(region.uid),
                owner = SERVER_OWNER_ID,
                key = key,
                spec = spec,
                placement = placement,
                access = access,
                isServerOwned = true,
            )
        sessions[instanceId] = session
        regions[instanceId] = region
        registerRegion(instanceId, region.uid)
        regionRepo.protect(region)
        spawnedNpcs[instanceId] = mutableListOf()
        return session
    }

    public fun sessionForOwner(ownerId: Long): InstanceSession? {
        val instanceId = ownerIndex[ownerId] ?: return null
        val session = sessions[instanceId]
        if (session == null) {
            ownerIndex.remove(ownerId)
            return null
        }
        return session
    }

    public fun sessionOwnedBy(key: String?, ownerId: Long): InstanceSession? {
        val session = sessionForOwner(ownerId) ?: return null
        if (key != null && session.key != key) {
            return null
        }
        return session
    }

    public fun sessionForKeyAndMemberName(key: String, memberName: String): InstanceSession? {
        val trimmed = memberName.trim()
        return sessionsForKey(key).firstOrNull { sessionMatchesMember(it, trimmed) }
    }

    public fun sessionForMemberName(memberName: String): InstanceSession? {
        val trimmed = memberName.trim()
        return sessions.values.firstOrNull { sessionMatchesMember(it, trimmed) }
    }

    public fun sessionOwnedByName(key: String?, ownerName: String): InstanceSession? {
        if (key != null) {
            return sessionForKeyAndMemberName(key, ownerName)
        }
        return sessionForMemberName(ownerName)
    }

    public fun memberLabel(playerId: Long): String? {
        val player = playerList.firstOrNull { it.uuid == playerId } ?: return null
        return player.displayName.ifBlank { player.username }.ifBlank { null }
    }

    public fun firstSession(): InstanceSession? = sessions.values.firstOrNull()

    public fun sessionsForKey(key: String): Collection<InstanceSession> =
        sessions.values.filter { it.key == key }

    public fun sessionForPlayer(player: Player): InstanceSession? =
        player.currentInstanceId()?.let(sessions::get)
            ?: playerIndex[player.playerId()]?.let(sessions::get)

    public fun sessionForId(id: InstanceId): InstanceSession? = sessions[id]

    public fun sessionForRegion(regionId: Int): InstanceSession? =
        regionToInstance[regionId]?.let(sessions::get)

    public fun localCoord(session: InstanceSession, local: RegionLocal): CoordGrid? {
        val region = regions[session.id] ?: return null
        return session.localCoord(region, local)
    }

    public fun contributionsFor(id: InstanceId): DamageContributions? =
        sessionForId(id)?.damageContributions

    public fun instanceForNpc(npc: Npc): InstanceId? = npcInstanceIndex[npc.uid]

    public fun npcsForInstance(id: InstanceId): List<Npc> = spawnedNpcs[id] ?: emptyList()

    public fun attachNpc(instanceId: InstanceId, npc: Npc) {
        spawnedNpcs.getOrPut(instanceId) { mutableListOf() }.add(npc)
        indexNpc(instanceId, npc)
    }

    public fun registerSessionNpc(player: Player, npc: Npc): Boolean {
        val instanceId =
            player.currentInstanceId() ?: playerIndex[player.playerId()] ?: return false
        attachNpc(instanceId, npc)
        sessionForId(instanceId)?.let { startBossKillTimer(it, npc, worldClock.cycle) }
        return true
    }

    public fun canSpawnBosses(player: Player): Boolean {
        val session = sessionForPlayer(player) ?: return true
        return !session.arenaExpired
    }

    public fun join(
        player: Player,
        session: InstanceSession,
        currentTick: Int,
        code: String? = null,
        forceAccess: Boolean = false,
    ): Result {
        val playerId = player.playerId()
        if (!forceAccess && !canJoin(playerId, session, code)) {
            return Result.Failed("You do not have access to that instance.")
        }
        if (session.state is SessionState.Grace) {
            return Result.Failed("That instance is ending and no longer accepts players.")
        }
        if (!session.occupants.contains(playerId) && session.isFull()) {
            return Result.Failed("That instance is full.")
        }
        val region = regions[session.id] ?: return Result.Failed("That instance no longer exists.")
        if (!session.isServerOwned && !regionRepo.isValid(region)) {
            destroy(session)
            return Result.Failed("That instance is no longer available. Please create a new instance.")
        }
        abandonOwnedSessionIfEmpty(player, session, currentTick)
        if (session.occupants.isEmpty() && !session.arenaExpired) {
            resetNpcs(session, region, currentTick)
            startSession(session, currentTick)
        }
        return Result.Joined(session, session.enterCoord(region))
    }

    /** Call after the player has telejumped into the instance (e.g. after an enter transition). */
    public fun finalizeEntry(player: Player, session: InstanceSession, currentTick: Int) {
        val playerId = player.playerId()
        if (playerId in session.occupants) {
            return
        }
        assignOccupant(player, session)
        publishPlayerJoin(player, session)
    }

    /** Destroys an owned instance that was created but never entered (e.g. logout during fade). */
    public fun cancelPendingEntry(player: Player, currentTick: Int) {
        val playerId = player.playerId()
        val ownedId = ownerIndex[playerId] ?: return
        val session = sessions[ownedId] ?: run {
            ownerIndex.remove(playerId)
            return
        }
        if (playerId in session.occupants) {
            return
        }
        destroy(session)
    }

    private fun abandonOwnedSessionIfEmpty(
        player: Player,
        target: InstanceSession,
        currentTick: Int,
    ) {
        val playerId = player.playerId()
        val ownedId = ownerIndex[playerId] ?: return
        if (ownedId == target.id) {
            return
        }
        val owned = sessions[ownedId] ?: return
        if (playerId in owned.occupants) {
            removeOccupant(player, owned, currentTick)
        } else if (owned.owner == playerId) {
            destroy(owned)
        }
    }

    private fun resetNpcs(session: InstanceSession, region: Region, currentTick: Int) {
        spawnedNpcs.remove(session.id)?.forEach { untagAndDelete(it) }
        val spawned = mutableListOf<Npc>()
        spawnSessionNpcs(session, region, spawned, currentTick)
        spawnedNpcs[session.id] = spawned
    }

    private fun spawnSessionNpcs(
        session: InstanceSession,
        region: Region,
        out: MutableList<Npc>,
        currentTick: Int,
    ) {
        if (session.arenaExpired) {
            return
        }
        val placement = session.placement
        for (entry in placement.npcSpawns) {
            val type = ServerCacheManager.getNpc(entry.npcType.asRSCM(RSCMType.NPC)) ?: continue
            val npc = Npc(type, session.localCoord(region, entry.coord))
            npc.mode = type.defaultMode
            npcRepo.add(npc, Int.MAX_VALUE)
            indexNpc(session.id, npc)
            out += npc
            startBossKillTimer(session, npc, currentTick)
        }
    }

    private fun canJoin(playerId: Long, session: InstanceSession, code: String?): Boolean =
        when (val access = session.access) {
            is InstanceAccess.Private -> playerId == session.owner
            is InstanceAccess.Friends -> true
            is InstanceAccess.Code -> code != null && code == access.value
        }

    public fun leave(player: Player, session: InstanceSession, currentTick: Int): CoordGrid {
        removeOccupant(player, session, currentTick)
        return session.placement.exitCoord
    }

    public fun handleLogout(player: Player, currentTick: Int) {
        val session = sessionForPlayer(player)
        if (session != null) {
            val exit = session.placement.exitCoord
            removeOccupant(player, session, currentTick)
            player.attr[InstanceAttributes.LOGIN_EXIT_COORD] = exit.packed
            return
        }
        cancelPendingEntry(player, currentTick)
    }

    public fun handleDeath(player: Player, currentTick: Int) {
        val session = sessionForPlayer(player) ?: return
        removeOccupant(player, session, currentTick)
    }

    public fun handleBossKill(npc: Npc, currentTick: Int) {
        val instanceId = instanceForNpc(npc) ?: return
        val session = sessionForId(instanceId) ?: return
        if (!countsAsFightBoss(session, npc)) {
            return
        }

        completeBossKillTimer(session, currentTick)

        if (session.state != SessionState.Active || !session.gracePending) {
            return
        }
        if (isBossAlive(session)) {
            return
        }
        beginGrace(session, currentTick)
    }

    public fun tickReclaim(currentTick: Int) {
        reconcileOccupants(currentTick)
        tickTimed(currentTick)
        val expired = sessions.values.filter { it.isExpired(currentTick) }
        expired.forEach(::destroy)
    }

    private fun reconcileOccupants(currentTick: Int) {
        for (session in sessions.values.toList()) {
            val region = regions[session.id] ?: continue
            val center = session.enterCoord(region)
            val leavers =
                session.occupants.filter { occupant ->
                    val player = playerList.firstOrNull { it.uuid == occupant }
                    player == null || player.coords.chebyshevDistance(center) > REGION_RADIUS
                }
            for (occupant in leavers) {
                val player = playerList.firstOrNull { it.uuid == occupant } ?: continue
                removeOccupant(player, session, currentTick)
            }
        }
    }

    private fun tickTimed(currentTick: Int) {
        for (session in sessions.values.toList()) {
            when (val state = session.state) {
                SessionState.Active -> tickActiveTimer(session, currentTick)
                is SessionState.Grace -> tickGraceTimer(session, currentTick, state)
                is SessionState.Reclaim -> Unit
            }
        }
    }

    private fun tickActiveTimer(session: InstanceSession, currentTick: Int) {
        val limit = session.spec.timeLimitTicks ?: return
        if (session.occupants.isEmpty()) {
            return
        }

        val elapsed = session.elapsedTicks(currentTick)
        val remaining = limit - elapsed

        publish(
            InstanceTimeTickEvent(
                key = session.key,
                instanceId = session.id,
                ownerId = session.owner,
                spec = session.spec,
                currentTick = currentTick,
                startedAtTick = session.startedAtTick,
                elapsedTicks = elapsed,
                remainingTicks = remaining,
            )
        )

        InstanceTiming.notifyThresholds(
            remainingTicks = remaining,
            totalTicks = limit,
            sent = session.timeWarningsSent,
            players = InstanceTiming.playersIn(session, playerList),
        ) { duration ->
            "Your instance will end in $duration."
        }

        if (remaining <= 0) {
            if (session.markArenaExpired()) {
                messageOccupantsRed(session, INSTANCE_ARENA_EXPIRED_MESSAGE)
            }
            if (isBossAlive(session)) {
                session.markGracePending()
                return
            }
            beginGrace(session, currentTick)
        }
    }

    private fun startBossKillTimer(session: InstanceSession, npc: Npc, currentTick: Int) {
        if (!countsAsFightBoss(session, npc) || !InstanceKillTimer.tracksKillTime(session)) {
            return
        }
        session.bossFightStartTick = currentTick
    }

    private fun completeBossKillTimer(session: InstanceSession, currentTick: Int) {

        if (session.isServerOwned) return

        val startTick = session.bossFightStartTick ?: return
        session.bossFightStartTick = null
        if (!InstanceKillTimer.tracksKillTime(session)) {
            return
        }
        val elapsed = currentTick - startTick
        for (player in InstanceTiming.playersIn(session, playerList)) {
            if (session.damageContributions.damageBy(player) <= 0) continue
            InstanceKillTimer.reportKillTime(player, session.key, elapsed)
        }
    }

    private fun countsAsFightBoss(session: InstanceSession, npc: Npc): Boolean {
        val bossTypes = session.spec.bossNpcs ?: return false
        return bossTypes.any { bossType ->
            npcMatchesBossType(npc, bossType)
        }
    }

    private fun isBossAlive(session: InstanceSession): Boolean {
        val bossTypes = session.spec.bossNpcs
        val spawnTypes = session.placement.npcSpawns.map { it.npcType }.toSet()

        if (bossTypes.isNullOrEmpty() && spawnTypes.isEmpty()) {
            return false
        }

        val npcs = spawnedNpcs[session.id] ?: return false
        return npcs.any { npc ->
            npc.isSlotAssigned &&
                npc.hitpoints > 0 &&
                npcCountsAsBoss(npc, bossTypes, spawnTypes)
        }
    }

    private fun npcCountsAsBoss(
        npc: Npc,
        bossTypes: List<NpcServerType>?,
        spawnTypes: Set<String>,
    ): Boolean {
        if (bossTypes?.any { npcMatchesBossType(npc, it) } == true) {
            return true
        }

        return spawnTypes.any { spawnType ->
            npc.isType(spawnType) || npc.isVisType(spawnType)
        }
    }

    private fun npcMatchesBossType(npc: Npc, bossType: NpcServerType): Boolean {
        return npc.type == bossType ||
            npc.visType == bossType ||
            npc.isType(bossType.internalName) ||
            npc.isVisType(bossType.internalName)
    }

    private fun tickGraceTimer(
        session: InstanceSession,
        currentTick: Int,
        grace: SessionState.Grace,
    ) {
        val remaining = grace.deadlineTick - currentTick

        if (session.occupants.isNotEmpty()) {
            InstanceTiming.notifyThresholds(
                remainingTicks = remaining,
                totalTicks = session.spec.graceTicks,
                sent = session.graceWarningsSent,
                players = InstanceTiming.playersIn(session, playerList),
            ) { duration ->
                "You will be removed from the instance in $duration."
            }
        }

        if (remaining <= 0) {
            expireGrace(session, currentTick)
        }
    }

    private fun beginGrace(session: InstanceSession, currentTick: Int) {
        if (session.state is SessionState.Grace) {
            return
        }
        despawnSessionNpcs(session)
        session.enterGrace(currentTick)
        val graceLabel = InstanceTiming.formatRemaining(session.spec.graceTicks)
        messageOccupants(
            session,
            "The instance timer has ended. You have $graceLabel to collect loot before being removed.",
        )
    }

    private fun expireGrace(session: InstanceSession, currentTick: Int) {
        val exit = session.placement.exitCoord
        for (occupant in session.occupants.toList()) {
            val player = playerList.firstOrNull { it.uuid == occupant } ?: continue
            removeOccupant(player, session, currentTick)
            player.coords = exit
        }
        if (session.isServerOwned) {
            if (session.state is SessionState.Grace) {
                resetServerOwned(session, currentTick)
            }
        } else {
            destroy(session)
        }
    }

    private fun resetServerOwned(session: InstanceSession, currentTick: Int) {
        session.resetState()
        val region = regions[session.id] ?: return
        spawnedNpcs.remove(session.id)?.forEach { untagAndDelete(it) }
        val spawned = mutableListOf<Npc>()
        spawnSessionNpcs(session, region, spawned, currentTick)
        spawnedNpcs[session.id] = spawned
    }

    private fun despawnSessionNpcs(session: InstanceSession) {
        spawnedNpcs.remove(session.id)?.forEach { untagAndDelete(it) }
    }

    private fun messageOccupants(session: InstanceSession, text: String) {
        for (player in InstanceTiming.playersIn(session, playerList)) {
            player.mes(text, ChatType.Engine)
        }
    }

    private fun messageOccupantsRed(session: InstanceSession, text: String) {
        messageOccupants(session, "<col=ff0000>$text</col>")
    }

    private fun destroy(session: InstanceSession) {
        if (sessions.remove(session.id) == null) return
        publish(
            InstanceEndedEvent(
                key = session.key,
                instanceId = session.id,
                ownerId = session.owner,
                spec = session.spec,
            )
        )
        areaResolver.release(session.spec.area, session.placement)
        spawnedNpcs.remove(session.id)?.forEach { untagAndDelete(it) }
        for (regionId in session.regionIds) {
            regionToInstance.remove(regionId, session.id)
        }
        val region = regions.remove(session.id)
        if (session.isServerOwned && region != null) {
            regionRepo.unprotect(region)
        }
        ownerIndex.remove(session.owner)
        playerIndex.entries.removeIf { it.value == session.id }
        val exitCoord = session.placement.exitCoord
        for (occupant in session.occupants) {
            val player = playerList.firstOrNull { it.uuid == occupant } ?: continue
            if (exitCoord != CoordGrid.ZERO) {
                player.attr[InstanceAttributes.LOGIN_EXIT_COORD] = exitCoord.packed
            }
            player.clearInstance()
        }
    }

    private fun removeOccupant(player: Player, session: InstanceSession, currentTick: Int) {
        val playerId = player.playerId()
        session.removeOccupant(playerId, currentTick)
        clearOccupant(player)
        publishPlayerLeave(player, session)
        if (
            session.spec.destroyWhenEmpty &&
                !session.isServerOwned &&
                session.occupants.isEmpty() &&
                session.state !is SessionState.Grace
        ) {
            destroy(session)
            return
        }
        if (session.isServerOwned && session.occupants.isEmpty() && session.state is SessionState.Grace) {
            resetServerOwned(session, currentTick)
        }
    }

    private fun assignOccupant(player: Player, session: InstanceSession) {
        val playerId = player.playerId()
        session.addOccupant(playerId)
        player.assignInstance(session.id)
        playerIndex[playerId] = session.id
    }

    private fun clearOccupant(player: Player) {
        player.clearInstance()
        playerIndex.remove(player.playerId())
    }

    private fun startSession(session: InstanceSession, currentTick: Int) {
        session.damageContributions.clear()
        session.markStarted(currentTick)
        publish(
            InstanceStartedEvent(
                key = session.key,
                instanceId = session.id,
                ownerId = session.owner,
                spec = session.spec,
                startedAtTick = session.startedAtTick,
            )
        )
    }

    private fun publishPlayerJoin(player: Player, session: InstanceSession) {
        publish(
            InstancePlayerJoinEvent(
                player = player,
                key = session.key,
                instanceId = session.id,
                ownerId = session.owner,
            )
        )
        eventBus.publish(
            InstancePlayerJoinUnboundEvent(
                player = player,
                key = session.key,
                instanceId = session.id,
                ownerId = session.owner,
            )
        )
    }

    private fun publishPlayerLeave(player: Player, session: InstanceSession) {
        publish(
            InstancePlayerLeaveEvent(
                player = player,
                key = session.key,
                instanceId = session.id,
                ownerId = session.owner,
            )
        )
        eventBus.publish(
            InstancePlayerLeaveUnboundEvent(
                player = player,
                key = session.key,
                instanceId = session.id,
                ownerId = session.owner,
            )
        )
    }

    private fun publish(event: KeyedEvent) {
        eventBus.publish(event)
    }

    private fun allocateId(): InstanceId = InstanceId(nextId.getAndIncrement())

    private fun registerRegion(instanceId: InstanceId, regionId: Int) {
        regionToInstance[regionId] = instanceId
        sessions[instanceId]?.registerRegion(regionId)
    }

    private fun indexNpc(instanceId: InstanceId, npc: Npc) {
        if (npc.uid == NpcUid.NULL) return
        npcInstanceIndex[npc.uid] = instanceId
    }

    private fun untagAndDelete(npc: Npc) {
        if (npc.uid != NpcUid.NULL) {
            npcInstanceIndex.remove(npc.uid)
        }
        if (!npc.isSlotAssigned) {
            return
        }
        npcRepo.del(npc, Int.MAX_VALUE)
    }

    private fun Player.playerId(): Long = requireNotNull(uuid) { "Player has no uuid: $this" }

    private fun sessionMatchesMember(session: InstanceSession, name: String): Boolean {
        if (playerNameMatches(session.owner, name)) {
            return true
        }
        return session.occupants.any { playerNameMatches(it, name) }
    }

    private fun playerNameMatches(playerId: Long, name: String): Boolean {
        val player = playerList.firstOrNull { it.uuid == playerId } ?: return false
        return playerNameMatches(player, name)
    }

    private fun playerNameMatches(player: Player, name: String): Boolean =
        player.displayName.equals(name, ignoreCase = true) ||
            player.username.equals(name, ignoreCase = true)

    private companion object {
        private const val REGION_RADIUS = OsrsInstancing.PADDING_BETWEEN_INSTANCES
        private const val SERVER_OWNER_ID: Long = 0L
    }
}
