package org.rsmod.content.interfaces.spellbook.pack

import dev.openrune.pack.PluginPack

/**
 * Packs the spellbook clientscript overrides in `pack/cs2`. The only override replaces the
 * client's per-spell quest requirement check (`script4128`, called from
 * `magic_spellbook_unlocked`) so no spell is greyed out or blocked for a quest the player has not
 * completed - the server does not enforce spell quest requirements either.
 */
class SpellbookPluginPack : PluginPack()
