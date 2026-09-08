package org.rsmod.api.player.events.interact

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import dev.openrune.types.aconverted.interf.IfButtonOp
import dev.openrune.util.Wearpos
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.events.EventBus
import org.rsmod.events.KeyedEvent
import org.rsmod.events.SuspendEvent
import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player
import org.rsmod.game.interact.HeldOp
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory

public class HeldBanksideEvents {
    public class Type(
        public val player: Player,
        public val slot: Int,
        public val type: ItemServerType,
        public val op: IfButtonOp = IfButtonOp.Op9,
        override val id: Long = type.id.toLong(),
    ) : KeyedEvent
}

public class HeldDropEvents {
    public class Trigger(
        public val player: Player,
        public val dropSlot: Int,
        public val obj: InvObj,
        public val type: ItemServerType,
        triggerType: String,
        override val id: Long = triggerType.asRSCM().toLong(),
    ) : KeyedEvent {
        init {
            RSCM.requireRSCM(RSCMType.DROP_TRIGGER, triggerType)
        }
    }

    public data class Drop(
        public val player: Player,
        public val dropSlot: Int,
        public val obj: InvObj,
        public val type: ItemServerType,
    ) : UnboundEvent

    public data class Destroy(
        public val player: Player,
        public val invSlot: Int,
        public val obj: InvObj,
        public val type: String,
    ) : UnboundEvent

    public data class Release(
        public val player: Player,
        public val invSlot: Int,
        public val obj: InvObj,
        public val type: String,
    ) : UnboundEvent

    public data class Dispose(
        public val player: Player,
        public val invType: String,
        public val invSlot: Int,
        public val obj: InvObj,
    ) : UnboundEvent
}

public class HeldEquipEvents {
    public data class Equip(
        public val player: Player,
        public val invSlot: Int,
        public val wearpos: Wearpos,
        public val type: ItemServerType,
        override val id: Long = type.contentGroup.toLong(),
    ) : KeyedEvent

    public data class Unequip(
        public val player: Player,
        public val wearpos: Wearpos,
        public val type: ItemServerType,
        override val id: Long = type.contentGroup.toLong(),
    ) : KeyedEvent

    public data class WearposChange(
        public val player: Player,
        public val wearpos: Wearpos,
        public val objType: ItemServerType,
    ) : UnboundEvent
}

public sealed class HeldObjEvents(id: Number) : OpEvent(id.toLong()) {
    public class Op1(
        public val slot: Int,
        public val obj: InvObj,
        public val type: ItemServerType,
        public val inventory: Inventory,
    ) : HeldObjEvents(obj.id)

    public class Op2(
        public val slot: Int,
        public val obj: InvObj,
        public val type: ItemServerType,
        public val inventory: Inventory,
    ) : HeldObjEvents(obj.id)

    public class Op3(
        public val slot: Int,
        public val obj: InvObj,
        public val type: ItemServerType,
        public val inventory: Inventory,
    ) : HeldObjEvents(obj.id)

    public class Op4(
        public val slot: Int,
        public val obj: InvObj,
        public val type: ItemServerType,
        public val inventory: Inventory,
    ) : HeldObjEvents(obj.id)

    public class Op5(
        public val slot: Int,
        public val obj: InvObj,
        public val type: ItemServerType,
        public val inventory: Inventory,
    ) : HeldObjEvents(obj.id)
}

public data class HeldSubOpEvent(
    val slot: Int,
    val obj: InvObj,
    val type: ItemServerType,
    val inventory: Inventory,
    val op: HeldOp,
    val subop: Int,
) : SuspendEvent<ProtectedAccess> {
    override val id: Long = type.id.toLong()
}

public sealed class HeldContentEvents(id: Number) : OpEvent(id.toLong()) {
    public class Op1(
        public val slot: Int,
        public val obj: InvObj,
        public val type: ItemServerType,
        public val inventory: Inventory,
    ) : HeldContentEvents(type.contentGroup)

    public class Op2(
        public val slot: Int,
        public val obj: InvObj,
        public val type: ItemServerType,
        public val inventory: Inventory,
    ) : HeldContentEvents(type.contentGroup)

    public class Op3(
        public val slot: Int,
        public val obj: InvObj,
        public val type: ItemServerType,
        public val inventory: Inventory,
    ) : HeldContentEvents(type.contentGroup)

    public class Op4(
        public val slot: Int,
        public val obj: InvObj,
        public val type: ItemServerType,
        public val inventory: Inventory,
    ) : HeldContentEvents(type.contentGroup)

    public class Op5(
        public val slot: Int,
        public val obj: InvObj,
        public val type: ItemServerType,
        public val inventory: Inventory,
    ) : HeldContentEvents(type.contentGroup)
}

public class HeldUEvents {
    public class Type(
        public val first: ItemServerType,
        public val firstSlot: Int,
        public val second: ItemServerType,
        public val secondSlot: Int,
    ) : SuspendEvent<ProtectedAccess> {
        override val id: Long = EventBus.composeLongKey(first.id, second.id)
    }
}

public class HeldUContentEvents {
    public class Type(
        public val first: ItemServerType,
        public val firstSlot: Int,
        public val second: ItemServerType,
        public val secondSlot: Int,
    ) : SuspendEvent<ProtectedAccess> {
        override val id: Long = EventBus.composeLongKey(first.contentGroup, second.id)
    }

    public class Content(
        public val first: ItemServerType,
        public val firstSlot: Int,
        public val second: ItemServerType,
        public val secondSlot: Int,
    ) : SuspendEvent<ProtectedAccess> {
        override val id: Long = EventBus.composeLongKey(first.contentGroup, second.contentGroup)
    }
}

public class HeldUDefaultEvents {
    public class Type(
        public val first: ItemServerType,
        public val firstSlot: Int,
        public val second: ItemServerType,
        public val secondSlot: Int,
    ) : SuspendEvent<ProtectedAccess> {
        override val id: Long = first.id.toLong()
    }

    public class Content(
        public val first: ItemServerType,
        public val firstSlot: Int,
        public val second: ItemServerType,
        public val secondSlot: Int,
    ) : SuspendEvent<ProtectedAccess> {
        override val id: Long = first.contentGroup.toLong()
    }
}
