package org.rsmod.content.bosses.vardorvis

internal object VardorvisScaling {
    const val MIN_DEFENCE: Int = 0

    const val MAX_STRENGTH: Int = 400

    fun defence(hpFraction: Double, baseDefence: Int): Int =
        lerp(from = MIN_DEFENCE, to = baseDefence, fraction = hpFraction.coerceIn(0.0, 1.0))

    fun strength(hpFraction: Double, baseStrength: Int): Int =
        lerp(from = MAX_STRENGTH, to = baseStrength, fraction = hpFraction.coerceIn(0.0, 1.0))

    private fun lerp(from: Int, to: Int, fraction: Double): Int =
        (from + (to - from) * fraction).toInt()
}
