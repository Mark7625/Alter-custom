package org.rsmod.content.other.grandexchange

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import java.nio.file.Path
import kotlin.math.max
import kotlin.math.min
import net.rsprot.protocol.game.outgoing.misc.player.UpdateStockMarketSlot
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.invtx.invDel
import org.rsmod.api.player.ironman.isSoloIronman
import org.rsmod.api.player.midiJingle
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.startInvTransmit
import org.rsmod.api.player.stopInvTransmit
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.utils.format.formatAmount
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.type.uncert

/**
 * Everything the Grand Exchange does *to a player*: driving the cache's offer, collection and
 * history screens, moving coins and items in and out of the [GeExchange] book, and telling
 * players when their offers move.
 *
 * Client contract (read from the revision-240 clientscripts, see [GeConfig]):
 * - Each slot's state arrives in an `UpdateStockMarketSlot` packet; the screens redraw from it.
 * - The setup panel reads the chosen item from `varp.tradingpost_search` and the quantity, price
 *   and type from the `ge_newoffer_*` varbits. The panel's buttons change those varbits client
 *   side, and also reach the server as buttons on `ge_offers:setup`, so the same numbers are
 *   kept here; a mismatch would make the client snap back to the server value.
 * - Collectable items and coins are drawn from the two-slot `tradingpost_sell_N` / `ge_collect_N`
 *   inventories (slot 0 items, slot 1 coins).
 * - The `ge_itemsink_obj_N` varps must be -1 or the client shows "Offer status" on empty slots.
 */
class GeService
@Inject
constructor(
    private val exchange: GeExchange,
    private val items: GeItemData,
    private val playerList: PlayerList,
) : GeExchange.Listener {
    private val savePath: Path = Path.of(GeConfig.SAVE_PATH)
    private var saveCountdown = GeConfig.SAVE_INTERVAL_TICKS
    private val coinsId: Int by lazy { GeConfig.COINS.asRSCM(RSCMType.OBJ) }
    private val bondId: Int by lazy { GeConfig.BOND.asRSCM(RSCMType.OBJ) }

    init {
        exchange.listener = this
    }

    /* Lifecycle */

    fun load() {
        try {
            exchange.load(savePath)
        } catch (t: Throwable) {
            logger.error(t) { "Could not load the Grand Exchange book from $savePath; starting empty." }
        }
    }

    fun tick() {
        if (!exchange.dirty) {
            return
        }
        if (--saveCountdown <= 0) {
            save()
        }
    }

    fun save() {
        saveCountdown = GeConfig.SAVE_INTERVAL_TICKS
        if (!exchange.dirty) {
            return
        }
        try {
            exchange.save(savePath)
        } catch (t: Throwable) {
            logger.error(t) { "Could not save the Grand Exchange book to $savePath." }
        }
    }

    fun onLogin(player: Player) {
        for (slot in 0 until GeConfig.SLOT_COUNT) {
            setVar(player, GeConfig.itemSinkObjVarp(slot), -1)
            setVar(player, GeConfig.itemSinkPriceVarp(slot), -1)
        }
        setVar(player, GeConfig.VARBIT_TAX_RATE, GeConfig.TAX_PER_MILLE)
        setVar(player, GeConfig.VARBIT_SELECTED_SLOT, 0)
        setVar(player, GeConfig.VARP_ITEM, -1)
        refreshAll(player)
    }

    /* Listener: offers moving while the owner may or may not be online. */

    override fun onOfferTraded(offer: GeOffer, trade: GeTrade) {
        val player = online(offer.owner) ?: return
        refresh(player, offer.slot)
    }

    override fun onOfferFinished(offer: GeOffer, aborted: Boolean) {
        val player = online(offer.owner) ?: return
        refresh(player, offer.slot)
        if (aborted) {
            return
        }
        val verb = if (offer.type == GeOfferType.Buy) "buying" else "selling"
        player.mes("<col=ff0000>Grand Exchange: Finished $verb ${offer.total.formatAmount} x ${name(offer.item)}.</col>")
        player.midiJingle(GeConfig.COMPLETION_JINGLE_GROUP)
    }

    private fun online(accountId: Int): Player? = playerList.firstOrNull { it.accountId == accountId }

    /* Client state */

    fun refreshAll(player: Player) {
        for (slot in 0 until GeConfig.SLOT_COUNT) {
            refresh(player, slot)
        }
    }

    fun refresh(player: Player, slot: Int) {
        val offer = exchange.offer(player.accountId, slot)
        val update =
            if (offer == null) {
                UpdateStockMarketSlot.ResetStockMarketSlot
            } else {
                UpdateStockMarketSlot.SetStockMarketSlot(
                    status = offer.clientStatus(),
                    obj = offer.item,
                    price = offer.price,
                    count = offer.total,
                    completedCount = offer.completed,
                    completedGold = offer.gold,
                )
            }
        player.client.write(UpdateStockMarketSlot(slot, update))
        GeConfig.TAX_VARPS[slot]?.let { setVar(player, it, offer?.taxPaid ?: 0) }
        syncCollectInv(player, slot)
    }

    private fun collectInv(player: Player, slot: Int): Inventory = player.invMap.getOrPut(GeConfig.COLLECT_INVS[slot])

    private fun syncCollectInv(player: Player, slot: Int) {
        val inv = collectInv(player, slot)
        val offer = exchange.offer(player.accountId, slot)
        val itemsObj =
            offer?.takeIf { it.collectItems > 0 }?.let { InvObj(ServerCacheManager.getItemOrDefault(it.item), it.collectItems) }
        val coinsObj = offer?.takeIf { it.collectCoins > 0 }?.let { InvObj(ServerCacheManager.getItemOrDefault(coinsId), it.collectCoins) }
        if (inv[GeConfig.COLLECT_BOX_ITEMS] != itemsObj) {
            inv[GeConfig.COLLECT_BOX_ITEMS] = itemsObj
        }
        if (inv[GeConfig.COLLECT_BOX_COINS] != coinsObj) {
            inv[GeConfig.COLLECT_BOX_COINS] = coinsObj
        }
    }

    /* Offers screen */

    fun open(access: ProtectedAccess) {
        val player = access.player
        if (player.isSoloIronman) {
            player.mes("As an Ironman, you can only use the Grand Exchange to buy bonds.")
        }
        setVar(player, GeConfig.VARBIT_SELECTED_SLOT, 0)
        clearSetup(player)
        access.ifOpenMainSidePair(GeConfig.OFFERS, GeConfig.OFFERS_SIDE)
    }

    /** Called when `interface.ge_offers` opens, however it was opened. */
    fun onOffersOpened(player: Player) {
        for (slot in 0 until GeConfig.SLOT_COUNT) {
            player.ifSetEvents(
                GeConfig.indexSlot(slot),
                GeConfig.INDEX_CHILD_VIEW..GeConfig.INDEX_CHILD_SELL,
                IfEvent.Op1,
            )
        }
        player.ifSetEvents(GeConfig.COMP_SETUP, 0 until GeConfig.SETUP_CHILD_COUNT, IfEvent.Op1, IfEvent.Op2)
        player.ifSetEvents(GeConfig.COMP_DETAILS, GeConfig.DETAILS_ABORT..GeConfig.DETAILS_MODIFY, IfEvent.Op1)
        player.ifSetEvents(
            GeConfig.COMP_DETAILS_COLLECT,
            GeConfig.DETAILS_COLLECT_ITEMS_CHILD..GeConfig.DETAILS_COLLECT_COINS_CHILD,
            IfEvent.Op1,
            IfEvent.Op2,
            IfEvent.Op3,
            IfEvent.Op10,
        )
        player.ifSetEvents(
            GeConfig.COMP_COLLECT_ALL,
            GeConfig.COLLECT_ALL_BUTTON..GeConfig.REPEAT_OFFER_BUTTON,
            IfEvent.Op1,
            IfEvent.Op2,
        )
        player.ifSetEvents(GeConfig.COMP_SIDE_ITEMS, player.inv.indices, IfEvent.Op1, IfEvent.Op10)
        startTransmits(player)
        refreshAll(player)
    }

    fun onOffersClosed(player: Player) {
        stopTransmits(player)
        setVar(player, GeConfig.VARBIT_SELECTED_SLOT, 0)
        clearSetup(player)
    }

    /** Called when `interface.ge_collect` (the collection box) opens. */
    fun onCollectionBoxOpened(player: Player) {
        for (slot in 0 until GeConfig.SLOT_COUNT) {
            player.ifSetEvents(
                GeConfig.collectSlot(slot),
                GeConfig.COLLECT_SLOT_ITEMS_CHILD..GeConfig.COLLECT_SLOT_COINS_CHILD,
                IfEvent.Op1,
                IfEvent.Op2,
                IfEvent.Op3,
                IfEvent.Op10,
            )
        }
        player.ifSetEvents(GeConfig.COMP_COLLECT_INV, 0..0, IfEvent.Op1)
        player.ifSetEvents(GeConfig.COMP_COLLECT_BANK, 0..0, IfEvent.Op1)
        startTransmits(player)
        refreshAll(player)
    }

    fun onCollectionBoxClosed(player: Player) {
        stopTransmits(player)
    }

    private fun startTransmits(player: Player) {
        for (slot in 0 until GeConfig.SLOT_COUNT) {
            player.startInvTransmit(collectInv(player, slot))
        }
    }

    private fun stopTransmits(player: Player) {
        for (slot in 0 until GeConfig.SLOT_COUNT) {
            player.stopInvTransmit(collectInv(player, slot))
        }
    }

    /* Slot selection */

    fun viewSlot(access: ProtectedAccess, slot: Int) {
        val player = access.player
        if (exchange.offer(player.accountId, slot) == null) {
            clearSetup(player)
        }
        setVar(player, GeConfig.VARBIT_SELECTED_SLOT, slot + 1)
    }

    fun back(player: Player) {
        setVar(player, GeConfig.VARBIT_SELECTED_SLOT, 0)
        clearSetup(player)
    }

    suspend fun startBuy(access: ProtectedAccess, slot: Int) {
        val player = access.player
        if (exchange.offer(player.accountId, slot) != null) {
            viewSlot(access, slot)
            return
        }
        beginSetup(player, slot, GeOfferType.Buy, item = -1, quantity = 0, price = 0)
        chooseItem(access)
    }

    fun startSell(access: ProtectedAccess, slot: Int) {
        val player = access.player
        if (exchange.offer(player.accountId, slot) != null) {
            viewSlot(access, slot)
            return
        }
        if (player.isSoloIronman) {
            player.mes("As an Ironman, you cannot sell items on the Grand Exchange.")
            return
        }
        beginSetup(player, slot, GeOfferType.Sell, item = -1, quantity = 0, price = 0)
    }

    private fun beginSetup(player: Player, slot: Int, type: GeOfferType, item: Int, quantity: Int, price: Int) {
        setVar(player, GeConfig.VARBIT_SELECTED_SLOT, slot + 1)
        setVar(player, GeConfig.VARBIT_TYPE, type.clientId)
        setVar(player, GeConfig.VARP_ITEM, item)
        setVar(player, GeConfig.VARBIT_QUANTITY, quantity)
        setVar(player, GeConfig.VARBIT_PRICE, price)
    }

    private fun clearSetup(player: Player) {
        setVar(player, GeConfig.VARP_ITEM, -1)
        setVar(player, GeConfig.VARBIT_QUANTITY, 0)
        setVar(player, GeConfig.VARBIT_PRICE, 0)
    }

    /** The slot an offer is being set up in, or null when the setup panel is not showing. */
    private fun setupSlot(player: Player): Int? {
        val selected = player.vars[GeConfig.VARBIT_SELECTED_SLOT] - 1
        if (selected !in 0 until GeConfig.SLOT_COUNT) {
            return null
        }
        return selected.takeIf { exchange.offer(player.accountId, it) == null }
    }

    /** The offer whose status panel is showing, or null. */
    private fun selectedOffer(player: Player): GeOffer? {
        val selected = player.vars[GeConfig.VARBIT_SELECTED_SLOT] - 1
        if (selected !in 0 until GeConfig.SLOT_COUNT) {
            return null
        }
        return exchange.offer(player.accountId, selected)
    }

    private fun setupType(player: Player): GeOfferType = GeOfferType.fromClientId(player.vars[GeConfig.VARBIT_TYPE])

    private fun setupItem(player: Player): ItemServerType? {
        val id = player.vars[GeConfig.VARP_ITEM]
        if (id <= 0) {
            return null
        }
        return ServerCacheManager.getItem(id)
    }

    /* Setup panel */

    suspend fun chooseItem(access: ProtectedAccess) {
        val player = access.player
        val slot = setupSlot(player) ?: return
        if (setupType(player) != GeOfferType.Buy) {
            player.mes("Choose the item you want to sell from your inventory.")
            return
        }
        val chosen = access.objDialog("What would you like to buy?", stockMarketRestriction = true, showLastSearched = true)
        // Anything can have changed while the search was open.
        if (setupSlot(player) != slot || setupType(player) != GeOfferType.Buy) {
            return
        }
        val item = uncert(chosen)
        if (!canTrade(player, item, GeOfferType.Buy)) {
            return
        }
        setVar(player, GeConfig.VARP_LAST_SEARCHED, item.id)
        setVar(player, GeConfig.VARP_ITEM, item.id)
        setVar(player, GeConfig.VARBIT_QUANTITY, 1)
        setVar(player, GeConfig.VARBIT_PRICE, items.guidePrice(item.id))
    }

    /** "Offer" on an inventory item: sell it, in the open sell setup or the first free slot. */
    fun offerFromInventory(access: ProtectedAccess, invSlot: Int) {
        val player = access.player
        val obj = player.inv[invSlot] ?: return
        val item = uncert(ServerCacheManager.getItemOrDefault(obj.id))
        if (!canTrade(player, item, GeOfferType.Sell)) {
            return
        }
        val current = setupSlot(player)
        val slot =
            if (current != null && setupType(player) == GeOfferType.Sell) {
                current
            } else {
                exchange.firstEmptySlot(player.accountId)
            }
        if (slot == null) {
            player.mes("You have no free offer slots.")
            return
        }
        val available = available(player, item)
        beginSetup(player, slot, GeOfferType.Sell, item.id, available, items.guidePrice(item.id))
    }

    fun examineInventory(player: Player, invSlot: Int) {
        val obj = player.inv[invSlot] ?: return
        player.mes(ServerCacheManager.getItemOrDefault(obj.id).examine)
    }

    fun adjustQuantity(player: Player, delta: Int) {
        val current = player.vars[GeConfig.VARBIT_QUANTITY]
        setQuantity(player, current.toLong() + delta)
    }

    fun setQuantity(player: Player, value: Long) {
        setupSlot(player) ?: return
        val item = setupItem(player) ?: return
        val max = maxQuantity(player, item)
        setVar(player, GeConfig.VARBIT_QUANTITY, value.coerceIn(1L, max.toLong()).toInt())
    }

    fun setAllOrThousand(player: Player) {
        setupSlot(player) ?: return
        val item = setupItem(player) ?: return
        if (setupType(player) == GeOfferType.Sell) {
            setQuantity(player, available(player, item).toLong())
        } else {
            adjustQuantity(player, 1000)
        }
    }

    suspend fun enterQuantity(access: ProtectedAccess) {
        setupSlot(access.player) ?: return
        val count = access.countDialog("How many do you wish to trade?")
        setQuantity(access.player, count.toLong())
    }

    fun adjustPrice(player: Player, delta: Int) {
        val current = player.vars[GeConfig.VARBIT_PRICE]
        setPrice(player, current.toLong() + delta)
    }

    fun adjustPricePercent(player: Player, percent: Int) {
        val current = player.vars[GeConfig.VARBIT_PRICE].toLong()
        setPrice(player, current + current * percent / 100)
    }

    fun customPercent(player: Player): Int {
        val custom = player.vars[GeConfig.VARBIT_CUSTOM_PERCENT]
        return if (custom in 1..GeConfig.MAX_CUSTOM_PERCENT) custom else GeConfig.DEFAULT_CUSTOM_PERCENT
    }

    fun guidePrice(player: Player) {
        val item = setupItem(player) ?: return
        setPrice(player, items.guidePrice(item.id).toLong())
    }

    fun setPrice(player: Player, value: Long) {
        setupSlot(player) ?: return
        setupItem(player) ?: return
        setVar(player, GeConfig.VARBIT_PRICE, value.coerceIn(1L, Int.MAX_VALUE.toLong()).toInt())
    }

    suspend fun enterPrice(access: ProtectedAccess) {
        setupSlot(access.player) ?: return
        val price = access.countDialog("Set a price for each item:")
        setPrice(access.player, price.toLong())
    }

    suspend fun enterCustomPercent(access: ProtectedAccess) {
        val percent = access.countDialog("Set a custom percentage (1-${GeConfig.MAX_CUSTOM_PERCENT}):")
        setVar(access.player, GeConfig.VARBIT_CUSTOM_PERCENT, percent.coerceIn(1, GeConfig.MAX_CUSTOM_PERCENT))
    }

    fun confirm(access: ProtectedAccess) {
        val player = access.player
        val slot = setupSlot(player) ?: return
        val item = setupItem(player)
        if (item == null) {
            player.mes("You must choose an item first.")
            return
        }
        val type = setupType(player)
        val quantity = player.vars[GeConfig.VARBIT_QUANTITY]
        val price = player.vars[GeConfig.VARBIT_PRICE]
        if (quantity <= 0 || price <= 0) {
            player.mes("Set a quantity and a price for your offer first.")
            return
        }
        if (price.toLong() * quantity > Int.MAX_VALUE) {
            player.mes("Too much money! Your offer cannot be worth more than ${Int.MAX_VALUE.formatAmount} coins.")
            return
        }
        if (!canTrade(player, item, type)) {
            return
        }
        when (type) {
            GeOfferType.Buy -> {
                val cost = price * quantity
                val paid = player.invDel(player.inv, coinsId, cost)
                if (!paid.success) {
                    player.mes("You don't have enough coins.")
                    return
                }
            }
            GeOfferType.Sell -> {
                if (!removeForSale(player, item, quantity)) {
                    player.mes("You don't have enough of that item to sell.")
                    return
                }
            }
        }
        val offer = exchange.place(player.accountId, player.displayName, slot, type, item.id, price, quantity)
        setVar(player, GeConfig.VARP_LAST_OFFER_ITEM, item.id)
        setVar(player, GeConfig.VARP_LAST_OFFER_QUANTITY, quantity)
        setVar(player, GeConfig.VARP_LAST_OFFER_PRICE, price)
        setVar(player, GeConfig.VARP_LAST_OFFER_TYPE, type.clientId)
        back(player)
        refresh(player, slot)
        if (type == GeOfferType.Buy && offer.isActive && exchange.remainingBuyLimit(player.accountId, item.id) <= 0) {
            val minutes = (exchange.buyLimitResetIn(player.accountId, item.id) + 59_999) / 60_000
            player.mes("You've reached the buy limit for ${item.name}; your offer will continue in about $minutes minutes.")
        }
    }

    private fun maxQuantity(player: Player, item: ItemServerType): Int {
        if (setupType(player) == GeOfferType.Sell) {
            return max(1, available(player, item))
        }
        val price = max(1, player.vars[GeConfig.VARBIT_PRICE])
        return max(1, Int.MAX_VALUE / price)
    }

    /** How many of [item] the player carries, counting both the item and its banknote. */
    private fun available(player: Player, item: ItemServerType): Int {
        val cert = item.certlink.takeIf { item.canCert }
        var total = 0L
        for (obj in player.inv.objs) {
            if (obj == null) {
                continue
            }
            if (obj.id == item.id || obj.id == cert) {
                total += obj.count
            }
        }
        return min(total, Int.MAX_VALUE.toLong()).toInt()
    }

    private fun removeForSale(player: Player, item: ItemServerType, quantity: Int): Boolean {
        if (available(player, item) < quantity) {
            return false
        }
        var remaining = quantity
        val unnoted = player.inv.objs.filterNotNull().filter { it.id == item.id }.sumOf { it.count.toLong() }
        val takeUnnoted = min(remaining.toLong(), unnoted).toInt()
        if (takeUnnoted > 0) {
            val result = player.invDel(player.inv, item.id, takeUnnoted)
            if (!result.success) {
                return false
            }
            remaining -= takeUnnoted
        }
        if (remaining > 0) {
            val result = player.invDel(player.inv, item.certlink, remaining)
            if (!result.success) {
                // Put the unnoted ones back rather than leave the player short.
                if (takeUnnoted > 0) {
                    player.invAdd(player.inv, item.id, takeUnnoted)
                }
                return false
            }
        }
        return true
    }

    private fun canTrade(player: Player, item: ItemServerType, type: GeOfferType): Boolean {
        if (item.id == coinsId || !item.stockmarket || !item.tradeable || item.isCert) {
            player.mes("You can't trade that item on the Grand Exchange.")
            return false
        }
        if (player.isSoloIronman && (type != GeOfferType.Buy || item.id != bondId)) {
            player.mes("As an Ironman, you can only use the Grand Exchange to buy bonds.")
            return false
        }
        return true
    }

    /* Status panel */

    fun abort(player: Player) {
        val offer = selectedOffer(player) ?: return
        if (!exchange.abort(offer)) {
            player.mes("That offer has already finished.")
            return
        }
        refresh(player, offer.slot)
    }

    /** Aborts the selected offer, pockets what it holds and sets the slot up again from it. */
    fun modify(access: ProtectedAccess) {
        val player = access.player
        val offer = selectedOffer(player) ?: return
        val type = offer.type
        val remaining = offer.remaining
        exchange.abort(offer)
        collectBox(player, offer, GeConfig.COLLECT_BOX_ITEMS, CollectMode.Default)
        collectBox(player, offer, GeConfig.COLLECT_BOX_COINS, CollectMode.Default)
        refresh(player, offer.slot)
        if (exchange.offer(player.accountId, offer.slot) != null) {
            player.mes("Collect what the offer holds before modifying it.")
            return
        }
        val quantity =
            if (type == GeOfferType.Sell) {
                min(remaining, available(player, ServerCacheManager.getItemOrDefault(offer.item)))
            } else {
                remaining
            }
        beginSetup(player, offer.slot, type, offer.item, max(1, quantity), offer.price)
    }

    enum class CollectMode {
        /** Left click: notes when several non-stackable items wait, the item otherwise. */
        Default,
        Item,
        Note,
        Bank,
    }

    fun collectSelected(player: Player, box: Int, mode: CollectMode) {
        val offer = selectedOffer(player) ?: return
        collectBox(player, offer, box, mode)
        refresh(player, offer.slot)
    }

    fun collectSlot(player: Player, slot: Int, box: Int, mode: CollectMode) {
        val offer = exchange.offer(player.accountId, slot) ?: return
        collectBox(player, offer, box, mode)
        refresh(player, slot)
    }

    fun examineBox(player: Player, slot: Int?, box: Int) {
        val offer = (if (slot == null) selectedOffer(player) else exchange.offer(player.accountId, slot)) ?: return
        val item = if (box == GeConfig.COLLECT_BOX_COINS) coinsId else offer.item
        player.mes(ServerCacheManager.getItemOrDefault(item).examine)
    }

    fun collectAll(player: Player, toBank: Boolean) {
        val mode = if (toBank) CollectMode.Bank else CollectMode.Default
        var any = false
        for (slot in 0 until GeConfig.SLOT_COUNT) {
            val offer = exchange.offer(player.accountId, slot) ?: continue
            if (!offer.hasCollectables) {
                continue
            }
            any = true
            collectBox(player, offer, GeConfig.COLLECT_BOX_ITEMS, mode)
            collectBox(player, offer, GeConfig.COLLECT_BOX_COINS, mode)
            refresh(player, slot)
        }
        if (!any) {
            player.mes("You have nothing to collect.")
        }
    }

    private fun collectBox(player: Player, offer: GeOffer, box: Int, mode: CollectMode) {
        if (box == GeConfig.COLLECT_BOX_COINS) {
            collectCoins(player, offer, mode == CollectMode.Bank)
        } else {
            collectItems(player, offer, mode)
        }
    }

    private fun collectCoins(player: Player, offer: GeOffer, toBank: Boolean) {
        val count = offer.collectCoins
        if (count <= 0) {
            return
        }
        val inv = if (toBank) bank(player) else player.inv
        val result = player.invAdd(inv, coinsId, count)
        if (!result.success) {
            player.mes(if (toBank) "Your bank is full." else "You don't have enough inventory space.")
            return
        }
        exchange.takeCoins(offer, count)
    }

    private fun collectItems(player: Player, offer: GeOffer, mode: CollectMode) {
        val count = offer.collectItems
        if (count <= 0) {
            return
        }
        val item = ServerCacheManager.getItemOrDefault(offer.item)
        if (mode == CollectMode.Bank) {
            val result = player.invAdd(bank(player), item.id, count)
            if (!result.success) {
                player.mes("Your bank is full.")
                return
            }
            exchange.takeItems(offer, count)
            return
        }
        val asNote =
            when (mode) {
                CollectMode.Note -> item.canCert
                CollectMode.Item -> false
                else -> item.canCert && !item.isStackable && count > 1
            }
        val take =
            if (asNote || item.isStackable) {
                count
            } else {
                min(count, player.inv.freeSpace())
            }
        if (take <= 0) {
            player.mes("You don't have enough inventory space.")
            return
        }
        val result = player.invAdd(player.inv, item.id, take, cert = asNote)
        if (!result.success) {
            player.mes("You don't have enough inventory space.")
            return
        }
        exchange.takeItems(offer, take)
    }

    private fun bank(player: Player): Inventory = player.invMap.getOrPut("inv.bank")

    /* Collect-all layer */

    fun repeatOffer(access: ProtectedAccess) {
        val player = access.player
        val item = player.vars[GeConfig.VARP_LAST_OFFER_ITEM]
        if (item <= 0) {
            player.mes("You haven't made an offer to repeat yet.")
            return
        }
        val type = GeOfferType.fromClientId(player.vars[GeConfig.VARP_LAST_OFFER_TYPE])
        val itemType = ServerCacheManager.getItem(item) ?: return
        if (!canTrade(player, itemType, type)) {
            return
        }
        val slot = exchange.firstEmptySlot(player.accountId)
        if (slot == null) {
            player.mes("You have no free offer slots.")
            return
        }
        var quantity = player.vars[GeConfig.VARP_LAST_OFFER_QUANTITY]
        if (type == GeOfferType.Sell) {
            quantity = min(quantity, available(player, itemType))
            if (quantity <= 0) {
                player.mes("You don't have any of that item to sell.")
                return
            }
        }
        beginSetup(player, slot, type, item, max(1, quantity), max(1, player.vars[GeConfig.VARP_LAST_OFFER_PRICE]))
    }

    /* History */

    fun openHistory(access: ProtectedAccess) {
        val player = access.player
        access.ifOpenMainModal(GeConfig.HISTORY)
        val trades = exchange.history(player.accountId)
        player.runClientScript(GeConfig.CS2_HISTORY_INIT)
        for ((index, trade) in trades.withIndex()) {
            player.runClientScript(
                GeConfig.CS2_HISTORY_ADD_LINE,
                index,
                trade.item,
                trade.type.clientId,
                trade.quantity,
                trade.total,
                trade.tax,
            )
        }
        player.runClientScript(GeConfig.CS2_HISTORY_FINISH)
        if (trades.isNotEmpty()) {
            player.ifSetEvents(GeConfig.COMP_HISTORY_LIST, 0 until trades.size * GeConfig.HISTORY_LINE_CHILDREN, IfEvent.Op1, IfEvent.Op10)
        }
    }

    /** The trade a history line child belongs to, or null. */
    fun historyTrade(player: Player, comsub: Int): GeTrade? =
        exchange.history(player.accountId).getOrNull(comsub / GeConfig.HISTORY_LINE_CHILDREN)

    /** "Buy-offer" on a history line: open the exchange with a buy setup for that item. */
    fun buyAgain(access: ProtectedAccess, trade: GeTrade) {
        val player = access.player
        val item = ServerCacheManager.getItem(trade.item) ?: return
        if (!canTrade(player, item, GeOfferType.Buy)) {
            return
        }
        val slot = exchange.firstEmptySlot(player.accountId)
        if (slot == null) {
            player.mes("You have no free offer slots.")
            return
        }
        open(access)
        beginSetup(player, slot, GeOfferType.Buy, item.id, 1, items.guidePrice(item.id))
    }

    /* Prices */

    fun guidePriceText(item: ItemServerType): String {
        val base = uncert(item)
        val price = items.guidePrice(base.id)
        val limit = items.buyLimit(base.id)
        val limitText = if (limit == GeItemData.NO_LIMIT) "" else " You can buy up to ${limit.formatAmount} every four hours."
        return "${base.name}: ${price.formatAmount} coin${if (price == 1) "" else "s"}.$limitText"
    }

    private fun name(item: Int): String = ServerCacheManager.getItemOrDefault(item).name

    private fun setVar(player: Player, name: String, value: Int) {
        VarPlayerIntMapSetter.set(player, name, value)
    }

    private companion object {
        private val logger = InlineLogger()
    }
}
