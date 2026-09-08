package org.rsmod.content.generic.locs.passages

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import dev.openrune.definition.constants.ConstantProvider
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ObjectServerType
import jakarta.inject.Inject
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.params
import org.rsmod.api.player.events.interact.LocDefaultEvents
import org.rsmod.api.player.events.interact.OpDefaultEvent
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.interact.LocInteractions
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onProtectedEvent
import org.rsmod.content.generic.locs.doors.DoorTranslations
import org.rsmod.content.generic.locs.gate.GateTranslations
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.game.map.collision.get
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.game.map.collision.isZoneValid
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

/**
 * Catch-all for the doors, gates, ladders, staircases, trapdoors, cave mouths and stiles that no
 * other script claims. It runs only when a loc op has no type, content or category handler, so
 * the data-driven door, gate and ladder scripts always take precedence.
 *
 * Doors and gates find their open or closed twin by cache name: `metalgateclosedl` and
 * `metalgateopenl` are the same panel, as are `poordoor` and `poordooropen`, whatever the twin's
 * display name. A closed panel with a matching panel beside it is one half of a double door or a
 * two-panel gate, and both halves swing together using the same geometry as `DoubleDoorScript`
 * (each panel on its own post; the fold-to-one-side fence gates stay with `PicketGate`). A panel with no open form in the cache swings its own model instead of
 * vanishing. Every panel this script opens is remembered until it resets, so closing it puts the
 * original panels back exactly where they were.
 *
 * Ladders and stairs move the player one level, or between the surface and the dungeon copy of
 * the map 6400 tiles north, and land them on the nearest free tile. Cave mouths do the same
 * surface/dungeon swap.
 */
class GenericPassageScript
@Inject
constructor(
    private val locRepo: LocRepository,
    private val collision: CollisionFlagMap,
    private val locInteractions: LocInteractions,
) : PluginScript() {
    /** Open panels spawned by this script, keyed by where they stand, and how to undo them. */
    private val openedPanels = HashMap<PanelKey, OpenedPassage>()

    /** Cache name (without the `loc.` prefix) of every named loc, by id. */
    private val locNames: Map<Int, String> by lazy {
        // `ConstantProvider.mappings` is the loaded gameval table; `getMappingProvider()` is an
        // unrelated default provider that stays empty.
        val table = ConstantProvider.mappings[RSCMType.LOC.prefix].orEmpty()
        HashMap<Int, String>(table.size).also { names ->
            for ((name, id) in table) {
                names[id] = name.removePrefix("loc.").lowercase()
            }
        }
    }

    /** Every named loc in the cache grouped by [twinKey]. */
    private val locsByTwinKey: Map<String, List<ObjectServerType>> by lazy {
        ServerCacheManager.getObjects().values.filter { it.id in locNames }.groupBy { twinKey(it) }
    }

    override fun ScriptContext.startup() {
        onProtectedEvent<LocDefaultEvents.Op1>(OpDefaultEvent.ID) { interact(it.loc, it.type, 0) }
        onProtectedEvent<LocDefaultEvents.Op2>(OpDefaultEvent.ID) { interact(it.loc, it.type, 1) }
        onProtectedEvent<LocDefaultEvents.Op3>(OpDefaultEvent.ID) { interact(it.loc, it.type, 2) }
    }

    private suspend fun ProtectedAccess.interact(
        loc: BoundLocInfo,
        type: ObjectServerType,
        opIndex: Int,
    ) {
        val op = type.actions.getOpOrNull(opIndex)
        val action = op?.let { Passages.classify(type.name, it, loc.shape) }
        if (action == null) {
            mes(constants.dm_default, ChatType.Engine)
            logger.debug { "No generic passage handling for loc '${type.name}' op=$op type=$type" }
            return
        }
        arriveDelay()
        when (action) {
            PassageAction.OpenDoor -> openDoor(loc, type)
            PassageAction.CloseDoor -> closeDoor(loc, type)
            PassageAction.OpenTrapdoor -> openTrapdoor(loc, type)
            PassageAction.ClimbUp -> climb(type, up = true)
            PassageAction.ClimbDown -> climb(type, up = false)
            PassageAction.ClimbEither -> climbEither(type)
            PassageAction.Enter -> enter()
            PassageAction.ClimbOver -> climbOver(loc)
        }
    }

    /* Doors and gates */

    /**
     * A panel as it stands in the world. [base] is the loc actually in the map (a multi-loc's
     * parent for gates such as Al Kharid's) and is what gets deleted or put back; [vis] is the
     * variant the player sees and is what the open or closed twin is worked out from.
     */
    private data class Panel(
        val coords: CoordGrid,
        val shape: LocShape,
        val angle: LocAngle,
        val base: ObjectServerType,
        val vis: ObjectServerType,
    )

    private data class PanelKey(val coords: CoordGrid, val locId: Int)

    private data class OpenedPassage(val opened: List<Panel>, val closed: List<Panel>)

    private fun ProtectedAccess.openDoor(loc: BoundLocInfo, type: ObjectServerType) {
        // A panel swinging its own model keeps its `Open` op; a second click closes it.
        if (openedPanels.containsKey(PanelKey(loc.coords, type.id))) {
            closeDoor(loc, type)
            return
        }
        val sound = type.paramOrNull(params.opensound)
        if (sound != null) soundSynth(sound) else soundSynth(DEFAULT_OPEN_SOUND)

        val base = ServerCacheManager.getObject(loc.id) ?: type
        val clicked = Panel(loc.coords, loc.shape, loc.angle, base, type)
        val plan = planOpen(clicked)
        val swingsOntoPlayer =
            clicked.shape == LocShape.WallDiagonal && plan?.opened?.any { it.coords == coords } == true
        if (plan == null || swingsOntoPlayer) {
            // A shape we cannot swing, or a diagonal door that would land on the player: just let
            // them pass.
            locRepo.del(loc, DOOR_DURATION)
            return
        }
        logger.debug {
            val closed = plan.closed.joinToString { "${it.vis.internalNameOrEmpty()}@${it.coords}/${it.angle}" }
            val opened = plan.opened.joinToString { "${it.vis.internalNameOrEmpty()}@${it.coords}/${it.angle}" }
            "Opening passage picket=${clicked.isPicketGate()} closed=[$closed] opened=[$opened]"
        }
        for (panel in plan.closed) {
            val info = findPanel(panel)
            if (info == null) {
                logger.warn { "Closed panel not found in map: ${panel.vis.internalNameOrEmpty()} at ${panel.coords}" }
                continue
            }
            locRepo.del(info, DOOR_DURATION)
        }
        for (panel in plan.opened) {
            val key = PanelKey(panel.coords, panel.base.id)
            locRepo.add(panel.coords, panel.base, DOOR_DURATION, panel.angle, panel.shape) {
                openedPanels.remove(key)
            }
            openedPanels[key] = plan
        }
    }

    /**
     * Works out where the clicked panel, and its partner if it has one, go when opened. Returns
     * `null` only for shapes that cannot be swung.
     */
    private fun ProtectedAccess.planOpen(clicked: Panel): OpenedPassage? {
        if (!clicked.shape.isTranslatable()) {
            return null
        }
        // The partner sits at the first offset when the clicked panel is the left one.
        val partnerRight =
            findClosedPartner(
                clicked,
                DoorTranslations.translateClose(clicked.coords, clicked.shape, clicked.angle),
            )
        val partnerLeft =
            if (partnerRight != null) {
                null
            } else {
                findClosedPartner(
                    clicked,
                    DoorTranslations.translateCloseOpposite(
                        clicked.coords,
                        clicked.shape,
                        clicked.angle,
                    ),
                )
            }
        val left = partnerLeft ?: clicked
        val right = if (partnerLeft != null) clicked else partnerRight

        val picket = clicked.isPicketGate()
        val leftOpened = left.opened(isLeft = true, alone = right == null, picket = picket)
        val rightOpened = right?.opened(isLeft = false, alone = false, picket = picket)
        return OpenedPassage(
            opened = listOfNotNull(leftOpened, rightOpened),
            closed = listOfNotNull(left, right),
        )
    }

    /**
     * Where this closed panel stands once open. Doors, metal gates and castle gates follow
     * `DoorScript` for a lone panel and `DoubleDoorScript` for a pair: every panel moves one tile
     * out of the doorway and turns on the post it hangs from, the left panel anticlockwise and
     * the right panel clockwise. Wooden fence gates ([picket]) follow `PicketGate` instead, both
     * panels folding back to the same side. With no open form in the cache the closed model
     * itself is turned on its hinge.
     */
    private fun Panel.opened(isLeft: Boolean, alone: Boolean, picket: Boolean): Panel {
        val openType = findTwin(vis, "Close") ?: vis
        val (openCoords, openAngle) =
            when {
                picket && isLeft -> coords + GateTranslations.leftGateOpen(shape, angle) to angle.turn(3)
                picket -> coords + GateTranslations.rightGateOpen(shape, angle) to angle.turn(3)
                isLeft && !alone -> DoorTranslations.translateOpen(coords, shape, angle) to angle.turn(3)
                else -> DoorTranslations.translateOpen(coords, shape, angle) to angle.turn(1)
            }
        return Panel(openCoords, shape, openAngle, base = openType, vis = openType)
    }

    /**
     * Wooden fence gates fold both panels to one side, unlike metal and castle gates which swing
     * like double doors. The cache names them by their fence, and the arena gate is one too.
     */
    private fun Panel.isPicketGate(): Boolean {
        if (shape != LocShape.WallStraight) {
            return false
        }
        val name = vis.internalNameOrEmpty()
        return PICKET_GATE_NAMES.any { name.contains(it) }
    }

    /**
     * A closed panel of the same kind as [like] standing at [coords], facing the same way. The
     * candidate's visible variant is resolved through the player's vars so a multi-loc gate
     * matches its neighbour by what the player sees, not by its unnamed parent loc.
     */
    private fun ProtectedAccess.findClosedPartner(like: Panel, coords: CoordGrid): Panel? {
        for (candidate in locRepo.findAll(coords)) {
            if (candidate.shape != like.shape || candidate.angle != like.angle) {
                continue
            }
            val base = ServerCacheManager.getObject(candidate.entity.id) ?: continue
            val vis = visibleType(candidate, base)
            if (vis.name != like.vis.name || vis.actions.getOpOrNull(0) != "Open") {
                continue
            }
            return Panel(candidate.coords, candidate.shape, candidate.angle, base, vis)
        }
        return null
    }

    private fun ProtectedAccess.visibleType(
        info: LocInfo,
        base: ObjectServerType,
    ): ObjectServerType {
        val bound = BoundLocInfo(info, base)
        val multi = locInteractions.multiLoc(bound, base, player.vars) ?: return base
        return ServerCacheManager.getObject(multi.id) ?: base
    }

    private fun findPanel(panel: Panel): LocInfo? = locRepo.findExact(panel.coords, panel.base)

    private fun ProtectedAccess.closeDoor(loc: BoundLocInfo, type: ObjectServerType) {
        val tracked = openedPanels[PanelKey(loc.coords, type.id)]
        val restore = tracked?.closed ?: singleClosed(loc, type)?.let(::listOf) ?: return
        val closesOntoPlayer =
            loc.shape == LocShape.WallDiagonal && restore.any { it.coords == coords }
        if (closesOntoPlayer) {
            mes("You cannot close the door while you are standing in the doorway.")
            return
        }
        val sound = type.paramOrNull(params.closesound)
        if (sound != null) soundSynth(sound) else soundSynth(DEFAULT_CLOSE_SOUND)

        val remove =
            tracked?.opened
                ?: listOf(Panel(loc.coords, loc.shape, loc.angle, base = type, vis = type))
        for (panel in remove) {
            val info = findPanel(panel) ?: continue
            locRepo.del(info, DOOR_DURATION)
            openedPanels.remove(PanelKey(panel.coords, panel.base.id))
        }
        for (panel in restore) {
            locRepo.add(panel.coords, panel.base, DOOR_DURATION, panel.angle, panel.shape)
        }
    }

    /**
     * Where an open panel this script did not open (one the map itself left open) goes when
     * closed, treating it as a lone door or gate.
     */
    private fun singleClosed(loc: BoundLocInfo, type: ObjectServerType): Panel? {
        val closedType = findTwin(type, "Open") ?: return null
        if (!loc.shape.isTranslatable()) {
            return null
        }
        val panel = Panel(loc.coords, loc.shape, loc.angle, base = type, vis = type)
        return if (panel.isPicketGate()) {
            Panel(
                loc.coords + GateTranslations.leftGateClose(loc.shape, loc.angle),
                loc.shape,
                loc.angle.turn(-3),
                base = closedType,
                vis = closedType,
            )
        } else {
            Panel(
                DoorTranslations.translateClose(loc.coords, loc.shape, loc.angle),
                loc.shape,
                loc.angle.turn(-1),
                base = closedType,
                vis = closedType,
            )
        }
    }

    private fun LocShape.isTranslatable(): Boolean =
        this == LocShape.WallStraight || this == LocShape.WallDiagonal

    /**
     * The open or closed counterpart of [type]: a loc with the same footprint whose first op is
     * [firstOp] and whose cache name is the same once "open" and "closed" are taken out of it
     * (`poordoor`/`poordooropen`, `metalgateclosedl`/`metalgateopenl`, `door_l`/`door_l_open`).
     * Failing that, the nearest loc by id with the same display name and footprint.
     */
    private fun findTwin(type: ObjectServerType, firstOp: String): ObjectServerType? {
        val sameName = locsByTwinKey[twinKey(type)]?.filter { it.id != type.id && it.sameFootprint(type) }
        val byName =
            sameName?.firstOrNull { it.actions.getOpOrNull(0) == firstOp }
                ?: sameName?.firstOrNull { candidate ->
                    val wantOpen = firstOp == "Close"
                    val looksOpen = candidate.internalNameOrEmpty().contains("open")
                    candidate.actions.getOpOrNull(0) == null && looksOpen == wantOpen
                }
        if (byName != null) {
            return byName
        }
        for (offset in TWIN_SEARCH_OFFSETS) {
            val candidate = ServerCacheManager.getObject(type.id + offset) ?: continue
            if (candidate.name != type.name || !candidate.sameFootprint(type)) {
                continue
            }
            if (candidate.actions.getOpOrNull(0) == firstOp) {
                return candidate
            }
        }
        return null
    }

    private fun ObjectServerType.sameFootprint(other: ObjectServerType): Boolean =
        width == other.width && length == other.length

    private fun ObjectServerType.internalNameOrEmpty(): String = locNames[id] ?: ""

    /** The cache name with every "open"/"closed" marker and underscore removed. */
    private fun twinKey(type: ObjectServerType): String {
        val name = type.internalNameOrEmpty()
        if (name.isEmpty()) {
            return "#${type.id}"
        }
        return name.replace("closed", "").replace("open", "").replace("close", "").replace("_", "")
    }

    /* Trapdoors */

    private suspend fun ProtectedAccess.openTrapdoor(loc: BoundLocInfo, type: ObjectServerType) {
        val opened = findTwin(type, "Climb-down") ?: findTwin(type, "Close")
        if (opened != null) {
            soundSynth(DEFAULT_OPEN_SOUND)
            locRepo.change(loc, opened, DOOR_DURATION)
            return
        }
        // No open form in the cache: go straight down.
        climb(type, up = false)
    }

    /* Ladders, stairs and ropes */

    private suspend fun ProtectedAccess.climbEither(type: ObjectServerType) {
        val up = choice2("Climb up.", true, "Climb down.", false, title = "Climb up or down?")
        climb(type, up)
    }

    private suspend fun ProtectedAccess.climb(type: ObjectServerType, up: Boolean) {
        val dest = Passages.climbDestination(coords, up)?.let(::landing)
        if (dest == null) {
            mes(if (up) "You cannot see a way up from here." else "You cannot see a way down from here.")
            return
        }
        val climbAnim = type.paramOrNull(params.climb_anim)
        if (climbAnim != null) {
            anim(RSCM.getReverseMapping(RSCMType.SEQ, climbAnim.id))
        } else if (type.name in LADDER_NAMES) {
            anim(LADDER_ANIM)
        }
        delay(1)
        telejump(dest, TeleportType.Exempt)
    }

    /* Caves, tunnels and other mouths of the underground */

    private suspend fun ProtectedAccess.enter() {
        val dest = landing(Passages.enterDestination(coords))
        if (dest == null) {
            mes("You cannot see a way through.")
            return
        }
        delay(1)
        telejump(dest, TeleportType.Exempt)
    }

    /* Stiles and fences */

    private suspend fun ProtectedAccess.climbOver(loc: BoundLocInfo) {
        val dest = Passages.farSide(loc, coords)
        if (dest == null || !walkable(dest)) {
            mes("You cannot climb over from here.")
            return
        }
        hopTo(dest, CLIMB_OVER_ANIM, ticks = 2)
    }

    /**
     * The nearest free tile to [dest], or `null` if everything around it is blocked or the map
     * there is featureless filler rather than somewhere a player can be.
     */
    private fun landing(dest: CoordGrid): CoordGrid? {
        if (!hasSurroundings(dest)) {
            return null
        }
        return Passages.landingCandidates(dest).firstOrNull(::walkable)
    }

    private fun walkable(coords: CoordGrid): Boolean =
        collision.isZoneValid(coords) && !collision.isWalkBlocked(coords)

    /**
     * Whether anything at all (a wall, a loc, a blocked tile) sits within [SURROUNDINGS_RADIUS]
     * tiles of [centre]. Real rooms and caves always have something close by; the black filler
     * that pads out a map square has nothing, and a guessed destination that lands in it would
     * strand the player.
     */
    private fun hasSurroundings(centre: CoordGrid): Boolean {
        for (dz in -SURROUNDINGS_RADIUS..SURROUNDINGS_RADIUS) {
            for (dx in -SURROUNDINGS_RADIUS..SURROUNDINGS_RADIUS) {
                val tile = centre.translate(dx, dz)
                if (!collision.isZoneValid(tile)) {
                    continue
                }
                if (collision[tile] and SURROUNDINGS_MASK != 0) {
                    return true
                }
            }
        }
        return false
    }

    private companion object {
        private val logger = InlineLogger()

        /** How long (in cycles) an opened door or trapdoor stays changed before it resets. */
        private const val DOOR_DURATION = 500

        private val TWIN_SEARCH_OFFSETS =
            listOf(1, -1, 2, -2, 3, -3, 4, -4, 5, -5, 6, -6, 8, -8, 10, -10, 12, -12)

        private val LADDER_NAMES = setOf("Ladder", "Rope", "Rope ladder")

        /** Cache-name fragments of the wooden fence gates that fold to one side. */
        private val PICKET_GATE_NAMES = listOf("fence", "wooden", "pvpa_access_gate")

        private const val SURROUNDINGS_RADIUS = 8

        /** Every collision flag except the roof marker. */
        private const val SURROUNDINGS_MASK = CollisionFlag.ROOF.inv()

        private const val DEFAULT_OPEN_SOUND = "synth.door_open"
        private const val DEFAULT_CLOSE_SOUND = "synth.door_close"
        private const val LADDER_ANIM = "seq.human_reachforladder"
        private const val CLIMB_OVER_ANIM = "seq.human_walk_style"
    }
}
