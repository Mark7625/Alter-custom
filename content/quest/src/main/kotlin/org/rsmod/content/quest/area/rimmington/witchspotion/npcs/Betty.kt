package org.rsmod.content.quest.area.rimmington.witchspotion.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.shops.Shops
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Betty, who runs Betty's Magic Emporium in Port Sarim: runes, hats and eyes of newt. */
class Betty @Inject constructor(private val shops: Shops) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1("npc.betty") { startDialogue(it.npc) { betty(it.npc) } }
        onOpNpc3("npc.betty") { player.openShop(it.npc) }
    }

    private fun Player.openShop(npc: Npc) {
        shops.open(this, npc, SHOP_NAME, SHOP_INV)
    }

    private suspend fun Dialogue.betty(npc: Npc) {
        chatNpc(happy, "Welcome to the magic emporium.")
        when (
            choice2(
                "Can I see your wares?", 1,
                "Sorry, I'm not into magic.", 2,
            )
        ) {
            1 -> {
                chatPlayer(happy, "Can I see your wares?")
                player.openShop(npc)
            }
            2 -> {
                chatPlayer(neutral, "Sorry, I'm not into magic.")
                chatNpc(neutral, "Well, if you see anyone who is, please send them my way.")
            }
        }
    }

    private companion object {
        const val SHOP_NAME = "Betty's Magic Emporium"
        const val SHOP_INV = "inv.magicshop"
    }
}
