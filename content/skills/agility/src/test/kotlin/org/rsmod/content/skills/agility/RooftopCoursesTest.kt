package org.rsmod.content.skills.agility

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.content.skills.agility.rooftop.ObstacleMove
import org.rsmod.content.skills.agility.rooftop.RooftopCourse
import org.rsmod.content.skills.agility.rooftop.RooftopCourses
import org.rsmod.map.CoordGrid

class RooftopCoursesTest {
    @Test
    fun `every course has a layout and every layout ends with its only finish obstacle`() {
        for (course in RooftopCourse.entries) {
            val layout = RooftopCourses.layout(course)
            assertTrue(layout.obstacles.size >= 6, "$course has too few obstacles")
            val finishes = layout.obstacles.filter { it.isFinish }
            assertEquals(1, finishes.size, "$course must have exactly one finish obstacle")
            assertTrue(layout.obstacles.last().isFinish, "$course finish must be the last obstacle")
            assertTrue(layout.markTiles.isNotEmpty(), "$course needs mark of grace tiles")
        }
    }

    @Test
    fun `lap experience matches the wiki totals`() {
        val expected =
            mapOf(
                RooftopCourse.Draynor to 120.0,
                RooftopCourse.AlKharid to 216.0,
                RooftopCourse.Varrock to 269.7,
                RooftopCourse.Canifis to 240.0,
                RooftopCourse.Falador to 586.0,
                RooftopCourse.Seers to 570.0,
                RooftopCourse.Pollnivneach to 890.0,
                RooftopCourse.Rellekka to 780.0,
                RooftopCourse.Ardougne to 889.0,
            )
        for ((course, xp) in expected) {
            assertEquals(xp, RooftopCourses.layout(course).lapXp, 0.01, "$course lap xp")
        }
    }

    @Test
    fun `courses start on the ground and finish on the ground`() {
        for (layout in RooftopCourses.layouts) {
            val first = layout.obstacles.first()
            val last = layout.obstacles.last()
            assertEquals(0, first.start.level, "${layout.course} should start at ground level")
            assertEquals(0, last.move.destination.level, "${layout.course} should end at ground level")
        }
    }

    @Test
    fun `every move leaves its start tile and balance paths step one tile at a time`() {
        for (layout in RooftopCourses.layouts) {
            for (obstacle in layout.obstacles) {
                val label = "${layout.course} ${obstacle.name}"
                assertNotEquals(obstacle.start, obstacle.move.destination, "$label does not move")
                val move = obstacle.move
                if (move is ObstacleMove.Balance) {
                    var previous = obstacle.start
                    for (tile in move.path) {
                        assertTrue(
                            previous.chebyshevDistance(tile) <= 1 || previous.level != tile.level,
                            "$label path jumps from $previous to $tile",
                        )
                        previous = tile
                    }
                }
                obstacle.failure?.let { failure ->
                    assertEquals(0, failure.landing.level, "$label should fall to the ground")
                    assertTrue(failure.noFailLevel > layout.course.level, "$label no-fail level")
                    assertTrue(failure.minDamage in 1..failure.maxDamage, "$label damage range")
                }
            }
        }
    }

    @Test
    fun `obstacles chain together without teleporting across the map`() {
        for (layout in RooftopCourses.layouts) {
            val obstacles = layout.obstacles
            for (index in 1 until obstacles.size) {
                val landed = obstacles[index - 1].move.destination
                val next = obstacles[index].start
                assertTrue(
                    landed.chebyshevDistance(next) <= 20,
                    "${layout.course}: landing $landed is too far from next start $next",
                )
            }
        }
    }

    @Test
    fun `locs are unique across all courses`() {
        val locs = RooftopCourses.layouts.flatMap { it.obstacles }.flatMap { it.locs }
        assertEquals(locs.size, locs.toSet().size, "duplicate obstacle locs: $locs")
        assertTrue(locs.all { it.startsWith("loc.rooftops_") })
    }

    @Test
    fun `line spells out straight and diagonal paths`() {
        val straight = line(CoordGrid(10, 10, 3), CoordGrid(10, 7, 3))
        assertEquals(listOf(CoordGrid(10, 9, 3), CoordGrid(10, 8, 3), CoordGrid(10, 7, 3)), straight)

        val mixed = line(CoordGrid(0, 0, 1), CoordGrid(2, 3, 1))
        assertEquals(
            listOf(CoordGrid(1, 1, 1), CoordGrid(2, 2, 1), CoordGrid(2, 3, 1)),
            mixed,
        )
        assertFalse(line(CoordGrid(5, 5, 0), CoordGrid(5, 5, 0)).isNotEmpty())
    }

    @Test
    fun `success chance rises to certainty at the no-fail level`() {
        assertEquals(60, successChance(level = 20, requiredLevel = 20, noFailLevel = 30))
        assertEquals(80, successChance(level = 25, requiredLevel = 20, noFailLevel = 30))
        assertEquals(100, successChance(level = 30, requiredLevel = 20, noFailLevel = 30))
        assertEquals(100, successChance(level = 99, requiredLevel = 20, noFailLevel = 30))
        assertEquals(60, successChance(level = 1, requiredLevel = 20, noFailLevel = 30))
    }
}
