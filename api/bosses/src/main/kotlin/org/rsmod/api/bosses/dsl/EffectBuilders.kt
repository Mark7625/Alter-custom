package org.rsmod.api.bosses.dsl

import dev.openrune.types.NpcMode
import org.rsmod.api.bosses.spec.*
import org.rsmod.api.combat.commons.types.MeleeAttackType as EngineMeleeAttackType

fun anim(seq: String, delay: Int = 0): Effect = Effect.Anim(seq, delay)

/** Plays [spot] on the caster (the boss npc itself), not on the target. */
fun spotanim(spot: String, height: Int = 0, delay: Int = 0): Effect =
    Effect.Spotanim(spot, height, delay)
fun say(text: String): Effect = Effect.Say(text)
fun sound(synth: String, radius: Int = 10): Effect = Effect.Sound(synth, radius)
fun delay(ticks: Int): Effect = Effect.Delay(ticks)
fun sequence(vararg e: Effect): Effect = Effect.Sequence(e.toList())
fun parallel(vararg e: Effect): Effect = Effect.Parallel(e.toList())
fun repeat(times: Int, gap: Int = 0, effect: Effect): Effect = Effect.Repeat(times, effect, gap)
fun whenever(condition: Condition, then: Effect, otherwise: Effect = Effect.NoOp): Effect =
    Effect.Whenever(condition, then, otherwise)
fun onEach(targets: TargetExpr, effect: Effect): Effect = Effect.OnEach(targets, effect)
fun run(ability: String): Effect = Effect.Run(ability)

fun run(ability: AbilityRef): Effect = Effect.Run(ability.name)

fun transitionTo(phase: String): Effect = Effect.TransitionTo(phase)

fun transitionTo(phase: PhaseRef): Effect = Effect.TransitionTo(phase.name)
fun external(handler: String, params: Any? = null): Effect = Effect.External(handler, params)

fun hit(
    damage: DamageExpr,
    type: HitType,
    target: TargetExpr = TargetExpr.CurrentTarget,
    delay: Int = 0,
): Effect.Hit = Effect.Hit(target, damage, type, delay)

fun hit(block: HitBuilder.() -> Unit): Effect.Hit = HitBuilder().apply(block).build()

/** Inclusive rolled damage; use in `hit { damage((0..25).roll()); … }` or with [HitBuilder.damage]. */
fun IntRange.roll(): DamageExpr.Roll = DamageExpr.Roll(this)

/** Alias of [roll] for boss hit specs. */
fun IntRange.randomRoll(): DamageExpr.Roll = DamageExpr.Roll(this)

fun npcMaxHit(
    meleeAttackType: MeleeAttackType? = null,
    scale: Double = 1.0,
    minHit: Int = 0,
): DamageExpr.NpcMaxHit = DamageExpr.NpcMaxHit(meleeAttackType, scale, minHit)

fun projectile(
    spotanim: String,
    travel: String? = null,
    config: ProjectileConfig? = null,
    target: TargetExpr = TargetExpr.CurrentTarget,
    launch: String? = null,
    impact: String? = null,
    hit: Effect.Hit? = null,
): Effect = Effect.Projectile(spotanim, travel, config, target, launch, impact, hit)

fun tileAoE(
    center: TargetExpr,
    radius: Int,
    telegraph: TelegraphSpec? = null,
    damage: DamageExpr,
    type: HitType,
): Effect = Effect.TileAoE(center, radius, telegraph, damage, type)

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
): Effect =
    Effect.Debris(telegraph, damage, type, impact, windup, targetRadius, scatterRadius, count, center)

fun summon(
    npc: String,
    count: Int = 1,
    radius: Int = 3,
    centeredOn: TargetExpr = TargetExpr.Self,
    mode: NpcMode? = null,
): Effect = Effect.Summon(npc, count, radius, centeredOn, mode)

fun transmog(to: String, durationTicks: Int): Effect = Effect.Transmog(to, durationTicks)
fun poison(damage: Int, chance: Int = 1, outOf: Int = 1): Effect = Effect.Poison(damage, chance, outOf)

fun poison(damage: Int, odds: Odds): Effect = Effect.Poison(damage, odds.chance, odds.outOf)

fun freeze(ticks: Int, chance: Int = 1, outOf: Int = 1): Effect = Effect.Freeze(ticks, chance, outOf)

fun freeze(ticks: Int, odds: Odds): Effect = Effect.Freeze(ticks, odds.chance, odds.outOf)
fun statDrain(block: StatDrainBuilder.() -> Unit): Effect = StatDrainBuilder().apply(block).build()

fun statDrain(vararg stats: String, amount: Int, chance: Int = 1, outOf: Int = 1): Effect =
    Effect.StatDrain(stats.map { StatDrainEntry(it, amount, chance, outOf) })

fun statDrain(stats: List<String>, amount: Int, chance: Int = 1, outOf: Int = 1): Effect =
    Effect.StatDrain(stats.map { StatDrainEntry(it, amount, chance, outOf) })

fun statDrain(stats: List<String>, amount: Int, odds: Odds): Effect =
    Effect.StatDrain(stats.map { StatDrainEntry(it, amount, odds.chance, odds.outOf) })

fun statDrain(vararg stats: String, amount: Int, odds: Odds): Effect =
    Effect.StatDrain(stats.map { StatDrainEntry(it, amount, odds.chance, odds.outOf) })

fun telegraph(spotanim: String, windup: Int): TelegraphSpec = TelegraphSpec(spotanim, windup)

fun weightedRandom(
    noRepeatBias: Double = 0.5,
    block: WeightedRandomBuilder.() -> Unit,
): Selector.WeightedRandom =
    WeightedRandomBuilder().apply {
        this.noRepeatBias = noRepeatBias
        block()
    }.build()

@BossDsl
class WeightedRandomBuilder internal constructor() {
    var noRepeatBias: Double = 0.5
    private val entries = mutableListOf<WeightedRef>()

    @BossDsl
    inner class RandomPending(
        private val abilityName: String,
        private val weight: Int,
        private val requires: Condition,
        private val cooldown: Int,
    ) {
        operator fun unaryPlus() {
            this@WeightedRandomBuilder.entries +=
                WeightedRef(ability = abilityName, weight = weight, cooldown = cooldown, requires = requires)
        }
    }

    fun random(
        ability: AbilityRef,
        weight: Int,
        requires: Condition = Condition.Always,
        cooldown: Int = 0,
    ): RandomPending = RandomPending(ability.name, weight, requires, cooldown)

    fun random(
        ability: String,
        weight: Int,
        requires: Condition = Condition.Always,
        cooldown: Int = 0,
    ): RandomPending = RandomPending(ability, weight, requires, cooldown)

    internal fun build(): Selector.WeightedRandom = Selector.WeightedRandom(entries, noRepeatBias)
}

fun rotation(block: RotationBuilder.() -> Unit): Selector.Rotation = RotationBuilder().apply(block).build()

@BossDsl
class RotationBuilder internal constructor() {
    private val sequence = mutableListOf<String>()

    @BossDsl
    inner class ThenPending(private val abilityName: String) {
        operator fun unaryPlus() {
            this@RotationBuilder.sequence += abilityName
        }
    }

    fun then(ability: AbilityRef): ThenPending = ThenPending(ability.name)

    fun then(ability: String): ThenPending = ThenPending(ability)

    internal fun build(): Selector.Rotation = Selector.Rotation(sequence)
}

// Re-exports for clean spec authoring
typealias Roll = DamageExpr.Roll
typealias Accuracy = DamageExpr.Accuracy
typealias Fixed = DamageExpr.Fixed
typealias NpcMaxHit = DamageExpr.NpcMaxHit
typealias HpBelow = Condition.HpBelow
typealias IncomingHitDamageAtLeast = Condition.IncomingHitDamageAtLeast
typealias PlayerEnterRange = Condition.PlayerEnterRange
typealias EveryNTicks = Condition.EveryNTicks
typealias TargetPraying = Condition.TargetPraying
typealias InPhase = Condition.InPhase
typealias WeightedRandom = Selector.WeightedRandom
typealias AbilityRef = org.rsmod.api.bosses.spec.AbilityRef
typealias PhaseRef = org.rsmod.api.bosses.spec.PhaseRef
typealias Rotation = Selector.Rotation
typealias Run = Effect.Run
typealias TransitionTo = Effect.TransitionTo
typealias AllInRadius = TargetExpr.AllInRadius
typealias MeleeAttackType = EngineMeleeAttackType

val OnDeath: Condition = Condition.OnDeath
val OnSpawn: Condition = Condition.OnSpawn
val Always: Condition = Condition.Always
val WithinMeleeRange: Condition = Condition.WithinMeleeRange
val CurrentTarget: TargetExpr = TargetExpr.CurrentTarget
val CurrentTargetTile: TargetExpr = TargetExpr.CurrentTargetTile
val Self: TargetExpr = TargetExpr.Self
val Melee: HitType = HitType.Melee
val Ranged: HitType = HitType.Ranged
val Magic: HitType = HitType.Magic
val Dragonfire: HitType = HitType.Dragonfire
val DragonfireMetal: HitType = HitType.DragonfireMetal
val WyvernIce: HitType = HitType.WyvernIce
val Typeless: HitType = HitType.Typeless
