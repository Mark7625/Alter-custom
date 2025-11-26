package org.alter.codegen

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import dev.openrune.cache.gameval.GameValElement
import dev.openrune.cache.gameval.GameValHandler.elementAs
import dev.openrune.cache.gameval.impl.Table
import dev.openrune.definition.type.DBRowType
import dev.openrune.definition.util.BaseVarType
import dev.openrune.definition.util.VarType
import org.alter.ColInfo
import org.alter.game.util.DbException
import org.alter.game.util.DbHelper
import org.alter.game.util.DbQueryCache
import java.io.File

data class TableColumn(
    val name: String,
    val simpleName: String,
    val varTypes: Map<Int, VarType>? = null,
    val optional: Boolean = false
)

data class TableDef(
    val tableName: String,
    val className: String,
    val columns: List<TableColumn>
)

fun startGeneration(elements: List<GameValElement>, rows: MutableMap<Int, DBRowType>) {
    elements.forEach { element ->
        val table = element.elementAs<Table>() ?: return@forEach
        val colInfoMap = table.columns.associate { it.name to ColInfo() }.toMutableMap()

        table(table.id, rows).forEach { row ->
            table.columns.forEach { col ->
                val info = colInfoMap[col.name]!!
                try {
                    row.getColumn(col.id).types.forEachIndexed { index, type ->
                        info.types[index] = type
                    }
                } catch (e: DbException.MissingColumn) {
                    info.optional = true
                }
            }
        }

        val generatedColumns = table.columns.map { col ->
            val info = colInfoMap[col.name]!!
            TableColumn(
                name = "columns.${table.name}:${col.name}",
                simpleName = col.name,
                varTypes = info.types,
                optional = info.optional
            )
        }

        generateTable(
            TableDef(
                tableName = table.name,
                className = formatClassName(table.name),
                columns = generatedColumns
            ),
            File("../content/src/main/kotlin/org/")
        )
    }
}

private fun table(tableId: Int, rows: MutableMap<Int, DBRowType>): List<DbHelper> {
    return DbQueryCache.getTable(tableId.toString()) {
        rows.asSequence()
            .filter { it.value.tableId == tableId }
            .map { DbHelper(it.value) }
            .distinctBy { it.id }
            .toList()
    }
}

private fun formatClassName(tableName: String): String {
    return tableName
        .split('_', '-', '.', ':')
        .filter { it.isNotBlank() }
        .joinToString("") { it.replaceFirstChar { c -> c.uppercase() } } + "Row"
}

private const val BASE_PACKAGE = "org.generated.tables"
private const val VAR_TYPES_PACKAGE = "org.alter.game.util.vars"
private val PACKAGE_PREFIXES = listOf(
    "fletching", "cluehelper", "fsw", "herblore", "woodcutting", "mining",
    "fishing", "cooking", "smithing", "crafting", "runecrafting",
    "agility", "thieving", "slayer", "construction", "hunter",
    "farming", "prayer", "magic", "ranged", "melee", "combat", "sailing"
)

private fun findMatchingPrefix(tableName: String): String? {
    val lower = tableName.lowercase()
    return PACKAGE_PREFIXES.sortedByDescending { it.length }
        .firstOrNull { lower.startsWith(it) || lower.contains(it) }
}

private fun toCamelCase(name: String): String {
    val result = name.split("_").joinToString("") { 
        it.lowercase().replaceFirstChar { c -> c.uppercase() } 
    }.replaceFirstChar { it.lowercase() }
    return if (result == "object") "objectID" else result
}

private fun getKotlinType(varType: BaseVarType, optional: Boolean, isList: Boolean, isBooleanType: Boolean): TypeName {
    val baseType = if (isBooleanType) BOOLEAN else when (varType) {
        BaseVarType.INTEGER -> INT
        BaseVarType.STRING -> STRING
        BaseVarType.LONG -> LONG
        else -> LONG
    }
    return if (isList) {
        val elemType = if (optional) baseType.copy(nullable = true) else baseType
        LIST.parameterizedBy(elemType)
    } else {
        if (optional) baseType.copy(nullable = true) else baseType
    }
}

private fun getVarTypeImplClass(varType: VarType): ClassName {
    return ClassName(VAR_TYPES_PACKAGE, when (varType) {
        VarType.BOOLEAN -> "BooleanType"
        VarType.INT -> "IntType"
        VarType.STRING -> "StringType"
        VarType.LONG -> "LongType"
        VarType.NPC -> "NpcType"
        VarType.LOC -> "LocType"
        VarType.OBJ -> "ObjType"
        VarType.COORDGRID -> "CoordType"
        VarType.MAPELEMENT -> "MapElementType"
        VarType.DBROW -> "RowType"
        VarType.NAMEDOBJ -> "NamedObjType"
        VarType.GRAPHIC -> "GraphicType"
        VarType.SEQ -> "SeqType"
        VarType.MODEL -> "ModelType"
        VarType.STAT -> "StatType"
        VarType.CATEGORY -> "CategoryType"
        VarType.COMPONENT -> "ComponentType"
        VarType.INV -> "InvType"
        VarType.IDKIT -> "IdkType"
        VarType.ENUM -> "EnumType"
        VarType.MIDI -> "MidiType"
        VarType.VARP -> "VarpType"
        VarType.STRUCT -> "StructType"
        VarType.DBTABLE -> "TableType"
        VarType.SYNTH -> "SynthType"
        VarType.LOC_SHAPE -> "LocShapeType"
        else -> error("Unmapped Type: $varType")
    })
}

private val tuplesToGen = mutableSetOf<Int>()

fun generateTable(table: TableDef, outputDir: File) {
    val dbHelper = ClassName("org.alter.game.util", "DbHelper")
    val listType = ClassName("kotlin.collections", "List")
    val tileType = ClassName("org.alter.game.model", "Tile")
    val rscmType = ClassName("org.alter.rscm", "RSCMType")
    val rscm = ClassName("org.alter.rscm", "RSCM")

    val packageName = findMatchingPrefix(table.tableName)?.let { "$BASE_PACKAGE.$it" } ?: BASE_PACKAGE
    val rowClassName = ClassName(packageName, table.className)
    val tableOutputDir = outputDir.parentFile

    val usedColumnFunctions = mutableSetOf<String>()
    var needsTileImport = false
    val tuplesUsedInThisTable = mutableSetOf<Int>()

    val rowClassBuilder = TypeSpec.classBuilder(table.className)
        .primaryConstructor(FunSpec.constructorBuilder().addParameter("row", dbHelper).build())

    val fileBuilder = FileSpec.builder(packageName, table.className)
        .addFileComment(
            """
            |This file is AUTO-GENERATED. Do NOT edit manually.
            |Generated for table: ${table.tableName}
            """.trimMargin()
        )
        .addImport("org.alter.game.util", "DbHelper")
        .addImport("org.alter.rscm", "RSCM", "RSCMType")
        .addImport("org.alter.rscm.RSCM", "asRSCM")

    table.columns.forEach { col ->
        val sortedVarTypes = col.varTypes?.toSortedMap()?.values?.toList() ?: emptyList()
        if (sortedVarTypes.isEmpty()) return@forEach

        val firstVarType = sortedVarTypes.first()
        val isList = sortedVarTypes.size > 1
        val isMixed = sortedVarTypes.toSet().size > 1
        val isCoordType = firstVarType == VarType.COORDGRID
        val propertyName = toCamelCase(col.simpleName)

        val columnFunc = when {
            isList -> if (col.optional) "multiColumnOptional" else "multiColumn"
            else -> if (col.optional) "columnOptional" else "column"
        }
        usedColumnFunctions.add(columnFunc)
        if (isMixed) usedColumnFunctions.add("multiColumnMixed")

        if (isMixed) {
            val arity = sortedVarTypes.size
            tuplesToGen.add(arity)
            tuplesUsedInThisTable.add(arity)
            val tupleClass = ClassName("org.generated", "Tuple$arity")
            val typesArray = sortedVarTypes.map {
                getKotlinType(it.baseType!!, col.optional, false, it == VarType.BOOLEAN)
            }.toTypedArray()
            val tupleType = tupleClass.parameterizedBy(*typesArray)
            val nullableTupleType = if (col.optional) tupleType.copy(nullable = true) else tupleType

            val initializer = if (col.optional) {
                CodeBlock.of(
                    "row.multiColumnMixed(%S" + sortedVarTypes.joinToString("") { ", %T" } + ").toTuple$arity()",
                    col.name,
                    *sortedVarTypes.map { getVarTypeImplClass(it) }.toTypedArray()
                )
            } else {
                CodeBlock.of(
                    "row.multiColumnMixed(%S" + sortedVarTypes.joinToString("") { ", %T" } + ").toTuple$arity() ?: error(\"Column ${'$'}{%S} returned empty list but is not optional\")",
                    col.name,
                    *sortedVarTypes.map { getVarTypeImplClass(it) }.toTypedArray(),
                    col.name
                )
            }

            rowClassBuilder.addProperty(
                PropertySpec.builder(propertyName, nullableTupleType)
                    .initializer(initializer)
                    .build()
            )
            return@forEach
        }

        val kotlinType = if (isCoordType && !isList) {
            needsTileImport = true
            if (col.optional) tileType.copy(nullable = true) else tileType
        } else {
            getKotlinType(firstVarType.baseType!!, col.optional, isList, firstVarType == VarType.BOOLEAN)
        }

        val initializer = if (isCoordType && !isList) {
            val columnFunc = if (col.optional) "columnOptional" else "column"
            usedColumnFunctions.add(columnFunc)
            if (col.optional) {
                CodeBlock.of(
                    "row.$columnFunc(%S, %T)?.let { %T.from30BitHash(it) }",
                    col.name,
                    getVarTypeImplClass(firstVarType),
                    tileType
                )
            } else {
                CodeBlock.of("%T.from30BitHash(row.$columnFunc(%S, %T))", tileType, col.name, getVarTypeImplClass(firstVarType))
            }
        } else {
            val fmt = when {
                isList && col.optional -> "row.multiColumnOptional(%S" + sortedVarTypes.joinToString("") { ", %T" } + ")"
                isList -> "row.multiColumn(%S" + sortedVarTypes.joinToString("") { ", %T" } + ")"
                col.optional -> "row.columnOptional(%S, %T)"
                else -> "row.column(%S, %T)"
            }
            CodeBlock.of(fmt, col.name, *sortedVarTypes.map { getVarTypeImplClass(it) }.toTypedArray())
        }

        rowClassBuilder.addProperty(
            PropertySpec.builder(propertyName, kotlinType)
                .initializer(initializer)
                .build()
        )
    }

    val companion = TypeSpec.companionObjectBuilder()
        .addFunction(FunSpec.builder("all")
            .returns(listType.parameterizedBy(rowClassName))
            .addStatement("return %T.table(%S).map { %T(it) }", dbHelper, "tables.${table.tableName}", rowClassName)
            .build())
        .addFunction(FunSpec.builder("getRow")
            .addParameter("row", INT)
            .returns(rowClassName)
            .addStatement("return %T(%T.row(row))", rowClassName, dbHelper)
            .build())
        .addFunction(FunSpec.builder("getRow")
            .addParameter("column", String::class)
            .returns(rowClassName)
            .addStatement("%T.requireRSCM(%T.COLUMNS, column)", rscm, rscmType)
            .addStatement("return getRow(column.asRSCM() and 0xFFFF)")
            .build())
        .build()

    rowClassBuilder.addType(companion)

    val imports = mutableListOf("DbHelper")
    imports.addAll(usedColumnFunctions.sorted())
    fileBuilder.addImport("org.alter.game.util", *imports.toTypedArray())
    if (needsTileImport) fileBuilder.addImport("org.alter.game.model", "Tile")

    tuplesUsedInThisTable.forEach { arity ->
        fileBuilder.addImport("org.generated", "Tuple$arity")
        fileBuilder.addImport("org.generated", "toTuple$arity")
    }

    fileBuilder.addType(rowClassBuilder.build())
    fileBuilder.build().writeTo(tableOutputDir)
    generateAllTuples(tableOutputDir, tuplesToGen)
}

fun generateAllTuples(outputDir: File, tuplesToGen: MutableSet<Int>) {
    val packageName = "org.generated"
    val fileBuilder = FileSpec.builder(packageName, "Tuples")
        .addFileComment(
            """
            |WARNING: This file is AUTO-GENERATED. Do NOT edit manually.
            |This file contains tuple classes and extension functions used by generated table classes.
            """.trimMargin()
        )

    tuplesToGen.forEach { n ->
        val typeParams = (0 until n).map { "T$it" }
        val tupleClassName = ClassName(packageName, "Tuple$n")

        val tupleClassBuilder = TypeSpec.classBuilder("Tuple$n")
            .addModifiers(KModifier.PUBLIC, KModifier.DATA)
            .primaryConstructor(
                FunSpec.constructorBuilder().apply {
                    typeParams.forEach { tp -> addParameter(tp.lowercase(), TypeVariableName(tp)) }
                }.build()
            )

        typeParams.forEach { tp ->
            val typeVar = TypeVariableName(tp)
            tupleClassBuilder.addTypeVariable(typeVar)
            tupleClassBuilder.addProperty(
                PropertySpec.builder(tp.lowercase(), typeVar)
                    .initializer(tp.lowercase())
                    .build()
            )
        }

        fileBuilder.addType(tupleClassBuilder.build())

        val extFun = FunSpec.builder("toTuple$n")
            .receiver(ClassName("kotlin.collections", "List").parameterizedBy(STAR))
            .addTypeVariables(typeParams.map { TypeVariableName(it) })
            .returns(tupleClassName.parameterizedBy(typeParams.map { TypeVariableName(it) }).copy(nullable = true))
            .addCode(buildCodeBlock {
                add("if (size < %L) return null\n", n)
                add("return %T(", tupleClassName)
                typeParams.forEachIndexed { index, tp ->
                    if (index > 0) add(", ")
                    add("this[%L] as %T", index, TypeVariableName(tp))
                }
                add(")\n")
            })

        fileBuilder.addFunction(extFun.build())
    }

    fileBuilder.build().writeTo(outputDir)
}
