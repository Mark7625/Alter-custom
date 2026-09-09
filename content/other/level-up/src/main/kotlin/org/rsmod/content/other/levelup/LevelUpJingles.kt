package org.rsmod.content.other.levelup

/**
 * Everything picking a jingle needs to know about one level up.
 *
 * @param level The base level the player has just reached.
 * @param maxLevel The stat's own ceiling, from the cache. 99 for every released skill, but read
 *   rather than assumed so a server that raises a cap keeps the "mastery" jingle on the real last
 *   level.
 * @param unlocksContent Whether this level makes new content available. See [LevelUpUnlocks].
 */
internal data class LevelUp(val level: Int, val maxLevel: Int, val unlocksContent: Boolean)

/**
 * The one or two jingles a skill can play when it advances, and the rule that chooses between them.
 *
 * @param base The jingle played when [preferAlternate] does not apply.
 * @param alternate The skill's second jingle, or `null` for the skills that only have one.
 */
internal class SkillJingle(
    val base: String,
    val alternate: String? = null,
    private val preferAlternate: (LevelUp) -> Boolean = { false },
) {
    fun pick(levelUp: LevelUp): String =
        if (alternate != null && preferAlternate(levelUp)) alternate else base
}

/**
 * Maps each skill to its level-up jingles, following
 * [the wiki's jingle list](https://oldschool.runescape.wiki/w/Jingles).
 *
 * Most skills have two: a normal one and one for "level ups that unlock new content". The rest pick
 * between their pair on a fixed rule instead - Hitpoints and Strength swap over at 50, Hunter
 * alternates on odd/even levels, Construction has a separate every-tenth jingle, Sailing has one
 * reserved for 99, and Smithing only ever plays its unlock jingle because in Old School RuneScape
 * every Smithing level unlocks something. Agility is the only skill with a single jingle.
 *
 * The names are the cache's own, which is why some of them are spelled the way the original RuneScape
 * developers spelled them: `advance_defense`, `advance_herblaw`, `advance_firemarking`,
 * `advance_carpentry`, `advance_hunting`. Farming is the odd one out and uses `farming_levelup`
 * rather than an `advance_` name.
 */
internal object LevelUpJingles {
    /** Returns the jingles for [internalStatName] (an RSCM name such as `"stat.attack"`). */
    operator fun get(internalStatName: String): SkillJingle? = byStat[internalStatName]

    private val onUnlock: (LevelUp) -> Boolean = LevelUp::unlocksContent
    private val always: (LevelUp) -> Boolean = { true }
    private val fromLevelFifty: (LevelUp) -> Boolean = { it.level >= 50 }
    private val onEvenLevel: (LevelUp) -> Boolean = { it.level % 2 == 0 }
    private val everyTenthLevel: (LevelUp) -> Boolean = { it.level % 10 == 0 }
    private val atMaxLevel: (LevelUp) -> Boolean = { it.level >= it.maxLevel }

    private val byStat: Map<String, SkillJingle> =
        mapOf(
            // Skills whose second jingle marks a level that unlocks new content.
            "stat.attack" to SkillJingle("jingle.advance_attack", "jingle.advance_attack2", onUnlock),
            "stat.cooking" to
                SkillJingle("jingle.advance_cooking", "jingle.advance_cooking2", onUnlock),
            "stat.crafting" to
                SkillJingle("jingle.advance_crafting", "jingle.advance_crafting2", onUnlock),
            "stat.defence" to
                SkillJingle("jingle.advance_defense", "jingle.advance_defense2", onUnlock),
            "stat.farming" to SkillJingle("jingle.farming_levelup", "jingle.farming_levelup_2", onUnlock),
            "stat.firemaking" to
                SkillJingle("jingle.advance_firemarking", "jingle.advance_firemarking2", onUnlock),
            "stat.fishing" to
                SkillJingle("jingle.advance_fishing", "jingle.advance_fishing2", onUnlock),
            "stat.fletching" to
                SkillJingle("jingle.advance_fletching", "jingle.advance_fletching2", onUnlock),
            "stat.herblore" to
                SkillJingle("jingle.advance_herblaw", "jingle.advance_herblaw2", onUnlock),
            "stat.magic" to SkillJingle("jingle.advance_magic", "jingle.advance_magic2", onUnlock),
            "stat.mining" to SkillJingle("jingle.advance_mining", "jingle.advance_mining2", onUnlock),
            "stat.prayer" to SkillJingle("jingle.advance_prayer", "jingle.advance_prayer2", onUnlock),
            "stat.ranged" to SkillJingle("jingle.advance_ranged", "jingle.advance_ranged2", onUnlock),
            "stat.runecrafting" to
                SkillJingle("jingle.advance_runecraft", "jingle.advance_runecraft2", onUnlock),
            "stat.slayer" to SkillJingle("jingle.advance_slayer", "jingle.advance_slayer2", onUnlock),
            "stat.thieving" to
                SkillJingle("jingle.advance_thieving", "jingle.advance_thieving2", onUnlock),
            "stat.woodcutting" to
                SkillJingle("jingle.advance_woodcutting", "jingle.advance_woodcutting2", onUnlock),

            // Skills that pick between their pair on a fixed rule.
            "stat.agility" to SkillJingle("jingle.advance_agility"),
            "stat.construction" to
                SkillJingle("jingle.advance_carpentry", "jingle.advance_carpentry2", everyTenthLevel),
            "stat.hitpoints" to
                SkillJingle("jingle.advance_hitpoints", "jingle.advance_hitpoints2", fromLevelFifty),
            // The wiki names Hunter's pair "Even" and "Odd" without saying which cache name is
            // which; the `2` variant is paired with the even levels here.
            "stat.hunter" to
                SkillJingle("jingle.advance_hunting", "jingle.advance_hunting2", onEvenLevel),
            "stat.sailing" to
                SkillJingle("jingle.advance_sailing", "jingle.advance_sailing2", atMaxLevel),
            "stat.smithing" to
                SkillJingle("jingle.advance_smithing", "jingle.advance_smithing2", always),
            "stat.strength" to
                SkillJingle("jingle.advance_strength", "jingle.advance_strength2", fromLevelFifty),
        )
}
