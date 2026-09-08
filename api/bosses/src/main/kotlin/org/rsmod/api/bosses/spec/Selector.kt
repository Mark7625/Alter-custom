package org.rsmod.api.bosses.spec

sealed interface Selector {
    data class WeightedRandom(
        val entries: List<WeightedRef> = emptyList(),
        val noRepeatBias: Double = 0.5,
    ) : Selector {
        constructor(vararg entries: WeightedRef, noRepeatBias: Double = 0.5) :
            this(entries.toList(), noRepeatBias)
    }

    data class Rotation(val sequence: List<String>) : Selector

    data class Conditional(
        val branches: List<Pair<Condition, String>>,
        val fallback: String,
    ) : Selector
}

data class WeightedRef(
    val ability: String,
    val weight: Int = 1,
    val cooldown: Int = 0,
    val requires: Condition = Condition.Always,
)

public data class AbilityRef(public val name: String)

public data class PhaseRef(public val name: String)

infix fun String.weight(w: Int): WeightedRef = WeightedRef(this, weight = w)

infix fun AbilityRef.weight(w: Int): WeightedRef = WeightedRef(name, weight = w)
infix fun WeightedRef.cooldown(t: Int): WeightedRef = copy(cooldown = t)
infix fun WeightedRef.requires(c: Condition): WeightedRef = copy(requires = c)
