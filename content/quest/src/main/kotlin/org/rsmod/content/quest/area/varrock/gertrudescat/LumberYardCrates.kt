package org.rsmod.content.quest.area.varrock.gertrudescat

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onAiTimer
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.varrock.gertrudescat.GertrudesCatQuest.Companion.FLUFFS_KITTEN
import org.rsmod.content.quest.area.varrock.gertrudescat.GertrudesCatQuest.Companion.STAGE_GAVE_SARDINE
import org.rsmod.game.entity.Npc
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The six invisible "Crate" npcs around the lumber yard that mew now and then and can be
 * searched. Once Fluffs has eaten the sardine, one of them (fixed per player) holds her kitten.
 */
class LumberYardCrates
@Inject
constructor(
    private val gertrudesCat: GertrudesCatQuest,
    private val random: GameRandom,
    private val worldRepo: WorldRepository,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onAiTimer(MEW_CRATE) { npc.mewTimer() }
        onOpNpc1(MEW_CRATE) { searchCrate(it.npc) }
    }

    private fun Npc.mewTimer() {
        aiTimer(random.of(MEW_MIN_TICKS..MEW_MAX_TICKS))
        say("Mew!")
        worldRepo.soundArea(this, MEW_SOUND, radius = MEW_RADIUS)
    }

    private suspend fun ProtectedAccess.searchCrate(npc: Npc) {
        arriveDelay()
        faceEntitySquare(npc)
        anim("seq.human_pickuptable")
        delay(1)

        val stage = gertrudesCat.stage(player)
        if (stage != STAGE_GAVE_SARDINE || inv.count(FLUFFS_KITTEN) > 0) {
            mes("You search the crate but find nothing.")
            return
        }
        if (crateIndex(npc) != gertrudesCat.kittenCrate.get(player)) {
            mes("You search the crate but find nothing. The mewing seems to be coming from somewhere else.")
            return
        }
        if (inv.freeSpace() < 1) {
            mes("You find a kitten, but you have no room in your pack to carry it.")
            return
        }
        invAdd(inv, FLUFFS_KITTEN)
        npc.say("Mew!")
        soundSynth(MEW_SOUND)
        mes("You find a kitten! You carefully place it in your backpack.")
    }

    /** Which of [CRATES] this npc is, by the spawn tile it stands closest to. */
    private fun crateIndex(npc: Npc): Int =
        CRATES.indices.minByOrNull { npc.coords.chebyshevDistance(CRATES[it]) } ?: -1

    companion object {
        const val MEW_CRATE = "npc.kittens_mew"
        const val MEW_SOUND = "synth.kittens_mew"

        private const val MEW_MIN_TICKS = 20
        private const val MEW_MAX_TICKS = 45
        private const val MEW_RADIUS = 8

        /** Spawn tiles of the mewing crate npcs, in the order the kitten crate is picked from. */
        val CRATES =
            listOf(
                CoordGrid(3298, 3514, 0),
                CoordGrid(3303, 3506, 0),
                CoordGrid(3305, 3500, 0),
                CoordGrid(3307, 3507, 0),
                CoordGrid(3310, 3499, 0),
                CoordGrid(3315, 3515, 0),
            )
    }
}
