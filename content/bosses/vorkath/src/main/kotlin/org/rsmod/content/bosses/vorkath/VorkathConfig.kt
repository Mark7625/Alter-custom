package org.rsmod.content.bosses.vorkath

import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.instances.InstanceArea
import org.rsmod.api.instances.InstanceSettings
import org.rsmod.api.instances.RegionLocal
import org.rsmod.map.CoordGrid

internal const val VORKATH_INSTANCE_KEY = "vorkath"
internal const val VORKATH_MAX_HITPOINTS = 750
internal const val VORKATH_NPC_LIFETIME = Int.MAX_VALUE
internal const val VORKATH_ATTACK_RATE = 5
internal const val VORKATH_STANDARD_ATTACKS = 6
internal const val VORKATH_ACID_SHOTS = 25
internal const val VORKATH_SPAWN_DISTANCE = 8
internal const val VORKATH_DEATH_TICKS = 6
internal const val VORKATH_FREEZE_TICKS = 100
internal const val VORKATH_ACID_POOL_LOC_ID = 32000
internal const val VORKATH_DROP_DURATION = 18_000
internal const val VORKATH_DEATH_FEE = 100_000
internal const val VORKATH_CRATER_ENTRANCE_LOC_ID = 31990
internal val VORKATH_CRATER_ENTRANCE_LOC_IDS = intArrayOf(31990, 31992, 31994)
internal const val VORKATH_WALL_MIN_LOCAL_X = 25
internal const val VORKATH_WALL_MAX_LOCAL_X = 38
internal const val VORKATH_WALL_LOCAL_Z = 20
internal const val VORKATH_WALL_APPROACH_MIN_LOCAL_Z = 19
internal const val VORKATH_WALL_APPROACH_MAX_LOCAL_Z = 22
internal const val VORKATH_SOURCE_REGION_X = 35
internal const val VORKATH_SOURCE_REGION_Z = 63
internal const val VORKATH_WALL_OUTSIDE_LOCAL_Z = 20
internal const val VORKATH_WALL_INSIDE_LOCAL_Z = 23
internal const val VORKATH_WALL_CROSS_CLIENT_CYCLES = 38

internal val VORKATH_OUTSIDE = CoordGrid(2272, 4044, 0)
internal val VORKATH_RELLEKKA = CoordGrid(2642, 3697, 0)

internal val VORKATH_DEFAULT_ENTRY = RegionLocal(0, 35, 63, 32, VORKATH_WALL_OUTSIDE_LOCAL_Z)
internal val VORKATH_AREA =
    InstanceArea.copyRegions(
        regionIds = listOf(9023),
        enterCoord = VORKATH_DEFAULT_ENTRY,
        exitCoord = VORKATH_OUTSIDE,
    )

internal fun vorkathSpec(activeType: dev.openrune.types.NpcServerType) =
    InstanceSettings(
            maxPlayers = 1,
            destroyWhenEmpty = true,
            bossNpc = listOf(activeType),
            bossName = "Vorkath",
            recommendedCombat = 100..126,
            teamSize = 1,
            description = "Instanced Vorkath encounter",
        )
        .withArea(VORKATH_AREA, settingsRowId = 0)

internal enum class VorkathState {
    ENTERING,
    SLEEPING,
    AWAKENING,
    ACTIVE,
    ZOMBIFIED_SPAWN_SPECIAL,
    ACID_SPECIAL,
    DYING,
    LOOTABLE,
    RESETTING,
    ENDED,
}

internal enum class VorkathSpecial {
    ACID,
    ZOMBIFIED_SPAWN,
}

internal enum class VorkathStandardAttack {
    MELEE,
    RANGED,
    MAGIC,
    DRAGONFIRE,
    VENOM_DRAGONFIRE,
    PRAYER_DRAGONFIRE,
    FIREBALL,
}

internal object VorkathAssets {
    const val SLEEPING = "npc.vorkath_sleeping"
    const val SLEEPING_NOOP = "npc.vorkath_sleeping_noop"
    const val ACTIVE = "npc.vorkath"
    const val SPAWN = "npc.vorkath_spawn"
    const val TORFINN = "npc.torfinn_ungael"
    const val TORFINN_COLLECT = "npc.torfinn_collect_ungael"
    const val TORFINN_RELLEKKA = "npc.torfinn_rellekka"
    const val TORFINN_COLLECT_RELLEKKA = "npc.torfinn_collect_rellekka"

    const val WAKE_ANIM = "seq.ds2_vorkath_spawn"
    const val DEATH_ANIM = "seq.ds2_vorkath_death"
    const val ICE_WALL_JUMP_ANIM = "seq.human_spot_jump"
    const val MELEE_ANIM = "seq.ds2_vorkath_attack_melee"
    const val RANGED_ANIM = "seq.ds2_vorkath_ranged"
    const val RANGED_UP_ANIM = "seq.ds2_vorkath_ranged_up"
    const val ACID_ANIM = "seq.ds2_vorkath_acid"
    const val SPAWN_DEATH_ANIM = "seq.ds2_spawn_death"
    const val SPAWN_ATTACK_ANIM = "seq.ds2_spawn_attack"
    const val FIREBALL_IMPACT = 1466
    const val RAPID_FIRE_IMPACT = 131
    const val SPAWN_EXPLOSION = 1460
    const val FIREBALL_IMPACT_SOUND = 163
    const val RAPID_FIRE_IMPACT_SOUND = 158

    const val RANGED_TRAVEL = "spotanim.vorkath_ranged_travel"
    const val MAGIC_TRAVEL = "spotanim.vorkath_magic_travel"
    const val DRAGONFIRE_TRAVEL = "spotanim.dragon_ranged_fire_attack"
    const val VENOM_DRAGONFIRE_TRAVEL = "spotanim.dragon_ranged_venom_attack"
    const val PRAYER_DRAGONFIRE_TRAVEL = "spotanim.dragon_ranged_corrupting_attack"
    const val RANGED_IMPACT = "spotanim.vorkath_ranged_impact"
    const val MAGIC_IMPACT = "spotanim.vorkath_magic_impact"
    const val FIREBALL_TRAVEL = "spotanim.vorkath_area_travel"
    const val RAPID_FIRE_TRAVEL = "spotanim.vorkath_area_small_travel"
    const val ACID_TRAVEL = "spotanim.vorkath_acid_travel"
    const val SPAWN_TRAVEL = "spotanim.vorkath_spawn_travel"
}

internal data class PendingFireball(val launchCycle: Int)

internal data class PendingStandardEffect(
    val impactCycle: Int,
    val attack: VorkathStandardAttack,
)

internal data class PendingTileHit(
    val impactCycle: Int,
    val tile: CoordGrid,
    val minimum: Int,
    val maximum: Int,
    val kind: VorkathTileAttack,
)

internal enum class VorkathTileAttack {
    FIREBALL,
    RAPID_FIRE,
}

internal val VORKATH_PERSONAL_BEST_TICKS: AttributeKey<Int> =
    AttributeKey(persistenceKey = "vorkath_personal_best_ticks")
internal val VORKATH_PREVIOUS_DROP_DURATION: AttributeKey<Int> = AttributeKey()
internal val VORKATH_STORAGE_REMINDER_SHOWN: AttributeKey<Boolean> = AttributeKey()
