package org.rsmod.content.bosses.spindel

import jakarta.inject.Inject
import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossPluginScript

class SpindelSpiderling @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {

    override val spec =
        boss("npc.spindel_spiderling") {
            stats(attackRate = ATTACK_RATE, aggressionRadius = AGGRO_RANGE)

            val bite =
                ability("bite") {
                    anim("seq.small_spider_update_attack")
                    hit {
                        damage(0..MAX_HIT).roll()
                        type(Melee)
                    }
                    statDrain("stat.prayer", amount = PRAYER_DRAIN_AMOUNT)
                }

            phase(PHASE_DEFAULT) { weightedSelectorRandom { +random(bite, weight = 1) } }
        }

    private companion object {
        private const val PHASE_DEFAULT = "combat"
        private const val ATTACK_RATE = 4
        private const val AGGRO_RANGE = 1
        private const val MAX_HIT = 1
        private const val PRAYER_DRAIN_AMOUNT = 1
    }
}
