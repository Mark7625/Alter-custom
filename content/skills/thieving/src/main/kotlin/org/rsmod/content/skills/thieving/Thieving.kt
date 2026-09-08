package org.rsmod.content.skills.thieving

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.stats.xpmod.XpModifiers

/** Constants and small helpers shared by the pickpocket, stall and chest scripts. */
object Thieving {
    const val STAT: String = "stat.thieving"

    /** Player animation for pickpocketing npcs and stealing from stalls and chests. */
    const val STEAL_ANIM: String = "seq.human_pickpocket"

    /** Birds-around-the-head graphic shown while stunned after a failed pickpocket. */
    const val STUN_SPOTANIM: String = "spotanim.stunned_thieving"

    /** Cycles a failed pickpocket holds the player in place (5.4 seconds). */
    const val STUN_CYCLES: Int = 9

    /** The stun timer [org.rsmod.api.combat.commons.CombatEffects.stun] schedules. */
    const val STUN_TIMER: String = "timer.combat_stun"

    /** Successful pickpocket / stall theft sound. */
    const val STEAL_SYNTH: String = "synth.pick"

    /** Played when a failed pickpocket stuns the player. */
    const val STUN_SYNTH: String = "synth.thieving_stunned"

    fun objName(obj: String): String =
        ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ))?.name ?: obj.removePrefix("obj.")

    /** "a cake", "an apple", "some silk", "some bronze bolts". */
    fun withArticle(name: String): String {
        val lower = name.lowercase()
        val uncountable = lower in UNCOUNTABLE || (lower.endsWith("s") && !lower.endsWith("ss"))
        return when {
            uncountable -> "some $lower"
            lower.firstOrNull() in VOWELS -> "an $lower"
            else -> "a $lower"
        }
    }

    fun ProtectedAccess.giveThievingXp(xpMods: XpModifiers, xp: Double) {
        statAdvance(STAT, xp * xpMods.get(player, STAT))
    }

    /** Adds every drop to the inventory, dropping to the floor when it does not fit. */
    fun ProtectedAccess.giveLoot(objRepo: ObjRepository, drops: List<LootDrop>, multiplier: Int = 1) {
        for (drop in drops) {
            invAddOrDrop(objRepo, drop.obj, drop.count * multiplier)
        }
    }

    private val VOWELS = setOf('a', 'e', 'i', 'o', 'u')
    private val UNCOUNTABLE =
        setOf("silk", "fur", "grey wolf fur", "spice", "coins", "tokkul", "bread", "garlic")
}
