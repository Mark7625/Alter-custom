package org.rsmod.content.quest.area.lumbridge.restlessghost

import jakarta.inject.Inject
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.quest.area.lumbridge.restlessghost.RestlessGhostQuest.Companion.GHOST_SKULL
import org.rsmod.content.quest.area.lumbridge.restlessghost.RestlessGhostQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.lumbridge.restlessghost.RestlessGhostQuest.Companion.STAGE_GOT_SKULL
import org.rsmod.content.quest.area.lumbridge.restlessghost.RestlessGhostQuest.Companion.STAGE_SPOKE_TO_GHOST
import org.rsmod.game.entity.Npc
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The warlock's altar in the Wizards' Tower basement. Searching it once the ghost has explained
 * himself yields his skull, at which point the skeleton in the corner rises and attacks. The
 * altar is a cache multiloc that shows the skull until `varbit.restless_ghost_altar_var` is set.
 */
class WarlockAltar
@Inject
constructor(
    private val restlessGhost: RestlessGhostQuest,
    private val npcRepo: NpcRepository,
    private val aiInteractions: AiPlayerInteractions,
) : PluginScript() {

    private val quest
        get() = restlessGhost.quest

    override fun ScriptContext.startup() {
        for (altar in ALTARS) {
            onOpLoc1(altar) { searchAltar(it.loc) }
        }
    }

    private suspend fun ProtectedAccess.searchAltar(loc: BoundLocInfo) {
        arriveDelay()
        faceLoc(loc)
        anim("seq.human_pickuptable")
        delay(1)
        val stage = restlessGhost.stage(player)
        when {
            stage >= STAGE_COMPLETE ->
                mesbox("Surprisingly, this altar is empty. Probably because you took the skull off it earlier.")
            stage < STAGE_SPOKE_TO_GHOST ->
                mes("You search the altar but find nothing of interest.")
            with(restlessGhost) { hasSkullAnywhere() } ->
                mesbox("You already have the Ghost's skull.")
            player.inv.freeSpace() < 1 ->
                mes("You need a free space in your pack to take the skull.")
            else -> takeSkull(loc)
        }
    }

    private suspend fun ProtectedAccess.takeSkull(loc: BoundLocInfo) {
        invAdd(inv, GHOST_SKULL)
        player.rgAltarEmpty = true
        soundSynth("synth.pick2")
        objbox(GHOST_SKULL, "You lift the skull off the altar.")
        if (restlessGhost.stage(player) < STAGE_GOT_SKULL) {
            quest.advanceQuestStage(this)
        }

        mes("The skeleton in the corner suddenly comes to life!")
        player.rgSkeletonRisen = true
        soundSynth("synth.skeleton_resurrect")
        val skeleton = Npc(SKELETON, skeletonTile(loc))
        npcRepo.add(skeleton, SKELETON_TICKS)
        skeleton.facePlayer(player)
        skeleton.opPlayer2(player, aiInteractions)
    }

    /** A free tile near the altar for the skeleton to rise on, preferring the room's corner. */
    private fun ProtectedAccess.skeletonTile(loc: BoundLocInfo): CoordGrid {
        val candidates =
            listOf(
                SKELETON_CORNER,
                loc.coords.translate(-1, 0),
                loc.coords.translate(2, 0),
                loc.coords.translate(0, -1),
                loc.coords.translate(0, 1),
            )
        return candidates.firstOrNull { it != player.coords && !mapBlocked(it) } ?: player.coords
    }

    private companion object {
        val ALTARS = listOf("loc.restless_ghost_altar_skull", "loc.restless_ghost_altar_no_skull")
        const val SKELETON = "npc.skull_skeleton"

        /** Just beside the altar in the basement's corner. */
        val SKELETON_CORNER = CoordGrid(3119, 9565, 0)

        /** How long the risen skeleton hangs around before crumbling again. */
        const val SKELETON_TICKS = 150
    }
}
