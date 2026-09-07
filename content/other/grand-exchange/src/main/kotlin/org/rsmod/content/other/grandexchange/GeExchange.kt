package org.rsmod.content.other.grandexchange

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.math.min

/**
 * The world's order book: every player's offer slots, the matching between them, buy-limit
 * windows and trade history. Nothing in here touches a [org.rsmod.game.entity.Player]; the
 * service layer turns the [Listener] callbacks into packets, messages and inventories.
 *
 * Matching follows the wiki's description: a new buy offer trades against the cheapest existing
 * sell offers at *their* price, refunding the buyer the difference; a new sell offer trades
 * against the highest existing buy offers at *their* price, so the seller earns more than asked.
 * Older offers win ties. The seller pays the convenience fee on every item sold.
 */
class GeExchange(private val items: GeItemData, private val clock: () -> Long) {
    @Inject constructor(items: GeItemData) : this(items, System::currentTimeMillis)

    interface Listener {
        /** A trade moved [offer] forward; [trade] is what the offer's owner bought or sold. */
        fun onOfferTraded(offer: GeOffer, trade: GeTrade)

        /** [offer] finished: completed by trades or aborted by its owner. */
        fun onOfferFinished(offer: GeOffer, aborted: Boolean)
    }

    var listener: Listener? = null

    private val offers = LinkedHashMap<Long, GeOffer>()
    private val slotsByOwner = HashMap<Int, Array<GeOffer?>>()
    private val activeByItem = HashMap<Int, MutableList<GeOffer>>()
    private val history = HashMap<Int, ArrayDeque<GeTrade>>()
    private val buyWindows = HashMap<Int, HashMap<Int, GeBuyWindow>>()
    private var nextId = 1L

    var dirty: Boolean = false
        private set

    val offerCount: Int
        get() = offers.size

    fun allOffers(): List<GeOffer> = offers.values.toList()

    fun slots(owner: Int): List<GeOffer?> = slotsByOwner[owner]?.toList() ?: List(GeConfig.SLOT_COUNT) { null }

    fun offer(owner: Int, slot: Int): GeOffer? = slotsByOwner[owner]?.getOrNull(slot)

    fun firstEmptySlot(owner: Int): Int? {
        val slots = slotsByOwner[owner] ?: return 0
        return slots.indices.firstOrNull { slots[it] == null }
    }

    fun history(owner: Int): List<GeTrade> = history[owner]?.toList() ?: emptyList()

    /** How many more of [item] [owner] may buy before the current four-hour window ends. */
    fun remainingBuyLimit(owner: Int, item: Int, now: Long = clock()): Int {
        val limit = items.buyLimit(item)
        if (limit == GeItemData.NO_LIMIT) {
            return limit
        }
        val window = buyWindows[owner]?.get(item) ?: return limit
        if (now - window.start >= GeConfig.BUY_LIMIT_WINDOW_MILLIS) {
            return limit
        }
        return maxOf(0, limit - window.bought)
    }

    /** Millis until [owner]'s buy limit on [item] resets, or 0 when no window is open. */
    fun buyLimitResetIn(owner: Int, item: Int, now: Long = clock()): Long {
        val window = buyWindows[owner]?.get(item) ?: return 0
        val remaining = GeConfig.BUY_LIMIT_WINDOW_MILLIS - (now - window.start)
        return if (remaining > 0) remaining else 0
    }

    /**
     * Creates an offer in [slot] and matches it against the book straight away. The caller has
     * already taken the coins or items from the player.
     */
    fun place(
        owner: Int,
        ownerName: String,
        slot: Int,
        type: GeOfferType,
        item: Int,
        price: Int,
        quantity: Int,
    ): GeOffer {
        require(slot in 0 until GeConfig.SLOT_COUNT) { "Invalid slot: $slot" }
        require(price > 0) { "Price must be positive: $price" }
        require(quantity > 0) { "Quantity must be positive: $quantity" }
        require(price.toLong() * quantity <= Int.MAX_VALUE) { "Offer value overflows: $price x $quantity" }
        val slots = slotsByOwner.getOrPut(owner) { arrayOfNulls(GeConfig.SLOT_COUNT) }
        require(slots[slot] == null) { "Slot $slot of $owner is occupied" }

        val offer = GeOffer(nextId++, owner, ownerName, slot, type, item, price, quantity, clock())
        offers[offer.id] = offer
        slots[slot] = offer
        activeByItem.getOrPut(item) { ArrayList() } += offer
        dirty = true
        match(offer)
        return offer
    }

    /** Stops an active offer; what it has not traded moves to its collect box. */
    fun abort(offer: GeOffer): Boolean {
        if (offer.finished) {
            return false
        }
        val remaining = offer.remaining
        when (offer.type) {
            GeOfferType.Buy -> offer.collectCoins += remaining * offer.price
            GeOfferType.Sell -> offer.collectItems += remaining
        }
        finish(offer, aborted = true)
        return true
    }

    /**
     * Takes up to [count] items waiting in the offer's collect box and returns how many were
     * taken. Frees the slot once nothing is left to collect on a finished offer.
     */
    fun takeItems(offer: GeOffer, count: Int = offer.collectItems): Int {
        val taken = count.coerceIn(0, offer.collectItems)
        offer.collectItems -= taken
        dirty = true
        releaseIfDone(offer)
        return taken
    }

    /** Takes up to [count] coins waiting in the offer's collect box; see [takeItems]. */
    fun takeCoins(offer: GeOffer, count: Int = offer.collectCoins): Int {
        val taken = count.coerceIn(0, offer.collectCoins)
        offer.collectCoins -= taken
        dirty = true
        releaseIfDone(offer)
        return taken
    }

    private fun match(offer: GeOffer) {
        val active = activeByItem[offer.item] ?: return
        val candidates =
            active
                .filter { it !== offer && it.type != offer.type && it.isActive && it.remaining > 0 }
                .filter { other ->
                    if (offer.type == GeOfferType.Buy) other.price <= offer.price else other.price >= offer.price
                }
                .sortedWith(
                    if (offer.type == GeOfferType.Buy) {
                        compareBy<GeOffer>({ it.price }, { it.createdAt }, { it.id })
                    } else {
                        compareBy<GeOffer>({ -it.price }, { it.createdAt }, { it.id })
                    }
                )
        for (other in candidates) {
            if (offer.remaining <= 0) {
                break
            }
            val buy = if (offer.type == GeOfferType.Buy) offer else other
            val sell = if (offer.type == GeOfferType.Sell) offer else other
            var quantity = min(buy.remaining, sell.remaining)
            quantity = min(quantity, remainingBuyLimit(buy.owner, buy.item))
            if (quantity <= 0) {
                if (buy === offer) {
                    // The new buyer is at their limit; nothing else on the book can help.
                    break
                }
                continue
            }
            trade(buy, sell, quantity, other.price)
        }
    }

    private fun trade(buy: GeOffer, sell: GeOffer, quantity: Int, price: Int) {
        val now = clock()
        val gross = quantity * price
        val tax = items.taxPerItem(buy.item, price) * quantity

        buy.completed += quantity
        buy.gold += gross
        buy.collectItems += quantity
        buy.collectCoins += quantity * (buy.price - price)

        sell.completed += quantity
        sell.gold += gross
        sell.taxPaid += tax
        sell.collectCoins += gross - tax

        recordPurchase(buy.owner, buy.item, quantity, now)
        val bought = GeTrade(now, GeOfferType.Buy, buy.item, quantity, gross, 0)
        val sold = GeTrade(now, GeOfferType.Sell, sell.item, quantity, gross, tax)
        addHistory(buy.owner, bought)
        addHistory(sell.owner, sold)
        dirty = true

        listener?.onOfferTraded(buy, bought)
        listener?.onOfferTraded(sell, sold)
        if (buy.remaining <= 0) {
            finish(buy, aborted = false)
        }
        if (sell.remaining <= 0) {
            finish(sell, aborted = false)
        }
    }

    private fun finish(offer: GeOffer, aborted: Boolean) {
        offer.finished = true
        activeByItem[offer.item]?.remove(offer)
        dirty = true
        listener?.onOfferFinished(offer, aborted)
        releaseIfDone(offer)
    }

    private fun releaseIfDone(offer: GeOffer) {
        if (!offer.finished || offer.hasCollectables) {
            return
        }
        offers.remove(offer.id)
        slotsByOwner[offer.owner]?.let { slots ->
            if (slots.getOrNull(offer.slot) === offer) {
                slots[offer.slot] = null
            }
        }
        dirty = true
    }

    private fun recordPurchase(owner: Int, item: Int, quantity: Int, now: Long) {
        if (items.buyLimit(item) == GeItemData.NO_LIMIT) {
            return
        }
        val windows = buyWindows.getOrPut(owner) { HashMap() }
        val window = windows[item]
        windows[item] =
            if (window == null || now - window.start >= GeConfig.BUY_LIMIT_WINDOW_MILLIS) {
                GeBuyWindow(now, quantity)
            } else {
                window.copy(bought = window.bought + quantity)
            }
    }

    private fun addHistory(owner: Int, trade: GeTrade) {
        val list = history.getOrPut(owner) { ArrayDeque() }
        list.addFirst(trade)
        while (list.size > GeConfig.HISTORY_SIZE) {
            list.removeLast()
        }
    }

    /* Persistence */

    fun save(path: Path) {
        val data =
            GeSaveData(
                nextId = nextId,
                offers = offers.values.map(GeOfferData::from),
                history = history.mapValues { it.value.toList() },
                buyWindows =
                    buyWindows.flatMap { (owner, windows) ->
                        windows.map { (item, window) -> GeBuyWindowData(owner, item, window.start, window.bought) }
                    },
            )
        Files.createDirectories(path.toAbsolutePath().parent)
        val tmp = path.resolveSibling(path.fileName.toString() + ".tmp")
        mapper.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), data)
        Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        dirty = false
    }

    fun saveIfDirty(path: Path) {
        if (dirty) {
            save(path)
        }
    }

    fun load(path: Path): Boolean {
        if (!Files.exists(path)) {
            return false
        }
        val data = mapper.readValue<GeSaveData>(path.toFile())
        offers.clear()
        slotsByOwner.clear()
        activeByItem.clear()
        history.clear()
        buyWindows.clear()
        nextId = data.nextId
        for (saved in data.offers) {
            val offer = saved.toOffer()
            offers[offer.id] = offer
            slotsByOwner.getOrPut(offer.owner) { arrayOfNulls(GeConfig.SLOT_COUNT) }[offer.slot] = offer
            if (offer.isActive) {
                activeByItem.getOrPut(offer.item) { ArrayList() } += offer
            }
            if (offer.id >= nextId) {
                nextId = offer.id + 1
            }
        }
        for ((owner, trades) in data.history) {
            history[owner] = ArrayDeque(trades.take(GeConfig.HISTORY_SIZE))
        }
        for (window in data.buyWindows) {
            buyWindows.getOrPut(window.owner) { HashMap() }[window.item] = GeBuyWindow(window.start, window.bought)
        }
        dirty = false
        logger.info { "Loaded ${offers.size} Grand Exchange offers from $path." }
        return true
    }

    private companion object {
        private val logger = InlineLogger()
        private val mapper = jacksonObjectMapper()
    }
}

data class GeSaveData(
    val nextId: Long = 1,
    val offers: List<GeOfferData> = emptyList(),
    val history: Map<Int, List<GeTrade>> = emptyMap(),
    val buyWindows: List<GeBuyWindowData> = emptyList(),
)

data class GeBuyWindowData(val owner: Int = 0, val item: Int = 0, val start: Long = 0, val bought: Int = 0)

data class GeOfferData(
    val id: Long = 0,
    val owner: Int = 0,
    val ownerName: String = "",
    val slot: Int = 0,
    val type: GeOfferType = GeOfferType.Buy,
    val item: Int = 0,
    val price: Int = 0,
    val total: Int = 0,
    val createdAt: Long = 0,
    val completed: Int = 0,
    val gold: Int = 0,
    val taxPaid: Int = 0,
    val collectItems: Int = 0,
    val collectCoins: Int = 0,
    val finished: Boolean = false,
) {
    fun toOffer(): GeOffer =
        GeOffer(id, owner, ownerName, slot, type, item, price, total, createdAt).also {
            it.completed = completed
            it.gold = gold
            it.taxPaid = taxPaid
            it.collectItems = collectItems
            it.collectCoins = collectCoins
            it.finished = finished
        }

    companion object {
        fun from(offer: GeOffer): GeOfferData =
            GeOfferData(
                id = offer.id,
                owner = offer.owner,
                ownerName = offer.ownerName,
                slot = offer.slot,
                type = offer.type,
                item = offer.item,
                price = offer.price,
                total = offer.total,
                createdAt = offer.createdAt,
                completed = offer.completed,
                gold = offer.gold,
                taxPaid = offer.taxPaid,
                collectItems = offer.collectItems,
                collectCoins = offer.collectCoins,
                finished = offer.finished,
            )
    }
}
