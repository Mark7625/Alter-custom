package org.rsmod.content.skills.hunter

// Every tuned number the trap families run on. Full sourcing per constant: docs/hunter.md.

/** The controller type every laid trap is anchored to, whichever family it belongs to. */
const val TRAP_CONTROLLER: String = "controller.hunter_trap"

/**
 * How long an untouched trap has before it collapses. Seeded from RuneLite's `TRAP_TIME` overlay
 * figure (~1 minute); not server truth.
 */
const val TRAP_LIFETIME_CYCLES: Int = 100

/**
 * How long the `_trapping_` / `_failing_` loc is shown before it settles into `_full_` /
 * `_failed_`. Live's real duration is not answerable offline; a fixed short step is the model.
 */
const val TRAP_SPRING_CYCLES: Int = 2

/**
 * How long a collapsed trap is left on the ground after its controller is gone. Finite so the loc
 * cleans itself up if the owner never comes back.
 */
const val TRAP_COLLAPSE_LINGER_CYCLES: Int = 100

/** "within a 2-tile radius of the box trap" (wiki, *Box trap > Mechanics*). */
const val BOX_TRAP_TRIGGER_DISTANCE: Int = 2

/**
 * Unsourced: no page or cache record states a snare radius. Adjacency is the conservative
 * reading - do not promote it to the box trap's 2 without a source (docs/hunter.md).
 */
const val SNARE_TRIGGER_DISTANCE: Int = 1

/** "an attempt every 3 ticks (1.8 seconds)" (wiki, *Box trap > Mechanics*). */
const val BOX_TRAP_ATTEMPT_CYCLES: Int = 3

/** Unsourced, like [SNARE_TRIGGER_DISTANCE]: the wiki gives a cadence for the box trap only. */
const val SNARE_ATTEMPT_CYCLES: Int = 1

/** The most traps any player can have laid, reached at level 80. */
const val MAX_LAID_TRAPS: Int = 5
