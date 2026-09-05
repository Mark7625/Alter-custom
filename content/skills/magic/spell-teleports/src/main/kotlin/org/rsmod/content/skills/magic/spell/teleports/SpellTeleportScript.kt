package org.rsmod.content.skills.magic.spell.teleports

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import dev.openrune.types.aconverted.interf.IfButtonOp
import jakarta.inject.Inject
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.combat.commons.magic.MagicSpell
import org.rsmod.api.combat.manager.MagicRuneManager
import org.rsmod.api.combat.manager.MagicRuneManager.Companion.isFailure
import org.rsmod.api.config.refs.params
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.output.clearMapFlag
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.content.quest.manager.Quest
import org.rsmod.game.inv.isType
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class SpellTeleportScript
@Inject
constructor(
    private val spells: MagicSpellRegistry,
    private val runes: MagicRuneManager,
    private val teleportValidator: PlayerTeleportValidator,
    private val areaChecker: AreaChecker,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for (teleport in StandardSpellTeleport.entries) {
            val spell = teleport.resolveSpell() ?: continue
            onIfOverlayButton(spell.component) { castSpellTeleport(spell, teleport, it.op) }
        }
        onPlayerQueueWithArgs<PendingSpellTeleport>(TeleportQueue) {
            processQueuedTeleport(it.args)
        }
    }

    private fun StandardSpellTeleport.resolveSpell(): MagicSpell? {
        val spellObj = ServerCacheManager.getItem(spellObj.asRSCM(RSCMType.OBJ)) ?: return null
        return spells.getObjSpell(spellObj)
    }

    private suspend fun ProtectedAccess.castSpellTeleport(
        spell: MagicSpell,
        teleport: StandardSpellTeleport,
        op: IfButtonOp,
    ) {
        if (actionDelay > mapClock) {
            return
        }

        if (!teleport.canCast(this)) {
            return
        }

        val option = teleport.option(op)
        val destination = teleport.destination(spell, option)
        if (destination == null) {
            mes(option.missingDestinationMessage)
            return
        }

        if (!canTeleport()) {
            return
        }

        if (!consumeRequirements(spell, teleport)) {
            return
        }

        actionDelay = mapClock + TeleportActionDelay
        anim(TeleportStartAnim)
        spotanim(TeleportSpotanim, height = TeleportSpotanimHeight)
        soundSynth(TeleportSound)
        clearQueue(TeleportQueue)
        queue(TeleportQueue, TeleportDelay, PendingSpellTeleport(teleport, destination.packed))
    }

    private fun ProtectedAccess.processQueuedTeleport(task: PendingSpellTeleport) {
        val spell = task.teleport.resolveSpell() ?: return
        if (!canTeleport()) {
            return
        }
        telejump(CoordGrid(task.destination))
        anim(TeleportEndAnim)
        statAdvance("stat.magic", spell.castXp)
    }

    private fun ProtectedAccess.canTeleport(): Boolean {
        val denial =
            teleportValidator.validate(player, TeleportType.Standard, areaChecker)
        if (denial == null) {
            return true
        }
        player.clearMapFlag()
        player.mes(denial, ChatType.Engine)
        return false
    }

    private fun ProtectedAccess.consumeRequirements(
        spell: MagicSpell,
        teleport: StandardSpellTeleport,
    ): Boolean {
        val castSpell =
            if (teleport == StandardSpellTeleport.ApeAtoll) {
                val banana = ServerCacheManager.getItem(Banana.asRSCM(RSCMType.OBJ)) ?: return false
                val spellWithoutBanana = spell.copy(objReqs = spell.objReqs.withoutBananaReq())
                if (!runes.canCastSpell(player, spellWithoutBanana)) {
                    return false
                }
                if (!deleteBanana(banana)) {
                    mes("You need a banana to cast this spell.")
                    return false
                }
                spellWithoutBanana
            } else {
                spell
            }

        if (castSpell.objReqs.isEmpty()) {
            return runes.canCastSpell(player, castSpell)
        }
        return !runes.attemptCast(player, castSpell).isFailure()
    }

    private fun List<MagicSpell.ObjRequirement>.withoutBananaReq(): List<MagicSpell.ObjRequirement> {
        return filterNot { RSCM.getReverseMapping(RSCMType.OBJ, it.obj.id).contains("banana") }
    }

    private fun ProtectedAccess.deleteBanana(banana: ItemServerType): Boolean {
        val bananaSlot = inv.indexOfFirst { it.isType(banana) }
        if (bananaSlot == -1) {
            return false
        }
        val transaction =
            player.invTransaction(inv, autoCommit = true) {
                val targetInv = select(inv)
                delete {
                    from = targetInv
                    obj = banana.id
                    strictCount = 1
                    strictSlot = bananaSlot
                }
            }
        return transaction.success
    }

    private data class PendingSpellTeleport(
        val teleport: StandardSpellTeleport,
        val destination: Int,
    )

    private enum class StandardSpellTeleport(
        val spellObj: String,
        val destination: CoordGrid? = null,
        val destinationLevel: Int? = null,
        val alternate: TeleportOption? = null,
        val requiredQuest: String? = null,
        val lockedMessage: String = "You need to complete the required quest to cast this spell.",
        val missingDestinationMessage: String = "That teleport is not implemented yet.",
    ) {
        Home(
            "obj.48_home_teleport",
            CoordGrid(3222, 3222, 0),
        ),
        Varrock(
            "obj.25_varrock_teleport",
            alternate = TeleportOption(destination = CoordGrid(3164, 3487, 0)),
        ),
        Lumbridge(
            "obj.31_lumbridge_teleport",
        ),
        Falador(
            "obj.37_falador_teleport",
        ),
        TeleportToHouse(
            "obj.67_house_teleport",
            alternate =
                TeleportOption(
                    missingDestinationMessage =
                        "You need to purchase a house before you can teleport outside it."
                ),
            missingDestinationMessage = "You need to purchase a house before you can use this spell.",
        ),
        Camelot(
            "obj.45_camelot_teleport",
            alternate = TeleportOption(destination = CoordGrid(2725, 3485, 0)),
        ),
        KourendCastle(
            // Cache name is wrong, but its spell params match Kourend Castle Teleport.
            "obj.cert_deadman_level99_lamp",
            requiredQuest = "quest_clientofkourend",
            lockedMessage = "You need to complete Client of Kourend to cast this spell.",
        ),
        Ardougne(
            "obj.51_ardougne_teleport",
            requiredQuest = "quest_plaguecity",
            lockedMessage = "You need to complete Plague City to cast this spell.",
        ),
        CivitasIllaFortis(
            // Cache name is wrong, but its spell params match Civitas illa Fortis Teleport.
            "obj.placeholder_blighted_sack_snare",
            requiredQuest = "quest_twilightspromise",
            lockedMessage = "You need to complete Twilight's Promise to cast this spell.",
        ),
        Watchtower(
            "obj.58_watchtower_teleport",
            alternate = TeleportOption(destination = CoordGrid(2544, 3095, 0)),
            requiredQuest = "quest_watchtowerquest",
            lockedMessage = "You need to complete Watchtower to cast this spell.",
        ),
        Trollheim(
            "obj.61_trollheim_teleport",
            requiredQuest = "quest_eadgarsruse",
            lockedMessage = "You need to complete Eadgar's Ruse to cast this spell.",
        ),
        ApeAtoll(
            "obj.64_ape_atoll_teleport",
            destinationLevel = 1,
        ),
        TeleportBoatToMe(
            "obj.56_teleport_boat_to_me",
            requiredQuest = "quest_pandemonium",
            lockedMessage = "You need to complete Pandemonium to cast this spell.",
            missingDestinationMessage = "Boat teleports need boat-location support before they can be cast.",
        ),
        TeleportMeToBoat(
            "obj.67_teleport_me_to_boat",
            alternate =
                TeleportOption(
                    missingDestinationMessage =
                        "Last boat teleports need boat-location support before they can be cast."
                ),
            requiredQuest = "quest_pandemonium",
            lockedMessage = "You need to complete Pandemonium to cast this spell.",
            missingDestinationMessage = "Boat teleports need boat-location support before they can be cast.",
        );

        fun option(op: IfButtonOp): TeleportOption {
            return if (op == IfButtonOp.Op2 && alternate != null) {
                alternate
            } else {
                TeleportOption(destination, destinationLevel, missingDestinationMessage)
            }
        }

        fun destination(spell: MagicSpell, option: TeleportOption): CoordGrid? {
            val coord = option.destination ?: destination ?: spell.obj.paramOrNull(params.spell_telecoord)
            val level = option.destinationLevel ?: destinationLevel
            return if (coord != null && level != null) {
                coord.copy(level = level)
            } else {
                coord
            }
        }

        fun canCast(access: ProtectedAccess): Boolean {
            if (!ENFORCE_QUEST_REQUIREMENTS) {
                return true
            }
            val questKey = requiredQuest ?: return true
            val quest = Quest.get(questKey)
            if (quest?.isQuestCompleted(access.player) == true) {
                return true
            }
            access.mes(lockedMessage)
            return false
        }
    }

    private data class TeleportOption(
        val destination: CoordGrid? = null,
        val destinationLevel: Int? = null,
        val missingDestinationMessage: String = "That teleport is not implemented yet.",
    )

    private companion object {
        /**
         * Quest requirements on spells are recorded per spell but not enforced: every spell is
         * castable without its quest. Flip this to lock them again.
         */
        private const val ENFORCE_QUEST_REQUIREMENTS = false

        private val TeleportStartAnim = RSCM.getReverseMapping(RSCMType.SEQ, 714)
        private val TeleportEndAnim = RSCM.getReverseMapping(RSCMType.SEQ, 715)
        private val TeleportSpotanim = RSCM.getReverseMapping(RSCMType.SPOTANIM, 111)
        private const val Banana = "obj.banana"
        private const val TeleportQueue = "queue.spell_teleport"
        private const val TeleportSound = "synth.teleport_all"
        private const val TeleportSpotanimHeight = 92
        private const val TeleportDelay = 4
        private const val TeleportActionDelay = 5
    }
}
