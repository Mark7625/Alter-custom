package org.rsmod.tools.pohdatagen

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.openrune.ServerCacheManager
import dev.openrune.cache.MAPS
import dev.openrune.filesystem.Cache
import dev.openrune.gamevals.GameValProvider
import dev.openrune.map.loc.MapLocDefinition
import dev.openrune.map.loc.MapLocListDecoder
import dev.openrune.map.util.InlineByteBuf
import java.io.File
import java.nio.file.Paths
import java.security.MessageDigest

/**
 * Regenerates the POH room/hotspot resources consumed by `api/poh` from the rev-240 cache.
 *
 * Reads the room-template map squares out of `.data/cache/LIVE` (the squares silo's static map is
 * packed from) and the loc configs out of the packed `SERVER` cache (the types the server resolves
 * at runtime), then writes:
 * - `api/poh/src/main/resources/poh_rooms.json`
 * - `api/poh/src/main/resources/poh_hotspots.json`
 * - `api/poh/src/main/resources/poh_index_manifest.json`
 *
 * Room and hotspot indices are persisted in `character_attrs` blobs, so a regeneration must never
 * reorder existing entries: when a committed manifest exists, this tool fails if any previously
 * assigned index would change identity. Run manually via `./gradlew :tools:poh-datagen:run`.
 */

private data class Style(val index: Int, val key: String, val region: Int, val level: Int)

/** Style slots verified from `poh_hotspot_doorl_<style>` locs in each template square. */
private val STYLES =
    listOf(
        Style(0, "rimmington", 7534, 0),
        Style(1, "lumbridge", 7534, 1),
        Style(2, "pollnivneach", 7534, 2),
        Style(3, "rellekka", 7534, 3),
        Style(4, "brimhaven", 7790, 0),
        Style(5, "yanille", 7790, 1),
        Style(6, "deathly", 7790, 2),
        Style(7, "twisted", 7790, 3),
        Style(8, "hosidius", 8046, 0),
        Style(9, "xmas2020", 8046, 1),
        Style(10, "civitas", 8046, 2),
        Style(11, "canifis", 8046, 3),
        Style(12, "wilderness", 8302, 0),
    )

private data class RoomDef(val key: String, val zx: Int, val zz: Int, val extra: Boolean, val floor: String)

/** The 34 room-template zones. `extra` rooms live on the z-row-111 square (`region + 1`). */
private val ROOMS =
    listOf(
        RoomDef("parlour", 0, 7, extra = false, floor = "GROUND"),
        RoomDef("kitchen", 2, 7, extra = false, floor = "GROUND"),
        RoomDef("dining_room", 4, 7, extra = false, floor = "GROUND"),
        RoomDef("bedroom", 6, 7, extra = false, floor = "GROUND"),
        RoomDef("skill_hall_stairs_up", 1, 6, extra = false, floor = "GROUND"),
        RoomDef("skill_hall_stairs_top", 3, 6, extra = false, floor = "UPPER"),
        RoomDef("quest_hall_stairs_up", 5, 6, extra = false, floor = "GROUND"),
        RoomDef("quest_hall_stairs_top", 7, 6, extra = false, floor = "UPPER"),
        RoomDef("workshop", 0, 5, extra = false, floor = "GROUND"),
        RoomDef("chapel", 2, 5, extra = false, floor = "GROUND"),
        RoomDef("study", 4, 5, extra = false, floor = "GROUND"),
        RoomDef("throne_room", 6, 5, extra = false, floor = "GROUND"),
        RoomDef("portal_chamber", 1, 4, extra = false, floor = "GROUND"),
        RoomDef("combat_room", 3, 4, extra = false, floor = "GROUND"),
        RoomDef("games_room", 5, 4, extra = false, floor = "GROUND"),
        RoomDef("treasure_room", 7, 4, extra = false, floor = "DUNGEON"),
        RoomDef("dungeon_corridor", 0, 3, extra = false, floor = "DUNGEON"),
        RoomDef("dungeon_stairs", 2, 3, extra = false, floor = "DUNGEON"),
        RoomDef("dungeon_junction", 4, 3, extra = false, floor = "DUNGEON"),
        RoomDef("oubliette", 6, 3, extra = false, floor = "DUNGEON"),
        RoomDef("roof_a", 1, 2, extra = false, floor = "GROUND"),
        RoomDef("roof_b", 3, 2, extra = false, floor = "GROUND"),
        RoomDef("roof_c", 5, 2, extra = false, floor = "GROUND"),
        RoomDef("menagerie_indoor", 7, 2, extra = false, floor = "GROUND"),
        RoomDef("garden", 0, 1, extra = false, floor = "GROUND"),
        RoomDef("formal_garden", 2, 1, extra = false, floor = "GROUND"),
        RoomDef("costume_room", 6, 1, extra = false, floor = "GROUND"),
        RoomDef("grass_filler", 1, 0, extra = false, floor = "GROUND"),
        RoomDef("dungeon_filler", 3, 0, extra = false, floor = "DUNGEON"),
        RoomDef("superior_garden", 5, 0, extra = false, floor = "GROUND"),
        RoomDef("menagerie_outdoor", 7, 0, extra = false, floor = "GROUND"),
        RoomDef("achievement_gallery", 1, 0, extra = true, floor = "GROUND"),
        RoomDef("portal_nexus", 3, 0, extra = true, floor = "GROUND"),
        RoomDef("league_hall", 5, 0, extra = true, floor = "GROUND"),
    )

/** The `(4,1)` main-square zone is empty in every style; a Build-op loc there means a new room. */
private const val RESERVED_ZONE_X = 4
private const val RESERVED_ZONE_Z = 1

private const val ZONE_LEN = 8
private const val DOOR_PREFIX = "loc.poh_hotspot_door"
private const val OP_BUILD_SLOT = 4 // 0-based; op5.

/** Template placeholder every style bakes where its real wall window belongs. */
private const val WINDOW_PLACEHOLDER = "loc.poh_dynamic_window"

private data class HotspotPlacement(
    val locName: String,
    val name: String,
    val localX: Int,
    val localZ: Int,
    val shape: Int,
    val rotation: Int,
) : Comparable<HotspotPlacement> {
    override fun compareTo(other: HotspotPlacement): Int =
        compareValuesBy(this, other, { it.localX }, { it.localZ }, { it.shape }, { it.locName })
}

private data class DoorPlacement(
    val locName: String,
    val edge: String,
    val side: String,
    val localX: Int,
    val localZ: Int,
    val rotation: Int,
)

private data class WindowPlacement(
    val localX: Int,
    val localZ: Int,
    val shape: Int,
    val rotation: Int,
) : Comparable<WindowPlacement> {
    override fun compareTo(other: WindowPlacement): Int =
        compareValuesBy(this, other, { it.localX }, { it.localZ }, { it.rotation })
}

private val EDGES = arrayOf("WEST", "NORTH", "EAST", "SOUTH")

fun main() {
    GameValProvider.load()
    val rev = readRevision()
    println("[poh-datagen] revision=$rev")

    ServerCacheManager.init(Paths.get(".data", "cache", "SERVER"), rev)
    val liveCache = Cache.load(Paths.get(".data", "cache", "LIVE"))

    val regionLocs = HashMap<Int, List<MapLocDefinition>>()
    fun locsOf(region: Int): List<MapLocDefinition> =
        regionLocs.getOrPut(region) {
            val data =
                liveCache.data(MAPS, region, 1)
                    ?: error("Template region $region has no loc data in the LIVE cache.")
            MapLocListDecoder.decode(InlineByteBuf(data)).toMapLocDefinitionList()
        }

    // Runtime copies template zones out of the static map, which is packed from these squares; a
    // square missing from the SERVER cache would make every region build silently empty.
    for (region in STYLES.flatMap { listOf(it.region, it.region + 1) }.distinct()) {
        checkNotNull(liveCache.data(MAPS, region, 1)) { "LIVE cache missing region $region" }
    }

    // roomKey -> style index -> placements.
    val roomHotspots = LinkedHashMap<String, MutableMap<Int, MutableList<HotspotPlacement>>>()
    val roomDoors = LinkedHashMap<String, MutableMap<Int, MutableList<DoorPlacement>>>()
    val roomWindows = LinkedHashMap<String, MutableMap<Int, MutableList<WindowPlacement>>>()
    val styleDoorNames = LinkedHashMap<Int, MutableSet<String>>()

    for (style in STYLES) {
        for (room in ROOMS) {
            val region = if (room.extra) style.region + 1 else style.region
            val zoneLocs =
                locsOf(region).filter {
                    it.level == style.level &&
                        it.localX / ZONE_LEN == room.zx &&
                        it.localZ / ZONE_LEN == room.zz
                }
            if (zoneLocs.isEmpty()) {
                // Filler/exterior zones legitimately carry no locs in some styles; the room table
                // is anchored on style 0, so only an empty style-0 zone is fatal.
                check(style.index != 0) {
                    "Room ${room.key} zone (${room.zx},${room.zz}) is empty in style " +
                        "${style.key} (region=$region level=${style.level})."
                }
                println(
                    "[poh-datagen] note: ${room.key} zone empty in style ${style.key} " +
                        "(region=$region level=${style.level})"
                )
                continue
            }
            for (loc in zoneLocs) {
                val type =
                    ServerCacheManager.getObject(loc.id)
                        ?: error("Loc id=${loc.id} in room ${room.key} has no server type.")
                val name = type.internalName
                val localX = loc.localX % ZONE_LEN
                val localZ = loc.localZ % ZONE_LEN
                if (name.startsWith(DOOR_PREFIX)) {
                    val side = if (name.startsWith("${DOOR_PREFIX}l_")) "LEFT" else "RIGHT"
                    roomDoors
                        .getOrPut(room.key) { HashMap() }
                        .getOrPut(style.index) { mutableListOf() }
                        .add(DoorPlacement(name, EDGES[loc.angle], side, localX, localZ, loc.angle))
                    styleDoorNames.getOrPut(style.index) { mutableSetOf() }.add(name)
                } else if (name == WINDOW_PLACEHOLDER) {
                    roomWindows
                        .getOrPut(room.key) { HashMap() }
                        .getOrPut(style.index) { mutableListOf() }
                        .add(WindowPlacement(localX, localZ, loc.shape, loc.angle))
                } else if (type.actions.getOpOrNull(OP_BUILD_SLOT) == "Build") {
                    roomHotspots
                        .getOrPut(room.key) { HashMap() }
                        .getOrPut(style.index) { mutableListOf() }
                        .add(HotspotPlacement(name, type.name, localX, localZ, loc.shape, loc.angle))
                }
            }
        }

        // The reserved main-square zone must stay empty of hotspots in every style.
        val reserved =
            locsOf(style.region).filter {
                it.level == style.level &&
                    it.localX / ZONE_LEN == RESERVED_ZONE_X &&
                    it.localZ / ZONE_LEN == RESERVED_ZONE_Z &&
                    ServerCacheManager.getObject(it.id)?.actions?.getOpOrNull(OP_BUILD_SLOT) == "Build"
            }
        check(reserved.isEmpty()) {
            "Reserved zone ($RESERVED_ZONE_X,$RESERVED_ZONE_Z) has Build locs in ${style.key}: " +
                reserved.map { ServerCacheManager.getObject(it.id)?.internalName }
        }
    }

    // Hotspot placements are anchored on style 0 (rimmington): the runtime zone copy renders each
    // style's own template, but furniture slots and ghost-hiding use style-0 placements. The cache
    // holds small genuine divergences (hall stairwell middle pieces per style; the region-8302
    // Wilderness slot trims a few decoration hotspots), so cross-style differences are reported,
    // not fatal.
    for (room in ROOMS) {
        val byStyle = roomHotspots[room.key] ?: continue
        val reference = byStyle.getValue(0).sorted()
        for (style in STYLES.drop(1)) {
            val other = byStyle[style.index]?.sorted() ?: emptyList()
            if (other == reference) continue
            println(
                "[poh-datagen] note: ${room.key} hotspots diverge in style ${style.key} " +
                    "(${reference.size} vs ${other.size} placements); using style-0 layout."
            )
        }
    }

    // Window placeholders are style-0 anchored like hotspots; divergences reported, not fatal.
    for (room in ROOMS) {
        val byStyle = roomWindows[room.key] ?: continue
        val reference = byStyle[0]?.sorted() ?: emptyList()
        for (style in STYLES.drop(1)) {
            val other = byStyle[style.index]?.sorted() ?: emptyList()
            if (other == reference) continue
            println(
                "[poh-datagen] note: ${room.key} windows diverge in style ${style.key} " +
                    "(${reference.size} vs ${other.size} placements); using style-0 layout."
            )
        }
    }

    // Door edges must agree across styles 0-11 too (names differ; edges must not).
    val roomDoorEdges = LinkedHashMap<String, List<String>>()
    for (room in ROOMS) {
        val byStyle = roomDoors[room.key]
        if (byStyle == null) {
            roomDoorEdges[room.key] = emptyList()
            continue
        }
        val referenceEdges = byStyle.getValue(0).map { it.edge }.distinct().sorted()
        for (style in STYLES.drop(1)) {
            val other = byStyle[style.index]?.map { it.edge }?.distinct()?.sorted() ?: emptyList()
            if (other == referenceEdges) continue
            check(style.region == 8302) {
                "Room ${room.key} door edges differ between styles: $referenceEdges vs $other (${style.key})"
            }
            println(
                "[poh-datagen] note: ${room.key} door edges diverge in style ${style.key}: " +
                    "$referenceEdges vs $other; using style-0 edges."
            )
        }
        roomDoorEdges[room.key] = EDGES.filter { it in referenceEdges }
    }
    // Per-room door placements (positions/rotations), anchored on style 0. Positions must agree
    // across styles 0-11 or the runtime open-passage deletes would miss the styled door locs.
    val roomDoorPlacements = LinkedHashMap<String, List<DoorPlacement>>()
    for (room in ROOMS) {
        val byStyle = roomDoors[room.key]
        if (byStyle == null) {
            roomDoorPlacements[room.key] = emptyList()
            continue
        }
        // `side` (which of the doorl/doorr pair sits on which tile) genuinely varies per style;
        // runtime deletes try both style door locs at each position, so only positions must agree.
        fun positions(placements: List<DoorPlacement>?) =
            placements
                .orEmpty()
                .map { listOf(it.edge, it.localX, it.localZ, it.rotation) }
                .sortedBy { it.toString() }
        val reference = positions(byStyle[0])
        for (style in STYLES.drop(1)) {
            val other = positions(byStyle[style.index])
            if (other == reference) continue
            check(style.region == 8302) {
                "Room ${room.key} door placements differ between styles: $reference vs $other (${style.key})"
            }
            println(
                "[poh-datagen] note: ${room.key} door placements diverge in style ${style.key}; " +
                    "using style-0 placements."
            )
        }
        roomDoorPlacements[room.key] =
            byStyle.getValue(0).sortedWith(compareBy({ it.localX }, { it.localZ }, { it.side }))
    }

    // Per-style door hotspot loc names (style 12 reuses the lumbridge doors).
    val styleDoors =
        STYLES.associate { style ->
            val names = styleDoorNames[style.index].orEmpty()
            val left = names.filter { it.startsWith("${DOOR_PREFIX}l_") }
            val right = names.filter { it.startsWith("${DOOR_PREFIX}r_") }
            check(left.size == 1 && right.size == 1) {
                "Style ${style.key} should have exactly one doorl/doorr loc, found: $names"
            }
            style.index to Pair(left.single(), right.single())
        }

    val hotspotEntries =
        ROOMS.flatMap { room ->
            val placements = roomHotspots[room.key]?.getValue(0)?.sorted() ?: emptyList()
            placements.map { room.key to it }
        }

    val outDir = File("api/poh/src/main/resources")
    outDir.mkdirs()

    val roomIdentity = ROOMS.map { it.key }
    val hotspotIdentity =
        hotspotEntries.map { (roomKey, p) -> "$roomKey|${p.locName}|${p.localX}|${p.localZ}|0" }
    enforceManifestStability(File(outDir, "poh_index_manifest.json"), roomIdentity, hotspotIdentity)

    val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    val roomsJson = JsonObject()
    val stylesArr = JsonArray()
    for (style in STYLES) {
        val obj = JsonObject()
        obj.addProperty("index", style.index)
        obj.addProperty("key", style.key)
        obj.addProperty("region", style.region)
        obj.addProperty("extraRegion", style.region + 1)
        obj.addProperty("level", style.level)
        obj.addProperty("doorLeftLoc", styleDoors.getValue(style.index).first)
        obj.addProperty("doorRightLoc", styleDoors.getValue(style.index).second)
        stylesArr.add(obj)
    }
    roomsJson.add("styles", stylesArr)
    val roomsArr = JsonArray()
    for ((index, room) in ROOMS.withIndex()) {
        val obj = JsonObject()
        obj.addProperty("index", index)
        obj.addProperty("roomKey", room.key)
        obj.addProperty("templateRegion", if (room.extra) 7535 else 7534)
        obj.addProperty("templateZoneX", room.zx)
        obj.addProperty("templateZoneZ", room.zz)
        val edges = JsonArray()
        roomDoorEdges.getValue(room.key).forEach(edges::add)
        obj.add("doorEdges", edges)
        val doors = JsonArray()
        for (door in roomDoorPlacements.getValue(room.key)) {
            val doorObj = JsonObject()
            doorObj.addProperty("edge", door.edge)
            doorObj.addProperty("side", door.side)
            doorObj.addProperty("localX", door.localX)
            doorObj.addProperty("localZ", door.localZ)
            doorObj.addProperty("rotation", door.rotation)
            doors.add(doorObj)
        }
        obj.add("doorPlacements", doors)
        obj.addProperty("floor", room.floor)
        roomsArr.add(obj)
    }
    roomsJson.add("rooms", roomsArr)
    File(outDir, "poh_rooms.json").writeText(gson.toJson(roomsJson) + "\n")

    val hotspotsJson = JsonObject()
    val hotspotsArr = JsonArray()
    for ((index, entry) in hotspotEntries.withIndex()) {
        val (roomKey, p) = entry
        val obj = JsonObject()
        obj.addProperty("index", index)
        obj.addProperty("hotspotLoc", p.locName)
        obj.addProperty("roomKey", roomKey)
        obj.addProperty("name", p.name)
        obj.addProperty("localX", p.localX)
        obj.addProperty("localZ", p.localZ)
        obj.addProperty("level", 0)
        obj.addProperty("shape", p.shape)
        obj.addProperty("rotation", p.rotation)
        hotspotsArr.add(obj)
    }
    hotspotsJson.add("hotspots", hotspotsArr)
    File(outDir, "poh_hotspots.json").writeText(gson.toJson(hotspotsJson) + "\n")

    val windowsJson = JsonObject()
    val windowsArr = JsonArray()
    for (room in ROOMS) {
        val placements = roomWindows[room.key]?.get(0)?.sorted() ?: continue
        for (p in placements) {
            val obj = JsonObject()
            obj.addProperty("roomKey", room.key)
            obj.addProperty("localX", p.localX)
            obj.addProperty("localZ", p.localZ)
            obj.addProperty("shape", p.shape)
            obj.addProperty("rotation", p.rotation)
            windowsArr.add(obj)
        }
    }
    windowsJson.add("windows", windowsArr)
    File(outDir, "poh_windows.json").writeText(gson.toJson(windowsJson) + "\n")
    println("[poh-datagen] Wrote ${windowsArr.size()} window placements.")

    writeManifest(File(outDir, "poh_index_manifest.json"), gson, roomIdentity, hotspotIdentity)

    println("[poh-datagen] Wrote ${ROOMS.size} rooms, ${hotspotEntries.size} hotspot placements.")
    val distinctLocs = hotspotEntries.map { it.second.locName }.distinct().size
    println("[poh-datagen] Distinct hotspot locs: $distinctLocs")
}

private fun dev.openrune.map.loc.MapLocListDefinition.toMapLocDefinitionList(): List<MapLocDefinition> {
    val list = ArrayList<MapLocDefinition>(spawns.size)
    for (i in spawns.indices) {
        list += MapLocDefinition(spawns.getLong(i))
    }
    return list
}

private fun readRevision(): Int {
    val file = File("game.yml").takeIf { it.exists() } ?: File("game.example.yml")
    val line =
        file.useLines { lines -> lines.firstOrNull { it.trimStart().startsWith("revision:") } }
            ?: error("No revision line in ${file.name}")
    return line.substringAfter("revision:").trim().substringBefore('.').toInt()
}

private fun enforceManifestStability(
    manifestFile: File,
    roomIdentity: List<String>,
    hotspotIdentity: List<String>,
) {
    if (!manifestFile.exists()) return
    val manifest = JsonParser.parseString(manifestFile.readText()).asJsonObject
    val oldRooms = manifest.getAsJsonArray("rooms").map { it.asString }
    val oldHotspots = manifest.getAsJsonArray("hotspots").map { it.asString }
    for ((i, old) in oldRooms.withIndex()) {
        check(i < roomIdentity.size && roomIdentity[i] == old) {
            "Room index $i would change identity ('$old' -> '${roomIdentity.getOrNull(i)}'). " +
                "Saved houses key rooms by index; write a migration instead of regenerating."
        }
    }
    for ((i, old) in oldHotspots.withIndex()) {
        check(i < hotspotIdentity.size && hotspotIdentity[i] == old) {
            "Hotspot index $i would change identity ('$old' -> '${hotspotIdentity.getOrNull(i)}'). " +
                "Saved houses key furniture by index; write a migration instead of regenerating."
        }
    }
}

private fun writeManifest(
    manifestFile: File,
    gson: com.google.gson.Gson,
    roomIdentity: List<String>,
    hotspotIdentity: List<String>,
) {
    val manifest = JsonObject()
    val roomsArr = JsonArray()
    roomIdentity.forEach(roomsArr::add)
    val hotspotsArr = JsonArray()
    hotspotIdentity.forEach(hotspotsArr::add)
    manifest.add("rooms", roomsArr)
    manifest.add("hotspots", hotspotsArr)
    manifest.addProperty("roomsSha256", sha256(roomIdentity.joinToString("\n")))
    manifest.addProperty("hotspotsSha256", sha256(hotspotIdentity.joinToString("\n")))
    manifestFile.writeText(gson.toJson(manifest) + "\n")
}

private fun sha256(text: String): String =
    MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") {
        "%02x".format(it)
    }
