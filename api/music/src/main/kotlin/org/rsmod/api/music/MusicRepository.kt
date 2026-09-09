package org.rsmod.api.music

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import it.unimi.dsi.fastutil.ints.Int2ObjectMap
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import jakarta.inject.Inject
import org.rsmod.api.random.GameRandom
import org.rsmod.api.table.MusicModernRow
import org.rsmod.api.table.MusicRow

public class MusicRepository @Inject constructor(private val random: GameRandom) {
    private lateinit var musicRows: Int2ObjectMap<Music>
    private lateinit var musicIds: Int2ObjectMap<Music>
    private lateinit var jukebox: List<Music>

    private lateinit var modernAreas: Int2ObjectMap<List<Music>>
    private lateinit var classicAreas: Int2ObjectMap<Music>

    public fun forRow(row: MusicRow): Music? = musicRows[row.rowId]

    public fun forRowId(rowId: Int): Music? = musicRows[rowId]

    public fun forId(id: Int): Music? = musicIds[id]

    /**
     * The tracks in the order the music tab lists them. The client builds the jukebox from
     * `db_find(dbcol.music:hidden = 0)` and the comsub it sends when a track is clicked is the
     * index into that result, so this is every non-hidden row by ascending row id.
     */
    public fun jukeboxTracks(): List<Music> = jukebox

    public fun jukeboxTrack(index: Int): Music? = jukebox.getOrNull(index)

    public fun getModernArea(area: String): List<Music>? {
        return modernAreas[area.asRSCM(RSCMType.AREA)]
    }

    public fun getClassicArea(area: String): Music? {
        return classicAreas[area.asRSCM(RSCMType.AREA)]
    }

    public fun getAll(): Collection<Music> {
        return musicRows.values
    }

    public fun load() {
        val unlockVarps = unlockVarps()

        val musicRows = loadMusicRows(unlockVarps)
        this.musicRows = Int2ObjectOpenHashMap(musicRows)

        val musicSlots = mapMusicById(musicRows)
        this.musicIds = Int2ObjectOpenHashMap(musicSlots)

        this.jukebox = musicRows.values.filterNot(Music::hidden).sortedBy(Music::rowId)

        val modernAreas = loadModernAreas(musicRows)
        this.modernAreas = Int2ObjectOpenHashMap(modernAreas)

        val classicAreas = loadClassicAreas(musicRows)
        this.classicAreas = Int2ObjectOpenHashMap(classicAreas)
    }

    private fun loadMusicRows(unlockVarps: List<String>): Map<Int, Music> {
        val rows = MusicRow.all()
        val mapped = HashMap<Int, Music>(rows.size)
        var currId = 1
        for (row in rows) {
            val variable = row.variable
            var unlockVarp: String? = null
            var unlockVarpIndex = 0
            var unlockBitpos = -1
            if (variable.isNotEmpty()) {
                unlockVarp = unlockVarps.getOrNull(variable[0] - 1)
                unlockVarpIndex = variable[0]
                unlockBitpos = variable[1]
            }
            mapped[row.rowId] =
                Music(
                    id = currId++,
                    rowId = row.rowId,
                    displayName = row.displayname,
                    unlockHint = row.unlockhint,
                    duration = row.duration,
                    midi = row.midi,
                    unlockVarp = unlockVarp,
                    unlockVarpIndex = unlockVarpIndex,
                    unlockBitpos = unlockBitpos,
                    hidden = row.hidden ?: false,
                    secondary = null,
                )
        }
        return mapped
    }

    private fun mapMusicById(musicRows: Map<Int, Music>): Map<Int, Music> {
        return musicRows.values.associateBy(Music::id)
    }

    private fun loadModernAreas(musicRows: Map<Int, Music>): Map<Int, List<Music>> {
        val grouped = mutableMapOf<Int, MutableList<Music>>()

        MusicModernRow.all().forEach {
            val area = "area.${it.area}".asRSCM(RSCMType.AREA)
            val trackRows = it.tracks
            val musicList = ArrayList<Music>(trackRows.size)
            for (trackRow in trackRows) {
                val music = musicRows[trackRow.rowId] ?: continue
                musicList += music
            }
            val mappedList = grouped.computeIfAbsent(area) { mutableListOf() }
            mappedList += musicList
        }

        return grouped
    }

    private fun loadClassicAreas(musicRows: Map<Int, Music>): Map<Int, Music> {
        return emptyMap()
    }

    private fun unlockVarps(): List<String> =
        listOf(
            "varp.musicmulti_1",
            "varp.musicmulti_2",
            "varp.musicmulti_3",
            "varp.musicmulti_4",
            "varp.musicmulti_5",
            "varp.musicmulti_6",
            "varp.musicmulti_7",
            "varp.musicmulti_8",
            "varp.musicmulti_9",
            "varp.musicmulti_10",
            "varp.musicmulti_11",
            "varp.musicmulti_12",
            "varp.musicmulti_13",
            "varp.musicmulti_14",
            "varp.musicmulti_15",
            "varp.musicmulti_16",
            "varp.musicmulti_17",
            "varp.musicmulti_18",
            "varp.musicmulti_19",
            "varp.musicmulti_20",
            "varp.musicmulti_21",
            "varp.musicmulti_22",
            "varp.musicmulti_23",
            "varp.musicmulti_24",
            "varp.musicmulti_25",
            "varp.musicmulti_26",
            "varp.musicmulti_27",
        )
}
