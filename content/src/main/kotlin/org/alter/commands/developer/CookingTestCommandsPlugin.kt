package org.alter.commands.developer

import org.alter.api.Skills
import org.alter.api.ext.message
import org.alter.game.model.priv.Privilege
import org.alter.game.model.Tile
import org.alter.game.model.move.moveTo
import org.alter.game.pluginnew.PluginEvent
import org.alter.game.pluginnew.event.impl.CommandEvent
import org.alter.rscm.RSCM.asRSCM

class CookingTestCommandsPlugin : PluginEvent() {

    private enum class CookTestSet(val key: String) {
        LOW("low"),
        MID("mid"),
        HIGH("high"),
        MEAT("meat"),
        ALL("all")
    }

    private data class CookTestPreset(
        val key: String,
        val set: CookTestSet,
        val level: Int,
        val qty: Int,
    )

    private val presets = listOf(
        CookTestPreset(key = "low", set = CookTestSet.LOW, level = 1, qty = 5),
        CookTestPreset(key = "mid", set = CookTestSet.MID, level = 25, qty = 5),
        CookTestPreset(key = "high", set = CookTestSet.HIGH, level = 80, qty = 3),
        CookTestPreset(key = "meat", set = CookTestSet.MEAT, level = 1, qty = 10),
        CookTestPreset(key = "all", set = CookTestSet.ALL, level = 80, qty = 1),
    )

    override fun init() {
        on<CommandEvent> {
            where {
                command.equals("cooktest", ignoreCase = true) &&
                    player.world.privileges.isEligible(player.privilege, Privilege.DEV_POWER)
            }
            then {
                val args = args.orEmpty().map { it.trim() }.filter { it.isNotEmpty() }

                val preset = parseCookTestPreset(player, args)
                if (preset == null) {
                    sendCookTestHelp(player)
                    return@then
                }

                player.getSkills().setBaseLevel(Skills.COOKING, preset.level)

                val keys = keysFor(preset.set)
                if (keys.isEmpty()) {
                    player.message("No items configured for preset '${preset.key}'.")
                    return@then
                }

                var addedAny = false
                var failed = 0
                for (key in keys) {
                    val id = runCatching { key.asRSCM() }.getOrNull() ?: continue
                    val result = player.inventory.add(id, preset.qty)
                    if (result.hasSucceeded()) {
                        addedAny = true
                    } else {
                        failed++
                    }
                }

                if (!addedAny) {
                    player.message("Couldn't add any items (inventory full?).")
                } else if (failed > 0) {
                    player.message("Added cooking test items (some didn't fit: $failed).")
                } else {
                    player.message("Applied cooktest preset '${preset.key}' (x${preset.qty} each, Cooking=${preset.level}).")
                }
            }
        }
    }

    private fun parseCookTestPreset(player: org.alter.game.model.entity.Player, args: List<String>): CookTestPreset? {
        if (args.isEmpty() || args.any { it.equals("help", ignoreCase = true) || it == "-h" || it == "--help" }) {
            return null
        }

        val token = args.first().removePrefix("--").trim().lowercase()
        return presets.firstOrNull { it.key == token }
    }

    private fun keysFor(set: CookTestSet): List<String> =
        when (set) {
            CookTestSet.LOW -> listOf(
                "items.raw_shrimp",
                "items.raw_anchovies",
                "items.raw_sardine",
                "items.raw_herring"
            )

            CookTestSet.MID -> listOf(
                "items.raw_mackerel",
                "items.raw_trout",
                "items.raw_salmon"
            )

            CookTestSet.HIGH -> listOf(
                "items.raw_lobster",
                "items.raw_swordfish",
                "items.raw_shark"
            )

            CookTestSet.MEAT -> listOf(
                "items.raw_beef",
                "items.raw_chicken"
            )

            CookTestSet.ALL -> keysFor(CookTestSet.LOW) + keysFor(CookTestSet.MID) + keysFor(CookTestSet.HIGH) + keysFor(CookTestSet.MEAT)
        }

    private fun sendCookTestHelp(player: org.alter.game.model.entity.Player) {
        player.message("Usage: ::cooktest <preset>")
        player.message("Presets: ${presets.joinToString(", ") { it.key }}")
        player.message("Examples: ::cooktest low | ::cooktest mid | ::cooktest high | ::cooktest meat | ::cooktest all")
    }
}
