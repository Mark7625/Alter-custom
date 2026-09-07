package org.rsmod.content.quest.area.varrock.demonslayer

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcMode
import jakarta.inject.Inject
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceNpc
import org.rsmod.api.instances.events.InstancePlayerLeaveEvent
import org.rsmod.api.instances.events.instanceEventId
import org.rsmod.api.music.MusicRepository
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.music.MusicPlayMode
import org.rsmod.api.player.music.MusicPlayer
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.vars.enumVarp
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onArea
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onNpcQueue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.table.MusicRow
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.map.Direction
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The end of Demon Slayer: walking into the stone circle with Silverlight moves the player into
 * a private copy of it, where Denath and his dark wizards summon Delrith. Reducing Delrith to
 * zero hitpoints leaves him weakened; the player then has a short window to speak the incantation
 * Aris showed them. The right words banish him and complete the quest, the wrong ones restore
 * him to full strength, and waiting too long lets him slip away until the player re-enters.
 */
class DelrithFight
@Inject
constructor(
    private val quest: DemonSlayerQuest,
    private val circle: StoneCircle,
    private val manager: InstanceManager,
    private val npcRepo: NpcRepository,
    private val playerList: PlayerList,
    private val launcher: ProtectedAccessLauncher,
    private val aiInteractions: AiPlayerInteractions,
    private val musicPlayer: MusicPlayer,
    private val musicRepo: MusicRepository,
) : PluginScript() {

    /** "Delrith", played on a loop from the summoning through the battle. */
    private val fightTrack: MusicRow by lazy { MusicRow.getRow(FIGHT_TRACK) }

    private var Player.musicMode by enumVarp<MusicPlayMode>("varp.musicplay")
    private val Player.currentMusicId by intVarBit("varbit.music_curr_id")

    /** What each fighter was listening to before the fight track took over, by player uuid. */
    private val previousMusic = HashMap<Long, Pair<MusicPlayMode, Int>>()

    private val delrithType =
        ServerCacheManager.getNpc(DELRITH.asRSCM(RSCMType.NPC)) ?: error("Missing npc: $DELRITH")
    private val weakenedType =
        ServerCacheManager.getNpc(WEAKENED.asRSCM(RSCMType.NPC)) ?: error("Missing npc: $WEAKENED")
    private val denathType =
        ServerCacheManager.getNpc(DENATH.asRSCM(RSCMType.NPC)) ?: error("Missing npc: $DENATH")

    override fun ScriptContext.startup() {
        onArea(CIRCLE_AREA) { tryEnterFight() }
        onNpcQueue(delrithType, "queue.death") { weaken() }
        onNpcQueue(weakenedType, TIMEOUT_QUEUE) { timeout() }
        onOpNpc1(WEAKENED) { banish(it.npc) }
        onEvent<InstancePlayerLeaveEvent>(instanceEventId(KEY)) { endFightMusic(player) }
    }

    private suspend fun ProtectedAccess.tryEnterFight() {
        if (getStage() != DemonSlayerQuest.STAGE_SILVERLIGHT) {
            return
        }
        if (!with(quest) { carriesSilverlight() }) {
            return
        }
        if (manager.sessionForPlayer(player) != null) {
            return
        }
        enterFight()
    }

    private fun ProtectedAccess.getStage(): Int = quest.quest.getQuestStage(player)

    private suspend fun ProtectedAccess.enterFight() {
        fadeToBlack()
        val visit =
            try {
                with(circle) { enterCircle(KEY, WIZARD_SPAWNS) }
            } catch (e: Exception) {
                logger.error(e) { "Demon Slayer fight entry failed for ${player.displayName}." }
                mes("Demon Slayer: could not enter the circle (${e::class.simpleName}: ${e.message}).")
                with(circle) { exitToRoad() }
                null
            }
        if (visit == null) {
            fadeFromBlack()
            closeFadeOverlay()
            return
        }
        // Let the client rebuild the instance before the camera or overlay are touched.
        delay(1)
        if (quest.seenSummoning.get(player)) {
            fadeFromBlack()
            closeFadeOverlay()
            startFightMusic()
            val delrith = spawnDelrith(visit, summoned = false)
            engage(delrith, circle.npcsIn(visit))
            return
        }
        var delrith: Npc? = null
        try {
            delrith = summoningCutscene(visit)
        } catch (e: Exception) {
            logger.error(e) { "Demon Slayer summoning cutscene failed for ${player.displayName}." }
            mes("Demon Slayer: the summoning scene failed (${e::class.simpleName}: ${e.message}).")
        } finally {
            endCutscene()
            fadeFromBlack()
            closeFadeOverlay()
        }
        // The scene is only ever shown once; if it broke, skip it next time rather than loop.
        quest.seenSummoning.set(player, true)
        quest.syncVars(player)
        val wizards = circle.npcsIn(visit).filter { it.id != delrithType.id && it.id != denathType.id }
        for (wizard in wizards) {
            wizard.clearFacingLock()
            wizard.mode = wizard.type.defaultMode
        }
        engage(delrith ?: spawnDelrith(visit, summoned = false), wizards)
    }

    /** Plays the summoning and returns the Delrith it spawned. */
    private suspend fun ProtectedAccess.summoningCutscene(visit: StoneCircle.Visit): Npc {
        beginCutscene()
        camMoveTo(visit.at(CAMERA_FROM), height = CAMERA_HEIGHT, rate = CAMERA_RATE, rate2 = CAMERA_RATE)
        camLookAt(visit.at(CAMERA_AT), height = LOOK_HEIGHT, rate = CAMERA_RATE, rate2 = CAMERA_RATE)

        val wizards = circle.npcsIn(visit).filter { it.id != delrithType.id }
        val denath = circle.spawn(visit, DENATH, DENATH_TILE, Direction.North)
        for (wizard in wizards) {
            wizard.mode = NpcMode.None
            wizard.lockFacing(visit.at(StoneCircle.TABLE), targetWidth = 2, targetLength = 2)
        }
        delay(1)

        fadeFromBlack()
        startFightMusic()
        denath.say("Rise, mighty Delrith! Rise, and bring ruin to this soft, weak city!")
        denath.anim(CHANT_SEQ)
        soundSynth("synth.curse_cast_and_fire")
        delay(4)
        for (wizard in wizards) {
            wizard.say("Rise, Delrith!")
            wizard.anim(CHANT_SEQ)
        }
        soundSynth("synth.curse_cast_and_fire")
        delay(3)
        mes("The wizards cast an evil spell...")
        circle.animateTable(visit, "seq.qip_ds_table_explosion")
        soundSynth("synth.crumble_hit")
        delay(2)
        val delrith = spawnDelrith(visit, summoned = true)
        soundSynth("synth.summon_npc")
        delay(4)
        denath.say("At last! Welcome back, my demonic brother. Rest now, then take your revenge on Varrock!")
        delay(4)
        wizards.firstOrNull()?.say("Master, who is that?")
        delay(3)
        denath.say("No! That's Silverlight! Delrith isn't ready yet!")
        delay(3)
        denath.say("I'm not staying to find out what happens next...")
        denath.clearFacingLock()
        denath.walk(visit.at(DENATH_ESCAPE))
        delay(3)
        circle.remove(denath)
        fadeToBlack()
        return delrith
    }

    private fun spawnDelrith(visit: StoneCircle.Visit, summoned: Boolean): Npc {
        val delrith = circle.spawn(visit, DELRITH, DELRITH_TILE, Direction.South)
        delrith.mode = delrithType.defaultMode
        if (summoned) {
            delrith.anim("seq.qip_ds_delrith_summoned")
        }
        return delrith
    }

    /**
     * Delrith always comes for the player. The dark wizards only bother with players they would
     * be aggressive towards in the open world: the level 7 wizards below combat 15, the level
     * 20 wizards below combat 41.
     */
    private fun ProtectedAccess.engage(delrith: Npc, wizards: List<Npc>) {
        delrith.opPlayer2(player, aiInteractions)
        for (wizard in wizards) {
            if (wizard.id == delrithType.id || !wizard.isSlotAssigned) {
                continue
            }
            val threshold = if (wizard.type.combatLevel >= 20) 41 else 15
            if (player.combatLevel < threshold) {
                wizard.opPlayer2(player, aiInteractions)
            }
        }
    }

    /**
     * Plays the fight track and switches the player to single-track mode so it loops for as long
     * as the fight lasts. [endFightMusic] puts their previous mode and track back.
     */
    private fun ProtectedAccess.startFightMusic() {
        val uuid = player.uuid ?: return
        previousMusic.putIfAbsent(uuid, player.musicMode to player.currentMusicId)
        player.musicMode = MusicPlayMode.Manual
        musicPlay(fightTrack)
    }

    /** Restores the music from before the fight; safe to call more than once. */
    private fun endFightMusic(player: Player) {
        val uuid = player.uuid ?: return
        val (mode, trackId) = previousMusic.remove(uuid) ?: return
        player.musicMode = mode
        val previousRow = musicRepo.forId(trackId)?.let { MusicRow.getRow(it.rowId) }
        when {
            mode == MusicPlayMode.Manual && previousRow != null -> musicPlayer.play(player, previousRow)
            mode == MusicPlayMode.Manual -> musicPlayer.stop(player)
            else -> musicPlayer.skipTrack(player)
        }
    }

    /** Delrith's death queue: he does not die, he weakens and waits for the incantation. */
    private fun StandardNpcAccess.weaken() {
        val hero = findHero(playerList)
        val instanceId = manager.instanceForNpc(npc)
        val coords = npc.coords
        npcRepo.del(npc, Int.MAX_VALUE)

        val weakened = Npc(weakenedType, coords)
        weakened.mode = NpcMode.None
        npcRepo.add(weakened, Int.MAX_VALUE)
        if (instanceId != null) {
            manager.attachNpc(instanceId, weakened)
        }
        weakened.queue(TIMEOUT_QUEUE, TIMEOUT_TICKS)

        if (hero != null) {
            hero.mes("Delrith staggers, weakened. Now is the time for the incantation!")
            hero.soundSynth("synth.weaken_all")
            launcher.launch(hero) { banish(weakened) }
        }
    }

    /** The player took too long: Delrith gathers his strength and vanishes until they return. */
    private fun StandardNpcAccess.timeout() {
        if (!npc.isSlotAssigned) {
            return
        }
        val instanceId = manager.instanceForNpc(npc)
        val session = instanceId?.let { manager.sessionForId(it) }
        if (session != null) {
            for (occupant in session.occupants) {
                val player = playerList.firstOrNull { it.uuid == occupant } ?: continue
                player.mes("Delrith recovers and sinks back into the stone. Leave the circle and return to face him again.")
            }
        }
        npcRepo.del(npc, Int.MAX_VALUE)
    }

    private suspend fun ProtectedAccess.banish(weakened: Npc) {
        if (!weakened.isSlotAssigned) {
            mes("Delrith has already slipped away.")
            return
        }
        val words = DemonSlayerQuest.WORDS
        val chosen = ArrayList<Int>(words.size)
        startDialogue {
            chatPlayer(quiz, "Right... how did that incantation go again?")
            for (step in words.indices) {
                val pick =
                    choice5(
                        words[0], 0,
                        words[1], 1,
                        words[2], 2,
                        words[3], 3,
                        words[4], 4,
                        title = "Word ${step + 1} of the incantation",
                    )
                chosen += pick
                val suffix = if (step == words.lastIndex) "!" else "..."
                say(words[pick] + suffix)
                delay(1)
            }
        }
        if (!weakened.isSlotAssigned) {
            mes("Delrith has already slipped away.")
            return
        }
        if (chosen == quest.incantationOrder(player)) {
            banished(weakened)
        } else {
            wrongIncantation(weakened)
        }
    }

    private suspend fun ProtectedAccess.banished(weakened: Npc) {
        weakened.clearQueue(TIMEOUT_QUEUE)
        weakened.anim("seq.qip_ds_delrith_banished")
        soundSynth("synth.aide_teleport_portal")
        mesbox("Delrith is dragged into the vortex...")
        mesbox("...and back to the dark dimension he came from.")
        circle.remove(weakened)

        with(circle) { exitToRoad() }
        endFightMusic(player)
        mes("You slip away from the furious dark wizards, the demon defeated.")
        quest.advance(this)
    }

    private suspend fun ProtectedAccess.wrongIncantation(weakened: Npc) {
        soundSynth("synth.spellfail")
        mesbox("The vortex fizzles out. That was not the right incantation.")
        if (!weakened.isSlotAssigned) {
            return
        }
        weakened.clearQueue(TIMEOUT_QUEUE)
        val instanceId = manager.instanceForNpc(weakened)
        val coords = weakened.coords
        circle.remove(weakened)

        val delrith = Npc(delrithType, coords)
        npcRepo.add(delrith, Int.MAX_VALUE)
        if (instanceId != null) {
            manager.attachNpc(instanceId, delrith)
        }
        mes("Delrith's strength returns!")
        delrith.opPlayer2(player, aiInteractions)
    }

    private companion object {
        private val logger = InlineLogger()

        const val KEY = "demonslayer_fight"
        const val CIRCLE_AREA = "area.demon_slayer_stone_circle"
        const val TIMEOUT_QUEUE = "queue.demonslayer_delrith_timeout"

        /** Ticks a weakened Delrith waits for the incantation before slipping away. */
        const val TIMEOUT_TICKS = 100

        const val DELRITH = "npc.delrith"
        const val WEAKENED = "npc.delrith_weakened"
        const val DENATH = "npc.qip_ds_dark_wizard_denath"
        const val CHANT_SEQ = "seq.qip_ds_dark_wizard_chanting"
        const val FIGHT_TRACK = "dbrow.music_delrith_summoning"

        const val CAMERA_HEIGHT = 700
        const val LOOK_HEIGHT = 150
        const val CAMERA_RATE = 100

        val CAMERA_FROM = CoordGrid(3227, 3355, 0)
        val CAMERA_AT = CoordGrid(3228, 3370, 0)

        /** Delrith is 2x2; this puts him just north of the stone table. */
        val DELRITH_TILE = CoordGrid(3227, 3371, 0)

        /** South of the table, facing it. */
        val DENATH_TILE = CoordGrid(3227, 3367, 0)
        val DENATH_ESCAPE = CoordGrid(3235, 3362, 0)

        /** Two level 7 and one level 20 dark wizard, on the world spawn tiles around the table. */
        val WIZARD_SPAWNS =
            listOf(
                InstanceNpc("npc.qip_ds_young_dark_wizard1", CoordGrid(3225, 3365, 0)),
                InstanceNpc("npc.qip_ds_young_dark_wizard2", CoordGrid(3230, 3374, 0)),
                InstanceNpc("npc.qip_ds_young_dark_wizard3", CoordGrid(3232, 3367, 0)),
            )
    }
}
