package org.rsmod.api.player.music

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.music.Music
import org.rsmod.api.music.MusicRepository
import org.rsmod.api.player.chatMesColorTag
import org.rsmod.api.player.midiSong
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.ui.ifSetText
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.vars.enumVarBit
import org.rsmod.api.player.vars.enumVarp
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.random.GameRandom
import org.rsmod.api.table.MusicRow
import org.rsmod.game.entity.Player

public class MusicPlayer
@Inject
internal constructor(private val random: GameRandom, private val repo: MusicRepository) {
    private var Player.playMode by enumVarp<MusicPlayMode>("varp.musicplay")
    private val Player.areaMode by enumVarBit<MusicAreaMode>("varbit.music_area_mode")

    private var Player.lastMusicId by intVarBit("varbit.music_last_id")
    private var Player.currMusicId by intVarBit("varbit.music_curr_id")
    private var Player.currMusicArea by intVarBit("varbit.music_curr_area")

    private var Player.musicClocks by intVarBit("varbit.music_curr_clocks")
    private var Player.musicDuration by intVarBit("varbit.music_curr_duration")

    private var Player.musicPlaylist by intVarp("varp.music_playlist")
    private var Player.unlockMessageDisabled by boolVarBit("varbit.music_unlock_text_toggle")

    /** The `dbtable.music` row of the playing track. The music tab colours it as "playing". */
    private var Player.currentTrackRow by intVarp("varp.music_current_track")

    /** The playlist picked in the music tab dropdown: `0` for every track, otherwise `1..3`. */
    private val Player.currentPlaylist by intVarBit("varbit.music_current_playlist")

    private val Player.shuffleOnManualSelect by
        boolVarBit("varbit.use_shuffle_mode_on_manual_music_selection")

    public fun unlockAndPlay(player: Player, musicRow: MusicRow) {
        val music = getUnlockableOrThrow(musicRow)
        unlockAndPlay(player, music)
    }

    private fun unlockAndPlay(player: Player, music: Music) {
        unlock(player, music)
        play(player, music)
    }

    public fun enable(player: Player) {
        val current = repo.forId(player.currMusicId)
        if (current != null) {
            player.currMusicId = 0
            play(player, current)
            return
        }

        val previous = repo.forId(player.lastMusicId)
        if (previous != null) {
            play(player, previous)
        } else {
            playNext(player)
        }
    }

    public fun play(player: Player, musicRow: MusicRow) {
        val music = getOrThrow(musicRow)
        play(player, music)
    }

    private fun play(player: Player, music: Music) {
        val currMusicId = player.currMusicId

        // Attempting to play music that is already playing does not restart the midi.
        val alreadyPlaying = currMusicId == music.id
        if (!alreadyPlaying) {
            player.musicClocks = 0
            player.musicDuration = music.duration
        }
        player.currMusicId = music.id
        player.currentTrackRow = music.rowId

        val fadeSpeed = if (currMusicId == 0) 0 else MUSIC_PLAY_FADE
        player.midiSong(music.midi.id, fadeOutSpeed = fadeSpeed, fadeInDelay = fadeSpeed)
        player.ifSetText("component.music:now_playing_text", music.displayName)
    }

    /**
     * Plays [music] because the player picked it in the music tab. Returns `false` without playing
     * anything when the track has not been unlocked.
     *
     * A manual pick switches the player to [MusicPlayMode.Manual] so the track loops, or to
     * [MusicPlayMode.Random] when the "use shuffle mode on manual music selection" setting is on.
     */
    public fun playSelected(player: Player, music: Music): Boolean {
        if (!hasUnlocked(player, music)) {
            return false
        }
        player.playMode =
            if (player.shuffleOnManualSelect) MusicPlayMode.Random else MusicPlayMode.Manual
        play(player, music)
        return true
    }

    /**
     * Switches the player to [mode] the way the music tab's mode buttons do: area mode picks up
     * the music of the area the player stands in, shuffle mode starts a random unlocked track and
     * single mode keeps whatever is playing so it can loop.
     */
    public fun selectMode(player: Player, mode: MusicPlayMode) {
        if (player.playMode == mode) {
            return
        }
        player.playMode = mode
        when (mode) {
            MusicPlayMode.Area -> playNextArea(player)
            MusicPlayMode.Random -> playNextRandom(player)
            MusicPlayMode.Manual -> {}
        }
    }

    /** Moves on to the next track in area or shuffle mode; the "Skip Track" tab button. */
    public fun skipTrack(player: Player) {
        when (player.playMode) {
            MusicPlayMode.Area -> playNextArea(player)
            MusicPlayMode.Random -> playNextRandom(player)
            MusicPlayMode.Manual -> {}
        }
    }

    /** Called when the playing track has run for its full duration. */
    public fun trackEnded(player: Player) {
        if (player.playMode != MusicPlayMode.Manual) {
            stop(player)
            return
        }
        val music = repo.forId(player.currMusicId)
        if (music == null) {
            stop(player)
            return
        }
        player.currMusicId = 0 // Sends the midi song again so the client restarts it.
        play(player, music)
    }

    public fun resume(player: Player) {
        val music = repo.forId(player.currMusicId)
        if (music == null) {
            stop(player)
            setEmptyMusicText(player)
            return
        }
        player.currMusicId = 0 // Sends the midi song without fading.
        play(player, music)
    }

    public fun stop(player: Player) {
        player.lastMusicId = player.currMusicId
        player.currMusicId = 0
        player.musicClocks = 0
        player.musicDuration = 0
        player.currentTrackRow = NO_TRACK_ROW
        player.midiSong("midi.stop_music", fadeOutSpeed = MUSIC_END_FADE)
        if (player.playMode == MusicPlayMode.Manual) {
            setEmptyMusicText(player)
        }
    }

    /** Re-sends the "Playing:" text, e.g. when the music tab is (re)opened. */
    public fun updateNowPlayingText(player: Player) {
        val music = repo.forId(player.currMusicId)
        if (music == null) {
            setEmptyMusicText(player)
        } else {
            player.ifSetText("component.music:now_playing_text", music.displayName)
        }
    }

    public fun unlock(player: Player, musicRow: MusicRow) {
        val music = getUnlockableOrThrow(musicRow)
        unlock(player, music)
    }

    private fun unlock(player: Player, music: Music) {
        val varp = music.unlockVarp ?: error("Music cannot be unlocked: '${music.displayName}'")
        if (hasUnlocked(player, music)) {
            return
        }
        val unlockValue = player.vars[varp] or music.unlockBitflag
        player.sendUnlockMessage(music)
        VarPlayerIntMapSetter.set(player, varp, unlockValue)
    }

    public fun hasUnlocked(player: Player, musicRow: MusicRow): Boolean {
        val music = getOrThrow(musicRow)
        return hasUnlocked(player, music)
    }

    public fun hasUnlocked(player: Player, music: Music): Boolean {
        val varp = music.unlockVarp ?: return false
        val unlockedValue = player.vars[varp] and music.unlockBitflag
        return unlockedValue != 0
    }

    public fun isInPlaylist(player: Player, playlist: Int, music: Music): Boolean {
        return playlistSlot(player, playlist, music) != null
    }

    /** The tracks stored in [playlist] (`1..3`), in slot order. */
    public fun playlistTracks(player: Player, playlist: Int): List<Music> {
        require(playlist in 1..PLAYLIST_COUNT) { "Playlist must be in range 1..$PLAYLIST_COUNT." }
        val tracks = ArrayList<Music>()
        for (slot in 1..PLAYLIST_SIZE) {
            val value = player.vars[playlistSlotVarbit(playlist, slot)]
            if (value == 0) {
                continue
            }
            val music = repo.getAll().firstOrNull { it.canUnlock && it.playlistSlotValue == value }
            if (music != null) {
                tracks += music
            }
        }
        return tracks
    }

    /** Adds [music] to [playlist]; returns `false` when the playlist has no free slot. */
    public fun addToPlaylist(player: Player, playlist: Int, music: Music): Boolean {
        require(music.canUnlock) { "Only unlockable tracks can be stored: '${music.displayName}'" }
        if (isInPlaylist(player, playlist, music)) {
            return true
        }
        val free = (1..PLAYLIST_SIZE).firstOrNull { player.vars[playlistSlotVarbit(playlist, it)] == 0 }
        if (free == null) {
            return false
        }
        VarPlayerIntMapSetter.set(player, playlistSlotVarbit(playlist, free), music.playlistSlotValue)
        return true
    }

    public fun removeFromPlaylist(player: Player, playlist: Int, music: Music) {
        val slot = playlistSlot(player, playlist, music) ?: return
        VarPlayerIntMapSetter.set(player, playlistSlotVarbit(playlist, slot), 0)
    }

    private fun playlistSlot(player: Player, playlist: Int, music: Music): Int? {
        require(playlist in 1..PLAYLIST_COUNT) { "Playlist must be in range 1..$PLAYLIST_COUNT." }
        if (!music.canUnlock) {
            return null
        }
        val value = music.playlistSlotValue
        return (1..PLAYLIST_SIZE).firstOrNull { player.vars[playlistSlotVarbit(playlist, it)] == value }
    }

    private fun playlistSlotVarbit(playlist: Int, slot: Int): String =
        "varbit.music_playlist_${playlist}_track_$slot"

    private fun setEmptyMusicText(player: Player) {
        player.ifSetText("component.music:now_playing_text", " ")
    }

    private fun Player.sendUnlockMessage(music: Music) {
        if (unlockMessageDisabled) {
            return
        }
        val color = chatMesColorTag(opaque = "e00a19", transparent = "ff3045")
        mes("You have unlocked a new music track: $color${music.displayName}")
    }

    private fun getUnlockableOrThrow(row: MusicRow): Music {
        val music = getOrThrow(row)
        if (music.unlockVarp == null) {
            error("This music can be played but not unlocked: '${music.displayName}'")
        }
        return music
    }

    private fun getOrThrow(row: MusicRow): Music {
        return repo.forRow(row) ?: error("DbRow is not a valid music row: '${row.rowId}'")
    }

    public fun playNext(player: Player) {
        when (player.playMode) {
            MusicPlayMode.Area -> playNextArea(player)
            MusicPlayMode.Random -> playNextRandom(player)
            MusicPlayMode.Manual -> loopSelected(player)
        }
    }

    private fun loopSelected(player: Player) {
        val music = repo.forId(player.lastMusicId) ?: return
        play(player, music)
    }

    private fun playNextArea(player: Player) {
        if (player.currMusicArea == 0) {
            return
        }
        val area = RSCM.getReverseMapping(RSCMType.AREA, player.currMusicArea - 1)
        when (player.areaMode) {
            MusicAreaMode.Modern -> {
                val modernMusic = repo.getModernArea(area)
                if (modernMusic != null) {
                    // TODO(emulation): Does this unlock all tracks if they haven't already? Or
                    //  only the shuffled track that is selected to play? Will need to enter an
                    //  area with 3+ music tracks in classic mode, then switch to modern and wait
                    //  for the classic track to complete and see if the rest unlock all in one go
                    //  or one at a time as they play.
                    playShuffled(player, modernMusic)
                }
            }
            MusicAreaMode.Classic -> {
                val classicMusic = repo.getClassicArea(area)
                if (classicMusic != null && player.lastMusicId != classicMusic.id) {
                    unlockAndPlay(player, classicMusic)
                } else {
                    setEmptyMusicText(player)
                }
            }
        }
    }

    private fun playNextRandom(player: Player) {
        val playlist = player.currentPlaylist
        val candidates =
            if (playlist in 1..PLAYLIST_COUNT) playlistTracks(player, playlist) else repo.getAll()
        val unlocked = candidates.filter { hasUnlocked(player, it) && it.id != player.currMusicId }
        val next =
            random.pickOrNull(unlocked)
                ?: repo.forId(player.currMusicId)
                ?: repo.forId(player.lastMusicId)
                ?: repo.getAll().first()
        play(player, next)
    }

    public fun enterArea(player: Player, area: String) {
        player.musicPlaylist = 0
        when (player.areaMode) {
            MusicAreaMode.Modern -> {
                val modernMusic = repo.getModernArea(area)
                if (modernMusic != null) {
                    player.currMusicArea = area.asRSCM(RSCMType.AREA) + 1
                    unlockAndPlayShuffled(player, modernMusic)
                }
            }
            // Note: When entering a modern area with classic music mode, it does _not_ unlock
            // the modern tracks.
            MusicAreaMode.Classic -> {
                val classicMusic = repo.getClassicArea(area)
                if (classicMusic != null) {
                    player.currMusicArea = area.asRSCM(RSCMType.AREA) + 1
                    unlockAndPlay(player, classicMusic)
                }
            }
        }
    }

    private fun unlockAndPlayShuffled(player: Player, musicList: List<Music>) {
        for (music in musicList) {
            unlock(player, music)
        }
        if (player.playMode == MusicPlayMode.Area) {
            playShuffled(player, musicList)
        }
    }

    // Note: This uses a seeded random "music playlist" to keep a shuffled list of music tracks
    // when using the modern music area mode. This playlist will always keep the first music track
    // the same and shuffle the rest.
    private fun playShuffled(player: Player, musicList: List<Music>) {
        if (player.musicPlaylist == 0) {
            player.musicPlaylist = MusicPlaylist.create(random).packed
        }
        val playlist = MusicPlaylist(player.musicPlaylist)
        val music = playlist.getShuffledTrack(musicList)
        unlockAndPlay(player, music)

        val next = playlist.nextPosition(musicList.size)
        player.musicPlaylist = next.packed
    }

    public fun exitArea(player: Player, area: String) {
        if (player.currMusicArea != area.asRSCM(RSCMType.AREA) + 1) {
            return
        }
        player.currMusicArea = 0
        player.musicPlaylist = 0
        if (player.playMode == MusicPlayMode.Area) {
            stop(player)
        }
    }

    public companion object {
        public const val MUSIC_PLAY_FADE: Int = 60
        public const val MUSIC_END_FADE: Int = 20

        /** The number of custom playlists the music tab offers. */
        public const val PLAYLIST_COUNT: Int = 3

        /** The number of tracks a custom playlist can hold. */
        public const val PLAYLIST_SIZE: Int = 100

        /** `varp.music_current_track` value the music tab treats as "nothing playing". */
        private const val NO_TRACK_ROW: Int = -1
    }
}
