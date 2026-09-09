package org.rsmod.content.other.transformrings

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcServerType
import dev.openrune.util.Wearpos
import jakarta.inject.Inject
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.player.events.interact.HeldEquipEvents
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.worn.WornUnequipOp
import org.rsmod.api.player.worn.WornUnequipResult
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onPlayerCoordsChanged
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The transformation rings: Ring of stone, Ring of coins and Ring of nature.
 *
 * Wearing one of these rings turns the player into a rock, a pile of coins or a bush by
 * transmogrifying their appearance into the matching npc. The disguise lasts until the player
 * moves (or removes the ring); the Ring of stone additionally unequips itself when the player
 * unmorphs, as it does officially.
 *
 * The rings have no `Rub` or `Operate` option - equipping is the trigger - so this listens for
 * the ring wearpos changing rather than for an obj op.
 */
class TransformRingsScript @Inject constructor(private val unequipOp: WornUnequipOp) :
    PluginScript() {
    private val rings: Map<Int, TransformRing> =
        TransformRing.entries.associateBy { it.obj.asRSCM(RSCMType.OBJ) }

    override fun ScriptContext.startup() {
        onEvent<HeldEquipEvents.WearposChange> {
            if (wearpos == Wearpos.Ring) {
                onRingChange(player)
            }
        }
        onPlayerCoordsChanged { player.revertIfMoved() }
        onPlayerLogout { player.clearTransform() }
    }

    private fun onRingChange(player: Player) {
        val worn = player.worn[Wearpos.Ring.slot]
        val ring = worn?.let { rings[it.id] }
        if (ring == null) {
            player.clearTransform()
            return
        }
        player.transform(ring)
    }

    private fun Player.transform(ring: TransformRing) {
        val npcType = ring.npcType()
        transmog = npcType
        attr[TRANSFORM_RING_ATTR] = ring
        attr[TRANSFORM_COORDS_ATTR] = coords
        mes(ring.message)
    }

    /**
     * The coords-changed event fires every cycle whether or not the player moved, so the tile the
     * player transformed on is what decides whether they have moved since.
     */
    private fun Player.revertIfMoved() {
        val ring = attr[TRANSFORM_RING_ATTR] ?: return
        val origin = attr[TRANSFORM_COORDS_ATTR]
        if (origin == coords) {
            return
        }
        clearTransform()
        if (ring.unequipOnRevert) {
            unequipRing(ring)
        }
    }

    private fun Player.clearTransform() {
        if (attr[TRANSFORM_RING_ATTR] == null) {
            return
        }
        attr.remove(TRANSFORM_RING_ATTR)
        attr.remove(TRANSFORM_COORDS_ATTR)
        transmog = null
    }

    /**
     * Moves the ring back to the inventory. The ring stays worn if the inventory is full - the
     * player has already unmorphed either way.
     */
    private fun Player.unequipRing(ring: TransformRing) {
        val worn = worn[Wearpos.Ring.slot] ?: return
        if (rings[worn.id] != ring) {
            return
        }
        val result = unequipOp.unequip(this, Wearpos.Ring.slot, this.worn, inv)
        if (result is WornUnequipResult.Fail.NotEnoughInvSpace) {
            mes("Your ring stays on as you have no free inventory space.")
        }
    }

    private enum class TransformRing(
        val obj: String,
        val npc: String,
        val message: String,
        val unequipOnRevert: Boolean,
    ) {
        Stone(
            obj = "obj.enchanted_onyx_ring",
            npc = "npc.tzhaar_morph_rock",
            message = "You turn into a rock. Moving will turn you back.",
            unequipOnRevert = true,
        ),
        Coins(
            obj = "obj.ring_of_coins",
            npc = "npc.coins_transmog",
            message = "You turn into a pile of coins. Moving will turn you back.",
            unequipOnRevert = false,
        ),
        Nature(
            obj = "obj.ring_of_nature",
            npc = "npc.bush_transmog",
            message = "You turn into a bush. Moving will turn you back.",
            unequipOnRevert = false,
        );

        fun npcType(): NpcServerType =
            ServerCacheManager.getNpc(npc.asRSCM(RSCMType.NPC)) ?: error("Missing npc type: $npc")
    }

    private companion object {
        /** The ring the player is currently disguised by; absent when they look like themselves. */
        private val TRANSFORM_RING_ATTR = AttributeKey<TransformRing>()

        /** Where the player stood when they transformed; leaving that tile ends the disguise. */
        private val TRANSFORM_COORDS_ATTR = AttributeKey<CoordGrid>()
    }
}
