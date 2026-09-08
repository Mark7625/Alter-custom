package org.rsmod.content.skills.thieving.pets

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType

/**
 * The three forms of the Thieving skill pet. Feeding the follower the matching berries turns it
 * into that form; white berries turn it back into the raccoon.
 */
enum class RockyPet(val obj: String, val npc: String, val petName: String, val berries: String) {
    RACCOON("obj.skillpetthieving", "npc.skillpet_thieving", "Rocky", "obj.white_berries"),
    RED_PANDA("obj.skillpetthieving_panda", "npc.skillpet_thieving_panda", "Red", "obj.redberries"),
    TANUKI("obj.skillpetthieving_tanuki", "npc.skillpet_thieving_tanuki", "Ziggy", "obj.poisonivy_berries");

    val objId: Int
        get() = obj.asRSCM(RSCMType.OBJ)

    companion object {
        private val byObj: Map<Int, RockyPet> by lazy { entries.associateBy { it.objId } }

        fun fromObj(objId: Int): RockyPet? = byObj[objId]

        fun fromBerries(obj: String): RockyPet? = entries.firstOrNull { it.berries == obj }
    }
}
