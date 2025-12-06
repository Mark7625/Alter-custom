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

class BarbianOutpostCoursePlugin : PluginEvent() {

    private val MAX_STAGES = 8
    private val BONUS_XP = 46.3
    private val STRENGTH_XP = 41.3

    private val DROP_CHANCE = 1.0 / 5.0   // 20%

    private val FAIL_ROPE = 1.0 / 1.0
    private val FAIL_LOG = 1.0 / 10.0
    private val FAIL_LEDGE = 1.0 / 7.0

    private val MARK_SPAWN_TILES = listOf(
        Tile(2504, 3545, 1),
        Tile(2533, 3555, 0)
    )
    private val GraceService = MarkOfGraceService(
        spawnTiles = MARK_SPAWN_TILES,
        dropChance = DROP_CHANCE
    )

    val BARBARIAN_AGILITY_STAGE = AttributeKey<Int>("BarbarianAgilityStage")
    private fun Player.getStage(): Int = attr[BARBARIAN_AGILITY_STAGE] ?: 0
    private fun Player.setStage(v: Int) { attr[BARBARIAN_AGILITY_STAGE] = v }

    val BARBARIAN_LAPS = AttributeKey<Int>("BarbarianAgilityLaps")
    private fun Player.getLaps(): Int = attr[BARBARIAN_LAPS] ?: 0
    private fun Player.setLaps(v: Int) { attr[BARBARIAN_LAPS] = v }

    override fun init() {

        onObjectOption("objects.obstical_ropeswing1", "Swing-on") {
            handleObstacle(
                player = player,
                destination = Tile(2551, 3549, 0),
                anim = "sequences.human_ropeswing_long",
                duration1 = 5,
                duration2 = 250,
                angle = Direction.SOUTH.angle,
                xp = 22.0,
                messageStart = "You grab the rope...",
                messageEnd = "... and land safely.",
                stage = 1,

                failChance = FAIL_ROPE,
                onFail = { p ->
                    p.filterableMessage("You lose your grip and fall the pit below!")
                    p.animate("sequences.human_ropeswing_long_miss")
                    p.moveTo(Tile(2552, 9950, 0))
                    p.animate(RSCM.NONE)
                    player.damageMap.add(player, 2)
                }
            )
        }

        onObjectOption("objects.barbarian_log_balance1", "walk-across") {
            handleObstacle(
                player = player,
                destination = Tile(2541, 3546, 0),
                anim = "sequences.human_walk_logbalance_loop",
                duration1 = 5,
                duration2 = 250,
                angle = Direction.WEST.angle,
                xp = 13.7,
                messageStart = "You walk carefully across the slippery log...",
                messageEnd = "... and make it safely to the other side.",
                stage = 2,

                failChance = FAIL_LOG,
                onFail = { p ->
                    p.filterableMessage("You lose your footing and fall off the log!")
                    p.animate("sequences.human_walk_logbalance_stumble")
                    p.animate("sequences.human_drowning")
                    p.moveTo(Tile(2545, 3546, 0))
                    player.damageMap.add(player, 3)
                }
            )
        }

        onObjectOption("objects.agility_obstical_net_barbarian", "climb-over") {
            if (player.tile.z !in 3545..3546) return@onObjectOption
            handleObstacle(
                player = player,
                destination = Tile(2537, player.tile.z, 1),
                anim = "sequences.human_reachforladder",
                simpleMove = true,
                xp = 8.2,
                messageStart = "You climb up the netting...",
                stage = 3
            )
        }

        onObjectOption("objects.balancing_ledge1", "Walk-across") {
            handleObstacle(
                player = player,
                destination = Tile(2532, 3547, 1),
                anim = "sequences.human_walk_sidestepl",
                duration1 = 5,
                duration2 = 250,
                angle = Direction.WEST.angle,
                xp = 22.0,
                messageStart = "You carefully edge across the ledge...",
                stage = 4,

                failChance = FAIL_LEDGE,
                onFail = { p ->
                    p.filterableMessage("You lose your balance and fall!")
                    p.animate("sequences.human_sidestep_fall")
                    p.moveTo(Tile(2534, 3546, 0))
                    player.damageMap.add(player, 2)
                }
            )
        }

        onObjectOption("objects.barbarian_laddertop_norim", "Climb-down") {
            handleObstacle(
                player = player,
                destination = Tile(2532, 3546, 0),
                anim = "sequences.human_reachforladder",
                simpleMove = true,
                messageStart = "You climb down the ladder...",
                stage = 5
            )
        }

        onObjectOption("objects.castlecrumbly1", "climb-over") {

            when (player.tile.x) {

                2535, 2536 ->
                    handleObstacle(
                        player = player,
                        destination = Tile(2537, 3553, 0),
                        anim = "sequences.human_walk_crumbledwall",
                        simpleMove = true,
                        xp = 13.7,
                        messageStart = "You climb over the crumbling wall...",
                        stage = 6,
                    )

                2538, 2539 ->
                    handleObstacle(
                        player = player,
                        destination = Tile(2540, 3553, 0),
                        anim = "sequences.human_walk_crumbledwall",
                        simpleMove = true,
                        xp = 13.7,
                        messageStart = "You climb over the crumbling wall...",
                        stage = 7,
                    )

                2541, 2542 ->
                    handleObstacle(
                        player = player,
                        destination = Tile(2543, 3553, 0),
                        anim = "sequences.human_walk_crumbledwall",
                        simpleMove = true,
                        xp = 13.7,
                        messageStart = "You climb over the crumbling wall...",
                        stage = 8,
                        endStage = true,
                    )
            }
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
        endStage: Boolean = false,

        failChance: Double = 0.0,
        onFail: ((Player) -> Unit)? = null
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

            if (failChance > 0 && Math.random() < failChance) {
                player.setStage(0)
                onFail?.invoke(player)
                return@queue
            }

            messageStart?.let { player.filterableMessage(it) }
            anim?.let { player.animate(it) }

            if (doForcedMove) {
                val movement = ForcedMovement.of(
                    src = player.tile,
                    dst = destination,
                    clientDuration1 = duration1,
                    clientDuration2 = duration2,
                    directionAngle = angle
                )
                player.forceMove(this, movement)
                wait(1)
                player.animate(RSCM.NONE)

            } else if (simpleMove) {
                wait(2)
                player.moveTo(destination)
                wait(1)
                player.animate(RSCM.NONE)
            }

            wait(1)

            if (xp > 0.0)
                player.addXp(Skills.AGILITY, xp)

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
                player.addXp(Skills.STRENGTH, STRENGTH_XP)

                val laps = player.getLaps() + 1
                player.setLaps(laps)

                player.filterableMessage("Your Gnome Stronghold Agility lap count is: <col=ff0000>$laps</col>.")

                GraceService.spawnMarkofGrace(player)
            }

            player.setStage(0)
        }
    }
}
