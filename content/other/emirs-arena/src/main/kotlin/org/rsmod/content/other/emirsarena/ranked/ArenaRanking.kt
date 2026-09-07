package org.rsmod.content.other.emirsarena.ranked

/**
 * Point bookkeeping for ranked duels.
 *
 * Reward points follow the official numbers: 16 for a win, 12 for a loss, and a win-streak bonus of
 * one extra point per consecutive win up to ten (26 per win at most). Rank points start at 2,500,
 * cap at 15,000 and move by a fixed stake that is nudged towards the underdog, so beating a much
 * higher rank pays more than beating a much lower one.
 */
object ArenaRanking {
    const val STARTING_RANK_POINTS: Int = 2500
    const val MAX_POINTS: Int = 15_000

    const val WIN_REWARD_POINTS: Int = 16
    const val LOSS_REWARD_POINTS: Int = 12
    const val MAX_STREAK_BONUS: Int = 10

    private const val BASE_STAKE = 25
    private const val STAKE_SWING = 15
    private const val SWING_RANGE = 1000

    /** Reward points for a win given the number of wins already in the streak before this one. */
    fun winRewardPoints(previousStreak: Int): Int =
        WIN_REWARD_POINTS + previousStreak.coerceIn(0, MAX_STREAK_BONUS)

    fun addRewardPoints(current: Int, gained: Int): Int = (current + gained).coerceIn(0, MAX_POINTS)

    /** Rank points the winner gains; the loser loses the same amount (floored at zero). */
    fun rankStake(winnerRank: Int, loserRank: Int): Int {
        val diff = (loserRank - winnerRank).coerceIn(-SWING_RANGE, SWING_RANGE)
        return BASE_STAKE + diff * STAKE_SWING / SWING_RANGE
    }

    fun applyWin(rank: Int, stake: Int): Int = (rank + stake).coerceIn(0, MAX_POINTS)

    fun applyLoss(rank: Int, stake: Int): Int = (rank - stake).coerceIn(0, MAX_POINTS)

    /** A stored rank of zero means the player has never fought ranked; they start at 2,500. */
    fun effectiveRank(stored: Int): Int = if (stored <= 0) STARTING_RANK_POINTS else stored

    fun tier(rankPoints: Int): RankTier = RankTier.of(rankPoints)
}

/**
 * The rank ladder, Bronze I at the bottom up to Dragon Grandmaster at 9,000 points. Each metal has
 * three numbered steps of 250 points; the dragon ranks above are named steps.
 */
enum class RankTier(val label: String, val minPoints: Int) {
    BronzeI("Bronze I", 0),
    BronzeII("Bronze II", 250),
    BronzeIII("Bronze III", 500),
    IronI("Iron I", 750),
    IronII("Iron II", 1000),
    IronIII("Iron III", 1250),
    SteelI("Steel I", 1500),
    SteelII("Steel II", 1750),
    SteelIII("Steel III", 2000),
    MithrilI("Mithril I", 2250),
    MithrilII("Mithril II", 2500),
    MithrilIII("Mithril III", 2750),
    AdamantI("Adamant I", 3000),
    AdamantII("Adamant II", 3250),
    AdamantIII("Adamant III", 3500),
    RuneI("Rune I", 3750),
    RuneII("Rune II", 4250),
    RuneIII("Rune III", 4750),
    DragonI("Dragon I", 5250),
    DragonII("Dragon II", 5750),
    DragonIII("Dragon III", 6250),
    DragonElite("Dragon Elite", 6750),
    DragonMaster("Dragon Master", 7750),
    DragonGrandmaster("Dragon Grandmaster", 9000);

    companion object {
        fun of(rankPoints: Int): RankTier = entries.last { rankPoints >= it.minPoints }
    }
}
