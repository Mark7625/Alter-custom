package org.rsmod.content.other.consumables.food

import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.area.checker.isInWilderness
import org.rsmod.api.player.output.UpdateRun
import org.rsmod.api.player.hook.PlayerRestrictions
import org.rsmod.api.player.hook.RestrictedAction
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.baseHitpointsLvl
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeld2
import org.rsmod.api.script.onOpHeld3
import org.rsmod.api.script.onOpHeld4
import org.rsmod.api.table.FoodRow
import org.rsmod.content.other.consumables.ConsumableDelayState
import org.rsmod.content.other.consumables.ConsumableType
import org.rsmod.game.inv.Inventory
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class ConsumeFoodScript
@Inject
constructor(
    private val areaChecker: AreaChecker,
    private val restrictions: PlayerRestrictions,
    private val effects: FoodEffectService,
    private val specialEffects: FoodSpecialEffectService,
) : PluginScript() {
    override fun ScriptContext.startup() {
        val registrations =
            mutableListOf<FoodRegistration>()

        val registeredItems =
            mutableMapOf<Int, String>()

        val errors =
            mutableListOf<String>()

        FoodRow.all().forEach { food ->
            validateFood(
                food = food,
                registrations = registrations,
                registeredItems = registeredItems,
                errors = errors,
            )
        }

        require(errors.isEmpty()) {
            buildString {
                appendLine(
                    "Food table validation failed with " +
                        "${errors.size} problem(s):",
                )

                errors
                    .sorted()
                    .forEach { error ->
                        appendLine(" - $error")
                    }
            }
        }

        registrations.forEach { registration ->
            registerConsumeOption(registration)
        }
    }

    private fun validateFood(
        food: FoodRow,
        registrations: MutableList<FoodRegistration>,
        registeredItems: MutableMap<Int, String>,
        errors: MutableList<String>,
    ) {
        if (food.items.isEmpty()) {
            errors += "Food row contains no item stages."
            return
        }

        val rowName =
            food.items.first().internalName

        food.items.forEachIndexed { stage, item ->
            val option =
                item.consumeOption()

            if (option == -1) {
                if (!food.isTerminalContainer(stage)) {
                    errors +=
                        invalidConsumeOptionMessage(
                            rowName = rowName,
                            item = item,
                        )
                }
                return@forEachIndexed
            }

            val previous =
                registeredItems.putIfAbsent(
                    item.id,
                    rowName,
                )

            if (previous != null) {
                errors +=
                    "Food item '${item.internalName}' is " +
                        "registered by both '$previous' and '$rowName'."
                return@forEachIndexed
            }

            registrations +=
                FoodRegistration(
                    food = food,
                    item = item,
                    option = option,
                )
        }
    }

    private fun ScriptContext.registerConsumeOption(
        registration: FoodRegistration,
    ) {
        val food =
            registration.food

        val item =
            registration.item

        when (registration.option) {
            1 ->
                onOpHeld1(item) {
                    consume(
                        food = food,
                        slot = it.slot,
                        type = it.type,
                        inventory = it.inventory,
                    )
                }

            2 ->
                onOpHeld2(item) {
                    consume(
                        food = food,
                        slot = it.slot,
                        type = it.type,
                        inventory = it.inventory,
                    )
                }

            3 ->
                onOpHeld3(item) {
                    consume(
                        food = food,
                        slot = it.slot,
                        type = it.type,
                        inventory = it.inventory,
                    )
                }

            4 ->
                onOpHeld4(item) {
                    consume(
                        food = food,
                        slot = it.slot,
                        type = it.type,
                        inventory = it.inventory,
                    )
                }

            else ->
                error(
                    "Unsupported food consume option: " +
                        "${registration.option}.",
                )
        }
    }

    private fun ProtectedAccess.consume(
        food: FoodRow,
        slot: Int,
        type: ItemServerType,
        inventory: Inventory,
    ) {
        val restriction =
            restrictions.check(player, RestrictedAction.Food)
        if (restriction != null) {
            mes(restriction)
            return
        }

        val consumableType =
            food.consumableType()

        if (
            !ConsumableDelayState.canConsume(
                access = this,
                type = consumableType,
            )
        ) {
            return
        }

        val stage =
            food.items.indexOfFirst { item ->
                item.id == type.id
            }

        if (stage == -1) {
            return
        }

        val inWilderness =
            coords.isInWilderness(areaChecker)

        val blighted =
            type.name.startsWith(
                prefix = "Blighted",
                ignoreCase = true,
            )

        if (blighted && !inWilderness) {
            mes(
                "The ${type.name.lowercase()} can only be " +
                    "consumed in the Wilderness.",
            )
            return
        }

        val sweets =
            type.isType(PURPLE_SWEETS)

        val baseHeal =
            resolveBaseHealAmount(
                food = food,
                sweets = sweets,
                blighted = blighted,
            )

        val outcome =
            specialEffects.prepare(
                access = this,
                effect = food.effect,
                defaultHeal = baseHeal,
                defaultCanOverheal =
                    food.overheal &&
                        !isInPvpCombat(),
            )

        if (
            !replaceConsumedStage(
                food = food,
                stage = stage,
                slot = slot,
                type = type,
                inventory = inventory,
            )
        ) {
            return
        }

        val oldHitpoints =
            player.hitpoints

        anim(eatAnimation())
        soundSynth(EAT_FOOD_SOUND)

        heal(
            amount = outcome.healAmount,
            maximumHitpoints =
                outcome.maximumHitpoints,
        )

        applyConsumptionDelays(
            food = food,
            stage = stage,
            type = consumableType,
        )

        player.resetFaceEntity()

        sendConsumeMessage(
            type = type,
            sweets = sweets,
        )

        outcome.message?.let(::mes)

        if (
            outcome.showGenericHealMessage &&
            player.hitpoints > oldHitpoints
        ) {
            mes("It heals some health.")
        }

        applySecondaryEffect(
            food = food,
            outcome = outcome,
        )
    }

    private fun ProtectedAccess.replaceConsumedStage(
        food: FoodRow,
        stage: Int,
        slot: Int,
        type: ItemServerType,
        inventory: Inventory,
    ): Boolean {
        val nextStage =
            food.items.getOrNull(stage + 1)

        val transaction =
            if (nextStage == null) {
                invDel(
                    inv = inventory,
                    type = type.internalName,
                    count = 1,
                    slot = slot,
                )
            } else {
                invReplaceSlot(
                    inv = inventory,
                    slot = slot,
                    count = 1,
                    replacement = nextStage,
                )
            }

        return !transaction.failure
    }

    private fun ProtectedAccess.resolveBaseHealAmount(
        food: FoodRow,
        sweets: Boolean,
        blighted: Boolean,
    ): Int =
        when {
            food.heal >= 0 ->
                food.heal

            sweets ->
                random.of(
                    minInclusive = 1,
                    maxInclusive = 3,
                )

            blighted ->
                dynamicBlightedHeal(
                    baseHitpoints =
                        player.baseHitpointsLvl,
                )

            else ->
                dynamicHeal(
                    baseHitpoints =
                        player.baseHitpointsLvl,
                )
        }

    private fun ProtectedAccess.applyConsumptionDelays(
        food: FoodRow,
        stage: Int,
        type: ConsumableType,
    ) {
        ConsumableDelayState.recordConsumption(
            access = this,
            type = type,
            consumeDelay =
                food.eatDelay.getOrNull(stage)
                    ?: DEFAULT_EAT_DELAY,
            combatDelay =
                food.combatDelay.getOrNull(stage)
                    ?: DEFAULT_COMBAT_DELAY,
        )
    }

    private fun ProtectedAccess.sendConsumeMessage(
        type: ItemServerType,
        sweets: Boolean,
    ) {
        if (sweets) {
            restoreSweetsEnergy()

            mes(
                "You eat the sweets. " +
                    "The sugary goodness restores some energy.",
            )
            return
        }

        mes(
            "You ${type.consumeVerb()} " +
                "the ${type.name.lowercase()}.",
        )
    }

    private fun ProtectedAccess.applySecondaryEffect(
        food: FoodRow,
        outcome: FoodSpecialOutcome,
    ) {
        val effect =
            food.effect

        if (effect.isBlank()) {
            return
        }

        if (specialEffects.handles(effect)) {
            specialEffects.applyAfterConsume(
                access = this,
                effect = effect,
                outcome = outcome,
            )
        } else {
            effects.apply(
                access = this,
                effect = effect,
            )
        }
    }

    private fun ProtectedAccess.heal(
        amount: Int,
        maximumHitpoints: Int,
    ) {
        if (amount <= 0) {
            return
        }

        val actual =
            (
                maximumHitpoints -
                    player.hitpoints
                ).coerceIn(
                    minimumValue = 0,
                    maximumValue = amount,
                )

        if (actual <= 0) {
            return
        }

        statAdd(
            stat = HITPOINTS,
            constant = actual,
            percent = 0,
        )
    }

    private fun ProtectedAccess.restoreSweetsEnergy() {
        val restored =
            (player.runEnergy + SWEETS_RUN_ENERGY)
                .coerceAtMost(MAX_RUN_ENERGY)

        if (restored == player.runEnergy) {
            return
        }

        player.runEnergy =
            restored

        UpdateRun.energy(
            player = player,
            energy = restored,
        )
    }

    private fun ProtectedAccess.eatAnimation(): String =
        if (TOBOGGAN in worn) {
            TOBOGGAN_EAT_ANIM
        } else {
            DEFAULT_EAT_ANIM
        }

    private fun FoodRow.consumableType(): ConsumableType =
        if (combo) {
            ConsumableType.COMBO_FOOD
        } else {
            ConsumableType.FOOD
        }

    private fun FoodRow.isTerminalContainer(
        stage: Int,
    ): Boolean =
        items.size > 1 &&
            stage == items.lastIndex

    private fun ItemServerType.consumeOption(): Int {
        val index =
            interfaceOptions.indexOfFirst { option ->
                option.isConsumeOption()
            }

        return if (index in 0..3) {
            index + 1
        } else {
            -1
        }
    }

    private fun ItemServerType.consumeVerb(): String =
        interfaceOptions
            .firstOrNull { option ->
                option.isConsumeOption()
            }
            ?.lowercase()
            ?: "consume"

    private fun String?.isConsumeOption(): Boolean =
        equals(
            other = "eat",
            ignoreCase = true,
        ) ||
            equals(
                other = "drink",
                ignoreCase = true,
            )

    private fun invalidConsumeOptionMessage(
        rowName: String,
        item: ItemServerType,
    ): String {
        val options =
            item.interfaceOptions
                .mapIndexed { index, value ->
                    "${index + 1}='$value'"
                }
                .joinToString(
                    prefix = "[",
                    postfix = "]",
                )

        return "Food row '$rowName' contains non-consumable item " +
            "'${item.internalName}' (id=${item.id}, name='${item.name}', " +
            "options=$options)."
    }

    private fun dynamicHeal(
        baseHitpoints: Int,
    ): Int =
        baseHitpoints / 10 +
            2 * (baseHitpoints / 25) +
            5 * (baseHitpoints / 93) +
            2

    private fun dynamicBlightedHeal(
        baseHitpoints: Int,
    ): Int {
        val extra =
            when (baseHitpoints) {
                in 1..24 -> 2
                in 25..49 -> 4
                in 50..74 -> 6
                in 75..92 -> 8
                in 93..99 -> 13
                else -> 0
            }

        return baseHitpoints / 10 +
            extra
    }

    private data class FoodRegistration(
        val food: FoodRow,
        val item: ItemServerType,
        val option: Int,
    )

    private companion object {
        const val EAT_FOOD_SOUND: Int = 2393
        const val DEFAULT_EAT_DELAY: Int = 3
        const val DEFAULT_COMBAT_DELAY: Int = 3
        const val SWEETS_RUN_ENERGY: Int = 100
        const val MAX_RUN_ENERGY: Int = 1_000

        const val PURPLE_SWEETS: String =
            "obj.trail_sweets"

        const val TOBOGGAN: String =
            "obj.trollromance_toboggon"

        const val DEFAULT_EAT_ANIM: String =
            "seq.human_eat"

        const val TOBOGGAN_EAT_ANIM: String =
            "seq.trollromance_toboggan_eat"

        const val HITPOINTS: String =
            "stat.hitpoints"
    }
}
