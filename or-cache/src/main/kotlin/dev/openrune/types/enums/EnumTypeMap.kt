package dev.openrune.types.enums

import dev.openrune.definition.type.EnumType
import dev.openrune.literal.BaseStringVarCodec
import dev.openrune.literal.CacheVarCodec
import dev.openrune.literal.CacheVarTypeMap

public class EnumTypeMap<K : Any, V : Any>(private val raw: EnumType) : Iterable<Map.Entry<K, V?>> {

    public val id: Int = raw.id

    private val keyCodec: CacheVarCodec<Any, K> = CacheVarTypeMap.findCodec(raw.keyType)

    private val valueCodec: CacheVarCodec<Any, V> = CacheVarTypeMap.findCodec(raw.valueType)

    private val entries: Map<K, V?> by lazy {
        raw.values.entries.associate { (k, v) ->
            val key = requireNotNull(keyCodec.decode(k)) { "Null key decoded in enum ${raw.id}" }
            val value = v.let { valueCodec.decode(it) }
            key to value
        }
    }

    private val declaredDefault: Any?
        get() =
            when (valueCodec) {
                is BaseStringVarCodec<*> -> raw.defaultString.ifEmpty { null }
                else -> raw.defaultInt.takeIf { it != 0 }
            }

    public val default: V? by lazy { declaredDefault?.let(valueCodec::decode) }

    public val backing: Map<K, V?>
        get() = entries

    public val keys: Set<K>
        get() = entries.keys

    public val values: Collection<V?>
        get() = entries.values

    public val isEmpty: Boolean
        get() = entries.isEmpty()

    public val isNotEmpty: Boolean
        get() = entries.isNotEmpty()

    public operator fun get(key: K): V? = entries[key]

    public fun getOrNull(key: K): V? = entries[key]

    public fun getValue(key: K): V =
        entries[key] ?: throw NoSuchElementException("Missing key $key in enum ${raw.id}")

    public operator fun contains(key: K): Boolean = key in entries

    override fun iterator(): Iterator<Map.Entry<K, V?>> = entries.entries.iterator()

    public fun filterValuesNotNull(): EnumTypeNonNullMap<K, V> = EnumTypeNonNullMap.from(this)
}
