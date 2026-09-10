package org.rsmod.content.skills.fletching.pack

import dev.openrune.definition.dbtables.DBTable
import dev.openrune.pack.PluginPack

class FletchingPluginPack : PluginPack() {
    override fun dbTables(): List<DBTable> =
        listOf(
            FletchingTables.cutting(),
            FletchingTables.stringing(),
            FletchingTables.attaching(),
            FletchingTables.gemTips(),
            FletchingTables.assembly(),
        )
}
