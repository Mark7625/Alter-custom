package org.rsmod.content.skills.prayer

import jakarta.inject.Inject
import org.rsmod.api.player.events.interact.LocUEvents
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.api.table.prayer.SkillPrayerRow
import org.rsmod.content.skills.prayer.items.ZealotRobes.shouldConsume
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class GildedAltarEvents @Inject constructor(
    private val worldRepo: WorldRepository,
) : PluginScript() {

    override fun ScriptContext.startup() {
        val bones = PrayerBuryEvents.bones.filterNot { it.ashes }

        bones.forEach { row ->
            registerAltar("loc.chaosaltar", row, chaos = true)
        }

        onPlayerQueueWithArgs("queue.prayer_altar_sacrifice") {
            processSacrificeTick(it.args)
        }
    }

    private fun ScriptContext.registerAltar(altar: String, row: SkillPrayerRow, chaos: Boolean) {
        onOpLocU(altar, row.item.internalName) {
            startSacrifice(it, row, chaos)
        }
    }

    private fun ProtectedAccess.startSacrifice(
        event: LocUEvents.Op,
        row: SkillPrayerRow,
        chaos: Boolean,
    ) {
        val task = SacrificeTask(row = row, slot = event.invSlot, altar = event.vis, chaos = chaos)

        if (!canSacrifice(task)) {
            return
        }

        stopAction()
        weakQueue("queue.prayer_altar_sacrifice", 1, task)
    }

    private fun ProtectedAccess.processSacrificeTick(task: SacrificeTask) {
        if (!canSacrifice(task)) {
            return
        }

        performSacrifice(task)

        weakQueue("queue.prayer_altar_sacrifice", 4, task)
    }

    private fun ProtectedAccess.performSacrifice(task: SacrificeTask) {
        anim("seq.human_bone_sacrifice")

        spotanimMap(
            worldRepo,
            "spotanim.poh_bone_sacrifice",
            task.altar.coords,
        )

        statAdvance("stat.prayer", task.row.exp * 3.5)

        if (shouldConsumeBone(task).not()) {
            mes("The Dark Lord spares your sacrifice, but rewards you for your efforts.")
            return
        }

        val result = invDel(inv = inv, type = task.row.item.internalName)

        if (result.failure) {
            return
        }
    }

    private fun ProtectedAccess.canSacrifice(task: SacrificeTask): Boolean {
        val bone = task.row.item.internalName

        return when {
            inv.count(bone) <= 0 -> false
            !isWithinDistance(task.altar, 1) -> false
            task.chaos && !inArea("area.chaos_temple", player.coords) -> false
            else -> true
        }
    }

    private fun ProtectedAccess.shouldConsumeBone(task: SacrificeTask): Boolean {
        val baseConsumeChance = if (task.chaos) 0.5 else 1.0
        return player.shouldConsume(baseConsumeChance)
    }

    private data class SacrificeTask(
        val row: SkillPrayerRow,
        val slot: Int,
        val altar: BoundLocInfo,
        val chaos: Boolean,
    )
}
