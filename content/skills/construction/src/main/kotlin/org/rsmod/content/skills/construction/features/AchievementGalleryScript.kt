package org.rsmod.content.skills.construction.features

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.poh.PohManager
import org.rsmod.api.script.onOpLoc2
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Achievement gallery jewellery box: op2 `Teleport Menu` on the built box opens a destination
 * picker whose contents grow with the box tier, per the wiki `Jewellery Box` page:
 * - **Basic** (`loc.poh_jewellery_box_1_base`): games necklace + ring of dueling destinations.
 * - **Fancy** (`loc.poh_jewellery_box_2_base`): adds skills necklace + combat bracelet.
 * - **Ornate** (`loc.poh_jewellery_box_3_base`): adds ring of wealth + amulet of glory.
 *
 * The `_base` children carry the ops; the placed `poh_jewellery_box_1..3` parents are op-less
 * multiloc shells resolved through `varbit` 2308, which defaults to the `_base` child. The
 * per-destination child variants (last-destination shortcuts on op1) are not modelled yet.
 *
 * TODO: Achievement gallery altar-of-the-occult (spellbook swap) and boss lair displays are not
 *   implemented in this pass.
 */
class AchievementGalleryScript @Inject constructor(private val manager: PohManager) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc2(BASIC_BOX) { openJewelleryBox(BASIC_DESTINATIONS) }
        onOpLoc2(FANCY_BOX) { openJewelleryBox(FANCY_DESTINATIONS) }
        onOpLoc2(ORNATE_BOX) { openJewelleryBox(ORNATE_DESTINATIONS) }
    }

    private suspend fun ProtectedAccess.openJewelleryBox(
        destinations: List<Pair<String, CoordGrid>>
    ) {
        if (!manager.isInOwnHouse(player)) {
            mes("You can only use the jewellery box in your own house.")
            return
        }
        val index = menu(MENU_TITLE, hotkeys = true, choices = destinations.map { it.first })
        val destination = destinations.getOrNull(index)?.second ?: return
        arriveDelay()
        telejump(destination)
        mes("You teleport to ${destinations[index].first}.")
    }

    private companion object {
        const val MENU_TITLE = "Jewellery Box"

        const val BASIC_BOX = "loc.poh_jewellery_box_1_base"
        const val FANCY_BOX = "loc.poh_jewellery_box_2_base"
        const val ORNATE_BOX = "loc.poh_jewellery_box_3_base"

        /** Games necklace destinations (wiki landing tiles). */
        val GAMES_NECKLACE =
            listOf(
                "Burthorpe" to CoordGrid(2898, 3553, 0),
                "Barbarian Outpost" to CoordGrid(2520, 3571, 0),
                "Corporeal Beast" to CoordGrid(2965, 4382, 2),
                "Tears of Guthix" to CoordGrid(3244, 9501, 2),
                "Wintertodt Camp" to CoordGrid(1623, 3937, 0),
            )

        /** Ring of dueling destinations. */
        val RING_OF_DUELING =
            listOf(
                "PvP Arena" to CoordGrid(3316, 3235, 0),
                "Ferox Enclave" to CoordGrid(3151, 3635, 0),
                "Castle Wars" to CoordGrid(2440, 3089, 0),
            )

        /** Skills necklace destinations. */
        val SKILLS_NECKLACE =
            listOf(
                "Fishing Guild" to PohTeleportDestinations.FISHING_GUILD,
                "Mining Guild" to CoordGrid(3046, 9756, 0),
                "Crafting Guild" to CoordGrid(2933, 3295, 0),
                "Cooking Guild" to CoordGrid(3143, 3442, 0),
                "Woodcutting Guild" to CoordGrid(1660, 3505, 0),
                "Farming Guild" to CoordGrid(1248, 3718, 0),
            )

        /** Combat bracelet destinations. */
        val COMBAT_BRACELET =
            listOf(
                "Warriors' Guild" to CoordGrid(2882, 3548, 0),
                "Champions' Guild" to CoordGrid(3191, 3363, 0),
                "Edgeville Monastery" to CoordGrid(3052, 3488, 0),
                "Ranging Guild" to CoordGrid(2655, 3441, 0),
            )

        /** Ring of wealth destinations; the Grand Exchange tile matches silo's spell teleport. */
        val RING_OF_WEALTH =
            listOf(
                "Miscellania" to CoordGrid(2535, 3861, 0),
                "Grand Exchange" to PohTeleportDestinations.GRAND_EXCHANGE,
                "Falador Park" to CoordGrid(2995, 3375, 0),
                "Dondakan's Rock" to CoordGrid(2831, 10165, 0),
            )

        /** Amulet of glory destinations. */
        val AMULET_OF_GLORY =
            listOf(
                "Edgeville" to CoordGrid(3087, 3496, 0),
                "Karamja" to CoordGrid(2918, 3176, 0),
                "Draynor Village" to CoordGrid(3105, 3251, 0),
                "Al Kharid" to CoordGrid(3293, 3163, 0),
            )

        val BASIC_DESTINATIONS = GAMES_NECKLACE + RING_OF_DUELING
        val FANCY_DESTINATIONS = BASIC_DESTINATIONS + SKILLS_NECKLACE + COMBAT_BRACELET
        val ORNATE_DESTINATIONS = FANCY_DESTINATIONS + RING_OF_WEALTH + AMULET_OF_GLORY
    }
}
