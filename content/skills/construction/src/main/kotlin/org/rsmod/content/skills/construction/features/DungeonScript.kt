package org.rsmod.content.skills.construction.features

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Maps a built guard/guardian furniture loc to the npc that stands on it. All pairs are verified
 * gamevals: built locs 13366-13378 mirror npc ids 130-142 name-for-name.
 *
 * Dungeon and oubliette "Guard space" monsters: skeleton, guard dog, hobgoblin, troll, huge (giant)
 * spider, hellhound, baby red dragon and steel dragon. Treasure-room "Monster space" guardians:
 * demon, kalphite soldier, TzHaar (Tok-Xil), dagannoth and steel dragon. The oubliette ogre has an
 * npc (`npc.poh_ogre`, 136) but no `loc.poh_ogre` built loc in the cache, so it is intentionally
 * absent.
 */
internal object PohGuards {
    val NPC_BY_BUILT_LOC: Map<String, String> =
        mapOf(
            "loc.poh_skeleton" to "npc.poh_skeleton",
            "loc.poh_guarddog" to "npc.poh_guarddog",
            "loc.poh_hobgoblin" to "npc.poh_hobgoblin",
            "loc.poh_troll" to "npc.poh_troll",
            "loc.poh_giantspider" to "npc.poh_giantspider",
            "loc.poh_hellhound" to "npc.poh_hellhound",
            "loc.poh_babyreddragon" to "npc.poh_babyreddragon",
            "loc.poh_oub_monster1" to "npc.poh_oub_monster1",
            "loc.poh_kalphite_soldier" to "npc.poh_kalphite_soldier",
            "loc.poh_steel_dragon" to "npc.poh_steel_dragon",
            "loc.poh_dagganoth" to "npc.poh_dagganoth",
            "loc.poh_tok_xil" to "npc.poh_tok_xil",
        )
}

/**
 * Dungeon, oubliette and treasure-room content.
 *
 * Monster spawning itself is owned by [PohNpcSpawner], which reads [PohGuards.NPC_BY_BUILT_LOC]
 * against the house's built furniture on every `PohHouseEnteredEvent`. This script binds only the
 * treasure-room chest interaction; dungeon stairs, trapdoors and oubliette ladders belong to the
 * stairs script.
 */
class DungeonScript : PluginScript() {
    override fun ScriptContext.startup() {
        for (chest in TREASURE_CHESTS) {
            onOpLoc1(chest) { onTreasureChest() }
        }
    }

    /**
     * TODO: live treasure chests swap to their opened variant and hold loot for house guests
     *   (guests are out of scope for this pass), so for now the chest is a flavour message only.
     */
    private fun ProtectedAccess.onTreasureChest() {
        mes("You open the chest, but find nothing interesting inside.")
    }

    private companion object {
        /** The closed treasure-chest built locs (op1 = Open); ids 13283-13291, verified. */
        val TREASURE_CHESTS =
            listOf(
                "loc.poh_treasure_woodencrate",
                "loc.poh_treasure_oak_chest",
                "loc.poh_treasure_teak_chest",
                "loc.poh_treasure_mag_chest",
                "loc.poh_treasure_magic_chest",
            )
    }
}
