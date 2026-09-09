package org.rsmod.content.quest.area.varrock.gertrudescat.npcs

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpc4
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.quest.area.varrock.gertrudescat.GertrudesCatQuest
import org.rsmod.content.quest.area.varrock.gertrudescat.GertrudesCatQuest.Companion.BUCKET_EMPTY
import org.rsmod.content.quest.area.varrock.gertrudescat.GertrudesCatQuest.Companion.BUCKET_OF_MILK
import org.rsmod.content.quest.area.varrock.gertrudescat.GertrudesCatQuest.Companion.FLUFFS_KITTEN
import org.rsmod.content.quest.area.varrock.gertrudescat.GertrudesCatQuest.Companion.SEASONED_SARDINE
import org.rsmod.content.quest.area.varrock.gertrudescat.GertrudesCatQuest.Companion.STAGE_GAVE_MILK
import org.rsmod.content.quest.area.varrock.gertrudescat.GertrudesCatQuest.Companion.STAGE_GAVE_SARDINE
import org.rsmod.content.quest.area.varrock.gertrudescat.GertrudesCatQuest.Companion.STAGE_KITTEN_RETURNED
import org.rsmod.content.quest.area.varrock.gertrudescat.GertrudesCatQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.varrock.gertrudescat.LumberYardCrates
import org.rsmod.game.entity.Npc
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Fluffs, Gertrude's cat, hiding on the upper floor of the lumber yard's shed. */
class Fluffs
@Inject
constructor(
    private val gertrudesCat: GertrudesCatQuest,
    private val npcRepo: NpcRepository,
    private val random: GameRandom,
) : PluginScript() {

    private val milkId = BUCKET_OF_MILK.asRSCM(RSCMType.OBJ)
    private val sardineId = SEASONED_SARDINE.asRSCM(RSCMType.OBJ)
    private val kittenId = FLUFFS_KITTEN.asRSCM(RSCMType.OBJ)

    override fun ScriptContext.startup() {
        onOpNpc1(FLUFFS) { pickUp(it.npc) }
        onOpNpc3(FLUFFS) { stroke(it.npc) }
        onOpNpc4(FLUFFS) { talkTo(it.npc) }
        onOpNpcU(FLUFFS) { useItem(it.npc, it.objType, it.invSlot) }
    }

    private suspend fun ProtectedAccess.talkTo(npc: Npc) {
        arriveDelay()
        faceEntitySquare(npc)
        npc.facePlayer(player)
        noteFound()
        npc.say("Miaoww")
        soundSynth(MEOW_SOUND)
    }

    private suspend fun ProtectedAccess.pickUp(npc: Npc) {
        hiss(npc)
    }

    private suspend fun ProtectedAccess.stroke(npc: Npc) {
        hiss(npc)
    }

    /** Fluffs won't be handled until she is content, and hints at what she wants next. */
    private suspend fun ProtectedAccess.hiss(npc: Npc) {
        arriveDelay()
        faceEntitySquare(npc)
        npc.facePlayer(player)
        noteFound()
        val stage = gertrudesCat.stage(player)
        if (stage >= STAGE_KITTEN_RETURNED) {
            npc.say("Purr...")
            soundSynth(PURR_SOUND)
            mes("Fluffs purrs contentedly.")
            return
        }
        anim("seq.human_pickupfloor")
        npc.anim("seq.cat_archback")
        npc.say("Hisss!")
        soundSynth(HISS_SOUND)
        delay(1)
        say("Ouch!")
        delay(1)
        mes(
            when {
                stage < STAGE_STARTED -> "The cat doesn't seem to want to be handled."
                stage < STAGE_GAVE_MILK -> "Maybe the cat is thirsty?"
                stage < STAGE_GAVE_SARDINE -> "Maybe the cat is hungry?"
                else -> "The cat seems afraid to leave. In the distance you can hear kittens mewing..."
            },
        )
    }

    private suspend fun ProtectedAccess.useItem(npc: Npc, objType: ItemServerType, slot: Int) {
        arriveDelay()
        faceEntitySquare(npc)
        npc.facePlayer(player)
        noteFound()
        when (objType.id) {
            milkId -> giveMilk(npc)
            sardineId -> giveSardine(npc, slot)
            kittenId -> returnKitten(npc, slot)
            else -> mes("Fluffs isn't interested in that.")
        }
    }

    private suspend fun ProtectedAccess.giveMilk(npc: Npc) {
        val stage = gertrudesCat.stage(player)
        if (stage < STAGE_STARTED) {
            mes("The cat eyes the bucket suspiciously and backs away.")
            return
        }
        if (stage >= STAGE_GAVE_MILK) {
            mes("Fluffs has had all the milk she wants.")
            return
        }
        if (invReplace(inv, BUCKET_OF_MILK, 1, BUCKET_EMPTY).failure) {
            return
        }
        anim("seq.human_pickupfloor")
        npc.anim("seq.cat_paw")
        npc.say("Mew!")
        soundSynth(MEW_SOUND)
        mes("Fluffs laps up the milk.")
        gertrudesCat.quest.advanceQuestStage(access = this, amount = STAGE_GAVE_MILK - stage)
    }

    private suspend fun ProtectedAccess.giveSardine(npc: Npc, slot: Int) {
        val stage = gertrudesCat.stage(player)
        if (stage < STAGE_GAVE_MILK) {
            mes("The cat sniffs at the sardine but won't eat. Maybe the cat is thirsty?")
            return
        }
        if (stage >= STAGE_GAVE_SARDINE) {
            mes("Fluffs has eaten her fill.")
            return
        }
        if (invDel(inv, SEASONED_SARDINE, 1, slot = slot).failure) {
            return
        }
        anim("seq.human_pickupfloor")
        npc.anim("seq.cat_paw")
        npc.say("Mew!")
        soundSynth(MEW_SOUND)
        mes("Fluffs gobbles up the sardine.")
        gertrudesCat.kittenCrate.set(player, random.of(LumberYardCrates.CRATES.indices))
        gertrudesCat.quest.advanceQuestStage(this)
        delay(2)
        mes("The cat still seems afraid to leave. In the distance you can hear kittens mewing...")
    }

    /** Fluffs is reunited with her kitten and the two of them head home. */
    private suspend fun ProtectedAccess.returnKitten(npc: Npc, slot: Int) {
        val stage = gertrudesCat.stage(player)
        if (stage != STAGE_GAVE_SARDINE) {
            mes("Fluffs doesn't seem interested in that right now.")
            return
        }
        if (invDel(inv, FLUFFS_KITTEN, 1, slot = slot).failure) {
            return
        }
        anim("seq.human_pickupfloor")
        val kitten = Npc(KITTEN, kittenTile(npc))
        npcRepo.add(kitten, KITTEN_VISIBLE_TICKS)
        kitten.facePlayer(player)

        npc.say("Purr...")
        kitten.say("Purr...")
        soundSynth(PURR_SOUND)
        gertrudesCat.quest.advanceQuestStage(this)
        delay(3)

        npc.walk(HOME_TILE)
        kitten.walk(HOME_TILE.translate(-1, 0))
        delay(4)
        mes("Fluffs has run off home with her kitten.")
        if (kitten.isSlotAssigned) {
            npcRepo.del(kitten, Int.MAX_VALUE)
        }
        if (npc.isSlotAssigned) {
            npcRepo.hide(npc, FLUFFS_AWAY_TICKS)
        }
    }

    private fun ProtectedAccess.kittenTile(npc: Npc): CoordGrid {
        val candidates =
            listOf(
                npc.coords.translate(1, 0),
                npc.coords.translate(-1, 0),
                npc.coords.translate(0, 1),
                npc.coords.translate(0, -1),
            )
        return candidates.firstOrNull { it != player.coords && !mapBlocked(it) } ?: npc.coords
    }

    /** Remembers that the player has met Fluffs, which unlocks Gertrude's sardine advice. */
    private fun ProtectedAccess.noteFound() {
        if (gertrudesCat.stage(player) >= STAGE_STARTED && !gertrudesCat.foundFluffs.get(player)) {
            gertrudesCat.foundFluffs.set(player, true)
        }
    }

    private companion object {
        const val FLUFFS = "npc.gertrudescat"
        const val KITTEN = "npc.lostkitten"

        const val MEOW_SOUND = "synth.meeoow"
        const val MEW_SOUND = "synth.kittens_mew"
        const val HISS_SOUND = "synth.cat_hiss"
        const val PURR_SOUND = "synth.purr"

        /** Beside the top of the shed ladder; Fluffs and her kitten head here to "go home". */
        val HOME_TILE = CoordGrid(3309, 3510, 1)

        const val KITTEN_VISIBLE_TICKS = 20

        /** How long Fluffs stays away from the shed after running home. */
        const val FLUFFS_AWAY_TICKS = 100
    }
}
