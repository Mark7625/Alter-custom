package org.rsmod.api.music.plugin.scripts

import jakarta.inject.Inject
import org.rsmod.api.player.music.MusicPlayer
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

public class MusicTimerScript @Inject constructor(private val musicPlayer: MusicPlayer) :
    PluginScript() {
    private val Player.masterVolume by intVarBit("varbit.option_master_volume_desktop")
    private val Player.musicVolume by intVarBit("varbit.option_music_desktop")
    private var Player.currMusicId by intVarBit("varbit.music_curr_id")
    private var Player.musicClock by intVarBit("varbit.music_curr_clocks")
    private var Player.musicDuration by intVarBit("varbit.music_curr_duration")

    override fun ScriptContext.startup() {
        onPlayerLogin { player.musicLogin() }
        onPlayerSoftTimer("timer.music_sync") { player.musicSync() }
        onPlayerSoftTimer("timer.music_resume") { player.musicResume() }
    }

    private fun Player.musicLogin() {
        // TODO(emulation): Music is meant to start playing (sent twice as well) on the first
        //  login cycle for the player. However, as of now, the music area id is assigned when
        //  the engine queue `onarea` is invoked, which does not happen until the second cycle
        //  that the player is logged in for (due to when player logins take place in the loop).
        //  There are a few workarounds we can do, which includes directly accessing the `AreaIndex`
        //  and finding the music for each area; however, this seems excessive for this use case.
        //  We will leave this as-is and decide at a later date.
        softTimer("timer.music_sync", cycles = 1)
    }

    private fun Player.musicResume() {
        if (isMusicMuted()) {
            return
        }
        musicPlayer.enable(this)
    }

    private fun Player.musicSync() {
        if (isMusicMuted()) {
            return
        }

        // TODO(emulation): This is meant to have an extra 1-cycle delay before playing the next
        //  music track. In total it should take 2 cycles when going from "stop midi" to playing
        //  the next midi.
        if (currMusicId == 0) {
            musicPlayer.playNext(this)
            return
        }

        musicClock++

        val endMusic = musicClock > musicDuration
        if (endMusic) {
            musicPlayer.trackEnded(this)
            return
        }
    }

    private fun Player.isMusicMuted(): Boolean {
        return masterVolume == 0 || musicVolume == 0
    }
}
