package org.rsmod.content.other.consumables.potion

import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.area.checker.isInWilderness
import org.rsmod.api.player.hook.PlayerRestrictions
import org.rsmod.api.player.hook.RestrictedAction
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeld2
import org.rsmod.api.script.onOpHeld3
import org.rsmod.api.script.onOpHeld4
import org.rsmod.api.table.PotionRow
import org.rsmod.content.other.consumables.ConsumableActivityAccess
import org.rsmod.content.other.consumables.ConsumableDelayState
import org.rsmod.content.other.consumables.ConsumableType
import org.rsmod.game.inv.Inventory
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class ConsumePotionScript
@Inject
constructor(
    private val areaChecker: AreaChecker,
    private val restrictions: PlayerRestrictions,
    private val effects: PotionEffectService,
    private val activityAccess: ConsumableActivityAccess,
) : PluginScript() {
    override fun ScriptContext.startup() {
        val registrations =
            mutableListOf<PotionRegistration>()

        val registeredItems =
            mutableMapOf<Int, String>()

        val errors =
            mutableListOf<String>()

        PotionRow.all().forEach { potion ->
            if (!effects.supports(potion.effect)) {
                errors +=
                    "Potion row '${potion.name}' uses unsupported " +
                        "effect handler '${potion.effect.handler}'."
            }

            potion.items.forEach { item ->
                val option =
                    item.consumeOption()

                if (option == -1) {
                    errors +=
                        "Potion row '${potion.name}' contains " +
                            "non-consumable item '${item.internalName}' " +
                            "(id=${item.id}, name='${item.name}', " +
                            "options=${item.optionSummary()})."
                    return@forEach
                }

                val previous =
                    registeredItems.putIfAbsent(
                        item.id,
                        potion.name,
                    )

                if (previous != null) {
                    errors +=
                        "Potion item '${item.internalName}' is registered " +
                            "by both '$previous' and '${potion.name}'."
                    return@forEach
                }

                registrations +=
                    PotionRegistration(
                        potion = potion,
                        item = item,
                        option = option,
                    )
            }
        }

        require(errors.isEmpty()) {
            buildString {
                appendLine(
                    "Potion table validation failed with " +
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
            registerConsumeOption(
                potion = registration.potion,
                item = registration.item,
                option = registration.option,
            )
        }
    }

    private fun ScriptContext.registerConsumeOption(
        potion: PotionRow,
        item: ItemServerType,
        option: Int,
    ) {
        when (option) {
            1 ->
                onOpHeld1(item) {
                    drink(
                        potion = potion,
                        slot = it.slot,
                        type = it.type,
                        inventory = it.inventory,
                    )
                }

            2 ->
                onOpHeld2(item) {
                    drink(
                        potion = potion,
                        slot = it.slot,
                        type = it.type,
                        inventory = it.inventory,
                    )
                }

            3 ->
                onOpHeld3(item) {
                    drink(
                        potion = potion,
                        slot = it.slot,
                        type = it.type,
                        inventory = it.inventory,
                    )
                }

            4 ->
                onOpHeld4(item) {
                    drink(
                        potion = potion,
                        slot = it.slot,
                        type = it.type,
                        inventory = it.inventory,
                    )
                }

            else ->
                error(
                    "Unsupported potion consume option: $option.",
                )
        }
    }

    private fun ProtectedAccess.drink(
        potion: PotionRow,
        slot: Int,
        type: ItemServerType,
        inventory: Inventory,
    ) {
        if (
            !ConsumableDelayState.canConsume(
                access = this,
                type = ConsumableType.POTION,
            )
        ) {
            return
        }

        val restriction =
            restrictions.check(player, RestrictedAction.Drink)
        if (restriction != null) {
            mes(restriction)
            return
        }

        val doseIndex =
            potion.items.indexOfFirst { item ->
                item.id == type.id
            }

        if (doseIndex == -1) {
            return
        }

        if (
            potion.wildernessOnly &&
            !coords.isInWilderness(areaChecker)
        ) {
            mes(
                "You can only drink this potion in the Wilderness.",
            )
            return
        }

        if (
            !activityAccess.canConsume(
                player = player,
                minigameOnly = potion.minigameOnly,
                raidOnly = potion.raidOnly,
            )
        ) {
            mes(
                restrictedActivityMessage(
                    minigameOnly = potion.minigameOnly,
                    raidOnly = potion.raidOnly,
                ),
            )
            return
        }

        if (!effects.canApply(this, potion.effect)) {
            return
        }

        val replacement =
            potion.items.getOrNull(doseIndex + 1)
                ?: potion.empty

        val transaction =
            invReplaceSlot(
                inv = inventory,
                slot = slot,
                count = 1,
                replacement = replacement,
            )

        if (transaction.failure) {
            return
        }

        anim(DRINK_ANIMATION)
        soundSynth(DRINK_SOUND)

        effects.apply(
            access = this,
            effect = potion.effect,
        )

        effects.healMix(
            access = this,
            amount = potion.heal,
        )

        ConsumableDelayState.recordConsumption(
            access = this,
            type = ConsumableType.POTION,
            consumeDelay = potion.drinkDelay,
            combatDelay = potion.combatDelay,
        )

        player.resetFaceEntity()

        mes(
            "You drink some of your ${potion.name.lowercase()}.",
        )

        mes(
            remainingDoseMessage(
                potion = potion,
                consumedIndex = doseIndex,
            ),
        )
    }

    private fun remainingDoseMessage(
        potion: PotionRow,
        consumedIndex: Int,
    ): String {
        val remaining =
            potion.items.lastIndex - consumedIndex

        return when (remaining) {
            0 -> "You have finished your potion."
            1 -> "You have 1 dose of potion left."
            else -> "You have $remaining doses of potion left."
        }
    }

    private fun ItemServerType.consumeOption(): Int {
        val index =
            interfaceOptions.indexOfFirst { option ->
                CONSUME_OPTIONS.any { consumeOption ->
                    option.equals(
                        other = consumeOption,
                        ignoreCase = true,
                    )
                }
            }

        return if (index in 0..3) {
            index + 1
        } else {
            -1
        }
    }

    private fun ItemServerType.optionSummary(): String =
        interfaceOptions
            .mapIndexed { index, option ->
                "${index + 1}='$option'"
            }
            .joinToString(
                prefix = "[",
                postfix = "]",
            )

    private fun restrictedActivityMessage(
        minigameOnly: String,
        raidOnly: String,
    ): String {
        val key =
            minigameOnly.ifBlank {
                raidOnly
            }

        val activity =
            when (key) {
                "nightmare_zone" ->
                    "The Nightmare Zone"

                "chambers_of_xeric" ->
                    "The Chambers of Xeric"

                "tombs_of_amascut" ->
                    "The Tombs of Amascut"

                "moons_of_peril" ->
                    "The Moons of Peril"

                else ->
                    key
                        .replace('_', ' ')
                        .replaceFirstChar(Char::uppercase)
            }

        return "You can only drink this potion in $activity."
    }

    private data class PotionRegistration(
        val potion: PotionRow,
        val item: ItemServerType,
        val option: Int,
    )

    private companion object {
        const val DRINK_SOUND: Int =
            2401

        const val DRINK_ANIMATION: String =
            "seq.human_eat"

        val CONSUME_OPTIONS: Set<String> =
            setOf(
                "Drink",
                "Crush",
                "Apply",
                "Crack",
            )
    }
}
