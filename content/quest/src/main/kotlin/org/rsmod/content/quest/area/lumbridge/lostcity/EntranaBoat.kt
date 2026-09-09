package org.rsmod.content.quest.area.lumbridge.lostcity

import dev.openrune.types.ItemServerType
import dev.openrune.util.Wearpos
import jakarta.inject.Inject
import org.rsmod.api.config.refs.params
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.game.entity.Player
import org.rsmod.game.type.getInvObj
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The monks' ship between Port Sarim and Entrana, and the gangplanks at both ends.
 *
 * Saradomin's edict: no weapons or armour may be taken to the island. The monks at Port Sarim
 * search the player once per session and refuse passage while anything forbidden is carried or
 * worn. What counts as forbidden follows the OSRS rules closely enough for the quest: anything
 * that equips to the head, body, legs, weapon or shield slot and carries an attack, defence or
 * strength bonus, with the wizard robes the island tolerates as the exception. Capes, jewellery,
 * gloves, boots, ammunition and unequipable items are all allowed.
 */
class EntranaBoat @Inject constructor(private val lostCity: LostCityQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        for (monk in PORT_SARIM_MONKS) {
            onOpNpc1(monk) { startDialogue(it.npc) { portSarimMonk() } }
            onOpNpc3(monk) { startDialogue(it.npc) { boardForEntrana() } }
        }
        for (monk in ENTRANA_MONKS) {
            onOpNpc1(monk) { startDialogue(it.npc) { entranaMonk() } }
            onOpNpc3(monk) { sail(SARIM_DECK, ARRIVE_SARIM) }
        }
        onOpLoc1(SARIM_PLANK_ON) { cross(SARIM_DECK) }
        onOpLoc1(SARIM_PLANK_OFF) { cross(SARIM_JETTY) }
        onOpLoc1(ENTRANA_PLANK_ON) { cross(ENTRANA_DECK) }
        onOpLoc1(ENTRANA_PLANK_OFF) { cross(ENTRANA_JETTY) }
    }

    /* Port Sarim */

    private suspend fun Dialogue.portSarimMonk() {
        chatNpc(neutral, "Do you seek passage to holy Entrana? If so, you must leave your weaponry and armour behind. This is Saradomin's will.")
        when (
            choice2(
                "No, not right now.", 1,
                "Yes, okay, I'm ready to go.", 2,
            )
        ) {
            1 -> {
                chatPlayer(neutral, "No, not right now.")
                chatNpc(neutral, "Very well.")
            }
            2 -> {
                chatPlayer(happy, "Yes, okay, I'm ready to go.")
                boardForEntrana()
            }
        }
    }

    /** The search, the verdict, and the crossing if the player passes. */
    private suspend fun Dialogue.boardForEntrana() {
        if (!lostCity.searchedByMonks.get(player)) {
            chatNpc(neutral, "Very well. One moment please.")
            mesbox("The monk quickly searches you.")
            lostCity.searchedByMonks.set(player, true)
        }
        if (player.carriesForbiddenItem()) {
            chatNpc(angry, "NO WEAPONS OR ARMOUR are permitted on holy Entrana AT ALL. We will not allow you to travel there in breach of mighty Saradomin's edict.")
            chatNpc(angry, "Do not try and deceive us again. Come back when you have laid down your Zamorakian instruments of death.")
            return
        }
        chatNpc(happy, "All is satisfactory. You may board the boat now.")
        access.sail(ENTRANA_DECK, ARRIVE_ENTRANA)
    }

    /* Entrana */

    private suspend fun Dialogue.entranaMonk() {
        chatNpc(quiz, "Do you wish to leave holy Entrana?")
        when (
            choice2(
                "Yes, I'm ready to go.", 1,
                "Not just yet.", 2,
            )
        ) {
            1 -> {
                chatPlayer(happy, "Yes, I'm ready to go.")
                chatNpc(happy, "Okay, let's board...")
                access.sail(SARIM_DECK, ARRIVE_SARIM)
            }
            2 -> chatPlayer(neutral, "Not just yet.")
        }
    }

    /* Travel */

    private suspend fun ProtectedAccess.sail(deck: CoordGrid, arrival: String) {
        fadeOverlay(
            startColour = 0,
            startTransparency = 255,
            endColour = 0,
            endTransparency = 0,
            clientDuration = FADE_CLIENT_DURATION,
        )
        clearHealthHud()
        delay(FADE_TICKS)
        telejump(deck)
        delay(1)
        fadeOverlay(
            startColour = 0,
            startTransparency = 0,
            endColour = 0,
            endTransparency = 255,
            clientDuration = FADE_CLIENT_DURATION,
        )
        clearHealthHud()
        delay(FADE_TICKS)
        closeFadeOverlay()
        mesbox(arrival)
    }

    private suspend fun ProtectedAccess.cross(dest: CoordGrid) {
        arriveDelay()
        delay(1)
        telejump(dest)
    }

    /* Saradomin's edict */

    private fun Player.carriesForbiddenItem(): Boolean {
        val carried = inv.filterNotNull { true } + worn.filterNotNull { true }
        return carried.any { getInvObj(it).isForbiddenOnEntrana() }
    }

    private fun ItemServerType.isForbiddenOnEntrana(): Boolean {
        val slot = Wearpos[wearpos1] ?: return false
        if (slot !in FORBIDDEN_SLOTS || internalName in TOLERATED) {
            return false
        }
        return BONUS_PARAMS.any { (paramOrNull(it) ?: 0) != 0 }
    }

    private companion object {
        val PORT_SARIM_MONKS = listOf("npc.shipmonk", "npc.shipmonk1_b", "npc.shipmonk1_c")
        val ENTRANA_MONKS = listOf("npc.shipmonk2", "npc.shipmonk2_b", "npc.shipmonk2_c")

        const val SARIM_PLANK_ON = "loc.ship_to_entrana_on"
        const val SARIM_PLANK_OFF = "loc.ship_to_entrana_off"
        const val ENTRANA_PLANK_ON = "loc.ship_from_entrana_on"
        const val ENTRANA_PLANK_OFF = "loc.ship_from_entrana_off"

        /** The ship decks sit a level above the jetties, which are bridged down to ground level. */
        val SARIM_DECK = CoordGrid(3048, 3231, 1)
        val SARIM_JETTY = CoordGrid(3048, 3234, 0)
        val ENTRANA_DECK = CoordGrid(2834, 3331, 1)
        val ENTRANA_JETTY = CoordGrid(2834, 3335, 0)

        const val ARRIVE_ENTRANA = "The ship arrives at Entrana."
        const val ARRIVE_SARIM = "The ship arrives at Port Sarim."

        const val FADE_CLIENT_DURATION = 50
        const val FADE_TICKS = 3

        val FORBIDDEN_SLOTS =
            setOf(Wearpos.Hat, Wearpos.Torso, Wearpos.Legs, Wearpos.RightHand, Wearpos.LeftHand)

        /** Robes the monks let through despite their small magic bonuses. */
        val TOLERATED =
            setOf(
                "obj.wizards_robe",
                "obj.wizards_robe_trim",
                "obj.wizards_robe_trim_gold",
                "obj.bluewizhat",
                "obj.black_robe",
                "obj.black_wizards_robe_trim",
                "obj.black_wizards_robe_gold",
                "obj.blackwizhat",
                "obj.blue_skirt",
                "obj.black_skirt",
            )

        val BONUS_PARAMS =
            listOf(
                params.attack_stab,
                params.attack_slash,
                params.attack_crush,
                params.attack_magic,
                params.attack_ranged,
                params.defence_stab,
                params.defence_slash,
                params.defence_crush,
                params.defence_magic,
                params.defence_ranged,
                params.melee_strength,
                params.ranged_strength,
            )
    }
}
