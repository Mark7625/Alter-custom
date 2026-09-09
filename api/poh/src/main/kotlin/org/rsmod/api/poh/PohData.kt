package org.rsmod.api.poh

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import jakarta.inject.Singleton

/** Destination level a room occupies inside the house region. */
public enum class PohFloor(public val destLevel: Int) {
    DUNGEON(0),
    GROUND(1),
    UPPER(2),
}

/** Open room edges; ids match the loc rotation baked into the template door hotspots. */
public enum class PohDoorEdge(public val id: Int) {
    WEST(0),
    NORTH(1),
    EAST(2),
    SOUTH(3);

    /** The edge as seen from the room on the other side of the wall. */
    public val opposite: PohDoorEdge
        get() =
            when (this) {
                WEST -> EAST
                NORTH -> SOUTH
                EAST -> WEST
                SOUTH -> NORTH
            }

    /** Rotates this edge by [rotation] quarter-turns clockwise. */
    public fun rotate(rotation: Int): PohDoorEdge = entries[(id + rotation) and 0x3]

    /** Grid delta towards the adjacent room across this edge. */
    public val deltaX: Int
        get() =
            when (this) {
                WEST -> -1
                EAST -> 1
                else -> 0
            }

    public val deltaZ: Int
        get() =
            when (this) {
                SOUTH -> -1
                NORTH -> 1
                else -> 0
            }
}

/** One of the 13 house style slots on the template map squares. */
public data class PohStyle(
    val index: Int,
    val key: String,
    val region: Int,
    val extraRegion: Int,
    val level: Int,
    val doorLeftLoc: String,
    val doorRightLoc: String,
) {
    /** The style's real openable doors and wall filler shown outside building mode. */
    val doors: PohStyleDoors
        get() = PohStyleDoors.forStyle(key)
}

/**
 * The real door, wall, and window locs a style swaps in for its template locs: open passages
 * between rooms get the closed door pair (captures: `civitas_poh_door_l/r` in rsprox-289/294),
 * walled-off door edges get the style's plain wall piece, and the `poh_dynamic_window` placeholders
 * baked into the room templates get the style's real wall window (Zenyte `refreshWindows`, applied
 * in all modes). Loc names verified against the merged gamevals and `osrs-dumps/config/dump.loc`;
 * the wilderness slot has no wall set of its own and borrows the basic-stone brick wall and window.
 */
public data class PohStyleDoors(
    val closedLeft: String,
    val closedRight: String,
    val openLeft: String,
    val openRight: String,
    val wallFiller: String,
    val window: String,
) {
    public companion object {
        private val STYLE_DOORS: Map<String, PohStyleDoors> =
            mapOf(
                "rimmington" to
                    PohStyleDoors(
                        closedLeft = "loc.village_door_l",
                        closedRight = "loc.village_door_r",
                        openLeft = "loc.village_door_l_open",
                        openRight = "loc.village_door_r_open",
                        wallFiller = "loc.village_wall",
                        window = "loc.village_wall_window",
                    ),
                "lumbridge" to
                    PohStyleDoors(
                        closedLeft = "loc.poordoor_double_inner",
                        closedRight = "loc.poordoor_doubler_inner",
                        openLeft = "loc.openpoordoor_double_inner",
                        openRight = "loc.openpoordoor_doubler_inner",
                        wallFiller = "loc.brickwall_nosharelight",
                        window = "loc.brickwall_window",
                    ),
                "pollnivneach" to
                    PohStyleDoors(
                        closedLeft = "loc.desert_door_l",
                        closedRight = "loc.desert_door_r",
                        openLeft = "loc.desert_door_l_open",
                        openRight = "loc.desert_door_r_open",
                        wallFiller = "loc.desertwall_nosharelight",
                        window = "loc.desert_wall_window",
                    ),
                "rellekka" to
                    PohStyleDoors(
                        closedLeft = "loc.rellekka_poh_doubledoorl",
                        closedRight = "loc.rellekka_poh_doubledoor",
                        openLeft = "loc.rellekka_poh_doubledoorl_open",
                        openRight = "loc.rellekka_poh_doubledoor_open",
                        wallFiller = "loc.viking_longhall_wall_inner",
                        window = "loc.viking_longhall_wall_window_inner",
                    ),
                "brimhaven" to
                    PohStyleDoors(
                        closedLeft = "loc.timberwall_doorl",
                        closedRight = "loc.timberwall_door",
                        openLeft = "loc.timberwall_doorl_open",
                        openRight = "loc.timberwall_door_open",
                        wallFiller = "loc.timberwall",
                        window = "loc.timberwall_with_window_2",
                    ),
                "yanille" to
                    PohStyleDoors(
                        closedLeft = "loc.yanille_poh_double_doorl",
                        closedRight = "loc.yanille_poh_double_door",
                        openLeft = "loc.yanille_poh_double_doorl_open",
                        openRight = "loc.yanille_poh_double_door_open",
                        wallFiller = "loc.yanille_poh_wall",
                        window = "loc.yanille_poh_wall_window",
                    ),
                "deathly" to
                    PohStyleDoors(
                        closedLeft = "loc.deathly_poh_double_doorl",
                        closedRight = "loc.deathly_poh_double_door",
                        openLeft = "loc.deathly_poh_double_doorl_open",
                        openRight = "loc.deathly_poh_double_door_open",
                        wallFiller = "loc.deathly_poh_wall",
                        window = "loc.deathly_poh_wall_window",
                    ),
                "twisted" to
                    PohStyleDoors(
                        closedLeft = "loc.twisted_poh_doubledoorl",
                        closedRight = "loc.twisted_poh_doubledoor",
                        openLeft = "loc.twisted_poh_doubledoorl_open",
                        openRight = "loc.twisted_poh_doubledoor_open",
                        wallFiller = "loc.twisted_poh_wall_inner",
                        window = "loc.twisted_poh_wall_window_inner",
                    ),
                "hosidius" to
                    PohStyleDoors(
                        closedLeft = "loc.hosidius_poh_doubledoorl",
                        closedRight = "loc.hosidius_poh_doubledoor",
                        openLeft = "loc.hosidius_poh_doubledoorl_open",
                        openRight = "loc.hosidius_poh_doubledoor_open",
                        wallFiller = "loc.hosidius_poh_wall",
                        window = "loc.hosidius_poh_wall_window",
                    ),
                "xmas2020" to
                    PohStyleDoors(
                        closedLeft = "loc.xmas2020_poh_doubledoorl",
                        closedRight = "loc.xmas2020_poh_doubledoor",
                        openLeft = "loc.xmas2020_poh_doubledoorl_open",
                        openRight = "loc.xmas2020_poh_doubledoor_open",
                        wallFiller = "loc.xmas2020_poh_wall",
                        window = "loc.xmas2020_poh_wall_window",
                    ),
                "civitas" to
                    PohStyleDoors(
                        closedLeft = "loc.civitas_poh_door_l",
                        closedRight = "loc.civitas_poh_door_r",
                        openLeft = "loc.civitas_poh_door_l_open",
                        openRight = "loc.civitas_poh_door_r_open",
                        wallFiller = "loc.civitas_poh_wall_default",
                        window = "loc.civitas_poh_wall_window",
                    ),
                "canifis" to
                    PohStyleDoors(
                        closedLeft = "loc.canifis_poh_doubledoorl",
                        closedRight = "loc.canifis_poh_doubledoor",
                        openLeft = "loc.canifis_poh_doubledoorl_open",
                        openRight = "loc.canifis_poh_doubledoor_open",
                        wallFiller = "loc.canifis_poh_wall_plain",
                        window = "loc.canifis_poh_wall_window_inner",
                    ),
                "wilderness" to
                    PohStyleDoors(
                        closedLeft = "loc.wild_doubledoor_l",
                        closedRight = "loc.wild_doubledoor_r",
                        openLeft = "loc.wild_doubledoor_open_l",
                        openRight = "loc.wild_doubledoor_open_r",
                        wallFiller = "loc.brickwall_nosharelight",
                        window = "loc.brickwall_window",
                    ),
            )

        public fun forStyle(key: String): PohStyleDoors =
            STYLE_DOORS[key] ?: error("No door set for house style '$key'.")

        /** Every closed/open door loc of every style, for door-interaction scripts. */
        public fun allDoorLocs(): Sequence<PohStyleDoors> = STYLE_DOORS.values.asSequence()
    }
}

/** Which of the doorl/doorr pair a template door loc is (naming varies slightly per style). */
public enum class PohDoorSide {
    LEFT,
    RIGHT,
}

/** A door-hotspot wall loc baked into a room template zone. */
public data class PohDoorPlacement(
    val edge: PohDoorEdge,
    val side: PohDoorSide,
    val localX: Int,
    val localZ: Int,
    val rotation: Int,
)

/** One of the 34 room templates. */
public data class PohRoomType(
    val index: Int,
    val key: String,
    val templateRegion: Int,
    val templateZoneX: Int,
    val templateZoneZ: Int,
    val doorEdges: Set<PohDoorEdge>,
    val doorPlacements: List<PohDoorPlacement>,
    val floor: PohFloor,
) {
    /** Extra-square rooms live on the z-row-111 template squares (`style.extraRegion`). */
    val extraSquare: Boolean
        get() = templateRegion == EXTRA_TEMPLATE_REGION

    public companion object {
        public const val MAIN_TEMPLATE_REGION: Int = 7534
        public const val EXTRA_TEMPLATE_REGION: Int = 7535
    }
}

/** A single buildable hotspot placement within a room template. */
public data class PohHotspot(
    val index: Int,
    val loc: String,
    val roomKey: String,
    val name: String,
    val localX: Int,
    val localZ: Int,
    val level: Int,
    val shape: Int,
    val rotation: Int,
)

/** A `poh_dynamic_window` placeholder placement within a room template (style-0 anchored). */
public data class PohWindowPlacement(
    val roomKey: String,
    val localX: Int,
    val localZ: Int,
    val shape: Int,
    val rotation: Int,
)

/**
 * Loads the datagen resources (`poh_rooms.json`, `poh_hotspots.json`) committed by
 * `:tools:poh-datagen:run` and verifies them against `poh_index_manifest.json`.
 *
 * Room and hotspot **indices are persisted** in `character_attrs` blobs, so the manifest check
 * guarantees a regenerated table can never silently reinterpret saved houses.
 */
@Singleton
public class PohDataStore {
    public val styles: List<PohStyle>
    public val rooms: List<PohRoomType>
    public val hotspots: List<PohHotspot>
    public val windows: List<PohWindowPlacement>

    private val roomsByKey: Map<String, PohRoomType>
    private val hotspotsByRoom: Map<String, List<PohHotspot>>
    private val windowsByRoom: Map<String, List<PohWindowPlacement>>

    init {
        val roomsJson = parseResource("/poh_rooms.json")
        val hotspotsJson = parseResource("/poh_hotspots.json")
        val windowsJson = parseResource("/poh_windows.json")
        val manifest = parseResource("/poh_index_manifest.json")

        styles =
            roomsJson.getAsJsonArray("styles").map { el ->
                val obj = el.asJsonObject
                PohStyle(
                    index = obj["index"].asInt,
                    key = obj["key"].asString,
                    region = obj["region"].asInt,
                    extraRegion = obj["extraRegion"].asInt,
                    level = obj["level"].asInt,
                    doorLeftLoc = obj["doorLeftLoc"].asString,
                    doorRightLoc = obj["doorRightLoc"].asString,
                )
            }

        rooms =
            roomsJson.getAsJsonArray("rooms").map { el ->
                val obj = el.asJsonObject
                PohRoomType(
                    index = obj["index"].asInt,
                    key = obj["roomKey"].asString,
                    templateRegion = obj["templateRegion"].asInt,
                    templateZoneX = obj["templateZoneX"].asInt,
                    templateZoneZ = obj["templateZoneZ"].asInt,
                    doorEdges =
                        obj.getAsJsonArray("doorEdges")
                            .map { PohDoorEdge.valueOf(it.asString) }
                            .toSet(),
                    doorPlacements =
                        obj.getAsJsonArray("doorPlacements").map { doorEl ->
                            val door = doorEl.asJsonObject
                            PohDoorPlacement(
                                edge = PohDoorEdge.valueOf(door["edge"].asString),
                                side = PohDoorSide.valueOf(door["side"].asString),
                                localX = door["localX"].asInt,
                                localZ = door["localZ"].asInt,
                                rotation = door["rotation"].asInt,
                            )
                        },
                    floor = PohFloor.valueOf(obj["floor"].asString),
                )
            }

        hotspots =
            hotspotsJson.getAsJsonArray("hotspots").map { el ->
                val obj = el.asJsonObject
                PohHotspot(
                    index = obj["index"].asInt,
                    loc = obj["hotspotLoc"].asString,
                    roomKey = obj["roomKey"].asString,
                    name = obj["name"].asString,
                    localX = obj["localX"].asInt,
                    localZ = obj["localZ"].asInt,
                    level = obj["level"].asInt,
                    shape = obj["shape"].asInt,
                    rotation = obj["rotation"].asInt,
                )
            }

        windows =
            windowsJson.getAsJsonArray("windows").map { el ->
                val obj = el.asJsonObject
                PohWindowPlacement(
                    roomKey = obj["roomKey"].asString,
                    localX = obj["localX"].asInt,
                    localZ = obj["localZ"].asInt,
                    shape = obj["shape"].asInt,
                    rotation = obj["rotation"].asInt,
                )
            }

        verifyManifest(manifest)

        roomsByKey = rooms.associateBy { it.key }
        hotspotsByRoom = hotspots.groupBy { it.roomKey }
        windowsByRoom = windows.groupBy { it.roomKey }
    }

    public fun style(index: Int): PohStyle =
        styles.getOrNull(index) ?: error("Invalid poh style index: $index")

    public fun room(key: String): PohRoomType =
        roomsByKey[key] ?: error("Invalid poh room key: $key")

    public fun roomOrNull(key: String): PohRoomType? = roomsByKey[key]

    public fun room(index: Int): PohRoomType =
        rooms.getOrNull(index) ?: error("Invalid poh room index: $index")

    public fun hotspot(index: Int): PohHotspot =
        hotspots.getOrNull(index) ?: error("Invalid poh hotspot index: $index")

    public fun hotspots(roomKey: String): List<PohHotspot> = hotspotsByRoom[roomKey].orEmpty()

    public fun windows(roomKey: String): List<PohWindowPlacement> = windowsByRoom[roomKey].orEmpty()

    private fun verifyManifest(manifest: JsonObject) {
        val manifestRooms = manifest.getAsJsonArray("rooms").map { it.asString }
        val manifestHotspots = manifest.getAsJsonArray("hotspots").map { it.asString }

        check(manifestRooms.size == rooms.size) {
            "poh_index_manifest.json rooms (${manifestRooms.size}) out of sync with " +
                "poh_rooms.json (${rooms.size}); regenerate via :tools:poh-datagen:run."
        }
        for ((i, room) in rooms.withIndex()) {
            check(manifestRooms[i] == room.key) {
                "Room index $i identity mismatch: manifest='${manifestRooms[i]}', data='${room.key}'."
            }
        }

        check(manifestHotspots.size == hotspots.size) {
            "poh_index_manifest.json hotspots (${manifestHotspots.size}) out of sync with " +
                "poh_hotspots.json (${hotspots.size}); regenerate via :tools:poh-datagen:run."
        }
        for ((i, hotspot) in hotspots.withIndex()) {
            val identity =
                "${hotspot.roomKey}|${hotspot.loc}|${hotspot.localX}|${hotspot.localZ}|${hotspot.level}"
            check(manifestHotspots[i] == identity) {
                "Hotspot index $i identity mismatch: manifest='${manifestHotspots[i]}', data='$identity'."
            }
        }
    }

    private fun parseResource(path: String): JsonObject {
        val stream =
            checkNotNull(PohDataStore::class.java.getResourceAsStream(path)) {
                "Missing poh resource: $path (run `./gradlew :tools:poh-datagen:run`)."
            }
        return stream.reader().use { JsonParser.parseReader(it).asJsonObject }
    }
}
