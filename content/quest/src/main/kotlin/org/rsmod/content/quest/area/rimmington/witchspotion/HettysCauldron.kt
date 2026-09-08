package org.rsmod.content.quest.area.rimmington.witchspotion

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.quest.area.rimmington.witchspotion.WitchsPotionQuest.Companion.STAGE_BREWED
import org.rsmod.content.quest.area.rimmington.witchspotion.WitchsPotionQuest.Companion.STAGE_COMPLETE
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Hetty's cauldron: the potion is drunk straight from it once she has brewed it. */
class HettysCauldron @Inject constructor(private val witchsPotion: WitchsPotionQuest) : PluginScript() {

    private val quest
        get() = witchsPotion.quest

    override fun ScriptContext.startup() {
        onOpLoc1(CAULDRON) { drink(it.loc) }
    }

    private suspend fun ProtectedAccess.drink(loc: BoundLocInfo) {
        arriveDelay()
        faceLoc(loc)
        when (witchsPotion.stage(player)) {
            STAGE_BREWED -> {
                anim(DRINK_SEQ)
                soundSynth("synth.drink")
                delay(2)
                mesbox("You drink from the cauldron. It tastes horrible! You feel yourself imbued with power.")
                quest.advanceQuestStage(this)
            }
            STAGE_COMPLETE -> mes("You've had quite enough of that potion for one lifetime.")
            else -> startDialogue { chatPlayer(bored, "As nice as that looks, I think I'll give it a miss for now.") }
        }
    }

    private companion object {
        const val CAULDRON = "loc.hettycauldron"
        const val DRINK_SEQ = "seq.human_eat"
    }
}
