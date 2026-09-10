package org.rsmod.content.bosses.vardorvis

import kotlin.math.sign

internal object VardorvisAxes {
    const val RING: Int = 5

    const val SPAWN_INSET: Int = 2

    val TRAVEL_STEPS: IntArray = intArrayOf(2, 2, 2, 1)

    val ANCHORS: List<Anchor> =
        buildList {
            for (dx in intArrayOf(-RING, 0, RING)) {
                for (dz in intArrayOf(-RING, 0, RING)) {
                    if (dx != 0 || dz != 0) add(Anchor(dx, dz))
                }
            }
        }

    data class Anchor(val dx: Int, val dz: Int) {
        val hx: Int = -dx.sign
        val hz: Int = -dz.sign

        val spawnDx: Int = dx + hx * SPAWN_INSET
        val spawnDz: Int = dz + hz * SPAWN_INSET

        val bearing: Int = bearingOf(hx, hz)
    }

    fun count(hitpoints: Int, awakened: Boolean, rollExtra: () -> Boolean): Int =
        if (awakened) {
            when {
                hitpoints > 1380 -> 2
                hitpoints >= 462 -> if (rollExtra()) 4 else 3
                else -> 4
            }
        } else {
            when {
                hitpoints > 690 -> 1
                hitpoints >= 231 -> if (rollExtra()) 3 else 2
                else -> 3
            }
        }

    private fun bearingOf(hx: Int, hz: Int): Int =
        when {
            hx == 0 && hz == -1 -> 0
            hx == -1 && hz == -1 -> 256
            hx == -1 && hz == 0 -> 512
            hx == -1 && hz == 1 -> 768
            hx == 0 && hz == 1 -> 1024
            hx == 1 && hz == 1 -> 1280
            hx == 1 && hz == 0 -> 1536
            else -> 1792
        }
}
