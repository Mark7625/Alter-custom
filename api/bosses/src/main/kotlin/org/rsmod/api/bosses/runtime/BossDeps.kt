package org.rsmod.api.bosses.runtime

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.combat.formulas.MaxHitFormulae
import org.rsmod.api.player.hit.modifier.PlayerHitModifier
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.game.MapClock
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.routefinder.collision.CollisionFlagMap

@Singleton
class BossDeps @Inject constructor(
    val random: GameRandom,
    val worldRepo: WorldRepository,
    val npcRepo: NpcRepository,
    val playerList: PlayerList,
    val mapClock: MapClock,
    val worldQueues: WorldQueueList,
    val collision: CollisionFlagMap,
    val encounterRegistry: EncounterRegistry,
    val extensionRegistry: BossExtensionRegistry,
    val accuracy: AccuracyFormulae,
    val maxHit: MaxHitFormulae,
    val playerHitModifier: PlayerHitModifier,
)
