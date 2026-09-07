package org.rsmod.content.other.emirsarena

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.util.Wearpos
import jakarta.inject.Inject
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.hook.PvPAttackRestrictionHook
import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.api.death.PlayerDeathContext
import org.rsmod.api.death.PlayerDeathHandling
import org.rsmod.api.death.PlayerDeathHook
import org.rsmod.api.death.PlayerRespawnHook
import org.rsmod.api.death.PvPAttackValidateHook
import org.rsmod.api.death.PvPAttackValidateResult
import org.rsmod.api.death.UntradeableHandling
import org.rsmod.api.player.hook.PlayerRestrictionHook
import org.rsmod.api.player.hook.PlayerTeleportValidateHook
import org.rsmod.api.player.hook.RestrictedAction
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.content.other.emirsarena.duel.DuelManager
import org.rsmod.content.other.emirsarena.duel.DuelRule
import org.rsmod.content.other.emirsarena.duel.DuelStage
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

/**
 * Everything the engine asks the arena about while a duel is running: who may attack whom, which
 * attacks and actions the rules forbid, that nobody teleports out, and that dying in a duel costs
 * no items and wakes you up in the hospital.
 */
public class EmirsArenaHooks
@Inject
constructor(private val manager: DuelManager, private val areaChecker: AreaChecker) :
    PvPAttackValidateHook,
    PvPAttackRestrictionHook,
    PlayerRestrictionHook,
    PlayerTeleportValidateHook,
    PlayerDeathHook,
    PlayerDeathCleanupHook,
    PlayerRespawnHook {
    private val funWeapons: Set<Int> by lazy {
        EmirsArena.FUN_WEAPONS.map { it.asRSCM(RSCMType.OBJ) }.toSet()
    }

    override fun validate(attacker: Player, target: Player): PvPAttackValidateResult {
        val duel = manager.duelOf(attacker)
        if (duel == null) {
            if (manager.duelOf(target)?.isActive == true) {
                return PvPAttackValidateResult.Deny("${target.displayName} is in a duel.")
            }
            // Inside the arena the attack option is "Challenge": clicking it, or the chat request
            // (which the client sends as the same option), issues or accepts a challenge instead
            // of starting a fight. Fights only happen inside a duel here.
            if (areaChecker.inArea(EmirsArena.AREA, attacker.coords)) {
                manager.challenge(attacker, target)
                return PvPAttackValidateResult.Silent
            }
            return PvPAttackValidateResult.Pass
        }
        if (!duel.contains(target)) {
            return PvPAttackValidateResult.Deny("You can only attack your duel opponent.")
        }
        return when (duel.stage) {
            DuelStage.Fighting -> PvPAttackValidateResult.Pass
            DuelStage.Countdown -> PvPAttackValidateResult.Deny("The duel hasn't started yet!")
            else -> PvPAttackValidateResult.Deny("You are not fighting a duel right now.")
        }
    }

    override fun restriction(
        attacker: Player,
        target: Player,
        attack: CombatAttack.PlayerAttack,
        special: Boolean,
    ): String? {
        val duel = manager.duelOf(attacker) ?: return null
        if (duel.stage != DuelStage.Fighting) {
            return null
        }
        val rules = duel.rules
        if (special && rules.has(DuelRule.NoSpecialAttacks)) {
            return "Special attacks have been disabled for this duel."
        }
        val styleMessage =
            when (attack) {
                is CombatAttack.Melee ->
                    "Melee attacks have been disabled for this duel.".takeIf {
                        rules.has(DuelRule.NoMelee)
                    }
                is CombatAttack.Ranged ->
                    "Ranged attacks have been disabled for this duel.".takeIf {
                        rules.has(DuelRule.NoRanged)
                    }
                is CombatAttack.Magic ->
                    "Magic attacks have been disabled for this duel.".takeIf {
                        rules.has(DuelRule.NoMagic)
                    }
            }
        if (styleMessage != null) {
            return styleMessage
        }
        if (rules.has(DuelRule.FunWeapons)) {
            val weapon =
                when (attack) {
                    is CombatAttack.Melee -> attack.weapon
                    is CombatAttack.Ranged -> attack.weapon
                    is CombatAttack.Spell -> attack.weapon
                    is CombatAttack.Staff -> attack.weapon
                }
            if (weapon == null || weapon.id !in funWeapons) {
                return "You can only use fun weapons in this duel."
            }
        }
        return null
    }

    override fun restriction(player: Player, action: RestrictedAction): String? {
        val duel = manager.duelOf(player) ?: return null
        if (!duel.isActive) {
            return null
        }
        val rules = duel.rules
        return when (action) {
            RestrictedAction.Walk ->
                when {
                    duel.stage == DuelStage.Countdown -> "The duel hasn't started yet!"
                    rules.has(DuelRule.NoMovement) -> "Movement has been disabled for this duel."
                    else -> null
                }
            RestrictedAction.Prayer ->
                "Prayer has been disabled for this duel.".takeIf { rules.has(DuelRule.NoPrayer) }
            RestrictedAction.Food ->
                "Food has been disabled for this duel.".takeIf { rules.has(DuelRule.NoFood) }
            RestrictedAction.Drink ->
                "Drinks have been disabled for this duel.".takeIf { rules.has(DuelRule.NoDrinks) }
            is RestrictedAction.Equip -> {
                val slots =
                    listOf(action.obj.wearpos1, action.obj.wearpos2, action.obj.wearpos3).filter {
                        it >= 0
                    }
                when {
                    rules.has(DuelRule.NoWeaponSwitch) && Wearpos.RightHand.slot in slots ->
                        "Weapon switching has been disabled for this duel."
                    slots.any { rules.isSlotDisabled(it) } ->
                        "You can't wear that in this duel."
                    else -> null
                }
            }
        }
    }

    override fun validate(player: Player, type: TeleportType, areaChecker: AreaChecker): String? {
        val duel = manager.duelOf(player) ?: return null
        if (!duel.isActive || type == TeleportType.Exempt) {
            return null
        }
        return "You can't teleport out of a duel!"
    }

    override fun handleDeath(context: PlayerDeathContext): PlayerDeathHandling? {
        val duel = manager.duelOf(context.player) ?: return null
        if (!duel.isActive) {
            return null
        }
        return PlayerDeathHandling(
            keepCount = Int.MAX_VALUE,
            dropReceiver = null,
            dropDuration = 0,
            revealDelay = 0,
            supplyPile = false,
            untradeableHandling = UntradeableHandling.KEEP,
        )
    }

    override fun respawn(player: Player): CoordGrid? {
        val duel = manager.duelOf(player) ?: return null
        return if (duel.isActive) EmirsArena.LOBBY else null
    }

    override fun cleanup(player: Player) {
        manager.finishAfterDeath(player)
    }
}
