package org.rsmod.content.quest.area.lumbridge.lostcity

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The shed in the middle of Lumbridge Swamp. Its door is the portal to Zanaris: anyone opening
 * it while wielding a Dramen or Lunar staff is carried off by fairy magic to the ring at the
 * Zanaris entrance, and that ring brings them back into the shed. Without a staff it is just a
 * shed door.
 */
class ZanarisShed @Inject constructor(
    private val lostCity: LostCityQuest,
    private val locRepo: LocRepository,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(SHED_DOOR) { openDoor(it.loc) }
        onOpLoc1(ENTRY_RING) { leaveZanaris() }
    }

    private suspend fun ProtectedAccess.openDoor(door: BoundLocInfo) {
        arriveDelay()
        faceLoc(door)
        if (lostCity.wieldsDramenStaff(player)) {
            enterZanaris()
            return
        }
        soundSynth(DOOR_OPEN_SOUND)
        val opened = openedCoords(door)
        locRepo.del(door, DOOR_TICKS)
        locRepo.add(opened, SHED_DOOR_OPEN, DOOR_TICKS, door.turnAngle(1), door.shape)
    }

    /** The same swing the generic single-door script gives a straight wall door. */
    private fun openedCoords(door: BoundLocInfo): CoordGrid =
        when {
            door.shape == LocShape.WallStraight && door.angle == LocAngle.West -> door.coords.translateX(-1)
            door.shape == LocShape.WallStraight && door.angle == LocAngle.North -> door.coords.translateZ(1)
            door.shape == LocShape.WallStraight && door.angle == LocAngle.East -> door.coords.translateX(1)
            door.shape == LocShape.WallStraight && door.angle == LocAngle.South -> door.coords.translateZ(-1)
            else -> door.coords
        }

    /** The door swings open and the fairy magic in the staff whisks the player away. */
    private suspend fun ProtectedAccess.enterZanaris() {
        soundSynth(DOOR_OPEN_SOUND)
        delay(1)
        fairyVanish()
        telejump(ZANARIS_ARRIVAL)
        anim(APPEAR_ANIM)
        if (lostCity.quest.isQuestInProgress(player)) {
            lostCity.complete(this)
        }
    }

    /** The ring at the Zanaris entrance is the way back out to the swamp. */
    private suspend fun ProtectedAccess.leaveZanaris() {
        if (!lostCity.wieldsDramenStaff(player)) {
            mes("The fairy rings only work for those who wield fairy magic.")
            return
        }
        fairyVanish()
        telejump(SHED_INSIDE)
        anim(APPEAR_ANIM)
    }

    /** The same send-off the fairy rings give: the vanish pose, a ring of flowers and the chime. */
    private suspend fun ProtectedAccess.fairyVanish() {
        anim(VANISH_ANIM)
        spotanim(FLOWER_RING)
        soundSynth(TELEPORT_SOUND)
        delay(VANISH_TICKS)
    }

    private companion object {
        const val SHED_DOOR = "loc.zanarisdoor"
        const val SHED_DOOR_OPEN = "loc.zanarisdoor_open"
        const val ENTRY_RING = "loc.fairy_mushroom_ring"

        const val DOOR_OPEN_SOUND = "synth.door_open"

        /** How long the opened door stays open before swinging shut on its own. */
        const val DOOR_TICKS = 500

        /** The fairy ring just inside Zanaris, where the shed portal lets players out. */
        val ZANARIS_ARRIVAL = CoordGrid(2452, 4473, 0)

        /** Inside the swamp shed, where the entrance ring drops players off. */
        val SHED_INSIDE = CoordGrid(3203, 3169, 0)

        const val VANISH_ANIM = "seq.human_fairy_vanish"
        const val APPEAR_ANIM = "seq.human_fairy_appear"
        const val FLOWER_RING = "spotanim.fairy_flower_ring"
        const val TELEPORT_SOUND = "synth.fairy_teleport"
        const val VANISH_TICKS = 2
    }
}
