package org.rsmod.content.interfaces.music.tab

import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.types.aconverted.interf.IfButtonOp
import jakarta.inject.Inject
import org.rsmod.api.music.Music
import org.rsmod.api.music.MusicRepository
import org.rsmod.api.player.music.MusicPlayMode
import org.rsmod.api.player.music.MusicPlayer
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.vars.enumVarp
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.script.onIfOpen
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Server side of the music player side tab (`interface.music`).
 *
 * The client draws the track list, the unlocked counter and the button states on its own from
 * `dbtable.music` and the `varp.musicmulti_*` unlock varps. What it cannot do alone is send a
 * button press anywhere, so this script enables the button events, answers the presses and keeps
 * the varps the client scripts write locally (`varp.musicplay`, the selected playlist) in sync.
 *
 * Findings from the cache's client scripts that the handlers below rely on:
 * - The jukebox children are created from `db_find(dbcol.music:hidden = 0)`, so the comsub of a
 *   pressed track is its index in that result. [MusicRepository.jukeboxTracks] mirrors the order.
 * - Track ops are `Play`, `Unlock hint` and `Add to`/`Remove from playlist 1..3`.
 * - The mode buttons store `0` (area), `1` (shuffle) and `2` (single) in `varp.musicplay`.
 * - The playlist dropdown entries live at odd comsubs (`1 + 2 * index`) of `dropdown_content`
 *   and store the picked index in `varbit.music_current_playlist`.
 */
class MusicTabScript
@Inject
constructor(private val repo: MusicRepository, private val musicPlayer: MusicPlayer) :
    PluginScript() {
    private val Player.playMode by enumVarp<MusicPlayMode>("varp.musicplay")
    private var Player.currentPlaylist by intVarBit("varbit.music_current_playlist")
    private val Player.keepModeOnPlaylistChange by
        boolVarBit("varbit.dont_update_music_on_playlist_change")

    override fun ScriptContext.startup() {
        onIfOpen("interface.music") { player.openTab() }
        onIfOverlayButton("component.music:jukebox") { player.selectTrack(it.comsub, it.op) }
        onIfOverlayButton("component.music:area") {
            musicPlayer.selectMode(player, MusicPlayMode.Area)
        }
        onIfOverlayButton("component.music:shuffle") {
            musicPlayer.selectMode(player, MusicPlayMode.Random)
        }
        onIfOverlayButton("component.music:single") {
            musicPlayer.selectMode(player, MusicPlayMode.Manual)
        }
        onIfOverlayButton("component.music:skip") { musicPlayer.skipTrack(player) }
        onIfOverlayButton("component.music:dropdown_content") {
            player.selectDropdownEntry(it.comsub)
        }
    }

    private fun Player.openTab() {
        val trackCount = repo.jukeboxTracks().size
        ifSetEvents(
            "component.music:jukebox",
            0 until trackCount,
            IfEvent.Op1,
            IfEvent.Op2,
            IfEvent.Op3,
            IfEvent.Op4,
            IfEvent.Op5,
        )
        // The "Expand" op on the playlist dropdown button is set by the tab's own client script;
        // the cache component carries no events, so the press only reaches the script if we
        // enable it.
        ifSetEvents("component.music:playlist", -1..-1, IfEvent.Op1)
        ifSetEvents("component.music:dropdown_content", 0 until DROPDOWN_CHILD_COUNT, IfEvent.Op1)
        musicPlayer.updateNowPlayingText(this)
    }

    private fun Player.selectTrack(comsub: Int, op: IfButtonOp) {
        val music = repo.jukeboxTrack(comsub) ?: return
        when (op) {
            IfButtonOp.Op1 -> playTrack(music)
            IfButtonOp.Op2 -> mes("This track unlocks ${music.unlockHint}")
            IfButtonOp.Op3 -> togglePlaylist(1, music)
            IfButtonOp.Op4 -> togglePlaylist(2, music)
            IfButtonOp.Op5 -> togglePlaylist(3, music)
            else -> {}
        }
    }

    private fun Player.playTrack(music: Music) {
        val played = musicPlayer.playSelected(this, music)
        if (!played) {
            mes("You have not unlocked this piece of music yet!")
        }
    }

    private fun Player.togglePlaylist(playlist: Int, music: Music) {
        if (musicPlayer.isInPlaylist(this, playlist, music)) {
            musicPlayer.removeFromPlaylist(this, playlist, music)
            return
        }
        if (!music.canUnlock || !musicPlayer.hasUnlocked(this, music)) {
            mes("You can only add music tracks you have unlocked to a playlist.")
            return
        }
        val added = musicPlayer.addToPlaylist(this, playlist, music)
        if (!added) {
            mes("Playlist $playlist is full.")
        }
    }

    private fun Player.selectDropdownEntry(comsub: Int) {
        // Each dropdown entry is drawn as a pair of children; the clickable one is the odd child.
        if (comsub % 2 == 0) {
            return
        }
        val playlist = (comsub - 1) / 2
        if (playlist !in 0..MusicPlayer.PLAYLIST_COUNT) {
            return
        }
        selectPlaylist(playlist)
    }

    /**
     * Mirrors the client's `music_playlist_select` script: picking a playlist while in area mode
     * moves the player to shuffle mode so the playlist actually plays, and picking "all tracks"
     * returns them to area mode, unless the "don't update music on playlist change" setting is on.
     */
    private fun Player.selectPlaylist(playlist: Int) {
        currentPlaylist = playlist
        if (keepModeOnPlaylistChange) {
            return
        }
        if (playlist == 0) {
            musicPlayer.selectMode(this, MusicPlayMode.Area)
        } else if (playMode == MusicPlayMode.Area) {
            musicPlayer.selectMode(this, MusicPlayMode.Random)
        }
    }

    private companion object {
        /** "All tracks" plus the three playlists, two children per dropdown entry. */
        private const val DROPDOWN_CHILD_COUNT = (MusicPlayer.PLAYLIST_COUNT + 1) * 2
    }
}
