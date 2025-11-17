package org.alter.plugin.rscm

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.writeTo
import com.squareup.kotlinpoet.KModifier
import dev.openrune.definition.util.VarType
import java.io.File
import com.google.devtools.ksp.processing.CodeGenerator

class TableDataClassGeneratorSymbolProcessor(
    private val environment: SymbolProcessorEnvironment
) : SymbolProcessor {

    private val logger = environment.logger
    private val generatedTables = mutableSetOf<String>()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val moduleDir = environment.options["moduleDir"]
            ?: throw IllegalArgumentException("moduleDir KSP option not provided!")

        // Try relative to module dir first, then absolute path
        val symFile = File(moduleDir, "../data/sym/dbcolumn.sym")
            .takeIf { it.exists() }
            ?: File("../data/sym/dbcolumn.sym")
            .takeIf { it.exists() }
            ?: run {
                logger.warn("DB column sym file does not exist. Tried: ${File(moduleDir, "../data/sym/dbcolumn.sym")} and ../data/sym/dbcolumn.sym")
                return emptyList()
            }

        val tableColumns = parseSymFile(symFile)
        generateDataClasses(tableColumns)

        return emptyList()
    }
    

    private fun parseSymFile(file: File): Map<String, List<ColumnInfo>> {
        val tableColumns = mutableMapOf<String, MutableList<ColumnInfo>>()
        val processedColumns = mutableSetOf<String>() // Track columns we've already processed

        file.forEachLine { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEachLine

            // Format: offset	table:column	type
            val parts = trimmed.split("\t")
            if (parts.size < 3) return@forEachLine

            val offset = parts[0].trim().toIntOrNull() ?: return@forEachLine
            val columnKey = parts[1].trim()
            val typeStr = parts[2].trim()

            // Skip sub-columns (like quest:releasedate:0) - we'll handle the parent column
            if (columnKey.count { it == ':' } > 1) return@forEachLine

            // Parse table_name:column_name format
            val colonIndex = columnKey.indexOf(':')
            if (colonIndex == -1) return@forEachLine

            val tableName = columnKey.substring(0, colonIndex)
            val columnName = columnKey.substring(colonIndex + 1)

            // Skip if we've already processed this column
            val fullKey = "$tableName:$columnName"
            if (processedColumns.contains(fullKey)) return@forEachLine
            processedColumns.add(fullKey)

            // Parse type
            val type = parseType(typeStr)

            tableColumns.getOrPut(tableName) { mutableListOf() }
                .add(ColumnInfo(columnName, offset, type))
        }

        return tableColumns
    }

    private fun parseType(typeStr: String): ColumnType {
        // Handle array types like "int,int" or "boolean,string"
        val types = typeStr.split(",").map { it.trim() }
        
        return if (types.size > 1) {
            // Array type - store VarType enums
            ColumnType.Array(
                varTypes = types.map { parseVarType(it) }
            )
        } else {
            ColumnType.Single(
                varType = parseVarType(types[0])
            )
        }
    }

    private fun parseVarType(typeStr: String): VarType {
        // Map sym file type strings to VarType enum names
        val varTypeName = when (typeStr.lowercase()) {
            "int" -> "INT"
            "string" -> "STRING"
            "boolean" -> "BOOLEAN"
            "npc" -> "NPC"
            "loc" -> "LOC"
            "coord" -> "COORDGRID"
            "mapelement" -> "MAPELEMENT"
            "dbrow" -> "DBROW"
            else -> "INT" // Default
        }
        
        return try {
            VarType.valueOf(varTypeName)
        } catch (e: IllegalArgumentException) {
            VarType.INT // Fallback if VarType not found
        }
    }

    private fun generateDataClasses(tableColumns: Map<String, List<ColumnInfo>>) {
        // Predefined list of prefixes to extract into sub-packages
        val prefixes = listOf(
            "fletching", "cluehelper", "fsw", "herblore", "woodcutting", "mining",
            "fishing", "cooking", "smithing", "crafting", "runecrafting",
            "agility", "thieving", "slayer", "construction", "hunter",
            "farming", "prayer", "magic", "ranged", "melee", "combat", "sailing"
        )
        
        tableColumns.forEach { (tableName, columns) ->
            if (generatedTables.add(tableName)) {
                try {
                    val className = tableName.split("_")
                        .joinToString("") { it.replaceFirstChar { char -> char.uppercase() } } + "Data"
                    val packagePrefix = findMatchingPrefix(className, prefixes)
                    generateDataClass(tableName, columns, packagePrefix)
                } catch (e: Exception) {
                    logger.error("Error generating data class for table $tableName: ${e.message}", null)
                    generatedTables.remove(tableName)
                }
            }
        }
    }
    
    private fun findMatchingPrefix(className: String, prefixes: List<String>): String? {
        val classNameLower = className.lowercase()
        // Find the longest matching prefix (sorted by length descending)
        return prefixes.sortedByDescending { it.length }
            .firstOrNull { prefix ->
                classNameLower.startsWith(prefix)
            }
    }

    private fun generateDataClass(tableName: String, columns: List<ColumnInfo>, packagePrefix: String?) {
        val className = tableName.split("_")
            .joinToString("") { it.replaceFirstChar { char -> char.uppercase() } } + "Data"

        // Keep the full class name, just organize into sub-packages
        val packageName = if (packagePrefix != null) {
            "org.alter.tables.$packagePrefix"
        } else {
            "org.alter.tables"
        }

        val constructor = FunSpec.constructorBuilder()

        val properties = columns.map { column ->
            val name = column.name.toCamelCase()
            val type = getKotlinType(column.type)

            // Add parameter to constructor
            constructor.addParameter(name, type)

            // Create property initialized from constructor
            PropertySpec.builder(name, type)
                .initializer(name)
                .build()
        }

        val dataClassType = ClassName(packageName, className)
        val tableDataInterface = ClassName("org.alter.tables", "TableData")
            .parameterizedBy(dataClassType)
        
        // Create companion object with convert function
        val companionConvertFun = FunSpec.builder("convert")
            .addParameter("table", ClassName("org.alter.game.util", "DbHelper"))
            .returns(dataClassType)
            .addCode(buildConvertCode(tableName, columns, className, packagePrefix))
            .build()
        
        val companionObject = TypeSpec.companionObjectBuilder()
            .addFunction(companionConvertFun)
            .build()
        
        // Instance method that delegates to companion object (satisfies interface)
        val instanceConvertFun = FunSpec.builder("convert")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("table", ClassName("org.alter.game.util", "DbHelper"))
            .returns(dataClassType)
            .addCode("return Companion.convert(table)\n")
            .build()
        
        val dataClass = TypeSpec.classBuilder(className)
            .addModifiers(KModifier.DATA)
            .addSuperinterface(tableDataInterface)
            .primaryConstructor(constructor.build())
            .addProperties(properties)
            .addFunction(instanceConvertFun)
            .addType(companionObject)
            .build()

        val fileSpec = FileSpec.builder(packageName, className)
            .indent("    ")
            .addImport("org.alter.game.util", "DbHelper", "column", "multiColumnOptional")
            .addImport("org.alter.game.util.vars", "IntType", "ObjType")
            .addImport("org.alter.tables", "TableData")
            .addType(dataClass)
            .build()

        fileSpec.writeTo(environment.codeGenerator, aggregating = false)
        logger.info("Generated data class $className for table $tableName")
    }

    private fun buildMappingCode(tableName: String, columns: List<ColumnInfo>, className: String, packagePrefix: String?): CodeBlock {
        val packageName = if (packagePrefix != null) {
            "org.alter.tables.$packagePrefix"
        } else {
            "org.alter.tables"
        }
        
        val builder = CodeBlock.builder()
        builder.add("return %T(\n", ClassName(packageName, className))
        
        columns.forEachIndexed { index, column ->
            val propertyName = column.name.toCamelCase()
            val kotlinType = getKotlinType(column.type)
            val typeClass = getVarTypeImplClass(column.type)
            val indent = "    "
            
            if (kotlinType.toString().startsWith("kotlin.collections.List")) {
                builder.add("$indent$propertyName = this.multiColumnOptional(%S, %T).filterNotNull(),\n", 
                    "columns.$tableName:${column.name}", typeClass)
            } else {
                builder.add("$indent$propertyName = this.column(%S, %T),\n", 
                    "columns.$tableName:${column.name}", typeClass)
            }
        }
        
        builder.add(")")
        return builder.build()
    }
    
    private fun buildConvertCode(tableName: String, columns: List<ColumnInfo>, className: String, packagePrefix: String?): CodeBlock {
        val packageName = if (packagePrefix != null) {
            "org.alter.tables.$packagePrefix"
        } else {
            "org.alter.tables"
        }
        
        val builder = CodeBlock.builder()
        builder.add("return %T(\n", ClassName(packageName, className))
        
        columns.forEachIndexed { index, column ->
            val propertyName = column.name.toCamelCase()
            val kotlinType = getKotlinType(column.type)
            val typeClass = getVarTypeImplClass(column.type)
            val indent = "    "
            
            if (kotlinType.toString().startsWith("kotlin.collections.List")) {
                builder.add("$indent$propertyName = table.multiColumnOptional(%S, %T).filterNotNull(),\n", 
                    "columns.$tableName:${column.name}", typeClass)
            } else {
                builder.add("$indent$propertyName = table.column(%S, %T),\n", 
                    "columns.$tableName:${column.name}", typeClass)
            }
        }
        
        builder.add(")")
        return builder.build()
    }

    private fun getKotlinType(columnType: ColumnType): TypeName {
        return when (columnType) {
            is ColumnType.Single -> {
                getKotlinTypeFromVarType(columnType.varType)
            }
            is ColumnType.Array -> {
                // For arrays, determine the element type from the first VarType in the list
                val elementVarType: VarType = columnType.varTypes.firstOrNull() ?: VarType.INT
                val elementType = getKotlinTypeFromVarType(elementVarType)
                ClassName("kotlin.collections", "List")
                    .parameterizedBy(elementType)
            }
        }
    }

    private fun getKotlinTypeFromVarType(varType: VarType): TypeName {
        // For BOOLEAN, use Boolean directly, otherwise use baseType
        return if (varType == VarType.BOOLEAN) {
            ClassName("kotlin", "Boolean")
        } else {
            when (varType.baseType) {
                dev.openrune.definition.util.BaseVarType.INTEGER -> ClassName("kotlin", "Int")
                dev.openrune.definition.util.BaseVarType.LONG -> ClassName("kotlin", "Long")
                dev.openrune.definition.util.BaseVarType.STRING -> ClassName("kotlin", "String")
                else -> ClassName("kotlin", "Int") // Default fallback
            }
        }
    }
    
    private fun getVarTypeImplClass(columnType: ColumnType): ClassName {
        val varType: VarType = when (columnType) {
            is ColumnType.Single -> columnType.varType
            is ColumnType.Array -> columnType.varTypes.firstOrNull() ?: VarType.INT
        }
        
        return when (varType) {
            VarType.BOOLEAN -> ClassName("org.alter.game.util.vars", "BooleanType")
            VarType.STRING -> ClassName("org.alter.game.util.vars", "StringType")
            VarType.LONG -> ClassName("org.alter.game.util.vars", "LongType")
            VarType.NPC -> ClassName("org.alter.game.util.vars", "NpcType")
            VarType.LOC -> ClassName("org.alter.game.util.vars", "LocType")
            VarType.COORDGRID -> ClassName("org.alter.game.util.vars", "CoordType")
            VarType.MAPELEMENT -> ClassName("org.alter.game.util.vars", "MapElementType")
            VarType.DBROW -> ClassName("org.alter.game.util.vars", "RowType")
            else -> ClassName("org.alter.game.util.vars", "IntType")
        }
    }

    private fun extractPackagePrefix(className: String): Pair<String?, String> {
        // Common prefixes to extract (e.g., "Fletching", "Cluehelper")
        val prefixes = listOf(
            "Fletching", "Cluehelper", "Herblore", "Woodcutting", "Mining", 
            "Fishing", "Cooking", "Smithing", "Crafting", "Runecrafting",
            "Agility", "Thieving", "Slayer", "Construction", "Hunter",
            "Farming", "Prayer", "Magic", "Ranged", "Melee", "Combat"
        )
        
        for (prefix in prefixes) {
            if (className.startsWith(prefix)) {
                val remaining = className.substring(prefix.length)
                return Pair(prefix.lowercase(), remaining)
            }
        }
        
        return Pair(null, className)
    }

    private fun String.toCamelCase(): String {
        val result = split("_").joinToString("") { part ->
            part.lowercase().replaceFirstChar { it.uppercase() }
        }.replaceFirstChar { it.lowercase() }
        
        // Handle Kotlin keywords
        return when (result) {
            "object" -> "objectID"
            else -> result
        }
    }

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

