package org.rsmod.api.bosses.spec

data class BossSpec(
    val npcTypes: List<String>,
    val stats: BossStats,
    val abilities: Map<String, Effect>,
    val phases: Map<String, PhaseSpec>,
    val triggers: List<TriggerSpec>,
)

data class BossStats(
    val attackRate: Int = 4,
    val aggressionRadius: Int = 8,
    val retaliateOnHit: Boolean = true,
    val hitFloor: Int? = null,
)

data class PhaseSpec(
    val name: String,
    val entryHp: Double? = null,
    val transmog: String? = null,
    val lockMovement: Boolean = false,
    /**
     * Per-phase attack-rate default (ticks between ability uses). When set, it overrides
     * [BossStats.attackRate] while this phase is active. A per-encounter
     * [org.rsmod.api.bosses.runtime.BossEncounter.attackRateOverride] takes precedence over both.
     */
    val attackRate: Int? = null,
    val exitAfter: Int? = null,
    val nextPhase: String? = null,
    val idleAnim: String? = null,
    val entry: String? = null,
    val exit: String? = null,
    val selector: Selector = Selector.WeightedRandom(),
    val forceAbilities: List<ForcedAbility> = emptyList(),
)

data class ForcedAbility(
    val period: Int,
    val ability: String,
    val attackMin: Int? = null,
    val attackMax: Int? = null,
)

data class TriggerSpec(val condition: Condition, val effect: Effect)

data class TelegraphSpec(val spotanim: String, val windup: Int)

data class ProjectileConfig(
    val startHeight: Int = 43,
    val endHeight: Int = 31,
    val startDelay: Int = 51,
    val travelTime: Int = 56,
    val angle: Int = 10,
    val progress: Int = 15,
    val stepMultiplier: Int = 5,
)
