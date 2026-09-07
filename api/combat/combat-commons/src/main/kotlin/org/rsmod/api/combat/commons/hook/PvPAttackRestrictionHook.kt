package org.rsmod.api.combat.commons.hook

import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.game.entity.Player

/**
 * Asked once the engine has resolved *how* [attacker] is about to hit [target] (melee, ranged, a
 * spell or a staff attack, and whether a special attack is queued). A non-null message cancels the
 * attack and is shown to the attacker.
 *
 * Unlike `PvPAttackValidateHook`, which decides whether the two players may fight at all, this hook
 * lets content restrict the combat style within a fight that is otherwise allowed - duel rules such
 * as "No Magic" or "No Special Attacks".
 */
public fun interface PvPAttackRestrictionHook {
    public fun restriction(
        attacker: Player,
        target: Player,
        attack: CombatAttack.PlayerAttack,
        special: Boolean,
    ): String?
}
