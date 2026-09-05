package org.rsmod.tools.combatanims

import dev.openrune.util.WeaponCategory

/** What the resolver needs to know about an npc. All animation names are bare gameval names. */
data class NpcFacts(
    val rscm: String,
    val name: String,
    val readyAnim: String?,
    val attack: Int,
    val ranged: Int,
    val magic: Int,
    val attackable: Boolean,
    /** The npc already declares `param.attack_anim`; the cache data wins and it is left alone. */
    val hasAttackAnim: Boolean,
)

/** The weapon an npc is holding, identified by its wear model, with the weapon's own combat data. */
data class WeaponFacts(
    val rscm: String,
    val category: WeaponCategory,
    val attackAnim: String?,
    val attackSound: Int?,
    val defendAnim: String?,
    val projTravel: String?,
    val projType: String?,
    val attackRange: Int?,
    /** The weapon's third-stance attack (`attack_anim_stance3`): the spear lunge, the axe smash. */
    val lungeAnim: String? = null,
    val lungeSound: Int? = null,
)

/** The shield an npc is holding, identified by its wear model. */
data class ShieldFacts(val rscm: String, val name: String)

/** The resolved combat animation set for one npc. `null` fields are left as the cache has them. */
data class NpcCombatAnims(
    val npc: String,
    val source: String,
    val attackAnim: String? = null,
    val attackType: String? = null,
    val attackSound: Int? = null,
    val defendAnim: String? = null,
    val deathAnim: String? = null,
    val projTravel: String? = null,
    val projType: String? = null,
    val attackRange: Int? = null,
) {
    val isEmpty: Boolean
        get() =
            attackAnim == null &&
                attackType == null &&
                defendAnim == null &&
                deathAnim == null &&
                projTravel == null
}

/**
 * Decides which combat animations an npc should play, from what it is and what it holds.
 *
 * Two kinds of npc are handled:
 * - **Humanoids** share the player skeleton, so they use the player animations of the weapon
 *   they hold: the weapon's own `attack_anim_stance1`, `defend_anim`, sound and projectile. A
 *   shield overrides the block animation, as it does for players. Casters - staff or bare-handed
 *   npcs whose magic level leads, or whose name says wizard, druid, necromancer and so on - cast
 *   a strike spell instead of hitting with the staff. A humanoid whose weapon is not an item at
 *   all (the barbarians' giant axes, great hammers and two-handed swords, the ice warriors'
 *   swords) is recognised by its ready stance instead - see [stanceWeapons] - so it swings
 *   rather than punches.
 * - **Monsters** get their animation family from the name of their ready animation, via
 *   [AnimationFamilies].
 *
 * The resolver is pure: it takes facts and returns names, so it is unit tested without a cache.
 */
object NpcCombatAnimResolver {
    private const val SHIELD_BLOCK = "human_shield_defence"
    private const val DEFENDER_BLOCK = "warguild_parry_defend"
    private const val CAST_UNARMED = "human_caststrike"
    private const val CAST_STAFF = "human_caststrike_staff"

    private const val TYPE_STAB = "category.attacktype_stab"
    private const val TYPE_SLASH = "category.attacktype_slash"
    private const val TYPE_CRUSH = "category.attacktype_crush"
    private const val TYPE_RANGED = "category.attacktype_standard"
    private const val TYPE_MAGIC = "category.attacktype_magic"

    private const val DEFAULT_RANGED_RANGE = 7
    private const val DEFAULT_MAGIC_RANGE = 7

    /** The player sounds for the weapon kinds the stance fallbacks stand in for. */
    private const val TWO_HANDED_SOUND = 2503
    private const val TWO_HANDED_CHOP_SOUND = 2502
    private const val AXE_SOUND = 2498
    private const val STAFF_SOUND = 2555

    /** Weapon categories whose npcs use the third-stance (lunge) attack rather than the first. */
    private val lungeCategories = setOf(WeaponCategory.Spear, WeaponCategory.Polearm)

    /**
     * The spear spike; any weapon that spikes lunges instead. Halberds spike but have no lunge
     * stance of their own, so they borrow the spear's.
     */
    private const val SPEAR_SPIKE = "human_spear_spike"
    private const val SPEAR_LUNGE = "human_spear_lunge"
    private const val SPEAR_LUNGE_SOUND = 2555

    /** Axe npcs chop rather than hack. */
    private const val AXE_HACK = "human_axe_hack"
    private const val AXE_CHOP = "human_axe_chop"

    /** Ready animations that are humanoid but do not start with `human_`. */
    private val humanoidReadyAnims =
        setOf("barrow_dharok_ready", "wanted_ranger_ready", "walk_walkingstick")

    /**
     * Player-skeleton weapons that exist only as npc models, so no item wear model matches them.
     * These are the weapons the barbarian villagers, Gunthor and the skeleton heavies are drawn
     * with: giant axes, great hammers and two-handed swords, all swung with the two-handed sword
     * animations.
     */
    val npcOnlyWeapons: Map<Int, WeaponFacts> =
        listOf(11789, 11790, 11792, 11793).associateWith { model ->
            twoHanded("model:$model")
        }

    /**
     * What a humanoid with no recognisable weapon is holding, going by the stance it stands in.
     * `human_dh_weapon_ready` is the two-handed stance, `human_transready` the ice and earth
     * warriors' sword stance, and `human_staffready` a staff (used only when the npc is not a
     * caster, who would cast instead).
     */
    private val stanceWeapons: Map<String, WeaponFacts> =
        mapOf(
            "human_dh_weapon_ready" to twoHanded("stance:human_dh_weapon_ready"),
            "human_transready" to
                WeaponFacts(
                    rscm = "stance:human_transready",
                    category = WeaponCategory.Axe,
                    attackAnim = "human_trans_axe_chop",
                    attackSound = AXE_SOUND,
                    defendAnim = "human_trans_axe_def",
                    projTravel = null,
                    projType = null,
                    attackRange = null,
                ),
            "human_staffready" to
                WeaponFacts(
                    rscm = "stance:human_staffready",
                    category = WeaponCategory.Staff,
                    attackAnim = "human_staff_pound",
                    attackSound = STAFF_SOUND,
                    defendAnim = "human_staff_block",
                    projTravel = null,
                    projType = null,
                    attackRange = null,
                ),
            "barrow_dharok_ready" to
                WeaponFacts(
                    rscm = "stance:barrow_dharok_ready",
                    category = WeaponCategory.Axe,
                    attackAnim = "barrow_dharok_slash",
                    attackSound = TWO_HANDED_SOUND,
                    defendAnim = null,
                    projTravel = null,
                    projType = null,
                    attackRange = null,
                ),
        )

    /** Giant axes, great hammers and two-handed swords all chop with the two-handed chop. */
    private fun twoHanded(rscm: String): WeaponFacts =
        WeaponFacts(
            rscm = rscm,
            category = WeaponCategory.TwoHandedSword,
            attackAnim = "human_dhsword_chop",
            attackSound = TWO_HANDED_CHOP_SOUND,
            defendAnim = "human_dhsword_block",
            projTravel = null,
            projType = null,
            attackRange = null,
        )

    private val casterNames =
        Regex(
            "wizard|mage|magus|druid|necromancer|sorcer|warlock|witch|shaman|mystic|priest|cultist|seer"
        )

    private val rangedNames = Regex("archer|ranger|bowman|marksman")

    private val staffCategories =
        setOf(WeaponCategory.Staff, WeaponCategory.BladedStaff, WeaponCategory.PoweredStaff)

    private val rangedCategories =
        setOf(
            WeaponCategory.Bow,
            WeaponCategory.Crossbow,
            WeaponCategory.Thrown,
            WeaponCategory.Chinchompas,
            WeaponCategory.Gun,
        )

    private val attackTypes: Map<WeaponCategory, String> =
        mapOf(
            WeaponCategory.StabSword to TYPE_STAB,
            WeaponCategory.Spear to TYPE_STAB,
            WeaponCategory.Polearm to TYPE_STAB,
            WeaponCategory.SlashSword to TYPE_SLASH,
            WeaponCategory.Axe to TYPE_SLASH,
            WeaponCategory.TwoHandedSword to TYPE_SLASH,
            WeaponCategory.GodSword to TYPE_SLASH,
            WeaponCategory.Claw to TYPE_SLASH,
            WeaponCategory.Scythe to TYPE_SLASH,
            WeaponCategory.Whip to TYPE_SLASH,
            WeaponCategory.Salamander to TYPE_SLASH,
            WeaponCategory.Blunt to TYPE_CRUSH,
            WeaponCategory.Spiked to TYPE_CRUSH,
            WeaponCategory.Pickaxe to TYPE_CRUSH,
            WeaponCategory.Bludgeon to TYPE_CRUSH,
            WeaponCategory.Bulwark to TYPE_CRUSH,
            WeaponCategory.Banner to TYPE_CRUSH,
            WeaponCategory.Staff to TYPE_CRUSH,
            WeaponCategory.BladedStaff to TYPE_CRUSH,
            WeaponCategory.PoweredStaff to TYPE_CRUSH,
            WeaponCategory.Bow to TYPE_RANGED,
            WeaponCategory.Crossbow to TYPE_RANGED,
            WeaponCategory.Thrown to TYPE_RANGED,
            WeaponCategory.Chinchompas to TYPE_RANGED,
            WeaponCategory.Gun to TYPE_RANGED,
        )

    /** Block animation per category for weapons whose own data leaves it off (scimitars, mostly). */
    private val categoryBlocks: Map<WeaponCategory, String> =
        mapOf(
            WeaponCategory.SlashSword to "human_sword_def",
            WeaponCategory.StabSword to "human_sword_def",
            WeaponCategory.Axe to "human_axe_block",
            WeaponCategory.Blunt to "human_blunt_block",
            WeaponCategory.Spiked to "human_blunt_block",
            WeaponCategory.TwoHandedSword to "human_dhsword_block",
            WeaponCategory.Spear to "human_spear_block",
            WeaponCategory.Polearm to "human_spear_block",
            WeaponCategory.Staff to "human_staff_block",
        )

    fun resolve(
        npc: NpcFacts,
        weapon: WeaponFacts?,
        shield: ShieldFacts?,
        sequences: Set<String>,
    ): NpcCombatAnims? {
        if (!npc.attackable || npc.hasAttackAnim) {
            return null
        }
        val result =
            if (isHumanoid(npc, weapon, shield)) {
                resolveHumanoid(npc, weapon ?: stanceWeapon(npc, sequences), shield, sequences)
            } else {
                resolveMonster(npc, sequences)
            }
        return result?.takeUnless { it.isEmpty }
    }

    /**
     * The weapon a humanoid with no item weapon is holding, judged by its stance. Casters keep
     * their hands free so they cast instead; everyone else in a weapon stance swings it.
     */
    private fun stanceWeapon(npc: NpcFacts, sequences: Set<String>): WeaponFacts? {
        val ready = npc.readyAnim ?: return null
        val weapon = stanceWeapons[ready] ?: return null
        if (isCaster(npc, null)) {
            return null
        }
        if (weapon.attackAnim != null && weapon.attackAnim !in sequences) {
            return null
        }
        return weapon
    }

    fun isHumanoid(npc: NpcFacts, weapon: WeaponFacts?, shield: ShieldFacts?): Boolean {
        val ready = npc.readyAnim
        return weapon != null ||
            shield != null ||
            (ready != null && (ready.startsWith("human_") || ready in humanoidReadyAnims))
    }

    private fun resolveHumanoid(
        npc: NpcFacts,
        weapon: WeaponFacts?,
        shield: ShieldFacts?,
        sequences: Set<String>,
    ): NpcCombatAnims? {
        val shieldBlock = shield?.let(::shieldBlock)
        return when {
            isCaster(npc, weapon) -> caster(npc, weapon, shieldBlock, sequences)
            weapon != null && weapon.category in rangedCategories -> ranged(npc, weapon, shieldBlock)
            weapon != null -> melee(npc, weapon, shieldBlock)
            isUnarmedRanger(npc) -> bareBow(npc, shieldBlock)
            shieldBlock != null ->
                NpcCombatAnims(npc.rscm, "shield:${shield?.rscm}", defendAnim = shieldBlock)
            else -> null
        }
    }

    private fun isCaster(npc: NpcFacts, weapon: WeaponFacts?): Boolean {
        val handsFree = weapon == null || weapon.category in staffCategories
        if (!handsFree) {
            return false
        }
        // Strictly ahead: a villager with every stat at 1 is not a caster.
        val magicLeads = npc.magic > npc.attack && npc.magic > npc.ranged
        // The gameval name often says what the display name does not: `colosseum_warbander_mage`
        // is shown as "Fremennik warband seer".
        return magicLeads ||
            casterNames.containsMatchIn(npc.name.lowercase()) ||
            casterNames.containsMatchIn(npc.rscm.lowercase())
    }

    private fun isUnarmedRanger(npc: NpcFacts): Boolean =
        npc.ranged > npc.attack && rangedNames.containsMatchIn(npc.name.lowercase())

    private fun caster(
        npc: NpcFacts,
        weapon: WeaponFacts?,
        shieldBlock: String?,
        sequences: Set<String>,
    ): NpcCombatAnims {
        val staff = weapon != null
        val cast = if (staff && CAST_STAFF in sequences) CAST_STAFF else CAST_UNARMED
        return NpcCombatAnims(
            npc = npc.rscm,
            source = if (staff) "caster:${weapon?.rscm}" else "caster",
            attackAnim = cast,
            attackType = TYPE_MAGIC,
            defendAnim = shieldBlock ?: weapon?.let(::weaponBlock),
            projTravel = strikeSpell(npc.name),
            projType = "magic_spell",
            attackRange = DEFAULT_MAGIC_RANGE,
        )
    }

    /** The strike spell a caster throws: its element when the name gives one, wind otherwise. */
    private fun strikeSpell(name: String): String {
        val lowercase = name.lowercase()
        val element =
            when {
                "fire" in lowercase || "flame" in lowercase -> "fire"
                "water" in lowercase || "ice" in lowercase -> "water"
                "earth" in lowercase || "rock" in lowercase -> "earth"
                else -> "wind"
            }
        return "${element}strike_travel"
    }

    private fun ranged(npc: NpcFacts, weapon: WeaponFacts, shieldBlock: String?): NpcCombatAnims {
        val (travel, type) = defaultProjectile(weapon.category)
        return NpcCombatAnims(
            npc = npc.rscm,
            source = "weapon:${weapon.rscm}",
            attackAnim = weapon.attackAnim,
            attackType = TYPE_RANGED,
            attackSound = weapon.attackSound,
            defendAnim = shieldBlock,
            projTravel = weapon.projTravel ?: travel,
            projType = weapon.projType ?: type,
            attackRange = weapon.attackRange ?: DEFAULT_RANGED_RANGE,
        )
    }

    private fun bareBow(npc: NpcFacts, shieldBlock: String?): NpcCombatAnims =
        NpcCombatAnims(
            npc = npc.rscm,
            source = "ranger",
            attackAnim = "human_bow",
            attackType = TYPE_RANGED,
            defendAnim = shieldBlock,
            projTravel = "iron_arrow_travel",
            projType = "arrow",
            attackRange = DEFAULT_RANGED_RANGE,
        )

    private fun defaultProjectile(category: WeaponCategory): Pair<String?, String?> =
        when (category) {
            WeaponCategory.Bow -> "iron_arrow_travel" to "arrow"
            WeaponCategory.Crossbow -> "crossbowbolt_travel" to "bolt"
            WeaponCategory.Thrown -> null to "thrown"
            else -> null to null
        }

    private fun melee(npc: NpcFacts, weapon: WeaponFacts, shieldBlock: String?): NpcCombatAnims {
        // Spear and halberd npcs lunge rather than spike, and axe npcs chop rather than hack,
        // each with that stance's own sound so the audio matches the swing.
        val lunge = weapon.category in lungeCategories || weapon.attackAnim == SPEAR_SPIKE
        val chop = weapon.category == WeaponCategory.Axe && weapon.attackAnim == AXE_HACK
        val attackAnim =
            when {
                lunge -> weapon.lungeAnim ?: SPEAR_LUNGE
                chop -> AXE_CHOP
                else -> weapon.attackAnim
            }
        val attackSound = if (lunge) weapon.lungeSound ?: SPEAR_LUNGE_SOUND else weapon.attackSound
        return NpcCombatAnims(
            npc = npc.rscm,
            source = "weapon:${weapon.rscm}",
            attackAnim = attackAnim,
            attackType = attackTypes[weapon.category] ?: TYPE_CRUSH,
            attackSound = attackSound,
            defendAnim = shieldBlock ?: weaponBlock(weapon),
        )
    }

    private fun weaponBlock(weapon: WeaponFacts): String? =
        weapon.defendAnim ?: categoryBlocks[weapon.category]

    private fun shieldBlock(shield: ShieldFacts): String =
        if ("defender" in shield.name.lowercase()) DEFENDER_BLOCK else SHIELD_BLOCK

    private fun resolveMonster(npc: NpcFacts, sequences: Set<String>): NpcCombatAnims? {
        val ready = npc.readyAnim ?: return null
        val family = AnimationFamilies.resolve(ready, sequences)
        if (family.isEmpty) {
            return null
        }
        return NpcCombatAnims(
            npc = npc.rscm,
            source = "family:$ready",
            attackAnim = family.attack,
            defendAnim = family.defend,
            deathAnim = family.death,
        )
    }
}
