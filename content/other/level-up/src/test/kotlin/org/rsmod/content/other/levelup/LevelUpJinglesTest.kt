package org.rsmod.content.other.levelup

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class LevelUpJinglesTest {
    @Test
    fun `every released skill has a jingle`() {
        for (skill in RELEASED_SKILLS) {
            assertNotNull(LevelUpJingles["stat.$skill"], "No jingle mapped for stat.$skill")
        }
    }

    /**
     * A jingle name without an archive id plays nothing, silently - which is how Farming and
     * Sailing level ups went quiet without any test noticing.
     */
    @Test
    fun `every jingle a skill can pick has a known archive id`() {
        for (skill in RELEASED_SKILLS) {
            val jingle = LevelUpJingles["stat.$skill"] ?: continue
            assertNotNull(Jingles.idOf(jingle.base), "No archive id for ${jingle.base}")
            val alternate = jingle.alternate ?: continue
            assertNotNull(Jingles.idOf(alternate), "No archive id for $alternate")
        }
    }

    private companion object {
        val RELEASED_SKILLS =
            listOf(
                "attack",
                "defence",
                "strength",
                "hitpoints",
                "ranged",
                "prayer",
                "magic",
                "cooking",
                "woodcutting",
                "fletching",
                "fishing",
                "firemaking",
                "crafting",
                "smithing",
                "mining",
                "herblore",
                "agility",
                "thieving",
                "slayer",
                "farming",
                "runecrafting",
                "hunter",
                "construction",
                "sailing",
            )
    }

    @Test
    fun `skills with a single jingle ignore the level`() {
        val agility = LevelUpJingles["stat.agility"]!!

        assertEquals("jingle.advance_agility", agility.pick(levelUp(2)))
        assertEquals("jingle.advance_agility", agility.pick(levelUp(99, unlocksContent = true)))
    }

    @Test
    fun `unlock skills only take their second jingle when the level unlocks content`() {
        val woodcutting = LevelUpJingles["stat.woodcutting"]!!

        assertEquals("jingle.advance_woodcutting", woodcutting.pick(levelUp(14)))
        assertEquals(
            "jingle.advance_woodcutting2",
            woodcutting.pick(levelUp(15, unlocksContent = true)),
        )
    }

    /** Smithing never plays its normal jingle, because every Smithing level unlocks something. */
    @Test
    fun `smithing always takes its unlock jingle`() {
        val smithing = LevelUpJingles["stat.smithing"]!!

        assertEquals("jingle.advance_smithing2", smithing.pick(levelUp(2)))
        assertEquals("jingle.advance_smithing2", smithing.pick(levelUp(99)))
    }

    @Test
    fun `hitpoints and strength swap jingles at fifty`() {
        val hitpoints = LevelUpJingles["stat.hitpoints"]!!
        val strength = LevelUpJingles["stat.strength"]!!

        assertEquals("jingle.advance_hitpoints", hitpoints.pick(levelUp(49)))
        assertEquals("jingle.advance_hitpoints2", hitpoints.pick(levelUp(50)))
        assertEquals("jingle.advance_strength", strength.pick(levelUp(49)))
        assertEquals("jingle.advance_strength2", strength.pick(levelUp(50)))
    }

    @Test
    fun `hunter alternates on odd and even levels`() {
        val hunter = LevelUpJingles["stat.hunter"]!!

        assertEquals("jingle.advance_hunting2", hunter.pick(levelUp(2)))
        assertEquals("jingle.advance_hunting", hunter.pick(levelUp(3)))
        assertEquals("jingle.advance_hunting2", hunter.pick(levelUp(98)))
        assertEquals("jingle.advance_hunting", hunter.pick(levelUp(99)))
    }

    @Test
    fun `construction has a separate jingle every tenth level`() {
        val construction = LevelUpJingles["stat.construction"]!!

        assertEquals("jingle.advance_carpentry", construction.pick(levelUp(9)))
        assertEquals("jingle.advance_carpentry2", construction.pick(levelUp(10)))
        assertEquals("jingle.advance_carpentry", construction.pick(levelUp(11)))
        assertEquals("jingle.advance_carpentry2", construction.pick(levelUp(90)))
    }

    /** Sailing's second jingle is reserved for the last level, whatever the cache caps it at. */
    @Test
    fun `sailing only takes its second jingle at max level`() {
        val sailing = LevelUpJingles["stat.sailing"]!!

        assertEquals("jingle.advance_sailing", sailing.pick(levelUp(98)))
        assertEquals("jingle.advance_sailing2", sailing.pick(levelUp(99)))
        assertEquals(
            "jingle.advance_sailing2",
            sailing.pick(LevelUp(level = 120, maxLevel = 120, unlocksContent = false)),
        )
    }

    private fun levelUp(level: Int, unlocksContent: Boolean = false): LevelUp =
        LevelUp(level = level, maxLevel = 99, unlocksContent = unlocksContent)
}
