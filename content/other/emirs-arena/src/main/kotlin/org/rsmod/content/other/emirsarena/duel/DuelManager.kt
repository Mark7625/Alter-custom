package org.rsmod.content.other.emirsarena.duel

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.util.Wearpos
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.combat.manager.PlayerAttackManager
import org.rsmod.api.config.constants
import org.rsmod.api.mechanics.toxins.Toxin.cureAllToxins
import org.rsmod.api.player.disablePrayers
import org.rsmod.api.player.ironman.IronmanRestrictions
import org.rsmod.api.player.midiJingle
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.output.GameMessage
import org.rsmod.api.player.output.MiscOutput
import org.rsmod.api.player.output.UpdateRun
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.stat.statRestoreAll
import org.rsmod.api.player.ui.ifClose
import org.rsmod.api.player.ui.ifOpenMainModal
import org.rsmod.api.player.ui.ifSetObj
import org.rsmod.api.player.ui.ifSetPosition
import org.rsmod.api.player.ui.ifSetText
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.worn.WornUnequipOp
import org.rsmod.api.player.worn.WornUnequipResult
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.content.other.emirsarena.EmirsArena
import org.rsmod.content.other.emirsarena.EmirsArenaAttributes
import org.rsmod.content.other.emirsarena.ranked.ArenaRanking
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape

/**
 * Owns every duel in progress: the challenge handshake, the two interface screens, arena
 * allocation, the fight itself and the bookkeeping when it ends. The scripts and hooks are thin
 * wrappers that route events here.
 *
 * Anything that needs protected access on a player (teleporting, the countdown chatter) is done
 * through the `queue.emirs_arena_start` / `queue.emirs_arena_leave` strong queues, because a
 * player looking at a duel screen has a modal open and cannot be launched into protected access
 * directly.
 */
@Singleton
class DuelManager
@Inject
constructor(
    private val eventBus: EventBus,
    private val attackManager: PlayerAttackManager,
    private val unequipOp: WornUnequipOp,
    private val locRepo: LocRepository,
    private val areaChecker: AreaChecker,
    private val stakes: DuelStakes,
) {
    private val arenasInUse = HashSet<DuelArena>()
    private val trapdoorsSpawned = HashSet<DuelArena>()

    /** Players waiting for a ranked opponent, oldest first. */
    private val rankedQueue = ArrayDeque<Player>()

    /** Most recent duel results, newest first, for the arena scoreboards. */
    private val scoreboard = ArrayDeque<String>()

    private val allStats: List<String> by lazy {
        ServerCacheManager.getStats().values.map { RSCM.getReverseMapping(RSCMType.STAT, it.id) }
    }

    fun duelOf(player: Player): Duel? = player.attr[EmirsArenaAttributes.DUEL]

    fun inArena(player: Player): Boolean = areaChecker.inArea(EmirsArena.AREA, player.coords)

    val scoreboardLines: List<String>
        get() = scoreboard.toList()

    /* Area presence */

    fun enterArena(player: Player) {
        MiscOutput.setPlayerOp(player, EmirsArena.CHALLENGE_SLOT, "Challenge")
        recoverStake(player)
    }

    /** Hands back a stake left behind by a crash or restart; a no-op while a duel is pending. */
    fun recoverStake(player: Player) {
        if (duelOf(player) == null) {
            stakes.recover(player)
        }
    }

    fun exitArena(player: Player) {
        MiscOutput.setPlayerOp(player, EmirsArena.CHALLENGE_SLOT, null)
        player.attr.remove(EmirsArenaAttributes.CHALLENGE_TARGET)
        rankedQueue.remove(player)
        val duel = duelOf(player) ?: return
        if (!duel.isActive) {
            cancel(duel, "${player.displayName} has left the arena.")
        }
    }

    fun onLogout(player: Player) {
        rankedQueue.remove(player)
        player.attr.remove(EmirsArenaAttributes.CHALLENGE_TARGET)
        val duel = duelOf(player) ?: return
        when (duel.stage) {
            DuelStage.Countdown,
            DuelStage.Fighting -> finish(duel, winner = duel.other(player), loser = player, DuelEnd.Logout)
            DuelStage.Finished -> Unit
            else -> cancel(duel, "${player.displayName} has left the arena.")
        }
    }

    /* Challenges */

    fun challenge(challenger: Player, target: Player) {
        if (target === challenger) {
            return
        }
        if (duelOf(challenger) != null) {
            challenger.mes("You are already in a duel.")
            return
        }
        if (duelOf(target) != null) {
            challenger.mes("${target.displayName} is already in a duel.")
            return
        }
        if (!inArena(target)) {
            challenger.mes("${target.displayName} is not inside the arena.")
            return
        }
        if (target.isAccessProtected) {
            challenger.mes("${target.displayName} is busy at the moment.")
            return
        }
        if (target.attr[EmirsArenaAttributes.CHALLENGE_TARGET] === challenger) {
            target.attr.remove(EmirsArenaAttributes.CHALLENGE_TARGET)
            challenger.attr.remove(EmirsArenaAttributes.CHALLENGE_TARGET)
            openOptions(Duel(challenger = target, opponent = challenger, ranked = false))
            return
        }
        challenger.attr[EmirsArenaAttributes.CHALLENGE_TARGET] = target
        challenger.mes("Sending duel request...")
        // The client prints the text as-is and only uses the name to find the player when the line
        // is clicked (which arrives as the attack/Challenge option).
        GameMessage.requestMes(
            target,
            "${challenger.displayName} wishes to duel with you.",
            challenger.displayName,
            ChatType.ChalReqTrade,
        )
    }

    /* Options screen */

    private fun openOptions(duel: Duel) {
        for (player in duel.players) {
            player.attr[EmirsArenaAttributes.DUEL] = duel
            resetAcceptVars(player)
            syncRules(player, duel.rules)
            player.ifOpenMainModal(EmirsArena.OPTIONS_INTERFACE, eventBus)
            // The cache places the opponent panel at x=63, overlapping the option list; every
            // other middle-column component sits at x=140, so line it up with them.
            player.ifSetPosition(EmirsArena.OPTIONS_PREFIX + "opponent_detail", OPPONENT_PANEL_X, OPPONENT_PANEL_Y)
            showOpponentDetails(player, duel.other(player))
            player.ifSetText(EmirsArena.OPTIONS_PREFIX + "duelstatus", "")
            player.ifSetText(EmirsArena.OPTIONS_PREFIX + "acceptwarning", "")
        }
    }

    private fun showOpponentDetails(player: Player, opponent: Player) {
        player.ifSetText(EmirsArena.OPTIONS_PREFIX + "opponent_name", opponent.displayName)
        player.ifSetText(
            EmirsArena.OPTIONS_PREFIX + "opponent_level",
            "Combat level: ${opponent.appearance.combatLevel}",
        )
        for ((component, stat) in OPPONENT_STATS) {
            player.ifSetText(EmirsArena.OPTIONS_PREFIX + component, "${opponent.statBase(stat)}")
        }
    }

    fun changeRules(duel: Duel, changedBy: Player, rules: DuelRules) {
        if (duel.stage != DuelStage.Options || !rules.isValid) {
            return
        }
        duel.rules = rules
        duel.resetAcceptance()
        for (player in duel.players) {
            syncRules(player, rules)
            VarPlayerIntMapSetter.set(player, EmirsArena.ACCEPT_OPTIONS_VARBIT, 0)
            val status = if (player === changedBy) "" else "Duel options have been changed."
            player.ifSetText(EmirsArena.OPTIONS_PREFIX + "duelstatus", status)
        }
    }

    fun acceptOptions(duel: Duel, player: Player) {
        if (duel.stage != DuelStage.Options || duel.hasAccepted(player)) {
            return
        }
        val bothAccepted = duel.accept(player)
        VarPlayerIntMapSetter.set(player, EmirsArena.ACCEPT_OPTIONS_VARBIT, 1)
        player.ifSetText(EmirsArena.OPTIONS_PREFIX + "duelstatus", "Waiting for other player...")
        val other = duel.other(player)
        other.ifSetText(EmirsArena.OPTIONS_PREFIX + "duelstatus", "Other player has accepted.")
        if (bothAccepted) {
            if (duel.ranked) {
                openConfirm(duel)
            } else {
                openStakes(duel)
            }
        }
    }

    /* Stake screens (legacy duels only) */

    private fun openStakes(duel: Duel) {
        // Ironmen cannot stake, so their duels go straight to the rules confirmation.
        if (IronmanRestrictions.blockTrade(duel.challenger, duel.opponent)) {
            for (player in duel.players) {
                player.mes("This duel will be fought without stakes.")
            }
            openConfirm(duel)
            return
        }
        duel.stage = DuelStage.Stakes
        duel.resetAcceptance()
        for (player in duel.players) {
            resetAcceptVars(player)
        }
        stakes.open(duel)
    }

    suspend fun offerStake(access: org.rsmod.api.player.protect.ProtectedAccess, duel: Duel, slot: Int, op: dev.openrune.types.aconverted.interf.IfButtonOp) {
        if (duel.stage != DuelStage.Stakes) {
            return
        }
        stakes.offer(access, duel, slot, op)
    }

    suspend fun removeStake(access: org.rsmod.api.player.protect.ProtectedAccess, duel: Duel, slot: Int, op: dev.openrune.types.aconverted.interf.IfButtonOp) {
        if (duel.stage != DuelStage.Stakes) {
            return
        }
        stakes.remove(access, duel, slot, op)
    }

    fun acceptStakes(duel: Duel, player: Player) {
        if (duel.stage != DuelStage.Stakes) {
            return
        }
        if (stakes.accept(duel, player)) {
            duel.stage = DuelStage.StakeConfirm
            stakes.openConfirm(duel)
        }
    }

    fun acceptStakeConfirm(duel: Duel, player: Player) {
        if (duel.stage != DuelStage.StakeConfirm || duel.hasAccepted(player)) {
            return
        }
        if (duel.accept(player)) {
            for (duellist in duel.players) {
                stakes.close(duellist)
            }
            openConfirm(duel)
        }
    }

    /* Confirm screen */

    fun openConfirm(duel: Duel) {
        duel.stage = DuelStage.Confirm
        duel.resetAcceptance()
        for (player in duel.players) {
            player.attr[EmirsArenaAttributes.DUEL] = duel
            resetAcceptVars(player)
            syncRules(player, duel.rules)
            player.ifOpenMainModal(EmirsArena.CONFIRM_INTERFACE, eventBus)
            player.ifSetText(EmirsArena.CONFIRM_PREFIX + "status", "")
            showOpponentItems(player, duel.other(player), duel.rules)
        }
    }

    private fun showOpponentItems(player: Player, opponent: Player, rules: DuelRules) {
        if (!rules.has(DuelRule.ShowInventories)) {
            return
        }
        for (slot in EmirsArena.WORN_OPTION_SLOTS) {
            val obj = opponent.worn[slot] ?: continue
            player.ifSetObj(EmirsArena.CONFIRM_PREFIX + "otherworn$slot", obj, obj.count)
        }
        val backpack =
            opponent.inv
                .filterNotNull()
                .groupBy { it.id }
                .map { (id, objs) ->
                    val name = ServerCacheManager.getItem(id)?.name ?: "Unknown item"
                    val count = objs.sumOf { it.count }
                    if (count > 1) "$count x $name" else name
                }
        val text =
            if (backpack.isEmpty()) {
                "${opponent.displayName}'s backpack is empty."
            } else {
                "${opponent.displayName}'s backpack:<br>" + backpack.joinToString(", ")
            }
        player.ifSetText(EmirsArena.CONFIRM_PREFIX + "otherhidden", text)
    }

    fun acceptConfirm(duel: Duel, player: Player) {
        if (duel.stage != DuelStage.Confirm || duel.hasAccepted(player)) {
            return
        }
        val bothAccepted = duel.accept(player)
        VarPlayerIntMapSetter.set(player, EmirsArena.ACCEPT_CONFIRM_VARBIT, 1)
        player.ifSetText(EmirsArena.CONFIRM_PREFIX + "status", "Waiting for other player...")
        duel.other(player).ifSetText(EmirsArena.CONFIRM_PREFIX + "status", "Other player has accepted.")
        if (bothAccepted) {
            start(duel)
        }
    }

    /**
     * Called from the interface close events: a player closing a duel screen (escape key, another
     * interface) declines, unless the duel has already moved on to the next screen.
     */
    fun onScreenClosed(player: Player, stage: DuelStage) {
        val duel = duelOf(player) ?: return
        if (duel.stage == stage) {
            decline(duel, player)
        }
    }

    fun decline(duel: Duel, player: Player) {
        if (!duel.isActive && duel.stage != DuelStage.Finished) {
            cancel(duel, "${player.displayName} has declined the duel.")
        }
    }

    private fun cancel(duel: Duel, reason: String) {
        if (duel.stage == DuelStage.Finished) {
            return
        }
        duel.stage = DuelStage.Finished
        releaseArena(duel)
        for (player in duel.players) {
            detach(player)
            resetAcceptVars(player)
            player.ifClose(eventBus)
            stakes.close(player)
            stakes.refund(player)
            player.mes(reason)
        }
    }

    /* Starting the fight */

    private fun start(duel: Duel) {
        val arena = DuelArena.ALL.firstOrNull { it !in arenasInUse }
        if (arena == null) {
            cancel(duel, "All of the arenas are in use right now. Please try again shortly.")
            return
        }
        for (player in duel.players) {
            if (!removeDisallowedWorn(player, duel.rules)) {
                cancel(
                    duel,
                    "${player.displayName} does not have enough inventory space to remove the " +
                        "items this duel disallows.",
                )
                return
            }
        }
        duel.arena = arena
        duel.stage = DuelStage.Countdown
        arenasInUse += arena
        spawnTrapdoors(arena)
        for (player in duel.players) {
            player.attr[EmirsArenaAttributes.LAST_RULES] = duel.rules.packed
            resetAcceptVars(player)
            if (duel.rules.has(DuelRule.NoPrayer)) {
                player.disablePrayers()
            }
            if (duel.rules.has(DuelRule.NoDrinks)) {
                player.statRestoreAll(BOOSTABLE_COMBAT_STATS)
            }
            player.strongQueue(EmirsArena.START_QUEUE, 1)
        }
    }

    /** Moves worn items out of the slots the rules disable. False when a backpack is too full. */
    private fun removeDisallowedWorn(player: Player, rules: DuelRules): Boolean {
        for (wearpos in rules.disabledSlots) {
            if (player.worn[wearpos.slot] == null) {
                continue
            }
            val result = unequipOp.unequip(player, wearpos.slot, player.worn, player.inv)
            if (result is WornUnequipResult.Fail.NotEnoughInvSpace) {
                return false
            }
        }
        return true
    }

    private fun spawnTrapdoors(arena: DuelArena) {
        if (!trapdoorsSpawned.add(arena)) {
            return
        }
        for (coords in arena.trapdoors) {
            locRepo.add(
                coords = coords,
                internal = EmirsArena.TRAPDOOR_LOC,
                duration = Int.MAX_VALUE,
                angle = LocAngle.West,
                shape = LocShape.CentrepieceStraight,
            )
        }
    }

    /** Both countdown coroutines call this; the first one flips the duel into the fight. */
    fun beginFight(duel: Duel) {
        if (duel.stage != DuelStage.Countdown) {
            return
        }
        duel.stage = DuelStage.Fighting
        for (player in duel.players) {
            MiscOutput.setPlayerOp(player, EmirsArena.FIGHT_SLOT, "Fight", priority = true)
        }
    }

    /* Ending the fight */

    fun canForfeit(duel: Duel): Boolean = !duel.rules.has(DuelRule.NoForfeit)

    fun forfeit(duel: Duel, player: Player) {
        if (duel.stage != DuelStage.Fighting) {
            return
        }
        finish(duel, winner = duel.other(player), loser = player, DuelEnd.Forfeit)
    }

    /**
     * Runs from the loser's death sequence, after they have been moved to the hospital and had
     * their stats restored; settles the duel for both players.
     */
    fun finishAfterDeath(loser: Player) {
        val duel = duelOf(loser) ?: return
        if (!duel.isActive) {
            return
        }
        finish(duel, winner = duel.other(loser), loser = loser, DuelEnd.Death)
    }

    private fun finish(duel: Duel, winner: Player, loser: Player, end: DuelEnd) {
        if (duel.stage == DuelStage.Finished) {
            return
        }
        duel.stage = DuelStage.Finished
        duel.winner = winner
        releaseArena(duel)

        for (player in duel.players) {
            detach(player)
            val op = if (inArena(player)) "Challenge" else null
            MiscOutput.setPlayerOp(player, EmirsArena.FIGHT_SLOT, op)
        }

        // The loser is either mid-death (already being moved and restored by the death sequence)
        // or logging out; only a forfeiting loser still needs walking out of the arena.
        restore(winner)
        winner.midiJingle(EmirsArena.DUEL_WIN_JINGLE)
        winner.strongQueue(EmirsArena.LEAVE_QUEUE, 1)
        if (end == DuelEnd.Forfeit) {
            restore(loser)
            loser.strongQueue(EmirsArena.LEAVE_QUEUE, 1)
        }

        when (end) {
            DuelEnd.Death -> {
                winner.mes("Well done! You have defeated ${loser.displayName}!")
                loser.mes("You have been defeated by ${winner.displayName}.")
            }
            DuelEnd.Forfeit -> {
                winner.mes("${loser.displayName} has forfeited the duel. You win!")
                loser.mes("You have forfeited the duel.")
            }
            DuelEnd.Logout -> {
                winner.mes("${loser.displayName} has left the arena. You win by forfeit!")
            }
        }

        awardStakes(duel, winner, loser)
        record(duel, winner, loser)
    }

    private fun awardStakes(duel: Duel, winner: Player, loser: Player) {
        if (duel.ranked) {
            return
        }
        val staked = stakes.hasStake(winner) || stakes.hasStake(loser)
        stakes.award(winner, loser)
        if (staked) {
            winner.mes("You receive the stakes of the duel.")
            loser.mes("You have lost your stake.")
        }
    }

    private fun releaseArena(duel: Duel) {
        duel.arena?.let { arenasInUse -= it }
    }

    private fun detach(player: Player) {
        player.attr.remove(EmirsArenaAttributes.DUEL)
    }

    private fun restore(player: Player) {
        attackManager.stopCombat(player)
        player.statRestoreAll(allStats)
        player.cureAllToxins()
        player.runEnergy = constants.run_max_energy
        UpdateRun.energy(player, player.runEnergy)
        VarPlayerIntMapSetter.set(player, "varp.sa_energy", FULL_SPECIAL_ENERGY)
    }

    /* Records and points */

    private fun record(duel: Duel, winner: Player, loser: Player) {
        val kind = if (duel.ranked) "Ranked duel" else "Legacy duel"
        scoreboard.addFirst("${winner.displayName} defeated ${loser.displayName}  ($kind)")
        while (scoreboard.size > EmirsArena.SCOREBOARD_SIZE) {
            scoreboard.removeLast()
        }

        if (!duel.ranked) {
            winner.attr.increment(EmirsArenaAttributes.LEGACY_WINS)
            loser.attr.increment(EmirsArenaAttributes.LEGACY_LOSSES)
            return
        }

        winner.attr.increment(EmirsArenaAttributes.RANKED_WINS)
        loser.attr.increment(EmirsArenaAttributes.RANKED_LOSSES)

        val winnerRank = ArenaRanking.effectiveRank(winner.vars[EmirsArena.RANK_POINTS_VARBIT])
        val loserRank = ArenaRanking.effectiveRank(loser.vars[EmirsArena.RANK_POINTS_VARBIT])
        val stake = ArenaRanking.rankStake(winnerRank, loserRank)

        val streak = winner.attr.getOrDefault(EmirsArenaAttributes.WIN_STREAK, 0)
        val winnerReward = ArenaRanking.winRewardPoints(streak)
        winner.attr[EmirsArenaAttributes.WIN_STREAK] = streak + 1
        loser.attr[EmirsArenaAttributes.WIN_STREAK] = 0

        awardPoints(winner, ArenaRanking.applyWin(winnerRank, stake), winnerReward)
        awardPoints(loser, ArenaRanking.applyLoss(loserRank, stake), ArenaRanking.LOSS_REWARD_POINTS)

        winner.mes("You gain $stake rank points and $winnerReward reward points.")
        loser.mes("You lose $stake rank points and gain ${ArenaRanking.LOSS_REWARD_POINTS} reward points.")
    }

    private fun awardPoints(player: Player, rankPoints: Int, rewardPoints: Int) {
        VarPlayerIntMapSetter.set(player, EmirsArena.RANK_POINTS_VARBIT, rankPoints)
        val rewards = ArenaRanking.addRewardPoints(player.vars[EmirsArena.REWARD_POINTS_VARBIT], rewardPoints)
        VarPlayerIntMapSetter.set(player, EmirsArena.REWARD_POINTS_VARBIT, rewards)
    }

    /* Ranked matchmaking */

    fun isQueuedForRanked(player: Player): Boolean = player in rankedQueue

    /** Returns a message for the player, and pairs them up straight away when someone is waiting. */
    fun signUpForRanked(player: Player): String {
        if (duelOf(player) != null) {
            return "You can't sign up while you are in a duel."
        }
        if (player in rankedQueue) {
            return "You are already signed up for a ranked duel."
        }
        val waiting = rankedQueue.firstOrNull { it !== player && duelOf(it) == null && inArena(it) }
        if (waiting == null) {
            rankedQueue.addLast(player)
            return "You have signed up for a ranked duel. You'll be matched as soon as another " +
                "fighter signs up."
        }
        rankedQueue.remove(waiting)
        val duel = Duel(challenger = waiting, opponent = player, ranked = true)
        for (duellist in duel.players) {
            duellist.mes("You have been matched with ${duel.other(duellist).displayName} for a ranked duel.")
        }
        openConfirm(duel)
        return "A ranked opponent was found!"
    }

    fun withdrawFromRanked(player: Player): String =
        if (rankedQueue.remove(player)) {
            "You are no longer waiting for a ranked duel."
        } else {
            "You are not signed up for a ranked duel."
        }

    /* Helpers */

    private fun syncRules(player: Player, rules: DuelRules) {
        VarPlayerIntMapSetter.set(player, EmirsArena.RULES_VARP, rules.packed)
    }

    private fun resetAcceptVars(player: Player) {
        VarPlayerIntMapSetter.set(player, EmirsArena.ACCEPT_OPTIONS_VARBIT, 0)
        VarPlayerIntMapSetter.set(player, EmirsArena.ACCEPT_CONFIRM_VARBIT, 0)
    }

    private fun org.rsmod.api.attr.AttributeMap.increment(key: org.rsmod.api.attr.AttributeKey<Int>) {
        this[key] = getOrDefault(key, 0) + 1
    }

    private companion object {
        private const val FULL_SPECIAL_ENERGY = 1000
        private const val OPPONENT_PANEL_X = 140
        private const val OPPONENT_PANEL_Y = 2

        private val OPPONENT_STATS =
            listOf(
                "attack_level" to "stat.attack",
                "strength_level" to "stat.strength",
                "defence_level" to "stat.defence",
                "hitpoints_level" to "stat.hitpoints",
                "prayer_level" to "stat.prayer",
                "ranged_level" to "stat.ranged",
                "magic_level" to "stat.magic",
            )

        /** Stats a potion can boost; "No Drinks" resets them when the fight starts. */
        private val BOOSTABLE_COMBAT_STATS =
            listOf("stat.attack", "stat.strength", "stat.defence", "stat.ranged", "stat.magic")

        @Suppress("unused") private val WEAPON_SLOT = Wearpos.RightHand
    }
}
