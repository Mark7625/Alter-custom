package org.rsmod.content.quest.area.varrock.demonslayer

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.righthand
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Demon Slayer.
 *
 * Stage layout (stored in `varp.demonstart`, endstate 3 from `dbrow.quest_demonslayer`):
 * - 0: not started
 * - [STAGE_STARTED]: Aris has shown the vision; the three keys must be collected.
 * - [STAGE_SILVERLIGHT]: Sir Prysin handed over Silverlight; Delrith must be banished.
 * - [STAGE_COMPLETE]: Delrith banished.
 *
 * Every finer detail (incantation order, which keys were handed out, drain key, bones given to
 * Traiborn, summoning cutscene seen) lives in persistent quest attributes. The cache varbits for
 * those details share `varp.demonstart` with the stage, and the quest manager writes the whole
 * varp when it syncs the stage, so the varbits are only mirrors and are re-applied from the
 * attributes by [syncVars] whenever they might have been wiped.
 */
@Singleton
class DemonSlayerQuest : QuestScript(
    "quest_demonslayer",
    "varp.demonstart",
    rewards { extra("Silverlight") },
    ItemRewardDisplay(SILVERLIGHT),
) {
    /** Five digits, each the index into [WORDS]; empty until Aris reveals the incantation. */
    val incantation = quest.attribute(name = "INCANTATION", default = "")

    /** 0 = key still in the drain, 1 = washed into the sewer, 2 = picked up. */
    val drainKey = quest.attribute(name = "DRAIN_KEY", default = 0)

    /** Sir Prysin has explained the three keys, so later visits skip the introduction. */
    val prysinExplained = quest.attribute(name = "PRYSIN_EXPLAINED", default = false)

    val rovinKeyGiven = quest.attribute(name = "ROVIN_KEY_GIVEN", default = false)
    val traibornAsked = quest.attribute(name = "TRAIBORN_ASKED", default = false)
    val traibornKeyGiven = quest.attribute(name = "TRAIBORN_KEY_GIVEN", default = false)
    val bonesGiven = quest.attribute(name = "BONES_GIVEN", default = 0)
    val silverlightGiven = quest.attribute(name = "SILVERLIGHT_GIVEN", default = false)
    val seenSummoning = quest.attribute(name = "SEEN_SUMMONING", default = false)

    override fun ScriptContext.init() {
        // Registered after the quest manager's own login hook, which writes the whole varp and
        // therefore wipes the mirrored varbits.
        onPlayerLogin { syncVars(player) }
    }

    override fun subTitle(): String =
        "talking to <col=800000>Aris</col> in her tent on the west side of " +
            "<col=800000>Varrock Square</col>."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            objective(
                "Aris the fortune teller showed me a vision of <red>Delrith</red>, a demon that " +
                    "once tried to destroy Varrock. She says I am destined to stop him, using " +
                    "the sword <red>Silverlight</red> and the incantation the hero Wally used.",
            ) {}

            objective(
                "Silverlight is kept by <red>Sir Prysin</red> in Varrock Palace, locked in a " +
                    "case that needs three keys.",
            ) {
                visibleWhen { quest.getQuestStage(access.player) == STAGE_STARTED }
            }

            objective(
                "<red>Captain Rovin</red>, at the top of the north-west tower of the palace, " +
                    "holds one key.",
            ) {
                visibleWhen { quest.getQuestStage(access.player) == STAGE_STARTED }
                attribute(rovinKeyGiven, "I have Captain Rovin's key.").strike()
            }

            objective(
                "Sir Prysin dropped his key down the <red>drain</red> outside the palace " +
                    "kitchen. Pouring water down it should wash the key into the sewers.",
            ) {
                visibleWhen { quest.getQuestStage(access.player) == STAGE_STARTED }
                custom(drainKey.get(access.player) == 1, "I washed the key into the sewers. I should climb down the manhole east of the palace and look for it.")
                custom(drainKey.get(access.player) >= 2, "I have the key that was dropped down the drain.").strike()
            }

            objective(
                "<red>Wizard Traiborn</red>, on the first floor of the Wizards' Tower, holds " +
                    "the last key. He needs <red>25 sets of bones</red> for the ritual that " +
                    "opens his wardrobe.",
            ) {
                visibleWhen { quest.getQuestStage(access.player) == STAGE_STARTED }
                custom(
                    traibornAsked.get(access.player) && !traibornKeyGiven.get(access.player),
                    "I have given Traiborn ${bonesGiven.get(access.player)} of the 25 sets of bones he needs.",
                )
                attribute(traibornKeyGiven, "I have Wizard Traiborn's key.").strike()
            }

            objective(
                "Sir Prysin gave me <red>Silverlight</red>. Delrith is being summoned at the " +
                    "<red>stone circle</red> south of Varrock. I must weaken him with " +
                    "Silverlight and banish him with the incantation.",
            ) {
                visibleWhen { quest.getQuestStage(access.player) >= STAGE_SILVERLIGHT }
                custom(
                    !access.hasSilverlight(),
                    "I have lost Silverlight. Sir Prysin may be able to help me find it.",
                )
            }

            objective("If I forget the incantation, Aris can remind me of it.") {
                visibleWhen { quest.getQuestStage(access.player) >= STAGE_STARTED }
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "Aris the fortune teller saw the demon Delrith in her crystal ball and told me I " +
                    "was destined to stop him, just as the hero Wally did long ago.",
            )
            line(
                "I gathered the three keys to Silverlight's case from Captain Rovin, Wizard " +
                    "Traiborn and the palace drain, and Sir Prysin handed me the sword.",
            )
            line(
                "At the stone circle south of Varrock I struck down the newly summoned Delrith " +
                    "with Silverlight and banished him with the incantation.",
            )
        }

    /** The incantation for [player], generating and storing a random order on first use. */
    fun incantationOrder(player: Player): List<Int> {
        val stored = incantation.get(player)
        if (stored.length == WORDS.size && stored.all { it.isDigit() }) {
            return stored.map { it.digitToInt() }
        }
        val order = WORDS.indices.shuffled()
        incantation.set(player, order.joinToString(""))
        syncVars(player)
        return order
    }

    fun incantationWords(player: Player): List<String> = incantationOrder(player).map { WORDS[it] }

    /** The spoken form, e.g. `Aber... Carlem... Gabindo... Purchai... Camerinthum!`. */
    fun incantationSpoken(player: Player): String {
        val words = incantationWords(player)
        return words.dropLast(1).joinToString(" ") { "$it..." } + " " + words.last() + "!"
    }

    fun advance(access: ProtectedAccess) {
        quest.advanceQuestStage(access)
        syncVars(access.player)
    }

    /** Re-applies the client-facing varbits from the persisted attributes. */
    fun syncVars(player: Player) {
        val order = incantation.get(player)
        player.dsIncantation1 = order.getOrNull(0)?.digitToIntOrNull()?.plus(1) ?: 0
        player.dsIncantation2 = order.getOrNull(1)?.digitToIntOrNull()?.plus(1) ?: 0
        player.dsIncantation3 = order.getOrNull(2)?.digitToIntOrNull()?.plus(1) ?: 0
        player.dsIncantation4 = order.getOrNull(3)?.digitToIntOrNull()?.plus(1) ?: 0
        player.dsIncantation5 = order.getOrNull(4)?.digitToIntOrNull()?.plus(1) ?: 0
        player.dsDrainKey = drainKey.get(player)
        player.dsSilverlightCase = silverlightGiven.get(player)
        player.dsSeenSummoning = seenSummoning.get(player)
    }

    fun hasKey(player: Player, key: String): Boolean = player.inv.count(key) > 0

    fun hasAllKeys(player: Player): Boolean = KEYS.all { hasKey(player, it) }

    fun keysHeld(player: Player): List<String> = KEYS.filter { hasKey(player, it) }

    fun isWieldingSilverlight(player: Player): Boolean = player.righthand?.id == SILVERLIGHT_ID

    /** Silverlight wielded, carried or banked. */
    fun ProtectedAccess.hasSilverlight(): Boolean =
        isWieldingSilverlight(player) || inv.count(SILVERLIGHT) > 0 || bank.count(SILVERLIGHT) > 0

    fun ProtectedAccess.carriesSilverlight(): Boolean =
        isWieldingSilverlight(player) || inv.count(SILVERLIGHT) > 0

    companion object {
        const val STAGE_STARTED = 1
        const val STAGE_SILVERLIGHT = 2
        const val STAGE_COMPLETE = 3

        const val SILVERLIGHT = "obj.silverlight"
        val SILVERLIGHT_ID: Int = SILVERLIGHT.asRSCM(RSCMType.OBJ)

        /** Wizard Traiborn's key. */
        const val KEY_TRAIBORN = "obj.silverlight_key_1"

        /** Captain Rovin's key. */
        const val KEY_ROVIN = "obj.silverlight_key_2"

        /** The key Sir Prysin dropped down the drain. */
        const val KEY_DRAIN = "obj.silverlight_key_3"

        val KEYS = listOf(KEY_ROVIN, KEY_DRAIN, KEY_TRAIBORN)

        const val BONES_REQUIRED = 25
        const val SILVERLIGHT_REPLACEMENT_COST = 500

        /** The incantation words, in the fixed order the banish prompt offers them. */
        val WORDS = listOf("Carlem", "Aber", "Camerinthum", "Purchai", "Gabindo")
    }
}
