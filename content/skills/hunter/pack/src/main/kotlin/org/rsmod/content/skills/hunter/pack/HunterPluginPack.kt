package org.rsmod.content.skills.hunter.pack

import dev.openrune.definition.dbtables.DBTable
import dev.openrune.pack.PluginPack

class HunterPluginPack : PluginPack() {
    override fun dbTables(): List<DBTable> =
        listOf(
            HunterTables.snareCreatures(),
            HunterTables.boxCreatures(),
        )
}
