package org.rsmod.content.other.npcaggression

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.toml.TomlFactory
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

/**
 * One npc's aggression entry. [wiki] is the page the value came from and [note] any condition the
 * wiki attaches (an item that pacifies it, a quest that removes it) which is recorded but not yet
 * modelled. [aggressive] is only ever `false` in the overrides file, to silence a generated entry.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class NpcAggressionEntry(
    val id: String,
    val wiki: String? = null,
    val note: String? = null,
    val aggressive: Boolean = true,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class NpcAggressionFile(val npc: List<NpcAggressionEntry> = emptyList()) {
    companion object {
        /** Generated from the OSRS wiki; see `docs/aggression.md`. */
        const val GENERATED_RESOURCE: String = "npc-aggression.toml"

        /** Hand-maintained corrections. Applied after the generated file and always win. */
        const val OVERRIDES_RESOURCE: String = "npc-aggression-overrides.toml"

        private val mapper: ObjectMapper = ObjectMapper(TomlFactory()).registerKotlinModule()

        fun loadResource(name: String): NpcAggressionFile {
            val stream =
                NpcAggressionFile::class.java.classLoader.getResourceAsStream(name)
                    ?: error("Missing resource: $name")
            return stream.use { parse(it.readBytes().decodeToString()) }
        }

        fun parse(toml: String): NpcAggressionFile = mapper.readValue(toml)
    }
}
