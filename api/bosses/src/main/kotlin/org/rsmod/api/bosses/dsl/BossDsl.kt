package org.rsmod.api.bosses.dsl

import dev.openrune.types.NpcMode
import org.rsmod.api.bosses.spec.*
import org.rsmod.api.bosses.validation.SpecValidator

@DslMarker annotation class BossDsl

fun boss(vararg npcTypes: String, block: BossSpecBuilder.() -> Unit): BossSpec =
    BossSpecBuilder(npcTypes.toList()).apply(block).build()

@BossDsl
class BossSpecBuilder(private val npcTypes: List<String>) {
    init {
        require(npcTypes.isNotEmpty()) { "A boss spec must declare at least one npc type." }
    }

    private var stats = BossStats()
    private val abilities = mutableMapOf<String, Effect>()
    private val phases = mutableMapOf<String, PhaseSpec>()
    private val triggers = mutableListOf<TriggerSpec>()

    fun stats(
        attackRate: Int = 4,
        aggressionRadius: Int = 8,
        retaliateOnHit: Boolean = true,
        hitFloor: Int? = null,
    ) {
        stats = BossStats(attackRate, aggressionRadius, retaliateOnHit, hitFloor)
    }

    fun ability(name: String, block: AbilityBuilder.() -> Unit): AbilityRef {
        abilities[name] = AbilityBuilder().apply(block).build()
        return AbilityRef(name)
    }

    fun ability(name: String, effect: Effect): AbilityRef {
        abilities[name] = effect
        return AbilityRef(name)
    }

    fun phase(
        name: String,
        entryHp: Double? = null,
        transmog: String? = null,
        lockMovement: Boolean = false,
        exitAfter: Int? = null,
        nextPhase: String? = null,
        idleAnim: String? = null,
        attackRate: Int? = null,
        block: PhaseBuilder.() -> Unit,
    ): PhaseRef {
        val builder = PhaseBuilder(name).apply(block)
        phases[name] =
            PhaseSpec(
                name = name,
                entryHp = entryHp,
                transmog = transmog,
                lockMovement = lockMovement,
                exitAfter = exitAfter,
                nextPhase = nextPhase,
                idleAnim = idleAnim,
                attackRate = attackRate ?: builder.attackRate,
                entry = builder.entry,
                exit = builder.exit,
                selector = builder.selector,
                forceAbilities = builder.forceAbilities,
            )
        return PhaseRef(name)
    }

    fun triggers(block: TriggerBuilder.() -> Unit) {
        TriggerBuilder(triggers).apply(block)
    }

    fun build(): BossSpec {
        val spec = BossSpec(npcTypes, stats, abilities, phases, triggers)
        val errors = SpecValidator.validate(spec)
        if (errors.isNotEmpty()) {
            throw IllegalStateException(
                "Boss spec invalid for '${npcTypes.joinToString()}':\n" +
                    errors.joinToString("\n") { " - ${it.message}" }
            )
        }
        return spec
    }
}

/**
 * Call-style hit DSL: `hit { damage(0..25).roll(); type(Melee) }` (also on [ProjectileBuilder]).
 */
@BossDsl
class HitBuilder internal constructor() {
    private var damageExpr: DamageExpr? = null
    private var hitType: HitType? = null
    var target: TargetExpr = TargetExpr.CurrentTarget
    var delay: Int = 0
    private var spotanimSpot: String? = null
    private var spotanimHeight: Int = 0

    fun damage(expr: DamageExpr) {
        damageExpr = expr
    }

    /** Inclusive roll range; finish with [DamageRangeStep.roll] or [DamageRangeStep.random]. */
    fun damage(range: IntRange): DamageRangeStep = DamageRangeStep(this, range)

    fun type(t: HitType) {
        hitType = t
    }

    /** Plays [spot] on the resolved target(s) when the hit lands, e.g. a magic impact graphic. */
    fun spotanim(spot: String, height: Int = 0) {
        spotanimSpot = spot
        spotanimHeight = height
    }

    internal fun commitDamage(expr: DamageExpr) {
        damageExpr = expr
    }

    internal fun build(): Effect.Hit =
        Effect.Hit(
            target = target,
            damage =
                requireNotNull(damageExpr) {
                    "hit { } requires damage(…), e.g. damage(0..25).roll() or damage((0..25).roll())"
                },
            type = requireNotNull(hitType) { "hit { } requires type(…)" },
            delay = delay,
            spotanim = spotanimSpot,
            spotanimHeight = spotanimHeight,
        )
}

@BossDsl
class DamageRangeStep
internal constructor(private val builder: HitBuilder, private val range: IntRange) {
    fun roll() {
        builder.commitDamage(DamageExpr.Roll(range))
    }

    /** Same as [roll] (random value in [range] when the hit resolves). */
    fun random() {
        builder.commitDamage(DamageExpr.Roll(range))
    }
}

@BossDsl
class AbilityBuilder {
    private val effects = mutableListOf<Effect>()

    fun anim(seq: String, delay: Int = 0) {
        effects += Effect.Anim(seq, delay)
    }

    /** Plays [spot] on the caster (the boss npc itself), not on the target. */
    fun spotanim(spot: String, height: Int = 0, delay: Int = 0) {
        effects += Effect.Spotanim(spot, height, delay)
    }

    fun say(text: String) {
        effects += Effect.Say(text)
    }

    fun sound(synth: String, radius: Int = 10) {
        effects += Effect.Sound(synth, radius)
    }

    fun delay(ticks: Int) {
        effects += Effect.Delay(ticks)
    }

    fun broadcastInArea(text: String, radius: Int = 15) {
        effects += Effect.Broadcast(text, radius)
    }

    fun run(ability: String) {
        effects += Effect.Run(ability)
    }

    fun run(ability: AbilityRef) {
        effects += Effect.Run(ability.name)
    }

    fun transitionTo(phase: String) {
        effects += Effect.TransitionTo(phase)
    }

    fun transitionTo(phase: PhaseRef) {
        effects += Effect.TransitionTo(phase.name)
    }

    fun poison(damage: Int, chance: Int = 1, outOf: Int = 1) {
        effects += Effect.Poison(damage, chance, outOf)
    }

    fun poison(damage: Int, odds: Odds) {
        effects += Effect.Poison(damage, odds.chance, odds.outOf)
    }

    fun freeze(ticks: Int, chance: Int = 1, outOf: Int = 1) {
        effects += Effect.Freeze(ticks, chance, outOf)
    }

    fun freeze(ticks: Int, odds: Odds) {
        effects += Effect.Freeze(ticks, odds.chance, odds.outOf)
    }

    fun statDrain(block: StatDrainBuilder.() -> Unit) {
        effects += StatDrainBuilder().apply(block).build()
    }

    fun statDrain(vararg stats: String, amount: Int, chance: Int = 1, outOf: Int = 1) {
        effects += Effect.StatDrain(stats.map { StatDrainEntry(it, amount, chance, outOf) })
    }

    fun statDrain(stats: List<String>, amount: Int, chance: Int = 1, outOf: Int = 1) {
        effects += Effect.StatDrain(stats.map { StatDrainEntry(it, amount, chance, outOf) })
    }

    fun statDrain(vararg stats: String, amount: Int, odds: Odds) {
        effects +=
            Effect.StatDrain(stats.map { StatDrainEntry(it, amount, odds.chance, odds.outOf) })
    }

    fun statDrain(stats: List<String>, amount: Int, odds: Odds) {
        effects +=
            Effect.StatDrain(stats.map { StatDrainEntry(it, amount, odds.chance, odds.outOf) })
    }

    fun include(effect: Effect) {
        effects += effect
    }

    fun hit(
        damage: DamageExpr,
        type: HitType,
        target: TargetExpr = TargetExpr.CurrentTarget,
        delay: Int = 0,
    ) {
        effects += Effect.Hit(target, damage, type, delay)
    }

    fun hit(block: HitBuilder.() -> Unit) {
        effects += HitBuilder().apply(block).build()
    }

    fun projectile(
        spotanim: String,
        travel: String? = null,
        config: ProjectileConfig? = null,
        target: TargetExpr = TargetExpr.CurrentTarget,
        launch: String? = null,
        impact: String? = null,
        hit: Effect.Hit? = null,
    ) {
        effects += Effect.Projectile(spotanim, travel, config, target, launch, impact, hit)
    }

    /**
     * Block form of [projectile]; set [ProjectileBuilder.spotanim] and optional fields, then
     * [ProjectileBuilder.hit].
     */
    fun projectile(block: ProjectileBuilder.() -> Unit) {
        val built = ProjectileBuilder().apply(block).build()
        effects += built
    }

    @BossDsl
    class ProjectileBuilder internal constructor() {
        var spotanim: String? = null
        var travel: String? = null
        var config: ProjectileConfig? = null
        var target: TargetExpr = TargetExpr.CurrentTarget
        var launch: String? = null
        var impact: String? = null
        private var hitPayload: Effect.Hit? = null

        fun hit(
            damage: DamageExpr,
            type: HitType,
            hitTarget: TargetExpr = TargetExpr.CurrentTarget,
            delay: Int = 0,
        ) {
            hitPayload = Effect.Hit(hitTarget, damage, type, delay)
        }

        fun hit(block: HitBuilder.() -> Unit) {
            hitPayload = HitBuilder().apply(block).build()
        }

        internal fun build(): Effect.Projectile =
            Effect.Projectile(
                spotanim = requireNotNull(spotanim) { "projectile { } requires spotanim = \"…\"" },
                travel = travel,
                config = config,
                target = target,
                launch = launch,
                impact = impact,
                hit = hitPayload,
            )
    }

    fun tileAoE(
        center: TargetExpr,
        radius: Int,
        telegraph: TelegraphSpec? = null,
        damage: DamageExpr,
        type: HitType,
    ) {
        effects += Effect.TileAoE(center, radius, telegraph, damage, type)
    }

    fun summon(
        npc: String,
        count: Int = 1,
        radius: Int = 3,
        centeredOn: TargetExpr = TargetExpr.Self,
        mode: NpcMode? = null,
    ) {
        effects += Effect.Summon(npc, count, radius, centeredOn, mode)
    }

    /**
     * Telegraphed, dodgeable area attack (falling rocks/debris). See [Effect.Debris]. A [telegraph]
     * spotanim marks each tile, then after [windup] ticks players still standing on a marked tile take
     * [damage]. Tiles target every player within [targetRadius] of the boss plus random scatter within
     * [scatterRadius], totaling a value drawn from [count].
     */
    fun debris(
        telegraph: String,
        damage: DamageExpr,
        type: HitType = HitType.Typeless,
        impact: String? = null,
        windup: Int = 3,
        targetRadius: Int = 15,
        scatterRadius: Int = 5,
        count: IntRange = 1..1,
        center: TargetExpr = TargetExpr.Self,
    ) {
        effects +=
            Effect.Debris(
                telegraph,
                damage,
                type,
                impact,
                windup,
                targetRadius,
                scatterRadius,
                count,
                center,
            )
    }

    fun build(): Effect = if (effects.size == 1) effects.first() else Effect.Sequence(effects)
}

@BossDsl
class PhaseBuilder(private val name: String) {
    var entry: String? = null
    var exit: String? = null

    var attackRate: Int? = null
    var selector: Selector = Selector.WeightedRandom()
    internal val forceAbilities = mutableListOf<ForcedAbility>()

    fun forceEvery(period: Int, ability: String) {
        forceAbilities += ForcedAbility(period, ability)
    }

    fun forceEvery(period: Int, ability: AbilityRef) {
        forceAbilities += ForcedAbility(period, ability.name)
    }

    fun forceEveryAttacks(min: Int, max: Int, ability: String) {
        forceAbilities += ForcedAbility(period = 0, ability = ability, attackMin = min, attackMax = max)
    }

    fun forceEveryAttacks(min: Int, max: Int, ability: AbilityRef) {
        forceAbilities += ForcedAbility(period = 0, ability = ability.name, attackMin = min, attackMax = max)
    }

    fun weightedSelectorRandom(
        noRepeatBias: Double = 0.5,
        block: WeightedRandomBuilder.() -> Unit,
    ) {
        selector = weightedRandom(noRepeatBias, block)
    }

    fun rotationSelector(block: RotationBuilder.() -> Unit) {
        selector = rotation(block)
    }
}

@BossDsl
class TriggerBuilder(private val triggers: MutableList<TriggerSpec>) {
    fun on(c: Condition): Condition = c

    infix fun Condition.runs(effect: Effect) {
        triggers += TriggerSpec(this, effect)
    }
}
