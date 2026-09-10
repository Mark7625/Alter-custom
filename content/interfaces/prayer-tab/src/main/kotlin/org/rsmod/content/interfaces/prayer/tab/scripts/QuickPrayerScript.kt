package org.rsmod.content.interfaces.prayer.tab.scripts

import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.types.aconverted.interf.IfButtonOp
import dev.openrune.types.aconverted.interf.IfSubType
import jakarta.inject.Inject
import org.rsmod.api.config.constants
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.overheadProtectionPrayerVarbits
import org.rsmod.api.player.overheadsLocked
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.stat.prayerLvl
import org.rsmod.api.player.ui.ifClose
import org.rsmod.api.player.ui.ifCloseOverlay
import org.rsmod.api.player.ui.ifOpenSub
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.player.vars.resyncVar
import org.rsmod.api.script.onIfClose
import org.rsmod.api.script.onIfOpen
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerQueue
import org.rsmod.content.interfaces.prayer.tab.Prayer
import org.rsmod.content.interfaces.prayer.tab.PrayerRepository
import org.rsmod.content.interfaces.prayer.tab.util.disablePrayerDrain
import org.rsmod.content.interfaces.prayer.tab.util.disablePrayerStatRegen
import org.rsmod.content.interfaces.prayer.tab.util.enablePrayerDrain
import org.rsmod.content.interfaces.prayer.tab.util.enablePrayerStatRegen
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class QuickPrayerScript
@Inject
constructor(
    private val repo: PrayerRepository,
    private val eventBus: EventBus,
    private val protectedAccess: ProtectedAccessLauncher,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onPlayerLogin { player.disableQuickPrayers() }
        onIfOverlayButton("component.orbs:prayerbutton") { player.selectQuickPrayerOrb(it.op) }
        onPlayerQueue("queue.quick_prayer_toggle") { toggleQuickPrayers() }
        onIfOpen("interface.quickprayer") { player.onOpenQuickPrayerSetUp() }
        onIfClose("interface.quickprayer") { player.onCloseQuickPrayerSetUp() }
        onIfOverlayButton("component.quickprayer:buttons") {
            player.toggleQuickPrayer(it.comsub)
        }
        onIfOverlayButton("component.quickprayer:close") { player.closeQuickPrayerSetUp() }
    }

    private fun Player.disableQuickPrayers() {
        usingQuickPrayers = false
    }

    private fun Player.selectQuickPrayerOrb(op: IfButtonOp) {
        if (op == IfButtonOp.Op1) {
            selectToggleQuickPrayers()
        } else if (op == IfButtonOp.Op2) {
            selectSetUpQuickPrayers()
        }
    }

    private fun Player.selectToggleQuickPrayers() {
        if ("queue.quick_prayer_toggle" in queueList) {
            return
        }
        ifClose(eventBus)
        val toggled = protectedAccess.launch(this) { toggleQuickPrayers() }
        if (!toggled) {
            strongQueue("queue.quick_prayer_toggle", 1)
        }
    }

    private fun ProtectedAccess.toggleQuickPrayers() {
        if (vars["varbit.quickprayer_active"] != 0) {
            disableQuickPrayers()
        } else {
            enableQuickPrayers()
        }
    }

    private fun ProtectedAccess.enableQuickPrayers() {
        val quickPrayerVars = vars["varbit.quickprayer_selected"]

        if (quickPrayerVars == 0) {
            mes("You haven't selected any quick-prayers.")
            player.resyncVar("varbit.quickprayer_active")
            return
        }

        if (player.prayerLvl == 0) {
            mes("You've run out of prayer points.")
            player.resyncVar("varbit.quickprayer_active")
            return
        }

        val quickPrayers = repo.toPrayerList(quickPrayerVars)
        val lockedProtection =
            player.overheadsLocked &&
                quickPrayers.any { it.enabled in overheadProtectionPrayerVarbits }
        val grantedPrayers =
            if (lockedProtection) {
                quickPrayers.filter { it.enabled !in overheadProtectionPrayerVarbits }
            } else {
                quickPrayers
            }

        if (grantedPrayers.isEmpty()) {
            mes("You've been injured and can't use protection prayers!")
            soundSynth("synth.prayer_disable")
            player.resyncVar("varbit.quickprayer_active")
            return
        }

        val enabledPrayers = repo.toPrayerList(vars["varbit.prayer_allactive"])
        for (prayer in enabledPrayers) {
            disablePrayerStatRegen(prayer)
        }
        disableOverhead()
        vars["varbit.prayer_allactive"] = grantedPrayers.fold(0) { acc, p -> acc or (1 shl p.id) }
        vars["varbit.quickprayer_active"] = 1

        for (prayer in grantedPrayers) {
            vars[prayer.enabled] = 1
            soundSynth(prayer.sound)
            enablePrayerStatRegen(prayer)
            if (prayer.overhead != null) {
                player.overheadIcon = prayer.overhead
            }
        }
        if (lockedProtection) {
            mes("You've been injured and can't use protection prayers!")
            soundSynth("synth.prayer_disable")
        }
        enablePrayerDrain()
    }

    private fun ProtectedAccess.disableQuickPrayers() {
        val enabledPrayers = repo.toPrayerList(vars["varbit.prayer_allactive"])
        for (prayer in enabledPrayers) {
            disablePrayerStatRegen(prayer)
        }
        disablePrayerDrain()
        disableOverhead()
        vars["varbit.prayer_allactive"] = 0
        vars["varbit.quickprayer_active"] = 0
        soundSynth("synth.prayer_disable")
    }

    private fun ProtectedAccess.disableOverhead() {
        val overhead = player.overheadIcon ?: return
        if (constants.isOverhead(overhead)) {
            player.overheadIcon = null
        }
    }

    private fun Player.selectSetUpQuickPrayers() {
        val setUp = protectedAccess.launch(this) { setUpQuickPrayers() }
        if (!setUp) {
            mes("You can't set up your prayers at the moment.")
        }
    }

    private fun ProtectedAccess.setUpQuickPrayers() {
        ifOpenOverlay("interface.quickprayer", "component.toplevel_osrs_stretch:side5")
        toplevelSidebuttonSwitch(constants.toplevel_prayer)
    }

    private fun Player.toggleQuickPrayer(comsub: Int) {
        val prayer = repo.prayerList.getOrNull(comsub)
        if (prayer == null) {
            throw IllegalArgumentException("Invalid quick prayer comsub: $comsub")
        }
        val enabled = selectedQuickPrayers and (1 shl prayer.id) != 0
        if (enabled) {
            disableQuickPrayer(prayer)
        } else {
            enableQuickPrayer(prayer)
        }
    }

    private fun Player.enableQuickPrayer(prayer: Prayer) {
        if (!prayer.hasAllRequirements(this)) {
            val message = failedRequirementMessage(prayer)
            mes(message)
            return
        }
        disableCollisions(prayer)
        selectedQuickPrayers = selectedQuickPrayers or (1 shl prayer.id)
    }

    private fun Player.disableQuickPrayer(prayer: Prayer) {
        selectedQuickPrayers = selectedQuickPrayers and (1 shl prayer.id).inv()
    }

    private fun Player.disableCollisions(collisions: Iterable<Prayer>) {
        for (collision in collisions) {
            selectedQuickPrayers = selectedQuickPrayers and (1 shl collision.id).inv()
        }
    }

    private fun Player.disableCollisions(prayer: Prayer) = disableCollisions(repo[prayer])

    private fun Player.onOpenQuickPrayerSetUp() {
        ifSetEvents("component.quickprayer:buttons", repo.prayerList.indices, IfEvent.Op1)
    }

    private fun Player.closeQuickPrayerSetUp() {
        ifCloseOverlay("interface.quickprayer", eventBus)
    }

    private fun Player.onCloseQuickPrayerSetUp() {
        ifOpenSub(
            "interface.prayerbook",
            "component.toplevel_osrs_stretch:side5",
            IfSubType.Overlay,
            eventBus,
        )
    }

    private fun Player.failedRequirementMessage(prayer: Prayer): String =
        if (prayer.hasBaseRequirement(this)) {
            prayer.lockedMessage()
        } else {
            prayer.levelReqMessage()
        }

    private fun Prayer.levelReqMessage(): String = "You need a Prayer level of $level to use $name."

    private fun Prayer.lockedMessage(): String = plainLockedMessage ?: levelReqMessage()
}

private var Player.selectedQuickPrayers by intVarBit("varbit.quickprayer_selected")
private var Player.usingQuickPrayers by boolVarBit("varbit.quickprayer_active")
