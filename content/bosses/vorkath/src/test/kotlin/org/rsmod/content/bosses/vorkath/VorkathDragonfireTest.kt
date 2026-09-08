package org.rsmod.content.bosses.vorkath

import kotlin.test.Test
import kotlin.test.assertEquals

class VorkathDragonfireTest {
    @Test
    fun everyProtectionCombinationRestoresTheUnreducedRollAndMatchesTheWikiCaps() {
        for (shield in listOf(false, true)) {
            for (protect in listOf(false, true)) {
                for (antifire in listOf(false, true)) {
                    for (superAntifire in listOf(false, true)) {
                        val snapshot =
                            VorkathDragonfire.snapshot(shield, protect, antifire, superAntifire)
                        val cap =
                            when {
                                shield -> 20
                                protect -> 30
                                else -> 80
                            }
                        val resistedCap = if (shield || protect) cap else 50
                        val reduction =
                            when {
                                superAntifire -> 20
                                antifire -> 10
                                else -> 0
                            }
                        assertEquals(cap, snapshot.maximum)
                        assertEquals(resistedCap, snapshot.resistedMaximum)
                        assertEquals(reduction, snapshot.potionReduction)
                        for (raw in 0..cap) {
                            assertEquals(
                                (raw - reduction).coerceAtLeast(0),
                                snapshot.damage(raw, VorkathStandardAttack.DRAGONFIRE),
                            )
                            for (variant in
                                listOf(
                                    VorkathStandardAttack.VENOM_DRAGONFIRE,
                                    VorkathStandardAttack.PRAYER_DRAGONFIRE,
                                )) {
                                assertEquals(
                                    (raw - reduction - 5).coerceAtLeast(0),
                                    snapshot.damage(raw, variant),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun shieldAndRegularAntifireProduceElevenZeroOutcomesOutOfTwentyOneRolls() {
        val snapshot = VorkathDragonfire.snapshot(true, false, true, false)
        val outcomes =
            (0..snapshot.maximum).map { snapshot.damage(it, VorkathStandardAttack.DRAGONFIRE) }
        assertEquals(11, outcomes.count { it == 0 })
        assertEquals((1..10).toList(), outcomes.filter { it > 0 })
    }

    @Test
    fun shieldAndSuperAntifireAbsorbEveryBreathDamageOutcome() {
        val snapshot = VorkathDragonfire.snapshot(true, true, true, true)
        for (raw in 0..snapshot.maximum) {
            assertEquals(0, snapshot.damage(raw, VorkathStandardAttack.DRAGONFIRE))
            assertEquals(0, snapshot.damage(raw, VorkathStandardAttack.PRAYER_DRAGONFIRE))
            assertEquals(0, snapshot.damage(raw, VorkathStandardAttack.VENOM_DRAGONFIRE))
        }
    }

    @Test
    fun prayerAndSuperAntifireRetainTenNormalAndFiveVariantMaximum() {
        val snapshot = VorkathDragonfire.snapshot(false, true, false, true)
        assertEquals(10, snapshot.damage(snapshot.maximum, VorkathStandardAttack.DRAGONFIRE))
        assertEquals(5, snapshot.damage(snapshot.maximum, VorkathStandardAttack.PRAYER_DRAGONFIRE))
        assertEquals(5, snapshot.damage(snapshot.maximum, VorkathStandardAttack.VENOM_DRAGONFIRE))
    }

    @Test
    fun bareResistanceStillDealsReducedDamageInsteadOfAlwaysSplashing() {
        val snapshot = VorkathDragonfire.snapshot(false, false, false, false)
        assertEquals(80, snapshot.maximum)
        assertEquals(50, snapshot.resistedMaximum)
        assertEquals(
            50,
            snapshot.damage(snapshot.resistedMaximum, VorkathStandardAttack.DRAGONFIRE),
        )
        assertEquals(
            45,
            snapshot.damage(snapshot.resistedMaximum, VorkathStandardAttack.PRAYER_DRAGONFIRE),
        )
    }
}
