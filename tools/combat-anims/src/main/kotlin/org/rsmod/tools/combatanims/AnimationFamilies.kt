package org.rsmod.tools.combatanims

/**
 * Derives a monster's attack, block and death animations from the name of its ready animation.
 *
 * Jagex names an npc's animation set as a family: `giant_update_basic_ready`,
 * `giant_update_basic_attack`, `giant_update_basic_defend`, `giant_update_basic_death`. A variant
 * suffix on the ready animation carries over to the rest of the set: `zombie_update_ready_weapon`
 * pairs with `zombie_update_attack_weapon`. This object knows those conventions, plus the sound
 * effects of the families whose sounds the cache never attaches to the npc ([familySounds]) - it
 * works on plain sequence names so it can be tested without a cache.
 *
 * Names are the bare gameval names (`giant_update_basic_ready`), without the `seq.` prefix.
 */
object AnimationFamilies {
    data class Family(
        val attack: String?,
        val defend: String?,
        val death: String?,
        val attackSound: Int? = null,
        val defendSound: Int? = null,
        val deathSound: Int? = null,
    ) {
        val isEmpty: Boolean
            get() = attack == null && defend == null && death == null
    }

    /** The synth ids a family plays when it attacks, is hit and dies. */
    data class Sounds(val attack: Int, val defend: Int, val death: Int)

    /**
     * Sound effects by family prefix, for the monster families whose npcs carry no `attack_sound`,
     * `defend_sound` or `death_sound` params in the cache. The ids are the ones the cache does
     * attach to a member of the family where one exists (the giant spider `npc.giantspider1` and
     * the small `npc.spider`), and the OSRS wiki "List of sound IDs" names otherwise
     * (`ghost_attack`, `ghost_hit`, `ghost_death`).
     */
    private val familySounds: Map<String, Sounds> =
        mapOf(
            "ghost_update_tendrill" to Sounds(attack = 436, defend = 439, death = 438),
            "ghost_update_normal" to Sounds(attack = 436, defend = 439, death = 438),
            "spider_update" to Sounds(attack = 537, defend = 539, death = 538),
            "small_spider_update" to Sounds(attack = 3604, defend = 3609, death = 3608),
        )

    /**
     * Suffixes that mark a sequence as an attack when the family does not use `_attack`:
     * `olaf2_undead_sword_lunge`, `barrow_dharok_slash`.
     */
    private val attackWords =
        listOf("_attack", "_slash", "_lunge", "_stab", "_crush", "_hack", "_punch")

    /** Ready-animation markers, longest first so `_ready_update` wins over `_ready`. */
    private val readyMarkers =
        listOf("_just_ready_update", "_ready_update", "_readyanim", "_ready", "_idle", "_stand")

    fun resolve(readyAnim: String, sequences: Set<String>): Family {
        val splits = split(readyAnim)
        if (splits.isEmpty()) {
            return Family(null, null, null)
        }
        val sounds = splits.firstNotNullOfOrNull { (prefix, _) -> familySounds[prefix] }
        return Family(
            attack = splits.firstNotNullOfOrNull { attackFor(it, sequences) },
            defend = splits.firstNotNullOfOrNull { defendFor(it, sequences) },
            death = splits.firstNotNullOfOrNull { deathFor(it, sequences) },
            attackSound = sounds?.attack,
            defendSound = sounds?.defend,
            deathSound = sounds?.death,
        )
    }

    /** `zombie_update_ready_weapon` -> (`zombie_update`, `_weapon`); `cow_ready` -> (`cow`, ``). */
    internal fun split(readyAnim: String): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        for (marker in readyMarkers) {
            val index = readyAnim.indexOf(marker)
            if (index <= 0) {
                continue
            }
            val prefix = readyAnim.substring(0, index)
            val variant = readyAnim.substring(index + marker.length)
            out += prefix to variant
            // A variant like `_sword` may not exist for every member of the set; try without it.
            if (variant.isNotEmpty()) {
                out += prefix to ""
            }
            // The variant can also sit before the marker: `ork_update_weapon_ready` pairs with
            // `ork_update_defend`, and `mummy_update_soldier_ready` with `mummy_update_death`.
            val infix = prefix.lastIndexOf('_')
            if (infix > 0 && variant.isEmpty()) {
                out += prefix.substring(0, infix) to prefix.substring(infix)
                out += prefix.substring(0, infix) to ""
            }
        }
        return out.distinct()
    }

    private fun attackFor(split: Pair<String, String>, sequences: Set<String>): String? {
        val (prefix, variant) = split
        val exact =
            listOf(
                "${prefix}_attack$variant",
                "$prefix${variant}_attack",
                "${prefix}_attack",
                "${prefix}_unarmed_attack",
                "${prefix}_attack_unarmed",
                "${prefix}_attack_normal",
                "${prefix}_attack_melee",
                "${prefix}_melee$variant",
                "${prefix}_melee",
                "${prefix}_slash$variant",
                "${prefix}_slash",
                "${prefix}_crush",
                "${prefix}_stab",
                "${prefix}_lunge",
                "${prefix}_hack",
                "${prefix}_punch",
            )
        exact.firstOrNull { it in sequences }?.let {
            return it
        }
        // No exact member. If the family has attack animations, pick the plain melee one when
        // that is unambiguous; a family with several equally plausible attacks stays unresolved
        // so a wrong animation is never guessed.
        val candidates =
            sequences.filter { seq ->
                seq.startsWith("${prefix}_") &&
                    (seq.startsWith("${prefix}_attack") || attackWords.any { seq.endsWith(it) }) &&
                    !seq.endsWith("_transparent") &&
                    !seq.contains("_spotanim") &&
                    !seq.contains("_projanim") &&
                    !seq.contains("_special") &&
                    !seq.contains("_ranged") &&
                    !seq.contains("_magic") &&
                    !seq.contains("_cast")
            }
        if (candidates.size == 1) {
            return candidates.single()
        }
        val preferred = candidates.filter { it.contains("weapon") || it.contains("unarmed") }
        return preferred.singleOrNull()
    }

    private fun defendFor(split: Pair<String, String>, sequences: Set<String>): String? {
        val (prefix, variant) = split
        val exact =
            listOf(
                "${prefix}_defend$variant",
                "$prefix${variant}_defend",
                "${prefix}_block$variant",
                "$prefix${variant}_block",
                "${prefix}_parry$variant",
                "$prefix${variant}_parry",
                "${prefix}_def$variant",
                "${prefix}_defend",
                "${prefix}_block",
                "${prefix}_parry",
                "${prefix}_def",
                "${prefix}_defence",
                "${prefix}_sword_def",
                "${prefix}_sword_defend",
                "${prefix}_sword_block",
            )
        return exact.firstOrNull { it in sequences }
    }

    private fun deathFor(split: Pair<String, String>, sequences: Set<String>): String? {
        val (prefix, variant) = split
        val exact =
            listOf(
                "${prefix}_death$variant",
                "$prefix${variant}_death",
                "${prefix}_die$variant",
                "${prefix}_death",
                "${prefix}_die",
                "${prefix}_dead",
                "${prefix}_dies",
            )
        return exact.firstOrNull { it in sequences }
    }
}
