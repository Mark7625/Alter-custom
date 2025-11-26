package dev.openrune

import dev.openrune.OsrsCacheProvider.*
import dev.openrune.cache.CacheManager
import dev.openrune.filesystem.Cache
import java.nio.BufferUnderflowException
import dev.openrune.cache.getOrDefault
import dev.openrune.definition.type.*
import dev.openrune.codec.osrs.ItemDecoder
import dev.openrune.codec.osrs.ObjectDecoder
import dev.openrune.codec.osrs.HealthBarDecoder
import dev.openrune.codec.osrs.NpcDecoder
import dev.openrune.codec.osrs.SequenceDecoder
import dev.openrune.server.impl.item.ItemRenderDataManager
import dev.openrune.types.*
import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.file.Path

object ServerCacheManager {

    private val items: MutableMap<Int, ItemServerType> = mutableMapOf()
    private val npcs: MutableMap<Int, NpcServerType> = mutableMapOf()
    private val objects: MutableMap<Int, ObjectServerType> = mutableMapOf()
    private val healthBars: MutableMap<Int, HealthBarServerType> = mutableMapOf()
    private val structs: MutableMap<Int, StructType> = mutableMapOf()
    private val dbrows: MutableMap<Int, DBRowType> = mutableMapOf()
    private val dbtables: MutableMap<Int, DBTableType> = mutableMapOf()
    private val enums: MutableMap<Int, EnumType> = mutableMapOf()
    private val varbits: MutableMap<Int, VarBitType> = mutableMapOf()
    private val varps: MutableMap<Int, VarpType> = mutableMapOf()
    private val sequences = mutableMapOf<Int, SequenceServerType>()

    val logger = KotlinLogging.logger {}

    fun init(cachePath : Path) {
        val cache = Cache.load(cachePath)
        init(cache)
    }

    fun init(cache : Cache) {
        ItemRenderDataManager.init()
        CacheManager.init(OsrsCacheProvider(cache,234))
        try {
            EnumDecoder().load(cache, enums)
            ObjectDecoder().load(cache, objects)
            HealthBarDecoder().load(cache, healthBars)
            NpcDecoder().load(cache, npcs)
            ItemDecoder().load(cache, items)
            SequenceDecoder().load(cache,sequences)
            VarBitDecoder().load(cache, varbits)
            VarDecoder().load(cache, varps)
            StructDecoder().load(cache, structs)
            DBRowDecoder().load(cache, dbrows)
            DBTableDecoder().load(cache, dbtables)
        } catch (e: BufferUnderflowException) {
            logger.error(e) { "Error reading definitions" }
            throw e
        }
    }

    fun getNpc(id: Int) = npcs[id]
    fun getObject(id: Int) = objects[id]
    fun getItem(id: Int) = items[id]
    fun getVarbit(id: Int) = varbits[id]
    fun getVarp(id: Int) = varps[id]
    fun getAnim(id: Int) = sequences[id]
    fun getEnum(id: Int) = enums[id]
    fun getHealthBar(id: Int) = healthBars[id]
    fun getStruct(id: Int) = structs[id]
    fun getDbrow(id: Int) = dbrows[id]
    fun getDbtable(id: Int) = dbtables[id]

    fun getNpcOrDefault(id: Int) = getOrDefault(npcs, id, NpcServerType(), "Npc")
    fun getObjectOrDefault(id: Int) = getOrDefault(objects, id, ObjectServerType(), "Object")

    fun getItemOrDefault(id: Int) = getOrDefault(items, id, ItemServerType(), "Item")
    fun getVarbitOrDefault(id: Int) = getOrDefault(varbits, id, VarBitType(), "Varbit")
    fun getVarpOrDefault(id: Int) = getOrDefault(varps, id, VarpType(), "Varp")
    fun getEnumOrDefault(id: Int) = getOrDefault(enums, id, EnumType(), "Enum")
    fun getHealthBarOrDefault(id: Int) = getOrDefault(healthBars, id, HealthBarServerType(), "HealthBar")
    fun getStructOrDefault(id: Int) = getOrDefault(structs, id, StructType(), "Struct")
    fun getDbrowOrDefault(id: Int) = getOrDefault(dbrows, id, DBRowType(), "DBRow")
    fun getDbtableOrDefault(id: Int) = getOrDefault(dbtables, id, DBTableType(), "DBTable")

    // Size methods
    fun npcSize() = npcs.size
    fun objectSize() = objects.size
    fun itemSize() = items.size
    fun varbitSize() = varbits.size
    fun varpSize() = varps.size
    fun enumSize() = enums.size
    fun healthBarSize() = healthBars.size
    fun structSize() = structs.size
    fun animSize() = sequences.size

    // Bulk getters
    fun getNpcs() = npcs.toMap()
    fun getObjects() = objects.toMap()
    fun getItems() = items.toMap()
    fun getVarbits() = varbits.toMap()
    fun getVarps() = varps.toMap()
    fun getEnums() = enums.toMap()
    fun getHealthBars() = healthBars.toMap()
    fun getStructs() = structs.toMap()
    fun getAnims() = sequences.toMap()
    fun getRows() = dbrows.toMap()


}
