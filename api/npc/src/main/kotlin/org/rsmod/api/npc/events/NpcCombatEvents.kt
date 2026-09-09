package org.rsmod.api.npc.events

import org.rsmod.events.KeyedEvent
import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Npc
import org.rsmod.game.hit.Hit
import org.rsmod.game.hit.HitBuilder

public class NpcHitEvents {
    public data class Modify(
        public val npc: Npc,
        public val hit: HitBuilder,
        override val id: Long = npc.visType.id.toLong(),
    ) : KeyedEvent

    public data class Impact(
        public val npc: Npc,
        public val hit: Hit,
        override val id: Long = npc.visType.id.toLong(),
    ) : KeyedEvent

    /**
     * Published for every npc hit impact, whatever the npc type. [Impact] is keyed by npc type
     * and suits scripts about one npc; this one suits mechanics that apply to all of them, such as
     * weapon poison.
     */
    public data class AnyImpact(public val npc: Npc, public val hit: Hit) : UnboundEvent
}
