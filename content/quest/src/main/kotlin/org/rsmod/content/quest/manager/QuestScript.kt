package org.rsmod.content.quest.manager

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

enum class JournalState { OVERVIEW, LOG }

enum class QuestProgressState {
    NOT_STARTED,
    IN_PROGRESS,
    FINISHED;

    val isNotStarted: Boolean
        get() = this == NOT_STARTED

    val isInProgress: Boolean
        get() = this == IN_PROGRESS

    val isCompleted: Boolean
        get() = this == FINISHED

    companion object {
        fun fromStage(stage: Int, endState: Int): QuestProgressState =
            when {
                stage <= 0 -> NOT_STARTED
                stage >= endState -> FINISHED
                else -> IN_PROGRESS
            }
    }
}

@DslMarker
annotation class QuestJournalDsl

data class QuestReward(
    val xp: Map<String, Double> = emptyMap(),
    val items: List<Pair<String, Int>> = emptyList(),
    val extraText: String? = null
)

@QuestJournalDsl
fun rewards(builder: QuestRewardBuilder.() -> Unit): QuestReward {
    return QuestRewardBuilder().apply(builder).build()
}

@QuestJournalDsl
class QuestRewardBuilder {
    private val _xp = mutableMapOf<String, Double>()
    private val _items = mutableListOf<Pair<String, Int>>()
    private var _extraText: String? = null

    fun xp(skill: String, amount: Double) {
        _xp[skill] = amount
    }

    fun item(id: String, amount: Int = 1) {
        _items.add(id to amount)
    }

    fun extra(text: String) {
        _extraText = text
    }

    fun build(): QuestReward = QuestReward(_xp, _items, _extraText)
}


abstract class QuestScript(
    val questKey: String,
    val questVarp : String,
    val rewards: QuestReward,
    val completedQuestItemDisplay: ItemRewardDisplay,
    /** Jingle played alongside the completion scroll; quests with their own variant override it. */
    val completionJingle: String = Quest.DEFAULT_COMPLETION_JINGLE,
) : PluginScript() {

    private var Player.questState by intVarp(questVarp)

    val quest = Quest.register(questKey, questVarp, completedQuestItemDisplay, rewards, completionJingle)

    abstract fun subTitle(): String

    abstract fun questLog(player: ProtectedAccess): String

    abstract fun completedLog(player: ProtectedAccess): String

    abstract fun ScriptContext.init()


    override fun ScriptContext.startup() {
        RSCM.requireRSCM(RSCMType.DBROW, "dbrow.${questKey}")

        QuestJournalRegistry.register(
            quest,
            QuestJournalContent(
                subTitle = ::subTitle,
                questLog = ::questLog,
                completedLog = ::completedLog,
            ),
        )

        onPlayerLogin {
            player.questState = quest.getQuestStage(player)
        }

        this.init()
    }

    protected fun questJournal(
        player: ProtectedAccess,
        builder: QuestJournalBuilder.() -> Unit
    ): String = buildQuestJournal(player, quest, builder)

    protected fun completionJournal(
        player: ProtectedAccess,
        builder: QuestJournalBuilder.() -> Unit
    ): String = buildCompletionJournal(player, quest, builder)
}

