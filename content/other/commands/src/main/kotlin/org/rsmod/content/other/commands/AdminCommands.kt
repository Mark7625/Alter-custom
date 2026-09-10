package org.rsmod.content.other.commands

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import dev.openrune.types.NpcMode
import jakarta.inject.Inject
import kotlin.math.max
import kotlin.math.min
import org.rsmod.annotations.InternalApi
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.death.prepareAdminDieTest
import org.rsmod.api.death.preparePvpDeath
import org.rsmod.api.instances.BossInstanceRegistry
import org.rsmod.api.instances.InstanceArea
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.invtx.invClear
import org.rsmod.api.mechanics.toxins.impl.PlayerDisease
import org.rsmod.api.mechanics.toxins.impl.PlayerPoison
import org.rsmod.api.mechanics.toxins.impl.PlayerVenom
import org.rsmod.api.player.cheat.adminGodMode
import org.rsmod.api.player.cheat.adminMaxHit
import org.rsmod.api.player.debug.componentClickDebug
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.ironman.PlayerGamemode
import org.rsmod.api.player.ironman.setGamemode
import org.rsmod.api.player.output.MiscOutput
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.queueDeath
import org.rsmod.api.player.stat.PlayerSkillXP
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statAdvance
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.player.ui.PlayerInterfaceUpdates
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.vars.resyncVar
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.utils.format.formatAmount
import org.rsmod.api.utils.system.SafeServiceExit
import org.rsmod.game.GameUpdate
import org.rsmod.game.cheat.Cheat
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.game.map.Direction
import org.rsmod.game.stat.PlayerSkillXPTable
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareGrid
import org.rsmod.map.square.MapSquareKey
import org.rsmod.map.zone.ZoneGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.objtx.TransactionResult
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.loc.LocLayerConstants
import org.simmetrics.metrics.StringMetrics

class AdminCommands
@Inject
constructor(
    private val protectedAccess: ProtectedAccessLauncher,
    private val playerList: PlayerList,
    private val locRepo: LocRepository,
    private val npcRepo: NpcRepository,
    private val update: GameUpdate,
    private val areaChecker: AreaChecker,
    private val regions: RegionRegistry,
    private val deathKillHooks: Set<NpcDeathKillHook>,
    private val instanceRegistry: BossInstanceRegistry,
) : PluginScript() {
    private val logger = InlineLogger()

    private val levenshteinMetric = StringMetrics.levenshtein()

    private var Player.insideWilderness by boolVarBit("varbit.inside_wilderness")

    override fun ScriptContext.startup() {
        onCommand("master", "Max out all stats", ::master)
        onCommand("reset", "Reset all stats", ::reset)
        onCommand("mypos", "Get current coordinates", ::mypos)
        onCommand("tele", "Teleport to coordgrid", ::tele) {
            invalidArgs = "Usage: ::tele mx mz [level](e.g. ::tele 3200 3200 0)"
        }
        onCommand("telezone", "Teleport to zone key", ::teleZone) {
            invalidArgs = "Use as ::telezone zoneX zoneY level (ex: 400 400 0)"
        }
        onCommand("up", "Teleport up levels", ::up) {
            invalidArgs = "Use as ::up [amount] (ex: ::up 2)"
        }
        onCommand("down", "Teleport down levels", ::down) {
            invalidArgs = "Use as ::down [amount] (ex: ::down 2)"
        }
        onCommand("forward", "Teleport north tiles (ex: ::forward or ::forward 5)", ::forward)
        onCommand(
            "backwards",
            "Teleport south tiles (ex: ::back or ::backwards 5)",
            ::backwards,
            aliases = listOf("back"),
        )
        onCommand("left", "Teleport west tiles (ex: ::left or ::left 5)", ::left)
        onCommand("right", "Teleport east tiles (ex: ::right or ::right 5)", ::right)
        onCommand("anim", "Play animation", ::anim)
        onCommand("spot", "Play spotanim", ::spotanim) {
            invalidArgs = "Use as ::spot spotanimDebugNameOrId (ex: fx_emote_party01_active)"
        }
        onCommand("synth", "Play synth sound", ::synth) {
            invalidArgs = "Use as ::synth idOrName (ex: ::synth 3600 or ::synth pillory_wrong)"
        }
        onCommand("object", "Spawn loc", ::locAdd) {
            invalidArgs = "Use as ::object duration locDebugNameOrId (ex: 100 bookcase)"
        }

        onCommand("locadd", "Spawn loc", ::locAdd) {
            invalidArgs = "Use as ::locadd duration locDebugNameOrId (ex: 100 bookcase)"
        }
        onCommand("locdel", "Remove loc", ::locDel) { invalidArgs = "Use as ::locdel duration" }
        onCommand("objectdel", "Remove loc", ::locDel) {
            invalidArgs = "Use as ::objectdel duration"
        }

        onCommand("npc", "Spawn npc", ::npcAdd) {
            invalidArgs = "Use as ::npc duration npcDebugNameOrId (ex: 100 prison_pete)"
        }

        onCommand("npcadd", "Spawn npc", ::npcAdd) {
            invalidArgs = "Use as ::npcadd duration npcDebugNameOrId (ex: 100 prison_pete)"
        }

        onCommand("invadd", "Spawn obj into inv", ::invAdd)
        onCommand("item", "Spawn obj into inv (ex: ::item 995 100 or ::item coins 100)", ::invAdd)

        onCommand("invclear", "Remove all objs from inv", ::invClear)
        onCommand("varp", "Set varp value", ::setVarp) {
            invalidArgs = "Use as ::varp debugNameOrId value (ex: option_run 1)"
        }
        onCommand("varbit", "Set varbit value", ::setVarBit) {
            invalidArgs = "Use as ::varbit debugNameOrId value (ex: emote_hotline_bling 1)"
        }
        onCommand("getvarp", "Get varp value", ::getVarp) {
            invalidArgs = "Use as ::getvarp debugNameOrId (ex: option_run)"
        }
        onCommand("getvarbit", "Get varbit value", ::getVarBit) {
            invalidArgs = "Use as ::getvarbit debugNameOrId (ex: emote_hotline_bling)"
        }
        onCommand("reboot", "Reboots the game world, applying packed changes", ::reboot)
        onCommand("slowreboot", "Reboots the game world, with a timer", ::slowReboot)
        onCommand(
            "poison",
            "Test player poison (wiki initial damage, optional raw severity)",
            ::poisonTest,
        ) {
            invalidArgs =
                "Use as ::poison initialDamage [severity] (e.g. ::poison 8 or ::poison 0 36)"
        }
        onCommand("venom", "Test player venom (escalating damage timer)", ::venomTest)
        onCommand("venomclear", "Clears Venom", ::venomClear)
        onCommand("disease", "Test disease (drain per tick, default 3)", ::diseaseTest) {
            invalidArgs = "Use as ::disease [drainPerTick] (e.g. ::disease 5)"
        }
        onCommand(
            "diseaseclear",
            "Clears disease timer (stats recover via normal regen)",
            ::diseaseClear,
        )
        onCommand("die", "Simulate death: ::die pvm|pvp [true=in wildy]", ::dieTest) {
            invalidArgs =
                "Usage: ::die pvm|pvp [true|false]  (second arg = in Wilderness, default false)"
        }
        onCommand("god", "Toggle god mode (invincibility)", ::god)
        onCommand("componentdebug", "Toggle interface component click debug output", ::componentDebug)
        onCommand("maxhit", "Toggle always max hit", ::maxhit)
        onCommand("openbank", "Open the bank", ::bank)
        onCommand("transmog", "Transmog player to NPC appearance (no args to reset)", ::transmog) {
            invalidArgs = "Use as ::transmog npcNameOrId (ex: goblin or 126) or ::transmog to reset"
        }
        onCommand("gamemode", "Set account gamemode (normal|ironman|uim|hcim)", ::gamemode) {
            invalidArgs = "Use as ::gamemode normal|ironman|uim|hcim"
        }
        onCommand("interface", "Open an interface by id or RSCM name", ::openInterface) {
            invalidArgs =
                "Use as ::interface idOrName (ex: ::interface 219 or ::interface bankmain)"
        }
        onCommand("ifopen", "Open an interface by id or RSCM name", ::openInterface) {
            invalidArgs = "Use as ::ifopen idOrName (ex: ::ifopen 219 or ::ifopen bankmain)"
        }
        onCommand(
            "testloot",
            "Bulk-fire an npc's death drop hooks (no combat/animation) for loot testing",
            ::testLoot,
        ) {
            invalidArgs =
                "Use as ::testloot npcName [count] (ex: ::testloot godwars_bandos_avatar 100)"
        }
        onCommand(
            "instanceexit",
            "Teleport to an instance's exit coord",
            ::instanceExit,
        ) {
            invalidArgs = "Use as ::instanceexit instanceKey (ex: ::instanceexit graardor)"
        }
    }

    private fun gamemode(cheat: Cheat) =
        with(cheat) {
            val mode =
                when (args.getOrNull(0)?.lowercase()) {
                    "normal",
                    "main",
                    "0" -> PlayerGamemode.NORMAL
                    "ironman",
                    "iron",
                    "1" -> PlayerGamemode.IRONMAN
                    "uim",
                    "ultimate",
                    "2" -> PlayerGamemode.ULTIMATE_IRONMAN
                    "hcim",
                    "hardcore",
                    "3" -> PlayerGamemode.HARDCORE_IRONMAN
                    else -> {
                        player.mes("Use as ::gamemode normal|ironman|uim|hcim")
                        return
                    }
                }
            player.setGamemode(mode)
            player.mes("Gamemode set to $mode (varbit.ironman synced; persists on logout).")
        }

    private fun god(cheat: Cheat) =
        with(cheat) {
            player.adminGodMode = !player.adminGodMode
            player.mes("God mode ${if (player.adminGodMode) "enabled" else "disabled"}.")
        }

    private fun maxhit(cheat: Cheat) =
        with(cheat) {
            player.adminMaxHit = !player.adminMaxHit
            player.mes("Max hit ${if (player.adminMaxHit) "enabled" else "disabled"}.")
        }

    private fun componentDebug(cheat: Cheat) =
        with(cheat) {
            player.componentClickDebug = !player.componentClickDebug
            player.mes(
                "Component click debug ${if (player.componentClickDebug) "enabled" else "disabled"}."
            )
        }

    private fun poisonTest(cheat: Cheat) =
        with(cheat) {
            val initialDamage = args.getOrNull(0)?.toIntOrNull() ?: 0
            val severity = args.getOrNull(1)?.toIntOrNull() ?: 0
            val ok =
                PlayerPoison.tryPoison(player, initialDamage = initialDamage, severity = severity)
            player.mes(
                if (ok) {
                    "Poison applied (initialDamage=$initialDamage severityParam=$severity)."
                } else {
                    "Poison not applied (weaker/equal than current, or both inputs zero)."
                }
            )
        }

    private fun venomTest(cheat: Cheat) = with(cheat) { PlayerVenom.tryVenom(player) }

    private fun venomClear(cheat: Cheat) = with(cheat) { PlayerVenom.clear(player) }

    private fun diseaseTest(cheat: Cheat) =
        with(cheat) {
            val drain = args.getOrNull(0)?.toIntOrNull() ?: 3
            val ok = PlayerDisease.tryDisease(player, drain)
            player.mes(
                if (ok) {
                    "Disease applied (drain per tick=$drain)."
                } else {
                    "Disease not applied (no eligible skill)."
                }
            )
        }

    private fun diseaseClear(cheat: Cheat) =
        with(cheat) {
            PlayerDisease.clear(player)
            player.mes("Disease cleared.")
        }

    private fun master(cheat: Cheat) = with(cheat) { player.setStatLevels(level = 99) }

    private fun reset(cheat: Cheat) = with(cheat) { player.setStatLevels(level = 1) }

    private fun mypos(cheat: Cheat) =
        with(cheat) {
            player.mes("${player.coords}:")
            player.mes("  ${player.coords.x} ${player.coords.z} ${player.coords.level}")
            player.mes("  ${ZoneKey.from(player.coords)} - ${ZoneGrid.from(player.coords)}")
            player.mes(
                "  ${MapSquareKey.from(player.coords)} - ${MapSquareGrid.from(player.coords)}"
            )
            player.mes("  BuildArea(${player.buildArea})")
        }

    private fun tele(cheat: Cheat) =
        with(cheat) {
            val args = if (args.size == 1) args[0].split(",") else args
            val x = args[0].toInt()
            val y = args[1].toInt()
            val level = args.getOrNull(2)?.toInt() ?: 0
            val coords = CoordGrid(x, y, level)
            protectedAccess.launch(player) {
                player.mes("Teleported to $coords.")
                telejump(coords, TeleportType.Exempt)
            }
        }

    private fun teleZone(cheat: Cheat) =
        with(cheat) {
            val args = if (args.size == 1) args[0].split(",") else args
            val zoneX = args[0].toInt()
            val zoneZ = args[1].toInt()
            val level = args[2].toInt()
            val coords = ZoneKey(zoneX, zoneZ, level).toCoords()
            protectedAccess.launch(player) {
                player.mes("Teleported to $coords.")
                telejump(coords, TeleportType.Exempt)
            }
        }

    private fun up(cheat: Cheat) {
        teleLevel(cheat, levelDelta = 1)
    }

    private fun down(cheat: Cheat) {
        teleLevel(cheat, levelDelta = -1)
    }

    private fun teleLevel(cheat: Cheat, levelDelta: Int) {
        with(cheat) {
            val amount =
                when {
                    args.isEmpty() -> 1
                    else -> {
                        val parsed = args[0].toIntOrNull()
                        if (parsed == null) {
                            player.mes("Invalid amount: '${args[0]}'")
                            return@with
                        }
                        parsed
                    }
                }
            if (amount <= 0) {
                player.mes("Amount must be positive.")
                return@with
            }
            val current = player.coords
            val destLevel =
                (current.level + levelDelta * amount).coerceIn(0, CoordGrid.LEVEL_BIT_MASK)
            if (destLevel == current.level) {
                val direction = if (levelDelta > 0) "up" else "down"
                player.mes("Cannot go $direction from level ${current.level}.")
                return@with
            }
            val dest = current.copy(level = destLevel)
            protectedAccess.launch(player) {
                player.mes("Teleported to $dest.")
                telejump(dest, TeleportType.Exempt)
            }
        }
    }

    private fun forward(cheat: Cheat) {
        teleStep(cheat, Direction.North)
    }

    private fun backwards(cheat: Cheat) {
        teleStep(cheat, Direction.South)
    }

    private fun left(cheat: Cheat) {
        teleStep(cheat, Direction.West)
    }

    private fun right(cheat: Cheat) {
        teleStep(cheat, Direction.East)
    }

    private fun teleStep(cheat: Cheat, direction: Direction) {
        with(cheat) {
            val amount =
                when {
                    args.isEmpty() -> 1
                    else -> {
                        val parsed = args[0].toIntOrNull()
                        if (parsed == null) {
                            player.mes("Invalid amount: '${args[0]}'")
                            return@with
                        }
                        parsed
                    }
                }
            if (amount <= 0) {
                player.mes("Amount must be positive.")
                return@with
            }
            val current = player.coords
            val destX = current.x + direction.xOff * amount
            val destZ = current.z + direction.zOff * amount
            if (destX !in 0..CoordGrid.X_BIT_MASK || destZ !in 0..CoordGrid.Z_BIT_MASK) {
                player.mes("Cannot move $amount tile(s) $direction from $current.")
                return@with
            }
            val dest = CoordGrid(destX, destZ, current.level)
            protectedAccess.launch(player) {
                player.mes("Teleported to $dest.")
                telejump(dest, TeleportType.Exempt)
            }
        }
    }

    private fun anim(cheat: Cheat) =
        with(cheat) {
            val typeId = RSCM.getRSCM("seq.${args.asTypeName()}")
            if (typeId == -1) {
                player.mes("There is no seq mapped to: '${args.asTypeName()}'")
                return
            }
            val type = ServerCacheManager.getAnim(typeId)
            if (type == null) {
                player.mes("That seq does not exist: $typeId")
                return
            }
            player.anim("seq.${args.asTypeName()}")
            player.mes("Anim: '${args.asTypeName()}' (priority=${type.priority})")
            logger.debug { "Anim: $type" }
        }

    private fun spotanim(cheat: Cheat) =
        with(cheat) {
            val (typeName, heightArg) = args.asTypeNameAndNumber(defaultNumber = 0)
            val typeId = "spotanim.${typeName}".asRSCM()
            if (typeId == -1) {
                player.mes("There is no spotanim mapped to: '${typeName}'")
                return
            }

            val height = min(heightArg.toInt(), Short.MAX_VALUE.toInt())
            player.spotanim("spotanim.${typeName}", delay = 0, height = height, slot = 0)
            player.mes("Spotanim: '${typeName}' (height=$height)")
            logger.debug { "Spotanim: $typeName" }
        }

    private fun synth(cheat: Cheat) =
        with(cheat) {
            val arg = args.getOrNull(0)
            if (arg == null) {
                player.mes("Use as ::synth idOrName (ex: ::synth 3600 or ::synth pillory_wrong)")
                return
            }
            val id = arg.toIntOrNull()
            if (id != null) {
                player.soundSynth(id)
                player.mes("Synth: $id")
                return
            }
            val typeName = arg.removePrefix("synth.")
            val typeId = "synth.$typeName".asRSCM()
            if (typeId == -1) {
                player.mes("There is no synth mapped to: '$typeName'")
                return
            }
            player.soundSynth("synth.$typeName")
            player.mes("Synth: '$typeName' ($typeId)")
        }

    private fun locAdd(cheat: Cheat) =
        with(cheat) {
            val typeId = "loc.${args[1]}".asRSCM()

            val type = ServerCacheManager.getObject(typeId)!!
            if (type == null) {
                player.mes("That loc does not exist: $typeId")
                return
            }
            val duration = args[0].toInt()
            val angle = args.getOrNull(2)?.toInt() ?: LocAngle.West.id
            val shape = args.getOrNull(3)?.toInt() ?: LocShape.CentrepieceStraight.id
            val layer = LocLayerConstants.of(shape)
            val loc = LocInfo(layer, player.coords, LocEntity(type.id, shape, angle))
            locRepo.add(loc, duration)
            player.mes("Spawned loc '${type.internalName}' (duration: $duration cycles)")
            logger.debug { "Spawned loc: loc=$loc, type=$type" }
        }

    private fun locDel(cheat: Cheat) =
        with(cheat) {
            val zone = ZoneKey.from(player.coords)
            val locs = locRepo.findAll(zone).filter { it.coords == player.coords }.toList()
            if (locs.isEmpty()) {
                player.mes("No loc found on ${player.coords}")
                return
            }
            val duration = args[0].toInt()
            val shape = args.getOrNull(1)?.toIntOrNull() ?: LocShape.CentrepieceStraight.id
            val loc = locs.firstOrNull { it.shapeId == shape }
            if (loc == null) {
                player.mes("No loc with shape `${LocShape[shape]}` found on ${player.coords}")
                return
            }
            val type = ServerCacheManager.getObject(loc.id)!!
            locRepo.del(loc, duration)
            player.mes("Deleted loc `${type.internalName}` (duration: $duration cycles)")
            logger.debug { "Deleted loc: loc=$loc, type=$type" }
        }

    private fun npcAdd(cheat: Cheat) =
        with(cheat) {
            val typeId = "npc.${args[1]}".asRSCM()

            val type = ServerCacheManager.getNpc(typeId)
            if (type == null) {
                player.mes("That npc does not exist: $typeId")
                return
            }
            val duration = args[0].toInt()
            val npc = Npc(type, player.coords)
            npc.mode = NpcMode.None
            npcRepo.add(npc, duration)
            player.mes("Spawned npc `${args[1]}` (duration: $duration cycles)")
        }

    private fun testLoot(cheat: Cheat) =
        with(cheat) {
            if (args.isEmpty()) {
                player.mes(
                    "Use as ::testloot npcName [count] (ex: ::testloot godwars_bandos_avatar 100)"
                )
                return
            }
            val typeId = "npc.${args[0]}".asRSCM()
            val type = ServerCacheManager.getNpc(typeId)
            if (type == null) {
                player.mes("That npc does not exist: npc.${args[0]}")
                return
            }
            val count = args.getOrNull(1)?.toIntOrNull() ?: 100
            repeat(count) { i ->
                val npc = Npc(type, player.coords)
                val context = NpcDeathKillContext(hero = player, npc = npc, lootTrackerEventId = i)
                for (hook in deathKillHooks) {
                    hook.onKill(context)
                }
            }
            player.mes("Fired death-kill hooks for `npc.${args[0]}` x$count.")
        }

    private fun invAdd(cheat: Cheat) =
        with(cheat) {
            val (typeName, countArg) = args.asTypeNameAndNumber(defaultNumber = 1)
            val type = resolveObj(typeName)
            if (type == null) {
                player.mes("There is no obj mapped to: '$typeName'")
                return
            }

            val count = countArg.toLong().coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            val objName = type.name.ifEmpty { typeName }

            val spawned = player.invAdd(player.inv, type.id, count, strict = false)
            if (spawned.err is TransactionResult.RestrictedDummyitem) {
                player.mes("You can't spawn this item!")
                return
            }
            player.mes("Spawned inv obj `$objName` x ${spawned.completed().formatAmount}")
        }

    private fun invClear(cheat: Cheat) = with(cheat) { player.invClear(player.inv) }

    private fun setVarp(cheat: Cheat) =
        with(cheat) {
            val typeId = "varp.${args[0]}".asRSCM()

            val type = ServerCacheManager.getVarp(typeId)
            if (type == null) {
                player.mes("That varp does not exist: $typeId")
                return
            }
            val value = args[1].toInt()
            player.vars.backing[type.id] = value
            player.resyncVar(type)
            player.mes("Set varp '${args[0]}' to value: ${player.vars[type]}")
        }

    private fun setVarBit(cheat: Cheat) =
        with(cheat) {
            val typeId = "varbit.${args[0]}".asRSCM()

            val type = ServerCacheManager.getVarbit(typeId)
            if (type == null) {
                player.mes("That varbit does not exist: $typeId")
                return
            }
            val value = args[1].toInt()
            VarPlayerIntMapSetter.set(player, type, value)
            player.mes("Set varbit '${args[0]}' to value: ${player.vars[type]}")
        }

    private fun getVarp(cheat: Cheat) =
        with(cheat) {
            val typeId = "varp.${args[0]}".asRSCM()

            val type = ServerCacheManager.getVarp(typeId)
            if (type == null) {
                player.mes("That varp does not exist: $typeId")
                return
            }
            player.mes("Varp '${args[0]}' (id=$typeId) = ${player.vars[type]}")
        }

    private fun getVarBit(cheat: Cheat) =
        with(cheat) {
            val typeId = "varbit.${args[0]}".asRSCM()

            val type = ServerCacheManager.getVarbit(typeId)
            if (type == null) {
                player.mes("That varbit does not exist: $typeId")
                return
            }
            player.mes("Varbit '${args[0]}' (id=$typeId) = ${player.vars[type]}")
        }

    @OptIn(InternalApi::class)
    private fun Player.setStatLevels(level: Int) {
        val xp = PlayerSkillXPTable.getXPFromLevel(level)
        for (stat in ServerCacheManager.getStats().values) {
            val statInternal = RSCM.getReverseMapping(RSCMType.STAT, stat.id)

            val baseLevel = statMap.getBaseLevel(statInternal)
            val targetLevel = max(stat.minLevel, level)
            if (baseLevel > targetLevel) {
                statRevert(statInternal, targetLevel, xp)
                continue
            }
            val xpDelta = xp - statMap.getXP(statInternal)
            statMap.setCurrentLevel(statInternal, targetLevel.toByte())
            statAdvance(statInternal, xpDelta.toDouble(), rate = 1.0)
        }
    }

    // There is, by design, no helper function to decrease stat xp, as xp reduction is not a
    // standard operation in normal gameplay.
    @OptIn(InternalApi::class)
    private fun Player.statRevert(stat: String, targetLevel: Int, targetXp: Int) {
        statMap.setCurrentLevel(stat, statMap.getBaseLevel(stat))
        val levelDelta = stat(stat) - targetLevel
        require(levelDelta > 0) { "This function can only be used to reduce stat levels." }
        statMap.setXP(stat, targetXp)
        statMap.setBaseLevel(stat, targetLevel.toByte())
        statSub(stat, constant = levelDelta, percent = 0)
        appearance.combatLevel = PlayerSkillXP.calculateCombatLevel(this)
        PlayerInterfaceUpdates.updateCombatLevel(this)
    }

    private fun reboot(cheat: Cheat) {
        logger.info { "Reboot initiated by '${cheat.player.displayName}'." }
        SafeServiceExit.terminate()
    }

    private fun slowReboot(cheat: Cheat) =
        with(cheat) {
            val cycles = min(args[0].toInt(), 65535)
            if (cycles <= 0) {
                update.clear()
                return@with
            }
            update.startCountdown(cycles)
            for (p in playerList) {
                MiscOutput.updateRebootTimer(p, cycles)
            }
        }

    private fun dieTest(cheat: Cheat) =
        with(cheat) {
            val mode = args.getOrNull(0)?.lowercase()
            val inWildy = args.getOrNull(1)?.lowercase() == "true"
            when (mode) {
                "pvm" -> {
                    if (inWildy) player.insideWilderness = true
                    player.mes("Simulating PvM death${if (inWildy) " in Wilderness" else ""}.")
                    player.prepareAdminDieTest()
                    player.queueDeath()
                }
                "pvp" -> {
                    player.preparePvpDeath(player)
                    if (inWildy) player.insideWilderness = true
                    player.mes(
                        "Simulating PvP death${if (inWildy) " in Wilderness" else ""}. (self as killer)"
                    )
                    player.prepareAdminDieTest()
                    player.queueDeath()
                }
                else -> player.mes("Usage: ::die pvm|pvp [true|false]  (true = in Wilderness)")
            }
        }

    private fun bank(cheat: Cheat) =
        with(cheat) {
            protectedAccess.launch(player) {
                ifOpenMainSidePair(main = "interface.bankmain", side = "interface.bankside", transparency = -2)
            }
        }

    private fun transmog(cheat: Cheat) =
        with(cheat) {
            if (args.isEmpty()) {
                protectedAccess.launch(player) { resetTransmog() }
                player.mes("Transmog cleared.")
                return
            }
            val first = args[0]
            val npcName =
                if (first.toIntOrNull() != null) {
                    val resolved = RSCM.getReverseMapping(RSCMType.NPC, first.toInt())
                    if (resolved.isEmpty()) {
                        player.mes("No NPC mapped to ID: $first")
                        return
                    }
                    resolved
                } else {
                    "npc.${args.asTypeName()}"
                }
            protectedAccess.launch(player) { transmog(npcName) }
            player.mes("Transmog: '$npcName'")
        }

    private fun instanceExit(cheat: Cheat) = with(cheat) {
        val key = args.getOrNull(0)?.trim()
        if (key.isNullOrEmpty()) {
            player.mes("Usage: ::instanceexit instanceKey")
            player.mes("Known keys: ${instanceRegistry.keys().sorted().joinToString(", ")}")
            return@with
        }
        val spec = instanceRegistry.get(key)
        if (spec == null) {
            player.mes("No instance found with key: '$key'")
            player.mes("Known keys: ${instanceRegistry.keys().sorted().joinToString(", ")}")
            return@with
        }
        val exit = spec.area.exitCoord()
        if (exit == null || exit == CoordGrid.ZERO) {
            player.mes("Instance '$key' has no exit coord configured.")
            return@with
        }
        protectedAccess.launch(player) {
            player.mes("Teleported to '$key' instance exit coord: $exit")
            telejump(exit, TeleportType.Exempt)
        }
    }

    private fun InstanceArea.exitCoord(): CoordGrid? = when (this) {
        is InstanceArea.Template -> exitCoord
        is InstanceArea.CopyRegions -> exitCoord
    }

    private fun openInterface(cheat: Cheat) =
        with(cheat) {
            if (args.isEmpty()) {
                player.mes(
                    "Use as ::interface idOrName (ex: ::interface 219 or ::interface bankmain)"
                )
                return
            }
            val first = args[0]
            val interfName =
                if (first.toIntOrNull() != null) {
                    val id = first.toInt()
                    if (ServerCacheManager.getInterface(id) == null) {
                        player.mes("No interface exists with id: $id")
                        return
                    }
                    val resolved = RSCM.getReverseMapping(RSCMType.INTERFACE, id)
                    if (resolved.isEmpty()) {
                        player.mes("No RSCM name mapped to interface id: $id")
                        return
                    }
                    resolved
                } else {
                    "interface.${args.asTypeName()}"
                }
            val typeId = interfName.asRSCM()
            if (typeId == -1 || ServerCacheManager.getInterface(typeId) == null) {
                player.mes("That interface does not exist: '$interfName'")
                return
            }
            protectedAccess.launch(player) { ifOpenMain(interfName) }
            player.mes("Opened interface: '$interfName' (id=$typeId)")
        }

    private fun resolveArgTypeId(arg: String, names: Map<String, Int>): Int? {
        val argAsInt = arg.toIntOrNull()
        if (argAsInt != null) {
            return argAsInt
        }
        val sanitized = arg.replace("-", "_")
        return names[sanitized]
    }

    private fun resolveTypeName(name: String, names: Map<String, Int>): String =
        when {
            name in names -> name
            name.toIntOrNull() != null -> name
            else -> findClosestNameMatch(name, names.keys) ?: name
        }

    private fun List<String>.asTypeNameAndNumber(defaultNumber: Number): Pair<String, String> =
        if (size > 1 && last().toLongOrNull() != null) {
            dropLast(1).joinToString("_") to last()
        } else {
            joinToString("_") to defaultNumber.toString()
        }

    private fun resolveObj(input: String): ItemServerType? {
        val id = input.toIntOrNull()
        if (id != null) {
            return ServerCacheManager.getItem(id)
        }
        return ServerCacheManager.getItem("obj.$input".asRSCM(RSCMType.OBJ))
    }

    private fun List<String>.asTypeName(): String = joinToString("_")

    private fun findClosestNameMatch(input: String, names: Iterable<String>): String? {
        val normalizedInput = input.replace("_", " ")

        var bestMatchScore = 0.0f
        var bestMatchName: String? = null
        for (name in names) {
            val score = levenshteinMetric.compare(normalizedInput, name.replace("_", " "))
            if (score > bestMatchScore) {
                bestMatchScore = score
                bestMatchName = name
            }
        }

        return if (bestMatchScore >= 0.5) bestMatchName else null
    }
}
