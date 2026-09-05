package org.rsmod.tools.combatanims

/**
 * Derives a monster's attack, block and death animations from the name of its ready animation.
 *
 * Jagex names an npc's animation set as a family: `giant_update_basic_ready`,
 * `giant_update_basic_attack`, `giant_update_basic_defend`, `giant_update_basic_death`. A variant
 * suffix on the ready animation carries over to the rest of the set: `zombie_update_ready_weapon`
 * pairs with `zombie_update_attack_weapon`. This object knows those conventions, and nothing else -
 * it works on plain sequence names so it can be tested without a cache.
 *
 * Names are the bare gameval names (`giant_update_basic_ready`), without the `seq.` prefix.
 */
object AnimationFamilies {
    data class Family(val attack: String?, val defend: String?, val death: String?) {
        val isEmpty: Boolean
            get() = attack == null && defend == null && death == null
    }

    /** Ready-animation markers, longest first so `_ready_update` wins over `_ready`. */
    private val readyMarkers =
        listOf("_just_ready_update", "_ready_update", "_readyanim", "_ready", "_idle", "_stand")

    fun resolve(readyAnim: String, sequences: Set<String>): Family {
        val splits = split(readyAnim)
        if (splits.isEmpty()) {
            return Family(null, null, null)
        }
        return Family(
            attack = splits.firstNotNullOfOrNull { attackFor(it, sequences) },
            defend = splits.firstNotNullOfOrNull { defendFor(it, sequences) },
            death = splits.firstNotNullOfOrNull { deathFor(it, sequences) },
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
                    (seq.startsWith("${prefix}_attack") || seq.endsWith("_attack")) &&
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
