package org.rsmod.content.skills.construction.features

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.basePrayerLvl
import org.rsmod.api.player.stat.prayerLvl
import org.rsmod.api.poh.PohManager
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.table.prayer.SkillPrayerRow
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Chapel room features: praying at the built altar, offering bones on it, and lighting the incense
 * burners.
 *
 * Bone offerings follow the wiki `Chapel` altar table exactly: each altar tier has a base XP
 * multiplier (oak 1.0x through gilded 2.5x) and every lit incense burner in the chapel adds a
 * further 0.5x, capping at gilded + 2 burners = 3.5x. XP comes from the same `dbtable.skill_prayer`
 * rows the bury and chaos-altar scripts use, so per-bone XP can never drift between the paths. This
 * script owns every POH altar skin and tier, including the gilded skins previously bound at a flat
 * 3.5x by the prayer module's `GildedAltarEvents` (which now handles only the chaos altar).
 *
 * The altar built in the chapel is `loc.poh_altar_saradomin_<tier>`; the zamorak/guthix/gnomechild
 * skins are registered too so a future icon-driven altar swap needs no changes here.
 *
 * Burners are the tier 5-7 lamp builds (`loc.poh_torch_5..7`); lighting one requires a tinderbox
 * and consumes a clean marrentill per the wiki, swapping to the `_lit` variant for a limited burn
 * time. The lit variant's `Re-light` op refuels it for another full duration.
 */
class ChapelScript
@Inject
constructor(
    private val manager: PohManager,
    private val locRepo: LocRepository,
    private val worldRepo: WorldRepository,
) : PluginScript() {
    private val litBurnerIds by lazy { BURNERS.map { it.lit.asRSCM(RSCMType.LOC) }.toSet() }

    override fun ScriptContext.startup() {
        for (altar in ALTARS) {
            onOpLoc1(altar.loc) { prayAtAltar() }
        }

        val bones = SkillPrayerRow.all().filterNot { it.ashes }
        for (altar in ALTARS) {
            for (row in bones) {
                onOpLocU(altar.loc, row.item.internalName) { offerBones(it.vis, row, altar.tier) }
            }
        }

        for (burner in BURNERS) {
            onOpLoc1(burner.unlit) { lightBurner(it.loc, burner.lit, relight = false) }
            onOpLocU(burner.unlit, TINDERBOX) { lightBurner(it.vis, burner.lit, relight = false) }
            onOpLoc1(burner.lit) { lightBurner(it.loc, burner.lit, relight = true) }
        }
    }

    private suspend fun ProtectedAccess.prayAtAltar() {
        arriveDelay()
        anim(PRAY_ANIM)
        delay(2)

        val missing = player.basePrayerLvl - player.prayerLvl
        if (missing > 0) {
            statAdd(PRAYER_STAT, constant = missing, percent = 0)
            mes("You recharge your Prayer points.")
        } else {
            mes("You already have full prayer points.")
        }
    }

    /**
     * Offers [row]'s bone repeatedly until the player runs out, moves away, or interrupts the
     * action. One bone is consumed every [OFFER_INTERVAL] ticks, matching the gilded-altar cadence.
     */
    private suspend fun ProtectedAccess.offerBones(
        altar: BoundLocInfo,
        row: SkillPrayerRow,
        tier: Int,
    ) {
        if (!manager.isInOwnHouse(player)) {
            mes("You can only make offerings at an altar in your own house.")
            return
        }
        arriveDelay()

        val bone = row.item.internalName
        while (inv.count(bone) > 0 && isWithinDistance(altar, 1)) {
            anim(SACRIFICE_ANIM)
            spotanimMap(worldRepo, SACRIFICE_SPOTANIM, altar.coords)

            val result = invDel(inv, bone, 1)
            if (result.failure) {
                return
            }

            val litBurners = countLitBurners(altar)
            val multiplier = TIER_MULTIPLIERS.getValue(tier) + BURNER_BONUS * litBurners
            statAdvance(PRAYER_STAT, row.exp * multiplier)
            mes(offeringMessage(litBurners))

            delay(OFFER_INTERVAL)
        }
    }

    /** Counts lit burner locs in the altar's room zone; a chapel holds at most two burners. */
    private fun countLitBurners(altar: BoundLocInfo): Int {
        val zone = ZoneKey.from(altar.coords)
        return locRepo.findAll(zone).count { it.id in litBurnerIds }.coerceAtMost(MAX_BURNERS)
    }

    private fun offeringMessage(litBurners: Int): String =
        when (litBurners) {
            0 -> "The gods accept your offering."
            1 -> "The gods are pleased with your offering."
            else -> "The gods are very pleased with your offering."
        }

    private suspend fun ProtectedAccess.lightBurner(
        burner: BoundLocInfo,
        litLoc: String,
        relight: Boolean,
    ) {
        if (!manager.isInOwnHouse(player)) {
            mes("You can only do that in your own house.")
            return
        }
        if (invTotal(inv, TINDERBOX) < 1) {
            mes("You need a tinderbox to light the burner.")
            return
        }
        if (invTotal(inv, MARRENTILL) < 1) {
            mes("You need a clean marrentill to burn in the incense burner.")
            return
        }
        arriveDelay()

        val result = invDel(inv, MARRENTILL, 1)
        if (result.failure) {
            return
        }
        anim(LIGHT_ANIM)
        delay(1)

        locRepo.change(burner, litLoc, burnDuration())
        mes(if (relight) "You refuel the incense burner." else "You light the incense burner.")
    }

    private fun burnDuration(): Int = (BURN_TICKS_MIN..BURN_TICKS_MAX).random()

    private data class ChapelAltar(val loc: String, val tier: Int)

    private data class ChapelBurner(val unlit: String, val lit: String)

    private companion object {
        const val PRAYER_STAT = "stat.prayer"
        const val PRAY_ANIM = "seq.human_pray"
        const val SACRIFICE_ANIM = "seq.human_bone_sacrifice"
        const val SACRIFICE_SPOTANIM = "spotanim.poh_bone_sacrifice"
        const val LIGHT_ANIM = "seq.human_createfire"

        const val TINDERBOX = "obj.tinderbox"

        /** Clean marrentill; the cache gameval spells it `marentill`. */
        const val MARRENTILL = "obj.marentill"

        /** Ticks between bone offerings, matching the prayer module's sacrifice queue cadence. */
        const val OFFER_INTERVAL = 4

        /** Extra XP multiplier granted per lit incense burner (wiki: +50% each). */
        const val BURNER_BONUS = 0.5

        const val MAX_BURNERS = 2

        /** A burner stays lit for roughly two minutes plus a random grace period, as on OSRS. */
        const val BURN_TICKS_MIN = 200
        const val BURN_TICKS_MAX = 250

        /** Base bone-offering XP multiplier per altar tier (wiki `Chapel` altar table). */
        val TIER_MULTIPLIERS =
            mapOf(1 to 1.0, 2 to 1.1, 3 to 1.25, 4 to 1.5, 5 to 1.75, 6 to 2.0, 7 to 2.5)

        /** Every altar skin x tier; tier 1 = oak ... tier 7 = gilded. */
        val ALTARS = buildList {
            for (god in listOf("saradomin", "zamorak", "guthix", "gnomechild")) {
                for (tier in 1..7) {
                    add(ChapelAltar("loc.poh_altar_${god}_$tier", tier))
                }
            }
        }

        /** Lamp-space tiers 5-7 are the oak/mahogany/marble incense burners. */
        val BURNERS =
            listOf(
                ChapelBurner("loc.poh_torch_5", "loc.poh_torch_5_lit"),
                ChapelBurner("loc.poh_torch_6", "loc.poh_torch_6_lit"),
                ChapelBurner("loc.poh_torch_7", "loc.poh_torch_7_lit"),
            )
    }
}
