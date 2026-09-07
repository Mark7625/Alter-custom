package org.rsmod.content.other.pets.cats

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import dev.openrune.types.NpcMode
import dev.openrune.util.Wearpos
import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onOpHeld5
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpc4
import org.rsmod.api.script.onOpNpc5
import org.rsmod.api.script.onOpNpcU
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.game.entity.Npc
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Pet cats: kittens, cats, overgrown cats and lazy cats that follow their owner.
 *
 * Dropping the held cat releases it as a follower; `Pick-up` returns it to the inventory. A
 * kitten must be fed (fish or milk) and given attention (stroking or a ball of wool) or it runs
 * away; after three hours of following it grows into a cat, which after a further five and a
 * half hours becomes an overgrown cat. Cats and kittens can chase rats; overgrown and lazy cats
 * cannot. Wily cats do not exist in this cache revision and are not handled.
 */
class CatPetScript
@Inject
constructor(private val cats: CatPetManager, private val npcRepo: NpcRepository) :
    PluginScript() {

    override fun ScriptContext.startup() {
        onPlayerLogin { cats.followingPet(player)?.let { cats.spawn(player, it) } }
        onPlayerLogout { cats.despawn(player) }
        onPlayerSoftTimer(CatPetManager.TICK_TIMER) { cats.tick(player) }

        for (pet in CatPet.all) {
            onOpHeld5(pet.obj) { release(pet, it.slot) }
            onOpNpc1(pet.npc) { pickUp(it.npc, pet) }
            onOpNpc3(pet.npc) { talkTo(it.npc, pet) }
            onOpNpc4(pet.npc) { chase(it.npc, pet) }
            onOpNpc5(pet.npc) { interact(it.npc, pet) }
            onOpNpcU(pet.npc) { useItem(it.npc, pet, it.objType, it.invSlot) }
        }

        onCommand("cat") {
            desc = "Spawn a pet cat obj: ::cat <kitten|cat|overgrown|lazy> [colour 0-6]"
            requiredRights = Rights.ADMINISTRATOR
            cheat {
                val stage =
                    when (args.getOrNull(0)?.lowercase()) {
                        "kitten" -> CatStage.KITTEN
                        "cat" -> CatStage.CAT
                        "overgrown" -> CatStage.OVERGROWN
                        "lazy" -> CatStage.LAZY
                        else -> {
                            player.mes("Usage: ::cat <kitten|cat|overgrown|lazy> [colour 0-6]")
                            return@cheat
                        }
                    }
                val colour = CatColour.entries[args.getOrNull(1)?.toInt() ?: 0]
                val pet = CatPet(stage, colour)
                if (player.inv.isFull()) {
                    player.mes("You don't have enough inventory space.")
                    return@cheat
                }
                player.invAdd(player.inv, pet.obj, count = 1)
                player.mes("Spawned ${pet.obj}. Drop it to have it follow you.")
            }
        }

        onCommand("catgrow") {
            desc = "Make your following cat grow into its next stage on the next cycle"
            requiredRights = Rights.ADMINISTRATOR
            cheat {
                if (cats.forceGrowth(player)) {
                    player.mes("Your cat will grow on the next cycle.")
                } else {
                    player.mes("You have no following cat that can grow.")
                }
            }
        }

        onCommand("catstatus") {
            desc = "Show your following cat's growth, hunger and attention counters"
            requiredRights = Rights.ADMINISTRATOR
            cheat { player.mes(cats.status(player)) }
        }
    }

    /* Release / pick-up */

    private suspend fun ProtectedAccess.release(pet: CatPet, slot: Int) {
        if (cats.hasFollower(player)) {
            mes("You already have a follower.")
            return
        }
        if (invDel(inv, pet.obj, count = 1, slot = slot).failure) {
            return
        }
        val npc = cats.spawn(player, pet)
        anim("seq.human_pickupfloor")
        npc.say("Miaooow!")
        soundSynth(pet.mewSynth)
        mes("You put down your ${pet.stage.label} and it starts to follow you.")
    }

    private suspend fun ProtectedAccess.pickUp(npc: Npc, pet: CatPet) {
        if (!ownsCat(npc)) {
            return
        }
        arriveDelay()
        if (inv.isFull()) {
            mes("You don't have enough inventory space to pick up your ${pet.stage.label}.")
            return
        }
        faceEntitySquare(npc)
        anim("seq.human_pickupfloor")
        if (invAdd(inv, pet.obj, count = 1).failure) {
            return
        }
        cats.release(player)
        soundSynth(pet.mewSynth)
        mes("You pick up your ${pet.stage.label}.")
    }

    /* Talk-to */

    private suspend fun ProtectedAccess.talkTo(npc: Npc, pet: CatPet) {
        if (!ownsCat(npc)) {
            return
        }
        arriveDelay()
        val speaksCat = wearsCatspeakAmulet()
        startDialogue(npc) {
            chatPlayer(quiz, "Hey kitty. What's new?")
            if (!speaksCat) {
                npc.say(if (pet.isKitten) "Meow!" else "Miaow!")
                access.soundSynth(pet.mewSynth)
                access.mes("You don't understand a word your ${pet.stage.label} says.")
                return@startDialogue
            }
            catReply(npc, pet)
        }
    }

    private suspend fun Dialogue.catReply(npc: Npc, pet: CatPet) {
        val hungry = pet.isKitten && cats.isHungry(player)
        val lonely = pet.isKitten && cats.isLonely(player)
        val nearlyGrown = cats.growthPercent(player, pet) >= 80
        val line =
            when {
                hungry -> "I'm really hungry, do you have any fish, meeaow?"
                lonely ->
                    "You don't think I'm cute and cuddly any more, do you? Is there another cat " +
                        "you like?"
                pet.stage == CatStage.KITTEN -> "Meeow I'm happy."
                pet.stage == CatStage.CAT && nearlyGrown ->
                    "I'm not as young as I used be, I think I'm beginning to get a bit fat too."
                pet.stage == CatStage.CAT ->
                    "I'm good. But could we go adventuring soon, I'm tired of talking, meeoow?"
                pet.stage == CatStage.OVERGROWN ->
                    "I'm a bit too big to be chasing rats these days, meeoow. Let's just take " +
                        "things easy."
                else -> "Zzz... I'm far too comfortable to go anywhere, meeoow."
            }
        access.soundSynth(if (hungry || lonely) "synth.sad_meeoow" else pet.mewSynth)
        chatNpc(if (hungry || lonely) sad else happy, line)
        npc.say(if (hungry || lonely) "Meeeooow..." else "Purr")
    }

    /* Chase */

    private suspend fun ProtectedAccess.chase(npc: Npc, pet: CatPet) {
        if (!ownsCat(npc)) {
            return
        }
        arriveDelay()
        if (!pet.stage.canChase) {
            mes("Your ${pet.stage.label} is far too big and lazy to go chasing rats.")
            return
        }
        val rat = nearestRat(npc)
        if (rat == null) {
            npc.say("Meow?")
            soundSynth(pet.mewSynth)
            mes("Your ${pet.stage.label} looks around but can't see anything to chase.")
            return
        }
        cats.setChasing(player, true)
        try {
            npc.mode = NpcMode.None
            npc.resetFaceEntity()
            npc.walk(rat.coords)
            val steps = npc.coords.chebyshevDistance(rat.coords).coerceIn(1, 4)
            delay(steps)
            npc.faceSquare(rat.coords)
            npc.anim("seq.cat_pounce")
            soundSynth("synth.cat_hiss")
            delay(1)
            val chance = if (pet.isKitten) KITTEN_CATCH_CHANCE else CAT_CATCH_CHANCE
            if (rat.isSlotAssigned && random.of(maxExclusive = 100) < chance) {
                npcRepo.despawn(rat, rat.type.respawnRate)
                npc.say("Purr!")
                soundSynth("synth.purr")
                mes("Your ${pet.stage.label} pounces on the rat and catches it!")
            } else {
                mes("The rat is too quick and scurries away from your ${pet.stage.label}.")
            }
        } finally {
            cats.setChasing(player, false)
        }
    }

    private fun nearestRat(cat: Npc): Npc? =
        npcRepo
            .findAll(ZoneKey.from(cat.coords), zoneRadius = 1)
            .filter { it.isSlotAssigned && it.isVisible && it.type.name == "Rat" }
            .filter { it.coords.level == cat.coords.level }
            .filter { it.coords.chebyshevDistance(cat.coords) <= CHASE_RANGE }
            .minByOrNull { it.coords.chebyshevDistance(cat.coords) }

    /* Interact: stroke / shoo away */

    private suspend fun ProtectedAccess.interact(npc: Npc, pet: CatPet) {
        if (!ownsCat(npc)) {
            return
        }
        arriveDelay()
        startDialogue(npc) {
            val choice = choice2("Stroke", 1, "Shoo away", 2)
            when (choice) {
                1 -> stroke(npc, pet)
                2 -> shooAway(npc, pet)
            }
        }
    }

    private suspend fun Dialogue.stroke(npc: Npc, pet: CatPet) {
        access.faceEntitySquare(npc)
        access.anim("seq.human_pickupfloor")
        npc.anim("seq.cat_rollingover")
        npc.say("Purr...purr...")
        access.soundSynth("synth.purr")
        cats.stroke(player)
        access.mes("You stroke your ${pet.stage.label}. It purrs contentedly.")
    }

    private suspend fun Dialogue.shooAway(npc: Npc, pet: CatPet) {
        mesbox("Are you sure you want to shoo your ${pet.stage.label} away? It won't come back.")
        val confirm = choice2("Yes, shoo it away.", true, "No, I want to keep it.", false)
        if (!confirm) {
            return
        }
        access.faceEntitySquare(npc)
        npc.anim("seq.cat_archback")
        npc.say("Meeeooow!")
        access.soundSynth("synth.cat_hiss")
        cats.runAway(player)
        access.mes("You shoo your ${pet.stage.label} away. It runs off into the distance.")
    }

    /* Use item on cat */

    private suspend fun ProtectedAccess.useItem(
        npc: Npc,
        pet: CatPet,
        objType: ItemServerType,
        slot: Int,
    ) {
        if (!ownsCat(npc)) {
            return
        }
        arriveDelay()
        when {
            objType.id == BALL_OF_WOOL -> playWithWool(npc, pet, slot)
            objType.id == BUCKET_OF_MILK -> feedMilk(npc, pet, slot)
            objType.id in fishIds -> feedFish(npc, pet, objType, slot)
            else -> mes("Your ${pet.stage.label} isn't interested in that.")
        }
    }

    private fun ProtectedAccess.feedFish(npc: Npc, pet: CatPet, fish: ItemServerType, slot: Int) {
        if (invDel(inv, fish.internalName, count = 1, slot = slot).failure) {
            return
        }
        faceEntitySquare(npc)
        anim("seq.human_pickupfloor")
        npc.anim("seq.cat_paw")
        npc.say("Purr!")
        soundSynth("synth.purr")
        cats.feed(player)
        mes("The ${pet.stage.label} gobbles up the fish.")
    }

    private fun ProtectedAccess.feedMilk(npc: Npc, pet: CatPet, slot: Int) {
        val replace = invReplace(inv, "obj.bucket_milk", count = 1, replacement = "obj.bucket_empty")
        if (replace.failure) {
            return
        }
        faceEntitySquare(npc)
        anim("seq.human_pickupfloor")
        npc.anim("seq.cat_paw")
        npc.say("Purpurr")
        soundSynth("synth.purr")
        cats.feed(player)
        mes("The ${pet.stage.label} laps up the milk.")
        if (pet.isHellcat) {
            // Milk turns a hellcat back into a normal cat of the default colour.
            val normal = CatPet(pet.stage, CatColour.DEFAULT)
            cats.spawn(player, normal)
            mes("The milk washes the hellfire out of your ${pet.stage.label}'s fur.")
        }
    }

    private fun ProtectedAccess.playWithWool(npc: Npc, pet: CatPet, slot: Int) {
        if (invDel(inv, "obj.ball_of_wool", count = 1, slot = slot).failure) {
            return
        }
        faceEntitySquare(npc)
        anim("seq.human_pickupfloor")
        npc.anim("seq.cat_pounce")
        npc.say("Purr!")
        soundSynth("synth.purr")
        cats.play(player)
        mes(
            "That ${pet.stage.label} loves to play with that ball of wool. I think it is its " +
                "favourite."
        )
    }

    /* Helpers */

    private fun ProtectedAccess.ownsCat(npc: Npc): Boolean {
        if (cats.isOwnedBy(npc, player)) {
            return true
        }
        mes("That's not your cat.")
        return false
    }

    private fun ProtectedAccess.wearsCatspeakAmulet(): Boolean {
        val worn = player.worn[Wearpos.Front.slot] ?: return false
        return worn.id in catspeakAmulets
    }

    private val CatPet.mewSynth: String
        get() = if (isKitten) "synth.kittens_mew" else "synth.meeoow"

    private companion object {
        const val CHASE_RANGE: Int = 6
        const val KITTEN_CATCH_CHANCE: Int = 30
        const val CAT_CATCH_CHANCE: Int = 50

        val BALL_OF_WOOL: Int = "obj.ball_of_wool".asRSCM(RSCMType.OBJ)
        val BUCKET_OF_MILK: Int = "obj.bucket_milk".asRSCM(RSCMType.OBJ)

        val catspeakAmulets: Set<Int> =
            setOf(
                "obj.twocats_amuletofcatspeak".asRSCM(RSCMType.OBJ),
                "obj.ics_little_amulet_of_catspeak".asRSCM(RSCMType.OBJ),
            )

        /** Raw and cooked fish a cat will eat, plus roe, caviar and frog spawn. */
        val fishIds: Set<Int> =
            listOf(
                    "obj.raw_shrimp",
                    "obj.shrimp",
                    "obj.raw_anchovies",
                    "obj.anchovies",
                    "obj.raw_sardine",
                    "obj.sardine",
                    "obj.seasoned_sardine",
                    "obj.raw_herring",
                    "obj.herring",
                    "obj.raw_mackerel",
                    "obj.mackerel",
                    "obj.raw_trout",
                    "obj.trout",
                    "obj.raw_cod",
                    "obj.cod",
                    "obj.raw_pike",
                    "obj.pike",
                    "obj.raw_salmon",
                    "obj.salmon",
                    "obj.raw_tuna",
                    "obj.tuna",
                    "obj.raw_lobster",
                    "obj.lobster",
                    "obj.raw_bass",
                    "obj.bass",
                    "obj.raw_swordfish",
                    "obj.swordfish",
                    "obj.raw_monkfish",
                    "obj.monkfish",
                    "obj.raw_shark",
                    "obj.shark",
                    "obj.raw_seaturtle",
                    "obj.seaturtle",
                    "obj.raw_mantaray",
                    "obj.mantaray",
                    "obj.raw_anglerfish",
                    "obj.anglerfish",
                    "obj.raw_dark_crab",
                    "obj.dark_crab",
                    "obj.raw_cave_eel",
                    "obj.cave_eel",
                    "obj.raw_lava_eel",
                    "obj.lava_eel",
                    "obj.mort_slimey_eel",
                    "obj.mort_slimey_eel_cooked",
                    "obj.raw_giant_carp",
                    "obj.hunting_raw_fish_special",
                    "obj.hunting_fish_special",
                    "obj.tbwt_raw_karambwan",
                    "obj.tbwt_cooked_karambwan",
                    "obj.tbwt_poorly_cooked_karambwan",
                    "obj.tbwt_raw_karambwanji",
                    "obj.brut_roe",
                    "obj.brut_caviar",
                    "obj.giant_frogspawn",
                )
                .map { it.asRSCM(RSCMType.OBJ) }
                .toSet()
    }
}
