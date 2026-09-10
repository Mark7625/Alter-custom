package dev.openrune.types.enums

import dev.openrune.definition.type.EnumType
import dev.openrune.definition.util.CacheVarLiteral
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class EnumTypeMapTest {
    @Test
    fun `int enum exposes declared default`() {
        val enum = intEnum(defaultInt = 7, values = mutableMapOf(1 to 10))
        assertEquals(7, EnumTypeMap<Int, Int>(enum).default)
    }

    @Test
    fun `string enum exposes declared default`() {
        val enum = stringEnum(defaultString = "fallback", values = mutableMapOf(1 to "a"))
        assertEquals("fallback", EnumTypeMap<Int, String>(enum).default)
    }

    @Test
    fun `int enum without declared default has no default`() {
        val enum = intEnum(defaultInt = 0, values = mutableMapOf(1 to 10))
        assertNull(EnumTypeMap<Int, Int>(enum).default)
    }

    @Test
    fun `string enum without declared default has no default`() {
        val enum = stringEnum(defaultString = "", values = mutableMapOf(1 to "a"))
        assertNull(EnumTypeMap<Int, String>(enum).default)
    }

    @Test
    fun `non-null map keeps declared default`() {
        val enum = intEnum(defaultInt = 7, values = mutableMapOf(1 to 10))
        val map = EnumTypeMap<Int, Int>(enum).filterValuesNotNull()

        assertEquals(7, map[2])
    }

    @Test
    fun `non-null map prefers entry over declared default`() {
        val enum = intEnum(defaultInt = 7, values = mutableMapOf(1 to 10))
        val map = EnumTypeMap<Int, Int>(enum).filterValuesNotNull()

        assertEquals(10, map[1])
    }

    @Test
    fun `non-null map throws for missing key when no default is declared`() {
        val enum = intEnum(defaultInt = 0, values = mutableMapOf(1 to 10))
        val map = EnumTypeMap<Int, Int>(enum).filterValuesNotNull()

        assertThrows<NoSuchElementException> { map[2] }
    }

    @Test
    fun `non-null map drops entries whose value decodes to null`() {
        val enum =
            EnumType(
                id = 1,
                keyType = CacheVarLiteral.INT,
                valueType = CacheVarLiteral.COMPONENT,
                values = mutableMapOf(1 to -1),
            )
        val map = EnumTypeMap<Int, Any>(enum)

        assertNull(map[1])
        assertThrows<NoSuchElementException> { map.filterValuesNotNull()[1] }
    }

    private fun intEnum(defaultInt: Int, values: MutableMap<Int, Any>): EnumType =
        EnumType(
            id = 1,
            keyType = CacheVarLiteral.INT,
            valueType = CacheVarLiteral.INT,
            defaultInt = defaultInt,
            values = values,
        )

    private fun stringEnum(defaultString: String, values: MutableMap<Int, Any>): EnumType =
        EnumType(
            id = 1,
            keyType = CacheVarLiteral.INT,
            valueType = CacheVarLiteral.STRING,
            defaultString = defaultString,
            values = values,
        )
}
