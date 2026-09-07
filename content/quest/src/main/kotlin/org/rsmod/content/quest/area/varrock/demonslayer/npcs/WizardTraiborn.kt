package org.rsmod.content.quest.area.varrock.demonslayer.npcs

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.varrock.demonslayer.DemonSlayerQuest
import org.rsmod.content.quest.area.varrock.demonslayer.DemonSlayerQuest.Companion.BONES_REQUIRED
import org.rsmod.content.quest.area.varrock.demonslayer.DemonSlayerQuest.Companion.KEY_TRAIBORN
import org.rsmod.game.entity.Npc
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Wizard Traiborn, first floor of the Wizards' Tower. Trades his key for 25 sets of bones. */
class WizardTraiborn
@Inject
constructor(
    private val demonSlayer: DemonSlayerQuest,
    private val locRepo: LocRepository,
    private val worldRepo: WorldRepository,
) : PluginScript() {

    private val quest
        get() = demonSlayer.quest

    override fun ScriptContext.startup() {
        onOpNpc1("npc.traiborn") { startDialogue(it.npc) { traiborn(it.npc) } }
    }

    private suspend fun Dialogue.traiborn(npc: Npc) {
        chatNpc(happy, "Ello, young thingummywut.")
        val onQuest = quest.getQuestStage(player) == DemonSlayerQuest.STAGE_STARTED
        if (!onQuest) {
            somethingElse()
            return
        }
        when (
            choice2(
                "Talk about Demon Slayer.", 1,
                "Talk about something else.", 2,
            )
        ) {
            1 -> demonSlayer(npc)
            2 -> somethingElse()
        }
    }

    private suspend fun Dialogue.somethingElse() {
        chatPlayer(quiz, "What are you doing up here?")
        chatNpc(confused, "Doing? Thinking, mostly. Thinking is very tiring, you know. Now, where did I put my hat?")
        chatPlayer(neutral, "It's on your head.")
        chatNpc(happy, "So it is! Marvellous. You're a clever young thingummywut.")
    }

    private suspend fun Dialogue.demonSlayer(npc: Npc) {
        when {
            demonSlayer.traibornKeyGiven.get(player) -> afterKey()
            !demonSlayer.traibornAsked.get(player) -> askAboutKey()
            else -> bones(npc)
        }
    }

    private suspend fun Dialogue.askAboutKey() {
        chatPlayer(neutral, "I need to get a key that Sir Prysin gave you.")
        chatNpc(confused, "Sir Prysin? Who's that? And what would I want with his key?")
        when (
            choice3(
                "He told me you were looking after it for him.", 1,
                "He's one of the King's knights.", 2,
                "Well, have you got any keys knocking around?", 3,
            )
        ) {
            1 -> {
                chatPlayer(neutral, "He told me you were looking after it for him.")
                chatNpc(neutral, "That wasn't very clever of him. I'd lose my own head if it weren't attached. Tell him to find someone else to mind his valuables in future.")
                chatPlayer(neutral, "Okay, I'll tell him that.")
                chatNpc(happy, "Oh, that's very kind, if it isn't too much trouble.")
            }
            2 -> {
                chatPlayer(neutral, "He's one of the King's knights.")
                chatNpc(neutral, "I do remember one of the King's knights. He had lovely shoes... and didn't care for my home-made spinach rolls.")
            }
        }
        chatPlayer(quiz, "Well, have you got any keys knocking around?")
        chatNpc(happy, "Now that you mention it, yes, I do have a key. It's in my special wardrobe of valuable things. Now, how do I get into that again?")
        chatNpc(neutral, "I sealed it with one of my magic rituals, so it stands to reason that another ritual would open it.")
        chatPlayer(quiz, "So do you know which ritual to use?")
        chatNpc(neutral, "Let me think a moment... yes, a simple Drazier-style ritual should do it. The trouble is I'll need $BONES_REQUIRED sets of bones for that. Where would I find such a thing?")
        when (
            choice2(
                "Hmm, that's too bad. I really need that key.", 1,
                "I'll get the bones for you.", 2,
            )
        ) {
            1 -> {
                chatPlayer(sad, "Hmm, that's too bad. I really need that key.")
                chatNpc(sad, "Ah well. Sorry I couldn't be more help.")
                demonSlayer.traibornAsked.set(player, true)
            }
            2 -> {
                chatPlayer(happy, "I'll get the bones for you.")
                chatNpc(happy, "Ooh, that would be very good of you.")
                chatPlayer(neutral, "Okay, I'll speak to you when I've got some bones.")
                demonSlayer.traibornAsked.set(player, true)
            }
        }
    }

    private suspend fun Dialogue.bones(npc: Npc) {
        chatNpc(quiz, "How are you getting on finding those bones?")
        val carried = player.inv.count("obj.bones")
        if (carried <= 0) {
            chatPlayer(sad, "I haven't got any at the moment.")
            chatNpc(neutral, "Never mind, keep at it. I still need ${BONES_REQUIRED - demonSlayer.bonesGiven.get(player)} sets.")
            return
        }
        chatPlayer(happy, "I have some bones.")
        chatNpc(happy, "Give 'em here, then.")
        val needed = BONES_REQUIRED - demonSlayer.bonesGiven.get(player)
        val handing = minOf(carried, needed)
        access.invDel(access.inv, "obj.bones", handing)
        val total = demonSlayer.bonesGiven.get(player) + handing
        demonSlayer.bonesGiven.set(player, total)
        mesbox("You give Traiborn $handing ${if (handing == 1) "set" else "sets"} of bones.")
        if (total < BONES_REQUIRED) {
            chatPlayer(neutral, "That's all of them for now.")
            chatNpc(neutral, "I still need ${BONES_REQUIRED - total} more.")
            chatPlayer(neutral, "Okay, I'll keep looking.")
            return
        }
        ritual(npc)
    }

    /**
     * The Drazier ritual, timed from the sequence lengths: Traiborn casts facing the player while
     * the bones gather at his feet, the wardrobe rises on the tile beside him, he turns and
     * reaches into it, it sinks back into the floor and he turns back before handing the key over.
     */
    private suspend fun Dialogue.ritual(npc: Npc) {
        chatNpc(happy, "Hurrah! That's all $BONES_REQUIRED sets of bones.")
        chatNpc(neutral, "Wings of dark and colour too, spreading in the morning dew; locked away I keep a key; return it now, I bid, to me.")

        npc.facePlayer(player)
        npc.anim(CAST_SEQ)
        npc.spotanim(BONE_SPOT)
        worldRepo.soundArea(npc, "synth.bones_to_bananas_all", radius = SOUND_RADIUS)
        delay(ticks(CAST_SEQ))

        val wardrobeTile = access.wardrobeTile(npc)
        if (wardrobeTile != null) {
            val appear = ticks(APPEAR_SEQ)
            val reach = ticks(REACH_SEQ)
            val disappear = ticks(DISAPPEAR_SEQ)
            val wardrobe =
                locRepo.add(
                    wardrobeTile,
                    WARDROBE_LOC,
                    appear + reach + disappear + 1,
                    wardrobeAngle(npc.coords, wardrobeTile),
                    LocShape.CentrepieceStraight,
                )
            worldRepo.locAnim(wardrobe, APPEAR_SEQ)
            worldRepo.soundArea(wardrobeTile, "synth.summon_npc", radius = SOUND_RADIUS)
            access.mes("A strange-looking wardrobe rises out of the floor.")
            delay(appear)

            npc.lockFacing(wardrobeTile)
            npc.anim(REACH_SEQ)
            worldRepo.soundArea(npc, "synth.pick2", radius = SOUND_RADIUS)
            delay(reach)

            worldRepo.locAnim(wardrobe, DISAPPEAR_SEQ)
            worldRepo.soundArea(wardrobeTile, "synth.teleport_reverse", radius = SOUND_RADIUS)
            delay(disappear)
            npc.clearFacingLock()
            npc.facePlayer(player)
        }

        if (access.invAdd(access.inv, KEY_TRAIBORN).failure) {
            chatNpc(neutral, "Your pack is full, young thingummywut. Clear a space and I'll fetch the key out again.")
            demonSlayer.bonesGiven.set(player, BONES_REQUIRED)
            return
        }
        demonSlayer.traibornKeyGiven.set(player, true)
        objbox(KEY_TRAIBORN, "Traiborn hands you a key.")
        chatPlayer(happy, "Thank you very much.")
        chatNpc(happy, "Not a problem for a friend of Sir What's-his-face.")
    }

    /** Server ticks a sequence plays for, with a small fallback for anything unmeasured. */
    private fun ticks(seq: String): Int =
        ServerCacheManager.getAnim(seq.asRSCM(RSCMType.SEQ))?.tickDuration?.coerceAtLeast(1) ?: 2

    /**
     * Rotates the wardrobe so its doors face Traiborn. [FRONT_FACES_WEST] is the rotation at which
     * the model's doors face west; each further quarter turn rotates the model clockwise.
     */
    private fun wardrobeAngle(npc: CoordGrid, wardrobe: CoordGrid): LocAngle =
        when {
            wardrobe.x > npc.x -> FRONT_FACES_WEST
            wardrobe.z > npc.z -> FRONT_FACES_WEST.turn(3)
            wardrobe.x < npc.x -> FRONT_FACES_WEST.turn(2)
            else -> FRONT_FACES_WEST.turn(1)
        }

    private suspend fun Dialogue.afterKey() {
        if (player.inv.count(KEY_TRAIBORN) > 0 || access.bank.count(KEY_TRAIBORN) > 0) {
            chatNpc(neutral, "Don't you have somewhere to be, young thingummywut? You still have that key you asked me for.")
            chatPlayer(neutral, "You're right. I've got a demon to slay.")
            return
        }
        chatPlayer(sad, "I've lost the key you gave me.")
        chatNpc(neutral, "Yes, I know. It found its way back into my wardrobe. If you want it again you'll have to bring me another $BONES_REQUIRED sets of bones.")
        demonSlayer.traibornKeyGiven.set(player, false)
        demonSlayer.bonesGiven.set(player, 0)
    }

    /**
     * A free tile next to Traiborn for the wardrobe to rise from (east first, as in the original
     * scene), or null if he is boxed in.
     */
    private fun ProtectedAccess.wardrobeTile(npc: Npc): CoordGrid? {
        val candidates =
            listOf(
                npc.coords.translate(1, 0),
                npc.coords.translate(-1, 0),
                npc.coords.translate(0, 1),
                npc.coords.translate(0, -1),
            )
        return candidates.firstOrNull { it != player.coords && !mapBlocked(it) }
    }

    private companion object {
        const val WARDROBE_LOC = "loc.qip_ds_wizards_key_wardrobe_magic"
        const val CAST_SEQ = "seq.qip_ds_bones_wizard_anim"
        const val BONE_SPOT = "spotanim.qip_ds_bone_spotanim"
        const val APPEAR_SEQ = "seq.qip_ds_wardrobe_appear"
        const val DISAPPEAR_SEQ = "seq.qip_ds_wardrobe_disappear"
        const val REACH_SEQ = "seq.human_pickuptable"
        const val SOUND_RADIUS = 10
        /** Measured in game: rotation 1 puts the doors on the west side of the model. */
        val FRONT_FACES_WEST = LocAngle.North
    }
}
