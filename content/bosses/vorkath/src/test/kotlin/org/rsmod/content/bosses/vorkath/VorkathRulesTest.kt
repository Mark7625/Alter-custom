package org.rsmod.content.bosses.vorkath

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VorkathRulesTest {
    @Test
    fun craterEntranceWallAcceptsItsFullApproachArea() {
        assertTrue(VorkathRules.isCraterWall(25, 19))
        assertTrue(VorkathRules.isCraterWall(32, 20))
        assertTrue(VorkathRules.isCraterWall(38, 22))
    }

    @Test
    fun craterEntranceWallRejectsOtherUngaelIceChunks() {
        assertFalse(VorkathRules.isCraterWall(24, 20))
        assertFalse(VorkathRules.isCraterWall(39, 20))
        assertFalse(VorkathRules.isCraterWall(29, 18))
        assertFalse(VorkathRules.isCraterWall(29, 23))
        assertFalse(VorkathRules.isCraterWall(44, 26))
    }

    @Test
    fun screenshotTileIsRecognizedAsPublicCraterApproach() {
        assertTrue(VorkathRules.isPublicCraterApproach(worldX = 2272, worldZ = 4052, level = 0))
    }

    @Test
    fun matchingLocalTileInAnotherRegionIsRejected() {
        assertFalse(
            VorkathRules.isPublicCraterApproach(
                worldX = (34 * 64) + 32,
                worldZ = (63 * 64) + 20,
                level = 0,
            )
        )
    }

    @Test
    fun craterApproachOnAnotherPlaneIsRejected() {
        assertFalse(VorkathRules.isPublicCraterApproach(worldX = 2272, worldZ = 4052, level = 1))
    }

    @Test
    fun firstSpecialCanBeAcid() = assertEquals(VorkathSpecial.ACID, VorkathRules.firstSpecial(0))

    @Test
    fun firstSpecialCanBeSpawn() =
        assertEquals(VorkathSpecial.ZOMBIFIED_SPAWN, VorkathRules.firstSpecial(1))

    @Test
    fun acidAlternatesToSpawn() =
        assertEquals(VorkathSpecial.ZOMBIFIED_SPAWN, VorkathRules.nextSpecial(VorkathSpecial.ACID))

    @Test
    fun spawnAlternatesToAcid() =
        assertEquals(VorkathSpecial.ACID, VorkathRules.nextSpecial(VorkathSpecial.ZOMBIFIED_SPAWN))

    @Test fun fireballDirectMaximum() = assertEquals(121, VorkathRules.fireballMaximum(0))

    @Test fun fireballAdjacentMaximum() = assertEquals(60, VorkathRules.fireballMaximum(1))

    @Test fun fireballTwoTilesAvoids() = assertEquals(0, VorkathRules.fireballMaximum(2))

    @Test fun fireballFarAvoids() = assertEquals(0, VorkathRules.fireballMaximum(20))

    @Test
    fun standardDragonfireKeepsItsMaximum() =
        assertEquals(80, VorkathRules.dragonfireMaximum(80, VorkathStandardAttack.DRAGONFIRE))

    @Test
    fun venomDragonfireHasTheFivePointLowerMaximum() =
        assertEquals(75, VorkathRules.dragonfireMaximum(80, VorkathStandardAttack.VENOM_DRAGONFIRE))

    @Test
    fun prayerDragonfireHasTheFivePointLowerMaximum() =
        assertEquals(
            45,
            VorkathRules.dragonfireMaximum(50, VorkathStandardAttack.PRAYER_DRAGONFIRE),
        )

    @Test fun spawnFullHealthMaximum() = assertEquals(60, VorkathRules.zombifiedSpawnMaximum(38))

    @Test fun spawnHalfHealthScales() = assertEquals(30, VorkathRules.zombifiedSpawnMaximum(19))

    @Test fun spawnOneHealthScales() = assertEquals(1, VorkathRules.zombifiedSpawnMaximum(1))

    @Test
    fun spawnZeroHealthCannotExplode() = assertEquals(0, VorkathRules.zombifiedSpawnMaximum(0))

    @Test fun spawnHealthIsCapped() = assertEquals(60, VorkathRules.zombifiedSpawnMaximum(100))

    @Test fun acidHalvesEvenDamage() = assertEquals(20, VorkathRules.acidDamage(40))

    @Test fun acidRoundsOddDamageDown() = assertEquals(20, VorkathRules.acidDamage(41))

    @Test fun acidZeroStaysZero() = assertEquals(0, VorkathRules.acidDamage(0))

    @Test fun acidNegativeCannotHeal() = assertEquals(0, VorkathRules.acidDamage(-5))

    @Test
    fun meleeIsOnlyAdjacent() =
        assertTrue(VorkathStandardAttack.MELEE in VorkathRules.standardWeights(true))

    @Test
    fun meleeIsNeverRanged() =
        assertFalse(VorkathStandardAttack.MELEE in VorkathRules.standardWeights(false))

    @Test
    fun adjacentMagicUsesSupportedRelativeWeight() =
        assertEquals(3, VorkathRules.standardWeights(true)[VorkathStandardAttack.MAGIC])

    @Test
    fun rangedIsFavouredAtRange() =
        assertEquals(4, VorkathRules.standardWeights(false)[VorkathStandardAttack.RANGED])

    @Test
    fun allFourBreathsPresentAdjacent() =
        assertEquals(
            4,
            VorkathRules.standardWeights(true).keys.count {
                it.name.contains("DRAGONFIRE") || it == VorkathStandardAttack.FIREBALL
            },
        )

    @Test fun rangedPoolHasSixAttacks() = assertEquals(6, VorkathRules.standardWeights(false).size)

    @Test fun fortyNineIsNotGuaranteedHead() = assertFalse(VorkathRules.isGuaranteedHeadKill(49))

    @Test fun fiftiethIsGuaranteedHead() = assertTrue(VorkathRules.isGuaranteedHeadKill(50))

    @Test fun hundredthIsNotGuaranteedHead() = assertFalse(VorkathRules.isGuaranteedHeadKill(100))

    @Test fun zeroIsNotGuaranteedHead() = assertFalse(VorkathRules.isGuaranteedHeadKill(0))

    @Test fun feeAtThresholdIsAffordable() = assertTrue(VorkathRules.storageFeeAffordable(100_000))

    @Test
    fun feeBelowThresholdIsNotAffordable() = assertFalse(VorkathRules.storageFeeAffordable(99_999))

    @Test fun zeroTicksFormats() = assertEquals("0.0 seconds", VorkathRules.formatTicks(0))

    @Test
    fun tenTicksFormatsSixSeconds() = assertEquals("6.0 seconds", VorkathRules.formatTicks(10))

    @Test
    fun oneHundredTicksFormatsOneMinute() = assertEquals("1:00.0", VorkathRules.formatTicks(100))

    @Test
    fun magicRangedRatioHoldsInBothPoolsWithoutForcingMelee() {
        for (adjacent in listOf(false, true)) {
            val weights = VorkathRules.standardWeights(adjacent)
            assertEquals(
                3 * weights.getValue(VorkathStandardAttack.RANGED),
                4 * weights.getValue(VorkathStandardAttack.MAGIC),
            )
            assertTrue(weights.values.all { it > 0 })
            assertTrue(weights.keys.any { it != VorkathStandardAttack.MELEE })
        }
    }

    @Test
    fun fireballHalvesTheSampledHitAndDoesNotReroll() {
        for (damage in 0..121) {
            assertEquals(damage, VorkathRules.fireballDamage(damage, 0))
            assertEquals(damage / 2, VorkathRules.fireballDamage(damage, 1))
            assertEquals(0, VorkathRules.fireballDamage(damage, 2))
        }
        assertEquals(0, VorkathRules.fireballDamage(-10, 0))
    }

    @Test
    fun spawnExplosionUsesCurrentHealthAndLethalResolutionPreventsDamage() {
        assertEquals(60, VorkathRules.zombifiedSpawnDamage(38))
        assertEquals(30, VorkathRules.zombifiedSpawnDamage(19))
        assertEquals(0, VorkathRules.zombifiedSpawnDamage(0))
        assertEquals(0, VorkathRules.zombifiedSpawnDamage(-1))
    }
}
