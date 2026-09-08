package org.rsmod.content.skills.magic.spell.attacks.standard

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CrumbleUndeadRulesTest {
    @Test
    fun identifiesVorkathSpawn() = assertTrue(CrumbleUndeadRules.isVorkathSpawn("vorkath_spawn"))

    @Test
    fun identifiesNativeRevision240Spawn() =
        assertTrue(CrumbleUndeadRules.isVorkathSpawn("npc.vorkath_spawn"))

    @Test fun rejectsOtherUndead() = assertFalse(CrumbleUndeadRules.isVorkathSpawn("skeleton"))

    @Test
    fun bonusMinus63ForcesHit() = assertTrue(CrumbleUndeadRules.forceHitSpawn("npc.vorkath_spawn", -63))

    @Test
    fun bonusMinus64DoesNotForceHit() =
        assertFalse(CrumbleUndeadRules.forceHitSpawn("npc.vorkath_spawn", -64))

    @Test
    fun veryLowBonusDoesNotForceHit() =
        assertFalse(CrumbleUndeadRules.forceHitSpawn("npc.vorkath_spawn", -200))

    @Test
    fun otherUndeadNeverUsesSpawnException() =
        assertFalse(CrumbleUndeadRules.forceHitSpawn("zombie", 100))
}
