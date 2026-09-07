package org.rsmod.content.other.pets.cats

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType

/** The four cat life stages that exist as follower npcs in the cache. */
enum class CatStage(
    /** Lower-case name used in game messages, e.g. "Your kitten has run away." */
    val label: String,
    /** Held obj per [CatColour] (ordinal-indexed). */
    val objs: List<String>,
    /** Follower npc per [CatColour] (ordinal-indexed). */
    val npcs: List<String>,
    /** Cycles of following before the cat grows into [next]; `null` for a terminal stage. */
    val growthCycles: Int?,
    /** Stage this cat grows into, or `null` if it never grows further. */
    val next: CatStage?,
    /** Whether the cat is still young enough to catch rats. */
    val canChase: Boolean,
) {
    LAZY(
        label = "lazy cat",
        objs =
            listOf(
                "obj.lazycatobject",
                "obj.lazycatobject_light",
                "obj.lazycatobject_brown",
                "obj.lazycatobject_black",
                "obj.lazycatobject_browngrey",
                "obj.lazycatobject_bluegrey",
                "obj.lazycatobject_hell",
            ),
        npcs =
            listOf(
                "npc.lazycat",
                "npc.lazycat_light",
                "npc.lazycat_brown",
                "npc.lazycat_black",
                "npc.lazycat_browngrey",
                "npc.lazycat_bluegrey",
                "npc.lazycat_hell",
            ),
        growthCycles = null,
        next = null,
        canChase = false,
    ),
    OVERGROWN(
        label = "cat",
        objs =
            listOf(
                "obj.overgrowncatobject",
                "obj.overgrowncatobject_light",
                "obj.overgrowncatobject_brown",
                "obj.overgrowncatobject_black",
                "obj.overgrowncatobject_browngrey",
                "obj.overgrowncatobject_bluegrey",
                "obj.overgrowncatobject_hell",
            ),
        npcs =
            listOf(
                "npc.overgrowncat",
                "npc.overgrowncat_light",
                "npc.overgrowncat_brown",
                "npc.overgrowncat_black",
                "npc.overgrowncat_browngrey",
                "npc.overgrowncat_bluegrey",
                "npc.overgrowncat_hell",
            ),
        growthCycles = null,
        next = null,
        canChase = false,
    ),
    CAT(
        label = "cat",
        objs =
            listOf(
                "obj.growncatobject",
                "obj.growncatobject_light",
                "obj.growncatobject_brown",
                "obj.growncatobject_black",
                "obj.growncatobject_browngrey",
                "obj.growncatobject_bluegrey",
                "obj.growncatobject_hell",
            ),
        npcs =
            listOf(
                "npc.growncat",
                "npc.growncat_light",
                "npc.growncat_brown",
                "npc.growncat_black",
                "npc.growncat_browngrey",
                "npc.growncat_bluegrey",
                "npc.growncat_hell",
            ),
        // Five and a half hours of following, the middle of the wiki's 5-6 hour range.
        growthCycles = 33_000,
        next = OVERGROWN,
        canChase = true,
    ),
    KITTEN(
        label = "kitten",
        objs =
            listOf(
                "obj.kittenobject",
                "obj.kittenobject_light",
                "obj.kittenobject_brown",
                "obj.kittenobject_black",
                "obj.kittenobject_browngrey",
                "obj.kittenobject_bluegrey",
                "obj.kittenobject_hell",
            ),
        npcs =
            listOf(
                "npc.kittenpet1",
                "npc.kittenpet_light",
                "npc.kittenpet_brown",
                "npc.kittenpet_black",
                "npc.kittenpet_browngrey",
                "npc.kittenpet_bluegrey",
                "npc.kittenpet_hell",
            ),
        // Three hours of following.
        growthCycles = 18_000,
        next = CAT,
        canChase = true,
    ),
}

enum class CatColour {
    DEFAULT,
    LIGHT,
    BROWN,
    BLACK,
    BROWNGREY,
    BLUEGREY,
    HELL,
}

/** One concrete cat: a life stage in a colour. Resolves to a held obj and a follower npc. */
data class CatPet(val stage: CatStage, val colour: CatColour) {
    val obj: String
        get() = stage.objs[colour.ordinal]

    val npc: String
        get() = stage.npcs[colour.ordinal]

    val objId: Int
        get() = obj.asRSCM(RSCMType.OBJ)

    val npcId: Int
        get() = npc.asRSCM(RSCMType.NPC)

    val isKitten: Boolean
        get() = stage == CatStage.KITTEN

    val isHellcat: Boolean
        get() = colour == CatColour.HELL

    /** The same cat one stage older, or `null` if it is fully grown. */
    fun grown(): CatPet? = stage.next?.let { CatPet(it, colour) }

    companion object {
        val all: List<CatPet> =
            CatStage.entries.flatMap { stage -> CatColour.entries.map { CatPet(stage, it) } }

        private val byObj: Map<Int, CatPet> by lazy { all.associateBy { it.objId } }
        private val byNpc: Map<Int, CatPet> by lazy { all.associateBy { it.npcId } }

        fun fromObj(objId: Int): CatPet? = byObj[objId]

        fun fromNpc(npcId: Int): CatPet? = byNpc[npcId]
    }
}
