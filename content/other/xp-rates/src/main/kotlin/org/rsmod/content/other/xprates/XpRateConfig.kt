package org.rsmod.content.other.xprates

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.github.michaelbull.logging.InlineLogger
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.writeText

/**
 * Experience rate settings, read from `xp-rates.yml` next to `game.yml`.
 *
 * @param rate The per-player experience multiplier. `1.0` is authentic Old School RuneScape.
 * @param globalRate A second multiplier compounded on top of [rate], meant for temporary
 *   server-wide events such as a double xp weekend.
 * @param enforceOnLogin When `true`, [rate] is written onto every character as they log in,
 *   replacing whatever rate is stored on their account. Turn this off if you hand out individual
 *   rates with `::setxprate` and want them to stick.
 * @param resetExistingLevels When `true`, each character has their skills reset to fresh-account
 *   values the first time they log in, to clear levels earned at an old inflated rate. This is
 *   destructive and irreversible, so it defaults to `false`. Every character is flagged once it has
 *   been reset, so this only ever fires once per character and is safe to leave on.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class XpRateConfig(
    val rate: Double = AUTHENTIC_RATE,
    @JsonProperty("global-rate") val globalRate: Double = AUTHENTIC_RATE,
    @JsonProperty("enforce-on-login") val enforceOnLogin: Boolean = true,
    @JsonProperty("reset-existing-levels") val resetExistingLevels: Boolean = false,
) {
    /**
     * Returns a copy with both rates clamped into [MIN_RATE]`..`[MAX_RATE], falling back to
     * [AUTHENTIC_RATE] for values that are not finite. A hand-edited config should never be able to
     * push [org.rsmod.api.player.stat.statAdvance] into the overflow it throws on.
     */
    fun sanitized(): XpRateConfig =
        copy(rate = clampRate(rate), globalRate = clampRate(globalRate))

    companion object {
        /** Old School RuneScape's own rate. See https://oldschool.runescape.wiki/w/Experience. */
        const val AUTHENTIC_RATE: Double = 1.0

        const val MIN_RATE: Double = 0.01
        const val MAX_RATE: Double = 10_000.0

        fun clampRate(rate: Double): Double =
            if (rate.isFinite()) rate.coerceIn(MIN_RATE, MAX_RATE) else AUTHENTIC_RATE
    }
}

/**
 * Reads [XpRateConfig] from disk, writing a commented default file the first time the server boots
 * with this plugin installed.
 *
 * The default file is written from a literal template rather than serialized from [XpRateConfig] so
 * that the comments explaining each knob survive; a server owner editing this by hand is the whole
 * point of the file existing.
 */
class XpRateConfigLoader {
    private val logger = InlineLogger()

    fun loadOrCreate(file: Path = DEFAULT_FILE): XpRateConfig {
        if (!file.exists()) {
            file.writeText(DEFAULT_FILE_CONTENTS)
            logger.info { "Created default xp rate config in file: $file" }
            return XpRateConfig()
        }
        return load(file)
    }

    fun load(file: Path): XpRateConfig {
        val parsed =
            runCatching { yamlMapper.readValue(file.toFile(), XpRateConfig::class.java) }
                .getOrElse { error ->
                    logger.warn(error) { "Could not read '$file'; using authentic xp rates." }
                    return XpRateConfig()
                }
        val sanitized = parsed.sanitized()
        if (sanitized != parsed) {
            logger.warn {
                "Clamped out-of-range xp rates in '$file': $parsed -> $sanitized " +
                    "(valid range: ${XpRateConfig.MIN_RATE}-${XpRateConfig.MAX_RATE})"
            }
        }
        return sanitized
    }

    companion object {
        val DEFAULT_FILE: Path = Paths.get("./", "xp-rates.yml")

        private val yamlMapper: ObjectMapper = ObjectMapper(YAMLFactory()).registerKotlinModule()

        private val DEFAULT_FILE_CONTENTS =
            """
            # Experience rates.
            #
            # 1.0 is authentic Old School RuneScape. Every skill keeps the xp values Jagex uses -
            # 4 xp per damage in combat, 25 xp per willow log, and so on - and this multiplier is
            # applied on top of them.
            #
            #   https://oldschool.runescape.wiki/w/Experience
            #   https://oldschool.runescape.wiki/w/Skills
            #
            # `rate` is each player's personal multiplier. It is stored on the character, so it can
            # also be set per player in game with `::setxprate <rate> [player]`.
            rate: 1.0

            # `global-rate` is compounded on top of `rate` and applies to everyone. It is meant for
            # temporary events like a double xp weekend - set it to 2.0, then back to 1.0 after.
            global-rate: 1.0

            # Re-apply `rate` to every character as they log in, replacing the rate stored on their
            # account. Leave this on to keep the whole server at `rate`; turn it off if you want
            # `::setxprate` to grant lasting per-player rates.
            enforce-on-login: true

            # Wipe levels that were earned at an old, inflated rate.
            #
            # When this is on, each character's skills are reset to fresh-account values (all 1,
            # Hitpoints 10) the first time they log in. Items, bank, quests and location are not
            # touched. Each character is flagged once it has been reset, so this fires once per
            # character and is safe to leave on afterwards.
            #
            # THIS IS DESTRUCTIVE AND CANNOT BE UNDONE. Turn it on deliberately, once, after you
            # have settled on `rate` above. `::resetxp [player]` and `::resetxpall` do the same
            # thing on demand if you would rather pick who and when.
            reset-existing-levels: false
            """
                .trimIndent() + "\n"
    }
}
