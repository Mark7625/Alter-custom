package org.rsmod.content.skills.hunter

import org.rsmod.api.controller.vars.intVarCon
import org.rsmod.api.player.vars.intVarp
import org.rsmod.game.entity.Controller
import org.rsmod.game.entity.Player

/**
 * Everything a laid trap writes to save data - the module's contract with existing player saves.
 * Two rules govern it: [Controller.trapFamily] holds a [TrapFamily] **ordinal** and
 * [Controller.trapCreature] an index into **[HunterCreatures.all]**, so both may only be appended
 * to. The sentinels are negative because an unset varcon reads 0, a legitimate index.
 */

internal const val CREATURE_NONE: Int = -1

internal const val CREATURE_FAILED: Int = -2

/** An unwritten coord varp reads back 0; `CoordGrid.ZERO` is off-map, so no collision. */
private const val EMPTY_TRAP_COORD: Int = 0

var Controller.trapOwner: Int by intVarCon("varcon.hunter_trap_owner")

var Controller.trapFamily: Int by intVarCon("varcon.hunter_trap_family")

var Controller.trapCreature: Int by intVarCon("varcon.hunter_trap_creature")

private var Player.trapCoord1: Int by intVarp("varp.hunter_trap_coord_1")
private var Player.trapCoord2: Int by intVarp("varp.hunter_trap_coord_2")
private var Player.trapCoord3: Int by intVarp("varp.hunter_trap_coord_3")
private var Player.trapCoord4: Int by intVarp("varp.hunter_trap_coord_4")
private var Player.trapCoord5: Int by intVarp("varp.hunter_trap_coord_5")

/**
 * The packed coords of every trap this player believes it has laid. Coords, not a counter: a
 * counter leaks a slot whenever a trap dies while its owner is away, where a coord can be
 * re-checked against the world (docs/hunter.md).
 */
var Player.hunterTrapCoords: List<Int>
    get() =
        listOf(trapCoord1, trapCoord2, trapCoord3, trapCoord4, trapCoord5).filter {
            it != EMPTY_TRAP_COORD
        }
    set(value) {
        require(value.size <= MAX_LAID_TRAPS) {
            "Cannot store more than $MAX_LAID_TRAPS trap coords: $value"
        }
        trapCoord1 = value.getOrElse(0) { EMPTY_TRAP_COORD }
        trapCoord2 = value.getOrElse(1) { EMPTY_TRAP_COORD }
        trapCoord3 = value.getOrElse(2) { EMPTY_TRAP_COORD }
        trapCoord4 = value.getOrElse(3) { EMPTY_TRAP_COORD }
        trapCoord5 = value.getOrElse(4) { EMPTY_TRAP_COORD }
    }
