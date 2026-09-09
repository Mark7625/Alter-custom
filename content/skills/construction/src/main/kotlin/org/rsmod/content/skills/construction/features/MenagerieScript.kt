package org.rsmod.content.skills.construction.features

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.poh.PohManager
import org.rsmod.api.script.onOpLoc1
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Menagerie pet storage on the built pet house (and pet list) locs.
 *
 * Pet storage state lives in the real menagerie varbits, so stored pets persist through the
 * standard varp persistence path and stay compatible with the client's own menagerie clientscripts:
 * - One 1-bit *stored* flag per pet: `varbit.poh_menagerie_<pet>` for the classic pets and
 *   `varbit.pet_menagerie_<pet>` for every pet released after the naming switch (verified per entry
 *   below; all resolve as 1-bit varbits in the rev240 cache).
 * - Pets with cosmetic variants additionally carry a `varbit.poh_menagerie_multiform_*` *form*
 *   varbit whose value is the 0-based index of the stored form. Every form list below fits its
 *   varbit's verified bit width.
 *
 * The follower/pet system is not implemented in silo yet (see `EquipmentTabScript`'s "Call
 * follower" TODO), so storage works against the pet *items* the player carries: storing a pet
 * consumes its inventory item and sets the flag (and form) varbits; withdrawing gives the item back
 * and clears the flag. TODO: once followers exist, also allow storing the currently-following pet.
 *
 * `interface.poh_menagerie` (211) / `interface.poh_petlist` (210) are clientscript-driven and no
 * capture of their wire format exists, so interaction is a menu dialogue instead.
 *
 * TODO: open the real interface once a capture pins its open/refresh protocol; the varbits this
 *   script writes are the same ones those interfaces read.
 */
class MenagerieScript @Inject constructor(private val manager: PohManager) : PluginScript() {
    override fun ScriptContext.startup() {
        for (loc in PET_STORAGE_LOCS) {
            onOpLoc1(loc) { onPetStorage() }
        }
    }

    private suspend fun ProtectedAccess.onPetStorage() {
        if (!manager.isInOwnHouse(player)) {
            return
        }
        val store = choice2("Store a pet.", true, "Withdraw a pet.", false, title = "Pet house")
        if (store) {
            storeMenu()
        } else {
            withdrawMenu()
        }
    }

    /** Lists carried pet items whose pet is not already stored; stores the chosen one. */
    private suspend fun ProtectedAccess.storeMenu() {
        val candidates = buildList {
            for (pet in STORABLE_PETS) {
                if (vars[pet.storedVarbit] != 0) {
                    continue
                }
                for ((formIndex, form) in pet.forms.withIndex()) {
                    if (invTotal(inv, form) > 0) {
                        add(Triple(pet, formIndex, form))
                        break
                    }
                }
            }
        }
        if (candidates.isEmpty()) {
            mesbox("You don't have any pets that can be stored here.")
            return
        }
        val labels = candidates.map { (_, _, form) -> itemName(form) }
        val index = menu("Store which pet?", hotkeys = false, labels)
        val (pet, formIndex, form) = candidates.getOrNull(index) ?: return
        if (invDel(inv, form, count = 1).failure) {
            return
        }
        vars[pet.storedVarbit] = 1
        pet.formVarbit?.let { vars[it] = formIndex }
        mes("You put the ${itemName(form)} into the pet house.")
    }

    /** Lists stored pets (flag varbit set) and gives the stored form's item back. */
    private suspend fun ProtectedAccess.withdrawMenu() {
        val stored = buildList {
            for (pet in STORABLE_PETS) {
                if (vars[pet.storedVarbit] != 1) {
                    continue
                }
                val formIndex = pet.formVarbit?.let { vars[it] } ?: 0
                add(pet to pet.forms[formIndex.coerceIn(0, pet.forms.size - 1)])
            }
        }
        if (stored.isEmpty()) {
            mesbox("You don't have any pets stored here.")
            return
        }
        val labels = stored.map { (_, form) -> itemName(form) }
        val index = menu("Withdraw which pet?", hotkeys = false, labels)
        val (pet, form) = stored.getOrNull(index) ?: return
        if (invAdd(inv, form, count = 1).failure) {
            mes("You don't have enough inventory space to withdraw that.")
            return
        }
        vars[pet.storedVarbit] = 0
        pet.formVarbit?.let { vars[it] = 0 }
        mes("You take the ${itemName(form)} out of the pet house.")
    }

    private fun itemName(obj: String): String {
        val item = ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ))
        return item?.name?.takeIf { it.isNotBlank() } ?: obj
    }

    /**
     * A storable pet: its 1-bit stored flag, an optional form varbit, and the pet item obj per form
     * (index-aligned with the form varbit's value).
     */
    private class StorablePet(
        val storedVarbit: String,
        val formVarbit: String?,
        val forms: List<String>,
    )

    private companion object {
        /**
         * Built pet house locs (op1 `View`) plus the pet list. Every name resolves in the rev240
         * loc gamevals (26297-26299, 26830-26832 and 26868).
         */
        val PET_STORAGE_LOCS =
            listOf(
                "loc.poh_menagerie_pethouse_1",
                "loc.poh_menagerie_pethouse_2",
                "loc.poh_menagerie_pethouse_3",
                "loc.poh_menagerie_pethouse_4",
                "loc.poh_menagerie_pethouse_5",
                "loc.poh_menagerie_pethouse_6",
                "loc.poh_menagerie_petlist_1",
            )

        private fun pet(stored: String, vararg forms: String) =
            StorablePet(stored, formVarbit = null, forms.toList())

        private fun multiformPet(stored: String, form: String, vararg forms: String) =
            StorablePet(stored, form, forms.toList())

        /**
         * The pet item obj <-> varbit table, built from the rev240 `varbit.poh_menagerie_*` and
         * `varbit.pet_menagerie_*` gamevals. Every varbit and obj name below was verified against
         * the merged gameval mappings and the SERVER cache varbit definitions.
         *
         * Not covered:
         * - `varbit.pet_menagerie_skillpetsailing`: the sailing skill pet item obj is not yet
         *   verifiable in this revision's gamevals. TODO: map once the item ships.
         * - `varbit.poh_menagerie_closed`, `poh_menagerie2_overview`, `poh_menagerie3_overview`:
         *   interface state, not pets.
         */
        val STORABLE_PETS =
            listOf(
                // Classic single-form boss pets (poh_menagerie_* stored flags).
                pet("varbit.poh_menagerie_chaoselepet", "obj.chaoselepet"),
                pet("varbit.poh_menagerie_supremepet", "obj.supremepet"),
                pet("varbit.poh_menagerie_primepet", "obj.primepet"),
                pet("varbit.poh_menagerie_rexpet", "obj.rexpet"),
                pet("varbit.poh_menagerie_penancepet", "obj.penancepet"),
                pet("varbit.poh_menagerie_armadylpet", "obj.armadylpet"),
                pet("varbit.poh_menagerie_bandospet", "obj.bandospet"),
                pet("varbit.poh_menagerie_saradominpet", "obj.saradominpet"),
                pet("varbit.poh_menagerie_zamorakpet", "obj.zamorakpet"),
                pet("varbit.poh_menagerie_kbdpet", "obj.kbdpet"),
                pet("varbit.poh_menagerie_krakenpet", "obj.krakenpet"),
                pet("varbit.poh_menagerie_chompypet", "obj.chompybird_pet"),
                pet("varbit.poh_menagerie_scorpiapet", "obj.scorpia_pet"),
                pet("varbit.poh_menagerie_hellpet", "obj.hell_pet"),
                pet("varbit.poh_menagerie_zalcano", "obj.zalcanopet"),
                // Classic pets with a multiform twin: stored flag + 0-based form varbit.
                multiformPet(
                    "varbit.poh_menagerie_molepet",
                    "varbit.poh_menagerie_multiform_molepet",
                    "obj.molepet",
                    "obj.molepet_naked",
                ),
                multiformPet(
                    "varbit.poh_menagerie_kqpet",
                    "varbit.poh_menagerie_multiform_kqpet",
                    "obj.kqpet_walking",
                    "obj.kqpet_flying",
                ),
                multiformPet(
                    "varbit.poh_menagerie_smokepet",
                    "varbit.poh_menagerie_multiform_smokepet",
                    "obj.smokepet",
                    "obj.smokepet_old",
                ),
                multiformPet(
                    "varbit.poh_menagerie_snakepet",
                    "varbit.poh_menagerie_multiform_snakepet",
                    "obj.snakepet",
                    "obj.snakepet_orange",
                    "obj.snakepet_blue",
                ),
                multiformPet(
                    "varbit.poh_menagerie_venenatispet",
                    "varbit.poh_menagerie_multiform_venenatispet",
                    "obj.venenatis_pet",
                    "obj.venenatis_pet_legacy",
                ),
                multiformPet(
                    "varbit.poh_menagerie_callistopet",
                    "varbit.poh_menagerie_multiform_callistopet",
                    "obj.callisto_pet",
                    "obj.callisto_pet_legacy",
                ),
                multiformPet(
                    "varbit.poh_menagerie_vetionpet",
                    "varbit.poh_menagerie_multiform_vetionpet",
                    "obj.vetion_pet",
                    "obj.vetion_pet2",
                    "obj.vetion_pet_legacy",
                    "obj.vetion_pet2_legacy",
                ),
                // The dark core / corporeal critter share one pet slot: `corepet` is the stored
                // flag, the `corppet` multiform varbit picks the displayed form.
                multiformPet(
                    "varbit.poh_menagerie_corepet",
                    "varbit.poh_menagerie_multiform_corppet",
                    "obj.corepet",
                    "obj.corppet",
                ),
                multiformPet(
                    "varbit.poh_menagerie_jadpet",
                    "varbit.poh_menagerie_multiform_jadpet",
                    "obj.jad_pet",
                    "obj.jad_pet_inferno",
                ),
                multiformPet(
                    "varbit.poh_menagerie_sarachnispet",
                    "varbit.poh_menagerie_multiform_sarachnispet",
                    "obj.sarachnispet",
                    "obj.sarachnispet_orange",
                    "obj.sarachnispet_blue",
                ),
                // Newer boss pets (pet_menagerie_* stored flags).
                multiformPet(
                    "varbit.pet_menagerie_gargboss",
                    "varbit.poh_menagerie_multiform_gargboss",
                    "obj.dawnpet",
                    "obj.duskpet",
                ),
                multiformPet(
                    "varbit.pet_menagerie_inferno",
                    "varbit.poh_menagerie_multiform_infernopet",
                    "obj.infernopet",
                    "obj.infernopet_zuk",
                ),
                pet("varbit.pet_menagerie_olm", "obj.olmpet"),
                multiformPet(
                    "varbit.pet_menagerie_hydra",
                    "varbit.poh_menagerie_multiform_hydrapet",
                    "obj.hydrapet",
                    "obj.hydrapet_electric",
                    "obj.hydrapet_fire",
                    "obj.hydrapet_extinguished",
                ),
                multiformPet(
                    "varbit.pet_menagerie_gauntlet",
                    "varbit.poh_menagerie_multiform_gauntletpet",
                    "obj.gauntletpet",
                    "obj.gauntletpet_corrupt",
                ),
                multiformPet(
                    "varbit.pet_menagerie_phoenix",
                    "varbit.poh_menagerie_mutliform_phoenixpet",
                    "obj.phoenixpet",
                    "obj.phoenixpet_green",
                    "obj.phoenixpet_blue",
                    "obj.phoenixpet_white",
                    "obj.phoenixpet_purple",
                ),
                multiformPet(
                    "varbit.pet_menagerie_soulwars",
                    "varbit.poh_menagerie_multiform_soulwarspet",
                    "obj.soulwarspet_red",
                    "obj.soulwarspet_blue",
                ),
                multiformPet(
                    "varbit.pet_menagerie_nightmare",
                    "varbit.poh_menagerie_multiform_nightmarepet",
                    "obj.nightmarepet",
                    "obj.nightmarepet_parasite",
                ),
                pet("varbit.pet_menagerie_verzik", "obj.verzikpet"),
                multiformPet(
                    "varbit.pet_menagerie_wardens",
                    "varbit.poh_menagerie_multiform_wardenpet",
                    "obj.wardenpet_tumeken",
                    "obj.wardenpet_elidinis",
                    "obj.wardenpet_akkha",
                    "obj.wardenpet_baba",
                    "obj.wardenpet_kephri",
                    "obj.wardenpet_zebak",
                ),
                multiformPet(
                    "varbit.pet_menagerie_muspah",
                    "varbit.poh_menagerie_multiform_muspahpet",
                    "obj.muspahpet",
                    "obj.muspahpet_melee",
                    "obj.muspahpet_shielded",
                ),
                multiformPet(
                    "varbit.pet_menagerie_araxxor",
                    "varbit.poh_menagerie_multiform_araxxorpet",
                    "obj.araxxorpet",
                    "obj.araxxorpet_cute",
                ),
                // TODO: only Branda's item (`obj.rtbrandapet`) resolves for the Royal Titans pet;
                //  add Eldric's form if a matching obj exists in a later revision.
                multiformPet(
                    "varbit.pet_menagerie_royaltitans",
                    "varbit.poh_menagerie_multiform_royaltitanpet",
                    "obj.rtbrandapet",
                ),
                multiformPet(
                    "varbit.pet_menagerie_gryphon",
                    "varbit.poh_menagerie_multiform_gryphonbosspet",
                    "obj.gryphonbosspet",
                    "obj.gryphonbosspet_adult",
                ),
                pet("varbit.pet_menagerie_abyssalsirepet", "obj.abyssalsire_pet"),
                pet("varbit.pet_menagerie_bloodhound", "obj.bloodhound_pet"),
                pet("varbit.pet_menagerie_herbiboar", "obj.herbiboarpet"),
                pet("varbit.pet_menagerie_nex", "obj.nexpet"),
                pet("varbit.pet_menagerie_scurrius", "obj.scurriuspet"),
                pet("varbit.pet_menagerie_skotus", "obj.skotizopet"),
                pet("varbit.pet_menagerie_tempoross", "obj.temporosspet"),
                pet("varbit.pet_menagerie_vorki", "obj.vorkathpet"),
                pet("varbit.pet_menagerie_duke_sucellus", "obj.dukesucelluspet"),
                pet("varbit.pet_menagerie_leviathan", "obj.leviathanpet"),
                pet("varbit.pet_menagerie_vardorvis", "obj.vardorvispet"),
                pet("varbit.pet_menagerie_whisperer", "obj.whispererpet"),
                pet("varbit.pet_menagerie_amoxliatl", "obj.amoxliatlpet"),
                pet("varbit.pet_menagerie_huey", "obj.hueypet"),
                pet("varbit.pet_menagerie_yama", "obj.yamapet"),
                pet("varbit.pet_menagerie_solheredit", "obj.solhereditpet"),
                pet("varbit.pet_menagerie_quetzal", "obj.quetzalpet"),
                pet("varbit.pet_menagerie_dom", "obj.dompet"),
                pet("varbit.pet_menagerie_abyssal", "obj.abyssalpet"),
                // Skilling pets.
                multiformPet(
                    "varbit.pet_menagerie_skillpethunter",
                    "varbit.poh_menagerie_multiform_skillpethunter",
                    "obj.skillpethunter_grey",
                    "obj.skillpethunter_red",
                    "obj.skillpethunter_black",
                    "obj.skillpethunter_gold",
                ),
                multiformPet(
                    "varbit.pet_menagerie_skillpetrunecraft",
                    "varbit.poh_menagerie_multiform_skillpetrunecrafting",
                    "obj.skillpetrunecrafting_air",
                    "obj.skillpetrunecrafting_mind",
                    "obj.skillpetrunecrafting_water",
                    "obj.skillpetrunecrafting_earth",
                    "obj.skillpetrunecrafting_fire",
                    "obj.skillpetrunecrafting_body",
                    "obj.skillpetrunecrafting_cosmic",
                    "obj.skillpetrunecrafting_chaos",
                    "obj.skillpetrunecrafting_nature",
                    "obj.skillpetrunecrafting_law",
                    "obj.skillpetrunecrafting_death",
                    "obj.skillpetrunecrafting_soul",
                    "obj.skillpetrunecrafting_astral",
                    "obj.skillpetrunecrafting_blood",
                    "obj.skillpetrunecrafting_wrath",
                    "obj.skillpetrunecrafting_gotr",
                ),
                multiformPet(
                    "varbit.pet_menagerie_skillpetmining",
                    "varbit.poh_menagerie_multiform_skillpetmining",
                    "obj.skillpetmining",
                    "obj.skillpetmining_tin",
                    "obj.skillpetmining_copper",
                    "obj.skillpetmining_iron",
                    "obj.skillpetmining_blurite",
                    "obj.skillpetmining_silver",
                    "obj.skillpetmining_coal",
                    "obj.skillpetmining_gold",
                    "obj.skillpetmining_mithril",
                    "obj.skillpetmining_granite",
                    "obj.skillpetmining_adamantite",
                    "obj.skillpetmining_runite",
                    "obj.skillpetmining_amethyst",
                    "obj.skillpetmining_lovakite",
                    "obj.skillpetmining_elemental",
                    "obj.skillpetmining_daeyalt",
                    "obj.skillpetmining_lead",
                    "obj.skillpetmining_rubium",
                    "obj.skillpetmining_nickel",
                ),
                multiformPet(
                    "varbit.pet_menagerie_skillpetwc",
                    "varbit.poh_menagerie_multiform_skillpetwoodcutting",
                    "obj.skillpetwc",
                    "obj.skillpet_wc_oak",
                    "obj.skillpet_wc_willow",
                    "obj.skillpet_wc_maple",
                    "obj.skillpet_wc_yew",
                    "obj.skillpet_wc_magic",
                    "obj.skillpet_wc_redwood",
                    "obj.skillpet_wc_mahogany",
                    "obj.skillpet_wc_teak",
                    "obj.skillpet_wc_arctic",
                    "obj.skillpet_wc_pheasant",
                    "obj.skillpet_wc_fox",
                    "obj.skillpet_wc_camphor",
                    "obj.skillpet_wc_ironwood",
                    "obj.skillpet_wc_jatoba",
                    "obj.skillpet_wc_rosewood",
                ),
                multiformPet(
                    "varbit.pet_menagerie_skillpetfish",
                    "varbit.poh_menagerie_multiform_skillpetfishing",
                    "obj.skillpetfish",
                    "obj.skillpetfish_tempoross",
                ),
                multiformPet(
                    "varbit.pet_menagerie_skillpetfarming",
                    "varbit.poh_menagerie_mutliform_farmingpet",
                    "obj.skillpetfarming",
                    "obj.skillpetfarming_crystal",
                    "obj.skillpetfarming_dragon",
                    "obj.skillpetfarming_herb",
                    "obj.skillpetfarming_lily",
                    "obj.skillpetfarming_redwood",
                ),
                multiformPet(
                    "varbit.pet_menagerie_skillpetthieving",
                    "varbit.poh_menagerie_multiform_thievingpet",
                    "obj.skillpetthieving",
                    "obj.skillpetthieving_panda",
                    "obj.skillpetthieving_tanuki",
                ),
                multiformPet(
                    "varbit.pet_menagerie_skillpetagility",
                    "varbit.poh_menagerie_multiform_skillpetagility_bigger",
                    "obj.skillpetagility",
                    "obj.skillpetagility_dark",
                    "obj.skillpetagility_bone",
                ),
            )
    }
}
