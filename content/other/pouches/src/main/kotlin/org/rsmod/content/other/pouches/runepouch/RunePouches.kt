package org.rsmod.content.other.pouches.runepouch

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import org.rsmod.api.enums.RuneEnums.rune_compact_ids
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.entity.Player

object RunePouches {
    const val REGULAR = "obj.bh_rune_pouch"
    const val REGULAR_LOCKED = "obj.bh_rune_pouch_trouver"
    const val DIVINE = "obj.divine_rune_pouch"
    const val DIVINE_LOCKED = "obj.divine_rune_pouch_trouver"

    val ALL = listOf(REGULAR, REGULAR_LOCKED, DIVINE, DIVINE_LOCKED)

    const val REGULAR_SLOTS = 3
    const val DIVINE_SLOTS = 4

    val ids: Set<Int> by lazy { ALL.map { it.asRSCM(RSCMType.OBJ) }.toSet() }
    val divineIds: Set<Int> by lazy {
        setOf(DIVINE.asRSCM(RSCMType.OBJ), DIVINE_LOCKED.asRSCM(RSCMType.OBJ))
    }

    val TYPE_VARBITS =
        listOf(
            "varbit.rune_pouch_type_1",
            "varbit.rune_pouch_type_2",
            "varbit.rune_pouch_type_3",
            "varbit.rune_pouch_type_4",
        )

    val COUNT_VARBITS =
        listOf(
            "varbit.rune_pouch_quantity_1",
            "varbit.rune_pouch_quantity_2",
            "varbit.rune_pouch_quantity_3",
            "varbit.rune_pouch_quantity_4",
        )
}

/** The runes a pouch accepts, keyed both ways between obj id and compact id. */
object RunePouchRunes {
    class Rune(val id: Int, val name: String, val compactId: Int, val type: ItemServerType)

    private val runes: List<Rune> by lazy {
        rune_compact_ids.backing.mapNotNull { (type, compact) ->
            compact?.let { Rune(type.id, RSCM.getReverseMapping(RSCMType.OBJ, type.id), it, type) }
        }
    }

    private val byObjId: Map<Int, Rune> by lazy { runes.associateBy { it.id } }
    private val byCompactId: Map<Int, Rune> by lazy { runes.associateBy { it.compactId } }

    fun byObj(objId: Int): Rune? = byObjId[objId]

    fun byCompact(compactId: Int): Rune? = byCompactId[compactId]
}

/** Slots of the pouch the player carries, or `null` without one. A divine pouch wins if both. */
fun Player.runePouchSlots(): Int? =
    when {
        inv.any { it != null && it.id in RunePouches.divineIds } -> RunePouches.DIVINE_SLOTS
        inv.any { it != null && it.id in RunePouches.ids } -> RunePouches.REGULAR_SLOTS
        else -> null
    }

fun Player.readRunePouch(slots: Int): RunePouchContents =
    RunePouchContents(
        types = IntArray(slots) { vars[RunePouches.TYPE_VARBITS[it]] },
        counts = IntArray(slots) { vars[RunePouches.COUNT_VARBITS[it]] },
    )

fun Player.writeRunePouch(contents: RunePouchContents) {
    for (slot in 0 until contents.slots) {
        VarPlayerIntMapSetter.set(this, RunePouches.TYPE_VARBITS[slot], contents.types[slot])
        VarPlayerIntMapSetter.set(this, RunePouches.COUNT_VARBITS[slot], contents.counts[slot])
    }
}
