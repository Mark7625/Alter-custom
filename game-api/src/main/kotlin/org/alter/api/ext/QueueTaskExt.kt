package org.alter.api.ext

import dev.openrune.ServerCacheManager.getItem
import dev.openrune.ServerCacheManager.getNpc
import net.rsprot.protocol.game.incoming.resumed.ResumePauseButton
import net.rsprot.protocol.game.outgoing.misc.player.TriggerOnDialogAbort
import org.alter.api.ClientScript
import org.alter.api.CommonClientScripts
import org.alter.game.model.attr.INTERACTING_NPC_ATTR
import org.alter.game.model.entity.Player
import org.alter.game.model.item.Item
import org.alter.game.model.queue.QueueTask


import org.alter.game.pluginnew.event.impl.DialogCloseAll
import org.alter.game.pluginnew.event.impl.DialogItem
import org.alter.game.pluginnew.event.impl.DialogItemDouble
import org.alter.game.pluginnew.event.impl.DialogMessageOpen
import org.alter.game.pluginnew.event.impl.DialogMessageOption
import org.alter.game.pluginnew.event.impl.DialogNpcOpen
import org.alter.game.pluginnew.event.impl.DialogPlayerOpen
import org.alter.game.pluginnew.event.impl.DialogSkillMulti
import org.alter.game.pluginnew.event.EventManager
import org.alter.rscm.RSCM.asRSCM


/**
 * The child id of the chat box in the gameframe interface. This can change
 * with revision.
 */
const val CHATBOX_CHILD = 566


/**
 * The default action that will occur when interrupting or finishing a dialog.
 */
private fun closeDialog(player: Player): QueueTask.() -> Unit =
    {
        DialogCloseAll(player).post()
        player.write(TriggerOnDialogAbort)
    }

/**
 * Invoked when input dialog queues are interrupted.
 */
private fun closeInput(player: Player): QueueTask.() -> Unit = {
        player.closeInputDialog()
    }


/**
 * Prompts the player with options.
 *
 * @return
 * The id of the option chosen. The id can range from [1] inclusive to [options.size] inclusive.
 */
suspend fun QueueTask.options(
    player: Player,
    vararg options: String,
    title: String = "Select an Option",
    fullSize: Boolean = true
): Int {
    DialogMessageOption(title, options.joinToString("|"),player).post()
    terminateAction = closeDialog(player)
    waitReturnValue()
    terminateAction!!(this)
    return (requestReturnValue as? ResumePauseButton)?.sub ?: -1
}

/**
 * Prompts the player with an input dialog where they can only enter an integer.
 *
 * @return
 * The integer input.
 */
suspend fun QueueTask.inputInt(
    player: Player,
    description: String = "Enter amount"
): Int {
    player.runClientScript(ClientScript("meslayer_mode7"), description)
    terminateAction = closeInput(player)
    waitReturnValue()
    terminateAction!!(this)

    return requestReturnValue as? Int ?: -1
}

/**
 * Prompts the player with an input dialog where they can enter a string.
 *
 * @param description the title, or description, of the dialog box.
 *
 * @return the string input.
 */
suspend fun QueueTask.inputString(
    player: Player,
    description: String = "Enter text"
): String {
    player.runClientScript(ClientScript("meslayer_mode9"), description)
    terminateAction = closeInput(player)
    waitReturnValue()
    terminateAction!!(this)

    return requestReturnValue as? String ?: ""
}


/**
 * Prompts the player with a chatbox interface that allows them to search
 * for an item.
 *
 * @return
 * The selected item's id.
 */
suspend fun QueueTask.searchItemInput(
    player: Player,
    message: String
): Int {
    player.runClientScript(CommonClientScripts.GE_SEARCH_ITEMS, message, 1, -1)

    terminateAction = closeInput(player)
    waitReturnValue()

    return requestReturnValue as? Int ?: -1
}

/**
 * Prompts the player with a chatbox interface that allows them to search
 * for an item.
 * Difference from [QueueTask.searchItemInput] It let's you choose untradeable items too.
 *
 * @return
 * The selected item's id.
 */
suspend fun QueueTask.searchItemInputT(
    player: Player,
    message: String
): Int {
    player.runClientScript(CommonClientScripts.GE_SEARCH_ITEMS, message, 0, -1)

    terminateAction = closeInput(player)
    waitReturnValue()

    return requestReturnValue as? Int ?: -1
}

/**
 * Sends a normal message dialog.
 *
 * @message
 * The message to render on the dialog box.
 *
 * @lineSpacing
 * The spacing, in pixels, in between each line that will be rendered on the
 * dialog box.
 */
suspend fun QueueTask.messageBox(
    player: Player,
    message: String,
    continues: Boolean = true,
) {
    
    DialogMessageOpen(message, continues,player).post()

    terminateAction = closeDialog(player)
    waitReturnValue()
    terminateAction!!(this)
}

/**
 * Send a dialog with an npc's head model.
 *
 * @param message
 * The message to render on the dialog box.
 *
 * @npc
 * The npc id which represents the npc that will be drawn on the dialog box.
 * If set to -1, the npc id will be set to the player's interacting npc. If the
 * player is not interacting with an npc, a [RuntimeException] will be thrown.
 *
 * @animation
 * The animation id of the npc's head model.
 *
 * @title
 * The title of the dialog, if left as null, the npc's name will be used.
 */
suspend fun QueueTask.chatNpc(
    player: Player,
    message: String,
    npc: Int = -1,
    animation: String = "sequences.chatneu1",
    title: String? = null,
) {
    val npcId = when {
        npc != -1 -> npc
        else -> player.attr[INTERACTING_NPC_ATTR]?.get()?.getTransform(player)
            ?: error("Npc id must be manually set because the player is not interacting with an NPC.")
    }

    val dialogTitle = title ?: getNpc(npcId)?.name.orEmpty()
    DialogNpcOpen(message = message, npc = npcId,animation = animation, title = dialogTitle, player = player).post()
    terminateAction = closeDialog(player)
    waitReturnValue()
    terminateAction?.invoke(this)
}

/**
 * Send a dialog with your player's head model.
 *
 * @param message
 * The message to render on the dialog box.
*/

suspend fun QueueTask.chatPlayer(
    player: Player,
    message: String,
    animation: String = "sequences.chatneu1",
    title: String? = null,
) {
    val dialogTitle = title ?: player.username
    DialogPlayerOpen(message,animation,dialogTitle,player).post()
    terminateAction = closeDialog(player)
    waitReturnValue()
    terminateAction!!(this)
}

/**
 * Sends a single item dialog.
 *
 * @param message
 * The message to render on the dialog box.
 *
 * @param item
 * The id of the item to show on the dialog.
 *
 * @param amountOrZoom
 * The amount or zoom of the item to show on the dialog.
 *
 * @param options
 * Item dialog boxes can have multiple options be shown instead of the default
 * 'Click here to continue'.
 */
suspend fun QueueTask.itemMessageBox(
    player: Player,
    message: String,
    item: String,
    amountOrZoom: Int = 1,
    vararg options: String = arrayOf("Click here to continue"),
) {
    DialogItem(message,item.asRSCM(),amountOrZoom,player)

    terminateAction = closeDialog(player)
    waitReturnValue()
    terminateAction!!(this)
}

suspend fun QueueTask.doubleItemMessageBox(
    player: Player,
    message: String,
    item1: String,
    item2: String,
    amount1: Int = 1,
    amount2: Int = 1,
) {
    DialogItemDouble(message, item1.asRSCM(), item2.asRSCM(), amount1, amount2,player).post()

    terminateAction = closeDialog(player)
    waitReturnValue()
    terminateAction!!(this)
}


/**
 * Prompts the player with skill menu for making things.
 *
 * @param items
 * The possible product item ids the menu presents as options.
 * This UI supports up to 18 items (components a..r). Extra items are ignored.
 *
 * @param title
 * Title string to display atop the prompt.
 *
 * @param maxProducable
 * The possible number of products which could be made from the available input mats.
 * Note| defaults to full inventory as being possible
 *
 * @param logic
 * Callback invoked with `(selectedItemId, quantity)` after the player responds.
 *
 * ## ClientScript contract
 * This function is a server-side wrapper around the clientscript `skillmulti_setup`.
 * The reference dump for the script (signature + behaviour) is:
 * https://github.com/Joshua-F/osrs-dumps/blob/ef7ba91167f84b05792373056ad4c9d374041394/script/%5Bclientscript%2Cskillmulti_setup%5D.cs2
 *
 * The script expects arguments in this shape:
 * - `int0`: mode/type (we pass `0`)
 * - `string0`: payload formatted as `Title|Name1|Name2|...`
 * - `int1`: max producible (used for quantity button setup/clamping)
 * - `obj2..obj19`: up to 18 item ids; unused obj slots should be sent as `-1` (treated as null obj)
 * - `int20`: suggested quantity
 *
 * If the argument count/order is wrong, the interface can open but appear blank.
 *
 * ## Ordering / race avoidance
 * The interface must be opened client-side before `skillmulti_setup` runs. Since dialog open events
 * are posted through an async event bus, we open the interface synchronously (`postAndWait`) to
 * avoid races where the script runs before the modal exists.
 *
 * ## Selection mapping
 * Mapping from the incoming resume packet (`ResumePauseButton.componentId`) to an item index is
 * revision-dependent. The current implementation assumes item components start at child id `15`.
 *
 * @return
 * The selected item id and quantity are delivered to [logic].
 */
suspend fun QueueTask.produceItemBox(
    player: Player,
    vararg items: Int,
    title: String = "What would you like to make?",
    maxProducable: Int = player.inventory.size,
    logic: Player.(Int, Int) -> Unit,
) {

    // `skillmulti_setup` (CS2) takes 18 obj slots (obj2..obj19) and a trailing suggested quantity (int20).
    // If we call it with the wrong argument count/order, the clientscript can fail and the interface appears blank.
    val maxSelectable = 18
    val scriptSlots = 18

    val displayedItems = items.take(maxSelectable)
    val displayedDefs = displayedItems.mapNotNull { itemId -> getItem(itemId)?.let { def -> itemId to def } }
    if (displayedDefs.isEmpty()) {
        player.message("You can't think of any options.")
        return
    }

    val baseChild = 15
    val itemArray = Array(scriptSlots) { -1 }
    displayedDefs.withIndex().forEach { (index, pair) ->
        itemArray[index] = pair.second.id
    }

    val nameString = buildString {
        append(title)
        displayedDefs.forEach { (_, def) ->
            append("|")
            append(def.name)
        }
    }

    val suggestedQuantity = 1

    player.runClientScript(CommonClientScripts.CHATBOX_RESET_BACKGROUND)
    player.sendTempVarbit("varbits.chatmodal_unclamp", 1)

    // Open the interface synchronously; the async event bus can otherwise race and
    // run the clientscript before the interface exists client-side.
    EventManager.postAndWait(DialogSkillMulti(player))

    player.runClientScript(
        CommonClientScripts.SKILL_MULTI_SETUP,
        0,
        nameString,
        maxProducable,
        *itemArray,
        suggestedQuantity
    )

    terminateAction = closeDialog(player)
    waitReturnValue()
    terminateAction!!(this)

    val msg = requestReturnValue as? ResumePauseButton ?: return

    val child = msg.componentId

    if (child < baseChild || child >= baseChild + displayedDefs.size) {
        return
    }

    val item = displayedDefs[child - baseChild].first
    val qty = msg.sub

    logic(player, item, qty)
}
