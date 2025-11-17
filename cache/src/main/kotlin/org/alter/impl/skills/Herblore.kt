package org.alter.impl.skills

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType

object Herblore {

    // Unfinished potions table columns (in order of definition)
    const val COL_HERB_ITEM = 0
    const val COL_LEVEL = 1
    const val COL_XP = 2
    const val COL_UNFINISHED_POTION = 3

    // Finished potions table columns (in order of definition)
    const val COL_POT_PRIMARY = 0
    const val COL_SECONDARIES = 1
    const val COL_LEVEL_REQUIRED = 2
    const val COL_XP_FINISHED = 3
    const val COL_FINISHED_POTION = 4

    // Cleaning herbs table columns (in order of definition)
    const val COL_GRIMY_HERB = 0
    const val COL_CLEAN_LEVEL = 1
    const val COL_CLEAN_XP = 2
    const val COL_CLEAN_HERB = 3

    // Barbarian mixes table columns (in order of definition)
    const val COL_TWO_DOSE_POTION = 0
    const val COL_MIX_INGREDIENT = 1
    const val COL_MIX_LEVEL = 2
    const val COL_MIX_XP = 3
    const val COL_BARBARIAN_MIX = 4

    // Swamp tar table columns (in order of definition)
    const val COL_TAR_HERB = 0
    const val COL_TAR_LEVEL = 1
    const val COL_TAR_XP = 2
    const val COL_TAR_FINISHED = 3

    // Crushing table columns (in order of definition)
    const val COL_CRUSH_ITEM = 0
    const val COL_CRUSH_LEVEL = 1
    const val COL_CRUSH_XP = 2
    const val COL_CRUSHED_ITEM = 3

    /**
     * Table for creating unfinished potions (herb + vial of water)
     */
    fun unfinishedPotions() = dbTable("tables.herblore_unfinished") {
        column("herb_item", COL_HERB_ITEM, VarType.OBJ)
        column("level", COL_LEVEL, VarType.INT)
        column("xp", COL_XP, VarType.INT)
        column("unfinished_potion", COL_UNFINISHED_POTION, VarType.OBJ)

        // Guam leaf
        row("dbrows.herblore_guam_unfinished") {
            columnRSCM(COL_HERB_ITEM, "items.guam_leaf")
            column(COL_LEVEL, 3)
            column(COL_XP, 2)
            columnRSCM(COL_UNFINISHED_POTION, "items.guamvial")
        }

        // Marrentill
        row("dbrows.herblore_marrentill_unfinished") {
            columnRSCM(COL_HERB_ITEM, "items.marentill")
            column(COL_LEVEL, 5)
            column(COL_XP, 3)
            columnRSCM(COL_UNFINISHED_POTION, "items.marrentillvial")
        }

        // Tarromin
        row("dbrows.herblore_tarromin_unfinished") {
            columnRSCM(COL_HERB_ITEM, "items.tarromin")
            column(COL_LEVEL, 11)
            column(COL_XP, 5)
            columnRSCM(COL_UNFINISHED_POTION, "items.tarrominvial")
        }

        // Harralander
        row("dbrows.herblore_harralander_unfinished") {
            columnRSCM(COL_HERB_ITEM, "items.harralander")
            column(COL_LEVEL, 20)
            column(COL_XP, 6)
            columnRSCM(COL_UNFINISHED_POTION, "items.harralandervial")
        }

        // Ranarr weed
        row("dbrows.herblore_ranarr_unfinished") {
            columnRSCM(COL_HERB_ITEM, "items.ranarr_weed")
            column(COL_LEVEL, 25)
            column(COL_XP, 8)
            columnRSCM(COL_UNFINISHED_POTION, "items.ranarrvial")
        }

        // Toadflax
        row("dbrows.herblore_toadflax_unfinished") {
            columnRSCM(COL_HERB_ITEM, "items.toadflax")
            column(COL_LEVEL, 30)
            column(COL_XP, 8)
            columnRSCM(COL_UNFINISHED_POTION, "items.toadflaxvial")
        }

        // Irit leaf
        row("dbrows.herblore_irit_unfinished") {
            columnRSCM(COL_HERB_ITEM, "items.irit_leaf")
            column(COL_LEVEL, 40)
            column(COL_XP, 9)
            columnRSCM(COL_UNFINISHED_POTION, "items.iritvial")
        }

        // Avantoe
        row("dbrows.herblore_avantoe_unfinished") {
            columnRSCM(COL_HERB_ITEM, "items.avantoe")
            column(COL_LEVEL, 48)
            column(COL_XP, 10)
            columnRSCM(COL_UNFINISHED_POTION, "items.avantoevial")
        }

        // Kwuarm
        row("dbrows.herblore_kwuarm_unfinished") {
            columnRSCM(COL_HERB_ITEM, "items.kwuarm")
            column(COL_LEVEL, 54)
            column(COL_XP, 11)
            columnRSCM(COL_UNFINISHED_POTION, "items.kwuarmvial")
        }

        // Snapdragon
        row("dbrows.herblore_snapdragon_unfinished") {
            columnRSCM(COL_HERB_ITEM, "items.snapdragon")
            column(COL_LEVEL, 59)
            column(COL_XP, 12)
            columnRSCM(COL_UNFINISHED_POTION, "items.snapdragonvial")
        }

        // Cadantine
        row("dbrows.herblore_cadantine_unfinished") {
            columnRSCM(COL_HERB_ITEM, "items.cadantine")
            column(COL_LEVEL, 65)
            column(COL_XP, 13)
            columnRSCM(COL_UNFINISHED_POTION, "items.cadantinevial")
        }

        // Lantadyme
        row("dbrows.herblore_lantadyme_unfinished") {
            columnRSCM(COL_HERB_ITEM, "items.lantadyme")
            column(COL_LEVEL, 67)
            column(COL_XP, 13)
            columnRSCM(COL_UNFINISHED_POTION, "items.lantadymevial")
        }

        // Dwarf weed
        row("dbrows.herblore_dwarf_weed_unfinished") {
            columnRSCM(COL_HERB_ITEM, "items.dwarf_weed")
            column(COL_LEVEL, 70)
            column(COL_XP, 13)
            columnRSCM(COL_UNFINISHED_POTION, "items.dwarfweedvial")
        }

        // Torstol
        row("dbrows.herblore_torstol_unfinished") {
            columnRSCM(COL_HERB_ITEM, "items.torstol")
            column(COL_LEVEL, 75)
            column(COL_XP, 14)
            columnRSCM(COL_UNFINISHED_POTION, "items.torstolvial")
        }
    }

    /**
     * Table for creating finished potions (unfinished potion + secondary ingredient)
     * Supports multi-step potions with additional ingredients
     */
    fun finishedPotions() = dbTable("tables.herblore_finished") {
        column("pot_primary", COL_POT_PRIMARY, VarType.OBJ)
        column("secondaries", COL_SECONDARIES, VarType.OBJ)
        column("level_required", COL_LEVEL_REQUIRED, VarType.INT)
        column("xp", COL_XP_FINISHED, VarType.INT)
        column("finished_potion", COL_FINISHED_POTION, VarType.OBJ)

        // Attack potion (Guam + Eye of newt)
        row("dbrows.herblore_attack_potion") {
            columnRSCM(COL_POT_PRIMARY, "items.guamvial")
            columnRSCM(COL_SECONDARIES, "items.eye_of_newt","items.dttd_follower_obj")
            column(COL_LEVEL_REQUIRED, 3)
            column(COL_XP_FINISHED, 25)
            columnRSCM(COL_FINISHED_POTION, "items.3dose1attack")
        }

        // Antipoison (Marrentill + Unicorn horn dust)
        row("dbrows.herblore_antipoison") {
            columnRSCM(COL_POT_PRIMARY, "items.marrentillvial")
            columnRSCM(COL_SECONDARIES, "items.unicorn_horn_dust")
            column(COL_LEVEL_REQUIRED, 5)
            column(COL_XP_FINISHED, 38)
            columnRSCM(COL_FINISHED_POTION, "items.3doseantipoison")
        }

        // Strength potion (Tarromin + Limpwurt root)
        row("dbrows.herblore_strength_potion") {
            columnRSCM(COL_POT_PRIMARY, "items.tarrominvial")
            columnRSCM(COL_SECONDARIES, "items.limpwurt_root")
            column(COL_LEVEL_REQUIRED, 12)
            column(COL_XP_FINISHED, 50)
            columnRSCM(COL_FINISHED_POTION, "items.3dose1strength")
        }

        // Restore potion (Harralander + Red spiders' eggs)
        row("dbrows.herblore_restore_potion") {
            columnRSCM(COL_POT_PRIMARY, "items.harralandervial")
            columnRSCM(COL_SECONDARIES, "items.red_spiders_eggs")
            column(COL_LEVEL_REQUIRED, 22)
            column(COL_XP_FINISHED, 63)
            columnRSCM(COL_FINISHED_POTION, "items.3dosestatrestore")
        }

        // Energy potion (Harralander + Chocolate dust)
        row("dbrows.herblore_energy_potion") {
            columnRSCM(COL_POT_PRIMARY, "items.harralandervial")
            columnRSCM(COL_SECONDARIES, "items.chocolate_dust")
            column(COL_LEVEL_REQUIRED, 26)
            column(COL_XP_FINISHED, 68)
            columnRSCM(COL_FINISHED_POTION, "items.3dose1energy")
        }

        // Prayer potion (Ranarr + Snape grass)
        row("dbrows.herblore_prayer_potion") {
            columnRSCM(COL_POT_PRIMARY, "items.ranarrvial")
            columnRSCM(COL_SECONDARIES, "items.snape_grass")
            column(COL_LEVEL_REQUIRED, 38)
            column(COL_XP_FINISHED, 88)
            columnRSCM(COL_FINISHED_POTION, "items.3doseprayerrestore")
        }

        // Super attack (Irit + Eye of newt)
        row("dbrows.herblore_super_attack") {
            columnRSCM(COL_POT_PRIMARY, "items.iritvial")
            columnRSCM(COL_SECONDARIES, "items.eye_of_newt")
            column(COL_LEVEL_REQUIRED, 45)
            column(COL_XP_FINISHED, 100)
            columnRSCM(COL_FINISHED_POTION, "items.3dose2attack")
        }

        // Superantipoison (Irit + Unicorn horn dust)
        row("dbrows.herblore_superantipoison") {
            columnRSCM(COL_POT_PRIMARY, "items.iritvial")
            columnRSCM(COL_SECONDARIES, "items.unicorn_horn_dust")
            column(COL_LEVEL_REQUIRED, 48)
            column(COL_XP_FINISHED, 105)
            columnRSCM(COL_FINISHED_POTION, "items.3dose2antipoison")
        }

        // Fishing potion (Avantoe + Snape grass)
        row("dbrows.herblore_fishing_potion") {
            columnRSCM(COL_POT_PRIMARY, "items.avantoevial")
            columnRSCM(COL_SECONDARIES, "items.snape_grass")
            column(COL_LEVEL_REQUIRED, 50)
            column(COL_XP_FINISHED, 113)
            columnRSCM(COL_FINISHED_POTION, "items.3dosefisherspotion")
        }

        // Super energy (Avantoe + Mort myre fungus)
        row("dbrows.herblore_super_energy") {
            columnRSCM(COL_POT_PRIMARY, "items.avantoevial")
            columnRSCM(COL_SECONDARIES, "items.mortmyremushroom")
            column(COL_LEVEL_REQUIRED, 52)
            column(COL_XP_FINISHED, 118)
            columnRSCM(COL_FINISHED_POTION, "items.3dose2energy")
        }

        // Super strength (Kwuarm + Limpwurt root)
        row("dbrows.herblore_super_strength") {
            columnRSCM(COL_POT_PRIMARY, "items.kwuarmvial")
            columnRSCM(COL_SECONDARIES, "items.limpwurt_root")
            column(COL_LEVEL_REQUIRED, 55)
            column(COL_XP_FINISHED, 125)
            columnRSCM(COL_FINISHED_POTION, "items.3dose2strength")
        }

        // Weapon poison (Kwuarm + Dragon scale dust)
        row("dbrows.herblore_weapon_poison") {
            columnRSCM(COL_POT_PRIMARY, "items.kwuarmvial")
            columnRSCM(COL_SECONDARIES, "items.dragon_scale_dust")
            column(COL_LEVEL_REQUIRED, 60)
            column(COL_XP_FINISHED, 138)
            columnRSCM(COL_FINISHED_POTION, "items.weapon_poison")
        }

        // Super restore (Snapdragon + Red spiders' eggs)
        row("dbrows.herblore_super_restore") {
            columnRSCM(COL_POT_PRIMARY, "items.snapdragonvial")
            columnRSCM(COL_SECONDARIES, "items.red_spiders_eggs")
            column(COL_LEVEL_REQUIRED, 63)
            column(COL_XP_FINISHED, 143)
            columnRSCM(COL_FINISHED_POTION, "items.3dose2restore")
        }

        // Super defence (Cadantine + White berries)
        row("dbrows.herblore_super_defence") {
            columnRSCM(COL_POT_PRIMARY, "items.cadantinevial")
            columnRSCM(COL_SECONDARIES, "items.white_berries")
            column(COL_LEVEL_REQUIRED, 66)
            column(COL_XP_FINISHED, 150)
            columnRSCM(COL_FINISHED_POTION, "items.3dose2defense")
        }

        // Antifire (Lantadyme + Dragon scale dust)
        row("dbrows.herblore_antifire") {
            columnRSCM(COL_POT_PRIMARY, "items.lantadymevial")
            columnRSCM(COL_SECONDARIES, "items.dragon_scale_dust")
            column(COL_LEVEL_REQUIRED, 69)
            column(COL_XP_FINISHED, 158)
            columnRSCM(COL_FINISHED_POTION, "items.3dose1antidragon")
        }

        // Super antifire (Lantadyme + Crushed superior dragon bones)
        row("dbrows.herblore_super_antifire") {
            columnRSCM(COL_POT_PRIMARY, "items.lantadymevial")
            columnRSCM(COL_SECONDARIES, "items.crushed_dragon_bones")
            column(COL_LEVEL_REQUIRED, 92)
            column(COL_XP_FINISHED, 180)
            columnRSCM(COL_FINISHED_POTION, "items.3dose2antidragon")
        }

        // Ranging potion (Dwarf weed + Wine of zamorak)
        row("dbrows.herblore_ranging_potion") {
            columnRSCM(COL_POT_PRIMARY, "items.dwarfweedvial")
            columnRSCM(COL_SECONDARIES, "items.wine_of_zamorak")
            column(COL_LEVEL_REQUIRED, 72)
            column(COL_XP_FINISHED, 163)
            columnRSCM(COL_FINISHED_POTION, "items.3doserangerspotion")
        }

        // Magic potion (Lantadyme + Potato cactus)
        row("dbrows.herblore_magic_potion") {
            columnRSCM(COL_POT_PRIMARY, "items.lantadymevial")
            columnRSCM(COL_SECONDARIES, "items.cactus_potato")
            column(COL_LEVEL_REQUIRED, 76)
            column(COL_XP_FINISHED, 173)
            columnRSCM(COL_FINISHED_POTION, "items.3dose1magic")
        }

        // Zamorak brew (Torstol + Jangerberries)
        row("dbrows.herblore_zamorak_brew") {
            columnRSCM(COL_POT_PRIMARY, "items.torstolvial")
            columnRSCM(COL_SECONDARIES, "items.jangerberries")
            column(COL_LEVEL_REQUIRED, 78)
            column(COL_XP_FINISHED, 175)
            columnRSCM(COL_FINISHED_POTION, "items.3dosepotionofzamorak")
        }

        // Saradomin brew (Toadflax + Crushed nest)
        row("dbrows.herblore_saradomin_brew") {
            columnRSCM(COL_POT_PRIMARY, "items.toadflaxvial")
            columnRSCM(COL_SECONDARIES, "items.crushed_bird_nest")
            column(COL_LEVEL_REQUIRED, 81)
            column(COL_XP_FINISHED, 180)
            columnRSCM(COL_FINISHED_POTION, "items.3dosepotionofsaradomin")
        }

        // Defence potion (Ranarr + White berries)
        row("dbrows.herblore_defence_potion") {
            columnRSCM(COL_POT_PRIMARY, "items.ranarrvial")
            columnRSCM(COL_SECONDARIES, "items.white_berries")
            column(COL_LEVEL_REQUIRED, 30)
            column(COL_XP_FINISHED, 75)
            columnRSCM(COL_FINISHED_POTION, "items.3dose1defense")
        }

        // Agility potion (Toadflax + Toad's legs)
        row("dbrows.herblore_agility_potion") {
            columnRSCM(COL_POT_PRIMARY, "items.toadflaxvial")
            columnRSCM(COL_SECONDARIES, "items.toads_legs")
            column(COL_LEVEL_REQUIRED, 34)
            column(COL_XP_FINISHED, 80)
            columnRSCM(COL_FINISHED_POTION, "items.3dose1agility")
        }

        // Combat potion (Harralander + Goat horn dust)
        row("dbrows.herblore_combat_potion") {
            columnRSCM(COL_POT_PRIMARY, "items.harralandervial")
            columnRSCM(COL_SECONDARIES, "items.ground_desert_goat_horn")
            column(COL_LEVEL_REQUIRED, 36)
            column(COL_XP_FINISHED, 84)
            columnRSCM(COL_FINISHED_POTION, "items.3dosecombat")
        }

        // Hunter potion (Avantoe + Kebbit teeth)
        row("dbrows.herblore_hunter_potion") {
            columnRSCM(COL_POT_PRIMARY, "items.avantoevial")
            columnRSCM(COL_SECONDARIES, "items.huntingbeast_sabreteeth")
            column(COL_LEVEL_REQUIRED, 53)
            column(COL_XP_FINISHED, 120)
            columnRSCM(COL_FINISHED_POTION, "items.3dosehunting")
        }

        // Bastion potion: Cadantine (unf) + Wine of zamorak + Crushed superior dragon bones → Bastion (3)
        row("dbrows.herblore_bastion_potion") {
            columnRSCM(COL_POT_PRIMARY, "items.cadantinevial")
            columnRSCM(COL_SECONDARIES, "items.wine_of_zamorak", "items.crushed_dragon_bones")
            column(COL_LEVEL_REQUIRED, 80)
            column(COL_XP_FINISHED, 155)
            columnRSCM(COL_FINISHED_POTION, "items.3dosebastion")
        }

        // Battlemage potion: Cadantine (unf) + Potato cactus + Crushed superior dragon bones → Battlemage (3)
        row("dbrows.herblore_battlemage_potion") {
            columnRSCM(COL_POT_PRIMARY, "items.cadantinevial")
            columnRSCM(COL_SECONDARIES, "items.cactus_potato", "items.crushed_dragon_bones")
            column(COL_LEVEL_REQUIRED, 79)
            column(COL_XP_FINISHED, 155)
            columnRSCM(COL_FINISHED_POTION, "items.3dosebattlemage")
        }

        // Super combat potion: Torstol (unf) + Super attack (3) + Super strength (3) + Super defence (3) = Super combat (3)
        row("dbrows.herblore_super_combat_potion") {
            columnRSCM(COL_POT_PRIMARY, "items.torstol")
            columnRSCM(COL_SECONDARIES, "items.3dose2attack", "items.3dose2strength", "items.3dose2defense")
            column(COL_LEVEL_REQUIRED, 90)
            column(COL_XP_FINISHED, 150)
            columnRSCM(COL_FINISHED_POTION, "items.3dose2combat")
        }

        // Extended anti-venom+ (Anti-venom+ (3) + Zulrah's scales)
        row("dbrows.herblore_extended_antivenom_plus") {
            columnRSCM(COL_POT_PRIMARY, "items.antivenom+3")
            columnRSCM(COL_SECONDARIES, "items.snakeboss_scale")
            column(COL_LEVEL_REQUIRED, 94)
            column(COL_XP_FINISHED, 160)
            columnRSCM(COL_FINISHED_POTION, "items.extended_antivenom+3")
        }
    }

    /**
     * Table for cleaning grimy herbs (unidentified -> clean)
     * One-time action, no repeatable delay
     */
    fun cleaningHerbs() = dbTable("tables.herblore_cleaning") {
        column("grimy_herb", COL_GRIMY_HERB, VarType.OBJ)
        column("level", COL_CLEAN_LEVEL, VarType.INT)
        column("xp", COL_CLEAN_XP, VarType.INT)
        column("clean_herb", COL_CLEAN_HERB, VarType.OBJ)

        // Guam
        row("dbrows.herblore_clean_guam") {
            columnRSCM(COL_GRIMY_HERB, "items.unidentified_guam")
            column(COL_CLEAN_LEVEL, 3)
            column(COL_CLEAN_XP, 2)
            columnRSCM(COL_CLEAN_HERB, "items.guam_leaf")
        }

        // Marrentill
        row("dbrows.herblore_clean_marrentill") {
            columnRSCM(COL_GRIMY_HERB, "items.unidentified_marentill")
            column(COL_CLEAN_LEVEL, 5)
            column(COL_CLEAN_XP, 3)
            columnRSCM(COL_CLEAN_HERB, "items.marentill")
        }

        // Tarromin
        row("dbrows.herblore_clean_tarromin") {
            columnRSCM(COL_GRIMY_HERB, "items.unidentified_tarromin")
            column(COL_CLEAN_LEVEL, 11)
            column(COL_CLEAN_XP, 5)
            columnRSCM(COL_CLEAN_HERB, "items.tarromin")
        }

        // Harralander
        row("dbrows.herblore_clean_harralander") {
            columnRSCM(COL_GRIMY_HERB, "items.unidentified_harralander")
            column(COL_CLEAN_LEVEL, 20)
            column(COL_CLEAN_XP, 6)
            columnRSCM(COL_CLEAN_HERB, "items.harralander")
        }

        // Ranarr
        row("dbrows.herblore_clean_ranarr") {
            columnRSCM(COL_GRIMY_HERB, "items.unidentified_ranarr")
            column(COL_CLEAN_LEVEL, 25)
            column(COL_CLEAN_XP, 8)
            columnRSCM(COL_CLEAN_HERB, "items.ranarr_weed")
        }

        // Toadflax
        row("dbrows.herblore_clean_toadflax") {
            columnRSCM(COL_GRIMY_HERB, "items.unidentified_toadflax")
            column(COL_CLEAN_LEVEL, 30)
            column(COL_CLEAN_XP, 8)
            columnRSCM(COL_CLEAN_HERB, "items.toadflax")
        }

        // Irit
        row("dbrows.herblore_clean_irit") {
            columnRSCM(COL_GRIMY_HERB, "items.unidentified_irit")
            column(COL_CLEAN_LEVEL, 40)
            column(COL_CLEAN_XP, 9)
            columnRSCM(COL_CLEAN_HERB, "items.irit_leaf")
        }

        // Avantoe
        row("dbrows.herblore_clean_avantoe") {
            columnRSCM(COL_GRIMY_HERB, "items.unidentified_avantoe")
            column(COL_CLEAN_LEVEL, 48)
            column(COL_CLEAN_XP, 10)
            columnRSCM(COL_CLEAN_HERB, "items.avantoe")
        }

        // Kwuarm
        row("dbrows.herblore_clean_kwuarm") {
            columnRSCM(COL_GRIMY_HERB, "items.unidentified_kwuarm")
            column(COL_CLEAN_LEVEL, 54)
            column(COL_CLEAN_XP, 11)
            columnRSCM(COL_CLEAN_HERB, "items.kwuarm")
        }

        // Snapdragon
        row("dbrows.herblore_clean_snapdragon") {
            columnRSCM(COL_GRIMY_HERB, "items.unidentified_snapdragon")
            column(COL_CLEAN_LEVEL, 59)
            column(COL_CLEAN_XP, 12)
            columnRSCM(COL_CLEAN_HERB, "items.snapdragon")
        }

        // Cadantine
        row("dbrows.herblore_clean_cadantine") {
            columnRSCM(COL_GRIMY_HERB, "items.unidentified_cadantine")
            column(COL_CLEAN_LEVEL, 65)
            column(COL_CLEAN_XP, 13)
            columnRSCM(COL_CLEAN_HERB, "items.cadantine")
        }

        // Lantadyme
        row("dbrows.herblore_clean_lantadyme") {
            columnRSCM(COL_GRIMY_HERB, "items.unidentified_lantadyme")
            column(COL_CLEAN_LEVEL, 67)
            column(COL_CLEAN_XP, 13)
            columnRSCM(COL_CLEAN_HERB, "items.lantadyme")
        }

        // Dwarf weed
        row("dbrows.herblore_clean_dwarf_weed") {
            columnRSCM(COL_GRIMY_HERB, "items.unidentified_dwarf_weed")
            column(COL_CLEAN_LEVEL, 70)
            column(COL_CLEAN_XP, 13)
            columnRSCM(COL_CLEAN_HERB, "items.dwarf_weed")
        }

        // Torstol
        row("dbrows.herblore_clean_torstol") {
            columnRSCM(COL_GRIMY_HERB, "items.unidentified_torstol")
            column(COL_CLEAN_LEVEL, 75)
            column(COL_CLEAN_XP, 14)
            columnRSCM(COL_CLEAN_HERB, "items.torstol")
        }
    }

    /**
     * Table for creating barbarian mixes (two-dose potion + roe/caviar)
     */
    fun barbarianMixes() = dbTable("tables.herblore_barbarian_mixes") {
        column("two_dose_potion", COL_TWO_DOSE_POTION, VarType.OBJ)
        column("mix_ingredient", COL_MIX_INGREDIENT, VarType.OBJ)
        column("level", COL_MIX_LEVEL, VarType.INT)
        column("xp", COL_MIX_XP, VarType.INT)
        column("barbarian_mix", COL_BARBARIAN_MIX, VarType.OBJ)

        // Attack mix (2-dose attack + roe)
        row("dbrows.herblore_attack_mix") {
            columnRSCM(COL_TWO_DOSE_POTION, "items.2dose1attack")
            columnRSCM(COL_MIX_INGREDIENT, "items.brut_roe")
            column(COL_MIX_LEVEL, 3)
            column(COL_MIX_XP, 0)
            columnRSCM(COL_BARBARIAN_MIX, "items.brutal_2dose1attack")
        }

        // Antipoison mix (2-dose antipoison + roe)
        row("dbrows.herblore_antipoison_mix") {
            columnRSCM(COL_TWO_DOSE_POTION, "items.2doseantipoison")
            columnRSCM(COL_MIX_INGREDIENT, "items.brut_roe")
            column(COL_MIX_LEVEL, 5)
            column(COL_MIX_XP, 0)
            columnRSCM(COL_BARBARIAN_MIX, "items.brutal_2doseantipoison")
        }

        // Attack mix (2-dose attack + caviar) - Alternative to roe
        row("dbrows.herblore_attack_mix_caviar") {
            columnRSCM(COL_TWO_DOSE_POTION, "items.2dose1attack")
            columnRSCM(COL_MIX_INGREDIENT, "items.brut_caviar")
            column(COL_MIX_LEVEL, 3)
            column(COL_MIX_XP, 0)
            columnRSCM(COL_BARBARIAN_MIX, "items.brutal_2dose1attack")
        }

        // Strength mix (2-dose strength + roe)
        row("dbrows.herblore_strength_mix") {
            columnRSCM(COL_TWO_DOSE_POTION, "items.2dose1strength")
            columnRSCM(COL_MIX_INGREDIENT, "items.brut_roe")
            column(COL_MIX_LEVEL, 12)
            column(COL_MIX_XP, 0)
            columnRSCM(COL_BARBARIAN_MIX, "items.brutal_2dose1strength")
        }

        // Stat restore mix (2-dose restore + roe)
        row("dbrows.herblore_stat_restore_mix") {
            columnRSCM(COL_TWO_DOSE_POTION, "items.2dosestatrestore")
            columnRSCM(COL_MIX_INGREDIENT, "items.brut_roe")
            column(COL_MIX_LEVEL, 22)
            column(COL_MIX_XP, 0)
            columnRSCM(COL_BARBARIAN_MIX, "items.brutal_2dosestatrestore")
        }

        // Energy mix (2-dose energy + roe)
        row("dbrows.herblore_energy_mix") {
            columnRSCM(COL_TWO_DOSE_POTION, "items.2dose1energy")
            columnRSCM(COL_MIX_INGREDIENT, "items.brut_roe")
            column(COL_MIX_LEVEL, 26)
            column(COL_MIX_XP, 0)
            columnRSCM(COL_BARBARIAN_MIX, "items.brutal_2dose1energy")
        }

        // Defence mix (2-dose defence + roe)
        row("dbrows.herblore_defence_mix") {
            columnRSCM(COL_TWO_DOSE_POTION, "items.2dose1defense")
            columnRSCM(COL_MIX_INGREDIENT, "items.brut_roe")
            column(COL_MIX_LEVEL, 30)
            column(COL_MIX_XP, 0)
            columnRSCM(COL_BARBARIAN_MIX, "items.brutal_2dose1defense")
        }

        // Agility mix (2-dose agility + roe)
        row("dbrows.herblore_agility_mix") {
            columnRSCM(COL_TWO_DOSE_POTION, "items.2dose1agility")
            columnRSCM(COL_MIX_INGREDIENT, "items.brut_roe")
            column(COL_MIX_LEVEL, 34)
            column(COL_MIX_XP, 0)
            columnRSCM(COL_BARBARIAN_MIX, "items.brutal_2dose1agility")
        }

        // Prayer mix (2-dose prayer + roe)
        row("dbrows.herblore_prayer_mix") {
            columnRSCM(COL_TWO_DOSE_POTION, "items.2doseprayerrestore")
            columnRSCM(COL_MIX_INGREDIENT, "items.brut_roe")
            column(COL_MIX_LEVEL, 38)
            column(COL_MIX_XP, 0)
            columnRSCM(COL_BARBARIAN_MIX, "items.brutal_2doseprayerrestore")
        }

        // Super attack mix (2-dose super attack + caviar)
        row("dbrows.herblore_super_attack_mix") {
            columnRSCM(COL_TWO_DOSE_POTION, "items.2dose2attack")
            columnRSCM(COL_MIX_INGREDIENT, "items.brut_caviar")
            column(COL_MIX_LEVEL, 45)
            column(COL_MIX_XP, 0)
            columnRSCM(COL_BARBARIAN_MIX, "items.brutal_2dose2attack")
        }

        // Super antipoison mix (2-dose super antipoison + caviar)
        row("dbrows.herblore_super_antipoison_mix") {
            columnRSCM(COL_TWO_DOSE_POTION, "items.2dose2antipoison")
            columnRSCM(COL_MIX_INGREDIENT, "items.brut_caviar")
            column(COL_MIX_LEVEL, 48)
            column(COL_MIX_XP, 0)
            columnRSCM(COL_BARBARIAN_MIX, "items.brutal_2dose2antipoison")
        }

        // Fishing mix (2-dose fishing + caviar)
        row("dbrows.herblore_fishing_mix") {
            columnRSCM(COL_TWO_DOSE_POTION, "items.2dosefisherspotion")
            columnRSCM(COL_MIX_INGREDIENT, "items.brut_caviar")
            column(COL_MIX_LEVEL, 50)
            column(COL_MIX_XP, 0)
            columnRSCM(COL_BARBARIAN_MIX, "items.brutal_2dosefisherspotion")
        }

        // Super energy mix (2-dose super energy + caviar)
        row("dbrows.herblore_super_energy_mix") {
            columnRSCM(COL_TWO_DOSE_POTION, "items.2dose2energy")
            columnRSCM(COL_MIX_INGREDIENT, "items.brut_caviar")
            column(COL_MIX_LEVEL, 52)
            column(COL_MIX_XP, 0)
            columnRSCM(COL_BARBARIAN_MIX, "items.brutal_2dose2energy")
        }

        // Super strength mix (2-dose super strength + caviar)
        row("dbrows.herblore_super_strength_mix") {
            columnRSCM(COL_TWO_DOSE_POTION, "items.2dose2strength")
            columnRSCM(COL_MIX_INGREDIENT, "items.brut_caviar")
            column(COL_MIX_LEVEL, 55)
            column(COL_MIX_XP, 0)
            columnRSCM(COL_BARBARIAN_MIX, "items.brutal_2dose2strength")
        }

        // Super restore mix (2-dose super restore + caviar)
        row("dbrows.herblore_super_restore_mix") {
            columnRSCM(COL_TWO_DOSE_POTION, "items.2dose2restore")
            columnRSCM(COL_MIX_INGREDIENT, "items.brut_caviar")
            column(COL_MIX_LEVEL, 63)
            column(COL_MIX_XP, 0)
            columnRSCM(COL_BARBARIAN_MIX, "items.brutal_2dose2restore")
        }

        // Super defence mix (2-dose super defence + caviar)
        row("dbrows.herblore_super_defence_mix") {
            columnRSCM(COL_TWO_DOSE_POTION, "items.2dose2defense")
            columnRSCM(COL_MIX_INGREDIENT, "items.brut_caviar")
            column(COL_MIX_LEVEL, 66)
            column(COL_MIX_XP, 0)
            columnRSCM(COL_BARBARIAN_MIX, "items.brutal_2dose2defense")
        }

        // Antifire mix (2-dose antifire + caviar)
        row("dbrows.herblore_antifire_mix") {
            columnRSCM(COL_TWO_DOSE_POTION, "items.2dose1antidragon")
            columnRSCM(COL_MIX_INGREDIENT, "items.brut_caviar")
            column(COL_MIX_LEVEL, 69)
            column(COL_MIX_XP, 0)
            columnRSCM(COL_BARBARIAN_MIX, "items.brutal_2dose1antidragon")
        }

        // Ranging mix (2-dose ranging + caviar)
        row("dbrows.herblore_ranging_mix") {
            columnRSCM(COL_TWO_DOSE_POTION, "items.2doserangerspotion")
            columnRSCM(COL_MIX_INGREDIENT, "items.brut_caviar")
            column(COL_MIX_LEVEL, 72)
            column(COL_MIX_XP, 0)
            columnRSCM(COL_BARBARIAN_MIX, "items.brutal_2doserangerspotion")
        }

        // Magic mix (2-dose magic + caviar)
        row("dbrows.herblore_magic_mix") {
            columnRSCM(COL_TWO_DOSE_POTION, "items.2dose1magic")
            columnRSCM(COL_MIX_INGREDIENT, "items.brut_caviar")
            column(COL_MIX_LEVEL, 76)
            column(COL_MIX_XP, 0)
            columnRSCM(COL_BARBARIAN_MIX, "items.brutal_2dose1magic")
        }

        // Zamorak mix (2-dose zamorak + caviar)
        row("dbrows.herblore_zamorak_mix") {
            columnRSCM(COL_TWO_DOSE_POTION, "items.2dosepotionofzamorak")
            columnRSCM(COL_MIX_INGREDIENT, "items.brut_caviar")
            column(COL_MIX_LEVEL, 78)
            column(COL_MIX_XP, 0)
            columnRSCM(COL_BARBARIAN_MIX, "items.brutal_2dosepotionofzamorak")
        }
    }

    /**
     * Table for creating swamp tar
     */
    fun swampTar() = dbTable("tables.herblore_swamp_tar") {
        column("herb", COL_TAR_HERB, VarType.OBJ)
        column("level", COL_TAR_LEVEL, VarType.INT)
        column("xp", COL_TAR_XP, VarType.INT)
        column("finished_tar", COL_TAR_FINISHED, VarType.OBJ)

        // Guam tar (Guam leaf + 15x swamp tar = 15x green tar)
        row("dbrows.herblore_guam_tar") {
            columnRSCM(COL_TAR_HERB, "items.guam_leaf")
            column(COL_TAR_LEVEL, 19)
            column(COL_TAR_XP, 30)
            columnRSCM(COL_TAR_FINISHED, "items.salamander_tar_green")
        }

        // Marrentill tar (Marrentill + 15x swamp tar = 15x orange tar)
        row("dbrows.herblore_marrentill_tar") {
            columnRSCM(COL_TAR_HERB, "items.marentill")
            column(COL_TAR_LEVEL, 31)
            column(COL_TAR_XP, 42)
            columnRSCM(COL_TAR_FINISHED, "items.salamander_tar_orange")
        }

        // Tarromin tar (Tarromin + 15x swamp tar = 15x red tar)
        row("dbrows.herblore_tarromin_tar") {
            columnRSCM(COL_TAR_HERB, "items.tarromin")
            column(COL_TAR_LEVEL, 39)
            column(COL_TAR_XP, 55)
            columnRSCM(COL_TAR_FINISHED, "items.salamander_tar_red")
        }

        // Harralander tar (Harralander + 15x swamp tar = 15x black tar)
        row("dbrows.herblore_harralander_tar") {
            columnRSCM(COL_TAR_HERB, "items.harralander")
            column(COL_TAR_LEVEL, 44)
            column(COL_TAR_XP, 72)
            columnRSCM(COL_TAR_FINISHED, "items.salamander_tar_black")
        }

        // Irit tar (Irit leaf + 15x swamp tar = 15x mountain tar)
        row("dbrows.herblore_irit_tar") {
            columnRSCM(COL_TAR_HERB, "items.irit_leaf")
            column(COL_TAR_LEVEL, 50)
            column(COL_TAR_XP, 84)
            columnRSCM(COL_TAR_FINISHED, "items.salamander_tar_mountain")
        }
    }

    /**
     * Table for crushing items with pestle and mortar
     * Auto-crushes every 3 ticks when multiple items are available
     */
    fun crushing() = dbTable("tables.herblore_crushing") {
        column("item", COL_CRUSH_ITEM, VarType.OBJ)
        column("level", COL_CRUSH_LEVEL, VarType.INT)
        column("xp", COL_CRUSH_XP, VarType.INT)
        column("crushed_item", COL_CRUSHED_ITEM, VarType.OBJ)

        // Bird nest (empty) → Crushed bird nest
        row("dbrows.herblore_crush_bird_nest") {
            columnRSCM(COL_CRUSH_ITEM, "items.bird_nest_empty")
            column(COL_CRUSH_LEVEL, 1)
            column(COL_CRUSH_XP, 0)
            columnRSCM(COL_CRUSHED_ITEM, "items.crushed_bird_nest")
        }

        // Chocolate bar → Chocolate dust
        row("dbrows.herblore_crush_chocolate") {
            columnRSCM(COL_CRUSH_ITEM, "items.chocolate_bar")
            column(COL_CRUSH_LEVEL, 1)
            column(COL_CRUSH_XP, 0)
            columnRSCM(COL_CRUSHED_ITEM, "items.chocolate_dust")
        }

        // Unicorn horn → Unicorn horn dust
        row("dbrows.herblore_crush_unicorn_horn") {
            columnRSCM(COL_CRUSH_ITEM, "items.unicorn_horn")
            column(COL_CRUSH_LEVEL, 1)
            column(COL_CRUSH_XP, 0)
            columnRSCM(COL_CRUSHED_ITEM, "items.unicorn_horn_dust")
        }

        // Blue dragon scale → Dragon scale dust
        row("dbrows.herblore_crush_dragon_scale") {
            columnRSCM(COL_CRUSH_ITEM, "items.blue_dragon_scale")
            column(COL_CRUSH_LEVEL, 1)
            column(COL_CRUSH_XP, 0)
            columnRSCM(COL_CRUSHED_ITEM, "items.dragon_scale_dust")
        }

        // Desert goat horn → Ground desert goat horn
        row("dbrows.herblore_crush_goat_horn") {
            columnRSCM(COL_CRUSH_ITEM, "items.desert_goat_horn")
            column(COL_CRUSH_LEVEL, 1)
            column(COL_CRUSH_XP, 0)
            columnRSCM(COL_CRUSHED_ITEM, "items.ground_desert_goat_horn")
        }

        // Superior dragon bones → Crushed superior dragon bones
        row("dbrows.herblore_crush_superior_dragon_bones") {
            columnRSCM(COL_CRUSH_ITEM, "items.dragon_bones_superior")
            column(COL_CRUSH_LEVEL, 1)
            column(COL_CRUSH_XP, 0)
            columnRSCM(COL_CRUSHED_ITEM, "items.crushed_dragon_bones")
        }
    }
}

