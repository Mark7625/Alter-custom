package org.alter

import dev.openrune.OsrsCacheProvider
import dev.openrune.cache.gameval.Format
import dev.openrune.cache.gameval.GameValHandler
import dev.openrune.cache.gameval.GameValHandler.elementAs
import dev.openrune.cache.gameval.dump
import dev.openrune.cache.gameval.impl.Table
import dev.openrune.cache.tools.Builder
import dev.openrune.cache.tools.CacheEnvironment
import dev.openrune.cache.tools.dbtables.PackDBTables
import dev.openrune.cache.tools.tasks.CacheTask
import dev.openrune.cache.tools.tasks.TaskType
import dev.openrune.cache.tools.tasks.impl.defs.PackConfig
import dev.openrune.definition.GameValGroupTypes
import dev.openrune.definition.type.DBTableType
import dev.openrune.filesystem.Cache
import dev.openrune.tools.PackServerConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import org.alter.impl.skills.Firemaking
import org.alter.impl.misc.FoodTable
import org.alter.impl.skills.PrayerTable
import org.alter.impl.StatComponents
import org.alter.impl.misc.TeleTabs
import org.alter.impl.skills.Woodcutting
import org.alter.impl.skills.Herblore
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.system.exitProcess

fun getCacheLocation() = File("../data/", "cache").path
fun getRawCacheLocation(dir: String) = File("../data/", "raw-cache/$dir/")

fun tablesToPack() = listOf(
    PrayerTable.skillTable(),
    TeleTabs.teleTabs(),
    StatComponents.statsComponents(),
    FoodTable.consumableFood(),
    Firemaking.logs(),
    Woodcutting.trees(),
    Woodcutting.axes(),
    Herblore.unfinishedPotions(),
    Herblore.finishedPotions(),
    Herblore.cleaningHerbs(),
    Herblore.barbarianMixes(),
    Herblore.swampTar(),
    Herblore.crushing()
)

private val logger = KotlinLogging.logger {}

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Usage: <buildType>")
        exitProcess(1)
    }
    downloadRev(TaskType.valueOf(args.first().uppercase()))
}

fun downloadRev(type: TaskType) {

    val rev = readRevision()

    logger.error { "Using Revision: $rev" }

    val tasks: List<CacheTask> = listOf(
        PackConfig(File("../data/raw-cache/server")),
        PackServerConfig(),
    ).toMutableList()

    when (type) {
        TaskType.FRESH_INSTALL -> {
            val builder = Builder(type = TaskType.FRESH_INSTALL, File(getCacheLocation()))
            builder.registerRSCM(File("../data/cfg/rscm2"))
            builder.revision(rev.first)
            builder.subRevision(rev.second)
            builder.removeXteas(false)
            builder.environment(CacheEnvironment.valueOf(rev.third))

            val tasksNew = tasks.toMutableList()
            tasksNew.add(PackDBTables(tablesToPack()))

            builder.extraTasks(*tasksNew.toTypedArray()).build().initialize()

            Files.move(
                File(getCacheLocation(), "xteas.json").toPath(),
                File("../data/", "xteas.json").toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        }

        TaskType.BUILD -> {
            val builder = Builder(type = TaskType.BUILD, cacheLocation = File(getCacheLocation()))
            builder.registerRSCM(File("../data/cfg/rscm2"))
            builder.revision(rev.first)

            val tasksNew = tasks.toMutableList()
            tasksNew.add(PackDBTables(tablesToPack()))

            builder.extraTasks(*tasksNew.toTypedArray()).build().initialize()

            val cache = Cache.load(File(getCacheLocation()).toPath(),true)

            GameValGroupTypes.entries.forEach {
                val type = GameValHandler.readGameVal(it, cache = cache, rev.first)
                type.dump(Format.RSCM_V2, File("../data/cfg/rscm2"), it).packed(true).write()
            }

            val basePath = File("../data/sym")
            
            symDumper(basePath,cache,"dbrow", GameValGroupTypes.ROWTYPES)
            symDumperDBTables(basePath,cache)

        }
    }



}

fun symDumper(basePath: File,cache: Cache, name: String, group: GameValGroupTypes) {
    if (group == GameValGroupTypes.TABLETYPES) return

    val transformed = GameValHandler.readGameVal(group, cache).map { "${it.id}\t${it.name}" }
    File(basePath, "$name.sym").writeText(transformed.joinToString("\n") + "\n")
}

fun symDumperDBTables(basePath: File, cache: Cache) {

    val dbTableTypes = mutableMapOf<Int, DBTableType>()
    OsrsCacheProvider.DBTableDecoder().load(cache, dbTableTypes)

    val tables = GameValHandler.readGameVal(GameValGroupTypes.TABLETYPES, cache)

    val dbTables = tables.map { "${it.id}\t${it.name}" }
    File(basePath, "dbtable.sym").writeText(dbTables.joinToString("\n") + "\n")

    val dbColumns = tables.mapNotNull { it.elementAs<Table>() }.flatMap { table ->
        val tableType = dbTableTypes[table.id] ?: return@flatMap emptyList()
        val tableName = table.name
        val packedBase = table.id shl 12

        buildList {
            table.columns.forEach { column ->
                val colId = column.id
                val colName = column.name
                val packedColumn = packedBase or (colId shl 4)

                val types = tableType.columns[colId]?.types
                    ?.map { it.name.lowercase().replace("coordgrid", "coord") }
                    ?: return@forEach

                if (types.isEmpty()) return@forEach

                if (types.size > 1) {
                    add("$packedColumn\t$tableName:$colName\t${types.joinToString(",")}")
                }

                types.forEachIndexed { index, typeName ->
                    val indexedName = if (types.size > 1) "$colName:$index" else colName
                    val indexedId = if (types.size > 1) packedColumn + (index + 1) else packedColumn
                    add("$indexedId\t$tableName:$indexedName\t$typeName")
                }
            }
        }
    }

    File(basePath, "dbcolumn.sym").writeText(dbColumns.joinToString("\n") + "\n")
}

fun readRevision(): Triple<Int, Int, String> {
    val file = listOf("../game.yml", "../game.example.yml")
        .map(::File)
        .firstOrNull { it.exists() }
        ?: error("No game.yml or game.example.yml found")

    return file.useLines { lines ->
        val revisionLine = lines.firstOrNull { it.trimStart().startsWith("revision:") }
            ?: error("No revision line found in ${file.name}")

        val revisionStr = revisionLine.substringAfter("revision:").trim()
        val match = Regex("""^(\d+)(?:\.(\d+))?$""").matchEntire(revisionStr)
            ?: error("Invalid revision format: '$revisionStr'")

        val major = match.groupValues[1].toInt()
        val minor = match.groupValues.getOrNull(2)?.toIntOrNull() ?: -1

        val envLine = file.readLines()
            .firstOrNull { it.trimStart().startsWith("environment:") }

        val environment = envLine
            ?.substringAfter("environment:")
            ?.trim()
            ?.removeSurrounding("\"")
            ?.ifBlank { "live" }
            ?: "live"

        Triple(major, minor, environment.uppercase())
    }
}