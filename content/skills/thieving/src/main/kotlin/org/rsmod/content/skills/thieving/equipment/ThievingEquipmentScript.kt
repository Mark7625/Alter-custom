package org.rsmod.content.skills.thieving.equipment

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeld3
import org.rsmod.api.script.onOpHeld4
import org.rsmod.api.script.onOpWorn1
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Inventory and worn `Check` / `Break` options on gloves of silence and the dodgy necklace. */
class ThievingEquipmentScript @Inject constructor(private val equipment: ThievingEquipment) :
    PluginScript() {

    override fun ScriptContext.startup() {
        onOpHeld3(ThievingEquipment.GLOVES_OF_SILENCE) { equipment.checkGloves(this) }
        onOpWorn1(ThievingEquipment.GLOVES_OF_SILENCE) { equipment.checkGloves(this) }

        onOpHeld3(ThievingEquipment.DODGY_NECKLACE) { equipment.checkNecklace(this) }
        onOpWorn1(ThievingEquipment.DODGY_NECKLACE) { equipment.checkNecklace(this) }
        onOpHeld4(ThievingEquipment.DODGY_NECKLACE) { confirmBreak(it.slot) }
    }

    private suspend fun ProtectedAccess.confirmBreak(slot: Int) {
        startDialogue {
            val choice = choice2("Yes, break it.", 1, "No, keep it.", 2)
            if (choice == 1) {
                equipment.breakNecklace(access, slot)
            }
        }
    }
}
