package org.rsmod.api.player.music

import org.rsmod.api.utils.vars.VarEnumDelegate

/**
 * The `varp.musicplay` values the music tab's client scripts write when one of its mode buttons
 * is pressed (`music_optionbuttons_setmode`): the "Area Mode" button stores `0`, "Shuffle Mode"
 * stores `1` and "Single Mode" stores `2`.
 */
public enum class MusicPlayMode(override val varValue: Int) : VarEnumDelegate {
    /** Plays the tracks of the music area the player is standing in. */
    Area(0),

    /** Plays random unlocked tracks. */
    Random(1),

    /** Loops the track the player picked in the music tab. */
    Manual(2),
}
