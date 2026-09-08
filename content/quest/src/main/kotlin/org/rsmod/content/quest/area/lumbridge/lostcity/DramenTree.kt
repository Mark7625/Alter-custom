package org.rsmod.content.quest.area.lumbridge.lostcity

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.hunt.HuntVis
import jakarta.inject.Inject
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.hunt.NpcSearch
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.stat.woodcuttingLvl
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onNpcQueue
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.stats.levelmod.InvisibleLevels
import org.rsmod.content.quest.area.lumbridge.lostcity.LostCityQuest.Companion.DRAMEN_BRANCH
import org.rsmod.content.quest.area.lumbridge.lostcity.LostCityQuest.Companion.STAGE_MET_SHAMUS
import org.rsmod.content.quest.area.lumbridge.lostcity.LostCityQuest.Companion.WOODCUTTING_REQ
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * The Dramen tree in the caves beneath Entrana and the Tree spirit that guards it.
 *
 * The first swing at the tree summons the spirit, a level 101 undead guardian that goes straight
 * for the player. Until it is beaten every attempt just provokes it again. Once it is dead the
 * tree yields Dramen branches for the rest of the session; the spirit gives up and fades away if
 * it is left alone for ten minutes, and comes back the next time someone raises an axe.
 */
class DramenTree
@Inject
constructor(
    private val lostCity: LostCityQuest,
    private val npcRepo: NpcRepository,
    private val search: NpcSearch,
    private val collision: CollisionFlagMap,
    private val aiInteractions: AiPlayerInteractions,
    private val invisibleLvls: InvisibleLevels,
    private val death: NpcDeath,
    private val playerList: PlayerList,
    private val launcher: ProtectedAccessLauncher,
) : PluginScript() {

    private val quest
        get() = lostCity.quest

    private val spiritType =
        ServerCacheManager.getNpc(TREE_SPIRIT.asRSCM(RSCMType.NPC)) ?: error("Missing npc: $TREE_SPIRIT")

    override fun ScriptContext.startup() {
        onOpLoc1(TREE) { chop(it.loc) }
        onNpcQueue(spiritType, "queue.death") { spiritDefeated() }
    }

    private suspend fun ProtectedAccess.chop(tree: BoundLocInfo) {
        arriveDelay()
        faceLoc(tree)
        if (player.woodcuttingLvl < WOODCUTTING_REQ) {
            mes("You need a Woodcutting level of $WOODCUTTING_REQ to chop down this tree.")
            return
        }
        val axe = player.findWoodcuttingAxe()
        if (axe == null) {
            mes(NO_AXE_MESSAGE)
            return
        }
        if (!lostCity.spiritDefeated.get(player)) {
            provokeSpirit(tree)
            return
        }
        if (lostCity.stage(player) == STAGE_MET_SHAMUS) {
            // The kill itself advances the quest; this covers a kill that landed while the
            // player was busy with something the launcher could not interrupt.
            quest.advanceQuestStage(this)
        }
        if (inv.isFull()) {
            mes("Your inventory is too full to hold any more dramen branches.")
            soundSynth("synth.pillory_wrong")
            return
        }
        val chopAnim = axe.woodcuttingAnim()
        anim(chopAnim)
        spam("You swing your axe at the tree.")
        repeat(MAX_SWINGS) {
            delay(SWING_TICKS)
            if (statRandom("stat.woodcutting", CUT_LOW, CUT_HIGH, invisibleLvls)) {
                resetAnim()
                invAdd(inv, DRAMEN_BRANCH)
                mes("You cut a branch from the Dramen tree.")
                return
            }
            anim(chopAnim)
        }
        resetAnim()
    }

    /** The guardian appears, or if it is already out, reminds the player who it is. */
    private fun ProtectedAccess.provokeSpirit(tree: BoundLocInfo) {
        val spirit =
            search.find(tree.coords, TREE_SPIRIT, SPIRIT_SEARCH_RADIUS, HuntVis.Off)
                ?: summonSpirit(tree)
                ?: return
        spirit.facePlayer(player)
        spirit.say("You must defeat me before touching the tree!")
        spirit.opPlayer2(player, aiInteractions)
    }

    private fun ProtectedAccess.summonSpirit(tree: BoundLocInfo): Npc? {
        val tile = collision.freeTileBeside(tree, player.coords) ?: return null
        val spirit = Npc(TREE_SPIRIT, tile)
        npcRepo.add(spirit, SPIRIT_LINGER_TICKS)
        return spirit
    }

    /**
     * The spirit's death queue. It drops nothing, so the default death (which would leave
     * bones) is replaced; whoever dealt the most damage is the one it stops guarding against.
     */
    private suspend fun StandardNpcAccess.spiritDefeated() {
        val hero = findHero(playerList)
        death.deathNoDrops(this)
        if (hero == null) {
            return
        }
        lostCity.spiritDefeated.set(hero, true)
        launcher.launch(hero) {
            if (lostCity.stage(player) == STAGE_MET_SHAMUS) {
                quest.advanceQuestStage(this)
            }
            mesbox("With the Tree Spirit defeated you can now chop the tree.")
        }
    }

    private companion object {
        const val TREE = "loc.dramentree"
        const val TREE_SPIRIT = "npc.tree_spirit"

        /** Ten minutes of being ignored and the spirit gives up. */
        const val SPIRIT_LINGER_TICKS = 1000
        const val SPIRIT_SEARCH_RADIUS = 12

        const val SWING_TICKS = 4
        const val MAX_SWINGS = 25

        /** Roughly a regular tree's odds; the Dramen tree has no success-rate params of its own. */
        const val CUT_LOW = 64
        const val CUT_HIGH = 200
    }
}
