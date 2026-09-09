package org.rsmod.content.quest.manager

import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.player.output.mes
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onCommand
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Testing commands for quests.
 *
 * `::resetquest <key>` puts a quest back to "not started" (stage, varp, quest attributes, and the
 * quest points if it was finished). The key is the dbrow name with or without the `quest_`
 * prefix, e.g. `::resetquest demonslayer`. `::resetquest` with no key lists the registered quests.
 *
 * `::testloc <loc> <angle>` raises a loc on the tile east of the player for a few seconds with the
 * given rotation (0-3), to check which way a model faces at each rotation.
 */
class QuestCommands
@Inject
constructor(
    private val locRepo: LocRepository,
    private val worldRepo: WorldRepository,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onCommand("resetquest") {
            desc = "Reset a quest to not started (ex: ::resetquest demonslayer)"
            invalidArgs = "Use as ::resetquest <quest key>"
            requiredRights = Rights.ADMINISTRATOR
            cheat {
                val key = args.firstOrNull()?.trim()?.lowercase()
                if (key.isNullOrEmpty()) {
                    val names = Quest.all().joinToString(", ") { it.key.removePrefix("quest_") }
                    player.mes("Registered quests: $names")
                    return@cheat
                }
                val quest = Quest.get(key) ?: Quest.get("quest_$key")
                if (quest == null) {
                    player.mes("No quest registered under '$key'.")
                    return@cheat
                }
                quest.resetQuest(player)
                player.mes("Reset ${quest.displayName} to not started.")
            }
        }

        onCommand("queststage") {
            desc = "Jump a quest to a stage without rewards (ex: ::queststage demonslayer 2)"
            invalidArgs = "Use as ::queststage <quest key> <stage>"
            requiredRights = Rights.ADMINISTRATOR
            cheat {
                val key = args[0].trim().lowercase()
                val stage = args[1].toInt()
                val quest = Quest.get(key) ?: Quest.get("quest_$key")
                if (quest == null) {
                    player.mes("No quest registered under '$key'.")
                    return@cheat
                }
                quest.jumpToStage(player, stage)
                player.mes("${quest.displayName} is now at stage ${quest.getQuestStage(player)} of ${quest.maxSteps}.")
            }
        }

        onCommand("testloc") {
            desc = "Spawn a loc east of you with a rotation (ex: ::testloc qip_ds_wizards_key_wardrobe_magic 0)"
            invalidArgs = "Use as ::testloc <loc name> <angle 0-3>"
            requiredRights = Rights.ADMINISTRATOR
            cheat {
                val name = args[0].removePrefix("loc.")
                val angleId = args.getOrNull(1)?.toIntOrNull() ?: 0
                val angle = LocAngle.entries.firstOrNull { it.id == angleId }
                if (angle == null) {
                    player.mes("Angle must be 0-3.")
                    return@cheat
                }
                val tile = player.coords.translate(1, 0)
                val loc = locRepo.add(tile, "loc.$name", TEST_LOC_TICKS, angle, LocShape.CentrepieceStraight)
                worldRepo.locAnim(loc, "seq.qip_ds_wardrobe_appear")
                player.mes("Spawned loc.$name east of you at rotation $angleId (${angle.name}) for $TEST_LOC_TICKS ticks.")
            }
        }
    }

    private companion object {
        const val TEST_LOC_TICKS = 25
    }
}
