package org.rsmod.content.other.emirsarena

import org.rsmod.api.attr.AttributeKey
import org.rsmod.content.other.emirsarena.duel.Duel
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

/** Names, places and player attributes shared by the Emir's Arena scripts. */
object EmirsArena {
    const val AREA: String = "area.emirs_arena"

    /** Inside the hospital, between the beds; where duellists are taken when a duel ends. */
    val LOBBY: CoordGrid = CoordGrid(3365, 3275, 0)
    const val LOBBY_RADIUS: Int = 2

    const val OPTIONS_INTERFACE: String = "interface.pvp_arena_legacyduel_options"
    const val CONFIRM_INTERFACE: String = "interface.pvp_arena_legacyduel_confirm"
    const val SCOREBOARD_INTERFACE: String = "interface.pvp_arena_scoreboard"

    const val OPTIONS_PREFIX: String = "component.pvp_arena_legacyduel_options:"
    const val CONFIRM_PREFIX: String = "component.pvp_arena_legacyduel_confirm:"
    const val SCOREBOARD_TEXT: String = "component.pvp_arena_scoreboard:text"

    /**
     * The cache has no Duel Arena stake screen any more, so stakes reuse the trade screens: the
     * trade main window for the two offers, the party-drop side panel (whose script draws the
     * backpack with "Offer 1/5/X/All") to add items, and the trade confirmation screen.
     */
    const val STAKE_INTERFACE: String = "interface.trademain"
    const val STAKE_SIDE_INTERFACE: String = "interface.partydrop_side"
    const val STAKE_CONFIRM_INTERFACE: String = "interface.tradeconfirm"
    const val STAKE_PREFIX: String = "component.trademain:"
    const val STAKE_SIDE_ITEMS: String = "component.partydrop_side:items"
    const val STAKE_CONFIRM_PREFIX: String = "component.tradeconfirm:"
    /** Persisted holding inventory for stakes; survives crashes and restarts mid-duel. */
    const val STAKE_INV: String = "inv.emirs_arena_stake"

    /** The inv id the trade screens are hard-wired to; the stake inv is shown under this id. */
    const val STAKE_DISPLAY_INV: String = "inv.tradeoffer"
    const val STAKE_MODIFIED_MINE_VARBIT: String = "varbit.trade_this_player_removed"
    const val STAKE_MODIFIED_OTHER_VARBIT: String = "varbit.trade_other_player_removed"
    const val OFFER_QUANTITY_VARBIT: String = "varbit.farming_tools_selectedquantity"

    /** The client mirrors this varp into the option and confirm screens (clientscripts 6169/6181). */
    const val RULES_VARP: String = "varp.dueloptions"
    const val ACCEPT_OPTIONS_VARBIT: String = "varbit.pvpa_legacyduel_transmit_iacceptoptions"
    const val ACCEPT_CONFIRM_VARBIT: String = "varbit.pvpa_legacyduel_transmit_iacceptconfirm"

    const val RANK_POINTS_VARBIT: String = "varbit.pvpa_rank_points"
    const val REWARD_POINTS_VARBIT: String = "varbit.pvpa_points_currency"
    const val REWARD_CURRENCY: String = "currency.pvp_arena_points"
    const val REWARD_SHOP_INV: String = "inv.pvpa_shop_inv"

    /**
     * Inside the arena the attack slot reads "Challenge"; clicking it (or the chat request, which
     * the client turns into the same option) is turned into a challenge by the arena's attack
     * hook. During a fight the slot reads "Fight".
     */
    const val CHALLENGE_SLOT: Int = 1
    const val FIGHT_SLOT: Int = 1

    const val START_QUEUE: String = "queue.emirs_arena_start"
    const val LEAVE_QUEUE: String = "queue.emirs_arena_leave"

    const val TRAPDOOR_LOC: String = "loc.pvpa_battlearea_trapdoor"

    /**
     * Js5 index 11 groups (the ids the client plays, listed on the OSRS wiki jingle catalogue):
     * "Commence The Fight!" when the duellists are placed in the arena, "You Are Victorious!" for
     * the winner. The loser's death jingle comes from the normal death sequence.
     */
    const val DUEL_START_JINGLE: Int = 97
    const val DUEL_WIN_JINGLE: Int = 98

    /** Worn slots the options screen has a toggle for (6, 8 and 11 are client-only slots). */
    val WORN_OPTION_SLOTS: List<Int> = listOf(0, 1, 2, 3, 4, 5, 7, 9, 10, 12, 13)

    /** Weapons with negative stats that the "Fun Weapons" rule allows. */
    val FUN_WEAPONS: List<String> =
        listOf(
            "obj.rubber_chicken",
            "obj.flowers_waterfall_quest",
            "obj.flowers_waterfall_quest_red",
            "obj.flowers_waterfall_quest_blue",
            "obj.flowers_waterfall_quest_yellow",
            "obj.flowers_waterfall_quest_purple",
            "obj.flowers_waterfall_quest_orange",
            "obj.flowers_waterfall_quest_mixed",
            "obj.flowers_waterfall_quest_white",
            "obj.flowers_waterfall_quest_black",
            "obj.stale_baguette",
            "obj.golden_tench",
            "obj.birthday_cake",
            "obj.twocats_mouse_toy",
        )

    const val SCOREBOARD_SIZE: Int = 50

    const val SUPPLIES_MESSAGE: String =
        "The supplies chest is only stocked on official PvP Arena worlds."
}

/** Session and saved state kept on the player. */
object EmirsArenaAttributes {
    val DUEL: AttributeKey<Duel> = AttributeKey()
    val CHALLENGE_TARGET: AttributeKey<Player> = AttributeKey()

    val LEGACY_WINS: AttributeKey<Int> = AttributeKey(persistenceKey = "emirs_arena_legacy_wins")
    val LEGACY_LOSSES: AttributeKey<Int> =
        AttributeKey(persistenceKey = "emirs_arena_legacy_losses")
    val RANKED_WINS: AttributeKey<Int> = AttributeKey(persistenceKey = "emirs_arena_ranked_wins")
    val RANKED_LOSSES: AttributeKey<Int> =
        AttributeKey(persistenceKey = "emirs_arena_ranked_losses")
    val WIN_STREAK: AttributeKey<Int> = AttributeKey(persistenceKey = "emirs_arena_win_streak")
    val SAVED_PRESET: AttributeKey<Int> = AttributeKey(persistenceKey = "emirs_arena_duel_preset")
    val LAST_RULES: AttributeKey<Int> = AttributeKey(persistenceKey = "emirs_arena_last_rules")
}
