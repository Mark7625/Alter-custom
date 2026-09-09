package org.rsmod.content.quest.manager

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.player.midiJingle
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.table.QuestRow
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

val QUEST_STAGE_MAP_ATTR = AttributeKey<MutableMap<String, Int>>("quest_stages")

data class ItemRewardDisplay(val item: String, val zoom: Int = 10)

data class Quest(
    val id: Int,
    val key: String,
    val rowID : Int,
    val displayName: String,
    val mapElement: Int?,
    val startCoord: CoordGrid?,
    val maxSteps: Int,
    val questPoints: Int,
    val questVarp: String,
    val rewards : QuestReward,
    val itemDisplay : ItemRewardDisplay,
    /** Js5 archive 11 group played when the quest is completed; see [QuestScript.completionJingle]. */
    val completionJingle: Int = DEFAULT_COMPLETION_JINGLE,
) {

    private var Player.questState by intVarp(questVarp)
    private var Player.questPoints by intVarp("varp.qp")
    private var Player.questsCompleted by intVarBit("varbit.quests_completed_count")

    private val attributeRegistry = mutableMapOf<String, QuestAttribute<*>>()

    companion object {
        private val logger = InlineLogger()
        private val questsByKey = mutableMapOf<String, Quest>()

        fun get(key: String): Quest? = questsByKey[key.normalizedQuestKey()]

        fun getById(id: Int): Quest? = questsByKey.values.find { it.id == id }

        fun all(): Collection<Quest> = questsByKey.values

        /**
         * Js5 archive 11 groups of the three "Quest Complete" jingles. The client plays a jingle
         * by its archive group, which is not the number the `jingle.*` gamevals resolve to; the
         * groups are the "Cache ID" on each jingle's OSRS wiki page (see
         * [org.rsmod.api.player.midiJingle]).
         */
        /** "Quest Complete 1": usually master-level quests. */
        const val QUEST_COMPLETE_1_JINGLE = 152

        /** "Quest Complete 2": usually intermediate and expert quests. */
        const val QUEST_COMPLETE_2_JINGLE = 153

        /** "Quest Complete 3": usually beginner and easy quests. */
        const val QUEST_COMPLETE_3_JINGLE = 154

        const val DEFAULT_COMPLETION_JINGLE = QUEST_COMPLETE_3_JINGLE

        fun register(
            rowKey: String,
            varp: String,
            itemDisplay : ItemRewardDisplay,
            rewards : QuestReward,
            completionJingle: Int = DEFAULT_COMPLETION_JINGLE,
        ): Quest {

            val rowKeyID = "dbrow.${rowKey}".asRSCM()
            val questRow = QuestRow.getRow(rowKeyID)
            val quest = Quest(
                id = questRow.id,
                rowID = rowKeyID,
                key = rowKey,
                displayName = questRow.displayname,
                mapElement = questRow.mapelement,
                startCoord = questRow.startcoord,
                maxSteps = questRow.endstate,
                questPoints = questRow.questpoints,
                questVarp = varp,
                itemDisplay = itemDisplay,
                rewards = rewards,
                completionJingle = completionJingle,
            )
            questsByKey[rowKey.normalizedQuestKey()] = quest
            return quest
        }
    }

    fun getQuestStage(access: Player): Int {
        val stages = access.attr.getOrPut(QUEST_STAGE_MAP_ATTR) { mutableMapOf() }
        return stages[key] ?: 0
    }

    private fun setQuestStage(access: ProtectedAccess, stage: Int) {
        val clampedStage = stage.coerceIn(0, maxSteps)
        val stages = access.player.attr.getOrPut(QUEST_STAGE_MAP_ATTR) { mutableMapOf() }
        stages[key] = clampedStage
    }

    fun questState(player: Player): QuestProgressState =
        QuestProgressState.fromStage(getQuestStage(player), maxSteps)

    fun isQuestNotStarted(player: Player): Boolean = questState(player).isNotStarted

    fun isQuestInProgress(player: Player): Boolean = questState(player).isInProgress

    fun isQuestCompleted(player: Player): Boolean = questState(player).isCompleted

    fun advanceQuestStage(access: ProtectedAccess, amount: Int = 1): Int {
        val currentStage = getQuestStage(access.player)
        val attemptedStage = currentStage + amount

        if (attemptedStage > maxSteps) {
            val playerName = access.player.displayName.ifEmpty { "unknown" }
            logger.error {
                "Attempted to advance quest '$key' for player '$playerName' " +
                    "from stage $currentStage by $amount (max=$maxSteps)."
            }
            throw IllegalStateException("Quest '$key' cannot advance past stage $maxSteps.")
        }

        val newStage = attemptedStage.coerceIn(0, maxSteps)
        val wasCompleted = currentStage >= maxSteps
        setQuestStage(access, newStage)

        // Quest varps store the real stage (0..endstate). Multinpc / journal clients depend on
        // endstate (e.g. runemysteries=6) rather than a collapsed 0/1/2 progress flag.
        if (access.player.questState != newStage) {
            access.player.questState = newStage
        }

        if (!wasCompleted && newStage >= maxSteps) {
            completedQuest(access)
        }

        return newStage
    }

    /**
     * Puts the quest back to "not started" for [player]: stage, varp, every registered quest
     * attribute, and the quest points and completion count if it had been finished. Intended for
     * testing via the `::resetquest` command.
     */
    fun resetQuest(player: Player) {
        val wasCompleted = isQuestCompleted(player)
        player.attr[QUEST_STAGE_MAP_ATTR]?.remove(key)
        player.questState = 0
        for (attribute in attributeRegistry.values) {
            attribute.clear(player)
        }
        if (wasCompleted) {
            player.questPoints = (player.questPoints - questPoints).coerceAtLeast(0)
            player.questsCompleted = (player.questsCompleted - 1).coerceAtLeast(0)
        }
    }

    /**
     * Jumps the quest to [stage] for [player] without running completion rewards. Intended for
     * testing via the `::queststage` command; attributes are left untouched.
     */
    fun jumpToStage(player: Player, stage: Int) {
        val clamped = stage.coerceIn(0, maxSteps)
        val stages = player.attr.getOrPut(QUEST_STAGE_MAP_ATTR) { mutableMapOf() }
        stages[key] = clamped
        player.questState = clamped
    }

    fun <T> attribute(
        name: String,
        default: T,
        resetOnDeath: Boolean = false,
        temp: Boolean = false
    ): QuestAttribute<T> = attribute(name, { default }, resetOnDeath, temp)

    fun <T> attribute(
        name: String,
        default: () -> T,
        resetOnDeath: Boolean = false,
        temp: Boolean = false
    ): QuestAttribute<T> {
        @Suppress("UNCHECKED_CAST")
        return attributeRegistry.getOrPut(name) {
            QuestAttribute(
                name = name,
                attributeKey = AttributeKey(
                    persistenceKey = "quest.$key.$name",
                    resetOnDeath = resetOnDeath,
                    temp = temp
                ),
                defaultProvider = default
            )
        } as QuestAttribute<T>
    }

    private fun completedQuest(access: ProtectedAccess) {

        access.player.questPoints += questPoints
        access.player.questsCompleted++
        access.player.midiJingle(completionJingle)

        access.ifOpenMain("interface.questscroll")
        access.ifSetText("component.questscroll:quest_title", "You have completed ${displayName}!")
        access.ifSetText("component.questscroll:quest_reward1", "$questPoints Quest Point")

        access.ifSetObj("component.questscroll:quest_model", obj = itemDisplay.item, zoom = itemDisplay.zoom)

        val rewardLines = mutableListOf<String>()

        rewards.xp.forEach { (skill, amount) ->
            val stat = ServerCacheManager.getStats(skill.asRSCM(RSCMType.STAT))
                ?: error("No stat found for $skill")

            access.statAdvance(skill,amount)
            rewardLines.add("${amount.toInt()} ${stat.displayName} XP")
        }

        rewards.items.forEach { (item, amount) ->
            access.invAdd(access.inv,item,amount)
            val type = ServerCacheManager.getItem(item.asRSCM(RSCMType.OBJ))?: error("No item found for $item")
            rewardLines.add("$amount x ${type.name}")
        }

        rewards.extraText?.let {
            rewardLines.add(it)
        }

        val linesToShow = rewardLines.take(6)

        for (i in 0 until 6) {
            val componentId = "component.questscroll:quest_reward${i + 2}"
            val text = linesToShow.getOrNull(i) ?: ""
            access.ifSetText(componentId, text)
        }

    }

}
