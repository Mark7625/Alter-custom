package org.alter.skills.mining

import org.alter.api.Skills
import org.alter.game.model.World
import org.alter.game.model.entity.GameObject
import org.alter.game.model.entity.Player
import org.alter.game.pluginnew.event.PlayerEvent
import org.alter.game.pluginnew.event.impl.SkillingActionCompletedGatheringEvent
import org.generated.tables.mining.MiningRocksRow

/**
 * Event triggered when a rock is depleted during mining.
 *
 * @param player The player who mined the rock.
 * @param rockObject The GameObject representing the rock that was depleted.
 * @param rockRscm The RSCM identifier of the rock.
 * @param rockType The database row identifier for the rock type.
 * @param world The game world.
 */
class RockDepleteEvent(
    override val player: Player,
    val rockObject: GameObject,
    val rockType: String,
    val world: World,
) : PlayerEvent(player)

/**
 * Event triggered when a player successfully obtains ore from a rock.
 */
class RockOreObtainedEvent(
    override val player: Player,
    rockObject: GameObject,
    rockData: MiningRocksRow,
    val rockType: String = rockData.type,
    val clueBaseChance: Int = rockData.clueBaseChance,
    override val resourceId: Int = rockData.oreItem ?: -1,
) : SkillingActionCompletedGatheringEvent(
    player = player,
    skill = Skills.MINING,
    actionObject = rockObject,
    experienceGained = rockData.xp.toDouble(),
    resourceId = resourceId,
    amountGathered = 1,
)

