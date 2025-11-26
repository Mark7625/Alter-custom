package org.alter.skills.runecrafting

import org.alter.api.Skills
import org.alter.api.ext.getItemName
import org.alter.api.ext.message
import org.alter.api.ext.messageBox
import org.alter.api.ext.setVarbit
import org.alter.game.model.Graphic
import org.alter.game.model.attr.AttributeKey
import org.alter.game.model.entity.Player
import org.alter.rscm.RSCM.asRSCM
import org.alter.skills.runecrafting.BloodEssenceEvents.Companion.BLOOD_ESSENCE
import org.generated.tables.ComboruneRecipeRow
import org.generated.tables.runecrafting.RunecraftingRunesRow
import kotlin.math.floor

object RunecraftAction {

    private const val RUNECRAFT_WAIT_CYCLE = 3
    private val RUNECRAFT_GRAPHIC = Graphic(id = "spotanims.runecrafting", height = 100)
    private const val RUNECRAFT_SOUND = 2710

    private val runecraftingExtract = mapOf(
        "items.scar_extract_warped".asRSCM() to 250,
        "items.scar_extract_twisted".asRSCM() to 60,
        "items.scar_extract_mangled".asRSCM() to 60,
        "items.scar_extract_scarred".asRSCM() to 60,
    )

    private fun preCraft(player: Player) {
        player.lock()
        player.queue {
            player.animate("sequences.human_runecraft")
            player.graphic(RUNECRAFT_GRAPHIC)
            player.playSound(RUNECRAFT_SOUND)
            wait(RUNECRAFT_WAIT_CYCLE)
        }
        player.unlock()
    }

    fun craftRune(player: Player, rune: RunecraftingRunesRow) {
        if (!canCraftRune(player, rune)) return

        preCraft(player)

        val inv = player.inventory
        val pureEssCount = inv.getItemCount("items.blankrune_high")
        val daeyaltEssCount = inv.getItemCount("items.blankrune_daeyalt")
        val totalEssence = pureEssCount + daeyaltEssCount
        if (totalEssence <= 0) return

        if (daeyaltEssCount > 0 && !inv.remove("items.blankrune_daeyalt", daeyaltEssCount).hasSucceeded()) return
        if (pureEssCount > 0 && !inv.remove("items.blankrune_high", pureEssCount).hasSucceeded()) return

        val level = player.getSkills().getBaseLevel(Skills.RUNECRAFTING)
        val baseMultiplier = getBonusMultiplier(rune.runeOutput, level).toInt()

        val producedDaeyalt = applyOutfitBonus(player, daeyaltEssCount * baseMultiplier)
        val producedPure = applyOutfitBonus(player, pureEssCount * baseMultiplier)
        var totalRunes = producedDaeyalt + producedPure

        // Extract bonus
        rune.extract.takeIf { inv.contains(it) }?.let {
            totalRunes += runecraftingExtract[it] ?: 0
        }

        // Blood essence bonus
        if (inv.contains("items.blood_essence_active") && rune.runeOutput == "items.bloodrune".asRSCM()) {
            applyBloodEssenceBonus(player, totalEssence)?.let { bonus ->
                totalRunes += bonus
            }
        }

        inv.add(rune.runeOutput, totalRunes)

        // XP
        val totalXp = (producedDaeyalt * rune.xp * 1.5) + (producedPure * rune.xp)
        player.addXp(Skills.RUNECRAFTING, totalXp)
    }

    private fun applyBloodEssenceBonus(player: Player, totalEssence: Int): Int? {
        val inv = player.inventory
        val durability = player.attr[BLOOD_ESSENCE]?: 0
        var extraRunes = 0
        repeat(totalEssence) { if ((0..1).random() == 0) extraRunes++ }

        val granted = extraRunes.coerceAtMost(durability)
        if (granted <= 0) return null

        player.attr.decrement(BLOOD_ESSENCE, granted)

        player.attr[BLOOD_ESSENCE]?.let { remaining ->
            if (remaining <= 0) {
                inv.remove("items.blood_essence_active")
                player.message("Your blood essence has been destroyed after crafting 1,000 runes.")
            } else {
                player.message("You manage to extract power from the Blood Essence and craft $granted extra rune${if (granted != 1) "s" else ""}.")
            }
        }
        return granted
    }

    private fun countRaimentPieces(player: Player): Int {
        val sets = listOf(
            listOf("items.hat_of_the_eye", "items.hat_of_the_eye_blue", "items.hat_of_the_eye_green"),
            listOf("items.robe_top_of_the_eye", "items.robe_top_of_the_eye_blue", "items.robe_top_of_the_eye_green"),
            listOf("items.robe_bottom_of_the_eye", "items.robe_bottom_of_the_eye_blue", "items.robe_bottom_of_the_eye_green"),
            listOf("items.boots_of_the_eye")
        )
        return sets.count { variants -> variants.any { player.equipment.contains(it) } }
    }

    private fun applyOutfitBonus(player: Player, baseRunes: Int): Int {
        val pieces = countRaimentPieces(player)
        if (pieces == 0) return baseRunes

        val multiplier = when (pieces) {
            1 -> 1
            2 -> 2
            3 -> 3
            4 -> 6
            else -> 1
        }

        val tens = baseRunes / 10
        val remainder = baseRunes % 10
        var bonus = tens * multiplier
        if (remainder > 0 && player.world.random(10) < remainder) bonus += multiplier
        return baseRunes + bonus
    }

    private fun canCraftRune(player: Player, rune: RunecraftingRunesRow): Boolean {
        val level = player.getSkills().getBaseLevel(Skills.RUNECRAFTING)
        if (level < rune.level) {
            player.queue { messageBox(player, "You need Runecrafting level ${rune.level} to craft ${rune.runeOutput.getItemName().lowercase()}s.") }
            return false
        }

        val hasEssence = player.inventory.any { it != null && (rune.validEssences.contains(it.id) || it.id == "items.blankrune_daeyalt".asRSCM()) }
        if (!hasEssence) {
            val essenceName = rune.validEssences.first().getItemName().lowercase()
            player.queue { messageBox(player, "You do not have any $essenceName or Daeyalt essence to bind.") }
            return false
        }

        return true
    }

    fun getBonusMultiplier(rune: Int, level: Int): Double = when (rune) {
        "items.airrune".asRSCM() -> floor(level / 11.0) + 1
        "items.mindrune".asRSCM() -> floor(level / 14.0) + 1
        "items.waterrune".asRSCM() -> floor(level / 19.0) + 1
        "items.earthrune".asRSCM() -> floor(level / 26.0) + 1
        "items.firerune".asRSCM() -> floor(level / 35.0) + 1
        "items.bodyrune".asRSCM() -> floor(level / 46.0) + 1
        "items.cosmicrune".asRSCM() -> floor(level / 59.0) + 1
        "items.chaosrune".asRSCM() -> floor(level / 74.0) + 1
        "items.naturerune".asRSCM() -> floor(level / 91.0) + 1
        else -> 1.0
    }

    fun craftCombination(player: Player, combo: ComboruneRecipeRow) {
        if (!canCraftCombo(player, combo)) return

        preCraft(player)

        val inv = player.inventory
        val craftCount = minOf(inv.getItemCount("items.blankrune_high"), inv.getItemCount(combo.runeInput!!))
        if (craftCount <= 0) return

        if (!inv.remove(combo.talisman!!).hasSucceeded()) return
        if (!inv.remove("items.blankrune_high", craftCount).hasSucceeded()) return
        val removedRunes = inv.remove(combo.runeInput!!, craftCount)
        if (!removedRunes.hasSucceeded()) return

        val wearingNecklace = player.equipment.contains("items.magic_emerald_necklace")
        var finalCount = if (wearingNecklace) removedRunes.completed else (1..removedRunes.completed).count { player.world.random(100) < 50 }

        if (inv.contains("items.scar_extract_twisted")) finalCount += runecraftingExtract["items.scar_extract_twisted".asRSCM()] ?: 0

        inv.add(combo.runeOutput!!, finalCount)
        player.addXp(Skills.RUNECRAFTING, finalCount * combo.xp!!)
    }

    private fun canCraftCombo(player: Player, combo: ComboruneRecipeRow): Boolean {
        val level = player.getSkills().getBaseLevel(Skills.RUNECRAFTING)
        val outputName = combo.runeOutput!!.getItemName()
        val inputName = combo.runeInput!!.getItemName()
        val talismanName = combo.talisman!!.getItemName()

        if (level < combo.level!!) {
            player.queue { messageBox(player, "You need Runecrafting level ${combo.level} to craft ${outputName}s.") }
            return false
        }
        if (!player.inventory.contains("items.blankrune_high")) {
            player.message("You need pure essence to craft ${outputName}s.")
            return false
        }
        if (!player.inventory.contains(combo.runeInput)) {
            player.message("You need ${inputName}s to craft ${outputName}s.")
            return false
        }
        if (!player.inventory.contains(combo.talisman)) {
            player.message("You need a $talismanName to craft ${outputName}s.")
            return false
        }
        return true
    }
}