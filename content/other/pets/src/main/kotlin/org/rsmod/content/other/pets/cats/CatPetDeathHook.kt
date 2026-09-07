package org.rsmod.content.other.pets.cats

import jakarta.inject.Inject
import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.api.player.output.mes
import org.rsmod.game.entity.Player

/** A cat that is following its owner when they die runs off; cats cannot be insured. */
class CatPetDeathHook @Inject constructor(private val cats: CatPetManager) :
    PlayerDeathCleanupHook {
    override fun cleanup(player: Player) {
        val pet = cats.followingPet(player) ?: return
        cats.release(player)
        player.mes("Your ${pet.stage.label} has run away.")
    }
}
