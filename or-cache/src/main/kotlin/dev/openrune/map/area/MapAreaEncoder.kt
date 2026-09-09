package dev.openrune.map.area

import dev.openrune.cache.MAPS
import dev.openrune.filesystem.Cache
import dev.openrune.map.util.toReadableByteArray
import io.netty.buffer.ByteBuf
import io.netty.buffer.PooledByteBufAllocator
import org.rsmod.game.area.AreaIndex
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareGrid
import org.rsmod.map.square.MapSquareKey
import org.rsmod.map.util.LocalMapSquareZone
import org.rsmod.map.zone.ZoneKey

public object MapAreaEncoder {
    private const val MAX_AREAS_PER_COORD = AreaIndex.MAX_AREAS_PER_KEY

    public fun encodeAll(cache: Cache, areas: Map<MapSquareKey, MapAreaDefinition>) {
        val buffer = PooledByteBufAllocator.DEFAULT.buffer()
        val archive = MAPS
        for ((key, area) in areas) {
            validateAreaLimits(key, area)

            val group = (key.x shl 8) or (key.z and 0xFF)
            val newBuf = buffer.clear().apply { encode(area, this) }
            cache.write(archive, group,7, newBuf.toReadableByteArray())
        }
        buffer.release()
    }

    public fun encode(area: MapAreaDefinition, data: ByteBuf): Unit =
        with(area) {
            data.writeByte(mapSquareAreas.size)
            for (area in mapSquareAreas.iterator()) {
                data.writeShort(area.toInt())
            }

            // A map square split between areas on every level has up to 256 zone entries,
            // which does not fit in a byte.
            data.writeShort(zoneAreas.size)
            for ((packed, areas) in zoneAreas) {
                check(areas.isNotEmpty()) {
                    val localZone = LocalMapSquareZone(packed.toInt())
                    "Area set for zone should not be empty: zone=$localZone, def=$area"
                }
                check(areas.size <= 255) {
                    val localZone = LocalMapSquareZone(packed.toInt())
                    "Area count for zone should not exceed 255: zone=$localZone, def=$area"
                }

                data.writeByte(packed.toInt())
                data.writeByte(areas.size)

                val it = areas.iterator()
                while (it.hasNext()) {
                    data.writeShort(it.nextShort().toInt())
                }
            }

            data.writeShort(coordAreas.size)
            for ((packed, areas) in coordAreas) {
                check(areas.isNotEmpty()) {
                    val grid = MapSquareGrid(packed.toInt())
                    "Area set for grid should not be empty: grid=$grid, def=$area"
                }
                check(areas.size <= 255) {
                    val grid = MapSquareGrid(packed.toInt())
                    "Area count for grid should not exceed 255: grid=$grid, def=$area"
                }

                data.writeShort(packed.toInt())
                data.writeByte(areas.size)

                val it = areas.iterator()
                while (it.hasNext()) {
                    data.writeShort(it.nextShort().toInt())
                }
            }

            data.writeShort(includes.size)

            for ((parent, refs) in includes) {
                check(refs.size <= 255) {
                    "Include count for area $parent exceeds 255: ${refs.size}"
                }

                data.writeShort(parent.toInt())
                data.writeByte(refs.size)

                val it = refs.iterator()
                while (it.hasNext()) {
                    data.writeShort(it.nextShort().toInt())
                }
            }

            data.writeShort(excludes.size)

            for ((parent, refs) in excludes) {
                check(refs.size <= 255) {
                    "Exclude count for area $parent exceeds 255: ${refs.size}"
                }

                data.writeShort(parent.toInt())
                data.writeByte(refs.size)

                val it = refs.iterator()
                while (it.hasNext()) {
                    data.writeShort(it.nextShort().toInt())
                }
            }
        }

    public fun validateAreaLimits(mapSquare: MapSquareKey, area: MapAreaDefinition) {
        val mapSquareCoord = mapSquare.toCoords(level = 0)
        val mapSquareAreaCount = area.mapSquareAreas.size
        if (mapSquareAreaCount > MAX_AREAS_PER_COORD) {
            val message =
                "MapSquare cannot be associated with more than $MAX_AREAS_PER_COORD areas: " +
                    "mapSquare=$mapSquare, areaCount=$mapSquareAreaCount"
            throw IllegalStateException(message)
        }

        val cachedZoneAreaCounts =
            ByteArray(LocalMapSquareZone.LENGTH * LocalMapSquareZone.LENGTH * CoordGrid.LEVEL_COUNT)
        for ((packed, areas) in area.zoneAreas) {
            val localZone = LocalMapSquareZone(packed.toInt())
            cachedZoneAreaCounts[localZone.packed and 0xFF] = areas.size.toByte()

            val totalAreas = mapSquareAreaCount + areas.size
            if (totalAreas > MAX_AREAS_PER_COORD) {
                val zoneCoord = mapSquareCoord.translate(localZone.x, localZone.z, localZone.level)
                val zoneKey = ZoneKey.from(zoneCoord)
                val message =
                    "Zone cannot be associated with more than $MAX_AREAS_PER_COORD areas: " +
                        "zone=$zoneKey, areaCount=$totalAreas, " +
                        "(mapSquareAreas=$mapSquareAreaCount, zoneAreas=${areas.size})"
                throw IllegalStateException(message)
            }
        }

        for ((packed, areas) in area.coordAreas) {
            val grid = MapSquareGrid(packed.toInt())
            val coord = mapSquareCoord.translate(grid.x, grid.z, grid.level)
            val coordZoneKey = ZoneKey.from(coord)
            val localZone = LocalMapSquareZone.from(coordZoneKey)
            val zoneAreaCount = cachedZoneAreaCounts[localZone.packed and 0xFF]

            val totalAreas = mapSquareAreaCount + zoneAreaCount + areas.size
            if (totalAreas > MAX_AREAS_PER_COORD) {
                val message =
                    "Coord cannot be associated with more than $MAX_AREAS_PER_COORD areas: " +
                        "coord=$coord, areaCount=$totalAreas, " +
                        "(mapSquareAreas=$mapSquareAreaCount, " +
                        "zoneAreas=$zoneAreaCount, " +
                        "coordAreas=${areas.size})"
                throw IllegalStateException(message)
            }
        }
    }
}
