package org.rsmod.content.skills.hunter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.utils.skills.SkillingSuccessRate

/**
 * Every shipped catch-rate pair, checked against the published chart it was fit against
 * (docs/hunter.md). Every expected number is read off a wiki chart checked in under
 * `src/test/resources/wiki-charts`, not copied out of `HunterTables`, and rates are read back out
 * of the *packed* dbtable, so a column-id gap that shifts a table sideways fails here too.
 */
@ResourceLock(HUNTER_TEST_WORLD_LOCK)
class HunterRateTablesTest {
    /** Every charted point is reproduced exactly by the pair the server ships for that creature. */
    @Test
    fun everyChartedPointReproducesItsShippedPair() {
        val charts = readCharts()
        var checked = 0
        for (series in CHARTED) {
            val rate = shippedRate(series.npc)
            val points = charts.getValue(series.series)
            for ((level, expected) in points) {
                assertEquals(
                    expected,
                    charted(rate.low + series.netBonus, rate.high + series.netBonus, level),
                    "${series.series} (${series.npc}) at level $level: chart says $expected/256",
                )
                checked++
            }
        }
        assertEquals(300, checked, "Chart point count changed; confirm the resources are intact.")
    }

    /**
     * Each chart begins at its creature's level requirement - the cross-check that catches a
     * swapped pair of level requirements, which reproducing the curves alone would not.
     */
    @Test
    fun everyChartStartsAtItsCreaturesLevelRequirement() {
        val charts = readCharts()
        for (series in CHARTED) {
            val first = charts.getValue(series.series).minOf { it.level }
            assertEquals(
                shippedRate(series.npc).level,
                first,
                "${series.series}: chart starts at L$first, so ${series.npc}'s requirement should too",
            )
        }
    }

    /** No chart sits in the resources unaccounted for, mapped to nothing and asserted by nothing. */
    @Test
    fun everyChartedSeriesIsEitherMappedToARowOrDeclaredUnshipped() {
        val mapped = CHARTED.map { it.series }.toSet()
        for (series in readCharts().keys.sorted()) {
            assertTrue(
                series in mapped || series in UNSHIPPED_SERIES,
                "Chart series '$series' is mapped to no creature and not declared unshipped.",
            )
        }
    }

    /**
     * Every shipped pair is the chart template's **own published parameter**, exactly - strictly
     * stronger than reproducing the chart, which does not pin a pair (docs/hunter.md).
     */
    @Test
    fun everyShippedPairIsThePublishedParameter() {
        val params = readParams()
        for (entry in PUBLISHED) {
            val key = "${entry.page}|${entry.series}"
            val published = checkNotNull(params[key]) { "No published parameters for $key" }
            val rate = shippedRate(entry.npc)
            assertEquals(
                (published.low - entry.netBonus) to (published.high - entry.netBonus),
                rate.low to rate.high,
                "${entry.npc}: $key publishes (${published.low}, ${published.high})" +
                    if (entry.netBonus != 0) " less the ${entry.netBonus} net bonus" else "",
            )
        }
    }

    /** The template also publishes each creature's level requirement; it must be the shipped one. */
    @Test
    fun everyShippedLevelIsThePublishedRequirement() {
        val params = readParams()
        for (entry in PUBLISHED) {
            val published = params.getValue("${entry.page}|${entry.series}")
            assertEquals(
                published.req,
                shippedRate(entry.npc).level,
                "${entry.npc}: ${entry.page} publishes req=${published.req}",
            )
        }
    }

    /** No charted creature may rely on a fit when its parameters are published. */
    @Test
    fun everyChartedCreatureAlsoHasItsPublishedParameterAsserted() {
        assertEquals(
            emptySet<String>(),
            CHARTED.map { it.npc }.toSet() - PUBLISHED.map { it.npc }.toSet(),
            "Charted creatures whose published parameters are not asserted.",
        )
    }

    private fun readParams(): Map<String, PublishedParams> =
        checkNotNull(javaClass.getResourceAsStream("/wiki-charts/published-params.tsv")) {
                "Missing /wiki-charts/published-params.tsv"
            }
            .bufferedReader()
            .readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .associate { line ->
                val f = line.split("\t")
                require(f.size == 6) { "Malformed params row: $line" }
                "${f[0]}|${f[2]}" to PublishedParams(f[3].toInt(), f[4].toInt(), f[5].toInt())
            }

    private data class PublishedParams(val low: Int, val high: Int, val req: Int)

    /** A published chart series, and the shipped row whose pair it is the source for. */
    private data class Published(
        val page: String,
        val series: String,
        val npc: String,
        val netBonus: Int = 0,
    )

    private fun shippedRate(npc: String): ShippedRate =
        checkNotNull(allShippedRates().firstOrNull { it.npc == npc }) {
            "No shipped rate row for $npc"
        }

    private fun allShippedRates(): List<ShippedRate> =
        HunterCreatures.all.map { ShippedRate(it.npc, it.level, it.successLow, it.successHigh) }

    private fun firstCertainLevel(rate: ShippedRate): Int =
        (1..99).first { charted(rate.low, rate.high, it) == 256 }

    /** What the engine rolls against, out of 256, uncapped. */
    private fun chance256(low: Int, high: Int, level: Int): Int =
        Math.round(SkillingSuccessRate.successRate(low, high, level, 99) * 256).toInt()

    /** What the wiki *charts*, which is [chance256] capped at certainty. */
    private fun charted(low: Int, high: Int, level: Int): Int =
        minOf(256, chance256(low, high, level))

    private fun readCharts(): Map<String, List<ChartPoint>> =
        CHART_FILES
            .flatMap { file ->
                val resource = "/wiki-charts/$file"
                val text =
                    checkNotNull(javaClass.getResourceAsStream(resource)) {
                            "Missing chart resource $resource"
                        }
                        .bufferedReader()
                        .readText()
                text.lineSequence()
                    .filter { it.isNotBlank() && !it.startsWith("#") }
                    .map { line ->
                        val fields = line.trim().split(Regex("\\s+"))
                        require(fields.size == 3) { "Malformed row in $file: $line" }
                        fields[0] to ChartPoint(fields[1].toInt(), fields[2].toInt())
                    }
                    .toList()
            }
            .groupBy({ it.first }, { it.second })

    private data class ChartPoint(val level: Int, val chance256: Int)

    private data class ShippedRate(val npc: String, val level: Int, val low: Int, val high: Int)

    /** A charted series and the row it is the source for. */
    private data class Charted(val series: String, val npc: String, val netBonus: Int = 0)

    companion object {
        private val CHART_FILES =
            listOf(
                "birdsnare-chance.tsv",
                "boxtrap-chance.tsv",
            )

        private val CHARTED =
            listOf(
                Charted("crimson_swift", "npc.hunting_bird_jungle"),
                Charted("golden_warbler", "npc.hunting_bird_desert"),
                Charted("copper_longtail", "npc.hunting_bird_woodland"),
                Charted("cerulean_twitch", "npc.hunting_bird_polar"),
                Charted("chinchompa", "npc.hunting_chinchompa"),
                Charted("carnivorous_chinchompa", "npc.hunting_chinchompa_big"),
                Charted("black_chinchompa", "npc.hunting_chinchompa_black"),
            )

        /** The published `{{Skilling success chart}}` parameters, keyed by page and series label. */
        private val PUBLISHED =
            listOf(
                Published("Crimson swift", "Crimson swift", "npc.hunting_bird_jungle"),
                Published("Golden warbler", "Golden warbler", "npc.hunting_bird_desert"),
                Published("Copper longtail", "Copper longtail", "npc.hunting_bird_woodland"),
                Published("Cerulean twitch", "Cerulean twitch", "npc.hunting_bird_polar"),
                Published("Chinchompa (Hunter)", "Grey", "npc.hunting_chinchompa"),
                Published("Chinchompa (Hunter)", "Red", "npc.hunting_chinchompa_big"),
                Published("Chinchompa (Hunter)", "Black", "npc.hunting_chinchompa_black"),
            )

        /**
         * Charted but deliberately not shipped; see
         * [theMoonlightMothIsNotOnTheOtherButterfliesCurve].
         */
        private val UNSHIPPED_SERIES = setOf("moonlight_moth", "moonlight_moth_magicnet")

        @JvmStatic
        @BeforeAll
        fun loadCache() {
            HunterTestCache.load()
        }
    }
}
