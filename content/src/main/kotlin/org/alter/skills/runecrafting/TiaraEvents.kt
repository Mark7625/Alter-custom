package org.alter.skills.runecrafting

import dev.openrune.ServerCacheManager.getItem
import org.alter.api.Skills
import org.alter.api.ext.message
import org.alter.game.model.entity.Player
import org.alter.game.pluginnew.PluginEvent
import org.alter.game.pluginnew.event.impl.ItemOnItemEvent
import org.alter.game.pluginnew.event.impl.ItemOnObject
import org.alter.game.pluginnew.event.impl.onItemOnItem
import org.alter.rscm.RSCM.asRSCM
import org.generated.tables.runecrafting.RunecraftingAltarsRow
import org.generated.tables.runecrafting.RunecraftingTiaraRow

class TiaraEvents : PluginEvent() {

    override fun init() {
        RunecraftingAltarsRow.all().filter { it.tiara != null && it.talisman != null }.forEach { altar ->
            val tiaraId = altar.tiara!!
            val talismanId = altar.talisman!!
            val tiaraDef = RunecraftingTiaraRow.getRow(tiaraId)
            onItemOnItem(altar.talisman,"items.tiara") {
                createTiara(player, talismanId, tiaraDef)
            }
        }
    }
}

fun createTiara(
    player: Player,
    talismanId: Int,
    def: RunecraftingTiaraRow
) {
    val tiaraDef = getItem("items.tiara".asRSCM()) ?: return
    val talismanDef = getItem(talismanId) ?: return
    val inv = player.inventory

    if (!inv.contains(tiaraDef.id)) {
        player.message("You need a ${tiaraDef.name} to bind to your talisman.")
        return
    }

    if (!inv.contains(talismanDef.id)) {
        player.message("You need a ${talismanDef.name} to bind a ${tiaraDef.name} here.")
        return
    }

    val removedTiara = inv.remove(tiaraDef.id).hasSucceeded()
    val removedTalisman = inv.remove(talismanDef.id).hasSucceeded()

    if (removedTiara && removedTalisman) {
        if (inv.add(def.item).hasSucceeded()) {
            player.addXp(Skills.RUNECRAFTING, def.xp)
            player.message("You bind the power of the talisman into your tiara.")
        }
    }
}
