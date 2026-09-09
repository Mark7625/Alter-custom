package dev.openrune.map.area

import dev.openrune.map.util.InlineByteBuf
import it.unimi.dsi.fastutil.bytes.Byte2ObjectMap
import it.unimi.dsi.fastutil.bytes.Byte2ObjectMaps
import it.unimi.dsi.fastutil.bytes.Byte2ObjectOpenHashMap
import it.unimi.dsi.fastutil.shorts.Short2ObjectMap
import it.unimi.dsi.fastutil.shorts.Short2ObjectMaps
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap
import it.unimi.dsi.fastutil.shorts.ShortArraySet
import it.unimi.dsi.fastutil.shorts.ShortSet
import it.unimi.dsi.fastutil.shorts.ShortSets

public object MapAreaDecoder {
    public fun decode(buf: InlineByteBuf): MapAreaDefinition {
        var cursor = buf.newCursor()

        // Map-square areas
        cursor = buf.readUnsignedByte(cursor)
        val fullMapSqAreaCount = cursor.value
        val fullMapSqAreas: ShortSet =
            if (fullMapSqAreaCount == 0) {
                ShortSets.emptySet()
            } else {
                val areas = ShortArraySet(fullMapSqAreaCount)
                repeat(fullMapSqAreaCount) {
                    cursor = buf.readShort(cursor)
                    areas.add(cursor.value.toShort())
                }
                areas
            }

        // Zone areas (up to 256 entries, so the count is a short)
        cursor = buf.readShort(cursor)
        val zoneCount = cursor.value
        val zoneAreas: Byte2ObjectMap<ShortSet> =
            if (zoneCount == 0) {
                Byte2ObjectMaps.emptyMap()
            } else {
                val areas = Byte2ObjectOpenHashMap<ShortSet>(zoneCount)
                repeat(zoneCount) {
                    cursor = buf.readByte(cursor)
                    val localZone = cursor.value.toByte()

                    cursor = buf.readUnsignedByte(cursor)
                    val areaCount = cursor.value

                    val areaSet = ShortArraySet(areaCount)
                    repeat(areaCount) {
                        cursor = buf.readShort(cursor)
                        areaSet.add(cursor.value.toShort())
                    }

                    areas[localZone] = areaSet
                }
                areas
            }

        // Coord areas
        cursor = buf.readShort(cursor)
        val coordCount = cursor.value
        val coordAreas: Short2ObjectMap<ShortSet> =
            if (coordCount == 0) {
                Short2ObjectMaps.emptyMap()
            } else {
                val areas = Short2ObjectOpenHashMap<ShortSet>(coordCount)
                repeat(coordCount) {
                    cursor = buf.readShort(cursor)
                    val grid = cursor.value.toShort()

                    cursor = buf.readUnsignedByte(cursor)
                    val areaCount = cursor.value

                    val areaSet = ShortArraySet(areaCount)
                    repeat(areaCount) {
                        cursor = buf.readShort(cursor)
                        areaSet.add(cursor.value.toShort())
                    }

                    areas[grid] = areaSet
                }
                areas
            }

        // Includes
        cursor = buf.readShort(cursor)
        val includeCount = cursor.value
        val includes: Short2ObjectMap<ShortSet> =
            if (includeCount == 0) {
                Short2ObjectMaps.emptyMap()
            } else {
                val map = Short2ObjectOpenHashMap<ShortSet>(includeCount)

                repeat(includeCount) {
                    cursor = buf.readShort(cursor)
                    val area = cursor.value.toShort()

                    cursor = buf.readUnsignedByte(cursor)
                    val refCount = cursor.value

                    val refs = ShortArraySet(refCount)
                    repeat(refCount) {
                        cursor = buf.readShort(cursor)
                        refs.add(cursor.value.toShort())
                    }

                    map[area] = refs
                }

                map
            }

        // Excludes
        cursor = buf.readShort(cursor)
        val excludeCount = cursor.value
        val excludes: Short2ObjectMap<ShortSet> =
            if (excludeCount == 0) {
                Short2ObjectMaps.emptyMap()
            } else {
                val map = Short2ObjectOpenHashMap<ShortSet>(excludeCount)

                repeat(excludeCount) {
                    cursor = buf.readShort(cursor)
                    val area = cursor.value.toShort()

                    cursor = buf.readUnsignedByte(cursor)
                    val refCount = cursor.value

                    val refs = ShortArraySet(refCount)
                    repeat(refCount) {
                        cursor = buf.readShort(cursor)
                        refs.add(cursor.value.toShort())
                    }

                    map[area] = refs
                }

                map
            }

        return MapAreaDefinition(
            mapSquareAreas = fullMapSqAreas,
            zoneAreas = zoneAreas,
            coordAreas = coordAreas,
            includes = includes,
            excludes = excludes,
        )
    }
}
