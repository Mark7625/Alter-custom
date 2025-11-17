package org.alter.plugin.rscm

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.writeTo
import dev.openrune.definition.util.BaseVarType
import dev.openrune.definition.util.VarType
import java.io.File

class TableDataClassGeneratorSymbolProcessor(
    private val environment: SymbolProcessorEnvironment
) : SymbolProcessor {

    private val logger = environment.logger
    private val generatedTables = mutableSetOf<String>()
    private val writtenFiles = mutableSetOf<String>()
    private val tableToClassInfo = mutableMapOf<String, Pair<String, String?>>()

    companion object {
        private val PACKAGE_PREFIXES = listOf(
            "fletching", "cluehelper", "fsw", "herblore", "woodcutting", "mining",
            "fishing", "cooking", "smithing", "crafting", "runecrafting",
            "agility", "thieving", "slayer", "construction", "hunter",
            "farming", "prayer", "magic", "ranged", "melee", "combat", "sailing"
        )

        private val BASE_PACKAGE = "org.alter.tables"
        private val DB_HELPER_PACKAGE = "org.alter.game.util"
        private val VAR_TYPES_PACKAGE = "org.alter.game.util.vars"
        private val RSCM_PACKAGE = "org.alter.rscm"
    }

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val moduleDir = environment.options["moduleDir"]
            ?: throw IllegalArgumentException("moduleDir KSP option not provided!")

        val symFile = File(moduleDir, "../data/sym/dbcolumn.sym")
            .takeIf { it.exists() }
            ?: File("../data/sym/dbcolumn.sym")
                .takeIf { it.exists() }
            ?: run {
                logger.warn("DB column sym file not found: ${File(moduleDir, "../data/sym/dbcolumn.sym")} or ../data/sym/dbcolumn.sym")
                return emptyList()
            }

        generateDataClasses(parseSymFile(symFile))
        return emptyList()
    }

    private fun parseSymFile(file: File): Map<String, List<ColumnInfo>> {
        val tableColumns = mutableMapOf<String, MutableList<ColumnInfo>>()
        val processedColumns = mutableSetOf<String>()

        file.forEachLine { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEachLine

            val parts = trimmed.split("\t")
            if (parts.size < 3) return@forEachLine

            val offset = parts[0].trim().toIntOrNull() ?: return@forEachLine
            val columnKey = parts[1].trim()
            val typeStr = parts[2].trim()

            if (columnKey.count { it == ':' } > 1) return@forEachLine

            val colonIndex = columnKey.indexOf(':')
            if (colonIndex == -1) return@forEachLine

            val tableName = columnKey.substring(0, colonIndex)
            val columnName = columnKey.substring(colonIndex + 1)
            val fullKey = "$tableName:$columnName"

            if (!processedColumns.add(fullKey)) return@forEachLine

            tableColumns.getOrPut(tableName) { mutableListOf() }
                .add(ColumnInfo(columnName, offset, parseType(typeStr)))
        }

        return tableColumns
    }

    private fun parseType(typeStr: String): ColumnType {
        val types = typeStr.split(",").map { it.trim() }
        return if (types.size > 1) {
            ColumnType.Array(types.map(::parseVarType))
        } else {
            ColumnType.Single(parseVarType(types[0]))
        }
    }

    private fun parseVarType(typeStr: String) = VarType.valueOf(typeStr.replace("coord","coordgrid").uppercase())

    private fun generateDataClasses(tableColumns: Map<String, List<ColumnInfo>>) {
        collectTableInfo(tableColumns)
        generateClasses(tableColumns)
    }

    private fun collectTableInfo(tableColumns: Map<String, List<ColumnInfo>>) {
        tableColumns.keys.forEach { tableName ->
            if (generatedTables.add(tableName)) {
                tableToClassInfo[tableName] = tableName.toClassName() to findMatchingPrefix(tableName.toClassName())
            }
        }

        tableColumns.forEach { (tableName, columns) ->
            columns.forEach { column ->
                if (column.isRowType) {
                    val referencedTableName = column.name
                    if (!tableToClassInfo.containsKey(referencedTableName)) {
                        tableToClassInfo[referencedTableName] = referencedTableName.toClassName() to findMatchingPrefix(referencedTableName.toClassName())
                    }
                }
            }
        }
    }

    private fun generateClasses(tableColumns: Map<String, List<ColumnInfo>>) {
        tableColumns.forEach { (tableName, columns) ->
            if (generatedTables.contains(tableName) && writtenFiles.add(tableName)) {
                generateClassSafely(tableName, columns, tableToClassInfo[tableName]!!)
            }
        }

        tableToClassInfo.forEach { (tableName, pair) ->
            if (!tableColumns.containsKey(tableName) && writtenFiles.add(tableName)) {
                generateClassSafely(tableName, emptyList(), pair)
            }
        }
    }

    private fun generateClassSafely(tableName: String, columns: List<ColumnInfo>, classInfo: Pair<String, String?>) {
        try {
            generateRowClass(tableName, columns, classInfo.second)
        } catch (e: Exception) {
            logger.error("Error generating row class for table $tableName: ${e.message}", null)
            logger.error("Stack trace: ${e.stackTraceToString()}", null)
            generatedTables.remove(tableName)
            tableToClassInfo.remove(tableName)
        }
    }

    private fun String.toClassName() = split("_")
        .joinToString("") { it.replaceFirstChar { char -> char.uppercase() } } + "Row"

    private fun findMatchingPrefix(className: String): String? =
        PACKAGE_PREFIXES.sortedByDescending { it.length }
            .firstOrNull { className.lowercase().startsWith(it) }

    private fun generateRowClass(tableName: String, columns: List<ColumnInfo>, packagePrefix: String?) {
        val className = tableName.toClassName()
        val packageName = if (packagePrefix != null) "$BASE_PACKAGE.$packagePrefix" else BASE_PACKAGE
        val rowClassType = ClassName(packageName, className)
        val dbHelperType = ClassName(DB_HELPER_PACKAGE, "DbHelper")

        val properties = columns.map { column ->
            val propertyName = column.name.toCamelCase()
            val columnPath = "columns.$tableName:${column.name}"
            val (kotlinType, initializer) = buildPropertyTypeAndInitializer(column, columnPath)
            PropertySpec.builder(propertyName, kotlinType).initializer(initializer).build()
        }

        val companionObject = TypeSpec.companionObjectBuilder()
            .addFunction(createAllFunction(rowClassType, dbHelperType, tableName))
            .addFunction(createGetRowByIdFunction(rowClassType, dbHelperType))
            .addFunction(createGetRowByColumnFunction(rowClassType))
            .build()

        val rowClass = TypeSpec.classBuilder(className)
            .primaryConstructor(FunSpec.constructorBuilder().addParameter("row", dbHelperType).build())
            .addProperties(properties)
            .addType(companionObject)
            .build()

        val fileSpecBuilder = FileSpec.builder(packageName, className)
            .indent("    ")
            .addImport(DB_HELPER_PACKAGE, "DbHelper", "column", "multiColumnOptional")
            .addImport(VAR_TYPES_PACKAGE, "IntType", "ObjType", "BooleanType", "StringType",
                "LongType", "NpcType", "LocType", "CoordType", "MapElementType", "RowType")
            .addImport("$RSCM_PACKAGE.RSCM", "asRSCM", "requireRSCM")
            .addImport(RSCM_PACKAGE, "RSCMType")

        columns.filter { it.isRowType }.forEach { column ->
            tableToClassInfo[column.name]?.let { (referencedClassName, referencedPackagePrefix) ->
                val referencedPackageName = if (referencedPackagePrefix != null) "$BASE_PACKAGE.$referencedPackagePrefix" else BASE_PACKAGE
                fileSpecBuilder.addImport(referencedPackageName, referencedClassName)
            }
        }

        if (columns.any { it.isCoordType }) {
            fileSpecBuilder.addImport("org.alter.game.model", "Tile")
        }

        try {
            fileSpecBuilder.addType(rowClass)
                .build()
                .writeTo(environment.codeGenerator, aggregating = false)
            logger.info("Generated row class $className for table $tableName")
        } catch (e: Exception) {
            logger.error("Failed to write file for table $tableName: ${e.message}", null)
            throw e
        }
    }

    private fun createAllFunction(rowClassType: ClassName, dbHelperType: ClassName, tableName: String) =
        FunSpec.builder("all")
            .returns(ClassName("kotlin.collections", "List").parameterizedBy(rowClassType))
            .addCode(
                """
                |return %T.table(%S)
                |    .map {
                |        %T(it)
                |    }
                """.trimMargin(),
                dbHelperType, "tables.$tableName", rowClassType
            )
            .build()

    private fun createGetRowByIdFunction(rowClassType: ClassName, dbHelperType: ClassName) =
        FunSpec.builder("getRow")
            .addParameter("row", ClassName("kotlin", "Int"))
            .returns(rowClassType)
            .addCode("return %T(%T.row(row))", rowClassType, dbHelperType)
            .build()

    private fun createGetRowByColumnFunction(rowClassType: ClassName) =
        FunSpec.builder("getRow")
            .addParameter("column", ClassName("kotlin", "String"))
            .returns(rowClassType)
            .addCode(
                CodeBlock.builder()
                    .addStatement("requireRSCM(%T.COLUMNS, column)", ClassName(RSCM_PACKAGE, "RSCMType"))
                    .addStatement("return getRow(column.asRSCM() and 0xFFFF)")
                    .build()
            )
            .build()

    private fun getKotlinType(columnType: ColumnType): TypeName = when (columnType) {
        is ColumnType.Single -> getKotlinTypeFromVarType(columnType.varType)
        is ColumnType.Array -> ClassName("kotlin.collections", "List")
            .parameterizedBy(getKotlinTypeFromVarType(columnType.varTypes.firstOrNull() ?: VarType.INT))
    }

    private fun getKotlinTypeFromVarType(varType: VarType): TypeName = when {
        varType == VarType.BOOLEAN -> ClassName("kotlin", "Boolean")
        else -> when (varType.baseType) {
            BaseVarType.INTEGER -> ClassName("kotlin", "Int")
            BaseVarType.LONG -> ClassName("kotlin", "Long")
            BaseVarType.STRING -> ClassName("kotlin", "String")
            else -> ClassName("kotlin", "Int")
        }
    }

    private fun getVarTypeImplClass(columnType: ColumnType): ClassName {
        val varType = when (columnType) {
            is ColumnType.Single -> columnType.varType
            is ColumnType.Array -> columnType.varTypes.firstOrNull() ?: VarType.INT
        }
        return when (varType) {
            VarType.BOOLEAN -> ClassName(VAR_TYPES_PACKAGE, "BooleanType")
            VarType.INT -> ClassName(VAR_TYPES_PACKAGE, "IntType")
            VarType.STRING -> ClassName(VAR_TYPES_PACKAGE, "StringType")
            VarType.LONG -> ClassName(VAR_TYPES_PACKAGE, "LongType")
            VarType.NPC -> ClassName(VAR_TYPES_PACKAGE, "NpcType")
            VarType.LOC -> ClassName(VAR_TYPES_PACKAGE, "LocType")
            VarType.OBJ -> ClassName(VAR_TYPES_PACKAGE, "ObjType")
            VarType.COORDGRID -> ClassName(VAR_TYPES_PACKAGE, "CoordType")
            VarType.MAPELEMENT -> ClassName(VAR_TYPES_PACKAGE, "MapElementType")
            VarType.DBROW -> ClassName(VAR_TYPES_PACKAGE, "RowType")
            VarType.NAMEDOBJ -> ClassName(VAR_TYPES_PACKAGE, "NamedObjType")
            VarType.GRAPHIC -> ClassName(VAR_TYPES_PACKAGE, "GraphicType")
            VarType.SEQ -> ClassName(VAR_TYPES_PACKAGE, "SeqType")
            VarType.MODEL -> ClassName(VAR_TYPES_PACKAGE, "ModelType")
            VarType.STAT -> ClassName(VAR_TYPES_PACKAGE, "StatType")
            VarType.CATEGORY -> ClassName(VAR_TYPES_PACKAGE, "CategoryType")
            VarType.COMPONENT -> ClassName(VAR_TYPES_PACKAGE, "ComponentType")
            VarType.INV -> ClassName(VAR_TYPES_PACKAGE, "InvType")
            VarType.IDKIT -> ClassName(VAR_TYPES_PACKAGE, "IdkType")
            VarType.ENUM -> ClassName(VAR_TYPES_PACKAGE, "EnumType")
            VarType.MIDI -> ClassName(VAR_TYPES_PACKAGE, "MidiType")
            VarType.VARP -> ClassName(VAR_TYPES_PACKAGE, "VarpType")
            VarType.STRUCT -> ClassName(VAR_TYPES_PACKAGE, "StructType")
            VarType.DBTABLE -> ClassName(VAR_TYPES_PACKAGE, "TableType")

            else -> error("Unmapped Type: $varType")
        }
    }

    private fun buildPropertyTypeAndInitializer(column: ColumnInfo, columnPath: String): Pair<TypeName, CodeBlock> {
        val typeClass = getVarTypeImplClass(column.type)
        return when {
            column.isRowType -> buildRowTypeProperty(column, columnPath, typeClass)
            column.isCoordType -> buildCoordTypeProperty(columnPath, typeClass)
            column.type.isListType -> buildListTypeProperty(column, columnPath, typeClass)
            else -> buildStandardProperty(column, columnPath, typeClass)
        }
    }

    private fun buildRowTypeProperty(column: ColumnInfo, columnPath: String, typeClass: ClassName): Pair<TypeName, CodeBlock> {
        val (referencedClassName, referencedPackagePrefix) = tableToClassInfo[column.name]
            ?: error("RowType column '${column.name}' references unknown table '${column.name}'")
        val referencedPackageName = if (referencedPackagePrefix != null) "$BASE_PACKAGE.$referencedPackagePrefix" else BASE_PACKAGE
        val referencedRowClassType = ClassName(referencedPackageName, referencedClassName)
        return referencedRowClassType to CodeBlock.of("%T.getRow(row.column(%S, %T))", referencedRowClassType, columnPath, typeClass)
    }

    private fun buildCoordTypeProperty(columnPath: String, typeClass: ClassName): Pair<TypeName, CodeBlock> {
        val tileType = ClassName("org.alter.game.model", "Tile")
        return tileType to CodeBlock.of("%T.from30BitHash(row.column(%S, %T))", tileType, columnPath, typeClass)
    }

    private fun buildListTypeProperty(column: ColumnInfo, columnPath: String, typeClass: ClassName): Pair<TypeName, CodeBlock> {
        val kotlinType = getKotlinType(column.type)
        return kotlinType to CodeBlock.of("row.multiColumnOptional(%S, %T).filterNotNull()", columnPath, typeClass)
    }

    private fun buildStandardProperty(column: ColumnInfo, columnPath: String, typeClass: ClassName): Pair<TypeName, CodeBlock> {
        val kotlinType = getKotlinType(column.type)
        return kotlinType to CodeBlock.of("row.column(%S, %T)", columnPath, typeClass)
    }

    private fun String.toCamelCase(): String {
        val result = split("_").joinToString("") { it.lowercase().replaceFirstChar(Char::uppercase) }
            .replaceFirstChar(Char::lowercase)
        return if (result == "object") "objectID" else result
    }

    private val ColumnInfo.isRowType: Boolean
        get() = type is ColumnType.Single && type.varType == VarType.DBROW

    private val ColumnInfo.isCoordType: Boolean
        get() = type is ColumnType.Single && type.varType == VarType.COORDGRID

    private val ColumnType.isListType: Boolean
        get() = this is ColumnType.Array

    private sealed class ColumnType {
        data class Single(val varType: VarType) : ColumnType()
        data class Array(val varTypes: List<VarType>) : ColumnType()
    }

    private data class ColumnInfo(
        val name: String,
        val offset: Int,
        val type: ColumnType
    )
}
