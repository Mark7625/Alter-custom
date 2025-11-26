package org.alter.game.pluginnew.event.impl

import dev.openrune.ServerCacheManager.getObject
import org.alter.game.model.entity.GameObject
import org.alter.game.model.entity.Player
import org.alter.game.model.item.Item
import org.alter.game.pluginnew.MenuOption
import org.alter.game.pluginnew.PluginEvent
import org.alter.game.pluginnew.event.EventListener
import org.alter.game.pluginnew.event.PlayerEvent
import org.alter.rscm.RSCM
import org.alter.rscm.RSCM.asRSCM
import org.alter.rscm.RSCM.requireRSCM
import org.alter.rscm.RSCMType

class ItemOnObject(
    val item : Item,
    val gameObject: GameObject,
    val slot : Int,
    val id : Int = gameObject.internalID,
    override val player: Player
) : PlayerEvent(player) {

    val option: String
        get() = resolveOptionName(id, MenuOption.OP1.id)

    companion object {
        private fun resolveOptionName(id: Int, opId: Int): String {
            val def = getObject(id)
                ?: error("Object not found for id=$id")

            return def.actions.getOrNull(opId - 1)
                ?: error("No action found at index $opId for object id=$id")
        }
    }
}

open class ObjectClickEvent(
    open val gameObject: GameObject,
    open val op: MenuOption,
    /**
     * The ID of the object.
     * Note: This returns the transformed ID if the object has a varbit/varp transform applied,
     * not necessarily the original internal ID.
     */
    val id : Int = gameObject.internalID,
    player: Player
) : EntityInteractionEvent<GameObject>(gameObject, op, player) {

    override fun resolveOptionName(): String {
        val def = getObject(id) ?: error("Object not found for id=${id}[${id}]")
        return def.actions.getOrNull(op.id - 1) ?: error("No action found at index ${op.id} for object id=${id}[${id}]")
    }

    fun objHasOption(option: String, ignoreCase: Boolean = true): Boolean {
        val def = getObject(id) ?: return false
        return def.actions.any { it?.equals(option, ignoreCase) == true }
    }

    fun objHasOption(options: Iterable<String>, ignoreCase: Boolean = true): Boolean {
        return options.any { objHasOption(it, ignoreCase) }
    }

    fun objHasOption(vararg options: String): Boolean {
        return objHasOption(options.asList())
    }
}


fun PluginEvent.onObjectOption(
    obj: String,
    vararg options: String,
    action: suspend ObjectClickEvent.() -> Unit
): EventListener<ObjectClickEvent> {
    requireRSCM(RSCMType.LOCTYPES,obj)
    return on<ObjectClickEvent> {
        where {
            RSCM.getReverseMapping(RSCMType.LOCTYPES, id) == obj && options.any { it.equals(optionName, ignoreCase = true) }
        }
        then { action(this) }
    }
}

fun PluginEvent.onObjectOption(
    obj: Int,
    vararg options: String,
    action: suspend ObjectClickEvent.() -> Unit
): EventListener<ObjectClickEvent> {
    return on<ObjectClickEvent> {
        where {
            id == obj && options.any { it.equals(optionName, ignoreCase = true) }
        }
        then { action(this) }
    }
}