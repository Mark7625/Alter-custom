package org.rsmod.tools.combatanims

/**
 * Renders resolved npc animation sets as the TOML the `npc-combat-anims` plugin reads: one inline
 * table per npc under a single `npc` array, so a diff shows one changed npc per line.
 */
object NpcCombatAnimToml {
    private const val HEADER =
        """# Combat animations for npcs the cache leaves silent - generated, do not edit by hand.
#
# Regenerate with:  ./gradlew :tools:combat-anims:dumpNpcCombatAnims
# Hand corrections:  npc-combat-anims-overrides.toml (same format; those always win).
#
# Each entry names the npc, where its animations came from (a held weapon, a spell caster, or the
# animation family of its ready animation) and the values the plugin sets at boot. Only fields
# present are applied, and only where the npc's own data does not already declare them.
"""

    fun render(entries: List<NpcCombatAnims>): String =
        buildString {
            append(HEADER)
            append('\n')
            append("npc = [\n")
            for (entry in entries.sortedBy { it.npc }) {
                append("    ")
                append(renderEntry(entry))
                append(",\n")
            }
            append("]\n")
        }

    fun renderEntry(entry: NpcCombatAnims): String {
        val fields = mutableListOf<String>()
        fields += "id = \"${entry.npc}\""
        fields += "source = \"${entry.source}\""
        entry.attackAnim?.let { fields += "attack_anim = \"seq.$it\"" }
        entry.attackType?.let { fields += "attack_type = \"$it\"" }
        entry.attackSound?.let { fields += "attack_sound = $it" }
        entry.defendAnim?.let { fields += "defend_anim = \"seq.$it\"" }
        entry.deathAnim?.let { fields += "death_anim = \"seq.$it\"" }
        entry.projTravel?.let { fields += "proj_travel = \"spotanim.$it\"" }
        entry.projType?.let { fields += "proj_type = \"projanim.$it\"" }
        entry.attackRange?.let { fields += "attack_range = $it" }
        return fields.joinToString(", ", prefix = "{ ", postfix = " }")
    }
}
