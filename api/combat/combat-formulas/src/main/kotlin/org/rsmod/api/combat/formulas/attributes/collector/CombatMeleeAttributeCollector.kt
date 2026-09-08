package org.rsmod.api.combat.formulas.attributes.collector

import dev.openrune.types.ItemServerType
import java.util.EnumSet
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.combat.formulas.attributes.CombatMeleeAttributes
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.BaseParams
import org.rsmod.api.player.front
import org.rsmod.api.player.hat
import org.rsmod.api.player.legs
import org.rsmod.api.player.righthand
import org.rsmod.api.player.torso
import org.rsmod.api.player.worn.EquipmentChecks
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.isAnyType
import org.rsmod.game.inv.isType
import org.rsmod.game.type.getOrNull

public class CombatMeleeAttributeCollector {
    public fun collect(
        player: Player,
        attackType: MeleeAttackType?,
    ): EnumSet<CombatMeleeAttributes> {
        val attributes = EnumSet.noneOf(CombatMeleeAttributes::class.java)

        if (attackType == MeleeAttackType.Crush) {
            attributes += CombatMeleeAttributes.Crush
        } else if (attackType == MeleeAttackType.Stab) {
            attributes += CombatMeleeAttributes.Stab
        }

        if (player.skullIcon != null && constants.isForinthrySurgeSkull(player.skullIcon!!)) {
            attributes += CombatMeleeAttributes.ForinthrySurge
        }

        val amulet = player.front
        if (amulet.isType("obj.wild_cave_amulet")) {
            attributes += CombatMeleeAttributes.AmuletOfAvarice
        } else if (amulet.isAnyType("obj.lotr_crystalshard_necklace_upgrade", "obj.nzone_salve_amulet_e")) {
            attributes += CombatMeleeAttributes.SalveAmuletE
        } else if (amulet.isAnyType("obj.crystalshard_necklace", "obj.nzone_salve_amulet")) {
            attributes += CombatMeleeAttributes.SalveAmulet
        }

        val helm = player.hat
        val helmType = getOrNull(helm)
        if (helmType != null && helmType.hasBlackMaskAttribute()) {
            attributes += CombatMeleeAttributes.BlackMask
        }

        val weapon = player.righthand
        if (weapon.isAnyType("obj.arclight", "obj.emberlight")) {
            attributes += CombatMeleeAttributes.Arclight
        } else if (weapon.isType("obj.bone_claws")) {
            attributes += CombatMeleeAttributes.BurningClaws
        }

        val top = player.torso
        val legs = player.legs
        if (EquipmentChecks.isObsidianSet(helm, top, legs)) {
            attributes += CombatMeleeAttributes.Obsidian
        }

        val weaponAttribute =
            when {
                weapon.isAnyType(
                    "obj.tzhaar_knife",
                    "obj.tzhaar_splitsword",
                    "obj.tzhaar_mace",
                    "obj.tzhaar_maul",
                    "obj.tzhaar_maul_t",
                ) -> {
                    CombatMeleeAttributes.TzHaarWeapon
                }

                weapon.isType("obj.dragonhunter_lance") -> {
                    CombatMeleeAttributes.DragonHunterLance
                }

                weapon.isType("obj.dragonhunter_wand") -> {
                    CombatMeleeAttributes.DragonHunterWand
                }

                weapon.isType("obj.keris_partisan_breach") -> {
                    CombatMeleeAttributes.KerisBreachPartisan
                }

                weapon.isType("obj.keris_partisan_sun") -> {
                    CombatMeleeAttributes.KerisSunPartisan
                }

                weapon.isAnyType(
                    "obj.contact_keris",
                    "obj.contact_keris_p",
                    "obj.contact_keris_p+",
                    "obj.contact_keris_p++",
                    "obj.keris_partisan",
                    "obj.keris_partisan_corruption",
                ) -> {
                    CombatMeleeAttributes.KerisWeapon
                }

                weapon.isAnyType("obj.barronite_mace", "obj.barronite_mace_trouver") -> {
                    CombatMeleeAttributes.BarroniteMaceWeapon
                }

                weapon.isAnyType("obj.wild_cave_chainmace_charged", "obj.wild_cave_ursine_charged") -> {
                    CombatMeleeAttributes.RevenantWeapon
                }

                weapon.isType("obj.agrith_silverlight_dyed") -> {
                    CombatMeleeAttributes.Silverlight
                }

                weapon.isAnyType(
                    "obj.leafbladed_sword",
                    "obj.slayer_leafbladed_spear",
                    "obj.leafbladed_battleaxe",
                ) -> {
                    CombatMeleeAttributes.LeafBladed
                }

                weapon.isType("obj.giants_foundry_colossal_blade") -> {
                    CombatMeleeAttributes.ColossalBlade
                }

                weapon.isType("obj.rat_bone_mace") -> {
                    CombatMeleeAttributes.RatBoneWeapon
                }

                weapon.isType("obj.inquisitors_mace") -> {
                    CombatMeleeAttributes.InquisitorWeapon
                }

                weapon.isAnyType("obj.osmumtens_fang", "obj.osmumtens_fang_ornament") -> {
                    CombatMeleeAttributes.OsmumtensFang
                }

                weapon.isType("obj.gadderanks_warhammer") -> {
                    CombatMeleeAttributes.Gadderhammer
                }

                else -> null
            }

        if (weaponAttribute != null) {
            attributes += weaponAttribute
        }

        if (helm.isType("obj.inquisitors_helm")) {
            attributes += CombatMeleeAttributes.InquisitorHelm
        }

        if (top.isType("obj.inquisitors_body")) {
            attributes += CombatMeleeAttributes.InquisitorTop
        }

        if (legs.isType("obj.inquisitors_skirt")) {
            attributes += CombatMeleeAttributes.InquisitorBottom
        }

        if (EquipmentChecks.isDharokSet(helm, top, legs, weapon)) {
            attributes += CombatMeleeAttributes.Dharoks
        }

        if (amulet.isAnyType("obj.jewl_beserker_necklace", "obj.jewl_beserker_necklace_ornament")) {
            attributes += CombatMeleeAttributes.BerserkerNeck
        }

        val weaponType = getOrNull(weapon)
        if (weaponType != null && attackType == MeleeAttackType.Stab) {
            val isCorpbaneWeapon =
                weaponType.isCategoryType("category.halberd") ||
                    weaponType.isCategoryType("category.spear") ||
                    CombatMeleeAttributes.OsmumtensFang in attributes

            if (isCorpbaneWeapon) {
                attributes += CombatMeleeAttributes.CorpBaneWeapon
            }
        }

        return attributes
    }

    private fun ItemServerType.hasBlackMaskAttribute(): Boolean =
        param(BaseParams.blackmask) != 0 ||
            param(BaseParams.slayer_helm) != 0 ||
            param(BaseParams.blackmask_imbued) != 0 ||
            param(BaseParams.slayer_helm_imbued) != 0
}
