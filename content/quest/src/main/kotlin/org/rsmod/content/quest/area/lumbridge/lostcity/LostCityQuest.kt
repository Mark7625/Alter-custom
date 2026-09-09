package org.rsmod.content.quest.area.lumbridge.lostcity

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.righthand
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Lost City.
 *
 * Stages (stored in `varp.zanaris`, endstate from `dbrow.quest_lostcity`):
 * - [STAGE_STARTED]: the Warrior at the swamp camp let slip that a leprechaun in a nearby tree
 *   knows the way to Zanaris.
 * - [STAGE_MET_SHAMUS]: Shamus explained the shed portal and the Dramen staff.
 * - [STAGE_SPIRIT_DEFEATED]: the Tree spirit guarding the Dramen tree has been beaten.
 * - Complete: the player entered Zanaris through the shed while wielding a Dramen staff.
 *
 * The quest varp has no varbits sharing it in this cache revision, so the stage is the only
 * thing stored there; everything else lives in quest attributes.
 */
@Singleton
class LostCityQuest : QuestScript(
    "quest_lostcity",
    "varp.zanaris",
    rewards { extra("Access to Zanaris") },
    ItemRewardDisplay(DRAMEN_STAFF),
) {
    /** The Warrior has already been thanked once after the quest. */
    val thankedWarrior = quest.attribute(name = "THANKED_WARRIOR", default = false)

    /**
     * The Tree spirit has been beaten this login session. It is not persisted on purpose: the
     * spirit returns to guard the tree the next time the player comes back to the dungeon, as it
     * does when it despawns after being ignored for too long.
     */
    val spiritDefeated = quest.attribute(name = "SPIRIT_DEFEATED", default = false, temp = true)

    /** The Port Sarim monks have searched the player once this session. */
    val searchedByMonks = quest.attribute(name = "SEARCHED_BY_MONKS", default = false, temp = true)

    private val staffIds = FAIRY_STAFFS.map { it.asRSCM(RSCMType.OBJ) }.toSet()

    override fun ScriptContext.init() {}

    override fun subTitle(): String =
        "talking to the <col=800000>Warrior</col> at the adventurers' camp on the north-west " +
            "edge of <col=800000>Lumbridge Swamp</col>, south of Draynor Village."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            objective(
                "A group of adventurers have made camp in <red>Lumbridge Swamp</red>. The " +
                    "<red>Warrior</red> let slip that they are searching for Zanaris, a " +
                    "hidden city, and that a <red>leprechaun</red> hiding in one of the trees " +
                    "near the camp knows the way there.",
            ) {}

            objective(
                "I should look for the tree the leprechaun is hiding in and <red>chop</red> it " +
                    "to get him to come out.",
            ) {
                visibleWhen { quest.getQuestStage(access.player) == STAGE_STARTED }
            }

            objective(
                "<red>Shamus</red> the leprechaun told me the doorway of the <red>shed</red> " +
                    "in the middle of the swamp is a portal to Zanaris, but only if I am " +
                    "carrying a <red>Dramen staff</red>.",
            ) {
                visibleWhen { quest.getQuestStage(access.player) >= STAGE_MET_SHAMUS }
            }

            objective(
                "Dramen staffs are crafted from branches of the <red>Dramen tree</red>, which " +
                    "grows in a cave on the island of <red>Entrana</red>. The monks run a ship " +
                    "there from <red>Port Sarim</red>, but they will not let me bring any " +
                    "weapons or armour.",
            ) {
                visibleWhen { quest.getQuestStage(access.player) == STAGE_MET_SHAMUS }
            }

            objective(
                "I defeated the <red>Tree spirit</red> guarding the Dramen tree. I can now cut " +
                    "a <red>Dramen branch</red> from it and use a <red>knife</red> on the " +
                    "branch to carve a staff.",
            ) {
                visibleWhen { quest.getQuestStage(access.player) == STAGE_SPIRIT_DEFEATED }
                hasItem("dramen_branch", "I have a Dramen branch.").strike()
                hasItem("dramen_staff", "I have made a Dramen staff.").strike()
            }

            objective(
                "I should wield the <red>Dramen staff</red> and enter the <red>shed</red> in " +
                    "the middle of Lumbridge Swamp.",
            ) {
                visibleWhen {
                    quest.getQuestStage(access.player) == STAGE_SPIRIT_DEFEATED &&
                        (access.inv.contains(DRAMEN_STAFF) || wieldsDramenStaff(access.player))
                }
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "A group of adventurers camped in Lumbridge Swamp were searching for the lost " +
                    "city of Zanaris. Their Warrior let slip that a leprechaun hiding in a " +
                    "nearby tree knew the way.",
            )
            line(
                "Shamus the leprechaun told me the shed in the swamp is a portal to Zanaris " +
                    "for anyone carrying a Dramen staff. I sailed to Entrana, fought the " +
                    "Tree spirit guarding the Dramen tree in the caves beneath the island, " +
                    "cut a branch and carved it into a staff.",
            )
            line("With the staff in hand I stepped through the shed door into Zanaris.")
        }

    fun stage(player: Player): Int = quest.getQuestStage(player)

    /** A Dramen or Lunar staff is in the player's weapon slot. */
    fun wieldsDramenStaff(player: Player): Boolean {
        val weapon = player.righthand ?: return false
        return weapon.id in staffIds
    }

    /** Finishes the quest for a player who has just entered Zanaris. */
    fun complete(access: ProtectedAccess) {
        val remaining = quest.maxSteps - stage(access.player)
        if (remaining > 0) {
            quest.advanceQuestStage(access, remaining)
        }
    }

    companion object {
        const val STAGE_STARTED = 1
        const val STAGE_MET_SHAMUS = 2
        const val STAGE_SPIRIT_DEFEATED = 3

        const val CRAFTING_REQ = 31
        const val WOODCUTTING_REQ = 36
        const val RECOMMENDED_COMBAT = 45

        const val DRAMEN_BRANCH = "obj.dramen_branch"
        const val DRAMEN_STAFF = "obj.dramen_staff"

        /** Staffs that open the way to Zanaris. */
        val FAIRY_STAFFS = listOf(DRAMEN_STAFF, "obj.lunar_moonclan_liminal_staff")
    }
}
