package org.rsmod.api.player

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import net.rsprot.protocol.game.outgoing.sound.MidiJingle
import net.rsprot.protocol.game.outgoing.sound.MidiSongV2
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.game.entity.Player

internal var Player.musicClocks by intVarBit("varbit.music_curr_clocks")

/** @see [MidiJingle] */
public fun Player.midiJingle(jingle: String) {
    musicClocks = 0 // Client restarts music when a jingle is played.
    client.write(MidiJingle(jingle.asRSCM(RSCMType.JINGLE)))
}

/**
 * Plays the jingle stored as js5 archive 11 group [group].
 *
 * The client looks a jingle up by its archive group, which is not the id the `jingle.*` gameval
 * resolves to (the sound gameval table numbers music and jingles in its own space), so a jingle
 * whose group has been identified by ear or by duration is played through this overload.
 */
public fun Player.midiJingle(group: Int) {
    musicClocks = 0 // Client restarts music when a jingle is played.
    client.write(MidiJingle(group))
}

/** @see [MidiSongV2] */
public fun Player.midiSong(
    midi: String,
    fadeOutDelay: Int = 0,
    fadeOutSpeed: Int = 0,
    fadeInDelay: Int = 0,
    fadeInSpeed: Int = 0,
) {
    client.write(MidiSongV2(midi.asRSCM(RSCMType.MIDI), fadeOutDelay, fadeOutSpeed, fadeInDelay, fadeInSpeed))
}

public fun Player.midiSong(
    midi: Int,
    fadeOutDelay: Int = 0,
    fadeOutSpeed: Int = 0,
    fadeInDelay: Int = 0,
    fadeInSpeed: Int = 0,
) {
    client.write(MidiSongV2(midi, fadeOutDelay, fadeOutSpeed, fadeInDelay, fadeInSpeed))
}
