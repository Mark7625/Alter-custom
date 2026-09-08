package dev.openrune.impl

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType

public const val Z_BIT_COUNT: Int = 14
public const val X_BIT_COUNT: Int = 14

public const val Z_BIT_OFFSET: Int = 0
public const val X_BIT_OFFSET: Int = Z_BIT_OFFSET + Z_BIT_COUNT

public const val Z_BIT_MASK: Int = (1 shl Z_BIT_COUNT) - 1
public const val X_BIT_MASK: Int = (1 shl X_BIT_COUNT) - 1
public const val LENGTH: Int = 64

fun packVertex(mx: Int, mz: Int, lx: Int, lz: Int): Int {
    require(mx in 0..X_BIT_MASK)
    require(mz in 0..Z_BIT_MASK)
    require(lx in 0..X_BIT_MASK)
    require(lz in 0..Z_BIT_MASK)

    val x = mx * LENGTH + lx
    val z = mz * LENGTH + lz

    require(x in 0..X_BIT_MASK)
    require(z in 0..Z_BIT_MASK)

    return ((x and X_BIT_MASK) shl X_BIT_OFFSET) or ((z and Z_BIT_MASK) shl Z_BIT_OFFSET)
}

object Music {

    /**
     * A music area and the tracks that shuffle inside it. [area] is the bare area gameval name
     * (`area.<area>` must exist in `.data/gamevals/area.rscm` and have a polygon under
     * `.data/raw-cache/map/area`), and [tracks] are `dbrow.music_*` rows from the cache.
     */
    private data class ModernArea(val row: String, val area: String, val tracks: List<String>)

    private fun modern(area: String, vararg tracks: String) =
        ModernArea("dbrow.music_modern_$area", area, tracks.map { "dbrow.music_$it" })

    /** Track lists follow the OSRS wiki "Music" section of each location. */
    private val MODERN_AREAS =
        listOf(
            modern("lumbridge", "autumn_voyage", "book_of_spells", "dream", "flute_salad", "harmony", "yesteryear"),
            modern("draynor_village", "start", "unknown_land", "wander"),
            modern("draynor_manor", "spooky"),
            modern("wizards_tower", "vision"),
            modern("zanaris", "faerie", "crystal_cave"),
            modern(
                "varrock",
                "adventure",
                "garden",
                "medieval",
                "spirit",
                "looking_back",
                "doorways",
                "expanse",
                "greatness",
                "still_night",
            ),
            // The Grand Exchange plays its own track; its footprint is carved out of the Varrock
            // polygon so the two areas never overlap.
            modern("grand_exchange", "the_trade_parade"),
            modern("barbarian_village", "barbarianism"),
            modern("edgeville", "forever"),
            // The Wilderness gameplay area already has an onArea script, and an area may only have
            // one, so its music lives on a twin area with the same polygon.
            modern(
                "wilderness_music",
                "army_of_darkness",
                "close_quarters",
                "dangerous",
                "everlasting_fire",
                "faithless",
                "forbidden",
                "inspiration",
                "moody",
                "pirates_of_peril",
                "regal",
                "scape_sad",
                "scape_wild",
                "shining",
                "troubled",
                "undercurrent",
                "underground",
                "wild_isle",
                "wild_side",
                "wilderness",
                "wilderness2",
                "wilderness3",
                "witching",
                "wonder",
            ),
            modern("falador", "arrival", "fanfare", "workshop"),
            modern("falador_south", "long_way_home", "miles_away", "nightfall", "wander"),
            modern("falador_north", "lightness", "scape_soft"),
            modern("taverley", "horizon", "splendour"),
            modern("rimmington", "attention", "emperor", "long_way_home"),
            modern("burthorpe", "principality", "kingdom"),
            modern("goblin_village", "goblin_village", "too_many_cooks"),
            modern("ice_mountain", "alone"),
            modern("asgarnian_ice_dungeon", "starlight", "woe_of_the_wyvern"),
            modern("taverley_dungeon", "arabique", "courage", "dunjun", "royale", "underground"),
            modern("catherby", "fishing"),
            modern("seers_village", "overture", "prime_time", "twilight"),
            modern("camelot", "camelot"),
            modern("rellekka", "rellekka"),
            modern("brineratcavern", "rising_damp"),
            modern("barbarian_outpost", "legion"),
            modern("al_kharid", "al_kharid"),
            modern("port_sarim", "sea_shanty2"),
            modern("mudskipper_point", "mudskipper_melody"),
            modern("fishing_guild", "mellow"),
            modern("east_ardougne", "baroque", "knightly", "the_tower"),
            modern("west_ardougne", "sad_meadow"),
            modern("ardougne_sewers", "the_cellar_dwellers"),
            modern("south_ardougne", "ballad_of_enchantment", "upcoming"),
            modern("legends_guild", "trinity"),
            modern("mourner_tunnels", "fight_or_flight"),
            modern("emirs_arena_music", "duelarena"),
            modern("mage_training_arena", "shine"),
            // Since the February 2026 music rework the three stronghold tracks shuffle everywhere
            // inside the walls, while Gnomeball is confined to the ball field, which is carved out
            // of the stronghold polygon.
            modern("tree_gnome_stronghold", "gnome_king", "gnome_village", "gnome_village2"),
            modern("gnome_ball_field", "gnomeball"),
            modern("yanille", "big_chords", "magic_dance"),
            modern("yanille_chain", "long_ago"),
            modern("yanille_agility_dungeon", "cavern"),
            // "Castle Wars" plays in the lobby and the arena; "Ready for Battle" in the waiting
            // rooms and the tunnels underneath.
            modern("castle_wars", "castlewars"),
            modern("castle_wars_underground", "ready_for_battle"),
        )

    fun musicModern() =
        dbTable("dbtable.music_modern", serverOnly = true) {
            column("area", 0, VarType.STRING)
            column("tracks", 1, VarType.DBROW)
            column("auto_script", 2, VarType.BOOLEAN)

            for (entry in MODERN_AREAS) {
                row(entry.row) {
                    column(0, entry.area)
                    columnRSCM(1, *entry.tracks.toTypedArray())
                    column(2, true)
                }
            }
        }

    fun musicClassic() =
        dbTable("dbtable.music_classic", serverOnly = true) {
            column("area", 0, VarType.AREA)
            column("track", 1, VarType.DBROW)
            column("auto_script", 2, VarType.BOOLEAN)
        }
}
