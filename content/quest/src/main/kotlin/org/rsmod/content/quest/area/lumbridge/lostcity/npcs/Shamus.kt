package org.rsmod.content.quest.area.lumbridge.lostcity.npcs

import dev.openrune.types.NpcMode
import dev.openrune.types.hunt.HuntVis
import jakarta.inject.Inject
import org.rsmod.api.hunt.NpcSearch
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.lumbridge.lostcity.LostCityQuest
import org.rsmod.content.quest.area.lumbridge.lostcity.LostCityQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.lumbridge.lostcity.NO_AXE_MESSAGE
import org.rsmod.content.quest.area.lumbridge.lostcity.findWoodcuttingAxe
import org.rsmod.content.quest.area.lumbridge.lostcity.freeTileBeside
import org.rsmod.content.quest.area.lumbridge.lostcity.woodcuttingAnim
import org.rsmod.game.entity.Npc
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * Shamus, the leprechaun who lives in the tree west of the adventurers' camp. The tree is the
 * one with a plain `Chop` option; taking an axe to it makes him pop out beside it, and he
 * vanishes again as soon as he has finished talking.
 */
class Shamus
@Inject
constructor(
    private val lostCity: LostCityQuest,
    private val npcRepo: NpcRepository,
    private val search: NpcSearch,
    private val collision: CollisionFlagMap,
) : PluginScript() {

    private val quest
        get() = lostCity.quest

    override fun ScriptContext.startup() {
        onOpLoc1(TREE) { chopTree(it.loc) }
        onOpNpc1(SHAMUS) { startDialogue(it.npc) { shamus(it.npc) } }
    }

    private suspend fun ProtectedAccess.chopTree(tree: BoundLocInfo) {
        arriveDelay()
        faceLoc(tree)
        val outside = search.find(tree.coords, SHAMUS, SEARCH_RADIUS, HuntVis.Off)
        if (outside != null) {
            outside.facePlayer(player)
            startDialogue(outside) {
                chatNpc(angry, "Hey! Yer big elephant! Don't go choppin' down me house, now!")
            }
            return
        }
        val axe = player.findWoodcuttingAxe()
        if (axe == null) {
            mes(NO_AXE_MESSAGE)
            return
        }
        anim(axe.woodcuttingAnim())
        spam("You swing your axe at the tree.")
        delay(CHOP_TICKS)
        resetAnim()

        val tile = collision.freeTileBeside(tree, player.coords)
        if (tile == null) {
            mes("There's no room for anyone to come out of the tree.")
            return
        }
        val shamus = Npc(SHAMUS, tile)
        shamus.mode = NpcMode.None
        npcRepo.add(shamus, SHAMUS_TICKS)
        shamus.spotanim(PUFF)
        soundSynth(PUFF_SOUND)
        shamus.facePlayer(player)
        delay(1)
        startDialogue(shamus) { shamus(shamus) }
    }

    private suspend fun Dialogue.shamus(npc: Npc) {
        chatNpc(angry, "Ay yer big elephant! Yer've caught me, to be sure! What would an elephant like yer be wanting wid ol' Shamus then?")
        when (lostCity.stage(player)) {
            0 -> {
                chatPlayer(confused, "I'm not sure.")
                chatNpc(angry, "Well you'll have to be catchin' me again when yer are, elephant!")
            }
            STAGE_STARTED -> firstTalk()
            else -> laterTalks()
        }
        vanish(npc)
    }

    private suspend fun Dialogue.firstTalk() {
        chatPlayer(neutral, "I want to find Zanaris.")
        chatNpc(quiz, "Zanaris is it now? Well well well... Yer'll be needing to be going to that funny little shed out there in the swamp, so you will.")
        chatPlayer(confused, "...but... I thought... Zanaris was a city...?")
        chatNpc(happy, "Aye that it is!")
        when (
            choice2(
                "How does it fit in a shed then?", 1,
                "I've been in that shed, I didn't see a city.", 2,
            )
        ) {
            1 -> {
                chatPlayer(confused, "...How does it fit in a shed then?")
                chatNpc(angry, "Ah yer stupid elephant! The city isn't IN the shed! The doorway to the shed is being a portal to Zanaris, so it is.")
                chatPlayer(quiz, "So I just walk into the shed and end up in Zanaris then?")
            }
            2 -> chatPlayer(neutral, "I've been in that shed. I didn't see a city.")
        }
        chatNpc(quiz, "Oh, was I fergetting to say? Yer need to be carrying a Dramenwood staff to be getting there! Otherwise Yer'll just be ending up in the shed.")
        chatPlayer(quiz, "So where would I get a staff?")
        chatNpc(neutral, "Dramenwood staffs are crafted from branches of the Dramen tree, so they are. I hear there's a Dramen tree over on the island of Entrana in a cave")
        chatNpc(neutral, "or some such. There would probably be a good place for an elephant like yer to be starting looking I reckon.")
        chatNpc(angry, "The monks are running a ship from Port Sarim to Entrana, I hear too. Now leave me alone yer elephant!")
        if (lostCity.stage(player) == STAGE_STARTED) {
            quest.advanceQuestStage(access)
        }
    }

    private suspend fun Dialogue.laterTalks() {
        when (
            choice2(
                "I'm not sure.", 1,
                "How do I get to Zanaris again?", 2,
            )
        ) {
            1 -> {
                chatPlayer(confused, "I'm not sure.")
                chatNpc(laugh, "Ha! Look at yer! Look at the stupid elephant who tries to go catching a leprechaun when he don't even be knowing what he wants!")
            }
            2 -> {
                chatPlayer(quiz, "How do I get to Zanaris again?")
                chatNpc(angry, "Yer stupid elephant! I'll tell yer again! Yer need to be entering the shed in the middle of the swamp while holding a dramenwood staff! Yer can make the Dramen staff")
                chatNpc(angry, "from a dramen tree branch, and there's a Dramen tree on Entrana! Now leave me alone yer great elephant!")
            }
        }
    }

    /** Shamus is done talking and pops back into his tree. */
    private suspend fun Dialogue.vanish(npc: Npc) {
        if (npc.isSlotAssigned) {
            npc.spotanim(PUFF)
            access.soundSynth(PUFF_SOUND)
            access.delay(1)
            if (npc.isSlotAssigned) {
                npcRepo.del(npc, Int.MAX_VALUE)
            }
        }
        mesbox("The leprechaun magically disappears.")
    }

    private companion object {
        const val TREE = "loc.leprechauntree"
        const val SHAMUS = "npc.zanarisleprechaun"

        const val PUFF = "spotanim.smokepuff"
        const val PUFF_SOUND = "synth.smokepuff"

        /** Swings taken at the tree before Shamus gives up hiding. */
        const val CHOP_TICKS = 3

        /** How long Shamus waits beside his tree if the player wanders off mid-conversation. */
        const val SHAMUS_TICKS = 100
        const val SEARCH_RADIUS = 6
    }
}
