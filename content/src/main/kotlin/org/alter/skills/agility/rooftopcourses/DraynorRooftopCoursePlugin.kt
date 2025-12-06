package org.alter.skills.agility.rooftopcourses

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

class DraynorRooftopCoursePlugin : PluginEvent() {

    private val MAX_STAGES = 7
    private val BONUS_XP = 120.0
    private val DROP_CHANCE = 0.15 // 15% chance per obstacle

    private val MARK_SPAWN_TILES = listOf(
        Tile(3088, 3275, 3),
    )
    private val GraceService = MarkOfGraceService(
        spawnTiles = MARK_SPAWN_TILES,
        dropChance = DROP_CHANCE
    )
    val DRAYNOR_AGILITY_STAGE = AttributeKey<Int>("DraynorAgilityStage")
    private fun Player.getStage(): Int = attr[DRAYNOR_AGILITY_STAGE] ?: 0
    private fun Player.setStage(v: Int) { attr[DRAYNOR_AGILITY_STAGE] = v }

    val DRAYNOR_LAPS = AttributeKey<Int>("DraynorAgilityLaps")
    private fun Player.getLaps(): Int = attr[DRAYNOR_LAPS] ?: 0
    private fun Player.setLaps(v: Int) { attr[DRAYNOR_LAPS] = v }


    override fun init() {

        //Rough Wall (Climb up)
        onObjectOption("objects.rooftops_draynor_wallclimb", "climb") {
            handleObstacle(
                player = player,
                destination = Tile(3103, 3279, 3),
                anim = "sequences.human_reachforladder",
                simpleMove = true,
                xp = 8.0,
                messageStart = "You climb up the rough wall...",
                messageEnd = "...and reach the top.",
                stage = 1
            )
        }

        //Tightrope 1
        onObjectOption("objects.rooftops_draynor_tightrope_1", "cross") {
            handleObstacle(
                player = player,
                destination = Tile(3090, 3277, 3),
                anim = "sequences.human_walk_logbalance_loop",
                duration1 = 5,
                duration2 = 250,
                angle = Direction.WEST.angle,
                xp = 8.0,
                messageStart = "You carefully cross the tightrope.",
                stage = 2
            )
        }

        //Tightrope 2
        onObjectOption("objects.rooftops_draynor_tightrope_2", "cross") {
            handleObstacle(
                player = player,
                destination = Tile(3092, 3267, 3),
                anim = "sequences.human_walk_logbalance_loop",
                duration1 = 5,
                duration2 = 250,
                angle = Direction.SOUTH.angle,
                xp = 8.0,
                messageStart = "You carefully cross the tightrope.",
                stage = 3
            )
        }

        //Narrow Wall (Balance)
        onObjectOption("objects.rooftops_draynor_wallcrossing", "balance") {
            handleObstacle(
                player = player,
                destination = Tile(3088, 3261, 3),
                anim = "sequences.human_walk_logbalance_loop",
                duration1 = 5,
                duration2 = 250,
                angle = Direction.SOUTH.angle,
                xp = 8.0,
                messageStart = "You balance across the narrow wall.",
                stage = 4
            )
        }

        //Wall (Jump)
        onObjectOption("objects.rooftops_draynor_wallscramble", "jump-up") {
            handleObstacle(
                player = player,
                destination = Tile(3088, 3254, 3),
                anim = "sequences.agility_shortcut_wall_jump",
                simpleMove = true,
                xp = 8.0,
                messageStart = "You jump across the wall.",
                stage = 5
            )
        }

        //Gap (Jump down)
        onObjectOption("objects.rooftops_draynor_leapdown", "jump") {
            handleObstacle(
                player = player,
                destination = Tile(3096, 3256, 3),
                anim = "sequences.agility_shortcut_wall_jumpdown",
                simpleMove = true,
                xp = 8.0,
                messageStart = "You jump down from the gap.",
                messageEnd = "You land safely on the ground.",
                stage = 6
            )
        }

        //Crate (Jump) — EndStage
        onObjectOption("objects.rooftops_draynor_crate", "climb-down") {
            handleObstacle(
                player = player,
                destination = Tile(3103, 3261, 0),
                anim = "sequences.human_jump_hurdle",
                simpleMove = true,
                xp = 8.0,
                messageStart = "You jump onto the crate.",
                messageEnd = "You complete the course!",
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

                player.filterableMessage("Your Draynor Rooftop Agility lap count is: <col=ff0000>$laps</col>.")

                GraceService.spawnMarkofGrace(player)
            }

            player.setStage(0)
            return

        }
    }

}