package org.rsmod.content.other.emirsarena

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.dialogue.mesanims
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.stat.statRestore
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpc4
import org.rsmod.api.script.onOpNpc5
import org.rsmod.api.shops.Shops
import org.rsmod.api.shops.operation.ShopOperationMap
import org.rsmod.content.interfaces.bank.openBank
import org.rsmod.content.other.emirsarena.duel.DuelManager
import org.rsmod.content.other.emirsarena.ranked.ArenaRanking
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The people of Emir's Arena: Mubariz and his reward shop, the two guides, the arena guards, the
 * hospital staff who heal for free, Fadli's bank and rotten fruit stall, Chris, and the crowd.
 */
class EmirsArenaNpcScript
@Inject
constructor(
    private val shops: Shops,
    private val shopOperations: ShopOperationMap,
    private val eventBus: EventBus,
    private val manager: DuelManager,
) : PluginScript() {
    override fun ScriptContext.startup() {
        shopOperations.costOf(EmirsArena.REWARD_CURRENCY) { type -> REWARD_PRICES[type.id] ?: type.cost }

        onOpNpc1("npc.duel_mubariz") { startDialogue(it.npc) { mubariz(it.npc) } }
        onOpNpc3("npc.duel_mubariz") { openRewardShop(it.npc) }

        onOpNpc1("npc.pvpa_duel_guide") { startDialogue(it.npc) { duelGuide() } }
        onOpNpc1("npc.pvpa_1v1_guide") { startDialogue(it.npc) { tournamentGuide() } }

        for (guard in GUARDS) {
            onOpNpc1(guard) { startDialogue(it.npc) { arenaGuard() } }
        }

        for (nurse in NURSES) {
            onOpNpc1(nurse) { startDialogue(it.npc) { nurse(it.npc) } }
            onOpNpc3(nurse) { heal(it.npc) }
        }
        onOpNpc1("npc.duel_jaraah") { startDialogue(it.npc) { jaraah() } }
        onOpNpc3("npc.duel_jaraah") { heal(it.npc) }
        onOpNpc1("npc.duel_monk") { startDialogue(it.npc) { hamid() } }
        onOpNpc1("npc.duel_injured1") { startDialogue(it.npc) { zahwa() } }

        onOpNpc1("npc.duel_fadli") { startDialogue(it.npc) { fadli(it.npc) } }
        onOpNpc3("npc.duel_fadli") { player.openBank(eventBus) }
        onOpNpc4("npc.duel_fadli") { mes("The Grand Exchange collection service isn't available here.") }
        onOpNpc5("npc.duel_fadli") { openFruitStall(it.npc) }

        onOpNpc1("npc.pvpa_repairman") { startDialogue(it.npc) { chris() } }
        onOpNpc3("npc.pvpa_repairman") { chatNpc(it.npc, mesanims.sad, "Sorry, nothing's been handed in today.") }

        for (spectator in SPECTATORS) {
            onOpNpc1(spectator) { startDialogue(it.npc) { spectator() } }
        }
    }

    /* Mubariz */

    private suspend fun Dialogue.mubariz(npc: Npc) {
        chatNpc(happy, "Welcome to the Emir's Arena! What can I do for you?")
        mubarizMenu(npc)
    }

    private suspend fun Dialogue.mubarizMenu(npc: Npc) {
        val choice =
            choice5(
                "What is this place?",
                1,
                "How do I get a fight?",
                2,
                "What kind of fights are happening today?",
                3,
                "Can I see your shop?",
                4,
                "I'll be off.",
                5,
            )
        when (choice) {
            1 -> {
                chatPlayer(quiz, "What is this place?")
                chatNpc(
                    happy,
                    "This is the Emir's Arena. Fighters from all over Gielinor come here to " +
                        "test themselves against one another in the combat areas, with the " +
                        "Emir's guards making sure everything stays fair.",
                )
                val more = choice2("Where did this Arena come from?", true, "Thanks.", false)
                if (more) {
                    chatPlayer(quiz, "Where did this Arena come from?")
                    chatNpc(
                        neutral,
                        "It was built in the Second Age and lay buried until the Varrock " +
                            "Museum dug it out. For years it ran as the Duel Arena, until the " +
                            "Calamity Coven got their claws into it and rigged the fights.",
                    )
                    chatNpc(
                        happy,
                        "The Emir bought it, threw the Coven out and reopened it under his own " +
                            "name and his own guards. Nobody rigs anything here now.",
                    )
                }
                mubarizMenu(npc)
            }
            2 -> {
                chatPlayer(quiz, "How do I get a fight?")
                chatNpc(
                    neutral,
                    "For a ranked duel, sign up on one of the recruitment boards by the hospital " +
                        "and the staff will match you with the next fighter who signs up. Win and " +
                        "your rank climbs; either way you earn reward points.",
                )
                chatNpc(
                    neutral,
                    "If you'd rather pick your own opponent and your own rules, just challenge " +
                        "them: right-click anyone inside the Arena and choose Challenge.",
                )
                val challenge = choice2("I challenge you!", true, "Thanks.", false)
                if (challenge) {
                    chatPlayer(angry, "I challenge you!")
                    chatNpc(laugh, "Ha! I've been retired a long time, friend. Find someone your own size.")
                }
                mubarizMenu(npc)
            }
            3 -> {
                chatPlayer(quiz, "What kind of fights are happening today?")
                chatNpc(
                    neutral,
                    "Legacy duels and ranked duels, same as every day. Bring your own gear; the " +
                        "supplies chests are only stocked on the official arena worlds.",
                )
                mubarizMenu(npc)
            }
            4 -> {
                chatPlayer(quiz, "Can I see your shop?")
                chatNpc(happy, "Of course. Everything here is bought with the reward points you earn in ranked duels.")
                player.openRewardShop(npc)
            }
            else -> {
                chatPlayer(neutral, "I'll be off.")
                chatNpc(happy, "See you in the Arena!")
            }
        }
    }

    /** Reward points are spent at face value: no mark-up and no price drift as stock changes. */
    @Suppress("UNUSED_PARAMETER")
    private fun Player.openRewardShop(npc: Npc) {
        shops.open(
            player = this,
            title = "PvP Arena Rewards",
            shopInv = EmirsArena.REWARD_SHOP_INV,
            buyPercentage = 100.0,
            sellPercentage = 0.0,
            changePercentage = 0.0,
            currency = EmirsArena.REWARD_CURRENCY,
        )
    }

    private fun ProtectedAccess.openRewardShop(npc: Npc) = player.openRewardShop(npc)

    /* Guides */

    private suspend fun Dialogue.duelGuide() {
        chatNpc(
            happy,
            "Welcome to the Emir's Arena! Are you interested in a Duel? We have ranked and " +
                "unranked Duels available.",
        )
        duelGuideMenu()
    }

    private suspend fun Dialogue.duelGuideMenu() {
        val choice =
            choice5(
                "What's a Duel?",
                1,
                "Ranked or unranked? What does that mean?",
                2,
                "How do I get invited to a Duel?",
                3,
                "I want to organise my own Duel.",
                4,
                "I'm okay, thanks.",
                5,
            )
        when (choice) {
            1 -> {
                chatPlayer(quiz, "What's a Duel?")
                chatNpc(neutral, "Two fighters are placed in a combat arena together, to battle to the death.")
                chatNpc(happy, "Don't worry: nobody actually dies here. The hospital patches you up and you keep everything you brought.")
                duelGuideMenu()
            }
            2 -> {
                chatPlayer(quiz, "Ranked or unranked? What does that mean?")
                chatNpc(
                    neutral,
                    "Every fighter has a rank. Win a ranked Duel and your rank goes up; lose one " +
                        "and it goes down. The Arena staff arrange ranked matchups, so you never " +
                        "choose your opponent.",
                )
                chatNpc(
                    neutral,
                    "An unranked Duel is a casual fight with no change to your rank. Only ranked " +
                        "Duels earn reward points.",
                )
                duelGuideMenu()
            }
            3 -> {
                chatPlayer(quiz, "How do I get invited to a Duel?")
                chatNpc(
                    neutral,
                    "Sign up on one of the recruitment boards next to the hospital. You can carry " +
                        "on with whatever you were doing inside the Arena while you wait; when " +
                        "another fighter signs up the two of you will be matched.",
                )
                duelGuideMenu()
            }
            4 -> {
                chatPlayer(neutral, "I want to organise my own Duel.")
                chatNpc(
                    neutral,
                    "Challenge someone directly and you'll get an unranked Duel with whatever rules " +
                        "the two of you agree on. Right-click a fighter inside the Arena and choose " +
                        "Challenge; if they challenge you back, the options screen opens.",
                )
                chatNpc(
                    neutral,
                    "Remember the supplies chests are only stocked on the official arena worlds, " +
                        "so bring your own equipment.",
                )
                duelGuideMenu()
            }
            else -> chatPlayer(neutral, "I'm okay, thanks.")
        }
    }

    private suspend fun Dialogue.tournamentGuide() {
        chatNpc(
            happy,
            "Welcome to the Emir's Arena! Are you interested in a 1v1 Tournament? We have " +
                "ranked and unranked Tournaments available.",
        )
        tournamentGuideMenu()
    }

    private suspend fun Dialogue.tournamentGuideMenu() {
        val choice =
            choice5(
                "What's a 1v1 Tournament?",
                1,
                "Ranked or unranked? What does that mean?",
                2,
                "How do I get invited into a Tournament?",
                3,
                "I want to organise my own Tournament.",
                4,
                "I'm okay, thanks.",
                5,
            )
        when (choice) {
            1 -> {
                chatPlayer(quiz, "What's a 1v1 Tournament?")
                chatNpc(
                    neutral,
                    "A group of fighters is divided into pairs, and each pair fights. The winners " +
                        "are paired up again and fight, and so on, until eventually only one " +
                        "person is left standing.",
                )
                tournamentGuideMenu()
            }
            2 -> {
                chatPlayer(quiz, "Ranked or unranked? What does that mean?")
                chatNpc(
                    neutral,
                    "Ranked Tournaments count towards your standing and pay out rewards; " +
                        "unranked ones are just for fun.",
                )
                tournamentGuideMenu()
            }
            3 -> {
                chatPlayer(quiz, "How do I get invited into a Tournament?")
                chatNpc(
                    sad,
                    "I'm afraid the Tournament brackets aren't running at the moment. Sign up " +
                        "for a ranked Duel on the recruitment board instead, or challenge a " +
                        "fighter yourself.",
                )
                tournamentGuideMenu()
            }
            4 -> {
                chatPlayer(neutral, "I want to organise my own Tournament.")
                chatNpc(
                    sad,
                    "Not today, I'm afraid. Until the brackets are running again, the Duel Guide " +
                        "over there can set you up with a one-on-one.",
                )
                tournamentGuideMenu()
            }
            else -> chatPlayer(neutral, "I'm okay, thanks.")
        }
    }

    /* Guards */

    private suspend fun Dialogue.arenaGuard() {
        chatNpc(neutral, "Can I help you?")
        arenaGuardMenu()
    }

    private suspend fun Dialogue.arenaGuardMenu() {
        val choice =
            choice4(
                "What is this place?",
                1,
                "What do you do here?",
                2,
                "Could you come help me with something?",
                3,
                "I'm fine, thanks.",
                4,
            )
        when (choice) {
            1 -> {
                chatPlayer(quiz, "What is this place?")
                chatNpc(
                    neutral,
                    "This is the Emir's Arena. If you want a fight arranged, the recruitment " +
                        "boards are by the hospital to the north.",
                )
                arenaGuardMenu()
            }
            2 -> {
                chatPlayer(quiz, "What do you do here?")
                chatNpc(
                    neutral,
                    "The Emir put us here to protect the Arena and everyone in it. After what " +
                        "the Calamity Coven did, he wasn't taking any chances.",
                )
                val more = choice2("The Calamity Coven?", true, "I see.", false)
                if (more) {
                    chatPlayer(quiz, "The Calamity Coven?")
                    chatNpc(
                        angry,
                        "Three brigands who ran this place into the ground. Maoma rigged the " +
                            "fights, Saika forged the accounts and Koriff whipped up the crowds.",
                    )
                    chatNpc(neutral, "They're gone now, and we make sure nothing like them ever comes back.")
                }
                arenaGuardMenu()
            }
            3 -> {
                chatPlayer(quiz, "Could you come help me with something?")
                chatNpc(neutral, "If the problem isn't in the Arena, it's not my problem.")
                arenaGuardMenu()
            }
            else -> chatPlayer(neutral, "I'm fine, thanks.")
        }
    }

    /* Hospital */

    private suspend fun Dialogue.nurse(npc: Npc) {
        chatPlayer(happy, "Hi!")
        chatNpc(happy, "Hi. How can I help?")
        val choice =
            choice3(
                "Can you heal me?",
                1,
                "Do many fighters get badly hurt?",
                2,
                "Nothing, thanks.",
                3,
            )
        when (choice) {
            1 -> {
                chatPlayer(quiz, "Can you heal me?")
                access.heal(npc)
            }
            2 -> {
                chatPlayer(quiz, "Do many fighters get badly hurt?")
                chatNpc(
                    neutral,
                    "Now and then. Jaraah takes the worst of them. They call him 'The Butcher', " +
                        "but honestly, most of his patients walk out again.",
                )
            }
            else -> chatPlayer(neutral, "Nothing, thanks.")
        }
    }

    private suspend fun ProtectedAccess.heal(npc: Npc) {
        if (manager.duelOf(player)?.isActive == true) {
            chatNpc(npc, mesanims.angry, "Get back in there and finish your fight first!")
            return
        }
        val hurt = player.statBase("stat.hitpoints") > player.stat("stat.hitpoints")
        if (!hurt) {
            chatNpc(npc, mesanims.happy, "You look healthy to me!")
            return
        }
        player.statRestore("stat.hitpoints")
        chatNpc(npc, mesanims.happy, "There you go. Try not to bleed on the floor next time.")
    }

    private suspend fun Dialogue.jaraah() {
        chatNpc(shocked, "Ah! You aren't bleeding. Are you sure you need a surgeon?")
        val choice = choice2("Can you heal me?", true, "I'll leave you to it.", false)
        if (choice) {
            chatPlayer(quiz, "Can you heal me?")
            access.heal(npc ?: return)
        } else {
            chatPlayer(neutral, "I'll leave you to it.")
        }
    }

    private suspend fun Dialogue.hamid() {
        chatPlayer(happy, "Hi!")
        chatNpc(happy, "Hello traveller. How can I be of assistance?")
        val choice =
            choice3(
                "Can you heal me?",
                1,
                "What's a Monk doing in a place such as this?",
                2,
                "Which monastery do you come from?",
                3,
            )
        when (choice) {
            1 -> {
                chatPlayer(quiz, "Can you heal me?")
                chatNpc(happy, "You'd be better off speaking to one of the nurses. They are so... nice... after all!")
            }
            2 -> {
                chatPlayer(quiz, "What's a Monk doing in a place such as this?")
                chatNpc(happy, "Well don't tell anyone but I came here because of the nurses!")
                chatPlayer(shocked, "Really?")
                chatNpc(happy, "It beats being stuck in the monastery!")
            }
            else -> {
                chatPlayer(quiz, "Which monastery do you come from?")
                chatNpc(neutral, "I belong to the monastery north of Falador.")
                chatPlayer(quiz, "You're a long way from home?")
                chatNpc(sad, "Yeh. I miss the guys.")
            }
        }
    }

    private suspend fun Dialogue.zahwa() {
        chatNpc(sad, "Ow... please, keep your voice down. Every part of me hurts.")
        chatPlayer(quiz, "What happened to you?")
        chatNpc(sad, "I signed up for a ranked duel with no armour. Learn from my mistakes, friend.")
    }

    /* Fadli and Chris */

    private suspend fun Dialogue.fadli(npc: Npc) {
        chatNpc(bored, "What?")
        val choice =
            choice5(
                "What do you do?",
                1,
                "I'd like to access my bank, please.",
                2,
                "I'd like to collect items.",
                3,
                "Do you watch any matches?",
                4,
                "Goodbye, Fadli.",
                5,
            )
        when (choice) {
            1 -> {
                chatPlayer(quiz, "What do you do?")
                chatNpc(
                    angry,
                    "I look after everyone's valuables while they fight. Me! A man of my " +
                        "talents, stuck behind a counter while lesser fighters get all the glory!",
                )
                chatPlayer(shocked, "Easy, tiger!")
            }
            2 -> {
                chatPlayer(neutral, "I'd like to access my bank, please.")
                chatNpc(bored, "Sure.")
                player.openBank(eventBus)
            }
            3 -> {
                chatPlayer(neutral, "I'd like to collect items.")
                chatNpc(bored, "The Grand Exchange collection service isn't available here.")
            }
            4 -> {
                chatPlayer(quiz, "Do you watch any matches?")
                chatNpc(
                    happy,
                    "When I can. I sell rotten fruit on the side, if you'd like to make your " +
                        "opinion of the fighters known.",
                )
                val buy = choice2("Let me see the fruit.", true, "No thanks.", false)
                if (buy) {
                    access.openFruitStall(npc)
                } else {
                    chatNpc(bored, "Suit yourself.")
                }
            }
            else -> chatPlayer(neutral, "Goodbye, Fadli.")
        }
    }

    private fun ProtectedAccess.openFruitStall(npc: Npc) {
        shops.open(player, npc, "Pelters' Veg Stall", "inv.duel_rottenfruitshop")
    }

    private suspend fun Dialogue.chris() {
        chatNpc(happy, "Lost something in the Arena? Anything the guards find ends up with me.")
        chatPlayer(quiz, "Have you found anything of mine?")
        chatNpc(sad, "Sorry, nothing's been handed in today. Duels here are safe, so there isn't much to find.")
    }

    /* Crowd */

    private suspend fun Dialogue.spectator() {
        chatNpc(happy, CROWD_LINES.random())
    }

    private companion object {
        private val GUARDS =
            listOf(
                "npc.pvpa_guard_1",
                "npc.pvpa_guard_2_model",
                "npc.pvpa_guard_3_model",
                "npc.pvpa_guard_4",
                "npc.pvpa_guard_5",
                "npc.pvpa_guard_6",
                "npc.pvpa_guard_7",
                "npc.pvpa_guard_8",
            )

        private val NURSES = listOf("npc.duel_nurse1", "npc.duel_nurse2", "npc.duel_nurse3")

        private val SPECTATORS =
            listOf(
                "npc.duel_crowdmale1",
                "npc.duel_crowdmale2",
                "npc.duel_crowdmale3",
                "npc.duel_crowdfemale1",
                "npc.duel_crowdfemale2",
                "npc.duel_crowdfemale3",
            )

        private val CROWD_LINES =
            listOf(
                "Did you see that last fight? Over in three hits!",
                "I've got a rotten tomato with someone's name on it.",
                "Shh! I'm trying to watch the arena.",
                "My money's on the one with the whip. It's always the one with the whip.",
                "The Emir cleaned this place up nicely, didn't he?",
            )

        /** Reward point prices, keyed by obj id. */
        private val REWARD_PRICES: Map<Int, Int> =
            mapOf(
                "obj.blighted_sack_surge".objId() to 10,
                "obj.pvpa_imbuing_scroll".objId() to 200,
            )

        private fun String.objId(): Int = asRSCM(RSCMType.OBJ)
    }
}
