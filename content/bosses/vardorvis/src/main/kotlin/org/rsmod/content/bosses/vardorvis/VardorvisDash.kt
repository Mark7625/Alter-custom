package org.rsmod.content.bosses.vardorvis

import kotlin.math.PI
import kotlin.math.atan2

internal object VardorvisDash {
    data class Mark(val dx: Int, val dz: Int, val delayCc: Int)

    fun count(hitpoints: Int): Int =
        when {
            hitpoints > TWO_DART_HP -> 1
            hitpoints > THREE_DART_HP -> 2
            else -> 3
        }

    fun plan(dartCount: Int, rand: (bound: Int) -> Int): List<List<Mark>> {
        val flanks = FLANKS.toMutableList().also { shuffle(it, rand) }

        val dart1 = ArrayList<Pair<Int, Int>>(1 + DART1_WING)
        dart1 += 0 to 0
        repeat(DART1_WING.coerceAtMost(flanks.size)) { dart1 += flanks.removeAt(0) }

        val dart2 = ArrayList<Pair<Int, Int>>(DART2_TILES)
        repeat(DART2_TILES.coerceAtMost(flanks.size)) { dart2 += flanks.removeAt(0) }

        val darts = mutableListOf(dart1, dart2)
        if (dartCount >= 3) {
            val ext = EXT_POOL.toMutableList().also { shuffle(it, rand) }
            val dart3 = ArrayList<Pair<Int, Int>>(DART3_TILES)
            repeat(DART3_TILES.coerceAtMost(ext.size)) { dart3 += ext.removeAt(0) }
            darts += dart3
        }

        return darts.take(dartCount.coerceAtLeast(1)).map { tiles ->
            tiles.mapIndexed { i, (dx, dz) ->
                val delay = if (dx == 0 && dz == 0) CENTRE_CC else CC_CYCLE[i % CC_CYCLE.size]
                Mark(dx, dz, delay)
            }
        }
    }

    fun bearing(dx: Int, dz: Int): Int {
        if (dx == 0 && dz == 0) return 0
        val turns = (atan2(-dx.toDouble(), -dz.toDouble()) / (2 * PI) * ANGLE_STEPS).toInt()
        return ((turns % ANGLE_STEPS) + ANGLE_STEPS) % ANGLE_STEPS
    }

    private fun <T> shuffle(list: MutableList<T>, rand: (Int) -> Int) {
        for (i in list.size - 1 downTo 1) {
            val j = rand(i + 1)
            val tmp = list[i]
            list[i] = list[j]
            list[j] = tmp
        }
    }

    const val TWO_DART_HP: Int = 500
    const val THREE_DART_HP: Int = 130

    val FLANKS: List<Pair<Int, Int>> =
        buildList {
            for (dx in intArrayOf(-1, 1)) for (dz in intArrayOf(-2, -1, 1, 2)) add(dx to dz)
        }

    val EXT_POOL: List<Pair<Int, Int>> =
        listOf(-1 to 0, 1 to 0, -2 to -1, -2 to 1, 2 to -1, 2 to 1)

    private const val DART1_WING = 4
    private const val DART2_TILES = 4
    private const val DART3_TILES = 2

    private const val CENTRE_CC = 6
    private val CC_CYCLE = intArrayOf(3, 6, 9, 12)

    private const val ANGLE_STEPS = 2048
}
