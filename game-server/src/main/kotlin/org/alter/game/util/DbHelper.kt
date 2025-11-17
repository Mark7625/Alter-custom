package org.alter.game.util

import dev.openrune.ServerCacheManager
import dev.openrune.definition.constants.ConstantProvider
import dev.openrune.definition.type.DBRowType
import dev.openrune.definition.type.DBColumnType
import dev.openrune.definition.util.VarType
import dev.openrune.filesystem.Cache
import org.alter.game.util.DbHelper.Companion.table
import org.alter.game.util.vars.BooleanVarType
import org.alter.game.util.vars.ComponentType
import org.alter.game.util.vars.IntType
import org.alter.game.util.vars.NpcType
import org.alter.game.util.vars.ObjType
import org.alter.game.util.vars.VarTypeImpl
import org.alter.rscm.RSCM
import org.alter.rscm.RSCM.asRSCM
import org.alter.rscm.RSCM.requireRSCM
import org.alter.rscm.RSCMType
import org.alter.tables.TableData
import java.io.File
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.Path

fun <K, V> DbHelper.column(name: String, type: VarTypeImpl<K, V>): V =
    getNValue(name, type, 0)


fun <K, V> DbHelper.columnOptional(name: String, type: VarTypeImpl<K, V>): V? =
    getNValueOrNull(name, type, 0)


fun row(row: String) = DbHelper.row(row)

/**
 * Splits a single column into consecutive pairs.
 *
 * Example:
 * column.values = [8, 35, 4, 30]
 * returns [[8, 35], [4, 30]]
 */

@Suppress("UNCHECKED_CAST")
fun <K, V> DbHelper.multiColumn(
    columnName: String,
    vararg types: VarTypeImpl<K, V>
): List<V> {
    val column = getColumn(columnName)
    val values = column.column.values ?: return emptyList()

    require(types.isNotEmpty()) { "At least one VarTypeImpl must be provided" }

    return values.mapIndexed { i, raw ->
        val type = types[i % types.size]
        val value = column.get(i, type)
        type.convertTo(value as K)
    }
}

@Suppress("UNCHECKED_CAST")
fun <K, V> DbHelper.multiColumnOptional(
    columnName: String,
    vararg types: VarTypeImpl<K, V>
): List<V?> {
    val column = try {
        getColumn(columnName)
    } catch (e: DbException.MissingColumn) {
        return emptyList()
    } catch (e: DbException) {
        throw e
    } catch (_: Exception) {
        return emptyList()
    }

    val values = column.column.values ?: return emptyList()
    require(types.isNotEmpty()) { "At least one VarTypeImpl must be provided" }

    return values.mapIndexed { i, raw ->
        val type = types[i % types.size]
        try {
            run {
                val value = column.get(i, type)
                type.convertTo(value as K)
            }
        } catch (e: DbException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }
}

@Suppress("UNCHECKED_CAST")
class DbHelper private constructor(private val row: DBRowType) {

    val id: Int get() = row.id
    val tableId: Int get() = row.tableId

    override fun toString(): String =
        "DbHelper(id=$id, table=$tableId, columns=${row.columns.keys.joinToString()})"

    fun <K, V> DbHelper.getNValueOrNull(name: String, type: VarTypeImpl<K, V>, index: Int = 0): V? =
        try {
            val value = getColumn(name).get(index, type)
            @Suppress("UNCHECKED_CAST")
            if (type is BooleanVarType) type.convertToAny(value) as V else type.convertTo(value as K)
        } catch (_: Exception) {
            null
        }

    fun <K, V> getNValue(name: String, type: VarTypeImpl<K, V>, index: Int): V {
        val value = getColumn(name).get(index, type)
        @Suppress("UNCHECKED_CAST")
        return if (type is BooleanVarType) type.convertToAny(value) as V else type.convertTo(value as K)
    }

    fun getColumn(name: String): Column {
        requireRSCM(RSCMType.COLUMNS,name)
        return getColumn(name.asRSCM() and 0xFFFF)
    }

    fun getColumn(id: Int): Column {
        val col = row.columns[id] ?: throw DbException.MissingColumn(tableId, id, id)
        return Column(col, rowId = id, columnId = id, tableId = tableId)
    }


    class Column(
        val column: DBColumnType,
        private val rowId: Int,
        val columnId: Int,
        val tableId: Int
    ) {
        val types: Array<VarType> get() = column.types

        val size: Int get() = column.values?.size ?: 0

        fun <K, V> get(index: Int = 0, type: VarTypeImpl<K, V>): Any {
            val values = column.values
                ?: throw DbException.EmptyColumnValues(tableId, rowId, columnId)

            if (index !in values.indices) {
                throw DbException.IndexOutOfRange(tableId, rowId, columnId, index, values.size)
            }

            val actualType = types.getOrNull(index % types.size)
                ?: throw DbException.MissingVarType(tableId, rowId, columnId, index)

            if (actualType != type.type) {
                throw DbException.TypeMismatch(
                    tableId, rowId, columnId, expected = actualType, actual = type.type
                )
            }

            return values[index]
        }

        override fun toString(): String {
            val vals = column.values?.joinToString(", ") ?: "empty"
            return "Column(id=$columnId, row=$rowId, size=$size, values=[$vals])"
        }
    }

    companion object {

        fun table(table: String): List<DbHelper> {
            requireRSCM(RSCMType.TABLETYPES,table)

            return DbQueryCache.getTable(table) {
                val tableId = table.asRSCM()
                ServerCacheManager.getRows()
                    .asSequence()
                    .filter { it.value.tableId == tableId }
                    .map { DbHelper(it.value) }
                    .distinctBy { it.id }
                    .toList()
            }
        }

        inline fun <reified T : TableData<T>> tableAs(tableName: String): List<T> {
            val rows = table(tableName)
            val companion = T::class.java.getDeclaredField("Companion").get(null)
            val convertMethod = T::class.java.getDeclaredMethod("convert", DbHelper::class.java)

            return rows.map { row ->
                convertMethod.invoke(companion, row) as T
            }
        }

        fun <K, V> dbFind(column: String, value: V, type: VarTypeImpl<K, V>): List<DbHelper> {
            requireRSCM(RSCMType.COLUMNS,column)

            return DbQueryCache.getColumn(column, value, type) {
                val tableName = "tables." + column.removePrefix("columns.").substringBefore(':')
                val tableId = tableName.asRSCM()
                val columnId = column.asRSCM() and 0xFFFF

                ServerCacheManager.getRows()
                    .asSequence()
                    .filter { it.value.tableId == tableId }
                    .filter { (_, row) ->
                        val col = row.columns[columnId] ?: return@filter false
                        val values = col.values ?: return@filter false
                        values.any { raw -> type.convertTo(raw as K) == value }
                    }
                    .map { (_, row) -> DbHelper(row) }
                    .distinctBy { it.id }
                    .toList()
            }
        }

        private fun load(rowId: Int): DbHelper =
            ServerCacheManager.getDbrow(rowId)?.let(::DbHelper)
                ?: throw DbException.MissingRow(rowId)

        fun row(ref: String): DbHelper = load(ref.asRSCM())
        fun row(rowId: Int): DbHelper = load(rowId)
    }
}

object DbQueryCache {
    private val tableCache = ConcurrentHashMap<String, List<DbHelper>>()
    private val columnCache = ConcurrentHashMap<Triple<String, Any, VarTypeImpl<*, *>>, List<DbHelper>>()

    fun getTable(table: String, supplier: () -> List<DbHelper>): List<DbHelper> {
        return tableCache.computeIfAbsent(table) { supplier() }
    }

    fun <K, V> getColumn(column: String, value: V, type: VarTypeImpl<K, V>, supplier: () -> List<DbHelper>): List<DbHelper> {
        val key = Triple(column, value as Any, type as VarTypeImpl<*, *>)
        return columnCache.computeIfAbsent(key) { supplier() }
    }

    fun clear() {
        tableCache.clear()
        columnCache.clear()
    }
}

sealed class DbException(message: String) : RuntimeException(message) {

    class MissingColumn(tableId: Int, rowId: Int, columnId: Int) :
        DbException("Column $columnId not found in row $rowId (table $tableId)")

    class EmptyColumnValues(
        tableId: Int,
        rowId: Int,
        columnId: Int
    ) : DbException("No values found in column $columnId (row $rowId, table $tableId)")

    class IndexOutOfRange(tableId: Int, rowId: Int, columnId: Int, index: Int, max: Int) :
        DbException("Index $index out of bounds (size=$max) in column $columnId (row $rowId, table $tableId)")

    class MissingVarType(tableId: Int, rowId: Int, columnId: Int, index: Int) :
        DbException("No VarType available at index $index in column $columnId (row $rowId, table $tableId)")

    class TypeMismatch(tableId: Int, rowId: Int, columnId: Int, expected: VarType, actual: VarType) :
        DbException("Type mismatch in table $tableId, row $rowId, column $columnId: expected $expected but found $actual")

    class MissingRow(rowId: Int) :
        DbException("DBRow $rowId not found")


}