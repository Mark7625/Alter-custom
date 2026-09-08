package org.rsmod.content.skills.hunter

import kotlin.math.abs

object HunterTrapStates {
    // Authored data, never derived from the npc symbol (docs/hunter.md).
    private fun locKey(creature: HunterCreature): String =
        requireNotNull(creature.locKey) {
            "Creature is missing its loc key: ${creature.npc}"
        }

    fun setLoc(family: TrapFamily): String? =
        when (family) {
            TrapFamily.SNARE -> "loc.hunting_ojibway_trap"
            TrapFamily.BOX -> "loc.hunting_boxtrap_empty"
        }

    /** The mid-catch state, given where the creature stands relative to the trap ([dx], [dz]). */
    fun trappingLoc(creature: HunterCreature, dx: Int, dz: Int): String =
        when (creature.family) {
            TrapFamily.SNARE -> "loc.hunting_ojibway_trap_trapping_${locKey(creature)}"
            TrapFamily.BOX -> "loc.hunting_boxtrap_trapping_${locKey(creature)}_${compass(dx, dz)}"
        }

    fun fullLoc(creature: HunterCreature): String =
        when (creature.family) {
            TrapFamily.SNARE -> "loc.hunting_ojibway_trap_full_${locKey(creature)}"
            TrapFamily.BOX -> "loc.hunting_boxtrap_full_${locKey(creature)}"
        }

    fun failingLoc(family: TrapFamily, creature: HunterCreature? = null): String =
        when (family) {
            TrapFamily.SNARE -> "loc.hunting_ojibway_trap_failing"
            TrapFamily.BOX -> "loc.hunting_boxtrap_failing"
        }

    fun failedLoc(family: TrapFamily, creature: HunterCreature? = null): String =
        when (family) {
            TrapFamily.SNARE -> "loc.hunting_ojibway_trap_broken"
            TrapFamily.BOX -> "loc.hunting_boxtrap_failed"
        }

    // Ties and a same-tile creature fall to `n`; whether live picks this way is unverified.
    private fun compass(dx: Int, dz: Int): Char =
        when {
            abs(dz) >= abs(dx) && dz >= 0 -> 'n'
            abs(dz) >= abs(dx) -> 's'
            dx >= 0 -> 'e'
            else -> 'w'
        }
}
