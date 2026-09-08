package org.rsmod.content.quest.area.lumbridge.restlessghost

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Singleton
import org.rsmod.api.player.front
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Restless Ghost.
 *
 * Stages (stored in `varp.prieststart`, endstate 5 from `dbrow.quest_restlessghost`):
 * - [STAGE_STARTED]: Father Aereck asked for the graveyard ghost to be dealt with.
 * - [STAGE_GOT_AMULET]: Father Urhney handed over the Amulet of Ghostspeak.
 * - [STAGE_SPOKE_TO_GHOST]: the ghost explained that a warlock stole his skull.
 * - [STAGE_GOT_SKULL]: the skull was taken from the altar in the Wizards' Tower basement.
 * - [STAGE_COMPLETE]: the skull is back in the coffin and the ghost has been released.
 *
 * The coffin, altar and basement skeleton are cache multilocs driven by varbits on
 * `varp.restless_ghost_control`, a separate varp from the quest stage, so those varbits can be
 * used as-is.
 */
@Singleton
class RestlessGhostQuest : QuestScript(
    "quest_restlessghost",
    "varp.prieststart",
    rewards {
        xp("stat.prayer", 1125.0)
        extra("Amulet of Ghostspeak")
    },
    ItemRewardDisplay(GHOSTSPEAK_AMULET),
) {
    override fun ScriptContext.init() {}

    override fun subTitle(): String =
        "talking to <col=800000>Father Aereck</col> in the <col=800000>Lumbridge church</col>, " +
            "south-east of the castle."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            objective(
                "<red>Father Aereck</red> wants me to get rid of a <red>ghost</red> haunting the " +
                    "graveyard beside the Lumbridge church.",
            ) {}

            objective(
                "His friend <red>Father Urhney</red>, who lives as a hermit in a shack in the far " +
                    "west of the Lumbridge swamp, is an expert on ghosts and may be able to help.",
            ) {
                visibleWhen { quest.getQuestStage(access.player) < STAGE_GOT_AMULET }
            }

            objective(
                "Father Urhney gave me an <red>Amulet of Ghostspeak</red>. While wearing it I can " +
                    "talk to the ghost, whose coffin is in the small building in the south-east " +
                    "corner of the graveyard.",
            ) {
                visibleWhen { quest.getQuestStage(access.player) == STAGE_GOT_AMULET }
                custom(
                    !access.hasAmuletAnywhere(),
                    "I have lost the Amulet of Ghostspeak. Father Urhney may have a spare.",
                )
            }

            objective(
                "The ghost cannot rest because a <red>warlock</red> stole his <red>skull</red>. He " +
                    "thinks it is somewhere in the <red>Wizards' Tower</red>, on the island south " +
                    "of Draynor Village. I should search the tower for it.",
            ) {
                visibleWhen { quest.getQuestStage(access.player) == STAGE_SPOKE_TO_GHOST }
            }

            objective(
                "I found the ghost's skull on an altar in the Wizards' Tower basement. I should " +
                    "open the ghost's <red>coffin</red> in the Lumbridge graveyard and put the " +
                    "<red>skull</red> inside it.",
            ) {
                visibleWhen { quest.getQuestStage(access.player) == STAGE_GOT_SKULL }
                custom(
                    !access.hasSkullAnywhere(),
                    "I have lost the ghost's skull. I should search the altar in the Wizards' " +
                        "Tower basement again.",
                )
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "Father Aereck asked me to get rid of a ghost haunting the Lumbridge graveyard. " +
                    "Father Urhney, a hermit in the swamp, gave me an Amulet of Ghostspeak so I " +
                    "could talk to it.",
            )
            line(
                "The ghost told me a warlock had stolen his skull. I found it on an altar in the " +
                    "basement of the Wizards' Tower, where a skeleton rose to stop me.",
            )
            line(
                "I put the skull back in his coffin and the ghost was finally able to rest in " +
                    "peace. Father Urhney let me keep the amulet.",
            )
        }

    fun stage(player: Player): Int = quest.getQuestStage(player)

    /** Either ghostspeak amulet worn in the neck slot. */
    fun isWearingAmulet(player: Player): Boolean = player.front?.id in AMULET_IDS

    fun ProtectedAccess.hasAmuletAnywhere(): Boolean =
        isWearingAmulet(player) ||
            AMULETS.any { inv.count(it) > 0 || bank.count(it) > 0 }

    fun ProtectedAccess.carriesAmulet(): Boolean =
        isWearingAmulet(player) || AMULETS.any { inv.count(it) > 0 }

    fun ProtectedAccess.hasSkullAnywhere(): Boolean =
        inv.count(GHOST_SKULL) > 0 || bank.count(GHOST_SKULL) > 0

    companion object {
        const val STAGE_STARTED = 1
        const val STAGE_GOT_AMULET = 2
        const val STAGE_SPOKE_TO_GHOST = 3
        const val STAGE_GOT_SKULL = 4
        const val STAGE_COMPLETE = 5

        const val GHOSTSPEAK_AMULET = "obj.amulet_of_ghostspeak"
        const val GHOSTSPEAK_AMULET_ENCHANTED = "obj.amulet_of_ghostspeak_enchanted"
        const val GHOST_SKULL = "obj.ghostskull"

        val AMULETS = listOf(GHOSTSPEAK_AMULET, GHOSTSPEAK_AMULET_ENCHANTED)
        val AMULET_IDS: Set<Int> by lazy { AMULETS.map { it.asRSCM(RSCMType.OBJ) }.toSet() }
    }
}

/*
 * Mirrors of the cache multilocs on `varp.restless_ghost_control`. These are storage, not
 * mirrors: the quest manager never touches that varp.
 */

/** The skeleton in the corner of the Wizards' Tower basement has risen. */
var Player.rgSkeletonRisen by boolVarBit("varbit.restless_ghost_skeleton_var")

/** The ghost's coffin shows a complete skeleton (skull returned). */
var Player.rgCoffinHasHead by boolVarBit("varbit.restless_ghost_coffin_var")

/** The basement altar no longer holds the skull. */
var Player.rgAltarEmpty by boolVarBit("varbit.restless_ghost_altar_var")
