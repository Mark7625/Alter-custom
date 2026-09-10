package org.rsmod.content.other.commands

import dev.or2.central.account.Rights
import org.rsmod.api.cheat.CheatHandlerBuilder
import org.rsmod.api.script.onCommand
import org.rsmod.game.cheat.Cheat
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Registers an admin command under [command], as well as under every name in [aliases]. Each name
 * is registered as its own handler sharing the same [desc], [cheat] and [init], so alternative
 * spellings do not require duplicating the registration.
 */
internal fun ScriptContext.onCommand(
    command: String,
    desc: String,
    cheat: Cheat.() -> Unit,
    aliases: List<String> = emptyList(),
    init: CheatHandlerBuilder.() -> Unit = {},
) {
    for (name in listOf(command) + aliases) {
        onCommand(name) {
            this.requiredRights = Rights.ADMINISTRATOR
            this.desc = desc
            this.cheat(cheat)
            init()
        }
    }
}
