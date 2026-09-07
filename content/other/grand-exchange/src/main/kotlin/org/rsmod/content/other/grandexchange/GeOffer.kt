package org.rsmod.content.other.grandexchange

enum class GeOfferType(val clientId: Int) {
    Buy(0),
    Sell(1);

    companion object {
        fun fromClientId(id: Int): GeOfferType = if (id == Sell.clientId) Sell else Buy
    }
}

/**
 * One offer slot. An offer stays in its slot after it finishes (completed or aborted) until the
 * owner has collected everything it holds; only then is the slot free again.
 *
 * [gold] is the gross value traded so far: coins spent for a buy offer, coins earned before tax
 * for a sell offer. Refunds and proceeds waiting to be picked up sit in [collectCoins], items in
 * [collectItems] (always the unnoted item; the collect ops decide the form).
 */
class GeOffer(
    val id: Long,
    val owner: Int,
    val ownerName: String,
    val slot: Int,
    val type: GeOfferType,
    val item: Int,
    val price: Int,
    val total: Int,
    val createdAt: Long,
) {
    var completed: Int = 0
    var gold: Int = 0
    var taxPaid: Int = 0
    var collectItems: Int = 0
    var collectCoins: Int = 0
    var finished: Boolean = false

    val remaining: Int
        get() = total - completed

    val isActive: Boolean
        get() = !finished

    val hasCollectables: Boolean
        get() = collectItems > 0 || collectCoins > 0

    val isCancelled: Boolean
        get() = finished && completed < total

    /**
     * The status byte of the client's stock market slot packet: bit 1 live, bit 2 finished, bit 3
     * sell. A finished offer whose completed count is short of the total draws as cancelled.
     */
    fun clientStatus(): Int {
        val base = if (finished) STATUS_FINISHED else STATUS_LIVE
        return if (type == GeOfferType.Sell) base or STATUS_SELL else base
    }

    override fun toString(): String =
        "GeOffer(id=$id, owner=$owner, slot=$slot, type=$type, item=$item, price=$price, " +
            "total=$total, completed=$completed, gold=$gold, tax=$taxPaid, collectItems=$collectItems, " +
            "collectCoins=$collectCoins, finished=$finished)"

    companion object {
        const val STATUS_LIVE = 2
        const val STATUS_FINISHED = 5
        const val STATUS_SELL = 8
    }
}

/** A completed trade as the history screen lists it. [total] is gross; [tax] was taken from it. */
data class GeTrade(
    val time: Long = 0,
    val type: GeOfferType = GeOfferType.Buy,
    val item: Int = 0,
    val quantity: Int = 0,
    val total: Int = 0,
    val tax: Int = 0,
)

/** Purchases counted against an item's buy limit since the first purchase of the window. */
data class GeBuyWindow(val start: Long = 0, val bought: Int = 0)
