package dev.openrune.types.enums

public class EnumTypeNonNullMap<K : Any, V : Any>(
    private val entries: Map<K, V>,
    private val default: V?,
) : Iterable<Map.Entry<K, V>> {

    public val backing: Map<K, V>
        get() = entries

    public val keys: Set<K>
        get() = entries.keys

    public val values: Collection<V>
        get() = entries.values

    public operator fun get(key: K): V =
        entries[key] ?: default ?: throw NoSuchElementException("Missing key $key")

    public fun getOrNull(key: K): V? = entries[key]

    override fun iterator(): Iterator<Map.Entry<K, V>> = entries.entries.iterator()

    public companion object {
        public fun <K : Any, V : Any> from(other: EnumTypeMap<K, V>): EnumTypeNonNullMap<K, V> {
            val filtered =
                other
                    .associate { it.key to it.value }
                    .filterValues { it != null }
                    .mapValues { it.value!! }

            return EnumTypeNonNullMap(filtered, other.default)
        }
    }
}
