package org.rsmod.content.skills.construction.features

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcMode
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.baseConstructionLvl
import org.rsmod.api.poh.PohDataStore
import org.rsmod.api.poh.PohHotspotSlot
import org.rsmod.api.poh.PohHouse
import org.rsmod.api.poh.PohHouseEnteredEvent
import org.rsmod.api.poh.PohHouseExitedEvent
import org.rsmod.api.poh.PohManager
import org.rsmod.api.poh.pohServantPay
import org.rsmod.api.poh.pohServantType
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.npc.isSuccess
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpNpc1
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.game.map.collision.isZoneValid
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/** One of the five hireable servants; [type] is stored in `varbit.poh_servant_type`. */
internal data class PohServant(
    val type: Int,
    val npc: String,
    val displayName: String,
    val level: Int,
    val wage: Int,
    val capacity: Int,
)

/** Wiki-verified servant table: Construction level, wage per pay period and fetch capacity. */
internal object PohServants {
    val ALL: List<PohServant> =
        listOf(
            PohServant(1, "npc.poh_servant_dogsbody", "Rick", level = 20, wage = 500, capacity = 6),
            PohServant(
                2,
                "npc.poh_servant_waiter_woman",
                "Maid",
                level = 25,
                wage = 1_000,
                capacity = 10,
            ),
            PohServant(
                3,
                "npc.poh_servant_cook_woman",
                "Cook",
                level = 30,
                wage = 3_000,
                capacity = 16,
            ),
            PohServant(
                4,
                "npc.poh_servant_maitre_d_man",
                "Butler",
                level = 40,
                wage = 5_000,
                capacity = 20,
            ),
            PohServant(
                5,
                "npc.poh_servant_demon",
                "Demon butler",
                level = 50,
                wage = 10_000,
                capacity = 26,
            ),
        )

    fun forType(type: Int): PohServant? = ALL.firstOrNull { it.type == type }
}

/**
 * Spawns and reclaims the npcs that live inside a player's house: the hired servant (near the owner
 * on entry) and the dungeon/oubliette/treasure-room guards (on their built guard hotspots, mapped
 * through [PohGuards]).
 *
 * [respawn] is idempotent: spawned npcs are tracked per owner and deleted before every respawn, so
 * repeated enter/rebuild events never duplicate them. Region reclaim also deletes npcs standing in
 * unregistered zones (`RegionRegistry.clearAllZones`), so [despawn]'s deletes tolerate npcs that
 * are already gone. Spawns go through [NpcRegistry.add] - the repository's `add(npc,
 * Int.MAX_VALUE)` path double-registers permanent spawns.
 */
@Singleton
class PohNpcSpawner
@Inject
constructor(
    private val manager: PohManager,
    private val dataStore: PohDataStore,
    private val npcRegistry: NpcRegistry,
    private val collision: CollisionFlagMap,
) {
    private val spawned = HashMap<Long, MutableList<Npc>>()

    /** Clears any stale house npcs for [player] and spawns the current servant and guards. */
    fun respawn(player: Player) {
        despawn(player)
        val owner = player.uuid ?: return
        val active = manager.activeHouse(player) ?: return
        val tracked = mutableListOf<Npc>()
        spawnGuards(active, tracked)
        spawnServant(player, active, tracked)
        if (tracked.isNotEmpty()) {
            spawned[owner] = tracked
        }
    }

    /** Deletes every npc previously spawned for [player]'s house. */
    fun despawn(player: Player) {
        val owner = player.uuid ?: return
        val tracked = spawned.remove(owner) ?: return
        for (npc in tracked) {
            // Region reclaim may have deleted the npc already; the failed delete is benign.
            npcRegistry.del(npc)
        }
    }

    private fun spawnGuards(active: PohManager.ActiveHouse, tracked: MutableList<Npc>) {
        for ((slot, builtLoc) in active.house.furniture) {
            val npcName = PohGuards.NPC_BY_BUILT_LOC[builtLoc] ?: continue
            val room = active.house.rooms[slot.roomSlot] ?: continue
            val hotspot = dataStore.hotspot(slot.hotspotIndex)
            val coords = manager.hotspotCoords(active.region, slot.roomSlot, room, hotspot)
            spawn(npcName, coords)?.let(tracked::add)
        }
    }

    private fun spawnServant(
        player: Player,
        active: PohManager.ActiveHouse,
        tracked: MutableList<Npc>,
    ) {
        val servant = PohServants.forType(player.pohServantType) ?: return
        if (!manager.isInOwnHouse(player)) {
            return
        }
        val coords = walkableNear(player.coords.translate(1, 0))
        spawn(servant.npc, coords)?.let(tracked::add)
    }

    private fun spawn(npcName: String, coords: CoordGrid): Npc? {
        val type = ServerCacheManager.getNpc(npcName.asRSCM(RSCMType.NPC)) ?: return null
        val npc = Npc(type, coords)
        npc.mode = NpcMode.None
        val result = npcRegistry.add(npc)
        return if (result.isSuccess()) npc else null
    }

    private fun walkableNear(coords: CoordGrid): CoordGrid {
        if (isWalkable(coords)) {
            return coords
        }
        for (radius in 1..2) {
            for (dx in -radius..radius) {
                for (dz in -radius..radius) {
                    val candidate = coords.translate(dx, dz)
                    if (isWalkable(candidate)) {
                        return candidate
                    }
                }
            }
        }
        return coords
    }

    private fun isWalkable(coords: CoordGrid): Boolean =
        collision.isZoneValid(coords) && !collision.isWalkBlocked(coords)
}

/**
 * Servant hiring at the Servants' Guild and in-house servant services.
 *
 * Hiring (Talk-to on the guild npcs) requires no current servant, the wiki Construction level and
 * two bedrooms with built beds. In the owner's house the hired servant offers fetch-from-bank
 * (clamped to the servant's carry capacity), wage payment and dismissal. `varbit.poh_servant_pay`
 * counts unpaid services: the servant demands wages after [DEMAND_THRESHOLD] and refuses further
 * service at [REFUSE_THRESHOLD] until paid.
 *
 * TODO: sawmill runs, un-noting, take-to-bank, serving food, following the owner between rooms and
 *   the bell-pull "Call Servant" summon are not implemented yet.
 */
class ServantScript
@Inject
constructor(
    private val manager: PohManager,
    private val dataStore: PohDataStore,
    private val spawner: PohNpcSpawner,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<PohHouseEnteredEvent> { spawner.respawn(player) }
        onEvent<PohHouseExitedEvent> { spawner.despawn(player) }
        for (servant in PohServants.ALL) {
            onOpNpc1(servant.npc) { servantTalk(it.npc, servant) }
        }
    }

    private suspend fun ProtectedAccess.servantTalk(npc: Npc, servant: PohServant) {
        if (manager.isInOwnHouse(player) && player.pohServantType == servant.type) {
            startDialogue(npc) { houseService(servant) }
        } else {
            startDialogue(npc) { guildDialogue(servant) }
        }
    }

    /* Servants' Guild hiring */

    private suspend fun Dialogue.guildDialogue(servant: PohServant) {
        val player = access.player
        when {
            player.pohServantType == servant.type ->
                chatNpc(happy, "I already work for you! You'll find me waiting in your house.")
            player.pohServantType != 0 ->
                chatNpc(
                    neutral,
                    "You already employ a servant. You'll have to dismiss them before you can " +
                        "hire me.",
                )
            player.baseConstructionLvl < servant.level ->
                chatNpc(
                    neutral,
                    "I'm sorry, but I only work for masters with a Construction level of at " +
                        "least ${servant.level}.",
                )
            else -> offerHire(servant)
        }
    }

    private suspend fun Dialogue.offerHire(servant: PohServant) {
        val house = manager.houseOf(access.player)
        if (house == null) {
            chatNpc(neutral, "You'll need to own a house before you can hire a servant.")
            return
        }
        if (bedroomsWithBeds(house) < REQUIRED_BEDROOMS) {
            chatNpc(
                neutral,
                "Unfortunately I am only able to work in houses with at least two bedrooms " +
                    "that have beds in them.",
            )
            return
        }
        chatNpc(
            happy,
            "Good day! I charge wages of ${servant.wage} coins, collected after every " +
                "$REFUSE_THRESHOLD tasks I perform for you.",
        )
        val hire =
            choice2(
                "You're hired!",
                true,
                "Maybe another time.",
                false,
                title = "Hire the ${servant.displayName} for ${servant.wage} coins in wages?",
            )
        if (!hire) {
            chatPlayer(neutral, "Maybe another time.")
            return
        }
        access.player.pohServantType = servant.type
        access.player.pohServantPay = 0
        chatNpc(happy, "Excellent! I shall meet you at your house.")
    }

    /* In-house services */

    private suspend fun Dialogue.houseService(servant: PohServant) {
        if (access.player.pohServantPay >= REFUSE_THRESHOLD && !demandPayment(servant)) {
            return
        }
        chatNpc(happy, "Yes? How may I serve you?")
        val choice =
            choice4(
                "Fetch something from the bank.",
                OPT_FETCH,
                "Here, take your payment.",
                OPT_PAY,
                "You're fired.",
                OPT_FIRE,
                "Never mind.",
                OPT_CANCEL,
            )
        when (choice) {
            OPT_FETCH -> fetchFromBank(servant)
            OPT_PAY -> payWages(servant)
            OPT_FIRE -> fireServant(servant)
            OPT_CANCEL -> chatPlayer(neutral, "Never mind.")
        }
    }

    /**
     * Simple fetch-from-bank service: moves unnoted materials straight from the player's bank inv
     * into their inventory, clamped to the servant's capacity and free inventory space.
     *
     * TODO: live servants physically walk to the bank, support arbitrary items/noted fetches and
     *   hold undelivered items until wages are paid.
     */
    private suspend fun Dialogue.fetchFromBank(servant: PohServant) {
        chatPlayer(neutral, "Could you fetch something from my bank?")
        val available = FETCH_ITEMS.filter { access.invTotal(access.bank, it.obj) > 0 }
        if (available.isEmpty()) {
            chatNpc(sad, "I'm afraid there are no building materials in your bank for me to fetch.")
            return
        }
        val index =
            access.menu("Fetch which material?", hotkeys = false, available.map { it.label })
        val item = available.getOrNull(index) ?: return
        val requested = access.countDialog("How many? (I can carry up to ${servant.capacity}.)")
        if (requested <= 0) {
            return
        }
        val amount =
            minOf(
                requested,
                servant.capacity,
                access.invTotal(access.bank, item.obj),
                access.inv.freeSpace(),
            )
        if (amount <= 0) {
            chatNpc(sad, "You don't have any room to hold what I would bring back.")
            return
        }
        access.delay(TRIP_DELAY)
        access.invDel(access.bank, item.obj, amount)
        access.invAdd(access.inv, item.obj, amount)
        access.player.pohServantPay += 1
        chatNpc(happy, "Here you are: $amount x ${item.label}.")
        if (access.player.pohServantPay == DEMAND_THRESHOLD) {
            demandPayment(servant)
        }
    }

    /** Demands the servant's wages; returns `true` when the player pays. */
    private suspend fun Dialogue.demandPayment(servant: PohServant): Boolean {
        chatNpc(
            neutral,
            "Before I do anything else, I must ask for my wages of ${servant.wage} coins.",
        )
        val pay = choice2("Okay, here you go.", true, "Not right now.", false)
        if (!pay) {
            chatNpc(sad, "Then I'm afraid I can do nothing more for you until I am paid.")
            return false
        }
        if (access.invTotal(access.inv, COINS) < servant.wage) {
            chatPlayer(sad, "I don't seem to have enough coins on me.")
            return false
        }
        access.invDel(access.inv, COINS, servant.wage)
        access.player.pohServantPay = 0
        chatNpc(happy, "Thank you very much.")
        return true
    }

    private suspend fun Dialogue.payWages(servant: PohServant) {
        if (access.player.pohServantPay == 0) {
            chatNpc(happy, "You don't owe me anything yet.")
            return
        }
        if (access.invTotal(access.inv, COINS) < servant.wage) {
            chatNpc(sad, "I'm afraid you don't have my wages of ${servant.wage} coins on you.")
            return
        }
        access.invDel(access.inv, COINS, servant.wage)
        access.player.pohServantPay = 0
        chatNpc(happy, "Thank you very much.")
    }

    private suspend fun Dialogue.fireServant(servant: PohServant) {
        val confirm =
            choice2(
                "Yes, you're fired.",
                true,
                "Actually, never mind.",
                false,
                title = "Dismiss the ${servant.displayName}?",
            )
        if (!confirm) {
            return
        }
        access.player.pohServantType = 0
        access.player.pohServantPay = 0
        chatNpc(sad, "Very well. It has been a pleasure serving you.")
        spawner.respawn(access.player)
    }

    /** Counts bedrooms whose "Bed space" hotspot holds a built bed. */
    private fun bedroomsWithBeds(house: PohHouse): Int {
        val bedIndices =
            dataStore.hotspots(BEDROOM_ROOM).filter { it.name == BED_SPACE_NAME }.map { it.index }
        return house.rooms.count { (slot, room) ->
            room.type == BEDROOM_ROOM &&
                bedIndices.any { index ->
                    PohHotspotSlot(slot.level, slot.gridX, slot.gridZ, index) in house.furniture
                }
        }
    }

    private data class FetchItem(val obj: String, val label: String)

    private companion object {
        const val OPT_FETCH = 1
        const val OPT_PAY = 2
        const val OPT_FIRE = 3
        const val OPT_CANCEL = 4

        const val COINS = "obj.coins"
        const val BEDROOM_ROOM = "bedroom"
        const val BED_SPACE_NAME = "Bed space"
        const val REQUIRED_BEDROOMS = 2

        /** After this many unpaid services the servant asks for wages. */
        const val DEMAND_THRESHOLD = 7

        /** At this many unpaid services the servant refuses to work until paid. */
        const val REFUSE_THRESHOLD = 8

        /** Cycles the "trip" to the bank takes. */
        const val TRIP_DELAY = 2

        /** Construction materials the servant can fetch from the bank (RSCM names verified). */
        val FETCH_ITEMS =
            listOf(
                FetchItem("obj.woodplank", "Plank"),
                FetchItem("obj.plank_oak", "Oak plank"),
                FetchItem("obj.plank_teak", "Teak plank"),
                FetchItem("obj.plank_mahogany", "Mahogany plank"),
                FetchItem("obj.softclay", "Soft clay"),
                FetchItem("obj.limestonebrick", "Limestone brick"),
                FetchItem("obj.steel_bar", "Steel bar"),
                FetchItem("obj.cloth", "Bolt of cloth"),
                FetchItem("obj.gold_leaf", "Gold leaf"),
                FetchItem("obj.marble_block", "Marble block"),
                FetchItem("obj.poh_magic_crystal", "Magic stone"),
            )
    }
}
