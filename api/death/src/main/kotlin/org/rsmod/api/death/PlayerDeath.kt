package org.rsmod.api.death

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.or2.central.account.Rights
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.area.checker.isInWildernessBasic
import org.rsmod.api.mechanics.toxins.Toxin.cureAllToxins
import org.rsmod.api.player.death.DEATH_CAUSE_ATTR
import org.rsmod.api.player.death.DeathCause
import org.rsmod.api.player.deathResetTimers
import org.rsmod.api.player.disablePrayers
import org.rsmod.api.player.hasProtectItemPrayer
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.midiJingle
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

@Singleton
public class PlayerDeath
@Inject
constructor(
    private val mapClock: MapClock,
    private val drops: PlayerDeathDrops,
    private val handlingResolver: PlayerDeathHandlingResolver,
    private val cleanupHooks: Set<PlayerDeathCleanupHook>,
    private val respawnHooks: Set<PlayerRespawnHook>,
    private val areaChecker: AreaChecker,
    private val worldRepo: WorldRepository,
) {
    private var Player.specialAttackType by intVarp("varp.sa_attack")
    private var Player.inInstance by boolVarBit("varbit.player_in_instance")
    private var Player.insideWilderness by boolVarBit("varbit.inside_wilderness")

    public suspend fun death(access: ProtectedAccess) {
        access.deathSequence()
    }

    private suspend fun ProtectedAccess.deathSequence() {
        val respawn = respawnHooks.firstNotNullOfOrNull { it.respawn(player) } ?: DEFAULT_RESPAWN
        val randomRespawn = mapFindSquareLineOfWalk(respawn, minRadius = 0, maxRadius = 2)

        stopAction()
        delay(2)
        // An area sound, so players nearby hear the death as well as the one dying.
        worldRepo.soundArea(player, "synth.human_death", radius = DEATH_SOUND_RADIUS)
        anim("seq.human_death")
        delay(4)
        combatClearQueue()
        clearQueue("queue.death")

        val deathCoords = player.coords
        handleDeathDrops(player, deathCoords)

        midiSong("midi.stop_music")
        player.midiJingle(DEATH_JINGLE_GROUP)
        mes("Oh dear, you are dead!")
        telejump(randomRespawn ?: respawn, TeleportType.Exempt)
        resetAnim()
        resetPlayerState()
        restoreToplevelTabs(
            "component.toplevel_osrs_stretch:pvp_icons",
            "component.toplevel_osrs_stretch:side1",
            "component.toplevel_osrs_stretch:side2",
            "component.toplevel_osrs_stretch:side4",
            "component.toplevel_osrs_stretch:side5",
            "component.toplevel_osrs_stretch:side6",
            "component.toplevel_osrs_stretch:side9",
            "component.toplevel_osrs_stretch:side8",
            "component.toplevel_osrs_stretch:side7",
            "component.toplevel_osrs_stretch:side10",
            "component.toplevel_osrs_stretch:side11",
            "component.toplevel_osrs_stretch:side12",
            "component.toplevel_osrs_stretch:side13",
        )
    }

    private fun handleDeathDrops(player: Player, deathCoords: CoordGrid) {
        val bypassAdmin = player.deathDropsBypassAdmin()
        player.attr.remove(DEATH_DROPS_BYPASS_ADMIN_ATTR)
        if (player.modLevel.isAtLeast(Rights.ADMINISTRATOR) && !bypassAdmin) return

        val killer = when (val cause = player.attr[DEATH_CAUSE_ATTR]) {
            is DeathCause.ByPlayer -> cause.killer
            else -> player.attr[DEATH_KILLER_ATTR]
        }

        val context = buildContext(player, deathCoords, killer)
        val handling = handlingResolver.resolve(context)

        val result = drops.selectDrops(player, context, handling)
        drops.applyDrops(player, result, handling, deathCoords)
        drops.spawnRemains(deathCoords, handling)

        player.attr.remove(DEATH_KILLER_ATTR)
        player.attr.remove(DEATH_CAUSE_ATTR)
    }

    private fun buildContext(player: Player, deathCoords: CoordGrid, killer: Player?): PlayerDeathContext {
        val wildernessLevel = deathCoords.wildernessLevel()
        val recentPvp = wasRecentlyHitByPlayer(player)

        return PlayerDeathContext(
            player = player,
            coords = deathCoords,
            inWilderness = player.insideWilderness,
            wildernessLevel = wildernessLevel,
            inRevenantCaves = areaChecker.inArea("area.revenant_caves", deathCoords),
            inInstance = player.inInstance,
            isSkulled = player.hasSkullDeathPenalty(),
            hasProtectItem = player.hasProtectItemPrayer() && !player.isHighRiskSkulled(),
            recentPvpDamage = recentPvp,
            gamemode = player.gamemode,
            killer = killer,
        )
    }

    private fun ProtectedAccess.resetPlayerState() {
        player.disablePrayers()
        player.cureAllToxins()
        player.deathResetTimers()

        player.specialAttackType = 0
        player.skullIcon = null
        // Attributes flagged `resetOnDeath` (vengeance, toxin immunity, absorption) end here.
        player.attr.removeIf { it.resetOnDeath }

        for (hook in cleanupHooks) {
            hook.cleanup(player)
        }

        rebuildAppearance()
        camReset()
        statRestoreAll(ServerCacheManager.getStats().values.map { RSCM.getReverseMapping(RSCMType.STAT, it.id) })
        minimapReset()
    }

    private fun wasRecentlyHitByPlayer(player: Player): Boolean {
        val lastHitTick = player.attr[LAST_PVP_HIT_TICK_ATTR] ?: return false
        return (mapClock - lastHitTick) <= RECENT_PVP_HIT_TICKS
    }

    private companion object {
        private const val DEATH_SOUND_RADIUS = 10

        private val DEFAULT_RESPAWN = CoordGrid(0, 50, 50, 21, 18)

        /**
         * Js5 archive 11 group of the death jingle. The `jingle.death` gameval id is not the group
         * id the client plays (see [org.rsmod.api.player.midiJingle]); this number was confirmed
         * in-game by ear with the `::jingle` command.
         */
        private const val DEATH_JINGLE_GROUP = 90

        private fun CoordGrid.wildernessLevel(): Int {
            if (!isInWildernessBasic()) return -1
            val y = z
            return when {
                level == 0 && x in 2944..3392 && y in 3520..4351 -> ((y - 3520) shr 3) + 1
                level == 0 && x in 3008..3071 && y in 10112..10175 -> ((y - 9920) shr 3) - 1
                level == 0 && x in 2944..3455 && y in 9920..10879 -> ((y - 9920) shr 3) + 1
                else -> 1
            }
        }
    }
}
