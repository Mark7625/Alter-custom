package org.rsmod.content.other.grandexchange

/**
 * Names and numbers the Grand Exchange plugin depends on. Everything here was read from the
 * revision-240 cache (`interface.ge_offers` and friends, their clientscripts) or the OSRS wiki.
 */
object GeConfig {
    /** Offer slots per player. Members have eight; this server treats every account as a member. */
    const val SLOT_COUNT = 8

    /** The convenience fee sellers pay, in thousandths: 20 = 2%. */
    const val TAX_PER_MILLE = 20

    /** The fee is capped per item sold. */
    const val TAX_CAP_PER_ITEM = 5_000_000

    /** Buy limits reset four hours after the first purchase of the window. */
    const val BUY_LIMIT_WINDOW_MILLIS: Long = 4L * 60 * 60 * 1000

    /** How many completed trades the history screen keeps per player. */
    const val HISTORY_SIZE = 50

    /**
     * Archive group in the client's jingle index (11) of "Grand Transaction", the jingle that plays
     * when an offer completes. The `jingle.grand_exchange_trade_jingle` gameval resolves to a
     * different number space, so the group was matched by decoding the cache's jingles and finding
     * the one whose horn line is a note-for-note copy of The Trade Parade (10.7 s, as on the wiki).
     */
    const val COMPLETION_JINGLE_GROUP = 86

    /** Where the world's offer book is written, relative to the server working directory. */
    const val SAVE_PATH = ".data/grand-exchange.json"

    /** Ticks between background saves while there are unsaved changes. */
    const val SAVE_INTERVAL_TICKS = 50

    const val COINS = "obj.coins"
    const val BOND = "obj.osrs_bond"

    const val OFFERS = "interface.ge_offers"
    const val OFFERS_SIDE = "interface.ge_offers_side"
    const val COLLECT = "interface.ge_collect"
    const val HISTORY = "interface.ge_history"

    const val PREFIX = "component.ge_offers:"
    const val COMP_HISTORY = PREFIX + "history"
    const val COMP_BACK = PREFIX + "back"
    const val COMP_COLLECT_ALL = PREFIX + "collectall"
    const val COMP_DETAILS = PREFIX + "details"
    const val COMP_DETAILS_COLLECT = PREFIX + "details_collect"
    const val COMP_SETUP = PREFIX + "setup"
    const val COMP_SETUP_CONFIRM = PREFIX + "setup_confirm"
    const val COMP_SIDE_ITEMS = "component.ge_offers_side:items"
    const val COMP_COLLECT_INV = "component.ge_collect:collect_inv"
    const val COMP_COLLECT_BANK = "component.ge_collect:collect_bank"
    const val COMP_HISTORY_LIST = "component.ge_history:list"
    const val COMP_HISTORY_EXCHANGE = "component.ge_history:exchange"

    /** `ge_history_addline` creates this many children per trade, so comsub / 6 is the line. */
    const val HISTORY_LINE_CHILDREN = 6

    fun indexSlot(slot: Int): String = PREFIX + "index_$slot"

    fun collectSlot(slot: Int): String = "component.ge_collect:collect_$slot"

    /** Index slot box children created by `ge_offers_index_initslot`. */
    const val INDEX_CHILD_VIEW = 2
    const val INDEX_CHILD_BUY = 3
    const val INDEX_CHILD_SELL = 4

    /** Setup panel children created by `ge_offers_setup_init`, in creation order. */
    const val SETUP_CHOOSE_ITEM = 0
    const val SETUP_QTY_MINUS_1 = 1
    const val SETUP_QTY_PLUS_1_MOBILE = 2
    const val SETUP_QTY_PLUS_1 = 3
    const val SETUP_QTY_PLUS_10 = 4
    const val SETUP_QTY_PLUS_100 = 5
    const val SETUP_QTY_PLUS_1K_OR_ALL = 6
    const val SETUP_QTY_ENTER = 7
    const val SETUP_PRICE_MINUS_1 = 8
    const val SETUP_PRICE_PLUS_1 = 9
    const val SETUP_PRICE_MINUS_5PCT = 10
    const val SETUP_PRICE_GUIDE = 11
    const val SETUP_PRICE_ENTER = 12
    const val SETUP_PRICE_PLUS_5PCT = 13
    const val SETUP_PRICE_MINUS_CUSTOM = 14
    const val SETUP_PRICE_PLUS_CUSTOM = 15
    const val SETUP_CHILD_COUNT = 18

    /** Details panel children created by script 819. */
    const val DETAILS_ABORT = 0
    const val DETAILS_MODIFY = 1

    /** The two collect boxes of an offer: slot 0 of its inventory holds items, slot 1 coins. */
    const val COLLECT_BOX_ITEMS = 0
    const val COLLECT_BOX_COINS = 1

    /**
     * Clickable children of `details_collect` (script 816): two background boxes come first, then
     * the item icon and the coins icon.
     */
    const val DETAILS_COLLECT_ITEMS_CHILD = 2
    const val DETAILS_COLLECT_COINS_CHILD = 3

    /** Clickable children of `ge_collect:collect_N` (script 789): three frames, then the icons. */
    const val COLLECT_SLOT_ITEMS_CHILD = 3
    const val COLLECT_SLOT_COINS_CHILD = 4

    /** Maps a collect-box child comsub to [COLLECT_BOX_ITEMS] / [COLLECT_BOX_COINS], or null. */
    fun collectBoxOf(comsub: Int, itemsChild: Int, coinsChild: Int): Int? =
        when (comsub) {
            itemsChild -> COLLECT_BOX_ITEMS
            coinsChild -> COLLECT_BOX_COINS
            else -> null
        }

    /** `ge_offers_index_inittop` children on the collect-all layer. */
    const val COLLECT_ALL_BUTTON = 0
    const val REPEAT_OFFER_BUTTON = 1

    /** The two-slot inventories the client draws each slot's collectable items and coins from. */
    val COLLECT_INVS: List<String> =
        listOf(
            "inv.tradingpost_sell_0",
            "inv.tradingpost_sell_1",
            "inv.tradingpost_sell_2",
            "inv.tradingpost_sell_3",
            "inv.tradingpost_sell_4",
            "inv.tradingpost_sell_5",
            "inv.ge_collect_6",
            "inv.ge_collect_7",
        )

    /**
     * Varps the client reads the fee already paid on each slot from. Slots 2-5 point at music varps
     * in the cache's script (`currentsong`, `musiclength`, ...), so only the others are written.
     */
    val TAX_VARPS: List<String?> =
        listOf(
            "varp.ge_tax_slot_0",
            "varp.ge_tax_slot_1",
            null,
            null,
            null,
            null,
            "varp.ge_tax_slot_6",
            "varp.ge_tax_slot_7",
        )

    fun itemSinkObjVarp(slot: Int): String = "varp.ge_itemsink_obj_$slot"

    fun itemSinkPriceVarp(slot: Int): String = "varp.ge_itemsink_price_$slot"

    const val VARP_ITEM = "varp.tradingpost_search"
    const val VARP_LAST_SEARCHED = "varp.ge_last_searched"
    const val VARP_LAST_OFFER_ITEM = "varp.ge_last_offer_item"
    const val VARP_LAST_OFFER_QUANTITY = "varp.ge_last_offer_quantity"
    const val VARP_LAST_OFFER_PRICE = "varp.ge_last_offer_price"
    const val VARP_LAST_OFFER_TYPE = "varp.ge_last_offer_type"
    const val VARBIT_SELECTED_SLOT = "varbit.ge_selectedslot"
    const val VARBIT_TYPE = "varbit.ge_newoffer_type"
    const val VARBIT_QUANTITY = "varbit.ge_newoffer_quantity"
    const val VARBIT_PRICE = "varbit.ge_newoffer_price"
    const val VARBIT_CUSTOM_PERCENT = "varbit.ge_price_custom"
    const val VARBIT_TAX_RATE = "varbit.ge_transmit_taxrate"

    /** Clientscripts that fill the history screen. */
    const val CS2_HISTORY_INIT = 1644
    const val CS2_HISTORY_ADD_LINE = 1645
    const val CS2_HISTORY_FINISH = 1646

    const val LOC_BOOTH_EXCHANGE = "loc.exchange_bank_wall_exchange"
    const val LOC_BOOTH_BANK = "loc.exchange_bank_wall_bank"
    val CLERKS: List<String> = listOf("npc.ge_clerk_1", "npc.ge_clerk_2", "npc.ge_clerk_3", "npc.ge_clerk_4")

    /** Pricing experts: npc to the family of goods they quote. */
    val EXPERTS: Map<String, String> =
        mapOf(
            "npc.ge_boss" to "anything",
            "npc.ge_expert_ores" to "ores, bars and gems",
            "npc.ge_expert_herbs" to "herbs and potions",
            "npc.ge_expert_runes" to "runes",
            "npc.ge_expert_logs" to "logs",
            "npc.ge_expert_combat" to "weapons and armour",
        )

    const val MAX_CUSTOM_PERCENT = 100
    const val DEFAULT_CUSTOM_PERCENT = 5
}
