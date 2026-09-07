package org.rsmod.content.other.emirsarena.ranked

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ArenaRankingTest {
    @Test
    fun `reward points follow the official win and loss values with a capped streak bonus`() {
        assertEquals(16, ArenaRanking.winRewardPoints(previousStreak = 0))
        assertEquals(19, ArenaRanking.winRewardPoints(previousStreak = 3))
        assertEquals(26, ArenaRanking.winRewardPoints(previousStreak = 10))
        assertEquals(26, ArenaRanking.winRewardPoints(previousStreak = 40))
        assertEquals(12, ArenaRanking.LOSS_REWARD_POINTS)
    }

    @Test
    fun `reward points never exceed the cap`() {
        assertEquals(15_000, ArenaRanking.addRewardPoints(14_990, 26))
        assertEquals(15_000, ArenaRanking.addRewardPoints(15_000, 12))
    }

    @Test
    fun `unranked players start at two and a half thousand`() {
        assertEquals(2500, ArenaRanking.effectiveRank(0))
        assertEquals(2500, ArenaRanking.effectiveRank(-5))
        assertEquals(3100, ArenaRanking.effectiveRank(3100))
    }

    @Test
    fun `beating a higher rank is worth more than beating a lower one`() {
        val even = ArenaRanking.rankStake(winnerRank = 2500, loserRank = 2500)
        val upset = ArenaRanking.rankStake(winnerRank = 2500, loserRank = 3500)
        val expected = ArenaRanking.rankStake(winnerRank = 3500, loserRank = 2500)
        assertEquals(25, even)
        assertTrue(upset > even)
        assertTrue(expected < even)
        assertEquals(40, ArenaRanking.rankStake(winnerRank = 0, loserRank = 10_000))
        assertEquals(10, ArenaRanking.rankStake(winnerRank = 10_000, loserRank = 0))
    }

    @Test
    fun `rank points stay within zero and the cap`() {
        assertEquals(0, ArenaRanking.applyLoss(10, 25))
        assertEquals(15_000, ArenaRanking.applyWin(14_990, 25))
    }

    @Test
    fun `rank tiers run from Bronze I to Dragon Grandmaster`() {
        assertEquals(RankTier.BronzeI, RankTier.of(0))
        assertEquals(RankTier.BronzeI, RankTier.of(249))
        assertEquals(RankTier.BronzeII, RankTier.of(250))
        assertEquals(RankTier.MithrilII, RankTier.of(2500))
        assertEquals(RankTier.DragonMaster, RankTier.of(8999))
        assertEquals(RankTier.DragonGrandmaster, RankTier.of(9000))
        assertEquals(RankTier.DragonGrandmaster, RankTier.of(15_000))
        val thresholds = RankTier.entries.map { it.minPoints }
        assertEquals(thresholds.sorted(), thresholds, "tiers must be declared in ascending order")
    }
}
