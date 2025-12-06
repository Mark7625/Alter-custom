package org.alter.skills.agility.regularcourses

import org.alter.api.Skills
import org.alter.api.ext.filterableMessage
import org.alter.game.model.Direction
import org.alter.game.model.ForcedMovement
import org.alter.game.model.Tile
import org.alter.game.model.attr.AttributeKey
import org.alter.game.model.entity.Player
import org.alter.game.model.move.moveTo
import org.alter.game.pluginnew.PluginEvent
import org.alter.game.pluginnew.event.impl.onObjectOption
import org.alter.rscm.RSCM
import org.alter.skills.agility.MarkOfGraceService

class GnomeStrongholdCoursePlugin : PluginEvent() {

    private val MAX_STAGES = 7
    private val BONUS_XP = 50.0
    private val DROP_CHANCE = 1.0 / 3.0 // 1/3 chance per lap completion

    private val MARK_SPAWN_TILES = listOf(
        Tile(2471, 3422, 1),
        Tile(2474, 3418, 2),
        Tile(2488, 3421, 2)
    )
    private val GraceService = MarkOfGraceService(
        spawnTiles = MARK_SPAWN_TILES,
        dropChance = DROP_CHANCE
    )
    val GNOME_AGILITY_STAGE = AttributeKey<Int>("GnomeAgilityStage")
    private fun Player.getStage(): Int = attr[GNOME_AGILITY_STAGE] ?: 0
    private fun Player.setStage(v: Int) { attr[GNOME_AGILITY_STAGE] = v }

    val GNOME_LAPS = AttributeKey<Int>("GnomeAgilityLaps")
    private fun Player.getLaps(): Int = attr[GNOME_LAPS] ?: 0
    private fun Player.setLaps(v: Int) { attr[GNOME_LAPS] = v }


    override fun init() {

        //Balance Log
        onObjectOption("objects.gnome_log_balance1", "walk-across") {
            handleObstacle(
                player = player,
                destination = Tile(2474, 3429, 0),
                anim = "sequences.human_walk_logbalance_loop",
                duration1 = 5,
                duration2 = 250,
                angle = Direction.SOUTH.angle,
                xp = 7.5,
                messageStart = "You walk carefully across the slippery log...",
                messageEnd = "... and make it safely to the other side.",
                stage = 1
            )
        }

        //Net 1
        onObjectOption("objects.obstical_net2", "climb-over") {
            if (player.tile.x !in 2471..2476) return@onObjectOption
            handleObstacle(
                player = player,
                destination = Tile(player.tile.x, 3424, 1),
                anim = "sequences.human_reachforladder",
                simpleMove = true,
                xp = 10.0,
                messageStart = "You climb up the netting...",
                stage = 2
            )
        }

        //Tree Branch
        onObjectOption("objects.climbing_branch", "climb") {
            handleObstacle(
                player = player,
                destination = Tile(2473, 3420, 2),
                anim = "sequences.human_reachforladder",
                simpleMove = true,
                xp = 6.5,
                messageStart = "You climb the tree...",
                messageEnd = "...To the platform above.",
                stage = 3
            )
        }

        //Balance Rope
        onObjectOption("objects.balancing_rope", "walk-on") {
            handleObstacle(
                player = player,
                destination = Tile(2483, 3420, 2),
                anim = "sequences.human_walk_logbalance_loop",
                duration1 = 5,
                duration2 = 250,
                angle = Direction.EAST.angle,
                xp = 10.0,
                messageStart = "You carefully cross the tightrope.",
                stage = 4
            )
        }

        //Climbing Tree (2 objects)
        onObjectOption("objects.climbing_tree", "climb-down") {
            handleObstacle(
                player = player,
                destination = Tile(2487, 3420, 0),
                anim = "sequences.human_reachforladder",
                simpleMove = true,
                xp = 6.5,
                messageStart = "You climb the tree...",
                messageEnd = "You land on the ground.",
                stage = 5
            )
        }
        onObjectOption("objects.climbing_tree2", "climb-down") {
            handleObstacle(
                player = player,
                destination = Tile(2487, 3420, 0),
                anim = "sequences.human_reachforladder",
                simpleMove = true,
                xp = 6.5,
                messageStart = "You climb the tree...",
                messageEnd = "You land on the ground.",
                stage = 5
            )
        }

        //Net 2
        onObjectOption("objects.obstical_net3", "climb-over") {
            if (player.tile.x !in 2483..2488) return@onObjectOption
            handleObstacle(
                player = player,
                destination = Tile(player.tile.x, 3428, 0),
                anim = "sequences.human_reachforladder",
                simpleMove = true,
                xp = 10.0,
                messageStart = "You climb up the netting...",
                stage = 6
            )
        }

        //Pipe (2 objects) — EndStage
        onObjectOption("objects.obstical_pipe3_1", "squeeze-through") {
            handleObstacle(
                player = player,
                destination = Tile(2484, 3437, 0),
                anim = "sequences.human_pipesqueeze",
                duration1 = 30,
                duration2 = 210,
                angle = Direction.NORTH.angle,
                xp = 10.0,
                stage = 7,
                endStage = true
            )
        }
        onObjectOption("objects.obstical_pipe3_2", "squeeze-through") {
            handleObstacle(
                player = player,
                destination = Tile(2487, 3437, 0),
                anim = "sequences.human_pipesqueeze",
                duration1 = 30,
                duration2 = 210,
                angle = Direction.NORTH.angle,
                xp = 10.0,
                stage = 7,
                endStage = true
            )
        }
    }

    private fun handleObstacle(
        player: Player,
        destination: Tile,
        simpleMove: Boolean = false,
        angle: Int? = null,
        duration1: Int? = null,
        duration2: Int? = null,
        anim: String? = null,
        xp: Double = 0.0,
        messageStart: String? = null,
        messageEnd: String? = null,
        stage: Int = -1,
        endStage: Boolean = false
    ) {

        val doForcedMove = angle != null && duration1 != null && duration2 != null

        val current = player.getStage()

        val isNext = current + 1 == stage
        val isRepeat = current == stage

        if (isNext) {
            player.setStage(stage)
        } else if (!isRepeat) {
            player.setStage(0)
        }

        player.queue {

            messageStart?.let { player.filterableMessage(it) }
            anim?.let { player.animate(it) }

            if (doForcedMove) {
                val movement = ForcedMovement.Companion.of(
                    src = player.tile,
                    dst = destination,
                    clientDuration1 = duration1,
                    clientDuration2 = duration2,
                    directionAngle = angle
                )
                player.forceMove(this, movement)
                wait(1)
                player.animate(RSCM.NONE)
            }
            else if (simpleMove) {
                wait(2)
                player.moveTo(destination)
                wait(1)
                player.animate(RSCM.NONE)
            }

            wait(1)
            if (xp > 0.0) player.addXp(Skills.AGILITY, xp)
            messageEnd?.let { player.filterableMessage(it) }

            handleStage(player, stage, endStage)
        }
    }

    private fun handleStage(
        player: Player,
        stage: Int,
        endStage: Boolean
    ) {
        val cur = player.getStage()

        if (!endStage && stage >= 0) {
            if (stage == cur + 1) {
                player.setStage(stage)
            }
            return
        }

        if (endStage) {
            val completed = cur == MAX_STAGES

            if (completed) {
                player.addXp(Skills.AGILITY, BONUS_XP)

                val laps = player.getLaps() + 1
                player.setLaps(laps)

                player.filterableMessage("Your Gnome Stronghold Agility lap count is: <col=ff0000>$laps</col>.")

                GraceService.spawnMarkofGrace(player)
            }

            player.setStage(0)
            return

        }
    }
}