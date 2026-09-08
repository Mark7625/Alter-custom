package org.rsmod.content.quest.area.lumbridge.restlessghost

import dev.openrune.types.hunt.HuntVis
import jakarta.inject.Inject
import org.rsmod.api.hunt.NpcSearch
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeld5
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLocU
import org.rsmod.content.quest.area.lumbridge.restlessghost.RestlessGhostQuest.Companion.GHOST_SKULL
import org.rsmod.content.quest.area.lumbridge.restlessghost.RestlessGhostQuest.Companion.STAGE_GOT_SKULL
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The ghost's coffin in the Lumbridge graveyard, and the skull that belongs in it.
 *
 * The shut coffin is a static map loc; opening it swaps in the open coffin loc for a while
 * (everyone in the graveyard sees it open, as in the original). The open coffin is a cache
 * multiloc that shows a complete skeleton once `varbit.restless_ghost_coffin_var` is set, which
 * happens when the player returns the skull.
 */
class GhostCoffin
@Inject
constructor(
    private val restlessGhost: RestlessGhostQuest,
    private val locRepo: LocRepository,
    private val npcRepo: NpcRepository,
    private val search: NpcSearch,
) : PluginScript() {

    private val quest
        get() = restlessGhost.quest

    override fun ScriptContext.startup() {
        onOpLoc1(SHUT_COFFIN) { open(it.loc) }
        onOpLocU(SHUT_COFFIN, GHOST_SKULL) { mesbox("Maybe I should open it first.") }
        for (open in OPEN_COFFINS) {
            onOpLoc1(open) { searchCoffin() }
            onOpLoc2(open) { close(it.loc) }
            onOpLocU(open, GHOST_SKULL) { returnSkull(it.loc, it.invSlot) }
        }
        onOpHeld1(GHOST_SKULL) { inspectSkull() }
        onOpHeld5(GHOST_SKULL) { dropSkull() }
    }

    private suspend fun ProtectedAccess.open(loc: BoundLocInfo) {
        arriveDelay()
        faceLoc(loc)
        anim(OPEN_SEQ)
        soundSynth("synth.coffin_open")
        locRepo.add(loc.coords, OPEN_COFFIN, OPEN_TICKS, loc.angle(), loc.shape())
        mes("You open the coffin.")
    }

    private suspend fun ProtectedAccess.close(loc: BoundLocInfo) {
        arriveDelay()
        faceLoc(loc)
        anim(OPEN_SEQ)
        soundSynth("synth.coffin_close")
        // Re-adding the map's own loc cancels the timed open coffin.
        locRepo.add(loc.coords, SHUT_COFFIN, Int.MAX_VALUE, loc.angle(), loc.shape())
        mes("You close the coffin.")
    }

    private suspend fun ProtectedAccess.searchCoffin() {
        arriveDelay()
        anim("seq.human_pickuptable")
        if (player.rgCoffinHasHead) {
            mesbox("There's a nice and complete skeleton in here!")
        } else {
            mesbox("There's a skeleton without a skull in here.")
        }
    }

    /** The skull goes back where it belongs and the ghost is released. */
    private suspend fun ProtectedAccess.returnSkull(loc: BoundLocInfo, slot: Int) {
        arriveDelay()
        faceLoc(loc)
        if (restlessGhost.stage(player) != STAGE_GOT_SKULL) {
            mesbox("That doesn't belong in there.")
            return
        }
        if (invDel(inv, GHOST_SKULL, 1, slot = slot).failure) {
            return
        }
        anim("seq.human_pickuptable")
        player.rgCoffinHasHead = true
        player.rgSkeletonRisen = false
        soundSynth("synth.coffin_close")
        delay(1)

        val ghost = search.find(loc.coords, GHOST, GHOST_SEARCH_RADIUS, HuntVis.Off)
        if (ghost != null) {
            ghost.facePlayer(player)
            ghost.say("Release! Thank you stranger..")
            soundSynth("synth.ghost_disappear")
            delay(3)
            ghost.spotanim(VANISH_SPOT)
            delay(1)
            if (ghost.isSlotAssigned) {
                npcRepo.hide(ghost, GHOST_AWAY_TICKS)
            }
        }
        mesbox("The ghost's spirit drifts away over the river, finally at rest.")
        quest.advanceQuestStage(this)
    }

    private suspend fun ProtectedAccess.inspectSkull() {
        objbox(GHOST_SKULL, "It's the skull of the ghost that is haunting Lumbridge graveyard. Maybe I should return this back to the ghost's coffin.")
    }

    /** The skull is the only one of its kind; dropping it destroys it. */
    private suspend fun ProtectedAccess.dropSkull() {
        val destroy =
            confirmDestroy(
                GHOST_SKULL,
                1,
                "Dropping this skull here will destroy it!",
                "Are you sure you want to drop the skull?",
            )
        if (!destroy) {
            return
        }
        invDel(inv, GHOST_SKULL)
        mes("The skull crumbles to dust as it hits the ground.")
    }

    private fun BoundLocInfo.angle(): LocAngle =
        LocAngle.entries.firstOrNull { it.id == entity.angle } ?: LocAngle.West

    private fun BoundLocInfo.shape(): LocShape =
        LocShape.entries.firstOrNull { it.id == entity.shape } ?: LocShape.CentrepieceStraight

    private companion object {
        const val SHUT_COFFIN = "loc.shutghostcoffin"
        const val OPEN_COFFIN = "loc.openghostcoffin"
        val OPEN_COFFINS = listOf("loc.openghostcoffin_no_head", "loc.openghostcoffin_with_head")
        const val GHOST = "npc.ghostx"

        const val OPEN_SEQ = "seq.human_pickupfloor"
        const val VANISH_SPOT = "spotanim.smokepuff"

        /** How long the coffin stays open once lifted. */
        const val OPEN_TICKS = 100

        /** How long the released ghost stays away before the graveyard has him back. */
        const val GHOST_AWAY_TICKS = 100
        const val GHOST_SEARCH_RADIUS = 10
    }
}
