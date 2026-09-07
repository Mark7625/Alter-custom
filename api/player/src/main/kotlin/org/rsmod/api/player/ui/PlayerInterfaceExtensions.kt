package org.rsmod.api.player.ui

import dev.openrune.ServerCacheManager
import dev.openrune.cache.filestore.definition.InterfaceType
import dev.openrune.definition.type.widget.ComponentType
import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import dev.openrune.types.NpcServerType
import dev.openrune.types.SequenceServerType
import dev.openrune.types.aconverted.interf.IfSubType
import java.awt.Color
import net.rsprot.protocol.game.outgoing.interfaces.IfCloseSub
import net.rsprot.protocol.game.outgoing.interfaces.IfMoveSub
import net.rsprot.protocol.game.outgoing.interfaces.IfOpenSub
import net.rsprot.protocol.game.outgoing.interfaces.IfOpenTop
import net.rsprot.protocol.game.outgoing.interfaces.IfSetAnim
import net.rsprot.protocol.game.outgoing.interfaces.IfSetColour
import net.rsprot.protocol.game.outgoing.interfaces.IfSetEventsV2
import net.rsprot.protocol.game.outgoing.interfaces.IfSetHide
import net.rsprot.protocol.game.outgoing.interfaces.IfSetNpcHead
import net.rsprot.protocol.game.outgoing.interfaces.IfSetNpcHeadActive
import net.rsprot.protocol.game.outgoing.interfaces.IfSetObject
import net.rsprot.protocol.game.outgoing.interfaces.IfSetPlayerHead
import net.rsprot.protocol.game.outgoing.interfaces.IfSetPosition
import net.rsprot.protocol.game.outgoing.interfaces.IfSetText
import net.rsprot.protocol.game.outgoing.misc.player.TriggerOnDialogAbort
import org.rsmod.annotations.InternalApi
import org.rsmod.api.config.constants
import org.rsmod.api.player.input.ResumePCountDialogInput
import org.rsmod.api.player.input.ResumePNameDialogInput
import org.rsmod.api.player.input.ResumePObjDialogInput
import org.rsmod.api.player.input.ResumePStringDialogInput
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.output.ClientScripts.chatboxMultiInit
import org.rsmod.api.player.output.ClientScripts.confirmDestroyInit
import org.rsmod.api.player.output.ClientScripts.confirmOverlayInit
import org.rsmod.api.player.output.ClientScripts.ifSetTextAlign
import org.rsmod.api.player.output.ClientScripts.menu
import org.rsmod.api.player.output.ClientScripts.objboxSetButtons
import org.rsmod.api.player.output.ClientScripts.topLevelChatboxResetBackground
import org.rsmod.api.player.output.ClientScripts.topLevelMainModalBackground
import org.rsmod.api.player.output.ClientScripts.topLevelMainModalOpen
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.coroutine.resume.DeferredResumeCondition
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.ui.Component
import org.rsmod.game.ui.UserInterface
import org.rsmod.game.ui.UserInterfaceMap

private typealias OpenSub = org.rsmod.api.player.ui.IfOpenSub

private typealias CloseSub = org.rsmod.api.player.ui.IfCloseSub

private typealias MoveSub = org.rsmod.api.player.ui.IfMoveSub

private var Player.chatModalUnclamp: Int by intVarBit("varbit.chatmodal_unclamp")

public fun Player.ifSetObj(target: String, obj: String, zoomOrCount: Int) {
    client.write(IfSetObject(target.asRSCM(RSCMType.COMPONENT), obj.asRSCM(RSCMType.OBJ), zoomOrCount))
}

public fun Player.ifSetObj(target: String, obj: InvObj, zoomOrCount: Int) {
    client.write(IfSetObject(target.asRSCM(RSCMType.COMPONENT), obj.id, zoomOrCount))
}

public fun Player.ifSetAnim(internal: String, seq: SequenceServerType?) {
    val target = ServerCacheManager.fromComponent(internal.asRSCM(RSCMType.COMPONENT))
    client.write(IfSetAnim(target.interfaceId, target.component, seq?.id ?: -1))
}

public fun Player.ifSetPlayerHead(internal: String) {
    val target = ServerCacheManager.fromComponent(internal.asRSCM(RSCMType.COMPONENT))
    client.write(IfSetPlayerHead(target.interfaceId, target.component))
}

/** @see [IfSetNpcHead] */
public fun Player.ifSetNpcHead(internal: String, npc: String) {
    val target = ServerCacheManager.fromComponent(internal.asRSCM(RSCMType.COMPONENT))
    client.write(IfSetNpcHead(target.interfaceId, target.component, npc.asRSCM(RSCMType.NPC)))
}

/** @see [IfSetNpcHeadActive] */
public fun Player.ifSetNpcHeadActive(internal: String, npcSlotId: Int) {
    val target = ServerCacheManager.fromComponent(internal.asRSCM(RSCMType.COMPONENT))
    client.write(IfSetNpcHeadActive(target.interfaceId, target.component, npcSlotId))
}

public fun Player.ifOpenSide(interf: String, eventBus: EventBus) {
    openModal(interf, "component.toplevel_osrs_stretch:sidemodal", eventBus)
}

public fun Player.ifOpenMainModal(
    interf: String,
    eventBus: EventBus,
    colour: Int = -1,
    transparency: Int = -1,
) {
    topLevelMainModalOpen(this, colour, transparency)
    ifOpenMain(interf, eventBus)
}

public fun Player.ifOpenMain(interf: String, eventBus: EventBus) {
    openModal(interf, "component.toplevel_osrs_stretch:mainmodal", eventBus)
}

public fun Player.ifOpenMainSidePair(
    main: String,
    side: String,
    colour: Int,
    transparency: Int,
    eventBus: EventBus,
) {
    topLevelMainModalBackground(this, colour, transparency)
    openModal(main, "component.toplevel_osrs_stretch:mainmodal", eventBus)
    openModal(side, "component.toplevel_osrs_stretch:sidemodal", eventBus)
}

public fun Player.ifOpenOverlay(interf: String, target: String, eventBus: EventBus) {
    ifOpenSub(interf, target, IfSubType.Overlay, eventBus)
}

public fun Player.ifOpenOverlay(interf: String, eventBus: EventBus) {
    ifOpenOverlay(interf, "component.toplevel_osrs_stretch:floater", eventBus)
}

public fun Player.ifOpenFullOverlay(interf: String, eventBus: EventBus) {
    ifOpenOverlay(interf, "component.toplevel_osrs_stretch:overlay_atmosphere", eventBus)
}

/**
 * Difference from [ifCloseModals]: this function clears all weak queues for the player and closes
 * any active dialog.
 *
 * @see [cancelActiveDialog]
 */
public fun Player.ifClose(eventBus: EventBus) {
    cancelActiveDialog()
    weakQueueList.clear()
    ifCloseModals(eventBus)
}

/**
 * Cancels and closes any active _input_ dialog suspension.
 *
 * #### Note
 * This is a custom concept and not part of any known game mechanic or command. It is primarily used
 * internally to close suspending input dialogs during the handling of `If3Button` packets.
 *
 * **Warning:** Avoid calling this function unless you fully understand its implications, as it can
 * interrupt active dialog-related processes.
 */
@InternalApi("Usage of this function should only be used internally, or sparingly.")
public fun Player.ifCloseInputDialog() {
    val coroutine = activeCoroutine ?: return
    if (coroutine.requiresInputDialogAbort()) {
        cancelActiveCoroutine()
        client.write(TriggerOnDialogAbort)
    }
}

/**
 * If [requiresInputDialogAbort] or [requiresPauseDialogAbort] conditions are met, the player's
 * active script will be cancelled ([Player.cancelActiveCoroutine]) and [TriggerOnDialogAbort] will
 * be sent to their client.
 */
private fun Player.cancelActiveDialog() {
    val coroutine = activeCoroutine ?: return
    if (coroutine.requiresPauseDialogAbort() || coroutine.requiresInputDialogAbort()) {
        cancelActiveCoroutine()
        client.write(TriggerOnDialogAbort)
    }
}

/**
 * Checks if the coroutine is suspended on a [DeferredResumeCondition] and the deferred type matches
 * [ResumePauseButtonInput], which occurs during dialogs with `Click here to continue`-esque pause
 * buttons.
 */
private fun GameCoroutine.requiresPauseDialogAbort(): Boolean =
    isAwaiting(ResumePauseButtonInput::class)

/**
 * Checks if the coroutine is suspended on a [DeferredResumeCondition] and the deferred type matches
 * any input from dialogue boxes built through cs2, which are not standard modals or overlays.
 */
private fun GameCoroutine.requiresInputDialogAbort(): Boolean {
    return isAwaitingAny(
        ResumePCountDialogInput::class,
        ResumePNameDialogInput::class,
        ResumePStringDialogInput::class,
        ResumePObjDialogInput::class,
    )
}

public fun Player.ifCloseModals(eventBus: EventBus) {
    // This gives us an iterable copy of the entries, so we are safe to modify `ui.modals` while
    // closing them.
    val modalEntries = ui.modals.entries()
    for ((key, value) in modalEntries) {
        val interf = UserInterface(value)
        val target = Component(key)
        closeModal(interf, target, eventBus)
    }
    // Make sure _all_ modals were closed. If not, then something is wrong, and we'd rather force
    // the player to disconnect than to allow them to keep modals open when they shouldn't.
    check(ui.modals.isEmpty()) {
        "Could not close all modals for player `$this`. (modals=${ui.modals})"
    }
}

public fun Player.ifSetEvents(internal: String, range: IntRange, vararg event: IfEvent) {
    val packed = event.fold(0L) { sum, element -> sum or element.bitmask }
    ui.events.add(internal, range, packed)

    val target = ServerCacheManager.fromComponent(internal.asRSCM(RSCMType.COMPONENT))

    val packedHigh = (packed shr 32).toInt()
    val packedLow = packed.toInt()
    val prot =
        IfSetEventsV2(
            interfaceId = target.interfaceId,
            componentId = target.component,
            start = range.first,
            end = range.last,
            events1 = packedLow,
            events2 = packedHigh,
        )
    client.write(prot)
}

/** Moves a component to ([x], [y]) within its parent, keeping its position modes. */
public fun Player.ifSetPosition(internal: String, x: Int, y: Int) {
    val target = ServerCacheManager.fromComponent(internal.asRSCM(RSCMType.COMPONENT))
    client.write(IfSetPosition(target.interfaceId, target.component, x, y))
}

public fun Player.ifSetText(internal: String, text: String) {
    val target = ServerCacheManager.fromComponent(internal.asRSCM(RSCMType.COMPONENT))
    client.write(IfSetText(target.interfaceId, target.component, text))
}

public fun Player.ifSetHide(internal: String, hide: Boolean) {
    val target = ServerCacheManager.fromComponent(internal.asRSCM(RSCMType.COMPONENT))
    client.write(IfSetHide(target.interfaceId, target.component, hide))
}

public fun Player.ifOpenTop(topLevel: String) {
    val interfaceID = topLevel.asRSCM(RSCMType.INTERFACE)
    val userInterface = UserInterface(interfaceID)
    ui.topLevel = userInterface
    client.write(IfOpenTop(interfaceID))
}

public fun Player.ifMoveTop(dest: String, eventBus: EventBus) {
    check(ui.topLevel != UserInterface.NULL) {
        "This function can only be used after `ifOpenTop` has been called. " +
            "Use `ifOpenTop` instead."
    }
    val destInterface = ServerCacheManager.getInterface(dest.asRSCM(RSCMType.INTERFACE))
        ?: error("Could not find interface $dest")
    eventBus.publish(IfMoveTop(this, destInterface))
}

public fun Player.ifOpenSub(
    interf: String,
    target: String,
    type: IfSubType,
    eventBus: EventBus,
): Unit =
    when (type) {
        IfSubType.Modal -> openModal(interf, target, eventBus)
        IfSubType.Overlay -> openOverlay(interf, target, eventBus)
    }

public fun Player.ifCloseSub(interf: String, eventBus: EventBus) {
    closeModal(interf, eventBus)
    closeOverlay(interf, eventBus)
}

public fun Player.ifCloseModal(interf: String, eventBus: EventBus) {
    closeModal(interf, eventBus)
}

public fun Player.ifCloseOverlay(interf: String, eventBus: EventBus) {
    closeOverlay(interf, eventBus)
}

private fun Player.openModal(interf: String, internal: String, eventBus: EventBus) {
    val target = ServerCacheManager.fromComponent(internal.asRSCM(RSCMType.COMPONENT))
    val idComponent = target.toIdComponent()
    val interfaceID = interf.asRSCM(RSCMType.INTERFACE)
    val idInterface =
        ServerCacheManager.getInterface(interfaceID)?.toIdInterface()
            ?: error("Could not find interface $interf")
    triggerCloseSubs(idComponent, eventBus)
    ui.removeQueuedCloseSub(target)
    ui.modals[idComponent] = idInterface

    // Translate any gameframe target component when sent to the client. As far as the server is
    // aware, the interface is being opened on the "base" target component. (when applicable)
    val translated = ui.translate(idComponent)
    client.write(IfOpenSub(translated.parent, translated.child, interfaceID, IfSubType.Modal.id))

    eventBus.publish(OpenSub(this, idInterface, idComponent, IfSubType.Modal))
}

public fun Player.setColour(component: String, colour: Color) {
    client.write(IfSetColour(component.asRSCM(), colour))
}

private fun Player.openOverlay(interf: String, internal: String, eventBus: EventBus) {
    val target = ServerCacheManager.fromComponent(internal.asRSCM(RSCMType.COMPONENT))
    val idComponent = target.toIdComponent()
    val interfaceID = interf.asRSCM(RSCMType.INTERFACE)
    val idInterface =
        ServerCacheManager.getInterface(interfaceID)?.toIdInterface()
            ?: error("Could not find interface $interf")
    triggerCloseSubs(idComponent, eventBus)
    ui.removeQueuedCloseSub(target)
    ui.overlays[idComponent] = idInterface

    // Translate any gameframe target component when sent to the client. As far as the server is
    // aware, the interface is being opened on the "base" target component. (when applicable)
    val translated = ui.translate(idComponent)
    client.write(IfOpenSub(translated.parent, translated.child, interfaceID, IfSubType.Overlay.id))

    eventBus.publish(OpenSub(this, idInterface, idComponent, IfSubType.Overlay))
}

private fun Player.closeModal(interf: String, eventBus: EventBus) {
    val idInterface =
        ServerCacheManager.getInterface(interf.asRSCM(RSCMType.INTERFACE))?.toIdInterface()
            ?: error("Could not find interface $interf")
    val target = ui.modals.getComponent(idInterface)
    if (target != null) {
        closeModal(idInterface, target, eventBus)
    }
}

private fun Player.closeModal(interf: UserInterface, target: Component, eventBus: EventBus) {
    ui.modals.remove(target)
    ui.events.clear(interf)

    // Translate any gameframe target component when sent to the client. As far as the server
    // is aware, the interface was open on the "base" target component. (when applicable)
    val translated = ui.translate(target)
    client.write(IfCloseSub(translated.parent, translated.child))

    eventBus.publish(CloseSub(this, interf, target))

    closeOverlayChildren(interf, eventBus)
}

private fun Player.closeOverlay(interf: String, eventBus: EventBus) {
    val idInterface =
        ServerCacheManager.getInterface(interf.asRSCM(RSCMType.INTERFACE))?.toIdInterface()
            ?: error("Could not find interface $interf")
    val target = ui.overlays.getComponent(idInterface)
    if (target != null) {
        closeOverlay(idInterface, target, eventBus)
    }
}

private fun Player.closeOverlay(interf: UserInterface, target: Component, eventBus: EventBus) {
    ui.overlays.remove(target)
    ui.events.clear(interf)

    // Translate any gameframe target component when sent to the client. As far as the server
    // is aware, the interface was open on the "base" target component. (when applicable)
    val translated = ui.translate(target)
    client.write(IfCloseSub(translated.parent, translated.child))

    eventBus.publish(CloseSub(this, interf, target))

    closeOverlayChildren(interf, eventBus)
}

private fun Player.closeOverlayChildren(parent: UserInterface, eventBus: EventBus) {
    // This gives us an iterable copy of the entries, so we are safe to modify `ui.overlays` while
    // closing them.
    val overlayEntries = ui.overlays.entries()
    for ((key, value) in overlayEntries) {
        val interf = UserInterface(value)
        val target = Component(key)
        if (target.parent == parent.id) {
            closeOverlay(interf, target, eventBus)
        }
    }
}

@InternalApi("Usage of this function should only be used internally")
public fun Player.closeSubs(from: Component, eventBus: EventBus) {
    val remove = ui.modals.remove(from) ?: ui.overlays.remove(from)
    if (remove != null) {
        ui.events.clear(remove)

        // Translate any gameframe target component when sent to the client. As far as the server
        // is aware, the interface was open on the "base" target component. (when applicable)
        val translated = ui.translate(from)
        client.write(IfCloseSub(translated.parent, translated.child))

        eventBus.publish(CloseSub(this, remove, from))

        closeOverlayChildren(remove, eventBus)
    }
}

/**
 * Similar to [closeSubs], but only triggers "close sub" scripts and does _not_ send [IfCloseSub]
 * packet to the client.
 */
private fun Player.triggerCloseSubs(from: Component, eventBus: EventBus) {
    val remove = ui.modals.remove(from) ?: ui.overlays.remove(from)
    if (remove != null) {
        ui.events.clear(remove)
        eventBus.publish(CloseSub(this, remove, from))
        triggerCloseOverlayChildren(remove, eventBus)
    }
}

/**
 * Similar to [closeOverlayChildren], but only triggers "close sub" scripts and does _not_ send
 * [IfCloseSub] packet to the client.
 */
private fun Player.triggerCloseOverlayChildren(parent: UserInterface, eventBus: EventBus) {
    // This gives us an iterable copy of the entries, so we are safe to modify `ui.overlays` while
    // closing them.
    val overlayEntries = ui.overlays.entries()
    for ((key, value) in overlayEntries) {
        val interf = UserInterface(value)
        val target = Component(key)
        if (target.parent == parent.id) {
            triggerCloseOverlay(interf, target, eventBus)
        }
    }
}

/**
 * Similar to [closeOverlay], but only triggers "close sub" scripts and does _not_ send [IfCloseSub]
 * packet to the client.
 */
private fun Player.triggerCloseOverlay(
    interf: UserInterface,
    target: Component,
    eventBus: EventBus,
) {
    ui.overlays.remove(target)
    ui.events.clear(interf)
    eventBus.publish(CloseSub(this, interf, target))
    triggerCloseOverlayChildren(interf, eventBus)
}

public fun Player.ifMoveSub(
    source: Component,
    dest: Component,
    base: Component,
    eventBus: EventBus,
) {
    client.write(IfMoveSub(source.packed, dest.packed))
    eventBus.publish(MoveSub(this, base.packed))
}

private fun InterfaceType.toIdInterface() = UserInterface(id)

private fun ComponentType.toIdComponent() = Component(packed)

private fun UserInterfaceMap.translate(component: Component): Component =
    gameframe.getOrNull(component) ?: component

/*
 * Dialogue helper functions
 *
 * These functions are intended to help with displaying various dialogue interfaces to the player.
 * However, they do _not_ properly handle state suspension or resuming from player input.
 *
 * Important: These functions should only be used internally within systems that properly manage
 * player state, input handling, and coroutine suspension. Direct usage in other contexts may result
 * in unwanted behavior.
 */

internal fun Player.ifMesbox(text: String, pauseText: String, lineHeight: Int, eventBus: EventBus) {
    mes(text, ChatType.Mesbox)
    openModal("interface.messagebox", "component.chatbox:chatmodal", eventBus)
    ifSetText("component.messagebox:text", text)
    ifSetTextAlign(this, "component.messagebox:text", alignH = 1, alignV = 1, lineHeight)
    ifSetPauseText("component.messagebox:continue", pauseText)
    // TODO: Look into clientscript to name property and place in clientscript utility class.
    runClientScript(1508, "0")
}

internal fun Player.ifObjbox(
    text: String,
    obj: Int,
    zoom: Int,
    pauseText: String,
    eventBus: EventBus,
) {
    mes(text, ChatType.Mesbox)
    ifOpenChat("interface.objectbox", constants.modal_infinitewidthandheight, eventBus)
    objboxSetButtons(this, pauseText)
    if (pauseText.isNotBlank()) {
        ifSetEvents("component.objectbox:universe", 0..1, IfEvent.PauseButton)
    } else {
        // Note: This purposefully disables `if_events` for subcomponents -1 to -1.
        ifSetEvents("component.objectbox:universe", -1..-1)
    }
    ifSetObj("component.objectbox:item", obj, zoom)
    ifSetText("component.objectbox:text", text)
}

internal fun Player.ifDoubleobjbox(
    text: String,
    obj1: Int,
    zoom1: Int,
    obj2: Int,
    zoom2: Int,
    pauseText: String,
    eventBus: EventBus,
) {
    mes(text, ChatType.Mesbox)
    ifOpenChat("interface.objectbox_double", constants.modal_infinitewidthandheight, eventBus)
    ifSetPauseText("component.objectbox_double:pausebutton", pauseText)
    ifSetObj("component.objectbox_double:model1", obj1, zoom1)
    ifSetObj("component.objectbox_double:model2", obj2, zoom2)
    ifSetText("component.objectbox_double:text", text)
}

internal fun Player.ifConfirmDestroy(
    header: String,
    text: String,
    obj: Int,
    count: Int,
    eventBus: EventBus,
) {
    ifOpenChat("interface.confirmdestroy", constants.modal_fixedwidthandheight, eventBus)
    confirmDestroyInit(this, header, text, obj, count)
    ifSetEvents("component.confirmdestroy:universe", 0..1, IfEvent.PauseButton)
}

internal fun Player.ifConfirmOverlay(
    target: String,
    title: String,
    text: String,
    cancel: String,
    confirm: String,
    eventBus: EventBus,
) {
    ifOpenSub("interface.popupoverlay", target, IfSubType.Overlay, eventBus)
    confirmOverlayInit(this, target, title, text, cancel, confirm)
}

internal fun Player.ifConfirmOverlayClose(eventBus: EventBus): Unit =
    ifCloseOverlay("interface.popupoverlay", eventBus)

internal fun Player.ifMenu(
    title: String,
    joinedChoices: String,
    hotkeys: Boolean,
    eventBus: EventBus,
) {
    ifOpenMainModal("interface.menu", eventBus)
    menu(this, title, joinedChoices, hotkeys)
    ifSetEvents("component.menu:lj_layer1", 0..127, IfEvent.PauseButton)
}

/** @see [chatboxMultiInit] */
internal fun Player.ifChoice(
    title: String,
    joinedChoices: String,
    choiceCountInclusive: Int,
    eventBus: EventBus,
) {
    ifOpenChat("interface.chatmenu", constants.modal_infinitewidthandheight, eventBus)
    chatboxMultiInit(this, title, joinedChoices)
    ifSetEvents("component.chatmenu:options", 1..choiceCountInclusive, IfEvent.PauseButton)
}

internal fun Player.ifChatPlayer(
    title: String,
    text: String,
    expression: SequenceServerType?,
    pauseText: String,
    lineHeight: Int,
    eventBus: EventBus,
) {
    mes("$title|$text", ChatType.Dialogue)
    ifOpenChat("interface.chat_right", constants.modal_fixedwidthandheight, eventBus)
    ifSetPlayerHead("component.chat_right:head")
    ifSetAnim("component.chat_right:head", expression)
    ifSetText("component.chat_right:name", title)
    ifSetText("component.chat_right:text", text)
    ifSetTextAlign(this, "component.chat_right:text", alignH = 1, alignV = 1, lineHeight)
    ifSetPauseText("component.chat_right:continue", pauseText)
}

internal fun Player.ifChatNpcActive(
    title: String,
    npcSlotId: Int,
    text: String,
    chatanim: SequenceServerType?,
    pauseText: String,
    lineHeight: Int,
    eventBus: EventBus,
) {
    mes("$title|$text", ChatType.Dialogue)
    ifOpenChat("interface.chat_left", constants.modal_fixedwidthandheight, eventBus)
    ifSetNpcHeadActive("component.chat_left:head", npcSlotId)
    ifSetAnim("component.chat_left:head", chatanim)
    ifSetText("component.chat_left:name", title)
    ifSetText("component.chat_left:text", text)
    ifSetTextAlign(this, "component.chat_left:text", alignH = 1, alignV = 1, lineHeight)
    ifSetPauseText("component.chat_left:continue", pauseText)
}

internal fun Player.ifChatNpcSpecific(
    title: String,
    type: String,
    text: String,
    chatanim: SequenceServerType?,
    pauseText: String,
    lineHeight: Int,
    eventBus: EventBus,
) {
    mes("$title|$text", ChatType.Dialogue)
    ifOpenChat("interface.chat_left", constants.modal_infinitewidthandheight, eventBus)
    ifSetNpcHead("component.chat_left:head", type)
    ifSetAnim("component.chat_left:head", chatanim)
    ifSetText("component.chat_left:name", title)
    ifSetText("component.chat_left:text", text)
    ifSetTextAlign(this, "component.chat_left:text", alignH = 1, alignV = 1, lineHeight)
    ifSetPauseText("component.chat_left:continue", pauseText)
}

internal fun Player.ifOpenChat(interf: String, widthAndHeightMode: Int, eventBus: EventBus) {
    chatModalUnclamp = widthAndHeightMode
    topLevelChatboxResetBackground(this)
    openModal(interf, "component.chatbox:chatmodal", eventBus)
}

private fun Player.ifSetPauseText(component: String, text: String) {
    if (text.isNotBlank()) {
        ifSetEvents(component, -1..-1, IfEvent.PauseButton)
    } else {
        ifSetEvents(component, -1..-1)
    }
    ifSetText(component, text)
}

private fun Player.ifSetObj(target: String, obj: Int, zoomOrCount: Int) {
    client.write(IfSetObject(target.asRSCM(RSCMType.COMPONENT), obj, zoomOrCount))
}
