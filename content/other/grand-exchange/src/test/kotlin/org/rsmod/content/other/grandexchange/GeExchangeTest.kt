package org.rsmod.content.other.grandexchange

import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GeExchangeTest {
    private val items =
        GeItemData.fromLines(
            listOf(
                "# id\tlimit\tprice\tname",
                "$WHIP\t70\t1500000\tAbyssal whip",
                "$FEATHER\t13000\t3\tFeather",
                "$LOBSTER\t6000\t150\tLobster",
                "$COAL\t13000\t150\tCoal",
                "$BOND\t100\t8000000\tOld school bond",
                "$ENERGY\t2000\t90\tEnergy potion(4)",
                "$RARE\t8\t2000000000\tThird age",
            )
        )

    private var now = 1_000_000L
    private val exchange = GeExchange(items) { now }
    private val finished = ArrayList<Pair<GeOffer, Boolean>>()
    private val traded = ArrayList<Pair<GeOffer, GeTrade>>()

    init {
        exchange.listener =
            object : GeExchange.Listener {
                override fun onOfferTraded(offer: GeOffer, trade: GeTrade) {
                    traded += offer to trade
                }

                override fun onOfferFinished(offer: GeOffer, aborted: Boolean) {
                    finished += offer to aborted
                }
            }
    }

    @Test
    fun `buy offer trades at the cheapest sell price and refunds the difference`() {
        val sell = exchange.place(SELLER, "seller", 0, GeOfferType.Sell, FEATHER, 2, 100)
        val buy = exchange.place(BUYER, "buyer", 0, GeOfferType.Buy, FEATHER, 5, 100)

        assertTrue(buy.finished)
        assertTrue(sell.finished)
        assertEquals(100, buy.collectItems)
        assertEquals(300, buy.collectCoins, "buyer paid 5 each, trade happened at 2 each")
        assertEquals(200, buy.gold)
        assertEquals(200, sell.gold)
        assertEquals(0, sell.taxPaid, "2% of 2 coins rounds down to nothing")
        assertEquals(200, sell.collectCoins)
        assertEquals(listOf(buy to false, sell to false), finished)
    }

    @Test
    fun `sell offer trades at the highest buy price so the seller earns more`() {
        exchange.place(BUYER, "buyer", 0, GeOfferType.Buy, WHIP, 1_600_000, 1)
        exchange.place(BUYER, "buyer", 1, GeOfferType.Buy, WHIP, 1_700_000, 1)
        val sell = exchange.place(SELLER, "seller", 0, GeOfferType.Sell, WHIP, 1_500_000, 1)

        assertTrue(sell.finished)
        assertEquals(1_700_000, sell.gold)
        assertEquals(34_000, sell.taxPaid)
        assertEquals(1_666_000, sell.collectCoins)
        val cheaper = exchange.offer(BUYER, 0)!!
        assertTrue(cheaper.isActive, "the lower buy offer is still waiting")
        assertNull(exchange.offer(BUYER, 1)?.takeIf { it.isActive })
    }

    @Test
    fun `older offers win ties`() {
        val first = exchange.place(SELLER, "seller", 0, GeOfferType.Sell, LOBSTER, 150, 10)
        now += 1
        val second = exchange.place(SELLER, "seller", 1, GeOfferType.Sell, LOBSTER, 150, 10)
        exchange.place(BUYER, "buyer", 0, GeOfferType.Buy, LOBSTER, 150, 10)

        assertTrue(first.finished)
        assertFalse(second.finished)
        assertEquals(0, second.completed)
    }

    @Test
    fun `partial fills leave both offers live until quantities run out`() {
        val sell = exchange.place(SELLER, "seller", 0, GeOfferType.Sell, COAL, 100, 30)
        val buy = exchange.place(BUYER, "buyer", 0, GeOfferType.Buy, COAL, 100, 10)

        assertTrue(buy.finished)
        assertFalse(sell.finished)
        assertEquals(10, sell.completed)
        assertEquals(20, sell.remaining)
        assertEquals(1000, sell.gold)
        assertEquals(20, sell.taxPaid)
        assertEquals(980, sell.collectCoins)
    }

    @Test
    fun `tax is capped per item and waived on exempt items`() {
        assertEquals(5_000_000, items.taxPerItem(RARE, 2_000_000_000))
        assertEquals(0, items.taxPerItem(ENERGY, 90))
        assertEquals(0, items.taxPerItem(BOND, 8_000_000))
        assertEquals(0, items.taxPerItem(LOBSTER, 150), "lobsters are exempt")
        assertEquals(3, items.taxPerItem(COAL, 150))
        assertEquals(30_000, items.taxPerItem(WHIP, 1_500_000))
        assertEquals(0, items.taxPerItem(FEATHER, 49))
        assertEquals(1, items.taxPerItem(FEATHER, 50))
    }

    @Test
    fun `aborting returns what was not traded to the collect box`() {
        val buy = exchange.place(BUYER, "buyer", 0, GeOfferType.Buy, LOBSTER, 100, 10)
        val sell = exchange.place(SELLER, "seller", 0, GeOfferType.Sell, LOBSTER, 100, 4)
        assertTrue(sell.finished)
        assertTrue(exchange.abort(buy))
        assertFalse(exchange.abort(buy))

        assertTrue(buy.isCancelled)
        assertEquals(4, buy.collectItems)
        assertEquals(600, buy.collectCoins)
        assertEquals(buy to true, finished.last())

        val sell2 = exchange.place(SELLER, "seller", 1, GeOfferType.Sell, LOBSTER, 100, 5)
        assertTrue(exchange.abort(sell2))
        assertEquals(5, sell2.collectItems)
        assertEquals(0, sell2.collectCoins)
    }

    @Test
    fun `collecting everything frees the slot`() {
        val sell = exchange.place(SELLER, "seller", 3, GeOfferType.Sell, COAL, 100, 1)
        val buy = exchange.place(BUYER, "buyer", 2, GeOfferType.Buy, COAL, 120, 1)

        assertEquals(1, exchange.takeItems(buy))
        assertEquals(buy, exchange.offer(BUYER, 2), "coins are still waiting")
        assertEquals(20, exchange.takeCoins(buy))
        assertNull(exchange.offer(BUYER, 2))
        assertEquals(0, exchange.firstEmptySlot(BUYER))

        assertEquals(98, exchange.takeCoins(sell))
        assertNull(exchange.offer(SELLER, 3))
        assertEquals(0, exchange.offerCount)
    }

    @Test
    fun `buy limits cap trades and reset four hours after the first purchase`() {
        exchange.place(SELLER, "seller", 0, GeOfferType.Sell, RARE, 1000, 20)
        val buy = exchange.place(BUYER, "buyer", 0, GeOfferType.Buy, RARE, 1000, 20)

        assertEquals(8, buy.completed)
        assertFalse(buy.finished)
        assertEquals(0, exchange.remainingBuyLimit(BUYER, RARE))

        now += GeConfig.BUY_LIMIT_WINDOW_MILLIS - 1
        assertEquals(0, exchange.remainingBuyLimit(BUYER, RARE))
        now += 1
        assertEquals(8, exchange.remainingBuyLimit(BUYER, RARE))

        // A fresh sell offer matches the waiting buy offer again once the window has reset.
        exchange.place(SELLER, "seller", 1, GeOfferType.Sell, RARE, 1000, 20)
        assertEquals(16, buy.completed)
        assertEquals(GeItemData.NO_LIMIT, exchange.remainingBuyLimit(BUYER, 999_999))
    }

    @Test
    fun `history records both sides newest first`() {
        exchange.place(SELLER, "seller", 0, GeOfferType.Sell, COAL, 100, 2)
        exchange.place(BUYER, "buyer", 0, GeOfferType.Buy, COAL, 100, 1)
        now += 10
        exchange.place(BUYER, "buyer", 1, GeOfferType.Buy, COAL, 100, 1)

        val buyer = exchange.history(BUYER)
        assertEquals(2, buyer.size)
        assertEquals(GeOfferType.Buy, buyer[0].type)
        assertEquals(now, buyer[0].time)
        val seller = exchange.history(SELLER)
        assertEquals(2, seller.size)
        assertEquals(GeOfferType.Sell, seller[0].type)
        assertEquals(100, seller[0].total)
        assertEquals(2, seller[0].tax)
    }

    @Test
    fun `offers survive a save and load round trip`() {
        exchange.place(SELLER, "seller", 0, GeOfferType.Sell, LOBSTER, 100, 5)
        val buy = exchange.place(BUYER, "buyer", 4, GeOfferType.Buy, LOBSTER, 100, 2)
        val path = Files.createTempFile("ge-test", ".json")
        try {
            exchange.save(path)
            assertFalse(exchange.dirty)

            val loaded = GeExchange(items) { now }
            assertTrue(loaded.load(path))
            val sell = loaded.offer(SELLER, 0)!!
            assertEquals(2, sell.completed)
            assertEquals(3, sell.remaining)
            assertTrue(sell.isActive)
            val reloadedBuy = loaded.offer(BUYER, 4)!!
            assertEquals(buy.id, reloadedBuy.id)
            assertTrue(reloadedBuy.finished)
            assertEquals(2, reloadedBuy.collectItems)
            assertEquals(1, loaded.history(BUYER).size)

            // The active sell offer still matches new buyers after the reload.
            loaded.place(BUYER, "buyer", 0, GeOfferType.Buy, LOBSTER, 100, 3)
            assertTrue(loaded.offer(SELLER, 0)!!.finished)
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun `client status encodes type and completion`() {
        val sell = exchange.place(SELLER, "seller", 0, GeOfferType.Sell, LOBSTER, 100, 1)
        assertEquals(10, sell.clientStatus())
        val buy = exchange.place(BUYER, "buyer", 0, GeOfferType.Buy, LOBSTER, 100, 1)
        assertEquals(5, buy.clientStatus())
        assertEquals(13, sell.clientStatus())
    }

    private companion object {
        const val BUYER = 1
        const val SELLER = 2
        const val WHIP = 4151
        const val FEATHER = 314
        const val LOBSTER = 379
        const val COAL = 453
        const val BOND = 13190
        const val ENERGY = 3008
        const val RARE = 10344
    }
}
