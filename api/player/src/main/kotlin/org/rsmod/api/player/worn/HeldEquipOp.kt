package org.rsmod.api.player.worn

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import dev.openrune.util.Wearpos
import jakarta.inject.Inject
import org.rsmod.api.config.refs.BaseParams
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.invtx.swap
import org.rsmod.api.invtx.transfer
import org.rsmod.api.player.events.interact.HeldEquipEvents
import org.rsmod.api.player.hook.PlayerRestrictions
import org.rsmod.api.player.hook.RestrictedAction
import org.rsmod.api.player.righthand
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.utils.format.addArticle
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.Inventory
import org.rsmod.game.stat.StatRequirement
import org.rsmod.game.type.getInvObj
import org.rsmod.objtx.TransactionResult
import org.rsmod.objtx.isErr

public class HeldEquipOp
@Inject
constructor(private val eventBus: EventBus, private val restrictions: PlayerRestrictions) {
    public fun equip(player: Player, invSlot: Int, inventory: Inventory): HeldEquipResult {
        val obj = inventory[invSlot] ?: return HeldEquipResult.Fail.InvalidObj
        val objType = getInvObj(obj)

        val restriction = restrictions.check(player, RestrictedAction.Equip(objType))
        if (restriction != null) {
            return HeldEquipResult.Fail.Restricted(restriction)
        }

        val result = equip(player, objType)

        if (result is HeldEquipResult.Success) {
            val (unequipWearpos, primaryWearpos) = result
            val allWearpos = unequipWearpos + primaryWearpos
            val into = player.worn

            // Cache objs to publish as events after successful transaction.
            val unequipObjs =
                allWearpos.associateWith { pos ->
                    into[pos.slot]?.let { id ->
                        ServerCacheManager.getItems().values.firstOrNull { it.id == id.id }
                    }
                }
            val unequipPrimary = into[primaryWearpos.slot] != null

            val transaction =
                player.invTransaction(inventory, into) {
                    val inv = select(inventory)
                    val worn = select(into)
                    swap(
                        from = inv,
                        fromSlot = invSlot,
                        intoSlot = primaryWearpos.slot,
                        into = worn,
                        transform = true,
                        mergeStacks = true,
                    )
                    for (unequip in unequipWearpos) {
                        val wornObj = into[unequip.slot] ?: continue
                        transfer(
                            from = worn,
                            fromSlot = unequip.slot,
                            count = wornObj.count,
                            into = inv,
                            intoSlot = if (unequipPrimary) null else invSlot,
                            untransform = true,
                        )
                    }
                }

            val equipTransaction = transaction[0]
            if (equipTransaction.isErr()) {
                check(equipTransaction is TransactionResult.NotEnoughSpace) {
                    "Transaction error is expected to only be of " +
                        "`NotEnoughSpace` type: found=$equipTransaction"
                }
                val message = "You don't have enough free space to do that."
                return HeldEquipResult.Fail.NotEnoughWornSpace(message)
            }

            val unequipTransactionErr = transaction.err
            if (unequipTransactionErr != null) {
                check(unequipTransactionErr is TransactionResult.NotEnoughSpace) {
                    "Transaction error is expected to only be of " +
                        "`NotEnoughSpace` type: found=$unequipTransactionErr"
                }
                val message = "You don't have enough free inventory space to do that."
                return HeldEquipResult.Fail.NotEnoughInvSpace(message)
            }

            for (wearpos in allWearpos) {
                val wornType = if (wearpos == primaryWearpos) objType else unequipObjs[wearpos]
                if (wornType != null) {
                    val change = HeldEquipEvents.WearposChange(player, wearpos, wornType)
                    eventBus.publish(change)
                }
            }

            for ((wearpos, type) in unequipObjs) {
                val unequipType = type ?: continue
                val unequip = HeldEquipEvents.Unequip(player, wearpos, unequipType)
                eventBus.publish(unequip)
            }

            val equip = HeldEquipEvents.Equip(player, invSlot, primaryWearpos, objType)
            eventBus.publish(equip)

            player.rebuildAppearance()
        }

        return result
    }

    private fun equip(player: Player, type: ItemServerType): HeldEquipResult {
        val statRequirements =
            type.statRequirements().filter { player.statBase(RSCM.getReverseMapping(RSCMType.STAT,it.stat.id)) < it.level }
        if (statRequirements.isNotEmpty()) {
            val messages = type.toMessages(statRequirements)
            return HeldEquipResult.Fail.StatRequirements(messages)
        }

        val wearpos1 = Wearpos[type.wearpos1] ?: return HeldEquipResult.Fail.InvalidObj
        val wearpos2 = Wearpos[type.wearpos2]?.takeUnless { it.isClientOnly }
        val wearpos3 = Wearpos[type.wearpos3]?.takeUnless { it.isClientOnly }

        val unequipWearpos = mutableListOf<Wearpos>()
        wearpos2?.let(unequipWearpos::add)
        wearpos3?.let(unequipWearpos::add)
        if (wearpos1 == Wearpos.LeftHand) {
            val twoHanded = player.righthand?.takeIf { getInvObj(it).isTwoHanded() }
            if (twoHanded != null && Wearpos.RightHand !in unequipWearpos) {
                unequipWearpos += Wearpos.RightHand
            }
        }

        return HeldEquipResult.Success(unequipWearpos, wearpos1)
    }

    private fun ItemServerType.statRequirements(): List<StatRequirement> {
        val skillReq1 = paramOrNull(BaseParams.statreq1_skill)
        val skillReq2 = paramOrNull(BaseParams.statreq2_skill)
        if (skillReq1 == null && skillReq2 == null) {
            return emptyList()
        }
        val levelReq1 = paramOrNull(BaseParams.statreq1_level) ?: 0
        val levelReq2 = paramOrNull(BaseParams.statreq2_level) ?: 0
        val statReq1 = skillReq1?.let { StatRequirement(it, levelReq1) }
        val statReq2 = skillReq2?.let { StatRequirement(it, levelReq2) }
        return listOfNotNull(statReq1, statReq2)
    }

    private fun ItemServerType.toMessages(reqs: List<StatRequirement>): Pair<String, String> {
        val message1 = param(BaseParams.statreq_failmessage1)
        val message2 = paramOrNull(BaseParams.statreq_failmessage2)
        val replace =
            when (reqs.size) {
                1 -> {
                    val message = message2 ?: DEFAULT_STAT_MESSAGE1
                    message
                        .replace("{skill1}", reqs[0].stat.displayName.addArticle())
                        .replace("{level1}", reqs[0].level.toString())
                }
                2 -> {
                    val message = message2 ?: DEFAULT_STAT_MESSAGE2
                    message
                        .replace("{skill1}", reqs[0].stat.displayName.addArticle())
                        .replace("{level1}", reqs[0].level.toString())
                        .replace("{skill2}", reqs[1].stat.displayName.addArticle())
                        .replace("{level2}", reqs[1].level.toString())
                }
                else -> error("Obj unexpected stat requirement list size: reqs=$reqs, type=$this")
            }
        return message1 to replace
    }

    private fun ItemServerType.isTwoHanded(): Boolean =
        wearpos2 == Wearpos.LeftHand.slot || wearpos3 == Wearpos.LeftHand.slot

    private companion object {
        private const val DEFAULT_STAT_MESSAGE1 = "You need to have {skill1} level of {level1}."

        private const val DEFAULT_STAT_MESSAGE2 =
            "You need to have {skill1} level of {level1} and {skill2} level of {level2}."
    }
}
