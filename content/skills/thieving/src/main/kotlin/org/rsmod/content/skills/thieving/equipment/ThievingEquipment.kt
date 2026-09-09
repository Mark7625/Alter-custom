package org.rsmod.content.skills.thieving.equipment

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.util.Wearpos
import jakarta.inject.Singleton
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.random.GameRandom
import org.rsmod.game.entity.Player

/**
 * The worn gear that changes how pickpocketing plays out.
 *
 * - Rogue outfit: each of the five pieces adds a 15% chance of doubling pickpocket loot; the full
 *   set always doubles it. Stalls and chests are unaffected.
 * - Gloves of silence: +5% success chance. They wear out after 62 failed pickpockets.
 * - Dodgy necklace: a 25% chance to shrug off the stun and damage of a failed pickpocket. Each
 *   time it does, one of its 10 charges is spent; the last charge crumbles it to dust.
 * - Thieving cape: +10% success chance.
 *
 * Wear on the gloves and charges on the necklace are kept per player, so a replacement pair or
 * necklace starts fresh only once the old one is gone.
 */
@Singleton
class ThievingEquipment {

    fun roguePieces(player: Player): Int = ROGUE_PIECES.count { (wearpos, obj) -> player.wears(wearpos, obj) }

    /** Rolls whether the worn rogue pieces double this pickpocket's loot. */
    fun rollRogueDoubleLoot(player: Player, random: GameRandom): Boolean {
        val pieces = roguePieces(player)
        if (pieces == 0) {
            return false
        }
        val chance = if (pieces >= ROGUE_PIECES.size) 100 else pieces * ROGUE_PIECE_CHANCE
        return random.of(maxExclusive = 100) < chance
    }

    /** Multiplier applied to the pickpocket success chance. */
    fun successMultiplier(player: Player): Double {
        var multiplier = 1.0
        if (player.wearsGlovesOfSilence()) {
            multiplier += GLOVES_BONUS
        }
        if (player.wears(Wearpos.Back, THIEVING_CAPE) || player.wears(Wearpos.Back, THIEVING_CAPE_TRIMMED)) {
            multiplier += CAPE_BONUS
        }
        return multiplier
    }

    /** Called on every failed pickpocket: worn gloves of silence take one point of wear. */
    fun wearGlovesOfSilence(access: ProtectedAccess) {
        val player = access.player
        if (!player.wearsGlovesOfSilence()) {
            return
        }
        val wear = player.attr.getOrDefault(GLOVES_WEAR, 0) + 1
        if (wear < GLOVES_LIFETIME) {
            player.attr[GLOVES_WEAR] = wear
            if (wear == GLOVES_LIFETIME - 1) {
                access.mes("Your gloves of silence are about to fall apart!")
            }
            return
        }
        player.attr.remove(GLOVES_WEAR)
        if (!access.invDel(player.worn, GLOVES_OF_SILENCE, 1).failure) {
            player.rebuildAppearance()
        }
        access.mes("Your gloves of silence have worn out completely and fall apart.")
    }

    /**
     * Returns `true` when a worn dodgy necklace absorbs this failed pickpocket, spending a charge
     * and crumbling on the last one.
     */
    fun dodgyNecklaceProtects(access: ProtectedAccess): Boolean {
        val player = access.player
        if (!player.wears(Wearpos.Front, DODGY_NECKLACE)) {
            return false
        }
        if (access.random.of(maxExclusive = 100) >= DODGY_PROTECT_CHANCE) {
            return false
        }
        val charges = player.attr.getOrDefault(DODGY_CHARGES, DODGY_MAX_CHARGES) - 1
        if (charges > 0) {
            player.attr[DODGY_CHARGES] = charges
            access.mes("Your dodgy necklace protects you. It has $charges ${plural("charge", charges)} left.")
            return true
        }
        player.attr.remove(DODGY_CHARGES)
        if (!access.invDel(player.worn, DODGY_NECKLACE, 1).failure) {
            player.rebuildAppearance()
        }
        access.mes("Your dodgy necklace protects you. It then crumbles to dust.")
        return true
    }

    /** `Check` on gloves of silence. */
    fun checkGloves(access: ProtectedAccess) {
        val remaining = GLOVES_LIFETIME - access.player.attr.getOrDefault(GLOVES_WEAR, 0)
        val line =
            when {
                remaining >= GLOVES_LIFETIME -> "Your gloves are new."
                remaining > 33 -> "Your gloves are in good condition."
                remaining > 19 -> "Your gloves are starting to look quite shabby."
                remaining > 9 -> "Your gloves are starting to need repair."
                remaining > 1 -> "Your gloves are in need of repair!"
                else -> "Your gloves are about to fall apart!"
            }
        access.mes(line)
    }

    /** `Check` on a dodgy necklace. */
    fun checkNecklace(access: ProtectedAccess) {
        val charges = access.player.attr.getOrDefault(DODGY_CHARGES, DODGY_MAX_CHARGES)
        access.mes("Your dodgy necklace has $charges ${plural("charge", charges)} left.")
    }

    /** `Break` on a dodgy necklace: destroys it and forgets its charges. */
    fun breakNecklace(access: ProtectedAccess, slot: Int) {
        if (access.invDel(access.inv, DODGY_NECKLACE, 1, slot = slot).failure) {
            return
        }
        access.player.attr.remove(DODGY_CHARGES)
        access.mes("You break the dodgy necklace and it crumbles to dust.")
    }

    private fun Player.wearsGlovesOfSilence(): Boolean = wears(Wearpos.Hands, GLOVES_OF_SILENCE)

    private fun Player.wears(wearpos: Wearpos, obj: String): Boolean =
        worn[wearpos.slot]?.id == obj.asRSCM(RSCMType.OBJ)

    private fun plural(word: String, count: Int): String = if (count == 1) word else "${word}s"

    companion object {
        const val GLOVES_OF_SILENCE: String = "obj.hunting_silent_gloves"
        const val DODGY_NECKLACE: String = "obj.dodgy_necklace"
        const val THIEVING_CAPE: String = "obj.skillcape_thieving"
        const val THIEVING_CAPE_TRIMMED: String = "obj.skillcape_thieving_trimmed"

        val ROGUE_PIECES: Map<Wearpos, String> =
            mapOf(
                Wearpos.Hat to "obj.roguesden_helm",
                Wearpos.Torso to "obj.roguesden_body",
                Wearpos.Legs to "obj.roguesden_legs",
                Wearpos.Hands to "obj.roguesden_gloves",
                Wearpos.Feet to "obj.roguesden_boots",
            )

        /** Percent chance of double loot per rogue piece worn (the full set is 100%). */
        private const val ROGUE_PIECE_CHANCE: Int = 15

        private const val GLOVES_BONUS: Double = 0.05
        private const val CAPE_BONUS: Double = 0.10

        /** Failed pickpockets a pair of gloves survives. */
        const val GLOVES_LIFETIME: Int = 62

        private const val DODGY_PROTECT_CHANCE: Int = 25
        const val DODGY_MAX_CHARGES: Int = 10

        private val GLOVES_WEAR = AttributeKey<Int>(persistenceKey = "thieving.gloves_of_silence_wear")
        private val DODGY_CHARGES = AttributeKey<Int>(persistenceKey = "thieving.dodgy_necklace_charges")
    }
}
