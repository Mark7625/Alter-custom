package org.rsmod.content.bosses.vorkath

/** Encounter clock; all deadlines are absolute game ticks, never projectile helper durations. */
internal class VorkathTimeline(firstSpecial: VorkathSpecial) {
    var state = VorkathState.SLEEPING
    var nextSpecial = firstSpecial
    var standardAttacks = 0
        private set
    var nextAttackCycle = Int.MAX_VALUE
        private set
    var wakeCycle = Int.MAX_VALUE
        private set
    var specialStartCycle = Int.MAX_VALUE
        private set
    var shotsFired = 0
        private set

    fun wake(now: Int) {
        check(state == VorkathState.SLEEPING)
        state = VorkathState.AWAKENING
        wakeCycle = now + 7 // RSProx 3279: 5085 -> 5092.
    }

    fun activate(now: Int) {
        check(state == VorkathState.AWAKENING)
        state = VorkathState.ACTIVE
        nextAttackCycle = now + 1
    }

    fun attackDue(now: Int): Boolean =
        state == VorkathState.ACTIVE && now >= nextAttackCycle

    fun standardLaunched(now: Int) {
        check(attackDue(now) && standardAttacks < VORKATH_STANDARD_ATTACKS)
        standardAttacks++
        nextAttackCycle = now + VORKATH_ATTACK_RATE
    }

    fun beginSpecial(now: Int): VorkathSpecial {
        check(attackDue(now) && standardAttacks == VORKATH_STANDARD_ATTACKS)
        val selected = nextSpecial
        nextSpecial = VorkathRules.nextSpecial(selected)
        standardAttacks = 0
        specialStartCycle = now
        shotsFired = 0
        state = when (selected) {
            VorkathSpecial.ACID -> VorkathState.ACID_SPECIAL
            VorkathSpecial.ZOMBIFIED_SPAWN -> VorkathState.ZOMBIFIED_SPAWN_SPECIAL
        }
        return selected
    }

    fun rapidShotDue(now: Int): Boolean =
        state == VorkathState.ACID_SPECIAL &&
            shotsFired < VORKATH_ACID_SHOTS &&
            now >= specialStartCycle + 4 + shotsFired

    fun rapidLaunched(now: Int) {
        check(rapidShotDue(now))
        shotsFired++
    }

    fun finishSpecial(now: Int, recoveryTicks: Int) {
        check(state == VorkathState.ACID_SPECIAL || state == VorkathState.ZOMBIFIED_SPAWN_SPECIAL)
        state = VorkathState.ACTIVE
        nextAttackCycle = now + recoveryTicks
        specialStartCycle = Int.MAX_VALUE
    }

    fun reset(firstSpecial: VorkathSpecial) {
        state = VorkathState.SLEEPING
        nextSpecial = firstSpecial
        standardAttacks = 0
        shotsFired = 0
        nextAttackCycle = Int.MAX_VALUE
        wakeCycle = Int.MAX_VALUE
        specialStartCycle = Int.MAX_VALUE
    }

    /** Staff diagnostics still use the same transition and launch paths, with rewards disabled. */
    fun forceSpecial(now: Int, special: VorkathSpecial) {
        check(state == VorkathState.ACTIVE)
        nextSpecial = special
        standardAttacks = VORKATH_STANDARD_ATTACKS
        nextAttackCycle = now
    }

    fun forceAttack(now: Int) {
        check(state == VorkathState.ACTIVE)
        if (standardAttacks == VORKATH_STANDARD_ATTACKS) standardAttacks = 0
        nextAttackCycle = now
    }
}
