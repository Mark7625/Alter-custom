package org.rsmod.content.skills.hunter

/**
 * Which technique a creature is caught with, and how its trap behaves.
 *
 * **The order is persisted**: a laid trap stores this enum's `ordinal` in
 * `varcon.hunter_trap_family`, so a new family may only ever be *appended*.
 */
enum class TrapFamily {
    SNARE,
    BOX;

    /**
     * True for families laid from an inventory item onto an empty tile, false for ones armed in
     * place on a loc the map already supplied - which must never be deleted, only changed.
     */
    val portable: Boolean
        get() =
            when (this) {
                SNARE,
                BOX -> true
            }

    /** Sourced per family, and *not* the same split as [portable] (docs/hunter.md). */
    val suppressedByPlayerOnTile: Boolean
        get() =
            when (this) {
                SNARE,
                BOX -> true
            }

    /** Chebyshev tiles; only the box trap's radius is sourced (docs/hunter.md). */
    val triggerDistance: Int
        get() =
            when (this) {
                SNARE -> SNARE_TRIGGER_DISTANCE
                BOX -> BOX_TRAP_TRIGGER_DISTANCE
            }

    /** How often an armed trap rolls for a catch, in cycles. Only the box trap's is sourced. */
    val attemptCycles: Int
        get() =
            when (this) {
                SNARE -> SNARE_ATTEMPT_CYCLES
                BOX -> BOX_TRAP_ATTEMPT_CYCLES
            }
}

data class HunterCatch(val obj: String, val quantity: IntRange = 1..1)

/**
 * The size guard is the point: a ragged column edit must fail by name at boot, not as an
 * `IndexOutOfBounds` on the one tick that catches the one creature affected.
 */
internal fun parallelCatches(
    rowId: Int,
    objs: List<String>,
    min: List<Int>,
    max: List<Int>,
): List<HunterCatch> {
    require(min.size == objs.size && max.size == objs.size) {
        "Row $rowId has mismatched caught reward sizes: items=${objs.size}, " +
            "min=${min.size}, max=${max.size}"
    }
    return objs.mapIndexed { i, obj -> HunterCatch(obj, min[i]..max[i]) }
}

/**
 * A single laid-trap creature.
 *
 * [successLow] and [successHigh] are the engine-formula coefficients fit to (or published for) the
 * creature's charted per-level success curve - see docs/hunter.md for the fit, the 1/256-vs-/255
 * scale, and why a negative low means "always fails under-level" with no guard code.
 *
 * [locKey] is the suffix the trap's loc states are named by; authored data, never derived from
 * [npc] (docs/hunter.md).
 */
data class HunterCreature(
    val family: TrapFamily,
    val npc: String,
    val level: Int,
    val xp: Int,
    val caught: List<HunterCatch>,
    val successLow: Int,
    val successHigh: Int,
    val locKey: String? = null,
)
