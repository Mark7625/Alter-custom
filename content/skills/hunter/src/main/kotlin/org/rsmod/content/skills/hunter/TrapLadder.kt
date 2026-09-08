package org.rsmod.content.skills.hunter

/**
 * Live-trap cap per Hunter level, from the wiki's *Pitfall* "Multiple traps" table
 * (oldid=15201220), read from the effective level. Crab trapping keeps its own cap - its published
 * table has no below-20 rung. See docs/hunter.md.
 */
internal object TrapLadder {
    fun cap(level: Int): Int =
        when {
            level >= 80 -> 5
            level >= 60 -> 4
            level >= 40 -> 3
            level >= 20 -> 2
            else -> 1
        }
}
