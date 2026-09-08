package org.rsmod.content.bosses.vorkath

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.rsmod.map.CoordGrid

class VorkathProjectilesTest {
    private val boss = CoordGrid(2269, 4062)
    private val southMouth = CoordGrid(2272, 4063)

    @Test
    fun everyAttackSelectsItsNativeTravelAndImpact() {
        val expected = mapOf(
            VorkathStandardAttack.MELEE to (null to null),
            VorkathStandardAttack.RANGED to (1477 to 1478),
            VorkathStandardAttack.MAGIC to (1479 to 1480),
            VorkathStandardAttack.DRAGONFIRE to (393 to 1466),
            VorkathStandardAttack.VENOM_DRAGONFIRE to (1470 to 1472),
            VorkathStandardAttack.PRAYER_DRAGONFIRE to (1471 to 1473),
            VorkathStandardAttack.FIREBALL to (1481 to null),
        )
        assertEquals(VorkathStandardAttack.entries.toSet(), expected.keys)
        for ((attack, ids) in expected) {
            assertEquals(ids.first, VorkathProjectiles.standard(attack)?.spot, attack.name)
            assertEquals(ids.second, VorkathProjectiles.impact(attack), attack.name)
        }
        assertNull(VorkathProjectiles.standard(VorkathStandardAttack.MELEE))
    }

    @Test
    fun suppliedNormalWitnessesReplayTheirRawGeometryAndImpactTicks() {
        // rsprox-3279:117607: ranged at t5093, damage t5096.
        val ranged = VorkathProjectiles.RANGED.fromMouth(southMouth, CoordGrid(2271, 4058), 1908)
        assertEquals(listOf(1477, 30, 95, 14, 128, 142, 124),
            listOf(ranged.spotanim, ranged.startTime, ranged.endTime, ranged.angle,
                ranged.progress, ranged.startHeight, ranged.endHeight))
        assertEquals(3, ranged.endTime / 30)
        assertEquals(-1909, ranged.targetIndex)
        // rsprox-3279:118410: magic at t5139, E80.
        val magic = VorkathProjectiles.MAGIC.fromMouth(southMouth, CoordGrid(2271, 4061), 1908)
        assertEquals(80, magic.endTime)
        assertEquals(2, magic.endTime / 30)
    }

    @Test
    fun standardBreathsAndIceUseDistanceInsteadOfAFixedStyleDelay() {
        val specs = listOf(VorkathProjectiles.RANGED, VorkathProjectiles.MAGIC,
            VorkathProjectiles.DRAGONFIRE, VorkathProjectiles.VENOM,
            VorkathProjectiles.PRAYER, VorkathProjectiles.ICE)
        // Observed union across all 2,547 supplied normal/ice packets.
        val fixtures = listOf(2 to 80, 3 to 85, 4 to 90, 5 to 95,
            6 to 100, 7 to 105, 8 to 110, 9 to 115)
        for (spec in specs) {
            for ((distance, end) in fixtures) {
                val shot = spec.fromMouth(southMouth, southMouth.translate(0, -distance), 0)
                assertEquals(end, shot.endTime, "spot=${spec.spot}, distance=$distance")
                assertEquals(30, shot.startTime)
                assertEquals(14, shot.angle)
                assertEquals(128, shot.progress)
                assertEquals(142, shot.startHeight)
                assertEquals(124, shot.endHeight)
            }
        }
    }

    @Test
    fun highArcsAndRapidShotsKeepDistinctGeometryWithoutHoming() {
        val fixtures = listOf(
            VorkathProjectiles.FIREBALL to listOf(1481, 0, 120, 46, 128, 340, 38),
            VorkathProjectiles.ACID to listOf(1483, 32, 90, 46, 128, 340, 0),
            VorkathProjectiles.RAPID_FIRE to listOf(1482, 0, 30, 22, 128, 138, 30),
            VorkathProjectiles.SPAWN to listOf(1484, 32, 120, 46, 128, 340, 0),
        )
        val landing = CoordGrid(2279, 4054)
        for ((spec, expected) in fixtures) {
            for (tile in listOf(landing, landing.translate(10, 10))) {
                val shot = spec.fromMouth(southMouth, tile, targetSlot = 1908)
                assertEquals(expected, listOf(shot.spotanim, shot.startTime, shot.endTime,
                    shot.angle, shot.progress, shot.startHeight, shot.endHeight))
                assertEquals(0, shot.targetIndex)
                assertEquals(0, shot.sourceIndex)
                assertEquals(tile, shot.endCoord)
                assertEquals(southMouth, shot.startCoord)
            }
        }
    }

    @Test
    fun nativeMouthCardinalWitnessesRotateAroundTheBossCentre() {
        assertEquals(CoordGrid(2272, 4063),
            VorkathProjectiles.mouth(boss, 7, CoordGrid(2271, 4061)))
        assertEquals(CoordGrid(2272, 4067),
            VorkathProjectiles.mouth(boss, 7, CoordGrid(2273, 4069)))
        assertEquals(CoordGrid(2274, 4065),
            VorkathProjectiles.mouth(boss, 7, CoordGrid(2278, 4064)))
        assertEquals(CoordGrid(2271, 4063),
            VorkathProjectiles.mouth(boss, 7, CoordGrid(2269, 4061)))
    }

    @Test
    fun acidAndSpawnFaceThePlayerInsteadOfTheirIndividualLandingTiles() {
        val player = CoordGrid(2271, 4061)
        val west = CoordGrid(2263, 4071)
        val east = CoordGrid(2282, 4069)
        for (spec in listOf(VorkathProjectiles.ACID, VorkathProjectiles.SPAWN)) {
            val first = spec.build(boss, 7, west, facingTile = player)
            val second = spec.build(boss, 7, east, facingTile = player)
            assertEquals(southMouth, first.startCoord)
            assertEquals(first.startCoord, second.startCoord)
            assertEquals(west, first.endCoord)
            assertEquals(east, second.endCoord)
            assertTrue(first.startCoord != boss)
        }
    }
}
