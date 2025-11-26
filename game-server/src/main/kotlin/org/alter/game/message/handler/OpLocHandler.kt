package org.alter.game.message.handler

import dev.openrune.cache.CacheManager
import net.rsprot.protocol.game.incoming.locs.OpLoc
import org.alter.game.message.MessageHandler
import org.alter.game.model.EntityType
import org.alter.game.model.Tile
import org.alter.game.model.attr.INTERACTING_OBJ_ATTR
import org.alter.game.model.attr.INTERACTING_OPT_ATTR
import org.alter.game.model.entity.Client
import org.alter.game.model.entity.Entity
import org.alter.game.model.entity.GameObject
import org.alter.game.model.entity.Player
import org.alter.game.model.move.ObjectPathAction.walk
import org.alter.game.model.move.moveTo
import org.alter.game.model.move.stopMovement
import org.alter.game.model.priv.Privilege
import org.alter.game.pluginnew.MenuOption
import org.alter.game.pluginnew.event.EventManager
import org.alter.game.pluginnew.event.impl.ObjectClickEvent
import java.lang.ref.WeakReference

/**
 * @author Tom <rspsmods@gmail.com>
 */
class OpLocHandler : MessageHandler<OpLoc> {
    override fun consume(
        client: Client,
        message: OpLoc,
    ) {
        // NOTE: OP3 used to just be Ground Item action 3
        /*
         * If tile is too far away, don't process it.
         */
        val tile = Tile(message.x, message.z, client.tile.height)
        if (!tile.viewableFrom(client.tile, Player.TILE_VIEW_DISTANCE)) {
            return
        }

        /*
         * If player can't move, we don't do anything.
         */
        if (!client.lock.canMove()) {
            return
        }

        /*
         * Get the region chunk that the object would belong to.
         */
        val chunk = client.world.chunks.getOrCreate(tile)
        val obj = chunk
            .getEntities<GameObject>(tile, EntityType.STATIC_OBJECT, EntityType.DYNAMIC_OBJECT)
            .firstOrNull { it.internalID == message.id }
            ?: return


        client.stopMovement()
        client.closeInterfaceModal()
        client.interruptQueues()
        client.resetInteractions()

        if (message.controlKey && client.world.privileges.isEligible(client.privilege, Privilege.ADMIN_POWER)) {
            val def = obj.getDef()
            client.moveTo(
                client.world.findRandomTileAround(obj.tile, radius = 1, centreWidth = def.sizeX, centreLength = def.sizeY) ?: obj.tile,
            )
        }

        client.attr[INTERACTING_OPT_ATTR] = message.op
        client.attr[INTERACTING_OBJ_ATTR] = WeakReference(obj)

        val lineOfSightRange = client.world.plugins.getObjInteractionDistance(obj.internalID)

        val transform = obj.getTransform(client)
        val extraInfo = if (transform != message.id) {
            val def = CacheManager.getObject(obj.internalID)
           "multiloc=[${def?.transforms?.joinToString(", ")}]"
        } else {
            ""
        }

        log(
            client,
            "Object action %d: old=%d, new=%d, x=%d, y=%d, movement=%b%s",
            message.op,
            message.id,
            transform,
            message.x,
            message.z,
            message.controlKey,
            extraInfo
        )

        walk(client, obj, lineOfSightRange) {

            val handledByNewSystem = EventManager.postWithResult(ObjectClickEvent(obj, MenuOption.fromId(message.op), transform,client))
            val handledByOldSystem = client.world.plugins.executeObject(client, transform, message.op)

            if (!handledByNewSystem && !handledByOldSystem) {
                client.writeMessage(Entity.NOTHING_INTERESTING_HAPPENS)
                if (client.world.devContext.debugObjects) {
                    client.writeMessage(
                        "Unhandled object action: [opt=${message.op}, id=${obj.internalID}, type=${obj.type}, rot=${obj.rot}, x=${obj.tile.x}, y=${obj.tile.z}]",
                    )
                }
            }
        }

    }
}
