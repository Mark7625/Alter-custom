package org.rsmod.api.repo.world

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ProjAnimType
import dev.openrune.types.aconverted.SpotanimType
import dev.openrune.types.aconverted.SynthType
import jakarta.inject.Inject
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.proj.ProjAnim
import org.rsmod.map.CoordGrid

public class WorldRepository @Inject constructor(private val zoneUpdates: ZoneUpdateMap) {
    public fun locAnim(loc: LocInfo, seq: String) {
        zoneUpdates.locAnim(loc, seq.asRSCM(RSCMType.SEQ))
    }

    public fun locAnim(loc: BoundLocInfo, seq: String) {
        locAnim(LocInfo(loc.layer, loc.coords, loc.entity), seq)
    }

    public fun soundArea(
        source: CoordGrid,
        synth: String,
        delay: Int = 0,
        loops: Int = 1,
        radius: Int = 5,
        size: Int = 0,
    ) {
        zoneUpdates.soundArea(source, synth.asRSCM(RSCMType.SYNTH), delay, loops, radius, size)
    }

    public fun soundArea(
        source: CoordGrid,
        synth: SynthType,
        delay: Int = 0,
        loops: Int = 1,
        radius: Int = 5,
        size: Int = 0,
    ) {
        zoneUpdates.soundArea(source, synth.id, delay, loops, radius, size)
    }

    public fun soundArea(
        source: PathingEntity,
        synth: String,
        delay: Int = 0,
        loops: Int = 1,
        radius: Int = 5,
    ) {
        soundArea(source.coords, synth, delay, loops, radius, source.size)
    }

    public fun spotanimMap(
        spotanim: SpotanimType,
        coord: CoordGrid,
        height: Int = 0,
        delay: Int = 0,
    ) {
        zoneUpdates.mapAnim(spotanim.id, coord, height, delay)
    }

    public fun projAnim(projAnim: ProjAnim) {
        zoneUpdates.mapProjAnim(projAnim)
    }

    public fun projAnim(
        source: Player,
        target: Npc,
        spotanim: SpotanimType,
        type: String,
    ): ProjAnim {
        val projAnim = ProjAnim.fromBoundsToNpc(source.bounds(), target, spotanim.id, type)
        projAnim(projAnim)
        return projAnim
    }

    public fun projAnimSourced(
        source: Player,
        target: Npc,
        spotanim: SpotanimType,
        type: String,
    ): ProjAnim {
        val projAnim = ProjAnim.fromPlayerToNpc(source, target, spotanim.id, type)
        projAnim(projAnim)
        return projAnim
    }

    public fun projAnim(
        source: Player,
        target: Player,
        spotanim: SpotanimType,
        type: String,
    ): ProjAnim {
        val projAnim = ProjAnim.fromBoundsToPlayer(source.bounds(), target, spotanim.id, type)
        projAnim(projAnim)
        return projAnim
    }

    public fun projAnimSourced(
        source: Player,
        target: Player,
        spotanim: SpotanimType,
        type: String,
    ): ProjAnim {
        val projAnim = ProjAnim.fromPlayerToPlayer(source, target, spotanim.id, type)
        projAnim(projAnim)
        return projAnim
    }

    public fun projAnim(
        source: Player,
        target: CoordGrid,
        spotanim: SpotanimType,
        type: String,
    ): ProjAnim {
        val projAnim = ProjAnim.fromBoundsToCoord(source.bounds(), target, spotanim.id, type)
        projAnim(projAnim)
        return projAnim
    }

    public fun projAnimSourced(
        source: Player,
        target: CoordGrid,
        spotanim: SpotanimType,
        type: String,
    ): ProjAnim {
        val projAnim = ProjAnim.fromPlayerToCoord(source, target, spotanim.id, type)
        projAnim(projAnim)
        return projAnim
    }

    public fun projAnim(
        source: Npc,
        target: Npc,
        spotanim: SpotanimType,
        type: String,
    ): ProjAnim {
        val projAnim = ProjAnim.fromBoundsToNpc(source.bounds(), target, spotanim.id, type)
        projAnim(projAnim)
        return projAnim
    }

    public fun projAnimSourced(
        source: Npc,
        target: Npc,
        spotanim: SpotanimType,
        type: ProjAnimType,
    ): ProjAnim {
        val projAnim = ProjAnim.fromNpcToNpc(source, target, spotanim.id, type)
        projAnim(projAnim)
        return projAnim
    }

    public fun projAnim(
        source: Npc,
        target: Player,
        spotanim: SpotanimType,
        type: String,
    ): ProjAnim {
        val projAnim = ProjAnim.fromBoundsToPlayer(source.bounds(), target, spotanim.id, type)
        projAnim(projAnim)
        return projAnim
    }

    public fun projAnimSourced(
        source: Npc,
        target: Player,
        spotanim: SpotanimType,
        type: ProjAnimType,
    ): ProjAnim {
        val projAnim = ProjAnim.fromNpcToPlayer(source, target, spotanim.id, type)
        projAnim(projAnim)
        return projAnim
    }

    public fun projAnim(
        source: Npc,
        target: CoordGrid,
        spotanim: SpotanimType,
        type: String,
    ): ProjAnim {
        val projAnim = ProjAnim.fromBoundsToCoord(source.bounds(), target, spotanim.id, type)
        projAnim(projAnim)
        return projAnim
    }

    public fun projAnimSourced(
        source: Npc,
        target: CoordGrid,
        spotanim: SpotanimType,
        type: ProjAnimType,
    ): ProjAnim {
        val projAnim = ProjAnim.fromNpcToCoord(source, target, spotanim.id, type)
        projAnim(projAnim)
        return projAnim
    }
}
