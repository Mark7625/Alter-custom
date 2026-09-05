package org.rsmod.content.other.levelup

import net.rsprot.protocol.game.outgoing.sound.MidiJingle
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.game.entity.Player

private var Player.musicClocks: Int by intVarBit("varbit.music_curr_clocks")

/**
 * Sends `MIDI_JINGLE` with the id the **client** understands, which is not the id `RSCMType.JINGLE`
 * resolves to.
 *
 * [org.rsmod.api.player.midiJingle] passes the RSCM id straight into the packet, and for jingles
 * that id is meaningless to the client. `jingle.advance_attack` resolves to 1177; the client looks
 * that up as a group in js5 archive 11, which in this cache stops at 864. Nothing to play, so
 * nothing played - which is why levelling up was silent while the fireworks and the box worked.
 *
 * #### Why the name cannot be turned into an id
 *
 * The cache's gameval table for sounds is one flat list of 1197 names covering both music and
 * jingles, and its ids are their own space. 435 of the music names can be recovered from archive 6's
 * name hashes (`String.hashCode` of the original name), and for **every single one** the gameval id
 * differs from the group id - `claustrophobia` is gameval 2 but group 373, `sea_minor_shanty` is
 * gameval 21 but group 683. It is not an offset or any orderly permutation. Archive 11 carries no
 * name hashes at all, so nothing in the cache maps a jingle name to a group id.
 *
 * [IDS] therefore holds the numbers directly. See there for how they were found.
 */
internal object Jingles {
    /**
     * Archive 11 group ids for each level-up jingle, keyed by the RSCM name [LevelUpJingles] uses.
     *
     * Nothing in the cache maps a jingle name to a group id - archive 11's js5 index sets no name
     * flag, the files embed no names, and no dbtable, dbcol or param references one. A published
     * 317-era list was tried and is wrong for revision 240; the sound archives were renumbered
     * somewhere between the two.
     *
     * #### The `advance_*` batch
     *
     * The `advance_*` jingles sit in one contiguous alphabetical batch starting at group 28, all
     * 377-2550 bytes decompressed against much larger files either side. Group 28 was confirmed
     * in-game as the Agility level up and 29 as Attack, and Agility and Attack are alphabetically
     * first among those names, which fixes the run.
     *
     * The batch holds 43 names, not the 46 the cache lists: `advance_sailing`,
     * `advance_sailing2` and `advance_sailing_charting` are absent. Sailing is far newer than the
     * rest, so its jingles were appended to the end of the archive rather than inserted into a
     * batch laid down years earlier. Sailing sorts after `advance_runecraft2`, so only groups 61
     * and up moved. The ordering is only alphabetical *within* the batch - the archive as a whole
     * is not sorted.
     *
     * #### Everything outside the batch: match durations against the wiki
     *
     * Farming uses `farming_levelup` / `farming_levelup_2` rather than an `advance_` name, and
     * Sailing's three were appended years later, so neither is in the batch. They were found by
     * decoding every group in archive 11 - the files are Jagex's packed MIDI, which the client's
     * `MusicTrack` constructor turns back into standard MIDI - and computing each one's playing
     * time from its tempo events. The wiki's jingle recordings list their length to a tenth of a
     * second, and the two agree exactly on every group already known (28, 29, 30, 69, 70), so a
     * length is as good as a name:
     * - Farming Level Up! is 3.9 s and the unlocks variant 6.9 s. Groups 10 and 11 are the only
     *   groups in the archive with those lengths, they are adjacent, and they share a tempo and
     *   resolution no other group nearby has: a pair authored together.
     * - Sailing's three groups are 830-832, the only ones whose js5 version is a timestamp
     *   rather than a small edit count. 831 (6.4 s) matches Sailing Level Up!, 832 (9.9 s) the
     *   99 jingle, and 830 (4.1 s) is the charting jingle by elimination.
     *
     * Only the level-up jingles are listed. Any other jingle can be found the same way, or by
     * ear with `::jingle`.
     */
    private val IDS: Map<String, Int> =
        mapOf(
            "jingle.farming_levelup" to 10,
            "jingle.farming_levelup_2" to 11,
            "jingle.advance_agility" to 28,
            "jingle.advance_attack" to 29,
            "jingle.advance_attack2" to 30,
            "jingle.advance_carpentry" to 31,
            "jingle.advance_carpentry2" to 32,
            "jingle.advance_cooking" to 33,
            "jingle.advance_cooking2" to 34,
            "jingle.advance_crafting" to 35,
            "jingle.advance_crafting2" to 36,
            "jingle.advance_defense" to 37,
            "jingle.advance_defense2" to 38,
            "jingle.advance_firemarking" to 39,
            "jingle.advance_firemarking2" to 40,
            "jingle.advance_fishing" to 41,
            "jingle.advance_fishing2" to 42,
            "jingle.advance_fletching" to 43,
            "jingle.advance_fletching2" to 44,
            "jingle.advance_herblaw" to 45,
            "jingle.advance_herblaw2" to 46,
            "jingle.advance_hitpoints" to 47,
            "jingle.advance_hitpoints2" to 48,
            "jingle.advance_hunting" to 49,
            "jingle.advance_hunting2" to 50,
            "jingle.advance_magic" to 51,
            "jingle.advance_magic2" to 52,
            "jingle.advance_mining" to 53,
            "jingle.advance_mining2" to 54,
            "jingle.advance_prayer" to 55,
            "jingle.advance_prayer2" to 56,
            "jingle.advance_ranged" to 57,
            "jingle.advance_ranged2" to 58,
            "jingle.advance_runecraft" to 59,
            "jingle.advance_runecraft2" to 60,
            "jingle.advance_slayer" to 61,
            "jingle.advance_slayer2" to 62,
            "jingle.advance_smithing" to 63,
            "jingle.advance_smithing2" to 64,
            "jingle.advance_strength" to 65,
            "jingle.advance_strength2" to 66,
            "jingle.advance_thieving" to 67,
            "jingle.advance_thieving2" to 68,
            "jingle.advance_woodcutting" to 69,
            "jingle.advance_woodcutting2" to 70,
            "jingle.advance_sailing_charting" to 830,
            "jingle.advance_sailing" to 831,
            "jingle.advance_sailing2" to 832,
        )

    /** The archive 11 group id for [jingle], or `null` if it has not been identified. */
    fun idOf(jingle: String): Int? = IDS[jingle]

    /** Plays [jingle] on [player]; does nothing if that jingle's id is unknown. */
    fun play(player: Player, jingle: String) {
        val id = idOf(jingle) ?: return
        playRaw(player, id)
    }

    /** Plays archive 11 group [id] directly, for identifying jingles by ear. */
    fun playRaw(player: Player, id: Int) {
        player.musicClocks = 0 // The client restarts the current song once the jingle finishes.
        player.client.write(MidiJingle(id))
    }
}
