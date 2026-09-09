package org.rsmod.content.skills.construction.features

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.poh.PohManager
import org.rsmod.api.script.onOpLoc1
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Costume room storage: armour case, cape rack, magic wardrobe, toy box, treasure chest and fancy
 * dress box.
 *
 * Item storage is backed by the real cache inventories declared in
 * `.data/raw-cache/server/inv.toml` (`inv.poh_costume_room_armour_inv`,
 * `inv.poh_costume_room_capes_inv`, `inv.poh_costume_room_magic_wardrobe_inv`,
 * `inv.poh_costume_room_holiday_items_inv`, `inv.poh_costume_room_ame_inv` and
 * `inv.poh_costume_room_treasure_trail_0..3_inv`), all `Perm`-scoped so contents persist through
 * the standard inventory persistence path.
 *
 * The real `interface.poh_costumes` / `interface.poh_costumes_side` pair is driven by clientscripts
 * whose wire format was never exercised in the available RSProx captures, so this script falls back
 * to a menu-driven deposit/withdraw dialogue against the same inventories - storage correctness
 * over pixel parity. Wiring the real interface is a TODO once a capture of the costume room UI
 * exists.
 *
 * Storable item lists are a curated starter subset of the OSRS wiki "Costume room" storable lists
 * (wiki pages `Armour case space`, `Cape rack space`, `Magic wardrobe space`, `Toy box space`,
 * `Treasure chest space` and `Fancy dress box space`); the fancy dress box list is complete. Every
 * obj name below resolves against the rev240 gamevals. Treasure chest deposits are tier-gated by
 * the built chest (oak: beginner/easy, teak: +medium, mahogany: all), matching the wiki storage
 * limits; withdrawals are never tier-gated so a downgraded chest can always be emptied. Skillcape
 * count limits per cape rack tier are not enforced (starter set only carries two skillcapes).
 */
class CostumeRoomScript @Inject constructor(private val manager: PohManager) : PluginScript() {
    override fun ScriptContext.startup() {
        for (storage in STORAGE_UNITS) {
            for (loc in storage.builtLocs) {
                onOpLoc1(loc) { onStorageOpen(storage) }
            }
        }
        for ((loc, tierCount) in TREASURE_CHESTS) {
            onOpLoc1(loc) { onTreasureChestOpen(tierCount) }
        }
    }

    private suspend fun ProtectedAccess.onStorageOpen(storage: StorageUnit) {
        if (!verifyOwnHouse()) {
            return
        }
        val deposit =
            choice2("Deposit items.", true, "Withdraw items.", false, title = storage.displayName)
        if (deposit) {
            depositMenu(storage.displayName, listOf(storage.inv to storage.storables))
        } else {
            withdrawMenu(storage.displayName, listOf(storage.inv))
        }
    }

    private suspend fun ProtectedAccess.onTreasureChestOpen(tierCount: Int) {
        if (!verifyOwnHouse()) {
            return
        }
        val deposit =
            choice2("Deposit items.", true, "Withdraw items.", false, title = "Treasure chest")
        if (deposit) {
            val allowed = TREASURE_TIERS.take(tierCount).map { it.inv to it.storables }
            depositMenu("Treasure chest", allowed)
        } else {
            withdrawMenu("Treasure chest", TREASURE_TIERS.map { it.inv })
        }
    }

    /** Storage locs only exist in a house region; owner-only access mirrors live behaviour. */
    private fun ProtectedAccess.verifyOwnHouse(): Boolean = manager.isInOwnHouse(player)

    /**
     * Lists every storable item the player carries across [sections] (an `inv name -> storables`
     * pair per treasure tier; single-section for the other storage types) and deposits the full
     * carried count of the chosen item.
     */
    private suspend fun ProtectedAccess.depositMenu(
        displayName: String,
        sections: List<Pair<String, List<String>>>,
    ) {
        val candidates = buildList {
            for ((invName, storables) in sections) {
                for (obj in storables) {
                    if (invTotal(inv, obj) > 0) {
                        add(invName to obj)
                    }
                }
            }
        }
        if (candidates.isEmpty()) {
            mesbox("You don't have anything that can be stored in the $displayName.")
            return
        }
        val labels = candidates.map { (_, obj) -> itemName(obj) }
        val index = menu("Deposit which item?", hotkeys = false, labels)
        val (invName, obj) = candidates.getOrNull(index) ?: return
        val count = invTotal(inv, obj)
        if (invDel(inv, obj, count).failure) {
            return
        }
        val storage = inv(invName)
        if (invAdd(storage, obj, count).failure) {
            invAdd(inv, obj, count)
            mes("The $displayName is full.")
            return
        }
        mes("You store the ${itemName(obj)} in the $displayName.")
    }

    /** Lists stored items across [invNames] and returns the full stored count of the choice. */
    private suspend fun ProtectedAccess.withdrawMenu(displayName: String, invNames: List<String>) {
        val stored = buildList {
            for (invName in invNames) {
                val storage = inv(invName)
                for (slot in storage) {
                    if (slot != null && slot.count > 0) {
                        val obj = RSCM.getReverseMapping(RSCMType.OBJ, slot.id)
                        add(Triple(storage, obj, slot.count))
                    }
                }
            }
        }
        if (stored.isEmpty()) {
            mesbox("The $displayName is empty.")
            return
        }
        val labels =
            stored.map { (_, obj, count) ->
                if (count > 1) "${itemName(obj)} x$count" else itemName(obj)
            }
        val index = menu("Withdraw which item?", hotkeys = false, labels)
        val (storage, obj, count) = stored.getOrNull(index) ?: return
        if (invAdd(inv, obj, count).failure) {
            mes("You don't have enough inventory space to withdraw that.")
            return
        }
        invDel(storage, obj, count)
        mes("You take the ${itemName(obj)} out of the $displayName.")
    }

    private fun itemName(obj: String): String {
        val item = ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ))
        return item?.name?.takeIf { it.isNotBlank() } ?: obj
    }

    private class StorageUnit(
        val displayName: String,
        val inv: String,
        val builtLocs: List<String>,
        val storables: List<String>,
    )

    private class TreasureTier(val inv: String, val storables: List<String>)

    private companion object {
        /**
         * Non-chest storage units. Built loc names are the closed variants placed by the furniture
         * build flow; the `_open_` loc variants are never spawned by this server, so only the
         * closed locs carry handlers (op1 is `Open` on cases/boxes and `Search` on the cape rack -
         * both open the storage dialogue directly).
         */
        val STORAGE_UNITS =
            listOf(
                StorageUnit(
                    displayName = "armour case",
                    inv = "inv.poh_costume_room_armour_inv",
                    builtLocs =
                        listOf(
                            "loc.poh_cos_room_armour_case_oak",
                            "loc.poh_cos_room_armour_case_teak",
                            "loc.poh_cos_room_armour_case_mahogany",
                        ),
                    // Wiki "Armour case space": Angler outfit, Rogue equipment, Lumberjack outfit.
                    storables =
                        listOf(
                            "obj.trawler_reward_hat",
                            "obj.trawler_reward_top",
                            "obj.trawler_reward_legs",
                            "obj.trawler_reward_boots",
                            "obj.roguesden_helm",
                            "obj.roguesden_body",
                            "obj.roguesden_legs",
                            "obj.roguesden_gloves",
                            "obj.roguesden_boots",
                            "obj.ramble_lumberjack_hat",
                            "obj.ramble_lumberjack_top",
                            "obj.ramble_lumberjack_legs",
                            "obj.ramble_lumberjack_boots",
                        ),
                ),
                StorageUnit(
                    displayName = "cape rack",
                    inv = "inv.poh_costume_room_capes_inv",
                    builtLocs =
                        listOf(
                            "loc.poh_cos_room_cape_rack_oak",
                            "loc.poh_cos_room_cape_rack_teak",
                            "loc.poh_cos_room_cape_rack_mahogany",
                            "loc.poh_cos_room_cape_rack_mahogany_gilded",
                            "loc.poh_cos_room_cape_rack_marble",
                            "loc.poh_cos_room_cape_rack_magic_stone",
                        ),
                    // Wiki "Cape rack space": fire/infernal/obsidian capes, cape of legends and
                    // hood+cape+trimmed skillcape sets (attack and construction as starters).
                    storables =
                        listOf(
                            "obj.tzhaar_cape_fire",
                            "obj.infernal_cape",
                            "obj.tzhaar_cape_obsidian",
                            "obj.cape_of_legends",
                            "obj.skillcape_attack",
                            "obj.skillcape_attack_trimmed",
                            "obj.skillcape_attack_hood",
                            "obj.skillcape_construction",
                            "obj.skillcape_construction_trimmed",
                            "obj.skillcape_construction_hood",
                        ),
                ),
                StorageUnit(
                    displayName = "magic wardrobe",
                    inv = "inv.poh_costume_room_magic_wardrobe_inv",
                    builtLocs =
                        listOf(
                            "loc.poh_cos_room_magic_wardrobe_oak",
                            "loc.poh_cos_room_magic_wardrobe_carved_oak",
                            "loc.poh_cos_room_magic_wardrobe_teak",
                            "loc.poh_cos_room_magic_wardrobe_carved_teak",
                            "loc.poh_cos_room_magic_wardrobe_mahogany",
                            "loc.poh_cos_room_magic_wardrobe_mahogany_gilded",
                            "loc.poh_cos_room_magic_wardrobe_marble",
                        ),
                    // Wiki "Magic wardrobe space": infinity, splitbark, skeletal, dagon'hai and
                    // elder chaos robes.
                    storables =
                        listOf(
                            "obj.magictraining_infinityhat",
                            "obj.magictraining_infinitytop",
                            "obj.magictraining_infinitybottom",
                            "obj.magictraining_infinitygloves",
                            "obj.magictraining_infinityboots",
                            "obj.splitbark_helm",
                            "obj.splitbark_body",
                            "obj.splitbark_legs",
                            "obj.splitbark_gauntlets",
                            "obj.splitbark_greaves",
                            "obj.dagganoth_mage_helm",
                            "obj.dagganoth_mage_body",
                            "obj.dagganoth_mage_legs",
                            "obj.dagganoth_mage_gloves",
                            "obj.dagganoth_mage_feet",
                            "obj.dagonhai_hat",
                            "obj.dagonhai_robe_top",
                            "obj.dagonhai_robe_bottom",
                            "obj.elderchaos_hood",
                            "obj.elderchaos_top",
                            "obj.elderchaos_bottom",
                        ),
                ),
                StorageUnit(
                    displayName = "toy box",
                    inv = "inv.poh_costume_room_holiday_items_inv",
                    builtLocs =
                        listOf(
                            "loc.poh_cos_room_toy_box_oak",
                            "loc.poh_cos_room_toy_box_teak",
                            "loc.poh_cos_room_toy_box_mahogany",
                        ),
                    // Wiki "Toy box space": untradeable holiday rewards.
                    storables =
                        listOf(
                            "obj.xmas_yoyo",
                            "obj.rubber_chicken",
                            "obj.bunnyears",
                            "obj.scythe",
                            "obj.easter06_ring_of_egg",
                            "obj.hw06_pumpkin_head",
                            "obj.hw06_spooky_head",
                            "obj.hw06_spooky_body",
                            "obj.hw06_spooky_legs",
                            "obj.hw06_spooky_gloves",
                            "obj.hw06_spooky_boots",
                            "obj.gub_reindeer_hat",
                            "obj.easter07_chicken_head",
                            "obj.easter07_chicken_wings",
                            "obj.easter07_chicken_legs",
                            "obj.easter07_chicken_feet",
                            "obj.hw07_grim_hood",
                            "obj.win05_marionette_complete_red_alive",
                            "obj.win05_marionette_complete_blue_alive",
                            "obj.win05_marionette_complete_green_alive",
                        ),
                ),
                StorageUnit(
                    displayName = "fancy dress box",
                    inv = "inv.poh_costume_room_ame_inv",
                    builtLocs =
                        listOf(
                            "loc.poh_cos_room_fancy_dress_box_oak",
                            "loc.poh_cos_room_fancy_dress_box_teak",
                            "loc.poh_cos_room_fancy_dress_box_mahogany",
                        ),
                    // Wiki "Fancy dress box space" - complete random event costume list:
                    // beekeeper, camo (drill demon), frog/royal frog, lederhosen, mime,
                    // gravedigger zombie, plus the stale baguette. Shade robes are excluded:
                    // no dedicated random-event shade robe obj resolves in the rev240 gamevals.
                    // TODO: map the fancy dress box shade robes once their obj ids are known.
                    storables =
                        listOf(
                            "obj.beekeeper_hat",
                            "obj.beekeeper_top",
                            "obj.beekeeper_legs",
                            "obj.beekeeper_gloves",
                            "obj.beekeeper_boots",
                            "obj.drill_helm",
                            "obj.drill_top",
                            "obj.drill_bottoms",
                            "obj.macro_frog_mask",
                            "obj.macro_prince_torso",
                            "obj.macro_prince_legs",
                            "obj.macro_princess_torso",
                            "obj.macro_princess_legs",
                            "obj.laderhosen_hat",
                            "obj.laderhosen_top",
                            "obj.laderhosen_legs",
                            "obj.macro_mime_mask",
                            "obj.macro_mime_top",
                            "obj.macro_mime_legs",
                            "obj.macro_mime_gloves",
                            "obj.macro_mime_boots",
                            "obj.macro_digger_mask",
                            "obj.macro_digger_shirt",
                            "obj.macro_digger_legs",
                            "obj.macro_digger_gloves",
                            "obj.macro_digger_boots",
                            "obj.stale_baguette",
                        ),
                ),
            )

        /**
         * Treasure trail tiers in deposit-gate order; index-aligned with the tier inventories.
         * Elite and master rewards are not yet mapped to a tier inventory.
         *
         * TODO: extend with `inv.poh_costume_room_treasure_trail_1a/2a/3a_inv` for elite/master
         *   rewards once their live tier mapping is confirmed.
         */
        val TREASURE_TIERS =
            listOf(
                // Beginner.
                TreasureTier(
                    inv = "inv.poh_costume_room_treasure_trail_0_inv",
                    storables =
                        listOf(
                            "obj.mole_slippers",
                            "obj.frog_slippers",
                            "obj.bear_slippers",
                            "obj.demon_slippers",
                            "obj.jester_cape",
                            "obj.shoulder_parrot",
                            "obj.sandwich_lady_hat",
                            "obj.sandwich_lady_top",
                            "obj.sandwich_lady_bottom",
                        ),
                ),
                // Easy.
                TreasureTier(
                    inv = "inv.poh_costume_room_treasure_trail_1_inv",
                    storables =
                        listOf(
                            "obj.berret_black",
                            "obj.berret_blue",
                            "obj.berret_white",
                            "obj.headband_red",
                            "obj.headband_black",
                            "obj.headband_brown",
                            "obj.highwayman_mask",
                            "obj.trail_amulet_of_magic",
                        ),
                ),
                // Medium.
                TreasureTier(
                    inv = "inv.poh_costume_room_treasure_trail_2_inv",
                    storables = listOf("obj.boots_ranger", "obj.boots_wizard", "obj.holy_sandals"),
                ),
                // Hard.
                TreasureTier(
                    inv = "inv.poh_costume_room_treasure_trail_3_inv",
                    storables =
                        listOf(
                            "obj.robinhoodhat",
                            "obj.trail_ranger_coif",
                            "obj.trail_ranger_torso",
                            "obj.trail_ranger_legs",
                            "obj.trail_ranger_vambraces",
                            "obj.trail_armadyl_chaps",
                        ),
                ),
            )

        /** Built chest loc -> number of [TREASURE_TIERS] it accepts deposits for. */
        val TREASURE_CHESTS =
            listOf(
                "loc.poh_cos_room_tresure_chest_oak" to 2,
                "loc.poh_cos_room_tresure_chest_teak" to 3,
                "loc.poh_cos_room_tresure_chest_mahogany" to 4,
            )
    }
}
