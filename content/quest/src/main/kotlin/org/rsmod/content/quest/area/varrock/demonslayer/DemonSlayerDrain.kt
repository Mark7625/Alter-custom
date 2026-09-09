package org.rsmod.content.quest.area.varrock.demonslayer

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.content.quest.area.varrock.demonslayer.DemonSlayerQuest.Companion.KEY_DRAIN
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Sir Prysin's key: stuck in the drain outside the palace kitchen until water is poured down it,
 * then lying in the mud of the Varrock sewers. The drain and mud locs are cache multilocs driven
 * by `varbit.delrith_drain_key`, so each player sees the state matching their own progress.
 *
 * Also lets the palace kitchen sink fill water containers, since the quest expects it to.
 */
class DemonSlayerDrain @Inject constructor(private val demonSlayer: DemonSlayerQuest) : PluginScript() {

    private val quest
        get() = demonSlayer.quest

    override fun ScriptContext.startup() {
        onOpLoc1(DRAIN_WITH_KEY) { searchDrain() }
        onOpLoc1(DRAIN_NO_KEY) { searchDrain() }
        for (drain in listOf(DRAIN_WITH_KEY, DRAIN_NO_KEY)) {
            for ((full, _) in WATER_CONTAINERS) {
                onOpLocU(drain, full) { pourWater(full) }
            }
        }
        onOpLoc1(SEWER_KEY) { takeSewerKey() }
        onOpLoc1(SEWER_PIPE) { mes("A pipe runs down from the palace above. Anything dropped in the drain up there ends up around here.") }
        for ((full, empty) in WATER_CONTAINERS) {
            onOpLocU(KITCHEN_SINK, empty) { fillContainer(empty, full) }
        }
    }

    private suspend fun ProtectedAccess.searchDrain() {
        if (quest.isQuestInProgress(player) && demonSlayer.drainKey.get(player) == 0) {
            startDialogue {
                chatPlayer(quiz, "That must be the key Sir Prysin dropped.")
                chatPlayer(neutral, "I can't quite reach it. If I could dislodge it somehow it might wash down into the sewers, where I could pick it up.")
            }
            return
        }
        mes("Nothing interesting seems to have been dropped down here today.")
    }

    private suspend fun ProtectedAccess.pourWater(container: String) {
        val empty = WATER_CONTAINERS.first { it.first == container }.second
        anim("seq.human_pickuptable")
        soundSynth("synth.waterstrike_hit")
        invReplace(inv, container, 1, empty)
        if (quest.isQuestInProgress(player) && demonSlayer.drainKey.get(player) == 0) {
            demonSlayer.drainKey.set(player, 1)
            demonSlayer.syncVars(player)
            soundSynth("synth.coins_jingle_1", delay = 20)
            mes("You pour the water down the drain and hear a faint clink from below.")
            startDialogue {
                chatPlayer(happy, "That should have washed the key down into the sewer. I'd better go down and fetch it!")
            }
            return
        }
        mes("You pour the water down the drain.")
    }

    private suspend fun ProtectedAccess.takeSewerKey() {
        if (demonSlayer.drainKey.get(player) != 1) {
            mes("There is nothing here but mud.")
            return
        }
        if (inv.freeSpace() < 1) {
            mes("You don't have enough room in your pack to pick up the key.")
            return
        }
        anim("seq.human_pickupfloor")
        soundSynth("synth.pick2")
        delay(1)
        invAdd(inv, KEY_DRAIN)
        demonSlayer.drainKey.set(player, 2)
        demonSlayer.syncVars(player)
        objbox(KEY_DRAIN, "You pick up an old rusty key.")
    }

    private suspend fun ProtectedAccess.fillContainer(empty: String, full: String) {
        anim("seq.human_pickuptable")
        soundSynth("synth.waterstrike_hit")
        delay(1)
        invReplace(inv, empty, 1, full)
        val container = empty.removePrefix("obj.").substringBefore("_")
        mes("You fill the $container with water from the sink.")
    }

    private companion object {
        const val DRAIN_WITH_KEY = "loc.qip_ds_questdrain_key"
        const val DRAIN_NO_KEY = "loc.qip_ds_questdrain_nokey"
        const val SEWER_KEY = "loc.qip_ds_rustykey_mud"
        const val SEWER_PIPE = "loc.qip_ds_keypipe"
        const val KITCHEN_SINK = "loc.fai_varrock_posh_sink"

        /** Full container to its empty counterpart. */
        val WATER_CONTAINERS =
            listOf(
                "obj.bucket_water" to "obj.bucket_empty",
                "obj.jug_water" to "obj.jug_empty",
                "obj.bowl_water" to "obj.bowl_empty",
                "obj.vial_water" to "obj.vial_empty",
            )
    }
}
