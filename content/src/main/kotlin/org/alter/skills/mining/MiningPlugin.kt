package org.alter.skills.mining

import io.github.oshai.kotlinlogging.KotlinLogging
import org.alter.api.*
import org.alter.api.ext.*
import org.alter.api.success
import org.alter.game.model.attr.AttributeKey
import org.alter.game.model.attr.INTERACTING_OBJ_ATTR
import org.alter.game.model.entity.GameObject
import org.alter.game.model.entity.ObjectTimerMap
import org.alter.game.model.entity.Player
import org.alter.game.model.item.Item
import org.alter.game.model.queue.QueueTask
import org.alter.game.model.timer.TimerKey
import org.alter.game.pluginnew.PluginEvent
import org.alter.game.pluginnew.event.EventManager
import org.alter.game.pluginnew.event.ReturnableEventListener
import org.alter.game.pluginnew.event.impl.onObjectOption
import org.alter.game.util.DbHelper
import org.alter.game.util.DbHelper.Companion.table
import org.alter.game.util.multiColumn
import org.alter.game.util.vars.LocType
import org.alter.rscm.RSCM
import org.alter.rscm.RSCM.getRSCM
import org.alter.rscm.RSCMType
import org.alter.skills.mining.MiningDefinitions.pickaxeData
import org.alter.skills.mining.MiningDefinitions.tableToRockData

class MiningPlugin : PluginEvent() {

    companion object {
        private val logger = KotlinLogging.logger {}

        /**
         * Sounds
         */
        const val ORE_OBTAINED_SOUND = 3600

        /**
         * Timer key for rock countdown timers.
         */
        private val ROCK_COUNTDOWN_TIMER = TimerKey()

        /**
         * Attribute key for tracking players actively mining a rock.
         */
        val ACTIVE_MINERS_ATTR = AttributeKey<MutableSet<Player>>()

        /**
         * Attribute key for storing the max countdown value for a rock.
         */
        val MAX_COUNTDOWN_ATTR = AttributeKey<Int>()
    }

    /**
     * Registers a rock deplete handler for a specific rock type.
     */
    fun onDeplete(rockTypeId: String, handler: RockDepleteHandler) {
        require(handler.rockTypeId == rockTypeId) { "Handler rock type (${handler.rockTypeId}) must match provided rock type ($rockTypeId)" }
        RSCM.requireRSCM(RSCMType.ROWTYPES, rockTypeId)
        val columnId = getRSCM(rockTypeId)
        ReturnableEventListener.on<RockDepleteEvent, Boolean> {
            where { rockType == columnId }
            then {
                return@then handler.handleDeplete(player, rockObject, world)
            }
        }
    }

    override fun init() {
        registerDepleteHandlers()

        table("tables.mining_rocks").forEach { rock ->
            val rockObjects = rock.multiColumn("columns.mining_rocks:rock_object", LocType)
            rockObjects.forEach { rockId ->
                try {
                    onObjectOption(rockId, "mine") {
                        player.queue { mineRock(player, rock) }
                    }
                } catch (e: Exception) {
                    logger.warn { "Rock object '$rockId' not found in cache or option not available, skipping registration: ${e.message}" }
                }
            }
        }

        startTimerUpdateTask()
    }

    private fun startTimerUpdateTask() {
        world.queue {
            while (true) {
                wait(1)
                updateAllRockTimers()
            }
        }
    }

    private fun updateAllRockTimers() {
        ObjectTimerMap.forEach { obj ->
            val activeMiners = obj.attr[ACTIVE_MINERS_ATTR] ?: return@forEach
            val maxCountdown = obj.attr[MAX_COUNTDOWN_ATTR] ?: return@forEach

            val currentTime = obj.getTimeLeft(ROCK_COUNTDOWN_TIMER)
            if (currentTime == 0) {
                if (activeMiners.isEmpty()) {
                    obj.attr.remove(ACTIVE_MINERS_ATTR)
                    obj.attr.remove(MAX_COUNTDOWN_ATTR)
                }
                return@forEach
            }

            if (activeMiners.isNotEmpty()) {
                val newTime = (currentTime - 1).coerceAtLeast(0)
                if (newTime > 0) {
                    obj.setTimer(ROCK_COUNTDOWN_TIMER, newTime)
                } else {
                    obj.removeTimer(ROCK_COUNTDOWN_TIMER)
                }
            } else {
                if (currentTime < maxCountdown) {
                    obj.setTimer(ROCK_COUNTDOWN_TIMER, (currentTime + 1).coerceAtMost(maxCountdown))
                } else {
                    obj.removeTimer(ROCK_COUNTDOWN_TIMER)
                    obj.attr.remove(MAX_COUNTDOWN_ATTR)
                    obj.attr.remove(ACTIVE_MINERS_ATTR)
                }
            }
        }
    }

    private fun registerDepleteHandlers() {
        // Placeholder for custom rock-specific handlers when required.
    }

    private fun getDepletedRock(rockData: MiningDefinitions.RockData): Int? = rockData.depletedRock

    private fun hasAnyPickaxe(player: Player): Boolean {
        player.equipment[EquipmentType.WEAPON.id]?.let { weapon ->
            if (pickaxeData.containsKey(weapon.id)) return true
        }

        return player.inventory.asSequence()
            .filterNotNull()
            .any { pickaxeData.containsKey(it.id) }
    }

    private fun getBestPickaxe(player: Player): MiningDefinitions.PickaxeData? {
        val miningLevel = player.getSkills().getBaseLevel(Skills.MINING)

        player.equipment[EquipmentType.WEAPON.id]?.let { weapon ->
            pickaxeData[weapon.id]?.takeIf { miningLevel >= it.levelReq }?.let { return it }
        }

        return player.inventory.asSequence()
            .filterNotNull()
            .mapNotNull { pickaxeData[it.id] }
            .filter { miningLevel >= it.levelReq }
            .maxByOrNull { it.levelReq }
    }

    private fun isDepletedRock(obj: GameObject, depletedId: Int, player: Player): Boolean {
        return obj.getTransform(player) == depletedId
    }

    private fun handleOreObtained(
        player: Player,
        rockData: MiningDefinitions.RockData,
    ): Boolean {
        val oreItem = rockData.ore
        if (player.inventory.add(oreItem, 1).hasSucceeded()) {
            player.addXp(Skills.MINING, rockData.xp)
            try {
                val oreName = Item(oreItem).getName().lowercase()
                player.message("You manage to mine some $oreName.")
            } catch (e: Exception) {
                player.message("You manage to mine some ore.")
            }
            player.playSound(ORE_OBTAINED_SOUND, volume = 1, delay = 0)
            return true
        } else {
            player.message("Your inventory is too full to hold any more ore.")
            player.animate(RSCM.NONE)
            return false
        }
    }

    private suspend fun QueueTask.depleteRock(
        player: Player,
        obj: GameObject,
        columnId: Int,
        rockData: MiningDefinitions.RockData,
    ): Boolean {
        val specialRock = EventManager.postWithResult(
            RockDepleteEvent(
                player = player,
                rockObject = obj,
                rockRscm = obj.id,
                rockType = columnId,
                world = world,
            ),
        )

        if (specialRock) {
            return true
        }

        getDepletedRock(rockData)?.let { depleted ->
            obj.replaceWith(world, depleted, rockData.respawnCycles, restoreOriginal = true)
        }

        player.message("The rock is now depleted.")
        player.animate(RSCM.NONE)

        return false
    }

    private fun resolveAnimationId(pickaxe: MiningDefinitions.PickaxeData, rockType: String): Int {
        if (rockType == "wall") {
            pickaxe.wallAnimationId?.let { return it }
        }
        return pickaxe.animationId
    }

    suspend fun QueueTask.mineRock(player: Player, rockTable: DbHelper) {
        val rockData = tableToRockData(rockTable)
        val miningLevel = player.getSkills().getBaseLevel(Skills.MINING)

        if (miningLevel < rockData.levelReq) {
            player.message("You need a Mining level of ${rockData.levelReq} to mine this rock.")
            return
        }

        if (!hasAnyPickaxe(player)) {
            player.message("You need a pickaxe to mine this rock.")
            return
        }

        val pickaxe = getBestPickaxe(player)
        if (pickaxe == null) {
            player.message("You do not have a pickaxe which you have the Mining level to use.")
            return
        }

        val obj = player.attr[INTERACTING_OBJ_ATTR]?.get() ?: return

        if (!obj.isSpawned(world)) {
            return
        }

        val depletedId = getDepletedRock(rockData) ?: -1

        val nearestTile = obj.findNearestTile(player.tile)
        player.faceTile(nearestTile)
        player.message("You swing your pickaxe at the rock.")

        val tickDelay = pickaxe.tickDelay
        val (low, high) = rockData.successRateLow to rockData.successRateHigh
        val animationId = resolveAnimationId(pickaxe, rockData.rockType)
        val miningAnimation = RSCM.getReverseMapping(RSCMType.SEQTYPES, animationId) ?: return

        player.loopAnim(miningAnimation)

        if (rockData.usesCountdown()) {
            val activeMiners = obj.attr.getOrPut(ACTIVE_MINERS_ATTR) { mutableSetOf() }
            activeMiners.add(player)

            if (!obj.hasTimers() || obj.getTimeLeft(ROCK_COUNTDOWN_TIMER) == 0) {
                obj.setTimer(ROCK_COUNTDOWN_TIMER, rockData.despawnTicks)
                obj.attr[MAX_COUNTDOWN_ATTR] = rockData.despawnTicks
            }
        }

        repeatWhile(delay = tickDelay, immediate = false, canRepeat = {
            val currentNearestTile = obj.findNearestTile(player.tile)
            player.tile.isWithinRadius(currentNearestTile, 1) &&
                !player.inventory.isFull &&
                obj.isSpawned(world) &&
                !isDepletedRock(obj, depletedId, player)
        }) {

            val success = success(low, high, miningLevel)

            if (success) {
                RockOreObtainedEvent(player, obj, rockData, rockTable.id).post()
                val oreObtained = handleOreObtained(player, rockData)
                if (!oreObtained) {
                    if (rockData.usesCountdown()) {
                        obj.attr[ACTIVE_MINERS_ATTR]?.remove(player)
                    }
                    return@repeatWhile
                }

                val shouldDeplete = when {
                    rockData.isInfiniteResource() -> false
                    rockData.usesCountdown() -> obj.getTimeLeft(ROCK_COUNTDOWN_TIMER) <= 0
                    else -> true
                }

                if (shouldDeplete) {
                    if (rockData.usesCountdown()) {
                        obj.attr[ACTIVE_MINERS_ATTR]?.remove(player)
                    }
                    depleteRock(player, obj, rockTable.id, rockData)
                    return@repeatWhile
                }
            }
        }

        if (rockData.usesCountdown()) {
            obj.attr[ACTIVE_MINERS_ATTR]?.remove(player)
        }

        player.stopLoopAnim()
    }
}

