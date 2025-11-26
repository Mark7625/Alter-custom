package org.alter.skills.runecrafting

import org.alter.api.ext.message
import org.alter.game.model.attr.AttributeKey
import org.alter.game.model.entity.Player
import org.alter.game.pluginnew.PluginEvent
import org.alter.game.pluginnew.event.impl.ItemOnObject
import org.alter.game.pluginnew.event.impl.onItemOption
import org.generated.tables.runecrafting.RunecraftingAltarsRow

class BloodEssenceEvents : PluginEvent() {

    companion object {
        val BLOOD_ESSENCE = AttributeKey<Int>()
    }

    override fun init() {
        onItemOption("items.blood_essence_inactive","activate") {
            val alreadyHave = player.bank.contains("items.blood_essence_active") || player.inventory.contains("items.blood_essence_active")
            if (alreadyHave) {
                player.message("You can only have one active blood essence at a time.")
            } else {
                if (player.inventory.remove("items.blood_essence_inactive").hasSucceeded()) {
                    if (player.inventory.add("items.blood_essence_active").hasSucceeded()) {
                        player.attr[BLOOD_ESSENCE] = 1000
                        player.message("You activate the blood essence.")
                    } else {
                        player.inventory.add("items.blood_essence_inactive")
                    }
                }
            }
        }

        onItemOption("items.blood_essence_active","check") {
            val charges = player.attr[BLOOD_ESSENCE]?: 0
            player.message("Your blood essence has $charges ${if (charges != 1) "charges" else "charge"} remaining.")
        }
    }
}