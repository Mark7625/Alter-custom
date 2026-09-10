package org.rsmod.content.skills.fletching

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import org.rsmod.api.enums.NamedEnums.fletching_anims

/**
 * Per-recipe fletching animations, read from `enum.fletching_anims`.
 *
 * Cutting shares one animation; stringing and attaching each have one per bow tier and per
 * bolt/dart metal. An output with no entry resolves to null and leaves the caller on [GENERIC], so
 * a row can only ever be unanimated, never wrongly animated.
 */
internal object FletchingAnims {
    /**
     * Loops (`loops=15`, `maxloops=2`), so it covers a run of actions and has to be cancelled when
     * the run ends. [GENERIC_SINGLE] is the same frames without the looping; every animation in the
     * enum is likewise a one-shot.
     */
    const val GENERIC: String = "seq.human_fletching"

    const val GENERIC_SINGLE: String = "seq.human_fletching_single"

    private val byOutputId: Map<Int, String> by lazy {
        fletching_anims
            .mapNotNull { (obj, seq) ->
                seq?.let { obj.id to RSCM.getReverseMapping(RSCMType.SEQ, it.id) }
            }
            .toMap()
    }

    fun forOutput(output: ItemServerType): String? = byOutputId[output.id]
}
