package org.rsmod.content.quest.area.varrock.demonslayer

import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.game.entity.Player

/*
 * Client-facing mirrors of the quest attributes. All of these share `varp.demonstart` with the
 * quest stage; see [DemonSlayerQuest.syncVars].
 */

var Player.dsIncantation1 by intVarBit("varbit.delrith_incantation_1")
var Player.dsIncantation2 by intVarBit("varbit.delrith_incantation_2")
var Player.dsIncantation3 by intVarBit("varbit.delrith_incantation_3")
var Player.dsIncantation4 by intVarBit("varbit.delrith_incantation_4")
var Player.dsIncantation5 by intVarBit("varbit.delrith_incantation_5")

/** Drives the drain and sewer-mud multilocs: 0 key in drain, 1 key in the sewer, 2 taken. */
var Player.dsDrainKey by intVarBit("varbit.delrith_drain_key")

/** Drives the sword case multiloc in Sir Prysin's room: full while the sword is still inside. */
var Player.dsSilverlightCase by boolVarBit("varbit.delrith_silverlight_case")

/** Drives the stone table multiloc at the circle: intact until the summoning has been seen. */
var Player.dsSeenSummoning by boolVarBit("varbit.delrith_seen_summoning_cutscene")
