package org.rsmod.api.player

import dev.openrune.types.InvScope
import org.rsmod.annotations.InternalApi
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.config.constants
import org.rsmod.api.enums.NamedEnums
import org.rsmod.api.player.output.UpdateInventory
import org.rsmod.api.player.output.clearMapFlag
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.vars.enabledPrayers
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.player.vars.prayerDrainCounter
import org.rsmod.api.player.vars.usingQuickPrayers
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.Inventory

public fun Player.forceDisconnect() {
    forceDisconnect = true
}

public fun Player.clearInteractionRoute() {
    clearInteraction()
    abortRoute()
    clearMapFlag()
}

public fun Player.queueDeath() {
    queue("queue.death", 1)
}

public fun Player.combatClearQueue() {
    clearQueue("queue.com_retaliate_npc")
    clearQueue("queue.com_retaliate_player")
    clearQueue("queue.hit")
    clearQueue("queue.impact_hit")
}

public fun Player.hasProtectItemPrayer(): Boolean = vars["varbit.prayer_protectitem"] == 1

private var Player.overheadLockExpiration by intVarp("varp.overhead_lock_expiration")

private var Player.protectFromMelee by intVarBit("varbit.prayer_protectfrommelee")
private var Player.protectFromMissiles by intVarBit("varbit.prayer_protectfrommissiles")
private var Player.protectFromMagic by intVarBit("varbit.prayer_protectfrommagic")
private var Player.quickPrayerActive by intVarBit("varbit.quickprayer_active")

public val overheadProtectionPrayerVarbits: List<String> =
    listOf(
        "varbit.prayer_protectfrommelee",
        "varbit.prayer_protectfrommissiles",
        "varbit.prayer_protectfrommagic",
    )

public fun Player.lockOverheads(cycles: Int) {
    require(cycles > 0) { "`cycles` must be greater than 0. (cycles=$cycles)" }

    val hadProtection = protectFromMelee != 0 || protectFromMissiles != 0 || protectFromMagic != 0
    protectFromMelee = 0
    protectFromMissiles = 0
    protectFromMagic = 0
    if (hadProtection) {
        if (constants.isOverhead(appearance.overheadIcon)) {
            appearance.overheadIcon = null
        }
        if (enabledPrayers == 0) {
            quickPrayerActive = 0
            prayerDrainCounter = 0
            clearSoftTimer("timer.prayer_drain")
        }
    }

    overheadLockExpiration = currentMapClock + cycles
}

public val Player.overheadsLocked: Boolean
    get() = currentMapClock < overheadLockExpiration

public fun Player.disablePrayers() {
    enabledPrayers = 0
    prayerDrainCounter = 0

    if (usingQuickPrayers) {
        usingQuickPrayers = false
    }

    if (constants.isOverhead(appearance.overheadIcon)) {
        appearance.overheadIcon = null
    }

    clearQueue("queue.preserve_activation")
    clearSoftTimer("timer.prayer_drain")
    clearSoftTimer("timer.rapidrestore_regen")
}

public fun Player.deathResetTimers() {
    softTimer("timer.stat_regen", constants.stat_regen_interval)
    softTimer("timer.stat_boost_restore", constants.stat_boost_restore_interval)
    softTimer("timer.health_regen", constants.health_regen_interval)

    // Note: RL regeneration meter plugin does not reset on death. This can lead to de-sync, but
    // it is (currently) the official behavior.
    softTimer("timer.spec_regen", constants.spec_regen_interval)
}

public fun Player.isValidTarget(): Boolean {
    val isLoggingOut = pendingLogout || loggingOut
    if (isLoggingOut) {
        return false
    }
    return isSlotAssigned && isVisible && hitpoints > 0
}

public fun Player.isOutOfCombat(): Boolean = !isInCombat()

public fun Player.isInCombat(): Boolean = isInPvpCombat() || isInPvnCombat()

public fun Player.isInPvpCombat(): Boolean {
    return vars["varp.lastcombat_pvp"] + constants.combat_activecombat_delay >= currentMapClock
}

public fun Player.isInPvnCombat(): Boolean {
    return vars["varp.lastcombat"] + constants.combat_activecombat_delay >= currentMapClock
}

public fun Player.subjectPronoun(): String {
    appearance.pronoun = vars["varbit.settings_transmit_pronouns"]
    return appearance.subjectPronoun()
}

/** @return `true` if the player is **currently** in a multi-combat area. */
public fun Player.mapMultiway(checker: AreaChecker): Boolean {
    return checker.inArea("area.multiway", coords)
}

/**
 * Selects the proper hexadecimal color based on the player's client mode and chatbox transparency.
 *
 * _Note: The returned value is automatically wrapped with the `<col=>` tag._
 *
 * @param opaque The hexadecimal color (6 characters, no # symbol) to use when chatbox transparency
 *   is disabled.
 * @param transparent The hexadecimal color (6 characters, no # symbol) to use when chatbox
 *   transparency is enabled.
 * @return The transparent color if the client is in resizable mode and chatbox transparency is
 *   enabled; the opaque color otherwise.
 * @throws IllegalArgumentException if either [opaque] or [transparent] is not exactly 6 characters.
 */
public fun Player.chatMesColorTag(opaque: String, transparent: String): String {
    return "<col=${chatMesColor(opaque, transparent)}>"
}

private fun Player.chatMesColor(opaque: String, transparent: String): String {
    require(opaque.length == 6 && transparent.length == 6) {
        "Color tags must be exactly 6 hexadecimal characters without the # symbol (e.g., 'FF0000')."
    }
    val transparentChatbox = ui.frameResizable && vars["varbit.chatbox_transparency"] == 1
    return if (transparentChatbox) {
        transparent
    } else {
        opaque
    }
}

public fun Player.startInvTransmit(inv: Inventory) {
    check(inv.type.scope != InvScope.Shared || !invMap.contains(inv.internalName)) {
        "`inv` should have previously been removed from cached inv map: $inv"
    }
    /*
     * Reorders the given `inv` in the list of transmitted inventories. This ensures that updates
     * for inventories are sent in the order they were added when this function was called, even if
     * they were first added during login (e.g., `worn` and `inv`).
     *
     * This is done to emulate the behavior observed in os, where the transmitted inventory order
     * can change dynamically. For example, equipping an item will have the update order of `inv`
     * and `worn`. If you open a shop and then equip an item, the new order will be `worn` -> `inv`.
     *
     * This logic guarantees that updates sent from this point onward respect the new order.
     */
    transmittedInvs.remove(inv.type.id)
    transmittedInvAddQueue.add(inv.type.id)
    invMap[inv.internalName] = inv
}

public fun Player.stopInvTransmit(inv: Inventory) {
    if (inv.type.scope == InvScope.Shared) {
        val removed = invMap.remove(inv.internalName)
        check(removed == inv) { "Mismatch with cached value: (cached=$removed, inv=$inv)" }
    }
    transmittedInvs.remove(inv.type.id)
    transmittedInvAddQueue.remove(inv.type.id)
    UpdateInventory.updateInvStopTransmit(this, inv)
}

@OptIn(InternalApi::class)
public fun Player.hasAtLeast99s(requiredCount: Int): Boolean {
    return NamedEnums.skill_names.count { enum ->
        return statMap.getBaseLevel("stat.${enum.value?.lowercase()}") >= 99
    } >= requiredCount
}
