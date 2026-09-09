package org.rsmod.content.quest.area.varrock.demonslayer

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.music.MusicPlayer
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.table.MusicRow
import org.rsmod.game.map.Direction
import org.rsmod.map.CoordGrid

/**
 * Aris' crystal-ball vision: Wally striking down the newly summoned Delrith at the stone circle
 * and chanting the incantation. Runs in a private copy of the circle, then returns the player to
 * where they were standing.
 *
 * Whatever happens inside the scene, the player always ends up back at their origin with the
 * camera, interface and fade overlay restored. [play] returns false when the scene could not be
 * shown so the caller can narrate it instead.
 */
@Singleton
class WallyVision
@Inject
constructor(
    private val circle: StoneCircle,
    private val musicPlayer: MusicPlayer,
) {
    private val visionTrack: MusicRow by lazy { MusicRow.getRow(VISION_TRACK) }

    suspend fun ProtectedAccess.play(quest: DemonSlayerQuest): Boolean {
        val origin = player.coords
        val words = quest.incantationSpoken(player)

        fadeToBlack()
        val visit = with(circle) { enterCircle(KEY, emptyList()) }
        if (visit == null) {
            fadeFromBlack()
            closeFadeOverlay()
            return false
        }

        var played = false
        try {
            // Let the client rebuild the instance before anything camera- or overlay-related is
            // sent; packets sent in the same cycle as the teleport are applied to the old scene.
            // The player must stay within npc view range (about 15 tiles) of the actors or the
            // client is never told about them, so they park just behind the camera.
            telejump(visit.at(PLAYER_PARK), TeleportType.Exempt)
            delay(1)
            if (player.coords.chebyshevDistance(visit.at(PLAYER_PARK)) > 1) {
                logger.warn { "Demon Slayer vision: player did not arrive in the instance (coords=${player.coords})." }
                return false
            }
            playScene(visit, words)
            played = true
        } catch (e: Exception) {
            logger.error(e) { "Demon Slayer vision failed for ${player.displayName}." }
        } finally {
            fadeToBlack()
            endCutscene()
            with(circle) { leaveCircle() }
            telejump(origin, TeleportType.Exempt)
            delay(1)
            fadeFromBlack()
            closeFadeOverlay()
            // Hand the music back to the area (or shuffle) once the vision is over.
            musicPlayer.skipTrack(player)
        }
        return played
    }

    private suspend fun ProtectedAccess.playScene(visit: StoneCircle.Visit, words: String) {
        beginCutscene()
        camMoveTo(visit.at(CAMERA_FROM), height = CAMERA_HEIGHT, rate = CAMERA_RATE, rate2 = CAMERA_RATE)
        camLookAt(visit.at(CAMERA_AT), height = LOOK_HEIGHT, rate = CAMERA_RATE, rate2 = CAMERA_RATE)

        val wally = circle.spawn(visit, WALLY, WALLY_TILE, Direction.North)
        val delrith = circle.spawn(visit, DELRITH, DELRITH_TILE, Direction.South)
        val denath = circle.spawn(visit, DENATH, DENATH_TILE, Direction.East)
        delay(1)

        fadeFromBlack()
        musicPlay(visionTrack)
        denath.say("Rise, Delrith! Rise and serve your master!")
        soundSynth("synth.summon_npc")
        delay(3)
        wally.say("Begone, foul demon!")
        wally.anim("seq.qip_ds_wally_cutscene")
        soundSynth("synth.cleave")
        delay(3)
        wally.say("Now, how did that incantation go again?")
        delay(3)
        wally.say(words)
        delay(4)
        delrith.anim("seq.qip_ds_delrith_struck_down")
        soundSynth("synth.weaken_all")
        delay(3)
        delrith.anim("seq.qip_ds_delrith_banished")
        soundSynth("synth.aide_teleport_portal")
        delay(3)
        circle.remove(delrith)
        denath.say("No! My master!")
        delay(2)
        wally.say("Ha! No demon can stand against Wally the demon slayer!")
        delay(3)
    }

    private companion object {
        private val logger = InlineLogger()

        const val KEY = "demonslayer_vision"

        const val WALLY = "npc.qip_ds_wally"
        const val DELRITH = "npc.delrith"
        const val DENATH = "npc.qip_ds_dark_wizard_denath"

        /** "Wally the Hero", the music track the vision plays over. */
        const val VISION_TRACK = "dbrow.music_wally_cutscene"
        const val CAMERA_HEIGHT = 650
        const val LOOK_HEIGHT = 150
        const val CAMERA_RATE = 100

        val PLAYER_PARK = CoordGrid(3227, 3360, 0)
        val CAMERA_FROM = CoordGrid(3227, 3357, 0)
        val CAMERA_AT = CoordGrid(3228, 3370, 0)

        /** South of the table, facing it. */
        val WALLY_TILE = CoordGrid(3227, 3366, 0)

        /** Delrith is 2x2; this puts him just north of the table. */
        val DELRITH_TILE = CoordGrid(3227, 3371, 0)

        val DENATH_TILE = CoordGrid(3224, 3371, 0)
    }
}
