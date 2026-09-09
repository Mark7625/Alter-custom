package org.rsmod.content.quest.area.lumbridge.lostcity

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.prayerLvl
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * The way into and out of the caves beneath Entrana. The cave monk warns anyone about to climb
 * down; the ladder is one way and the evil below drains the player's prayer to nothing. The only
 * exit is the magic door in the south-east, which drops the player deep in the Wilderness.
 */
class EntranaDungeon @Inject constructor(private val collision: CollisionFlagMap) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(LADDER_TOP) { climbDown(it.loc) }
        onOpNpc1(CAVE_MONK) { startDialogue(it.npc) { warning() } }
        onOpLoc1(MAGIC_DOOR) { magicDoor(it.loc) }
    }

    private suspend fun ProtectedAccess.climbDown(ladder: BoundLocInfo) {
        arriveDelay()
        faceLoc(ladder)
        var proceed = false
        startDialogue {
            chatNpcSpecific("Cave monk", CAVE_MONK, worried, WARNING_1)
            chatNpcSpecific("Cave monk", CAVE_MONK, worried, WARNING_2)
            chatNpcSpecific("Cave monk", CAVE_MONK, worried, WARNING_3)
            when (
                choice2(
                    "I don't think I'm strong enough to enter then.", 1,
                    "Well that is a risk I will have to take.", 2,
                )
            ) {
                1 -> chatPlayer(worried, "I don't think I'm strong enough to enter then.")
                2 -> {
                    chatPlayer(neutral, "Well that is a risk I will have to take.")
                    proceed = true
                }
            }
        }
        if (!proceed) {
            return
        }
        anim(CLIMB_DOWN_SEQ)
        delay(2)
        telejump(collision.nearestFree(DUNGEON_ARRIVAL) ?: DUNGEON_ARRIVAL)
        // The evil below cuts the monks' gods off; the prayer drain is the quest's whole reason
        // for warning the player first.
        val prayer = player.prayerLvl
        if (prayer > 0) {
            statSub("stat.prayer", constant = prayer, percent = 0)
        }
    }

    private suspend fun Dialogue.warning() {
        chatNpc(worried, WARNING_1)
        chatNpc(worried, WARNING_2)
        chatNpc(worried, WARNING_3)
        when (
            choice2(
                "I don't think I'm strong enough to enter then.", 1,
                "Well that is a risk I will have to take.", 2,
            )
        ) {
            1 -> chatPlayer(worried, "I don't think I'm strong enough to enter then.")
            2 -> chatPlayer(neutral, "Well that is a risk I will have to take.")
        }
    }

    private suspend fun ProtectedAccess.magicDoor(door: BoundLocInfo) {
        arriveDelay()
        faceLoc(door)
        soundSynth(DOOR_SOUND)
        delay(1)
        telejump(collision.nearestFree(WILDERNESS_EXIT) ?: WILDERNESS_EXIT)
        mes("You step through the magic door and find yourself deep in the Wilderness.")
    }

    private companion object {
        const val LADDER_TOP = "loc.entranaladdertop"
        const val CAVE_MONK = "npc.cave_monk"

        /** Despite the cache name, this is the wilderness door in the Entrana caves. */
        const val MAGIC_DOOR = "loc.zanarismagicdoor"

        const val CLIMB_DOWN_SEQ = "seq.human_reachforladder"
        const val DOOR_SOUND = "synth.door_open"

        /** Foot of the one-way ladder, under the surface ladder at 2820,3374. */
        val DUNGEON_ARRIVAL = CoordGrid(2822, 9774, 0)

        /** Level 32 Wilderness, where the magic door leads. */
        val WILDERNESS_EXIT = CoordGrid(3250, 3772, 0)

        const val WARNING_1 =
            "Be careful going in there! You are unarmed, and there is much evilness lurking " +
                "down there! The evilness seems to block off our contact with our gods,"
        const val WARNING_2 =
            "so our prayers seem to have less effect down there. Oh, also, you won't be able " +
                "to come back this way - This ladder only goes one way!"
        const val WARNING_3 =
            "The only exit from the caves below is a portal which leads only to the deepest " +
                "wilderness!"
    }
}
