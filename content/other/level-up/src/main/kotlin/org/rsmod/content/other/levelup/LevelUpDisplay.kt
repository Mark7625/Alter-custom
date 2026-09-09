package org.rsmod.content.other.levelup

import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.interf.IfSubType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.config.constants
import org.rsmod.api.player.output.ClientScripts.topLevelChatboxResetBackground
import org.rsmod.api.player.ui.ifOpenSub
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.player.ui.ifSetHide
import org.rsmod.api.player.ui.ifSetText
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player

private var Player.chatModalUnclamp: Int by intVarBit("varbit.chatmodal_unclamp")

/**
 * The level-up box in the chatbox - `interface.levelup_display`, the one with the skill's icon,
 * "Congratulations, you just advanced a Cooking level." and "Click here to continue".
 *
 * The interface holds one hidden layer per skill (plus `combat`), each containing that skill's
 * icon models, and shares `text1`, `text2` and `continue` across all of them. There is no
 * clientscript driving it, so showing the right icon is the server's job: open the interface, then
 * un-hide the one layer that matches the skill.
 *
 * The layer names line up with the stat names - `attack`, `cooking`, `woodcutting` - so they are
 * derived rather than tabulated, with [LAYER_ALIASES] covering the one that does not match and a
 * check against the interface itself so a skill with no layer falls back to plain chat lines
 * instead of throwing.
 *
 * #### Dismissing it
 *
 * The box does not pause the player; it sits in the chatbox while they carry on. `continue` is sent
 * as an [IfEvent.PauseButton], and `ResumePauseButtonHandler` closes the modal on click whether or
 * not a coroutine is waiting on it, so clicking it dismisses the box. Anything that opens its own
 * chat modal replaces it, which is also how Old School RuneScape behaves.
 *
 * #### Why it never lands on top of a dialogue
 *
 * Opening a chat modal closes whatever is in that slot, which would strand a suspended dialogue.
 * It cannot happen here: `PlayerEngineQueueProcessor` only publishes an engine queue while the
 * player is not access-protected, and a player waiting on a dialogue is.
 */
@Singleton
class LevelUpDisplay @Inject constructor(private val eventBus: EventBus) {
    private val layerNames: Set<String> by lazy {
        val interf = ServerCacheManager.getInterface(INTERFACE.asRSCM(RSCMType.INTERFACE))
        interf?.components?.values?.mapNotNullTo(HashSet()) { it.internalName }.orEmpty()
    }

    /**
     * Returns the component of the layer holding [internalStatName]'s icon, or `null` if this
     * interface has no layer for that skill.
     */
    fun layerFor(internalStatName: String): String? {
        val name = LAYER_ALIASES[internalStatName] ?: internalStatName.substringAfter('.')
        return if (name in layerNames) "component.levelup_display:$name" else null
    }

    /** Opens the box in the chatbox with [layer]'s icon showing and [line1] / [line2] set. */
    fun show(player: Player, layer: String, line1: String, line2: String) {
        // Matches `ifOpenChat`, which is internal to api.player: the box is a fixed 479x96, so it
        // must not inherit an "unclamped" size from whichever dialogue used the slot last.
        player.chatModalUnclamp = constants.modal_fixedwidthandheight
        topLevelChatboxResetBackground(player)
        player.ifOpenSub(INTERFACE, CHAT_MODAL, IfSubType.Modal, eventBus)

        // Re-opening the interface resets its components to their cache defaults, which hides every
        // skill layer again. Hiding the last one shown is belt and braces, and costs one packet.
        val previous = player.attr[SHOWN_LAYER]
        if (previous != null && previous != layer) {
            player.ifSetHide(previous, true)
        }
        player.ifSetHide(layer, false)
        player.attr[SHOWN_LAYER] = layer

        player.ifSetText(TEXT_LINE_1, line1)
        player.ifSetText(TEXT_LINE_2, line2)
        player.ifSetEvents(CONTINUE, -1..-1, IfEvent.PauseButton)
        player.ifSetText(CONTINUE, "Click here to continue")
    }

    private companion object {
        private const val INTERFACE = "interface.levelup_display"
        private const val CHAT_MODAL = "component.chatbox:chatmodal"
        private const val TEXT_LINE_1 = "component.levelup_display:text1"
        private const val TEXT_LINE_2 = "component.levelup_display:text2"
        private const val CONTINUE = "component.levelup_display:continue"

        /** Layer names that do not match their stat name. */
        private val LAYER_ALIASES = mapOf("stat.runecrafting" to "runecraft")

        /** The layer last un-hidden for a player, so it can be hidden again on the next level up. */
        private val SHOWN_LAYER = AttributeKey<String>()
    }
}
