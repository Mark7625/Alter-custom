package org.alter.game.pluginnew.event.impl

import dev.openrune.ServerCacheManager.getItem
import org.alter.game.model.entity.Player
import org.alter.game.pluginnew.MenuOption
import org.alter.game.pluginnew.PluginEvent
import org.alter.game.pluginnew.event.EventListener
import org.alter.rscm.RSCM.asRSCM
import org.alter.rscm.RSCM.requireRSCM
import org.alter.rscm.RSCMType

open class ItemClickEvent(
    open val item: Int,
    val slot: Int,
    open val op: MenuOption,
    val container: ContainerType,
    player: Player
) : EntityInteractionEvent<Int>(item, op, player) {

    public override fun resolveOptionName(): String {
        val indexSlot = op.id - 2
        val def = getItem(item) ?: error("Item not found for id=$item")
        return def.interfaceOptions.getOrNull(indexSlot)
            ?: error("No action found at index $indexSlot for item id=$item")
    }

    fun hasOption(option: String) = resolveOptionName().equals(option, ignoreCase = true)

    fun isContainer(type: ContainerType) = container == type
    fun isInventory() = container == ContainerType.INVENTORY
    fun isWornEquipment() = container == ContainerType.WORN_EQUIPMENT
    fun isEquipment() = container == ContainerType.EQUIPMENT
}

private fun PluginEvent.onItemOptionInternal(
    itemMatches: (ItemClickEvent) -> Boolean,
    option: String? = null,
    op: MenuOption? = null,
    action: suspend ItemClickEvent.() -> Unit
): EventListener<ItemClickEvent> {
    require(!(option != null && op != null)) {
        "You cannot provide both `option` and `op` at the same time."
    }

    return on<ItemClickEvent> {
        where {
            itemMatches(this) &&
                    (op == null || this.op == op) &&
                    (option == null || runCatching {
                        resolveOptionName().equals(option, ignoreCase = true) ||
                                this.op.name.equals(option, ignoreCase = true)
                    }.getOrDefault(false))
        }
        then { action(this) }
    }
}

fun PluginEvent.onItemOption(
    item: String,
    option: String? = null,
    op: MenuOption? = null,
    action: suspend ItemClickEvent.() -> Unit
): EventListener<ItemClickEvent> {
    requireRSCM(RSCMType.OBJTYPES, item)
    val rscmItem = item.asRSCM()
    return onItemOptionInternal(
        itemMatches = { it.item == rscmItem },
        option = option,
        op = op,
        action = action
    )
}

fun PluginEvent.onItemOption(
    item: Int,
    option: String? = null,
    op: MenuOption? = null,
    action: suspend ItemClickEvent.() -> Unit
): EventListener<ItemClickEvent> =
    onItemOptionInternal(
        itemMatches = { it.item == item },
        option = option,
        op = op,
        action = action
    )

class ItemDropEvent(
    val itemId: Int,
    val slot: Int,
    val container: ContainerType,
    player: Player
) : ValueEvent<Int>(itemId, player)

class EquipEvent(
    val itemId: Int,
    val slot: Int,
    val container: ContainerType,
    player: Player
) : ValueEvent<Int>(itemId, player)

class UnequipEvent(
    val itemId: Int,
    val slot: Int,
    val container: ContainerType,
    player: Player
) : ValueEvent<Int>(itemId, player)

private fun PluginEvent.onItemEquipInternal(
    matchesItem: (EquipEvent) -> Boolean,
    action: suspend EquipEvent.() -> Unit
) = on<EquipEvent> {
    where { matchesItem(this) }
    then { action(this) }
}

fun PluginEvent.onItemEquip(
    item: String,
    action: suspend EquipEvent.() -> Unit
): EventListener<EquipEvent> {
    requireRSCM(RSCMType.OBJTYPES, item)
    val rscmItem = item.asRSCM()
    return onItemEquipInternal(matchesItem = { it.itemId == rscmItem }, action = action)
}

fun PluginEvent.onItemEquip(
    item: Int,
    action: suspend EquipEvent.() -> Unit
) = onItemEquipInternal(matchesItem = { it.itemId == item }, action = action)

private fun PluginEvent.onItemUnequipInternal(
    matchesItem: (UnequipEvent) -> Boolean,
    action: suspend UnequipEvent.() -> Unit
) = on<UnequipEvent> {
    where { matchesItem(this) }
    then { action(this) }
}

fun PluginEvent.onItemUnequip(
    item: String,
    action: suspend UnequipEvent.() -> Unit
): EventListener<UnequipEvent> {
    requireRSCM(RSCMType.OBJTYPES, item)
    val rscmItem = item.asRSCM()
    return onItemUnequipInternal(matchesItem = { it.itemId == rscmItem }, action = action)
}

fun PluginEvent.onItemUnequip(
    item: Int,
    action: suspend UnequipEvent.() -> Unit
) = onItemUnequipInternal(matchesItem = { it.itemId == item }, action = action)